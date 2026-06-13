# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

An Android (Jetpack Compose) app scaffolded by Google AI Studio. The committed UI is
still the default template (`MainActivity` shows a "Hello Android!" `Greeting`). The goal
is to **port [gandula](https://github.com/felipedbene/gandula) to native Android/Kotlin.**

Gandula is a text-based football (soccer) management simulator — a love letter to 1998-era
Brazilian games like Elifoot. A career drops you into a random Série C club at the bottom
of a three-tier "Brasileirão Imaginário"; you set tactics, play out matches minute-by-minute
(commentary in Brazilian Portuguese), and climb the pyramid. Live web version:
[gandula.debene.dev](https://gandula.debene.dev).

### Upstream source (what we're porting from)

The original repo is in this folder as **`gandula-main.zip`** (Rust + TypeScript). Its layout:

- `core/` — **Rust** deterministic match/season engine (`play_match`, `run_season`). This is
  the heart of the port. Tunables in `core/src/engine/strength.rs` and `.../tick.rs`.
- `wasm/` — wasm-bindgen wrapper so the web app can call the Rust core.
- `web/` — Vite + React + Mantine career-mode app. The **division pyramid, economy, Copa,
  and transfer market are pure TypeScript here** (the Rust core is division-agnostic).
- `assets/teams/` — 3 sample CLI clubs + 60 fictional clubs (`fictional/`) that form the
  three-tier league (Série A/B/C × 20).
- `ARCHITECTURE.md` — the authoritative tick-loop and event-weighting formulas (extract and
  read this before touching the engine). `ROADMAP.md` — feature history.

`gandula.zip` is just a snapshot of *this* Android scaffold; `gandula.apk` is a prior build.

### Porting strategy

Two layers must be reimplemented in Kotlin:
1. **Match/season engine** (from Rust `core/`) — keep it a pure, side-effect-free module so
   it stays unit-testable and deterministic. **Determinism is the single most important
   property**: a match must be a pure function of `(homeTeam, awayTeam, seed: Long)`, with no
   reads from system time, environment, or unordered collections. Upstream seeds
   `ChaCha8Rng`; a faithful port needs a matching seeded PRNG (don't use `kotlin.random.Random`
   naively if byte-for-byte parity with upstream matters — otherwise pick one seeded RNG and
   make it the only entropy source). Mirror upstream's `core/tests/determinism.rs` with a
   Kotlin test asserting identical event logs for repeated runs.
2. **Career/meta layer** (from TS `web/`) — league tables, economy, Copa, transfers. Pure
   Kotlin, persisted locally (Room is already on the classpath; upstream used browser IndexedDB).

### Engine spec (from upstream ARCHITECTURE.md — port these exactly)

One tick = one in-game minute; a match is 90 ticks + 0–4 (deterministic) injury minutes.
Per minute, in order: stamina drain → recompute team strength → possession draw → event roll
→ event classification. Key formulas:

```
drain          = 0.30 × tempo_factor × pressing_factor      # stamina, clamped [0,99]
p_home         = clamp(0.5 + 0.005×(home.midfield − away.midfield), 0.1, 0.9)
p_event        = 0.18 × tempo_event_factor(attacker.tempo)
shot_p         = clamp(0.70 × (1 + (attacker.attack − defender.defense)/200), 0.20, 0.95)
foul_p         = 0.15 × pressing_foul_factor(defender.pressing)
# one uniform r∈[0,1): r<shot_p → Shot; r<shot_p+foul_p → Foul; else silent

# Per-player raw stats:
attack_attr    = 0.5·finishing + 0.3·technique + 0.2·pace
midfield_attr  = 0.5·passing   + 0.3·technique + 0.2·stamina
defense_attr   = 0.5·defending + 0.2·pace      + 0.3·stamina
stamina_eff    = 0.7 + 0.3×(stamina/99)         # multiply raw stats before aggregating

# Position weights (per-player contribution = raw_attr × weight, then weighted-avg over XI):
#           GK    DEF   MID   FWD
# attack    0.0   0.1   0.3   0.6
# midfield  0.0   0.2   0.6   0.2
# defense   0.1   0.6   0.3   0.0
```

`HalfTime` event at minute 45, `FullTime` at 90+injury. Always read the in-zip
`ARCHITECTURE.md` for the full set of factor functions before changing engine numbers.

### Team data model

`teams.json` is an array of teams: `{ id, name, formation (e.g. "F433"),
tactics:{ mentality, tempo, pressing, width }, starting_xi:[playerId×11], bench:[playerId×5],
roster:[{ id, name, age, position (GK/DEF/MID/FWD), attributes:{ pace, technique, passing,
defending, finishing, stamina, ... } }] }`. The engine consumes `finishing, technique, pace,
passing, defending, stamina`; the per-player `attributes` block must carry all of them.

### Port status (what already exists in Kotlin)

The **match engine is ported and working** — a vertical "play one match" slice is in place:

- `dev.debene.gandula.domain` — `Player`/`Team`/`Tactics`/enums + `Match`/`MatchEvent`
  (Moshi `@JsonClass` data classes; enum constant names match the JSON strings verbatim).
- `dev.debene.gandula.rng.MatchRng` — seeded **xoshiro256\*\*** (SplitMix64-seeded) with the
  same API as the Rust `MatchRng`. Self-consistent determinism, NOT byte-parity with upstream
  ChaCha8 (a deliberate choice — see the determinism question answered during the port).
- `dev.debene.gandula.engine` — `Strength`, `Narration` (PT-BR), and `MatchEngine.simulate(home,
  away, seed)`, a one-shot port of tick.rs + manager.rs + mod.rs (no half-time snapshot/resume).
- `dev.debene.gandula.data.TeamRepository.loadTeams(context)` — parses `assets/teams.json`.
- `dev.debene.gandula.ui` — `MatchViewModel` + `MatchScreen` Compose UI (team pickers, seed,
  "Jogar", scoreboard, minute-by-minute feed). Wired into `MainActivity`.
- Tests in `app/src/test/java/dev/debene/gandula/`: `MatchEngineTest` (determinism +
  stronger-wins), `RealTeamsIntegrationTest` (parses the real assets file and prints a feed),
  `GandulaMatchScreenshotTest` (Roborazzi render of `MatchScreen`).

**Career mode (structural core) is ported and working** — `dev.debene.gandula.career` +
`engine/SeasonEngine`:

- `engine.SeasonEngine` — port of `core/src/season/mod.rs`: circle-method double round-robin,
  per-fixture deterministic seeds, standings (Pts/GD/GF/id), partial standings up-to-round.
- `career.Divisions` — 60 teams → 3 tiers of 20 by `avgStrength`; random Série C starter.
- `career.Promotion` — 3 up / 3 down at each boundary (port of `util/promotion.ts`).
- `career.CareerEngine` / `Career`/`Season`/`Division`/`SeasonHistory` — new career, reveal a
  round, advance a season (apply P/R, record history), minimal economy (start 2M, promo +500k,
  releg −200k). Opponents reset to the registry each season.
- `data.CareerStore` — single-slot JSON save to `filesDir` (faithful to the web's single
  IndexedDB slot); stores lightweight state and **re-simulates on load** (deterministic).
- `ui.GandulaApp` — two tabs: "Partida" (`MatchScreen`) and "Carreira" (`CareerScreen` +
  `CareerViewModel`, with a stateless `CareerScreenContent`). Career screen: club/division/
  year/cash header, play-round reveal, league table (promotion/relegation/user rows colored),
  season rollover.
- `career.Finances` — **economy**, port of the core of `util/finances.ts`: per-round cash
  accrual (home gate from fanbase×tier×opponent-draw capped by stadium capacity × ticket price ×
  form; TV deal + sponsorship floor sliced per round with fair rounding; win/draw bonuses; minus
  wages = Σ player-overall × rate), end-of-season placement prize + promotion/relegation bonus,
  fanbase drift + stadium-expansion + marketing-campaign spend levers, the cash-runway
  projection, and the firing lose-condition (balance < 0 → `Career.fired`). Cash accrues on each
  `revealNextRound`; the boundary adds only placement + P/R (per-round already banked). Numbers
  copied verbatim from upstream so the balance matches.
- `career.Roster` + `career.TransferMarket` — **transfer market**, port of `util/roster.ts` +
  `util/transfer-market.ts`. `Career.userRoster` is the squad overlay; `Roster.effectiveTeam`
  reconciles XI/bench; `buildSeason` substitutes the user's effective team so **bought players
  actually play** and `Finances.seasonSalary` bills their wages. The market is a deterministic
  per-(seed, year) 12-player free-agent pool (`mulberry32` PRNG, position-biased attrs + a 12%
  elite tail), age-curve pricing (`avg² × 100 × ageMultiplier`, 0.7 resale haircut), scout
  reports, and `canBuy`/`canSell` guards (roster 14–25, no selling an XI starter). The market is
  **gated to between seasons** (`marketOpen = seasonComplete`) so per-round finances stay
  consistent; buys apply to next season's sim.
- UI: `CareerScreen` finances card (ledger, runway, stadium/marketing buttons) + "demitido"
  (fired) state via flat `CareerUi`. Third tab **"Elenco"** = `MarketScreen` (squad + free-agent
  pool, buy/sell). `MatchScreen`/`CareerScreen`/`MarketScreen` share one `CareerViewModel`
  instance (same activity-scoped `viewModel()`).
- `career.Aging` + `career.Regen` + `career.RivalCoach` — **opponent evolution**, port of
  `util/aging.ts` + `regen.ts` + `rival-coach.ts`. Each season every squad ages along an age
  curve (young develop, prime plateaus, veterans decline), players ≥36 retire and are replaced by
  same-position youth (`buildYouth`/`regenId`), and a distilled per-tier **rival coach** spends a
  stateless transfer budget buying upgrades — so the league strengthens, not just ages.
  Opponents are recomputed from the registry each season (`evolveTeam(base, elapsed, seed)` +
  `applyRivalCoach`), keyed only on (tier, seed, teamId, yearOffset) so re-simulation on load
  reproduces them exactly. The user's squad ages too (`advanceSeason` evolves `userRoster`).
  `buildSeason` applies all this; season 0 (FIRST_YEAR) is the uncoached registry baseline.
- `career.Copa` — **Copa do Brasil**, port of `util/copa.ts`. A 64-slot season-long knockout
  over all 60 evolved clubs: strength-seeded bracket (4 Série A byes + 56 strength-mirror prelim,
  no PRNG), two-leg ties decided on aggregate → away-goals → a seeded penalty shootout, six rounds
  (prelim/R32/R16/QF/SF/final). The whole cup is **pre-simulated in `buildSeason`** over the same
  evolved/coached sides the league fields (deterministic in `(teams, seasonSeed)`), revealed by
  league round (`ROUND_AT_LEAGUE_ROUND`). Cup prize money (`Finances.cupPrizeTotal`: per-round +
  champion bonus) is banked at the season boundary; `SeasonHistory` records the cup champion + the
  user's result. UI: a "Copa do Brasil" card on `CareerScreen`.
- `career.Deals` + `Deals_` — **negotiable deals, season tactics, transfer history**. `Deal`/
  `Deals`/`DealOffer`: per-season TV/sponsorship offer slates (`generateOffers`: Sólida/Agressiva/
  Conservadora, term 1–3, perf-clause on Agressiva) anchored on the tier floors; a signed deal's
  `seasonAmount` overrides the floor in `Finances.tvIncomeForRound`/`sponsorshipForRound`, with a
  rare mid-season **scandal** (`scandalDropRound`, deterministic) reverting income to the floor for
  the tail (each segment fair-rounded). `keepDeal` carries deals across the boundary (drops on term
  expiry / relegation / failed clause). `SeasonTactics` (formation + tactics) is `Career.userTactics`,
  applied via `Roster.effectiveTeam` and threaded through `buildSeason`. `TransferRecord` tracks
  buys/sells on `Career.transfers`, archived into `SeasonHistory.transfers` at the boundary. All
  three pre-season actions (sign deal / set tactics / market) are gated to `seasonComplete` so the
  current (pre-simulated) season stays consistent; they take effect next season.
- UI: `CareerScreen` pre-season section (tactics cyclers + deal-offer buttons, shown when the
  season's done); `MarketScreen` "Negócios desta temporada" list; transfer/cup line in the
  last-season summary.
- Tests: `CareerEngineTest`, `CareerStoreTest`, `FinancesTest`, `TransferMarketTest`,
  `EvolutionTest`, `CopaTest`, `DealsTacticsTest` (transfer record→history→reset, tactics override +
  carry, deal overrides floor + slices sum, keepDeal drops, offer determinism + clause), plus
  `*ScreenshotTest`s. `CareerStore` is schema v3 (userRoster + userTactics + activeDeals + transfers);
  `Season.copa` is rebuilt on load, not persisted.

- **Live half-time tactics** — `MatchEngine.simulateFirstHalf` → `HalfTimeState` (live `MatchState`
  + the in-flight RNG) → `simulateSecondHalf(state, home, away)`, which re-reads tactics each tick,
  so passing a tactic-edited user team steers the second half. `simulate()` is now their composition
  (byte-identical for unchanged tactics — the `MatchEngineTest` determinism still holds). In career:
  `Career.halftimeTactics` (round → `SeasonTactics`) is persisted (v4); `CareerEngine.userFirstHalf`
  runs the user's first half for the live prompt, `applyHalftime` records the override and re-sims
  the user's second half, patching that match + recomputing standings. `buildSeason` re-applies all
  overrides on load (re-runs first+second half per affected round) via the shared `resolveTeam` +
  `patchHalftime`, so a steered season reproduces exactly. UI: `CareerScreen` shows a half-time card
  (interval score + tactic cyclers + "Seguir para o 2º tempo") when `vm.halftimePrompt` is set;
  `playNextRound` pauses at the interval, `confirmHalftime` resolves + advances.
- Tests: `HalftimeTest` (split == one-shot; second-half change is deterministic and keeps the first
  half; live patch reproduces on the load path) + `HalftimeScreenshotTest`.

**The full upstream gameplay arc is now ported.** Remaining gaps are minor: Room is on the
classpath but unused (career uses the JSON file slot, `CareerStore` schema v4); `Season.copa` and
half-time-patched records are rebuilt on load, not persisted.

## Build & test commands

A **Gradle wrapper** (`./gradlew`, Gradle 9.5.1) is checked in. Builds need a JDK 17+ on
`JAVA_HOME`; this machine has one at `/home/linuxbrew/.linuxbrew/opt/openjdk@21` (installed
via Homebrew) and the Android SDK at `~/Android/Sdk` (android-36, build-tools 36 — pointed to
by `local.properties`). Prefix commands with the JDK if it's not already on PATH:

```bash
export JAVA_HOME=/home/linuxbrew/.linuxbrew/opt/openjdk@21
export PATH="$JAVA_HOME/bin:$PATH"
```

- Build debug APK: `./gradlew :app:assembleDebug`
- Install on device/emulator: `./gradlew :app:installDebug`
- Run unit + Robolectric tests: `./gradlew :app:testDebugUnitTest`
- Run one test class: `./gradlew :app:testDebugUnitTest --tests "dev.debene.gandula.MatchEngineTest"`
- Run one test method: `./gradlew :app:testDebugUnitTest --tests "dev.debene.gandula.MatchEngineTest.same seed yields identical match"`
- Android Lint: `./gradlew :app:lintDebug`
- Instrumented tests (needs a device): `./gradlew :app:connectedDebugAndroidTest`

The debug build signs with `debug.keystore` at the repo root (standard
`androiddebugkey`/`android` keystore — generated locally, gitignored; regenerate with
`keytool -genkeypair -keystore debug.keystore -storepass android -keypass android -alias
androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug"`).

### Screenshot tests (Roborazzi)

UI is verified with Roborazzi screenshot tests (e.g. `GreetingScreenshotTest`) that run
under Robolectric — no device needed. Baselines live in `app/src/test/screenshots/`.

- Record/update baselines: `gradle :app:recordRoborazziDebug`
- Verify against baselines: `gradle :app:verifyRoborazziDebug`

When a Compose change is intentional, re-record baselines or `verifyRoborazziDebug` will fail.

## Configuration & secrets

- `GEMINI_API_KEY` is supplied via a `.env` file at the project root (copy `.env.example`).
  The **secrets-gradle-plugin** reads `.env` (falling back to `.env.example`) and exposes
  the value through `BuildConfig`. AI Studio injects this key automatically at runtime.
- `local.properties` sets `sdk.dir`; `.env`, `local.properties`, and `debug.keystore` are gitignored.

### Signing (important gotcha)

`app/build.gradle.kts` wires **debug** builds to a `debugConfig` signing config that
expects `debug.keystore` at the project root — which is gitignored and may be absent,
breaking a fresh debug build. `README.md` instructs removing
`signingConfig = signingConfigs.getByName("debugConfig")` from the debug build type when
importing. Release signing pulls `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD`
from environment variables.

## Project layout & stack

- Single Gradle module `:app`, namespace/applicationId `dev.debene`, label "My Application".
- compileSdk 36.1, targetSdk 36, minSdk 24, Java 11 source/target.
- Dependency versions are centralized in `gradle/libs.versions.toml` (version catalog);
  the root `build.gradle.kts` only declares plugins, `app/build.gradle.kts` applies them.
- Source under `app/src/main/java/dev/debene/`: `MainActivity.kt` plus `ui/theme/`
  (Compose `MyApplicationTheme`, Color/Type).
- Stack wired up but largely **not yet used**: Room (+KSP), Retrofit + Moshi (+KSP codegen)
  + OkHttp logging, Kotlin coroutines, Compose Material3, Firebase BOM. Several deps
  (CameraX, location, navigation, datastore, coil, firebase-ai) are commented out in
  `app/build.gradle.kts` — uncomment there rather than re-adding from scratch.

### Assets

`teams.json` lives at `app/src/main/assets/teams.json` (60 fictional clubs, ~1020 players).
It was originally committed at a doubled `app/app/src/main/assets/` path that wouldn't bundle;
that's been fixed — keep it under `app/src/main/assets/`.
