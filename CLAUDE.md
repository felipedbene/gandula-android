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
  reports, and `canBuy`/`canSell` guards (roster 14–25, no selling an XI starter). The squad
  market is **open all season** (`transfersOpen = !fired`): a buy/sell is recorded as a
  `TransferRecord` tagged with the round it took effect (+ a player snapshot), and the squad is
  *derived* from that log via `Roster.rosterAtRound(seasonStart, transfers, round)` — `userRoster`
  now means the season-**start** squad, not the live one. A mid-season move re-simulates only the
  user's still-unplayed matches (`CareerEngine.rebuildSeason`); already-revealed rounds, their
  results and banked cash are untouched, and wages bill from the move's round forward
  (`salarySliceForRound` uses `rosterAtRound`). The Copa squad is fixed at season start (cup
  registration). Deal-signing (TV/sponsorship) stays between-seasons (`marketOpen = seasonComplete`).
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
  buys/sells on `Career.transfers` (now also tagged with round + player snapshot for mid-season
  re-sim), archived into `SeasonHistory.transfers` at the boundary. Sign-deal and set-season-tactics
  stay gated to `seasonComplete` (take effect next season); the squad market and **per-match
  tactics are live mid-season** (see below).
- UI: `CareerScreen` pre-season section (tactics cyclers + deal-offer buttons, shown when the
  season's done); `MarketScreen` "Negócios desta temporada" list; transfer/cup line in the
  last-season summary.
- Tests: `CareerEngineTest`, `CareerStoreTest`, `FinancesTest`, `TransferMarketTest`,
  `EvolutionTest`, `CopaTest`, `DealsTacticsTest` (transfer record→history→reset, tactics override +
  carry, deal overrides floor + slices sum, keepDeal drops, offer determinism + clause), plus
  `*ScreenshotTest`s, plus `MidSeasonTest` (mid-season buy leaves played rounds intact + reproduces
  on load; pre-match tactics reproduce on load). `CareerStore` is schema **v5** (userRoster =
  season-start squad + userTactics + activeDeals + round-tagged transfers + matchTactics +
  halftimeTactics); `Season.copa` is rebuilt on load, not persisted.

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

- **Mid-season interventions (live transfers + per-match tactics)** — the season is no longer a
  frozen pre-sim: it's a deterministic *replay* of overlays (`userTactics`, round-tagged `transfers`,
  `matchTactics`, `halftimeTactics`) over the seeds. `CareerEngine.rebuildSeason` re-derives the
  divisions + Copa from the current overlays preserving the reveal cursor, and a unified
  `patchUserMatches` re-runs each of the user's matches at its per-round squad
  (`Roster.rosterAtRound`) + pre-kickoff tactic (`matchTactics[round] ?? userTactics`) + optional
  half-time override — rounds with no change are left byte-identical, so determinism (and the
  `MatchEngineTest`/`HalftimeTest` invariants) hold. Buy/sell append a round-tagged `TransferRecord`
  and route through `rebuildSeason` off the main thread (`CareerViewModel.mutateRebuild`); only
  unplayed user matches change. UI: `CareerScreen` shows a pre-kickoff "Escalação — rodada N" tactics
  card above the play button (`vm.matchTacticsDraft` + `cyclePre*`), and the **Elenco** tab's market
  is enabled all season. The Finances card was moved above the Copa card for discoverability.

- **Live match broadcast** — matches play out minute-by-minute instead of revealing instantly. The
  engine already emits the random-event log (`Shot`/`NearMiss`/`Goal`/`Foul`/`Yellow`/`RedCard`/
  `PenaltyAwarded`/`PenaltyMissed`/`Substitution`/`HalfTime`/`FullTime`); `ui/LiveMatch.kt`'s
  `MatchBroadcast` composable just streams a slice of `match.events` over a wall-clock (~340ms/min,
  a goal pauses longer), ticking a clock 0'→90'+, counting goals into a live scoreboard, with a
  "Pular" skip; `onDone` fires once when the slice ends. `splitAtHalfTime` cuts the event log at the
  whistle. **Career** brackets the existing half-time card with two broadcasts: `playNextRound`
  resolves the user's `CareerEngine.userMatch` and broadcasts the first half → `onBroadcastDone`
  shows the half-time card → `confirmHalftime` (re-sims the 2nd half if tactics changed) broadcasts
  the second half → `onBroadcastDone` calls `revealNextRound`. `CareerViewModel.broadcast`
  (+`CareerUi.live`) drives a `broadcast` slot on `CareerScreen`; the play/pre-match controls hide
  while live. **Partida** (friendly) plays the whole match as one straight broadcast. Pure
  presentation — the simulated `Match` is unchanged, so determinism holds.

- **Starting XI selection + PT tactic labels** — `SeasonTactics` gained an optional `xi: List<Int>?`
  (player ids); `Roster.effectiveTeam` fields it (validated against the roster, bench = the rest), so
  it flows through the season-default, pre-match, and half-time overlays uniformly and stays
  deterministic (the XI is read at kickoff). UI: `ui/Lineup.kt`'s `LineupEditor` is a tap-a-starter →
  tap-a-reserve swap (always 11), exposed via a collapsible "Escalação (titulares)" on the pre-match
  and pre-season cards (`vm.setPreMatchXi` / `setSeasonXi`, `upcomingXi` / `seasonXi`).
  `ui/TacticLabels.kt` maps the tactic enums to Portuguese (`ptLabel()`) so the UI never shows the raw
  English constant names (Attacking/Fast/High/Wide → Ofensivo/Rápido/Alta + formations like 4-3-3).

- **Half-time substitutions** — the half-time card now carries the `LineupEditor` too (capped at 3
  swaps); the chosen XI rides the half-time `SeasonTactics.xi`. `MatchEngine.simulateSecondHalf` gained
  optional `homeSubs`/`awaySubs` (`off id → on id`) applied at the interval via `applyUserSubs` (swap
  `homeCurrentXi`, reset stamina, mark bench used, count toward `MAX_SUBS_PER_MATCH`, emit a
  `Substitution` event) — **empty by default, so `simulate()` and unchanged half-times stay
  byte-identical**. `CareerEngine.patchUserMatches` derives the subs from the H1-XI vs half-time-XI
  diff (`computeSubs`) and passes them, so a substituted second half reproduces on load. Tests:
  `HalftimeTest` (sub changes 2nd half, deterministic, no-sub == `simulate()`), `MidSeasonTest`
  (custom XI changes the season + reproduces on load).

- **Contracts / wage demands / departures** (`career.Contracts`) — players have a deterministic
  *temperament* from (careerSeed, id): **loyal** (refused → sulks, a one-off attribute drop next
  season) vs **mercenary** (refused → leaves for a fee). Top players may also be **poached** (an
  outside bid to match-with-a-raise or sell). Each player carries a wage **multiplier**
  (`Career.wageMultipliers`, default 1.0); `Finances` bills `overall × rate × multiplier`.
  `endOfSeasonDemands` (pure in seed+year+squad) is generated the moment the season completes
  (`revealNextRound`) into `Career.pendingDemands`; the user accepts/refuses (`demandDecisions`) in the
  pre-season "Vestiário" card; `advanceSeason` calls `Contracts.resolve` at the boundary (raises bump
  the multiplier, departures remove the player + bank the fee, snubs sulk) — never touching the
  season already played. Persisted in `CareerStore` **schema v6**. Tests: `ContractsTest`.

- **UI design system — "frosted glass / neon console"** (Phase 1 of the visual refresh, modelled on
  the user's dashboard reference). `ui/theme/Color.kt` repoints the whole palette to deep slate
  (`#020617`) + indigo/purple accents (`IndigoLight`/`PurpleLight`) + emerald/amber/rose status, with
  the old color names kept as aliases so every screen adopts it for free. `ui/theme/Glass.kt` adds
  `GlowBackground` (slate fill + two radial indigo/purple glows, placed once at the app root in
  `GandulaApp`) and `GlassCard` (translucent body + glowing border). Hero headers use the indigo→purple
  gradient; the section/finance/player cards use the glass body+border.

- **Pitch-view lineup editor** (Phase 2, per the web reference) — `ui/PitchLineup.kt`'s
  `PitchLineupEditor` draws a green pitch in a `Canvas` (lines/box/circle) and lays the XI out by
  position band (FWD up top → GK at the back, spread across the width), with a horizontally-scrollable
  bench below. **Drag-to-swap**: `detectDragGestures` moves a token (zIndex-lifted, offset by the drag
  delta); on drop it hit-tests the nearest token centre (window coords via `boundsInWindow`, captured
  per-token in `onGloballyPositioned`) and swaps starter↔reserve — always 11, so always fieldable.
  Tokens are **coloured by position** (GK amber / DEF blue / MID green / FWD rose, with a legend), show
  overall + last name, and ring amber on low stamina / white while dragged. Replaces the list-based
  `LineupEditor` on the pre-match, pre-season, and half-time cards; `onChange` feeds the same
  `setPreMatchXi` / `setSeasonXi` / half-time-`xi` paths. Test: `PitchScreenshotTest`.
  **Formation ⇄ lineup are linked:** the pitch lays out by `Formation.lineCounts()` (DEF/MID/FWD per
  formation), so cycling 4-3-3 → 4-5-2 reshapes it; cycling the formation **rebuilds the XI**
  (`CareerViewModel.buildXiForFormation`: best GK + the formation's counts, preferring current
  starters) so the squad always matches the shape; and drag-swaps are **same-position only** — so the
  XI is always exactly 1 GK + a valid outfield composition (no keeper in the outfield, no incomplete
  side). Half-time keeps the same players (reshape only — the 3-sub cap governs personnel).
  **`Roster.lineupFor(roster, formation, prefer)`** is the single source of truth: it returns `prefer`
  unchanged when it's already a valid 11 for the formation, else rebuilds (1 GK + `Formation.lineCounts()`,
  preferring `prefer`, best-by-overall, shortfall filled). `Roster.effectiveTeam` now **always
  normalizes the user's XI through it**, so a corrupted lineup (e.g. the 2-keeper / striker-at-CB XIs
  that `reconcileXI`'s position-blind backfill could produce after retirements) is repaired for both
  display *and* simulation — and the repair is deterministic, so saved careers self-heal on load. The
  VM's `seasonXi`/`upcomingXi`/`buildXiForFormation` all delegate to `lineupFor`. Test: `MidSeasonTest`
  (custom 3-5-2 is fielded; `lineupFor` repairs a 2-keeper XI to one keeper).

- **Navigation + focused flow** (Phase 3) — `GandulaApp` replaced the top `TabRow` with a **frosted
  bottom nav** (`SlateNavBg` + glowing active indicator, `material-icons-extended` now enabled) of
  **five** destinations: Partida · **Jogo** · **Tabela** · **Finanças** · Elenco. The career screen was
  split so the pre-match view stays **focused on tactics**: `CareerScreen` ("Jogo") is now just
  header + tactics/lineup + the play button + live broadcast/half-time/season-end + pre-season
  (tactics/deals/vestiário); the league standings + Copa moved to `CareerTableScreen` ("Tabela") and
  the ledger/runway/stadium/marketing levers to `CareerFinanceScreen` ("Finanças") — all three share
  the one activity-scoped `CareerViewModel`. Polish: the header "Caixa" counts up (`animateIntAsState`)
  and the live scoreboard border pulses (`rememberInfiniteTransition`). `FinancesCard` was refactored
  to take explicit params (no longer the whole `CareerUi`).

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
