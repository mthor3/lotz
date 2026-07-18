# Build plan: Lotz

**Goal:** Lotz — a Kotlin Multiplatform lottery simulator: shared simulation engine (`core-sim`) + Compose Multiplatform app (`composeApp`) on Desktop (JVM), Android, iOS, and wasmJs. Three games at launch — Oregon Megabucks, Powerball, Mega Millions — with faithful real-life rules, prices, options, and odds. The player configures entries per drawing, game options, play frequency, and a stop condition: total money to spend (no limit), continuous time to play, or "play until jackpot regardless of cost." Jackpots roll realistically: simulated co-players buy tickets in volumes that respond to jackpot size (real sales-elasticity statistics), other winners occur at real odds (resetting or splitting the jackpot), and the jackpot grows from simulated sales. Batch/Monte Carlo mode runs a strategy many times and reports aggregate statistics. No persistence — runs live in memory.

**Target repo:** `/Users/marty/Workspaces/lotz` (this repo).

**Method:** chunked delegation. One chunk per fresh session; repo state is the only carrier of context. Every chunk starts by verifying its entry state and ends with a git commit. If a chunk's execution reveals this plan is wrong, **update this file and commit it** — the plan is a living checkpoint, not a contract. Marty is on Claude Pro: chunks are sized to ~1 focused hour, one chunk per session window.

**Chunk order:** 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8, strictly sequential; each chunk's entry state is the previous chunk's exit state. Suggested model per chunk: **Opus** for architecture-heavy chunks (1, 3, 6), **Sonnet** for spec-driven fill-in (2, 4, 5, 7, 8).

**Template repo:** `/Users/marty/Workspaces/use-case-kmp` is a prior chunked-delegation KMP build on this same machine. Its `gradle/libs.versions.toml`, module layout, target set, and documented plan drift (AGP pinned to 8.x, not 9.x; composeApp iOS targets limited to `iosArm64` + `iosSimulatorArm64`; deprecated `compose.*` plugin aliases) are proven on this toolchain (Xcode 26.6, JDK 25, Homebrew Gradle). Chunks should crib from it rather than rediscovering these traps.

## Assumptions and fixed decisions

- **Stack:** Kotlin 2.3.x, Gradle wrapper 8.x, Compose Multiplatform (latest stable, 1.11.x at planning time), kotlinx-serialization, kotlinx-datetime, kotlinx-coroutines. All versions in `gradle/libs.versions.toml`; verify current stable at chunk time. Copy the pinned-version rationale comments from `use-case-kmp/gradle/libs.versions.toml` (AGP 8.x pin especially).
- **Modules:** `:core-sim` (pure KMP library — games, market model, simulation, statistics; zero Compose deps) and `:composeApp` (all UI, shared `commonMain` Compose code; per-platform source sets only for entry points). `iosApp/` Xcode wrapper added in Chunk 8.
- **KMP targets:** `core-sim`: `jvm()`, `androidTarget()`, `iosArm64()`, `iosSimulatorArm64()`, `iosX64()`, `wasmJs { browser() }`. `composeApp`: same minus `iosX64` (known Compose material3 variant conflict — see use-case-kmp plan drift). Desktop = Compose for Desktop on JVM.
- **Money as `Long` cents** (avoid floating-point money). Odds/probability math in `Double`.
- **Deterministic RNG:** every simulation takes a seed (`kotlin.random.Random(seed)`); batch runs derive per-run seeds from a master seed. This makes tests reproducible and bugs replayable.
- **Simulated time**, not wall-clock: the engine steps through drawing dates per each game's real weekly schedule using kotlinx-datetime. "Play for 10 years" = iterate ~1,560 Powerball drawings instantly.
- **Game rules are data, not code:** each game is a `GameDefinition` value (matrix sizes, price, options, prize table, schedule, jackpot params) interpreted by one shared engine. Adding a game later = adding a definition.
- **Rules verified at build time, recorded in repo:** Chunk 2 web-verifies current real rules and writes `docs/game-rules.md` with sources and as-of date. The figures below are planning-time best knowledge (July 2026), not gospel:
  - **Oregon Megabucks:** pick 6 of 48; $1 buys 2 plays (min purchase $1); jackpot starts $1M; drawings Mon/Wed/Sat; jackpot odds 1 : 12,271,512 per play; lower tiers 5/6, 4/6, 3/6.
  - **Powerball:** 5 of 69 + 1 of 26; $2/play; Power Play +$1 (2–10×, 10× only below a jackpot threshold; Match-5 capped $2M), Double Play +$1; drawings Mon/Wed/Sat; jackpot odds 1 : 292,201,338; base jackpot ~$20M.
  - **Mega Millions (post-Apr-2025 format):** 5 of 70 + 1 of 24; $5/play with built-in random multiplier (2×–10×) on non-jackpot prizes (Megaplier discontinued); drawings Tue/Fri; jackpot odds 1 : 290,472,336; base jackpot ~$50M.
- **Jackpot payout:** track the advertised (annuity) jackpot; report both annuity and estimated cash value (documented ratio in the market model doc). No tax modeling in v1.
- **Market model (the novel part, Chunk 3):** ticket sales per drawing are a documented function of the current advertised jackpot (sales elasticity from published lottery-sales research/data — sales grow super-linearly at mega-jackpots); other-winner count per drawing ~ Poisson(salesTickets / jackpotOdds); any winner (including simultaneously with the player → split) resets the jackpot to base; no winner → jackpot grows by a documented contribution rate applied to simulated sales. Megabucks uses Oregon-scale sales; PB/MM use national-scale. All coefficients and sources live in `docs/market-model.md`.
- **No persistence.** Runs are in-memory; leaving the results screen or restarting loses them by design.
- **No network at runtime.** The app is fully offline; web research happens only at build time by chunk executors.

---

## Chunk 1 — Toolchain walking skeleton *(Opus)*

**Entry state:** commit `chore: scaffold lotz repo with build plan`; repo has `docs/build-plan.md`, `README.md`, `.gitignore`; no Gradle files.

**Start prompt:**
> In `/Users/marty/Workspaces/lotz`, execute **Chunk 1** of `docs/build-plan.md` (read the whole header first). Deliverable: a building, runnable KMP walking skeleton — Gradle wrapper + version catalog, `core-sim` KMP module compiling on jvm/android/iosArm64/iosSimulatorArm64/iosX64/wasmJs, and `composeApp` hello-world running on desktop and building for android and wasmJs, iOS framework linking for simulator. Crib versions, target config, and workarounds from the sibling repo `/Users/marty/Workspaces/use-case-kmp` (a proven build on this machine) — especially its `libs.versions.toml` comments about AGP 8.x and composeApp iOS targets. Verify entry state with `git log` first. Commit when exit criteria pass.

**Tasks:**
- `gradle wrapper`; `settings.gradle.kts` (`core-sim`, `composeApp`); `gradle/libs.versions.toml` with current stable versions (re-verify; don't blind-copy).
- `core-sim/build.gradle.kts`: six targets, kotlinx-serialization/datetime/coroutines in `commonMain`, kotlin-test in `commonTest`; one placeholder type + one common test.
- `composeApp/`: Compose MP scaffold — desktop `main.kt` (window titled "Lotz"), android manifest/activity, wasmJs entry + `index.html`, iosMain framework export — hello screen referencing the `core-sim` placeholder type.

**Exit criteria:** `./gradlew build` green (iOS-link exclusions as documented in use-case-kmp are acceptable); `:composeApp:run` opens the desktop window; `:composeApp:assembleDebug` and `:composeApp:wasmJsBrowserDistribution` green; `:composeApp:linkDebugFrameworkIosSimulatorArm64` green; common test passes.

**Commit:** `feat: multiplatform walking skeleton builds on all targets`

---

## Chunk 2 — Game rules engine *(Sonnet)*

**Entry state:** Chunk 1 commit; `./gradlew build` green.

**Start prompt:**
> In `/Users/marty/Workspaces/lotz`, execute **Chunk 2** of `docs/build-plan.md` (read the header, especially the game-rules figures). Deliverable: in `core-sim`, the data-driven game rules engine — `GameDefinition`s for Oregon Megabucks, Powerball, and Mega Millions, drawing generation, ticket/prize evaluation — plus `docs/game-rules.md` recording web-verified current rules with sources. Verify entry state (`git log`, `./gradlew build`) first. Commit when exit criteria pass.

**Tasks:**
- **Web-verify** each game's current matrix, price, options, prize tiers/amounts, schedule, base jackpot, and odds; write `docs/game-rules.md` (as-of date + source URLs). If reality differs from the plan header, follow reality and note the drift in that doc.
- `core-sim` `commonMain` (package `dev.marty.lotz.sim` or similar): `GameDefinition` (name, main matrix pick/pool, bonus matrix, playPrice cents, plays-per-purchase for Megabucks pricing, options list, prize table, `DayOfWeek` schedule, base jackpot, odds), `GameOption` (Power Play, Double Play, built-in MM multiplier — modeled generically: price delta + prize-multiplier behavior with weighted multiplier distributions), `Ticket`, `DrawResult`, `PrizeTier`, `Prize` evaluation (match counts → tier → amount, multipliers applied per option rules, jackpot tier flagged rather than amounted — jackpot value comes from the market model later).
- Drawing generation from seeded `Random` (distinct unordered main numbers + bonus ball); quick-pick ticket generation.
- Tests: exact odds per tier computed combinatorially and asserted against published odds (within rounding); prize evaluation table-driven per game; multiplier option behavior (incl. Power Play match-5 $2M cap, 10× threshold); Megabucks $1-buys-2-plays pricing; statistical smoke test (e.g. 100k seeded quick-picks, tier frequencies within tolerance of theoretical odds).

**Exit criteria:** `./gradlew :core-sim:jvmTest` green including odds assertions; `docs/game-rules.md` exists with sources; full build still green.

**Commit:** `feat: data-driven rules engine for Megabucks, Powerball, Mega Millions`

---

## Chunk 3 — Market model: rolling jackpots and co-players *(Opus — riskiest chunk, front-loaded)*

**Entry state:** Chunk 2 commit; tests green.

**Start prompt:**
> In `/Users/marty/Workspaces/lotz`, execute **Chunk 3** of `docs/build-plan.md` (read the header's market-model decision). Deliverable: the rolling-jackpot market model in `core-sim` — jackpot-elastic ticket sales, Poisson other-winners, split/reset/rollover — plus `docs/market-model.md` documenting every coefficient with real-world sources. Verify entry state first. Commit when exit criteria pass.

**Tasks:**
- **Research (web) and document** in `docs/market-model.md`: per-game sales-vs-jackpot data points (Powerball/Mega Millions national draw sales at various jackpot levels; Oregon Megabucks typical sales), fitted curve choice (e.g. power law or exponential in advertised jackpot, calibrated to the cited data points), jackpot contribution rate (share of sales that grows the advertised annuity jackpot, incl. the cash→annuity ratio), and the annuity/cash ratio used for reporting. Every constant in code must trace to a line in this doc.
- `core-sim`: `MarketModel` — `salesForDrawing(game, advertisedJackpot, rng)` (curve + noise), `otherJackpotWinners(sales, odds, rng)` via Poisson, `JackpotState` advance: winner(s) → reset to base (player co-winning splits evenly among winners incl. player); no winner → jackpot += contribution(sales). Deterministic under seed.
- Tests: monotonic sales in jackpot; calibration points reproduced within tolerance; Poisson winner frequency matches expectation over many seeded drawings; jackpot trajectory sanity (e.g. median drawings-to-reset for Powerball plausibly in the published rollover range); split math.

**Exit criteria:** tests green; `docs/market-model.md` exists, every code constant traceable to it; build green.

**Commit:** `feat: rolling jackpot market model with sales elasticity and co-players`

---

## Chunk 4 — Simulation engine *(Sonnet)*

**Entry state:** Chunk 3 commit; tests green.

**Start prompt:**
> In `/Users/marty/Workspaces/lotz`, execute **Chunk 4** of `docs/build-plan.md`. Deliverable: the single-run simulation engine in `core-sim` — player strategy config, drawing-by-drawing loop over simulated time, stop conditions (budget / duration / until-jackpot), and a run timeline result. Verify entry state first. Commit when exit criteria pass.

**Tasks:**
- `PlayerStrategy`: game, entries per drawing, selected options, number choice (quick pick v1; fixed numbers allowed), play frequency (every drawing, or every Nth), stop condition — sealed: `BudgetCap(totalCents)` (stop when next purchase would exceed; winnings do NOT extend budget unless a `reinvestWinnings` flag is set), `Duration(period)`, `UntilJackpot` (unbounded — engine must stream/checkpoint, not accumulate unbounded memory: keep aggregates + a bounded recent-events window + all jackpot-relevant events).
- `SimulationEngine.run(strategy, seed)`: steps drawing dates from a start date per game schedule; each drawing: market sales → player buys tickets (spend) → draw → evaluate player prizes + other winners → jackpot advance (split if player + others hit) → record. Result: `RunResult` — totals (spent, won, net, drawings, elapsed simulated time), per-tier win counts, jackpot-won flag/amount (annuity + cash), and a `TimelinePoint` series (bounded: aggregate to at most ~2,000 points for UI, uniform decimation).
- Suspend/cooperative: `run` is `suspend`, checks cancellation each drawing, reports progress via callback/`Flow` (needed for until-jackpot runs that may take millions of drawings).
- Tests: budget stop exact-boundary; duration stop date math; until-jackpot with a rigged high-odds `GameDefinition` completes; determinism (same seed ⇒ identical `RunResult`); memory-bounded timeline; net = won − spent invariant.

**Exit criteria:** tests green; a scratch `main` (or test) prints a readable 1-year Powerball run summary; build green.

**Commit:** `feat: simulation engine with budget, duration, and until-jackpot stop conditions`

---

## Chunk 5 — Batch runs and statistics *(Sonnet)*

**Entry state:** Chunk 4 commit; tests green.

**Start prompt:**
> In `/Users/marty/Workspaces/lotz`, execute **Chunk 5** of `docs/build-plan.md`. Deliverable: Monte Carlo batch execution with aggregate statistics in `core-sim`. Verify entry state first. Commit when exit criteria pass.

**Tasks:**
- `BatchRunner.run(strategy, runs, masterSeed)`: per-run seeds derived from master; runs executed with coroutines (`Dispatchers.Default` parallelism where the platform supports it; sequential fallback is fine on wasm); progress `Flow` (completed/total); cancellable.
- `BatchStats` from `RunResult`s: distribution summaries (min/median/mean/p90/p99/max) for net outcome and spend; probability of profit; expected loss per dollar; per-tier hit rates; for until-jackpot batches: distribution of drawings/years/cost to jackpot; histogram buckets for the UI.
- Guardrail: refuse/warn combinations that can't finish (e.g. 10,000 × until-jackpot on real Powerball odds) — compute expected total drawings first and surface an estimate; cap batch until-jackpot runs behind an explicit override flag in the config.
- Tests: seed-derivation determinism (batch reproducible); stats math against hand-computed fixtures; cancellation mid-batch; parallel and sequential paths agree for same master seed.

**Exit criteria:** tests green; scratch run of 1,000 × 1-year Powerball prints sensible stats in seconds on JVM; build green.

**Commit:** `feat: monte carlo batch runner with aggregate statistics`

---

## Chunk 6 — Shared UI: configuration and app shell *(Opus)*

**Entry state:** Chunk 5 commit; tests green.

**Start prompt:**
> In `/Users/marty/Workspaces/lotz`, execute **Chunk 6** of `docs/build-plan.md`. Deliverable: the Compose Multiplatform app shell and simulation-configuration UI in `composeApp/commonMain` — game picker, strategy form, run-mode selection — wired to a `SimulationViewModel` that can launch single and batch runs (results screen is a placeholder until Chunk 7). Verify entry state first. Commit when exit criteria pass.

**Tasks:**
- App shell in `commonMain`: simple navigation (config → running → results), Material 3 theme, works within one shared codebase for all targets (no per-platform screens).
- Config screen: game selection (the three games with matrix/price/schedule/odds summary), entries per drawing, options toggles per game (Power Play, Double Play; MM multiplier shown as built-in), frequency, mode: single vs batch (run count), stop condition picker (budget amount / duration / until-jackpot) with input validation (money parsing to cents, sensible bounds), seed field (blank = random) for reproducibility, and the Chunk 5 feasibility estimate/override surfaced for batch until-jackpot.
- `SimulationViewModel` (commonMain, plain class + `StateFlow`, lifecycle handled per target minimally): holds config state, launches engine/batch on background dispatcher, exposes progress, supports cancel. Placeholder results screen dumps raw totals as text.
- Manual check on desktop (`:composeApp:run`): configure and run a 1-year single Powerball run end-to-end; verify android/wasm/iOS-sim still build.

**Exit criteria:** desktop run works end-to-end to placeholder results; form validation blocks bad input; all target builds green.

**Commit:** `feat: compose app shell with simulation configuration UI`

---

## Chunk 7 — Shared UI: results and progress *(Sonnet)*

**Entry state:** Chunk 6 commit.

**Start prompt:**
> In `/Users/marty/Workspaces/lotz`, execute **Chunk 7** of `docs/build-plan.md`. Deliverable: real results UI in `composeApp/commonMain` — running/progress screen with cancel, single-run results with a balance-over-time chart and event log, batch results with distribution stats and histogram. Charts are hand-drawn with Compose `Canvas` (no third-party chart lib). Verify entry state first. Commit when exit criteria pass.

**Tasks:**
- Running screen: progress (drawings simulated / runs completed), elapsed, cancel button (cooperative cancellation already in engine).
- Single-run results: headline cards (spent, won, net, simulated span, jackpot banner if won — annuity + cash); line chart of cumulative net over simulated time from `TimelinePoint`s (Canvas: axes, min/max labels, zero line); notable-events list (big wins, jackpot splits, jackpot resets observed).
- Batch results: stat table (median/mean/p90/p99 net, probability of profit, loss per dollar, tier hit rates); histogram of net outcomes (Canvas bars); for until-jackpot batches, cost/years-to-jackpot distribution.
- Formatting: money from cents with locale-reasonable formatting (shared expect/actual only if genuinely needed; prefer a pure-Kotlin formatter in common).
- Manual desktop verification of all three views; all target builds green.

**Exit criteria:** desktop: single 10-year Megabucks run shows chart + events; 1,000-run batch shows stats + histogram; cancel works mid-run; builds green on all targets.

**Commit:** `feat: results UI with timeline chart, batch stats, and progress`

---

## Chunk 8 — Platform entry points, polish, docs *(Sonnet)*

**Entry state:** Chunk 7 commit.

**Start prompt:**
> In `/Users/marty/Workspaces/lotz`, execute **Chunk 8** of `docs/build-plan.md`. Deliverable: first-class entry points on every target (incl. `iosApp/` Xcode wrapper cribbed from `/Users/marty/Workspaces/use-case-kmp/iosApp`), per-target run verification, and a real README. Verify entry state first. Commit when exit criteria pass.

**Tasks:**
- `iosApp/` Xcode project wrapping the composeApp framework (copy structure from use-case-kmp; app name "Lotz"); build for simulator via `xcodebuild`.
- Desktop: window default size/min size, app title; Android: app name/icon-label, portrait-friendly layout check; wasm: page title, loading indicator, sequential-batch fallback verified.
- Sweep: run a long until-jackpot Megabucks simulation on desktop watching memory; fix any UI overflow on narrow (phone) widths using the shared layout (no per-platform screens).
- `README.md`: what Lotz is, module map, how to run each target (exact gradle/xcodebuild commands), simulation model summary linking `docs/game-rules.md` + `docs/market-model.md`, seed/reproducibility note.
- Final: `./gradlew build` from clean (documented iOS-link exclusions permitted), desktop + wasm launched manually, android `assembleDebug`, iOS simulator build.

**Exit criteria:** all four targets verifiably build; desktop and wasm manually launched; README instructions actually reproduce those launches; clean-tree build green.

**Commit:** `feat: platform entry points and release-ready docs for all targets`

---

## Resume protocol

- **One chunk per session.** Start each chunk in a fresh session; the chunk's start prompt is the whole context. On Pro, budget one chunk per session window.
- **First action:** verify entry state — `git log --oneline` shows the previous chunk's commit; expected files exist; `./gradlew build` (or the chunk's stated check) is green. On mismatch: finish/repair the previous chunk first; never start N+1 on an incomplete N.
- **Interrupted mid-chunk:** commit WIP anyway with a `NEXT:` comment at the top of the file being worked on stating exactly what remains; the next session's first move is `grep -rn "NEXT:" --include="*.kt" --include="*.md" .`
- **Decisions are files.** Anything learned or decided mid-chunk that later chunks need goes into `docs/` or code comments before the session ends — never rely on conversational memory.
- **The plan is a living checkpoint.** If execution proves the plan wrong, update `docs/build-plan.md` in the same commit and note the drift (see `use-case-kmp/docs/build-plan.md` for the format).
- **Last action:** run exit criteria, commit with the named message, stop — don't drift into the next chunk.

## Verification (overall)

- Per-chunk exit criteria are the test plan; they are cumulative (`./gradlew build` + tests stay green at every checkpoint).
- End-to-end acceptance after Chunk 8: on desktop, (1) run "Powerball, 2 entries/drawing, Power Play, $5,000 budget" and see a plausible losing timeline; (2) run a 1,000× batch of the same and see probability-of-profit and loss-per-dollar stats; (3) run "Megabucks until jackpot" and watch it grind to a jackpot with rolling-jackpot resets from co-player wins along the way; then repeat (1) with the same seed and confirm identical results.
