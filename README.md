# Lotz

Lotz is a lottery strategy simulator for Oregon Megabucks, Powerball, and Mega Millions. It plays your chosen strategy over simulated time, including rolling jackpots, co-player wins, prizes, and repeatable Monte Carlo batches.

The same Compose Multiplatform interface runs on desktop, Android, iOS, and the web. The simulator is for exploring odds and long-run outcomes, not for predicting lottery numbers.

## Modules

| Module | Responsibility |
| --- | --- |
| `core-sim` | Pure Kotlin Multiplatform simulation engine: official game rules, purchases, prizes, rolling-jackpot market model, and batch statistics. |
| `composeApp` | Shared Compose UI plus Android, desktop, iOS-framework, and wasm browser entry points. |
| `iosApp` | Small SwiftUI/Xcode host that embeds the `composeApp` iOS framework. |

## Run it

Prerequisites: JDK 17, Android SDK for Android builds, and Xcode with an installed iOS simulator runtime for iOS. On macOS, run all commands from the repository root.

### Desktop

```sh
./gradlew :composeApp:run
```

This opens **Lotz — Lottery strategy simulator**. The default window is 1120×800 and can shrink to a phone-like 390×620 for layout testing.

### Web (wasm)

```sh
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

Gradle starts a development server and opens the browser. The page title is `Lotz`; its initial `Loading Lotz…` indicator remains visible until the Compose UI starts. Monte Carlo batches retain deterministic seed order and execute cooperatively/sequentially on wasm's single-threaded runtime.

### Android

```sh
./gradlew :composeApp:assembleDebug
```

Install `composeApp/build/outputs/apk/debug/composeApp-debug.apk` on an emulator or device. The launcher label is **Lotz**; the shared scrollable layout is designed for portrait phone widths.

### iOS simulator

```sh
xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  build
```

The Xcode build phase invokes `:composeApp:embedAndSignAppleFrameworkForXcode` and embeds the static `ComposeApp` framework. If that exact simulator name is unavailable, list installed destinations with `xcrun simctl list devices available` and substitute one of its names.

## Simulation model

Lotz encodes each game's drawing matrix, prize tiers, options, ticket price, schedule, and jackpot odds as data. Players may choose entries per drawing, options, frequency, budget, duration, or an until-jackpot stop condition. A sales-elasticity model estimates co-player participation; the resulting co-player wins reset a rolling jackpot just as a player jackpot does.

See [game rules](docs/game-rules.md) for the rule sources and modeling decisions, and [market model](docs/market-model.md) for the rolling-jackpot and co-player model.

## Reproducibility

Every run has a seed. Enter one in the configuration screen to replay the exact same single run; batches derive each run's seed from one master seed, so the aggregate result is replayable too. Leave the field blank to generate a seed—the results screen records the generated value for later reuse.

## Public release materials

The release-ready app icons, store listing copy, privacy policy, and submission checklist are in [`docs/release`](docs/release/). Lotz is an educational simulator only: it does not sell tickets, accept wagers, or predict lottery numbers.

## Verify the project

```sh
./gradlew build
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:run
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build
```

The Gradle build compiles and tests all supported Kotlin targets. `core-sim` also retains the legacy Intel iOS simulator target; `composeApp` intentionally supports Apple Silicon simulators and devices only because of an upstream Compose Material3 variant conflict described in `composeApp/build.gradle.kts`.
