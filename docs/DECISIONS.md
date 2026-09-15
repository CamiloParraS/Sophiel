# DECISIONS.md

Running log of decisions: what was chosen, what was rejected, and why. Do not
relitigate a settled entry without asking the human.

---

## D1 — Device roles (SPEC.md §1.4, CLAUDE.md)

- **Device A (budget)** — primary development device. Default target for
  `./gradlew :app:installDebug`. Performance problems must surface here first.
- **Device B (flagship)** — demo and headline numbers only.

Roles are fixed for the life of the project. Do not swap them.

| Role                | Model              | Notes                                                                                                                               |
| ------------------- | ------------------ | ----------------------------------------------------------------------------------------------------------------------------------- |
| Device A (budget)   | Samsung Galaxy A71 | Older mid-range. Measured ~20–30 ms slower per classification than Device B with D12's model (human, 2026-09-14, test feed).        |
| Device B (flagship) | Samsung SM-S721B   | API 36. Sessions up to 2026-09-13 used it as the only attached device (logged as "Device A" in CLAUDE.md before this was assigned). |

---

## D2 — No Hilt (SPEC.md §2.4)

Two modules and one pipeline object do not justify a DI framework or its build
cost. Use constructor injection and a single hand-rolled `AppContainer`.

---

## D3 — No NNAPI delegate (SPEC.md §2.4)

NNAPI was deprecated in Android 15. Use LiteRT's CPU path. Add the GPU delegate
only if M5 benchmarks show CPU missing the latency target (SPEC.md §1.4).

---

## D4 — No Compose inside the overlay window (SPEC.md §2.4)

Overlay `ComposeView`s require manually attaching `ViewTreeLifecycleOwner` and
`SavedStateRegistryOwner` — a reliable source of lost days. The overlay
(`MaskView`, M4) is a plain `View` subclass drawing on a `Canvas`. Compose is for
the normal in-app UI only.

---

## D5 — One NSFW model, not two (SPEC.md §6.8)

The reference project shipped two models (~26 MB). We ship one. Halves the
conversion risk, which is the M1 critical path.

---

## D6 — Toolchain versions deviate from SPEC.md §2.3

SPEC.md §2.3 lists a known-good version set (AGP 8.7.3, Kotlin 2.0.21,
compileSdk 36, composeBom 2024.12.01) and explicitly permits deviation:
_"These versions are a known-good starting set, not gospel... record the
resulting versions here."_

The repo was scaffolded by a current Android Studio with a newer, mutually
consistent toolchain that already syncs and builds on the dev machine.
Downgrading to the §2.3 set would be net new risk for no benefit. Kept as-is:

| Item       | SPEC.md §2.3  | Actual (this repo) |
| ---------- | ------------- | ------------------ |
| AGP        | 8.7.3         | 9.3.2              |
| Gradle     | (unspecified) | 9.5.0              |
| Kotlin     | 2.0.21        | 2.2.10             |
| compileSdk | 36            | 37                 |
| targetSdk  | 36            | 37                 |
| composeBom | 2024.12.01    | 2026.02.01         |

Unchanged from SPEC: **`minSdk = 26`** (hard floor — `TYPE_APPLICATION_OVERLAY`
requires API 26).

LiteRT / Room / DataStore versions will be pinned when M1/M2/M4 introduce them.

---

## D7 — Package / application ID is `dev.sophiel` (SPEC.md §2.1)

The Studio scaffold used `com.example.sophiel`. Renamed to `dev.sophiel` in
M0 (cheapest point to do it) to match SPEC.md §2.1, §3.2, and every `adb` command
in SPEC.md §9 / CLAUDE.md. The git repo directory remains `sophiel`; the
Gradle `rootProject.name` remains `sophiel`. App display name: **Sophiel**.

---

## D8 — `:safecore` API contract stubbed in M0 (SPEC.md §3.4)

`Detector` / `Verdict` / `Severity` / `DetectorFactory` are committed in M0 as
the frozen public surface. `DetectorFactory.create` is `TODO()` until M2.

---

## D9 — Model: fallback pre-converted `.tflite`, not our own conversion (SPEC.md M1)

> **Superseded by D12** (2026-09-14). Kept for history.

SPEC.md M1's primary path (`TFLiteConverter.from_saved_model()` against
GantMan/nsfw_model's Keras checkpoint) needs TensorFlow in Python. No
`tensorflow` / `tensorflow-cpu` wheel is available for the Python 3.14
install on the development machine, and bootstrapping a second Python
(3.12, via msys64) far enough to install TensorFlow was not attempted
further once `pip` itself failed to bootstrap there — this is exactly the
"dependency resolution fighting you" situation SPEC.md tells us not to
burn 30 minutes on.

Took SPEC.md M1's own documented fallback instead: **a pre-converted
`.tflite` from an existing open-source Android NSFW project.**

- **Source:** `nipunru/nsfw-detector-android` (MIT), commit
  `d67bea108ce995b8090fd71c446627a2b5c7c13e`,
  `nsfwdetector/src/main/assets/automl/NSFW.tflite`. A Firebase AutoML
  Vision Edge export, 2-class (`nonnude`, `nude`).
- **Fetched and pinned by** `tools/convert_model.py`, which downloads the
  exact commit-pinned file and verifies it against a recorded SHA-256
  before installing it as `safecore/src/main/assets/nsfw.tflite`. No
  training or fine-tuning happened — pre-trained weights only (C2).
- **Size:** 5,855,200 bytes — within SPEC.md M1.V1's 3–30 MB band.
- **Input tensor:** `uint8 [1,224,224,3]`, quantization `scale=0.00787402,
zero_point=128`. This is a full-integer-quantized model, so
  `Preprocessor.kt` feeds raw 0–255 RGB pixel bytes directly — no float
  normalization step. (SPEC.md's "do not attempt full-integer
  quantization" warning is about calibration work _we_ would have to do;
  this model arrived already quantized by its original authors.)
- **Output tensor:** `uint8 [1,2]`, quantization `scale=0.00390625,
zero_point=0`, ordered `[nonnude, nude]`. `NsfwClassifier.classify()`
  returns the dequantized `nude` probability as the unsafe score in
  `Verdict.score`'s `[0,1]` range.
- **Channel order (RGB, not BGR)** is an assumption carried over from the
  model's Keras/TF training convention, not independently verified here.
  Because M1's parity gate only checks that the on-device interpreter
  agrees with `tools/reference_infer.py` — both of which share this
  project's own preprocessing code — a wrong channel-order assumption
  would still pass parity; it would only show up as poor accuracy in M5's
  evaluation. Revisit there if recall is suspiciously low.
- **Resize interpolation is not exercised by the parity gate.** The 10
  fixtures in `safecore/src/androidTest/assets/fixtures/` are pre-sized to
  exactly 224×224, making `Preprocessor`'s resize step a no-op for the
  parity test specifically. This isolates the parity check to channel
  order / byte layout / quantization — the actual risk area SPEC.md flags
  — instead of also gating on bilinear-vs-nearest cross-platform
  interpolation drift, which is a real but separate risk. Real capture
  frames (M3+) still go through `Bitmap.createScaledBitmap(..., filter =
true)`.
- **Fixtures are synthetic**, generated with a fixed seed (not committed
  as a repo script; see the docstring in `tools/reference_infer.py`), not
  sourced photos. This sidesteps licensing questions and JPEG-decoder
  cross-platform drift (PNG is lossless) for what is a numerical parity
  check, not an accuracy check.

---

## D10 — `org.tensorflow:tensorflow-lite`, not `com.google.ai.edge.litert` (SPEC.md §2.3)

SPEC.md's catalog suggests `com.google.ai.edge.litert:litert`. Every
version tried that ships the classic `org.tensorflow.lite.Interpreter`
compat API (1.0.0–1.4.2, 2.1.x, 2.2.0) transitively pulls
`com.google.ai.edge.litert:litert-api`, and both AARs declare the same
Android manifest `namespace` — a real upstream packaging bug that fails
`processDebugMainManifest` on AGP 9.3.2. The only litert line that avoids
it (2.0.x) does so by dropping the classic `Interpreter` API entirely in
favor of a newer `CompiledModel` / `TensorBuffer` Kotlin API with no
migration guide at the time of writing.

Switched to `org.tensorflow:tensorflow-lite:2.17.0` — the mature, single-
artifact upstream library LiteRT is meant to eventually replace. It
exposes the identical `org.tensorflow.lite.Interpreter` API, needs no
manifest workarounds, and `:app:assembleDebug` succeeds with it. Revisit
if a future litert release fixes the namespace collision and a concrete
reason to move (GPU delegate, smaller binary) shows up.

---

## D11 — `PolicyEngine`'s `SUGGESTIVE` threshold defaults to half of `EXPLICIT` (SPEC.md M2)

SPEC.md §3.4's `DetectorFactory.create` only takes one `threshold` (the
`EXPLICIT` cutoff); §6.2's hysteresis rule ("2 consecutive frames above
threshold to engage, 3 below to release") is likewise written in terms of
a single threshold and only governs the `EXPLICIT` engage/release
transition — it says nothing about where `SUGGESTIVE` begins. `Severity`
still has three ordinals, so `PolicyEngine` needs a second, lower cutoff.

Defaulted `suggestiveThreshold` to `explicitThreshold / 2f` (e.g. 0.35 at
the default 0.70 explicit threshold), with no hysteresis on the
`SUGGESTIVE` boundary itself — only `EXPLICIT` engage/release is
debounced, since that's the only transition SPEC.md describes as
strobe-prone (it's also the only one that will eventually drive the M4
overlay). Both thresholds are constructor parameters, so this is a
one-line change if M5's evaluation against the UI corpus (§7.3) suggests
a different split.

---

## D12 — Model: GantMan/nsfw_model MobileNetV2, replacing D9's AutoML model (SPEC.md M1)

On-device testing in M2 showed D9's 2-class AutoML model (`nonnude`/`nude`)
misbehaving on real content: a non-explicit 3D render scored highest, and a
more explicit variant of the same image scored lower than the milder one.
A binary "nude photo" classifier isn't a severity scale, and it has no
notion of renders or drawings.

Candidates considered:

| Model                              | Rejected / chosen because                                                                                                                                                             |
| ---------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **GantMan/nsfw_model MobileNetV2** | **Chosen.** SPEC.md M1's primary candidate. 5 classes incl. `drawings`/`hentai`/`sexy`, so renders and non-nude NSFW are in-distribution. Same TFLite runtime.                        |
| OpenNSFW2 (bhky)                   | ResNet-50 (~24M params, ~95 MB float); Yahoo's training set excluded drawings, so it repeats D9's render problem.                                                                     |
| Falconsai/nsfw_image_detection     | ViT-base (~86M params, ~340 MB); cannot meet SPEC §1.4's ≥4 fps on the budget device.                                                                                                 |
| NudeNet v3 (320n/640m)             | Object detector: whole-frame scoring needs box post-processing, adds ONNX Runtime, and its value is region localization — out of scope per SPEC §1.3. Offline spike only (see below). |

- **Source:** GantMan/nsfw_model (MIT) release `1.2.0`,
  `mobilenet_v2_140_224.1.zip` → `mobilenet_v2_140_224/saved_model.tflite`.
  The release already ships a converted `.tflite`, so D9's TensorFlow
  blocker no longer applies — no conversion step at all. Pre-trained weights
  only (C2).
- **Pinned by** `tools/convert_model.py`: SHA-256
  `380f98f7685f9d8a386f8cc595b6dfcb972989aae3d1b8b270d3a4a5b96fab40`.
- **Size:** 17,355,548 bytes — float32, not quantized, within M1.V1's 3–30 MB
  band. SPEC M1 prefers dynamic-range quantization; skipped because the float
  model is already in band. Revisit only if M5 latency on Device A misses.
- **Input:** `float32 [1,224,224,3]`, RGB in `[0,1]` (÷255), per GantMan's
  own `nsfw_detector/predict.py`. RGB order follows Keras `load_img`, so it's
  grounded upstream rather than assumed (unlike D9).
- **Output:** `float32 [1,5]` softmax, order from the release's
  `class_labels.txt`: `drawings, hentai, neutral, porn, sexy`.
- **Score:** `Verdict.score = hentai + porn + 0.5 * sexy` (human's call,
  2026-09-14). `sexy` counts because not all NSFW is nudity. At full weight,
  swimwear/cosplay engaged EXPLICIT; at 0.5 the human saw them drop to
  SUGGESTIVE, which they judged closer to right. Consequence to know: class
  probabilities sum to 1, so a sexy-only frame caps at 0.5 — below the 0.70
  EXPLICIT threshold — and **never engages the mask on its own**; only frames
  with hentai/porn mass do. `SEXY_WEIGHT` is a single constant in
  `NsfwClassifier` (mirrored in `tools/reference_infer.py`); M5's ROC decides
  whether 0.5 costs recall.
- **Parity fixtures** regenerated with `tools/reference_infer.py`; D9's notes
  on synthetic 224×224 fixtures and untested resize interpolation still hold.

Deferred ideas (recorded, not scheduled):

- **Skin gate vs non-nude NSFW.** Content that `sexy` catches may have little
  exposed skin and get short-circuited by `SkinGate` before the classifier
  runs. Measure in M5's ablation before touching `minRatio`.
  Human also observed (2026-09-14) the YCbCr rule sometimes gating dim /
  poorly-lit images that do contain skin — a false negative, the costly
  direction per SPEC §6.1. Include low-light images in the M5 eval set.
- **Region blur instead of full-screen blur.** Needs a detector (NudeNet-style
  boxes). Out of scope per SPEC §1.3 — candidate for a future version and the
  M6 "Future Work" section.

---

## D13 — M2.V5 re-verified after SEXY_WEIGHT 0.5 (2026-09-14, human-run)

`DetectorLeakTest.closeAndRecreateFiveTimesDoesNotLeak` (`Debug.getNativeHeapAllocatedSize`,
bound worst-minus-baseline < 8192 KB, threads stable) re-run by the human after the
D12 score change, one device at a time via
`:safecore:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=...DetectorLeakTest`:

| Device                   | Baseline | Cycles 1–5 (native KB)           | Threads | Verdict               |
| ------------------------ | -------- | -------------------------------- | ------- | --------------------- |
| B (SM-S) 10:45 UTC       | 6929     | 6959 / 6960 / 6960 / 6960 / 6960 | 13 flat | PASS (+31 KB worst)   |
| A (Galaxy A71) 10:51 UTC | 6931     | 8869 / 9503 / 8881 / 8228 / 9125 | 13 flat | PASS (+2572 KB worst) |

No monotonic growth on either device; Device A is noisier (GC/XNNPACK packing
sawtooth) but ~3× under the bound and ~14× under the proven-leaky `+35.5 MB`
signal with `close()` removed. Open question (unchanged): SPEC M2.V5 wording asks
for an Android Studio Profiler pass — human decides whether this automated
evidence closes V5.

---

## D14 — `AppContainer` / `SophielApp` introduced in M3, cross-component wiring (SPEC.md §2.4/§3.2)

SPEC.md §3.2's directory tree lists `SophielApp.kt` and `AppContainer.kt` from the
start, but M0–M2 never needed them — `testFeedScreen` just called
`DetectorFactory.create(context)` locally. M3 is the first point where two
independent Android components (`MainActivity`, `ProjectionService`) must observe
and drive the *same* `ProjectionController` (§4.4 state machine) even though the
service outlives any one Activity instance across rotation/recreation. That's a
real cross-component need, not speculative — so this is where D2's planned
container finally gets built, as a single field (`projectionController`) on a
`SophielApp : Application` reached via `(application as SophielApp).container`.

- `ProjectionController` (`app/capture/ProjectionController.kt`) wraps a pure,
  Android-type-free reducer, `ProjectionStateMachine.reduce(state, event)`
  (`app/capture/ProjectionStateMachine.kt`), so the §4.4 diagram's transitions are
  JVM-unit-testable without Robolectric. `ProjectionStateMachineTest` covers the
  granted/denied/cancelled/blocked/retry/happy-path/no-op cases.
- Android side effects (permission requests, launching the consent intent,
  starting/stopping the service) are supplied by the current Activity via a
  `ProjectionController.Effects` interface, attached in `onStart()` and cleared in
  `onStop()` — never held past an Activity instance's life, so rotation can't leak
  one.
- The `MediaProjection` consent result's `resultCode`/`data` are **not** threaded
  through the controller/reducer — they're plain fields on `MainActivity`, set by
  the consent `ActivityResultLauncher` callback and read by the
  `startCaptureService` effect when building the service `Intent`. Keeps the state
  machine's event set free of `android.content.Intent`.
- Two more pure, JVM-testable pieces followed the same reasoning:
  `FrameThrottle` (drops frames inside the 80 ms floor, SPEC.md M3) and
  `BlackFrameDetector.isAllBlack(IntArray)` (the `FLAG_SECURE` all-black check,
  SPEC.md §4.2), both operating on primitives/arrays rather than `Bitmap` so no
  Android framework dependency is needed to test them.
- Added `implementation(libs.kotlinx.coroutines.core)` to `app/build.gradle.kts` —
  `Detector.analyze()` is a suspend fun and `:app` previously had no coroutines
  dependency of its own (it only reached `DetectionPipeline` through `:safecore`'s
  public API, without needing `CoroutineScope`/`launch` itself until now).

---

## D15 — Debug overlay "pill" deferred past M3 (human request, 2026-09-14)

> **Superseded same day.** Human reviewed the tension below, approved building it anyway once
> M3's other verification was done, and it turned out to be load-bearing — see D18: it's the
> tool that found and fixed the real V7 bug. Kept for the history of the tradeoff.

While approving the M3 design, the human asked for a `TYPE_APPLICATION_OVERLAY`
debug pill showing live verdicts, "focus on the other tasks first and then
evaluate the idea." Recorded here rather than silently built or silently
dropped, because it runs directly against SPEC.md's M3 design: *"No overlay
yet — results go to the notification and Logcat... Separating capture from
overlay is deliberate. Two hard subsystems debugged at once is one subsystem
too many"* (§5 M3), and CLAUDE.md's hard rule 8 / stop-and-ask list ("you want
to add something not in scope"). M3's own notification already surfaces the
live verdict+score, which covers the "easier to see results" need without
touching `WindowManager` a milestone early. Not implemented. Revisit explicitly
with the human once M3 verification is done, rather than folding it in here.

---

## D16 — Android 15+ "sensitive content during screen share" hides other apps' notifications (discovered 2026-09-14, human device testing on Device B)

While testing M3 on-device, the human saw other apps' notifications (X,
LinkedIn) show up with unreadable/redacted content and unable to be
swipe-dismissed — only clearable by opening the app directly. Not a Sophiel
bug: this is a mandatory Android 15+ platform privacy feature ("sensitive
content protections during screen share"), which activates automatically for
*any* app actively holding a `MediaProjection` session — exactly what
`ProjectionService` does once `RUNNING`. It redacts notification content
system-wide (private messages, OTPs) and restricts dismissal specifically to
stop screen-recording/casting apps from exfiltrating that content. There is no
API for the capturing app to opt out; Android only exposes a Developer
Options toggle ("Disable screen share protections") for the human tester's own
device, which is a device setting, not something to code around. Our build's
`targetSdk 37` (D6) puts it in scope on any Android 15+ device. Relevant again
in M5 (UI-corpus / demo recording) and M6 (`docs/LIMITATIONS.md`) — note there
that a live demo showing notifications will hit this while Sophiel is running.

Separately, `buildNotification()` (`ProjectionService.kt`) already wraps the
live verdict text in `NotificationCompat.BigTextStyle` and sets
`Notification.CATEGORY_SERVICE` (both landed in the original M3 commit) so the
score/classification is visible in the collapsed notification without the
human needing to expand it by hand — this was the "app one, had to slide"
observation, unrelated to D16's OS-level redaction.

Sources: [Behavior changes: all apps | Android Developers](https://developer.android.com/about/versions/15/behavior-changes-all),
[How Does Android 15 Protect Against Screen Spying? | Guardsquare](https://www.guardsquare.com/blog/android-15-screen-spying-protection).

---

## D17 — M3 on-device verification findings (Device A, Galaxy A71, Android 13, 2026-09-14)

Ran SPEC.md M3's V1-V6 on Device A via adb (screen driven with `input tap`/`uiautomator dump`,
since no Activity was visually watched live). Two real defects found and fixed; two platform
behaviors worth recording so nobody "fixes" them again later.

**Bug: `ProjectionController.recheckOverlay()` re-opened Settings on every call, never
reaching BLOCKED.** The original single method did both "first check after notifications" and
"re-check on resume" with the same logic: if ungranted, call `requestOverlayPermission()` and
return — every time, including the resume-check. Reproduced live: denying overlay permission
sent the app back to the same Settings screen forever instead of reaching BLOCKED (SPEC.md
M3.V2). Fixed by splitting the two calls: `onNotificationsResult()` now does the one-time
"open Settings if not already granted" side effect itself; `recheckOverlay()` (called only from
`MainActivity.onStart()`) now only reads current permission state and transitions — it never
opens Settings again. Re-tested on-device: V2 (deny -> BLOCKED, with "Retry" recovering once
granted) and V3 (cancel consent -> IDLE) both pass cleanly now.

**Bug: stopping via the notification's own "Stop" action left the controller stuck at
RUNNING.** SPEC.md M3.V5 asks for "stopping from the status bar" to tear down cleanly; this
device/OS (One UI, Android 13) exposes no separate system "stop casting" chip for a
`MediaProjection` session — dumpsys `statusbar`/quick-settings had nothing — so the *only*
status-bar-reachable control is our own foreground notification. It had no stop affordance at
all, so a `NotificationCompat.Builder.addAction("Stop", ...)` (`ProjectionService.kt`,
`ACTION_STOP` PendingIntent) was added. That alone wasn't enough: `ACTION_STOP`'s handler
called `teardown()` directly without first telling the controller, so if the Activity wasn't
alive to have called `stop()` (StopRequested: RUNNING->STOPPING) first, `onTeardownComplete()`
landed on a reducer with no `STOPPING`-phase to transition out of and silently no-opped,
leaving `ControllerState.phase` wedged at RUNNING forever even though the service and
`MediaProjection` session were both genuinely gone. Fixed by having the `ACTION_STOP` handler
call `controller.onProjectionStopped()` (same call the external-revoke `MediaProjection.Callback`
path already used) before `teardown()`, so both stop paths converge. Verified: `dumpsys
media_projection` empty, service gone, controller state IDLE after tapping the notification's
Stop action with the Activity not in the foreground.

**Platform behavior, not a bug: MediaProjection only delivers a frame when the compositor
redraws something.** A ~30s stretch on a static home screen produced zero new
`DetectionPipeline` frames and looked exactly like SPEC.md §4.5's "stalled `ImageReader`" trap
(missing `image.close()`) — but resumed instantly on the next swipe/scroll. `image.close()` is
in fact called unconditionally in `FrameSource`'s `finally` block (verified in code); the
absence of frames was the OS simply not producing new buffers for unchanged content, which is
correct/efficient, not a leak. Consequence for M3.V6 and any future soak test: a "10 minutes of
continuous capture" run needs the screen actually changing throughout, or "frame count" isn't a
meaningful signal — a quiet screen looks identical to a stalled pipeline from the log alone.

**V7 (`FLAG_SECURE` -> "protected content") is inconclusive on this device, not confirmed
PASS.** Chrome Incognito is `FLAG_SECURE` (confirmed externally: `adb exec-out screencap`
returns a 0-byte file while it's frontmost, vs. a normal PNG otherwise) but our own capture
kept logging ordinary `severity=SAFE score=0.0 gated=true` — never the "protected content"
branch. Working theory: Chrome only blackens the WebView content surface, not the whole
Activity window (toolbar/tabs stay visible), so the composited frame `BlackFrameDetector` sees
isn't literally all-black — SPEC.md's assumption ("apps using FLAG_SECURE yield black frames")
describes a full-window secure Activity (its own example: "a banking app"), which is a
different case we don't have installed to test cleanly. Samsung Pass and Netflix were tried
first and didn't expose a secure surface reachable without deeper setup either. Without a real
banking-style full-window-secure app, or a way to see the actual captured frame (the deferred
D15 debug pill would help here), V7 stays unresolved — do not mark it PASS on the strength of
the Incognito test.

**V6 (10-minute soak) — PASS.** Ran 10 minutes with a scripted swipe every ~4s to keep the
compositor producing frames (per the redraw-driven finding above): 2946 `Sophiel` log lines,
zero matches for `FATAL|AndroidRuntime.*dev.sophiel|SecurityException|IllegalStateException|
OutOfMemory` across the whole run, service still `isForeground=true` at the end. Stopped
cleanly afterward via the in-app Stop button; `dumpsys media_projection` empty.

**M3 V1-V6 verified PASS on Device A (Galaxy A71, Android 13) this session; V7 inconclusive**
— see above for why, and what a clean V7 test needs (a full-window-secure app or capture
visibility).

---

## D18 — Debug pill built (human-approved D15); it found and fixed the real V7 bug

Human explicitly greenlit the D15 debug pill after the V7 write-up above, and separately
reported, from their own testing: Device A scored real content successfully in Secure Folder
and Google/Incognito searches (i.e. those aren't fully protected there); Device B's Chrome
Incognito "always returns 0"; Device B's real banking app (Bancolombia) "closes the screen
projection and it stops working"; and on both devices, turning the screen off during a session
left it unable to restart cleanly. All four leads were chased down this session, on Device B
(unlocked; Device A's lock PIN wasn't available to fully drive its UI).

**Debug pill (`DebugPillOverlay.kt`)**: a small `TYPE_APPLICATION_OVERLAY` `TextView`, shown
only while `RUNNING` and only in a debuggable build (`Context.isDebuggable`, checked via
`ApplicationInfo.FLAG_DEBUGGABLE` — no new manifest permission, reuses the already-granted
`SYSTEM_ALERT_WINDOW`). Plain View per D4. This is explicitly not the M4 `MaskView` — different
purpose (always-on diagnostic text, not a masking intervention) and gated out of any
hypothetical release build. Caught its own bug immediately: `ProjectionService.onFrame`'s
`serviceScope.launch` runs on `Dispatchers.Default` (a background thread), and the first call to
`DebugPillOverlay.update()` crashed with `CalledFromWrongThreadException` — Logcat: `FATAL
EXCEPTION: DefaultDispatcher-worker-1 ... at DebugPillOverlay.update`. Fixed by routing all
three of `show()`/`update()`/`hide()` through a `Handler(Looper.getMainLooper())` inside the
class itself, so no caller has to know or care which thread it's on.

**The real V7 bug, found once the pill made the pipeline observable: `BlackFrameDetector`'s
"every single pixel is exactly 0" check can never fire on a real device.** Pointed a genuine
FLAG_SECURE screen (Bancolombia, past its splash) at the running capture: the debug pill kept
showing ordinary `SAFE · score=0.00 · gated=true`, never "protected", even though `adb
screencap` on the same screen returned 0 bytes (confirming it actually was secured) and our own
pill (a *second*, unrelated overlay window) was visibly rendering on top of the captured area.
Two things break a whole-frame-is-pure-black check in practice: the system status bar
(icons/battery/clock — never black) is part of what a full-display `MediaProjection` capture
includes, and our own debug pill sits in the same captured frame while testing. Fixed by
changing `isAllBlack` from "all pixels are exactly 0" to "≥90% of pixels are exactly 0"
(`BLACK_FRACTION_THRESHOLD`), which tolerates the always-present status-bar strip (and the
pill) while still rejecting ordinary non-secure content. **Re-tested against the real banking
app: `Log.d` now shows `protected content (all-black frame, likely FLAG_SECURE)` repeatedly,
and the pill shows "PROTECTED — not analyzable". V7 is now a genuine PASS, not inconclusive.**
`BlackFrameDetectorTest` updated: the old "single non-black pixel breaks it" test inverted to
assert tolerance; added a "mostly non-black is still rejected" case and a "thin status-bar
strip doesn't break it" case.

This also likely explains the human's "it closes the screen projection and it stops working"
report: with the old exact-match check, a `FLAG_SECURE` screen never triggered the protected-
content branch, so the notification just froze at `score=0.00` forever — which reads exactly
like "stopped working" even though the service and session were both still alive the whole
time (confirmed: after the fix, on the identical app, the session stays healthy and
`isForeground=true` throughout — nothing actually terminates it). The human's Incognito
observation ("Device A classified images successfully", "Device B always returns 0") is
consistent with D17's standing theory (Chrome only blackens the WebView, not the whole window)
plus per-device variance in exactly how much of the frame that leaves non-black — not
re-verified further this session since the real banking app gave a cleaner, spec-literal test.

**Screen-off leaving the session unable to restart cleanly — reproduced and fixed.** Confirmed
on Device B: turning the screen off (`KEYCODE_POWER`) while `RUNNING`, the *pre-fix* build's
`MediaProjection.Callback.onStop()` did not reliably fire, and the controller could be left
believing it was still `RUNNING` with a dead capture pipeline underneath — matching "doesn't
restart when sharing screen again" (the UI would show "Stop protection" for a session that was
no longer doing anything, with no obvious way back to a fresh Start). Fixed proactively rather
than depending on uncertain OS callback timing: `ProjectionService` now registers a
`BroadcastReceiver` for `Intent.ACTION_SCREEN_OFF` in `onCreate()` and tears down immediately
on it (same `onProjectionStopped()` + `teardown()` pair as every other stop path), guaranteeing
a clean `IDLE` — and therefore a fresh, working consent flow — every time the screen turns off
mid-session. Verified on Device B: screen off → `dumpsys media_projection` empty within
seconds, service gone; screen back on → app shows `IDLE`; tapping Start protection shows a
fresh consent dialog and reaches `RUNNING` again.

**M3 V1-V7 now all verified PASS** (V1-V6 on Device A per D17, V7 and the screen-off fix on
Device B this session). M3 is ready to close.
