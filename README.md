# Gandula — Android port

[![CI](https://github.com/felipedbene/gandula-android/actions/workflows/ci.yml/badge.svg)](https://github.com/felipedbene/gandula-android/actions/workflows/ci.yml)

A native **Android / Jetpack Compose** port of [gandula](https://github.com/felipedbene/gandula)
— a text-based football (soccer) management simulator, a love letter to 1998-era Brazilian
games like Elifoot. Start at the bottom of a three-tier "Brasileirão Imaginário", set tactics,
play matches minute-by-minute (commentary in Brazilian Portuguese), and climb the pyramid.

The original is a Rust simulation core + TypeScript web app; this is a faithful, fully
offline Kotlin reimplementation (no network, no accounts — `100% salvo no aparelho`).

## What's in it

- **Deterministic match engine** with PT-BR radio commentary — a match is a pure function of
  `(home, away, seed)`. One-shot and half-split (so you can change tactics at the interval).
- **Three-tier league** (60 clubs) — round-robin schedule, standings, promotion/relegation.
- **Economy** — per-round gate/TV/sponsorship/wages, placement + promotion + cup prizes,
  stadium/fanbase/marketing levers, a cash-runway projection, and a firing lose-condition.
- **Transfer market** — deterministic free-agent pool, age-curve pricing, buy/sell.
- **A living world** — squads age, retire, regenerate youth, and AI clubs ("rival coaches")
  buy upgrades each season, so the league strengthens over time.
- **Copa do Brasil** — a 64-slot two-leg knockout (away-goals + penalty shootouts).
- **Negotiable TV/sponsorship deals + scandals**, season tactics, transfer history, and
  **live half-time tactics**.

State lives in a single on-device JSON save and is re-simulated deterministically on load.

## Build & run

Needs a JDK 17+ and the Android SDK (the Gradle wrapper is checked in).

```bash
./gradlew :app:assembleDebug        # build the debug APK
./gradlew :app:installDebug         # install on a connected device/emulator
./gradlew :app:testDebugUnitTest    # run the unit + Robolectric tests
```

The debug build signs with a local `debug.keystore` (gitignored); regenerate it with:

```bash
keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
  -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug"
```

CI (GitHub Actions) runs the tests and builds the APK on every push/PR — see
[`.github/workflows/ci.yml`](.github/workflows/ci.yml).

See [`CLAUDE.md`](CLAUDE.md) for the architecture and a map of the codebase.
