# CLAUDE.md

Agent entrypoint for **Sophiel**. Read this fully, then read `./docs/SPEC.md`.

`SPEC.md` is the source of truth. This file is a summary and a pointer — where the two disagree, `SPEC.md` wins.

---

## Current state

> **Update this block at the end of every work session. It is the first thing you and I both read.**

```
CURRENT MILESTONE:  M3 — Capture — CLOSED 2026-09-14
STATUS:             code complete on branch feat/capture (off main, which has M2 merged).
                    ALL SEVEN VERIFICATION ITEMS (V1-V7) VERIFIED PASS, across two sessions and
                    both devices (Device A = Galaxy A71/API 33; Device B = SM-S721B). Full
                    narrative in DECISIONS.md D17 (V1-V6, plus the recheckOverlay infinite-loop
                    and notification-Stop state-sync bugs found and fixed) and D18 (the debug
                    pill, its own threading crash and fix, the real V7 bug and fix, and the
                    screen-off fix) — not repeated in full here.
                    Built: ProjectionStateMachine.kt (pure §4.4 reducer) + ProjectionController
                    (Effects interface for Android side effects, attached/cleared with
                    MainActivity's lifecycle) + FrameThrottle (80ms floor) + BlackFrameDetector
                    (FLAG_SECURE detection — see below, this needed a real fix) + FrameSource
                    (ImageReader, rowStride crop, always closes Image) + ProjectionService
                    (startForeground before getMediaProjection, onStop->teardown,
                    onConfigurationChanged does resize()+setSurface() not recreate, a
                    BigTextStyle notification with a "Stop" action, an ACTION_SCREEN_OFF
                    receiver for clean teardown) + DebugPillOverlay (human-approved, D15/D18).
                    Introduced AppContainer.kt + SophielApp.kt (D14) for the first Activity<->
                    Service shared state M0-M2 never needed.
                    TESTS FIRST (TDD, per user request): ProjectionStateMachineTest (12 cases),
                    FrameThrottleTest, BlackFrameDetectorTest (updated when the tolerance fix
                    landed — see D18) — all written before their implementations.
                    KEY BUG, found via the debug pill (D18): BlackFrameDetector required every
                    single captured pixel to be exactly black, which can never be literally true
                    on a real device — the system status bar (and, while testing, the debug pill
                    itself) is always part of a full-display capture and is never black. Fixed
                    to "≥90% of pixels are black". This was the actual reason V7 looked
                    inconclusive in the first pass, and re-testing against a real banking app
                    (Bancolombia, Device B) confirms it now: Log shows "protected content
                    (all-black frame, likely FLAG_SECURE)" and the pill shows "PROTECTED — not
                    analyzable". Likely also explains the human's earlier "it closes the screen
                    projection and it stops working" report — the frozen score=0.00 forever
                    (old bug) reads exactly like "stopped" even though the session was alive the
                    whole time; confirmed the session now stays healthy throughout.
                    SCREEN-OFF FIX (D18): turning the screen off mid-session left the controller
                    possibly stuck believing it was RUNNING with a dead pipeline underneath
                    ("doesn't restart when sharing screen again", human report). Fixed
                    proactively: ProjectionService now tears down on ACTION_SCREEN_OFF rather
                    than trusting MediaProjection.Callback.onStop() to fire reliably for this
                    case. Verified end-to-end on Device B: screen off -> clean teardown -> screen
                    on -> IDLE -> Start protection shows a fresh consent dialog -> RUNNING again.
                    PLATFORM FINDING, not a bug: MediaProjection only pushes a frame when the
                    compositor redraws something — a static screen produces zero new frames,
                    which looks like (but is not) a stalled ImageReader. Matters for any future
                    soak test: "zero new frames" alone doesn't mean stalled.
                    VERIFIED: `:app:testDebugUnitTest` + `:safecore:test` pass; `:app:assembleDebug`
                    succeeds; `aapt dump permissions` — no INTERNET; V1-V7 all PASS on-device.
M2 CLOSED:          2026-09-14, confirmed by the human. All V1-V5 passed on both devices
                    (Device A = Galaxy A71, Device B = SM-S721B, per DECISIONS.md D1); model is
                    GantMan MobileNetV2 (D12), score = hentai+porn+0.5*sexy. Full history
                    (model swap, the DetectionPipeline hysteresis fix, the V5 Profiler saga,
                    git-hygiene notes) lives in prior commits' CLAUDE.md revisions and
                    DECISIONS.md D9-D13 — not repeated here.
POST-M3 (D19):      2026-09-15 hardening on feat/capture.
                    - Capture fixes committed (0672eb3, d68f96e): ImageReader and interpreter
                      now close on their owning threads (the real SIGSEGV), plus
                      decide-before-decode, one-frame-in-flight, and the system-bar crop.
                    - Skin gate (uncommitted): luma floor Y>=40 only. A Cr-Cb>=20 chroma guard
                      was tried and REVERTED because it gated 9 explicit test-feed images; real
                      skin sits at Cr-Cb 8-15.
                    - SPEC.md §4.2/§4.5/§4.6/§6.1/§8 and M4 (mask feedback loop) updated.
                    - Verified on Device B: test feed ungated again, crop non-zero, stress runs
                      with no crash, screen-off mid-inference tears down cleanly, restart
                      reaches RUNNING.
                    - M3.V6 soak re-run by the human on Device B (about 11 min of Twitter
                      scrolling, Profiler): memory flat at about 130 MB with no drift, no lag,
                      battery -3%. PASS.
                    - Not yet re-run: rotation, anything on Device A.
POST-M3 (D20):      2026-09-15 code-quality pass (uncommitted). Fixed controller stuck in
                    ACQUIRING_PROJECTION on a failed start (TeardownComplete now resets from any
                    phase). Effects run on phase entry. CaptureSession.kt extracted from
                    ProjectionService. Black-frame check on a 64x64 probe. Unit tests + build
                    pass; NOT re-verified on-device.
BLOCKED ON:         On-device re-run of M3 V1-V7 after D20 (no device was attached). Human should
                    also skim DECISIONS.md D17/D18/D20 before M4 work lands on top.
NEXT:               M4 — Overlay (MaskView, OverlayController, tap-to-reveal, threshold slider;
                    SPEC.md §5 M4). Feature freeze at end of M4. Note DebugPillOverlay already
                    exists (debug-only) — M4's MaskView is the real, always-on masking
                    intervention; don't conflate the two or let the pill's existence shortcut
                    M4's actual scope.
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
