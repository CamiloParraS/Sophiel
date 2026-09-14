# CLAUDE.md

Agent entrypoint for **Sophiel**. Read this fully, then read `./docs/SPEC.md`.

`SPEC.md` is the source of truth. This file is a summary and a pointer — where the two disagree, `SPEC.md` wins.

---

## Current state

> **Update this block at the end of every work session. It is the first thing you and I both read.**

```
CURRENT MILESTONE:  M2 — Pipeline and Test Feed
STATUS:             code complete, verified on-device — V1-V4 pass, V5 only proxy-checked.
                    MODEL SWAPPED 2026-09-14 (docs/DECISIONS.md D12): GantMan MobileNetV2
                    5-class float tflite replaces D9's AutoML model; score = hentai+porn+sexy.
                    M1 re-verified: V1 17.36 MB PASS · V3 ParityTest on SM-S721B PASS (expected
                    _logits.json regenerated) · :safecore:test PASS · installDebug OK · aapt: no
                    INTERNET. M2.V4 NOT re-checked with the new model, and SkinGate minRatio is
                    locally 0.0f (gate effectively off, uncommitted human experiment) — restore
                    0.05f and re-run V4 before closing M2.
                    PIPELINE FIX 2026-09-14: DetectionPipeline cached post-policy severity and
                    skipped PolicyEngine on cache hits / gated frames, so a static explicit
                    screen stuck at SUGGESTIVE forever. Now policy runs on every frame. New
                    androidTest PipelineHysteresisTest: FAILS on old code (EXPLICIT expected,
                    SUGGESTIVE actual), PASSES on new; ParityTest PASS; both on Device A (A71).
                    Test feed note: tiles share one PolicyEngine, so hysteresis carries across
                    unrelated tiles (score≈0 can read EXPLICIT for ≤2 tiles after explicit ones)
                    — expected for a frame stream, not a bug.
                    SCORE 2026-09-14: hentai + porn + 0.5*sexy (D12, SEXY_WEIGHT knob);
                    expected_logits regenerated; ParityTest PASS on both devices.
                    V4 re-checked by human with minRatio back at 0.05f: settings screenshot
                    gated=true. PASS. (Human noted some dim-lit images also get gated — logged
                    in D12 for M5.)
                    V5 AUTOMATED EVIDENCE 2026-09-14: androidTest DetectorLeakTest, 5 create/
                    analyze/close cycles vs a pre-create native-heap baseline. Proven sensitive:
                    with close() removed it FAILS (+35.5 MB over baseline on A71). With close():
                    A71 baseline 7012 KB → cycles 7995–8711 KB; S721B 6957 → 6986 KB flat;
                    threads 13 constant on both. PASS on both devices. RE-RUN 2026-09-14
                    ~10:45/10:51 UTC after SEXY_WEIGHT 0.5 (human, logcat `Sophiel:I V5`):
                    Device B baseline 6929 KB → cycles 6959/6960×5 flat, threads 13 PASS;
                    Device A baseline 6931 KB → cycles 8869/9503/8881/8228/9125 KB
                    (worst +2572 KB < 8192 KB bound), threads 13 PASS. No monotonic growth.
                    SPEC wording still says "via Android Studio Profiler" — human decides
                    whether this test closes V5 or a Profiler pass is still wanted.
                    PROFILER PASS 2026-09-14 (human, Device A, docs/Tests/M2 V5 Tests Device A, last
                    run): native 6.5 MB → 422 MB (open 1) → 809 MB (open 2) → flat ~809 MB for
                    cycles 3-6 (brief ~668 MB dip on each reopen). Human read it as a fail.
                    Diagnosis: NOT a Detector leak — testFeedScreen decoded the 56 bundled PNGs
                    at full resolution (up to 4096 px) = 385.6 MB of native bitmap memory per
                    screen open; GC frees the previous set lazily, so the plateau is one stale
                    set + one live set. Bounded, no growth after open 2.
                    FIX: testFeedScreen decodes with power-of-two inSampleSize keeping short side
                    ≥ 360 px (SPEC §4.6 capture size) → 71.1 MB per set. installDebug OK, aapt no
                    INTERNET. Re-measured via adb + dumpsys meminfo (Native Heap Alloc, NOT
                    Profiler) on Device A, 5 cycles: baseline 11 → feed 121 → 201 → 201/202/201;
                    Protection 92-93 MB flat. Same bounded shape, ~4x smaller. Caveat: the
                    before-fix numbers are the human's Profiler readings, not this adb script,
                    so "4x" compares two tools.
                    PROFILER PASS 2 2026-09-14 (human, Device A, fixed build, docs/Tests/M2 V5
                    Test2 Device A/results.md): native 6.3 MB → 116 MB (open 1) → ~199 MB (open
                    2) → flat ~198-199 MB through cycle 8+ (total ~343 MB); ~1 s dip / ~0.5 s
                    spike on each reopen; no difference Protection vs TestFeed; screen off →
                    back to cycle-1 level. Matches the adb re-measure (121 → 201). Bounded
                    plateau, no growth over 8 cycles = no leak; plateau is one live + one
                    not-yet-collected image set/Detector, reclaimed when GC runs.
                    PROFILER PASS Device B 2026-09-14 (human): same bounded plateau, upward
                    GC-marked spikes on each reopen instead of dips (faster decode/model init
                    lands before GC frees the old set). PASS.
                    V5 VERDICT: agent recommends PASS (Profiler per SPEC + DetectorLeakTest);
                    awaiting human confirmation before marking M2 closed.
                    FINAL M2 CHECK 2026-09-14 (current working tree, incl. human's
                    testFeedScreen rename): :safecore:test all pass; connectedAndroidTest
                    (Parity, PipelineHysteresis, DetectorLeak) PASS on A71 + S721B;
                    installDebug OK on both; aapt no INTERNET. V3 re-verified on Device A via
                    adb scroll: fixture_00 cacheHit=false on open → cacheHit=true, 1 ms after
                    scroll-to-bottom-and-back. PASS. → M2 ready to close; M3 next.
                    GIT HYGIENE before committing: `docs/M2 V5 Tests Device A/*` (5 PNG + md)
                    are STAGED in the index though moved on disk — unstage (hard rule 4).
                    `live-view-*.asdb` (Profiler capture) untracked in repo root — don't
                    commit. safecore/.gitignore's `/docs/M2 V5 Tests Device A*` line is a no-op
                    (path relative to safecore/). HEAD 336c6aa changed SEXY_WEIGHT without its
                    parity reference/logits/test — those are still uncommitted.
                    Side note: the bottom nav bar sits under the system navigation bar (only
                    16 px of the buttons exposed on the A71) — separate UI bug, not fixed here.
LAST VERIFIED:      2026-09-13 · Verified on a connected Samsung SM-S721B (API 36). Concrete
                    Device A/B models are still unassigned in docs/DECISIONS.md ("to be
                    filled in by the human before M3") — this was the only device attached,
                    treated as Device A for this session, human should confirm/update
                    DECISIONS.md before M3.
                    V1 — aapt dump permissions on rebuilt debug APK: no INTERNET. PASS.
                    V2 — testFeedScreen renders, every tile shows a verdict line. PASS.
                    V3 — FAILED initially: scrolling fixture_00 off/back on-screen never
                    re-ran its LaunchedEffect (cacheHit stayed false), because the original
                    10 short tiles barely exceeded one viewport's height, so Compose never
                    disposed/recomposed the item — confirmed via a temporary Log.d instrumented
                    in LaunchedEffect (removed after diagnosis). Fixed in testFeedScreen.kt by
                    repeating the bundled 10 fixtures REPEAT_COUNT=4 times (40 tiles, same
                    Bitmap objects reused, no new image content) so a real scroll-away/back
                    forces disposal. Re-verified: fixture_00 shows cacheHit=true, 0ms after
                    fling-to-bottom-and-back. PASS.
                    V4 — none of the 10 synthetic fixtures resembled a real UI screenshot, so
                    V4 (skin gate rejects a plain settings screen) had nothing to test against.
                    Human supplied two real device screenshots (Settings home, phone home
                    screen, no name/face) — added as app/src/main/assets/testfeed/zz_*.png,
                    gitignored (not committed — see .gitignore comment; these are real
                    captured content, not synthetic parity fixtures like fixture_00-09, so a
                    fresh clone won't have them and would need the human to re-supply them).
                    Confirmed zz_settings_screenshot.png → gated=true, score=0.00, classifier
                    never invoked. Bonus: zz_homescreen_screenshot.png (has a skin-toned photo
                    in a media widget) → gated=false, score=0.02 — gate discriminates rather
                    than blanket-rejecting screenshots. PASS.
                    V5 — NOT fully verified. No Android Studio Profiler session available
                    headlessly; instead cycled TestFeed↔Protection 5x (each cycle disposes and
                    recreates the Detector via DisposableEffect) and watched `adb shell dumpsys
                    meminfo`: native heap stabilized ~71-72MB after cycle 1 (no monotonic
                    growth across cycles 2-5), TOTAL PSS trended down, no exceptions in
                    logcat. This is a proxy signal only, not the Profiler-based leak check
                    SPEC.md M2.V5 asks for — human must still run the real Profiler pass
                    across 5 cycles before M2 is fully closed.
BLOCKED ON:         Human to (1) run the real Android Studio Profiler check for V5 across 5
                    close()/recreate cycles, (2) decide whether to commit the testFeedScreen.kt
                    fix (REPEAT_COUNT) and .gitignore change now sitting uncommitted.
                    Device roles now assigned in DECISIONS.md D1 (2026-09-14): Device A =
                    Galaxy A71 (SM-A715F), Device B = SM-S721B. The 2026-09-13 notes below
                    that say "treated as Device A" were actually run on Device B.
NEXT:               Close out M2.V5 on-device via Profiler, then M3 — Capture
                    (ProjectionController, ProjectionService, FrameSource; SPEC.md §5 M3).
```

Milestones are **strictly sequential**. Do not start M(n+1) until every verification item in M(n) passes. If you believe a milestone should be skipped or reordered, stop and ask.

---

## Before you write code

1. Read `SPEC.md` §0 (operating rules) and the section for the current milestone.
2. Read `docs/DECISIONS.md` — it records why things are the way they are. Do not relitigate a settled decision without asking.
3. If the task touches permissions, capture, or the overlay, read `SPEC.md` §4 in full first. That section is the highest-risk area of the project and it is written to be followed literally.

---

## Hard rules

These are absolute. Violating any of them is a defect, even if the code works.

| #   | Rule                                                                                                                           |
| --- | ------------------------------------------------------------------------------------------------------------------------------ |
| 1   | **No `INTERNET` permission**, in any manifest, including debug and test. This is a verifiable product claim, not a preference. |
| 2   | **No model training or fine-tuning.** Pre-trained weights only.                                                                |
| 3   | **No captured frame is ever persisted.** Frames stay in memory. Only scalars (hashes, scores, timings) may be written to disk. |
| 4   | **No image data is ever committed to git.** `eval/images/` is gitignored. Commit labels and metrics, never pixels.             |
| 5   | **Kotlin only.** No Java sources. Coroutines + Flow for async; no RxJava.                                                      |
| 6   | **Every milestone ends buildable.** `./gradlew :app:installDebug` must produce a launchable app before you stop working.       |
| 7   | **`:safecore` stays UI-free.** It must not reference `MediaProjection`, `WindowManager`, or any Compose symbol.                |
| 8   | **No features outside `SPEC.md` §1.2.** If it isn't in scope, it isn't in scope. §1.3 lists what to refuse.                    |

---

## Stop and ask the human

Do not improvise around these:

- **The M1 parity gate fails** — on-device model output doesn't match the Python reference within `1e-2`. Do not add a fudge factor, do not lower the tolerance, do not proceed to M2. The cause is almost always `Preprocessor.kt`; see `SPEC.md` M1 for the diagnostic order.
- A permission flow fails on a physical device in a way `SPEC.md` §4 doesn't cover.
- A milestone exceeds **2× its stated time budget**.
- You want to add something not in scope, or remove something that is.
- Dependency resolution has been fighting you for **more than 30 minutes**.

Stating a blocker clearly is a successful outcome. Silently working around one is not.

---

## Device roles

Do not swap these. They're recorded in `docs/DECISIONS.md`.

- **Device A (budget)** — primary development device. Default target for `installDebug`. Performance problems must surface here first.
- **Device B (flagship)** — demo and headline numbers only.

Both devices appear in the M5 success criteria at different thresholds. See `SPEC.md` §1.4.

---

## Commands

```bash
# Build + install (default: Device A)
./gradlew :app:installDebug

# Verify the no-INTERNET claim — MUST return nothing
aapt dump permissions app/build/outputs/apk/debug/app-debug.apk | grep -i internet

# Tests
./gradlew :safecore:test              # JVM: gate, hash, policy
./gradlew :safecore:connectedAndroidTest   # device: M1 parity gate

# Diagnostics
adb logcat -s Sophiel:D
adb shell dumpsys media_projection
adb shell dumpsys activity services dev.sophiel | grep -i foreground
```

Full command reference: `SPEC.md` §9.

---

## Traps that have already cost people days

Each is specified in detail in `SPEC.md`. Listed here because they fail _silently_ — no exception, no stack trace, just wrong behaviour.

- **Missing `image.close()`** → the `ImageReader` stalls permanently after exactly 2 frames. No error is thrown. If capture dies after two frames, this is why.
- **Ignoring `rowStride` padding** → a diagonally skewed bitmap that still looks like a plausible image in the debugger. §4.5.
- **Preprocessing mismatch** → plausible-looking but meaningless scores. Check channel order, normalization range, interpolation, tensor layout. §M1.
- **Starting `MediaProjection` before the foreground service** → `SecurityException` on API 34+. The ordering in §4.4 is load-bearing.
- **Recreating the `VirtualDisplay` on rotation** → `SecurityException`. Use `resize()` + `setSurface()`.
- **Reusing a consent `Intent`** → `SecurityException`. Request fresh every session; there is no "remember this choice".
- **Omitting `FLAG_NOT_FOCUSABLE`** on the overlay → steals input from every other app; the device feels bricked.
- **Compressed `.tflite` in assets** → the model can't be memory-mapped and fails to load. `noCompress += "tflite"`.

---

## Commit convention

The commit message should be structured as follows:

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

The commit contains the following structural elements, to communicate intent to the consumers of your library:

fix: a commit of the type fix patches a bug in your codebase (this correlates with PATCH in Semantic Versioning).
feat: a commit of the type feat introduces a new feature to the codebase (this correlates with MINOR in Semantic Versioning).
BREAKING CHANGE: a commit that has a footer BREAKING CHANGE:, or appends a ! after the type/scope, introduces a breaking API change (correlating with MAJOR in Semantic Versioning). A BREAKING CHANGE can be part of commits of any type.
types other than fix: and feat: are allowed, for example @commitlint/config-conventional (based on the Angular convention) recommends build:, chore:, ci:, docs:, style:, refactor:, perf:, test:, and others.
footers other than BREAKING CHANGE: <description> may be provided and follow a convention similar to git trailer format.
Additional types are not mandated by the Conventional Commits specification, and have no implicit effect in Semantic Versioning (unless they include a BREAKING CHANGE). A scope may be provided to a commit’s type, to provide additional contextual information and is contained within parenthesis, e.g., feat(parser): add ability to parse arrays.

---

## Feature freeze

**End of M4 is a hard feature freeze.** M5 is measurement, M6 is writing. After the freeze, the only acceptable code changes are bug fixes for failing verification items and instrumentation needed for benchmarks. If you find yourself adding a feature in week 5, stop.
