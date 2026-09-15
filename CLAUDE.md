# CLAUDE.md

Agent entrypoint for **Sophiel**. Read this fully, then read `./docs/SPEC.md`.

`SPEC.md` is the source of truth. This file is a summary and a pointer — where the two disagree, `SPEC.md` wins.

---

## Current state

> **Update this block at the end of every work session. It is the first thing you and I both read.**

```
CURRENT MILESTONE:  M3 — Capture
STATUS:             code complete on branch feat/capture (off main, which has M2 merged).
                    V1-V6 VERIFIED PASS on Device A (Galaxy A71, Android 13/API 33) this
                    session, via adb (uiautomator dump + input tap, no visual monitor). V7
                    INCONCLUSIVE — M3 is NOT closeable yet; see below.
                    Built: ProjectionStateMachine.kt (pure §4.4 reducer) + ProjectionController
                    (stateful wrapper, Effects interface for Android side effects, attached by
                    MainActivity in onStart/cleared in onStop) + FrameThrottle (80ms floor) +
                    BlackFrameDetector (FLAG_SECURE all-black check) + FrameSource (ImageReader,
                    rowStride crop, always closes Image) + ProjectionService (rewritten from the
                    M0 stub: startForeground(..., FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                    before getMediaProjection(), MediaProjection.Callback.onStop wired to
                    teardown, onConfigurationChanged does resize()+setSurface() not recreate,
                    notification shows live verdict+score via BigTextStyle, Log.d(Sophiel, ...)
                    per frame, a "Stop" notification action added this session — see below).
                    Introduced AppContainer.kt + SophielApp.kt (DECISIONS.md D14) — first time
                    M0-M2 didn't need cross-component (Activity+Service) shared state.
                    MainActivity's Protection destination is no longer a stub: Start/Stop button
                    driven by controller.state, wires the POST_NOTIFICATIONS and consent
                    ActivityResultLaunchers.
                    TESTS FIRST (TDD, per user request): app/src/test/kotlin/dev/sophiel/capture/
                    ProjectionStateMachineTest (12 cases: granted/denied/cancelled/blocked/retry/
                    happy-path/no-op), FrameThrottleTest, BlackFrameDetectorTest — all written
                    before their implementations, all pass. app had no src/test before this.
                    TWO BUGS FOUND AND FIXED ON-DEVICE (DECISIONS.md D17 has full detail):
                    (1) ProjectionController.recheckOverlay() re-opened the overlay Settings
                    screen on every call while ungranted, including the post-Settings resume
                    check — an infinite loop that never reached BLOCKED (SPEC.md M3.V2). Split
                    into a one-time "open Settings" side effect (onNotificationsResult) and a
                    pure recheck (recheckOverlay, onStart-only, never opens Settings). (2) The
                    notification's new "Stop" action (added this session — no other stop-from-
                    status-bar control exists on this OS/device; see D17) called teardown()
                    without first telling the controller, leaving ControllerState.phase stuck at
                    RUNNING forever if the Activity wasn't alive to have called stop() first.
                    Fixed by calling controller.onProjectionStopped() before teardown() in the
                    ACTION_STOP handler, same as the external-revoke path already did.
                    VERIFIED THIS SESSION: `:app:testDebugUnitTest` + `:safecore:test` pass;
                    `:app:assembleDebug` succeeds; `aapt dump permissions` — no INTERNET.
                    V1 (cold permission flow) PASS. V2 (deny overlay -> BLOCKED, "Retry" recovers)
                    PASS after the fix above. V3 (cancel consent -> IDLE) PASS. V4 (rotation, no
                    SecurityException, frames resume after rotating back) PASS. V5 (stop via the
                    notification's new Stop action while the Activity wasn't foregrounded ->
                    dumpsys media_projection empty, service gone, controller reaches IDLE) PASS
                    after the fix above. V6 (10-minute soak, screen kept changing via scripted
                    swipes so frames actually flowed — see the redraw-driven finding below): 2946
                    frames logged, zero exceptions/crashes, service foreground throughout PASS.
                    V7 (FLAG_SECURE -> "protected content") INCONCLUSIVE: Chrome Incognito is
                    confirmed FLAG_SECURE externally (adb screencap returns 0 bytes on it) but
                    our own capture kept logging ordinary SAFE/score=0.0, never "protected
                    content" — likely because Chrome only blackens the WebView surface, not the
                    whole window, so BlackFrameDetector's strict all-black check doesn't fire.
                    No real full-window-secure app (a banking app, per SPEC's own example) was
                    available to test cleanly; Samsung Pass and Netflix didn't expose one either
                    without deeper setup. Do not mark V7 PASS on the Incognito result.
                    PLATFORM FINDING, not a bug: MediaProjection only pushes a frame when the
                    compositor redraws something — a static screen for ~30s produced zero new
                    frames and looked exactly like SPEC §4.5's "stalled ImageReader" trap, but
                    resumed instantly on the next swipe. image.close() is called unconditionally
                    in FrameSource's finally block either way (verified in code). Matters for any
                    future soak test: "zero new frames" alone doesn't mean stalled.
                    DEFERRED (DECISIONS.md D15): human asked for a debug overlay "pill" showing
                    live verdicts, to evaluate after other M3 work. Still not built (SPEC.md's
                    M3/M4 separation); would have made V7 diagnosable by showing what was
                    actually captured. Revisit with the human now that V7 is the concrete reason
                    it would help.
M2 CLOSED:          2026-09-14, confirmed by the human. All V1-V5 passed on both devices
                    (Device A = Galaxy A71, Device B = SM-S721B, per DECISIONS.md D1); model is
                    GantMan MobileNetV2 (D12), score = hentai+porn+0.5*sexy. Full history
                    (model swap, the DetectionPipeline hysteresis fix, the V5 Profiler saga,
                    git-hygiene notes) lives in prior commits' CLAUDE.md revisions and
                    DECISIONS.md D9-D13 — not repeated here.
BLOCKED ON:         Human decision on V7: either supply/point at a real full-window-secure app
                    (a banking app, per SPEC's own example — Chrome Incognito and Samsung Pass
                    didn't work cleanly, see STATUS) to test against, or greenlight building the
                    deferred debug pill (DECISIONS.md D15) so the actual captured frame is
                    visible for diagnosis. Per CLAUDE.md's own hard rule (C4 / milestone
                    sequencing), M3 should NOT be marked closed until V7 has a real pass, not an
                    inconclusive result — this is a judgment call for the human, not something to
                    wave through.
NEXT:               Resolve V7 with the human, then M4 — Overlay (MaskView, OverlayController,
                    tap-to-reveal, threshold slider; SPEC.md §5 M4). Feature freeze at end of M4.
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
