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

---

## D19 — Post-M3 hardening: capture crash, backpressure, bar crop, skin-gate guards (2026-09-15, human request)

Human reported random background crashes, false gate triggers on grayscale and low-light
frames, and border noise, and asked for capture optimizations. SPEC.md edits were authorised.

**Crash root cause (evidence, not theory).** `adb logcat -b crash` on Device B showed
`SIGSEGV` in `Bitmap_copyPixelsFromBuffer` on the `FrameSource` thread (twice), plus
`IllegalStateException: Image is already closed` in `FrameSource.toCroppedBitmap`.
`FrameSource.close()` closed the `ImageReader` from the main thread (teardown on screen-off,
Stop, or rotation) while the listener was mid-copy on its own thread. Fixed by posting the
close onto the reader's handler thread. The same pattern existed, unobserved, for
`Interpreter.close()` versus an in-flight `run()`. `DetectionPipeline.close()` now closes on
the inference thread via `runBlocking(dispatcher)` (blocking for at most one inference), and
a `closed` flag makes a late `analyze()` throw `CancellationException` instead of touching a
freed interpreter. Battery optimisation was never the cause.

**Backpressure.** The 80 ms throttle ran *after* each `Image` was decoded, so every
display frame (60-120/s) became a full-size bitmap copy before being dropped. There was also
no in-flight check, so analysis slower than 80 ms queued work without bound, violating
SPEC.md §4.5. `FrameSource` now asks `wantsFrame()` before decoding, and `ProjectionService`
drops frames while one is in flight. Bitmaps are recycled after analysis.

**Crop.** System bars and cutout are cropped via `WindowMetrics` insets (API 30+; no crop
below). There is no center-crop: it would discard about half a portrait screen, which costs
recall. Squash-to-224 is kept because it matches the model's Keras `load_img` training
resize. Rationale is in SPEC.md §4.6. The crop rect is logged at session start
(`capture WxH crop=Rect(...)`) — **not yet verified on-device that the insets are non-zero
from a Service context.**

**Skin gate.** First added `Y ≥ 40` and `Cr − Cb ≥ 20` to the YCbCr box. The human saw
explicit test-feed images get gated, so both rules were measured offline with a scratch
PIL script mirroring `SkinGate` (64×64 bilinear) over all 60 test-feed images. Only
per-image ratios were computed; no pixels were stored.

- **Chroma guard: wrong, removed.** It gated 9 explicit images (for example 0.27 → 0.01 and
  0.21 → 0.03). Skin pixels' `Cr − Cb` sat at p10 8–11 and p50 10–15, the same band as the
  warm gray it targeted. The hand-picked "pale skin" test pixels (spread about 32) weren't
  representative; the test now uses a spread-16 pale pixel.
- **Luma guard: kept.** On its own it pushed zero images under `minRatio`. It is unmeasured
  on low-light explicit images, which the test feed doesn't contain (see D12's low-light
  note), so M5 must include them.

Net effect: the grayscale/warm-gray false-positive complaint is **not** fixed by colour
rules. It only costs a classifier run, per SPEC.md §6.1. The known
blind spot, true B&W imagery being gated SAFE, is recorded in SPEC.md §6.1 and §8 rather
than worked around.

**On-device verification (Device B only, 2026-09-15; Device A unavailable).**

- **Test feed:** all 9 images wrongly gated by the chroma rule are `gated=false` again. The
  images still gated were already below `minRatio` under the original rule.
- **Crop:** logged as `capture 360x780 crop=Rect(0, 30 - 360, 766)` (status bar 30 px, gesture
  bar 14 px), so the insets are non-zero from a Service context.
- **Scroll stress** in the test feed: 548 frames, no crash. Every frame was gated, because
  64 dp thumbnails on white measure a skin ratio of 0.013 as captured. That comes from
  thumbnail size, not from this change.
- **Classifier stress:** synthetic skin-block PNGs, cycled full-screen in Samsung Gallery and
  deleted afterwards. 382 non-gated frames and 167 real inferences, latency p50 43 ms, p95
  50 ms, no crash, session alive. Inference is under the 80 ms throttle here, so the in-flight
  drop rarely triggers on Device B; it matters on Device A.
- **Screen off mid-classification** (the original crash scenario): no crash, session and
  service gone, process alive. On this API 36 device the OS stopped the projection first
  (`stopReason=3`), so `MediaProjection.Callback.onStop()` ran teardown before the
  `ACTION_SCREEN_OFF` receiver fired. Both paths converge. A harmless
  "Attempted to stop inactive MediaProjection" warning follows.
- **After unlock:** IDLE → Start → fresh consent → RUNNING, new crop line logged, crash buffer
  empty.
- **M3.V6 soak re-run by the human (Device B), about 11 minutes of normal use, mostly
  scrolling Twitter, with the Android Studio Profiler attached:**
  - No perceived lag.
  - After a first-minute startup spike (about 190 MB), total memory held flat around
    130 MB (native about 37 MB, Java about 29 MB, graphics about 7 MB) with a steady GC
    sawtooth and no upward drift.
  - App CPU stayed low.
  - Device got warm; battery 35% → 32% (whole-device, about 16%/h). **PASS.**
- **Not re-run:** rotation, and anything on Device A.

**Screen-off is not changed.** D18's teardown stands. Android 15+ ends projection on a
secure lock anyway, and consent tokens are single-use. Re-initialising needs fresh consent,
so the only UX lever is a "tap to resume" notification. That is not built; it is the
human's call.

**M4 trap recorded** in SPEC.md M4: the mask itself is captured, so it will strobe unless
frames seen while masked are handled.

---

## D20 — Capture code-quality pass (2026-09-15, human request after a strict review)

Behaviour-preserving restructure of the M3 capture code, plus one real bug.

- **Bug: controller stuck in `ACQUIRING_PROJECTION`.** A missing consent result or a null
  `getMediaProjection()` tore the service down after `ServiceStarted`, but the reducer only
  accepted `TeardownComplete` from `STOPPING`. The UI sat on "Working…" forever. Now
  `TeardownComplete` resets to `IDLE` from any phase. `ProjectionStopped` and the service's
  three "report stopped, then tear down" call pairs are deleted: `teardown()` reports once.
- **Effects run on phase entry** (`ProjectionController.onEnter`), not by re-reading state
  after each call. Each effect fires once per transition, so D17's Settings loop can't recur
  by construction. The API-33 notification check moved into `MainActivity`'s effect.
- **`CaptureSession`** now owns everything that exists only while capturing: display,
  `FrameSource`, detector, backpressure, debug pill, capture sizing. `ProjectionService` is
  lifecycle + notification. `resize()` is a no-op when the size is unchanged (theme/locale
  changes). The detector closes on a background thread so teardown doesn't block the main
  thread on an in-flight inference.
- **Black-frame check** runs on a 64×64 nearest-neighbour probe instead of a full-size
  `IntArray` per frame; `isAllBlack` renamed `isMostlyBlack`. It stays in `:app`: moving it
  into `:safecore` needs a "protected" field on SPEC.md §3's `Verdict` contract.
- `ControllerState.blockedReason` deleted (always equivalent to `phase == BLOCKED`).
- Notification and debug pill now show the same status string (severity, score, gated, ms).

Verified: `:app:testDebugUnitTest`, `:safecore:test`, `:app:assembleDebug`, no INTERNET.
**Not re-verified on-device** (no device attached). Re-run M3 V1-V7 before M4 work lands.

---

## D21 — Resume notification, single-app capture note, Beta Blocker comparison (2026-09-23, human request)

Context: the human compared Sophiel with Beta Blocker Android (isla2d.itch.io, closed source)
using a Deep Research report (`investigation.md`, mostly generic). Their teacher said the
app can't run 24/7, and that it's fine to leave something that checks protection is still
running.

- **"Paused — tap to resume" notification.** `ProjectionService.teardown()` posts it when the
  screen is off at teardown (`PowerManager.isInteractive == false`). That covers both
  screen-off paths D19 saw: the OS `onStop()` running first, and our `ACTION_SCREEN_OFF`
  receiver running first. A user Stop, or a failed start, always happens with the screen
  on, so neither posts it. Tapping opens `MainActivity` with `ACTION_RESUME`, which calls
  `controller.start()` from IDLE: the normal §4.4 flow, with a **fresh consent dialog**.
  Nothing silent, no consent-token reuse, no new permission, no extra process. A new session
  cancels the notification. This replaces D19's "not built; human's call".
- **Single-app capture (Android 14 QPR2+) avoids the M4 feedback loop,** because another
  app's overlays aren't in the capture. Beta Blocker tells its users to pick it, and requires
  Android 15. It is not our fix: Device A is API 33, and single-app mode only covers one app.
  Noted in SPEC.md M4.
- **Not taken from the report:** detector/NMS/sub-region blur (§1.3), NNAPI (§1.3),
  its "performance presets" table (no source; looks invented), and file export (conflicts
  with rule 3).
- **Future Work, not built:** sub-region blur as a second, detector model, possibly as a
  heavier performance level. Performance presets: capture rate plus threshold per device
  class. Beta Blocker's promo material suggests a confidence cutoff plus a lower frame rate;
  ours are `FrameThrottle` (80 ms) and M4's threshold slider. Added to SPEC.md's Future Work
  list. Feature freeze at end of M4 still applies.

Verified: `:app:testDebugUnitTest` + `:app:assembleDebug` pass; no INTERNET.
**Not verified on-device** (no device attached). To check: start protection, turn the screen
off, unlock, the notification appears, tap it, fresh consent, RUNNING. Stop from the app,
no notification appears.

## D22 — Rescope to a parental-control app with per-tile masking (2026-10-01, human request)

**Why.** The original proposal to the course was a parental-control app that censors suggestive
areas of the live screen, preferably regions rather than the whole screen. The old SPEC defined a
single-user whole-frame blur and listed sub-region redaction as out of scope (§1.3), so the SPEC
and the proposal disagreed. The graded part is mostly the UI and how well it solves the problem.

**Decided** (planning record: `docs/wayfinder/map.md` and its tickets):
- **Kept:** model and preprocessing (D12), the whole capture stack (M3), no-INTERNET, no-persisted-frames, Kotlin + Compose.
- **Regional masking** via a tile grid reusing the same classifier: Light = 1x1, Balanced = 2x3 (2x2 fallback if the measured sweep is over ~400 ms on Device A). No detector model (Precise preset ruled out).
- **Feedback loop** (the mask is captured): per-tile CLEAR/MASKED/PROBING machine, exact-hash lock, probe on frame change. Probe exposure of one captured frame is accepted and must be measured (SPEC §3.4, §7).
- **Mask look:** solid block with lock and label, PIN-gated Reveal (prototype react: variant C).
- **Parent flow:** PIN gates anything that weakens protection, no PIN recovery, no Stop action in the notification, Strict/Normal/Relaxed, 7-day local log (masks, on/off, unanalyzable).
- **Dropped:** formal evaluation set / ROC / UI corpus as deliverables (debug only), hard latency gates (kept as stretch targets), old M4-M6 text, SPEC §6 "lessons" and the whole-frame-only rules.
- **Plan:** ~4 weeks, risk-first (gate, tiles, overlay, parent app with a freeze at the end of week 3, harden). SPEC §5.

**Alternatives rejected.** Whole-frame only (does not meet the proposal). A box-detector model
(new conversion and parity risk). Remote parent alerts (needs INTERNET and a backend).

**Old SPEC.** Preserved at git tag `spec-v1` and in the diff of the rescope PR.

## D23 — Spec review fixes to the tile design (2026-10-02, human-approved review)

A review of SPEC.md and the M4 tickets before any tile code found design bugs that would
only have shown up on a device. All fixes are in SPEC.md §3.3-§3.5, §5 and §7.

- **Probe validity.** Removing an overlay window does not reach the capture instantly, so a
  probe could read our own mask, score it SAFE, release, and flash. A probe frame now counts
  only once the tile no longer shows the mask (pixel check), capped at ~300 ms.
- **REVEALED state** (5 s) so a Parent's reveal is not re-masked immediately.
- **Own screens:** masks hidden and tracker paused while a Sophiel screen is in front, so the
  PIN prompt can never sit under a mask.
- **One owner for timing:** `PolicyEngine` becomes stateless; the tracker owns engage counts
  and release (probe only).
- **Light probes on the ~2 s timer only** (it has no CLEAR tiles to watch).
- **Per-tile emission** from `Detector.analyze` (a Flow), so a flagged tile is masked after its
  own classification, not the slowest tile's. Driven by the human's priority on feel.
- **Masks pass touches through** (`FLAG_NOT_TOUCHABLE`); Reveal moves to a PIN-gated action on
  Status and in the notification. Supersedes D22's tap-a-mask reveal. Human decision.
- **Whole-frame safety net** for content straddling tiles: measure first in M4, enable only
  if misses are found. Human decision.
- **Debug-only mask in M4** so the feedback loop is tested in week 1, not week 2.
- Log counts masking episodes, not frames. PIN lockout after 5 wrong attempts.
- No Room/KSP/DataStore (none configured): `SharedPreferences` + a plain log file.
- Mask colour never pure black (`BlackFrameDetector` counts exact-black pixels).
- Rotation: grid follows orientation; whole content area masked until post-rotation verdicts.
- Limitations now list every unblockable way a Child can stop protection.

## D24 — Spike: NudeNet v3 on device for a future heavy preset (2026-10-02, human request, branch `spike/heavy-models`)

Outside SPEC §1.3 (box detector) on purpose; a measurement, not a feature. The Benchmark tab
runs each model whole-frame on the 62 Test Feed images (no skin gate, no cache, CPU).

- **Runtime:** `onnxruntime-android` 1.30.0 loading upstream's ONNX files as-is (no conversion,
  C2 holds). Its AAR declares `INTERNET` and `ACCESS_NETWORK_STATE`; both are stripped with
  `tools:node="remove"` and the aapt check is empty again (C1). Watch this on every ORT bump.
- **Models** are gitignored (`app/src/main/assets/*.onnx`; 640m is over GitHub's 100 MB limit).
  Copy from `temp_download/NudeNet_{320n,640m}.onnx` as `nudenet_{320n,640m}.onnx`.
- **Live:** model chips on the Protection screen swap the capture judge between frames (whole frame;
  the debug pill shows score, top 3 classes, ms). GantMan keeps its skin gate + cache; NudeNet runs raw.
  NudeNet boxes are drawn live as outlines on a full-screen touch-through overlay (window alpha 0.8:
  Android 12+ drops touches through another app's overlay above 0.8 opacity; M5 masks will hit this too).
  Human-verified on both devices (2026-10-02): boxes align with content and touches pass through.
  Boxes are in the captured frame (the M4 feedback loop, D21/D23), but no score change or
  in-the-way effect was seen, so no mitigation in the spike.
- **Score** = max over the EXPOSED buttocks/breast/genitalia/anus classes. Per-class max replaces NMS.

| ms per image (mean / p90) | Device A (A71) | Device B (S24) |
| ------------------------- | -------------- | ----------------- |
| GantMan 224 (shipped)     | 52 / 55        | 27 / 30           |
| NudeNet 320n              | 106 / 119      | 22 / 26           |
| NudeNet 640m              | 1911 / 1974    | 581 / 657         |

Scores were identical across devices. NudeNet fires on the explicit images (0.36-0.86 at 320n)
and scores 0 on swimwear/gym/suggestive images that GantMan puts around 0.5. That cuts both ways:
fewer false positives, no "suggestive" signal. 640m is too slow for a live loop on Device A.

## D25 — Probe only on neighbouring change (2026-10-02, human decision after ticket 06 device run)

Ticket 06 on both devices: with the SPEC §3.4 trigger (half of *all* CLEAR tiles changed, or a
2 s timer), any activity on screen probed every masked tile every 1-2 s, showing its flagged
content for ~70-100 ms each time (~5-8% of the time while scrolling; Device B: 114 re-masks in
one session, every one a flash). Options were a longer timer or probing only on related change.

**Decision (human):** a masked tile probes only when at least half of its own CLEAR neighbours
(4-adjacent) changed hash this frame. No timer while it has a CLEAR neighbour. A longer timer
was rejected: it still flashes during unrelated scrolling.

- **Fallback:** a tile with no CLEAR neighbour (Light; or Balanced with every neighbour masked)
  keeps the per-tile 2 s timer, since nothing else can tell it the content moved.
- **Known cost:** content that changes only inside a masked tile (a video exactly under it)
  stays masked until a neighbour moves. That is over-masking, the safe direction; Reveal covers it.
- The per-tile 1 s minimum gap, probe validity and exact-hash lock are unchanged.

## D26 — M4 measurements: grid stays 2×3, whole-frame safety net stays off (2026-10-02, ticket 07)

Measured with `app/src/androidTest/.../TileBenchmark.kt` (`adb logcat -s SophielBench`): the 62
Test Feed images squashed to a 360×744 portrait frame, 3 runs, fresh verdict cache per run,
Normal threshold (0.70). Device A plugged in, 70→78 %, 29.6 °C; Device B on battery, 77→74 %,
31.4→32.6 °C. "Gate off" = every tile classified (worst case).

| ms                         | A71: Light | A71: Balanced | S24: Light | S24 : Balanced |
| -------------------------- | ---------- | ------------- | ------------- | ---------------- |
| sweep p50, gate on         | 51         | 252           | 38            | 192              |
| sweep p90, gate on         | 53         | 309           | 39            | 327              |
| sweep p50 / p90 / max, gate off | 51 / 55 / 95 | 304 / 315 / 477 | 38 / 39 / 40 | 284 / 327 / 345 |
| per classified tile p50    | 51         | 51            | 38            | 43               |
| per gated tile p50         | 1          | 1             | 0             | 0                |
| first flagged tile p50     | 51         | 154           | 38            | 151              |

Per-run sweep medians (Balanced, gate on): A 256 / 254 / 252, B 188 / 192 / 264. The skin gate
skips 34 % of Balanced tiles on this set (19 % of whole frames). Device B's per-tile cost is
closer to A's than D24's whole-frame numbers suggest, and its third run was slower; it was on
battery and warming, so treat B's Balanced numbers as an upper bound.

- **Grid: 2×3 stays.** Device A's worst case (all six tiles classified) is p90 315 ms, under the
  ~400 ms fallback line; one outlier frame took 477 ms.
- **Per-tile emission pays:** on frames with a flagged tile, the first one is out at ~150 ms
  against a ~250 ms full sweep (both devices).
- **Whole-frame safety net: off.** Each image the whole frame flags (8 of 62) placed on white
  at several positions. The whole frame never caught one that every tile missed:

  | placement (image fitted to box)             | any tile flags | whole frame flags |
  | ------------------------------------------- | -------------- | ----------------- |
  | one tile's size, inside a tile              | 3 / 8          | 0 / 8             |
  | one tile's size, across a vertical edge     | 1 / 8          | 0 / 8             |
  | one tile's size, on a 4-tile corner         | 0 / 8          | 0 / 8             |
  | feed-like, full width × 2 rows, row-aligned | 7 / 8          | 3 / 8             |
  | feed-like, full width × 2 rows, half-row off| 6 / 8          | 3 / 8             |

  Tiles beat the whole frame everywhere. **Known gap:** content about one tile in size that
  straddles a boundary is mostly missed, and the whole frame (gated at this size) does not
  rescue it, so the safety net would not fix it either. Recorded in SPEC §7, item 3.
- **Skin gate on real screens:** a settings screenshot gates 6/6 tiles; a launcher home
  screen gates 0/6 (wallpaper), scoring ≤ 0.01 everywhere, so it costs a full sweep but never
  flags. Retuning the ratio and luma floor needs a person to label more real screens.
- **Probe exposure** (ticket 06 sessions, *old* trigger, before D25): Device A median ~85 ms,
  worst 201 ms; Device B mostly 70-110 ms, worst 244 ms.
  **Under D25** (Device B, human scrolling session, ~27 s, 18 probes): median ~197 ms, p90 306 ms,
  max 307 ms; 12 re-masked, 6 released; 3 of 18 hit the 300 ms cap and re-masked unjudged.
  Exposure went *up*: with content moving, every CLEAR tile is classified (~38 ms each on B)
  and the probing tile is judged in index order, so its valid frame lands 100-150 ms into the sweep.
  **Fix: probing tiles judged first** (`e5c8a63`). Device B, ~33 s session, 6 probes: median 121 ms,
  p90 158 ms, max 168 ms, no 300 ms timeouts; 5 re-masked, 1 released. Most of what remains is
  waiting for the next captured frame (~80 ms throttle plus decode). Small sample.
  Device A on that build: median 279 ms, p90 307 ms, 6 of 12 probes hit the 300 ms cap. The first
  frame after a probe starts usually still shows the mask (correct), but that frame then classified
  the CLEAR tiles (~165 ms on A), so the valid frame landed after the cap; one re-masked 39 ms
  before its real verdict.
  **Fix: a probe frame that still shows the mask skips the CLEAR tiles** (they wait one frame).
  Device A, ~2.5 min session, 17 probes: median 138 ms, p90 200 ms, max 265 ms, **no timeouts**;
  12 released, 5 re-masked; 9 probe frames still showed the mask and were cut short as intended.
  Frame to mask on A: p50 104 ms, max 315 ms (14 episodes).
- **Frame to mask** (`frameToMaskMs`, Device B, 9 episodes): p50 95 ms, max 160 ms, measured from
  the frame that completes the 2-frame engage, so content-to-mask is about one frame interval more.
- **Skin gate: unchanged (human decision, 2026-10-03).** Its code and thresholds are as of
  `faaeb40` (5 % skin ratio, luma floor). What changed in M4 is only that it runs per tile, so the
  ratio is measured against a smaller area: it gates 34 % of Balanced tiles vs 19 % of Light frames.
  Retune only if real use shows misses or wasted sweeps.
- **Sensitivity values: kept as starting values (human decision, 2026-10-03):** Strict 0.55,
  Normal 0.70, Relaxed 0.85. Revisit with labelled score data.

## D27 — Drop Reveal (2026-10-03, human decision during M5 planning)

Reveal (PIN-gated uncover of all masks for 5 s) is removed. Touches already pass through masks, so
the Child can scroll past one; a false positive is handled by stopping protection behind the PIN
and restarting (one extra consent tap). Dropping it removes the REVEALED tracker state, the
PinPromptActivity from M5, the notification action and old M5 V5. Overrides SPEC §1.2, D22 and D23.
SPEC/CONTEXT/tracker cleanup is ticket 05 of `.scratch/m5-overlay/`.

Same session, D24 follow-up: the NudeNet spike stays on `main`, but only behind the debug menu.
Confirmed from the ORT 1.30.0 AAR's own manifest: it declares `INTERNET`, `ACCESS_NETWORK_STATE`
and a `TelemetryInitializer` provider; our manifest strips both permissions. Re-run the aapt
check on every ORT bump.

## D28 — M5 overlay architecture (2026-10-03, human decisions, `.scratch/m5-overlay/` tickets 01-03)

- **One full-screen touch-through window** draws all masks, at window alpha 0.79. Supersedes the SPEC's
  "one window per masked tile". Reason: it is what M4 proved on both devices, debug boxes can share it,
  and a probe is one redrawn rectangle with no add/remove churn. Research (ticket 01): Android's
  untrusted-touch rule is per touch point and per UID, from window alpha not pixels, `>0.8` blocks,
  so per-tile windows were legal too, but nothing else may overlap a mask window.
- **Look: strong noise** (~1 px, full brightness range). Beat flat+lock, lock pattern, old noise,
  pixelate (shape stays readable) and blur of our own copy at alpha 0.8 and 0.7 on both devices.
  Blur-behind is "NOT enabled" on both devices (A71 `mBlurEnabled=false`): dropped.
- **Lock chip + label** (small, centred) is a Parent **show-label toggle** (default on, no PIN to change).
  Human scope addition to SPEC §1.2; lives in M6 Settings, M5 draws both behind a constant.
- **"Still shows our mask"** = captured-tile mean colour vs the mask's, centre excluded; the capture is
  downscaled ~3-4x so the grain averages out. Tolerance set from probe-frame logs on both devices.
- **Masks cover the content area only**; bars stay visible (the capture crops them anyway).
- **Swiping from Recents does not stop protection** (the foreground service survives). Otherwise a
  Child could end protection with one swipe and no PIN. V4 now tests force-stop leaves no orphan window.
- **Own screens:** masks hidden and tracker paused only while a Sophiel activity is resumed and not in
  multi-window mode; in split-screen masks stay up. Detected with an in-process lifecycle counter.

## D29 — M5 plan review fixes (2026-10-03, review of tickets 06-11, human-approved)

A review of the M5 tickets against the code, before any overlay code, found design holes.
SPEC §3.4, M5, §7 and §9 are updated; tickets 06-12 of `.scratch/m5-overlay/` carry the work.

- **Rotation cover redesigned.** "Cover until the first post-rotation verdicts" cannot work: the
  capture sees the cover, so those verdicts would score it SAFE, and `reset()` to CLEAR with a
  2-frame engage would show flagged content for ~500 ms on Device A. Now: cover at once only if
  something is masked; on the first new-size frame every tile starts PROBING and the existing probe
  rules decide. Cost: one whole-screen probe per rotation, measured in V3. A preset change takes the
  same path (M6 V4).
- **Noise mask tinted.** The chosen noise averages to near-neutral grey, and so do ordinary photos;
  a mean-colour check could not tell them apart, and the tile would never release (no Reveal, D27).
  The noise keeps its grain and full brightness range but its mean moves well off grey. Generated
  once and tiled: a mask that changes per draw keeps a static screen sending frames (D18).
- **Protected probe frame releases the tile (human decision).** On a `FLAG_SECURE` app the probe
  frame is black, never scored, and the tile re-masked forever (Light: flickered every 2 s). Now
  every PROBING tile goes CLEAR on a protected frame. Rejected: accepting permanent over-masking.
- **V4 tests the paths that can fail.** Force-stop kills the process and its windows with it; an
  orphan window is only possible when protection ends with the process alive (status bar "Stop
  sharing", Quick Settings "Active apps"). The Recents-swipe claim in D28 was never tested on these
  Samsung devices; ticket 06's device run checks it first.
- **Limitations added (§7, 14-17):** ~21 % show-through at alpha 0.79; system bars never masked;
  change only under a mask stays masked (D25's "Reveal covers it" no longer holds); rotation
  exposure.
- M5 verification moves to D30.

## D30 — Camo mask and pattern-correlation mask check (2026-10-03, human decisions, ticket 13)

Supersedes D28's strong-noise look and D28/D29's mean-colour check. M5 verification moves to D31.

- **Why the strong noise showed through.** At about 393 ppi and 30 cm, a 1 px dot is under 1 arcminute,
  so the eye averages the grain to flat colour and the content's large shapes (silhouettes, skin on
  dark) show through at 21 %. Ticket 02 never saw true 1 px grain: the Masks tab stretched it ~5x
  in-app and 2x in the real window. The tab now draws at 1:1 device pixels.
- **Look: camo** (human pick in the Masks tab): blobs at 16/8/4 px plus 1 px grain, histogram-
  equalised (full contrast), 256 px, seeded, tiled. The shader is anchored to screen pixels.
- **Check: correlation, not mean colour.** A masked tile is 0.79 x pattern + 0.21 x content, so its
  luminance at 108 sample points follows the blobs we drew there; bare content is unrelated to our
  seeded blobs. Frame pixels map back to screen pixels through the capture crop and scale.
  Colour-free: the purple tint (D29) is no longer needed, and purple content no longer reads as
  masked. JVM simulation (raw grain, 3x capture): masked r >= 0.71, bare r <= 0.31.
- **Shipped settings (human pick, Masks tab):** blobs 16/8/4 px, brightness 100 (luma ~82), Slate.
  Purple was found too flashy.
- **Device run (2026-10-03):** B 141 probe frames (portrait), A 74 (portrait + landscape). Masked
  r 0.67-0.89, bare r -0.29-0.26, nothing in between on either device or orientation.
  THRESHOLD = 0.45, mid-gap leaning to "masked".

## D31 — Probe backoff on re-masking tiles (2026-10-03, human decision, ticket 14)

Amends D25's timing; the neighbour rule itself stays. M5 verification moves to D32.

- **Found on Device B:** a video playing next to a masked tile changes its neighbours on every
  frame, so the tile probed at the 1 s floor (tile 3: 43 probes in ~3 min, 32 re-masked) and the
  mask blinked about once a second for nothing.
- **Rule:** each probe that ends re-masked (locked hash, flagged, or the 300 ms timeout) doubles
  the tile's minimum probe gap: 1, 2, 4, 8 s. A releasing probe resets it; a new episode starts at
  1 s. The no-neighbour timer (Light) is max(2 s, gap).
- **Owed probes start on a timer.** First device check: a tile stuck masked. Its neighbours changed
  inside the 8 s gap, then the screen went static, so no frame came to start the probe (D18). A
  neighbour change inside the gap now marks the probe as owed; it starts when the gap ends, on the
  next frame or on a timer if none comes. Lifting the mask changes the screen, so the probe frame
  arrives. The Light/no-neighbour timer is never owed: it still needs a frame, as in D25.
- **Cost:** a backed-off tile can stay masked up to 8 s after its content leaves. Over-masking,
  the safe direction. Rejected for now: requiring 2 SAFE probe frames to release (would also stop
  the rarer release-then-re-mask cycle on video, but lengthens every probe).

## D32 — Opaque masks through an optional accessibility window (2026-10-03, human decision and build)

Scope addition to SPEC §1.2 (human). Amends D28's alpha 0.79.

- **Why.** At alpha 0.79 about 21 % of the masked content shows through (D28, §7 item 14), and the
  camo (D30) only reduces how readable it is. Android 12+ exempts trusted windows, including
  accessibility overlays, from the untrusted-touch opacity rule, so a `TYPE_ACCESSIBILITY_OVERLAY`
  window can be fully opaque and still pass every touch through.
- **How.** `MaskWindowService` is an `AccessibilityService` that does nothing but lend
  `OverlayController` its window token: no event types, `canRetrieveWindowContent="false"`. Enabled:
  the one mask window is an accessibility overlay at alpha 1.0. Off: the app overlay at 0.79, as
  before. Turned off mid-session: the masks move to the app overlay.
- **Device runs (2026-10-04):** A and B log `mask window: accessibility, alpha 1.0`; masking,
  probing and the D30 mask check work unchanged (masked tiles read r 0.70-0.90).
- **Costs and limits.** The Parent enables it in Accessibility settings; on Android 13+ a sideloaded
  APK first needs App info > "Allow restricted settings" (Parent setup, M6). Enabling it mid-session
  takes effect on the next protection start. An accessibility overlay sits above system UI panels,
  so the rotate-suggestion button (auto-rotate off, gesture navigation) can hide under a mask; a tap
  there still reaches it. Leaving holes in the bottom corners was rejected: it would expose content.
- **Amended 2026-10-04 (human): the accessibility window is the main path. Its Start gate is
  superseded by D46 (2026-10-06).** Release builds start
  protection only with the service on (Status and the M6 wizard gate Start); starting on the 0.79
  app overlay without it is a debug-build option. Reason: the app never ships to Play (school
  project), and opaque masks give better results. Turning the service off mid-session still moves
  the masks to the app overlay in every build, so one Settings toggle cannot drop every mask.

## D33 — Rotation cover held until the capture shows it (2026-10-04, ticket 08 device runs)

Refines D29's rotation design. M5 verification moves to D34.

- **Cover down too early.** D29 took the cover down on the first new-size frame. On Device B that
  frame (and the next ~4) is the system rotation animation, a snapshot of the old screen: not the
  content, not our cover (r 0.00-0.44). Judged as probe frames they released most tiles, and the
  flagged ones re-masked ~400 ms later, barely better than the 440 ms baseline.
- **Now:** after a grid change with anything masked, the tracker pauses, the cover stays, and frames
  are ignored until one shows the cover on every tile (D30 check). Then every tile starts PROBING
  and the cover comes down. If no frame shows it within 1 s (static screen, secure app), it goes
  ahead anyway. A preset change draws the same cover.
- **Blank tiles re-masked after a reset.** A reset tile's lock was hash 0, and a blank tile's dHash
  is 0, so empty content matched and stayed masked (Device A). The lock is now null until a tile
  is masked.
- **Device runs (opaque masks, D32):** cover seen after 373-393 ms on B, 526-730 ms on A; then
  tiles uncovered 73-161 ms on B, 94-218 ms on A, flagged tiles re-masked on the first probe frame.
  Baseline without the cover: ~440 ms on B, 740-900 ms on A.

## D34 — Peek under the mask with a window screenshot (2026-10-04, human decision, ticket 16 spike)

Amends D32's "no window content". M5 verification moves to D35, then D36.

- **Why.** Every probe lifts the mask, because MediaProjection captures it; D25-D33 and ticket 15
  work around that. `AccessibilityService.takeScreenshotOfWindow()` (API 34+) shoots one window
  without the windows above it. The SDK documents it for this case: a target window "visually
  underneath an accessibility overlay".
- **How (spike).** Debug chip "Peek under mask" on the Protection screen, default off; a Parent
  setting in M6. With it on, API 34+ and `MaskWindowService` on, a PROBING tile stays masked and is
  judged from a shot of the top app window under it, scaled into the capture frame's geometry. The
  tracker is unchanged (same triggers, backoff, 300 ms timeout). The shot is recycled after judging.
- **Service capabilities:** `canRetrieveWindowContent`, `canTakeScreenshot`,
  `flagRetrieveInteractiveWindows`. Only window ids, types and bounds are read: no nodes, no event
  types. Capabilities are static, so every build declares them; only the option uses them. Device B
  picked them up on reinstall without re-enabling the service (`capabilities=129`).
- **Device A (API 33) has no such API** and keeps the probe path.
- **Run 1 (B):** shots p50 59 ms, max 132; no flash. Tiles got stuck: inside X every shot failed
  (error 1, ~2 s), and probes waiting on a shot timed out, both counted as wasted and backed off.
  **Now:** a peek is its own state, PEEKING. A failed, slow (> 500 ms) or windowless shot lifts the
  tile into an ordinary probe, so peeking is never worse than probing. A flagged peek does not back
  off: nothing was shown.
- **Run 2 (B, ~3 min incl. X):** no tile stuck; 38 episodes all released, every one over 4 s was
  flagged content still on screen. Shots p50 73 ms, max 134. When X's shots failed again, the
  fallback probes released the tiles in ~85-130 ms. Shots now 400 ms apart (342 ms hit the limit).
- **Kept as an option**, debug chip now and a Parent setting in M6 (human). Unknown: why X's shots
  start failing (error 1, in system_server).

## D35 — NudeNet box masks, an experiment on window shots (2026-10-05, human decision, ticket 17)

Started outside SPEC §1.3 ("Precise preset (bounding-box detector model)") on purpose, like D24;
now in scope as the Precise preset (amendment below). M5 verification moves to D36.

- **What.** With NudeNet picked and "Peek under mask" on (API 34+), its unsafe boxes (score >= 0.3,
  padded 10 %) are drawn as camo masks. Frames can only add masks: they can't see under one. Every
  400 ms, while something is masked, a window shot (D34) replaces them all. A failed shot holds
  them up to 2 s. `NudeNet.detect` now does per-class NMS (IoU 0.45) instead of one box per class,
  so two regions get two masks; the max score, and D24's numbers, are unchanged.
- **Window cache off.** Run 1 flashed: 29 of 30 shots failed (error 1, ~2 s) after a switch through
  Recents, and each failure dropped the masks. `MaskWindowService` now calls
  `setCacheEnabled(false)`, so `getWindows()` is never stale. Run 2 (B, ~6 min, Gallery and X):
  130 shots, 0 failures. This also covers D34's tile peeks, which failed in X the same way.
- **Run 2 (B):** shot to masks p50 144 ms, max 954; NudeNet 320n per frame p50 54 ms, p90 115.
  Human: "great results"; on scroll, new content shows for ~100-200 ms until a frame's detection
  lands (model speed), and masks trail moving content by up to the 400 ms shot gap.
- **Device A cannot run it** (API 33: no window shots; 320n at 106 ms per frame, D24).
- **Amended 2026-10-05 (human): in the demo, as the Precise preset.** Reason: the most impressive
  result for the demo. Scope addition to SPEC §1.2; the §1.3 "Precise preset" row is removed.
  Precise = NudeNet 320n box masks wherever window shots work; elsewhere (Device A, service off
  mid-session) it runs Balanced tiles. Sensitivity does not apply (fixed box score 0.3). Light and
  Balanced stay the main protection; the demo shows Precise on Device B. NudeNet's weights stay
  gitignored (D24): a demo build needs `nudenet_320n.onnx` copied into the assets first. SPEC §7
  item 19.

## D36 — M5 verification (2026-10-05, ticket 11, human device runs)

Debug build at `ad80964`, Balanced, accessibility mask window (alpha 1.0). Device A = SM-A715F
(API 33), Device B = SM-S721B (API 36). Numbers from the run's `Sophiel` logcat; probe exposure is
`probe tile=N exposureMs`, "after rotation" is a probe within 2.5 s of a capture resize.

| | A | B |
|---|---|---|
| V1 masks on flagged tiles, portrait + landscape; touches pass through | pass | pass |
| V2 static image 10 s, no flicker; releases after content changes | pass | pass |
| V3 probe exposure, median / worst | 146 / 306 ms (n=26) | 85 / 188 ms (n=20) |
| V3 after rotation, median / worst (9 / 10 rotations) | 220 / 304 ms (n=12) | 129 / 210 ms (n=12) |
| V3 shortest gap between probes on one tile | 2121 ms | 2053 ms |
| V4 "Stop sharing" chip and Quick Settings Stop leave no window; Recents swipe keeps running | pass | pass |
| V5 secure app releases the mask, no flicker | pass, last tile ~2.1 s after the switch | pass (ticket 12 run 2) |

- **Worst cases sit on the 300 ms probe cap**: a probe that got no valid frame re-masks at 300 ms.
- **V5 needed a cache fix (ticket 12).** Black tiles hash to dHash 0; a dark tile scored EXPLICIT
  during an app switch was cached under 0 and re-masked every black probe after it. `VerdictCache`
  no longer stores hash 0. With several tiles masked the frame is not "mostly black", so ordinary
  probes release those tiles (black gates SAFE) and `releaseProbes` frees the last one.
- **V5 on A is slower by design (D31):** every tile released on its first probe after the switch,
  but three had just re-masked on the real image, so their next probe waited the 2 s backoff gap.
  That run drew masks on the 0.79 app overlay: the accessibility service was enabled but not yet
  connected when protection started (not investigated).
- **Rotation frames can be protected** (A: two black frames mid-rotation). The tracker is paused
  under the rotation cover, so they change nothing.
- **Release Start gate (D32), B:** a release build signed with the debug key. With the service off
  the note shows and Start is disabled; turning it on enables Start. Not run on A.
- **Own-screen pause (ticket 09)**: checked on B only (masks hide in Sophiel, return unchanged,
  stay up in split screen).
- **Open, not an M5 verification item:** ticket 15 (release flips on video), needs-info until
  there is a repeatable test clip. M5 is ticked on V1-V5.

## D37 — Fast re-mask just after a release (2026-10-05, ticket 15, human-set bar)

A probe can land on one safe frame of a video that is flagged again a moment later; re-engaging
then needed 2 flagged frames, so the video played uncovered. **A tile released by a probe or peek
in the last 3 s re-masks on its first flagged frame** (`TileMaskTracker.RECENT_RELEASE_MS`,
a calibration knob). Rejected: two SAFE probe frames to release (doubles every probe's exposure).

- **Bar (human):** build if > 1 in 5 releases re-mask within 3 s, or any uncovered stretch > ~1 s,
  on either phone, over at least 15-20 releases. Clip: a human screen recording (protection off),
  gitignored under `eval/images/m5-video/`, sha256 `3f326560…a5e9696`, played twice per phone.
- **Baseline:** A 15 of 65 releases flipped (23 %), B 17 of 55 (31 %). Bar met on both.
- **3 s fits:** release -> re-mask gaps cluster in 0-3 s (A 15, B 17); 3 in 3-5 s; the rest > 5 s.
- **Exposure per flip** (first flagged frame captured -> mask), median / worst:
  A 475 / 669 -> **154 / 295 ms**; B 274 / 969 -> **135 / 204 ms**.
- **Flip count is unchanged by design** (A 18 of 65, B 18 of 68): a flip is still a release then
  a re-mask, just shorter. **No sign of extra false masks:** after a flip the next probe released
  the tile 8 of 15 / 7 of 16 times before, 8 of 18 / 8 of 18 after.
- **Episode log (M6):** each fast re-mask is a CLEAR -> MASKED, so it logs as a new episode, as a
  flip already did.

## D38 — PIN, unlock and gating: one door (2026-10-05, human decisions, `.scratch/m6-parent-app/` ticket 01)

- **One door.** Status opens with no PIN (on/off, preset, Start, "Settings (parent)"). **Stop** and
  **opening Settings** need the PIN; everything behind Settings is gated by being there: preset,
  sensitivity, peek, show-label, Change PIN, Log, Clear log, debug menu. One rule, one V1 test.
  Overrides D28's "show-label needs no PIN". Rejected: per-action gating by direction (stricter
  passes, weaker prompts), a Settings screen where some controls prompt and others don't.
- **Unlock** (in memory, held in `AppContainer`): ends 2 min after the last PIN-gated action, or
  at once when `MainActivity` stops (background or screen off) unless `isChangingConfigurations`
  (rotation recreates the activity), or when the process dies. Covers the Parent handing the phone
  over. Leaving for a system Settings page from behind the door relocks; intended. The wizard runs
  only while no PIN exists and does not depend on the unlock.
- **Lockout:** 5 wrong attempts -> 30 s, doubling, capped at 1 h (no recovery, so no multi-day
  lockouts). Count and lock-until persisted in SharedPreferences on `elapsedRealtime` +
  `Settings.Global.BOOT_COUNT`; after a reboot the current lockout restarts at full length. Resets
  only on a correct PIN. Rejected: in memory only (one force-stop would beat V1).
- **Hash:** `PBKDF2WithHmacSHA256` (javax.crypto, API 26+), 16-byte SecureRandom salt, iteration
  count fixed for ~150 ms per check on Device A, run on `Dispatchers.Default`. A 4-6 digit PIN
  falls to brute force once the hash is read; the sandbox is the real protection, the hash meets
  "never plaintext".
- **PIN pad:** a Compose screen in `MainActivity`; `PinPromptActivity` is dropped (every gated
  action is already in `MainActivity`). Typed digits are not kept in saved instance state.
- **Debug menu:** 7 taps on the version label at the bottom of Settings, every build (the demo may
  run a release build and Test Feed is its fallback). Holds Test Feed, Benchmark, mask look, debug
  pill, model pick (D24), raw slider (in memory). The bottom-bar tabs go; preset, Precise and peek
  move to Parent Settings. The notification loses Stop in every build. Debug-build-only stays:
  starting without the accessibility service (D32).
- **Change PIN** (human scope addition to SPEC §1.2): a Settings row reusing the wizard's create +
  confirm screens.

## D39 — The Log: per-screen masking episodes (2026-10-05, human decisions, `.scratch/m6-parent-app/` ticket 02)

- **Masking episode is per screen, not per tile:** the screen going from no masks to at least one,
  in every preset. Probing/peeking tiles count as masked, so probes and the rotation cover never
  split it; masks that return within 3 s of the screen clearing (D37's `RECENT_RELEASE_MS`)
  continue it. Judged on the tracker / box state, not what is drawn (a Sophiel screen hiding the
  masks doesn't end it). A new session starts a new one. One entry, written at the start. Pure
  logic, JVM test with a fake clock. Reason: per tile, one photo over three tiles was three entries
  and a "masked" entry's tile count was always 1; Precise boxes have no identity; "N masks today"
  should mean "N times something was hidden". Human: also keeps the Log independent of grid size
  and of NudeNet. Supersedes D37's "Episode log" note: only a re-mask more than 3 s after the
  screen cleared is a new episode. CONTEXT updated.
- **Masked entry:** time, effective preset, tile or box count at the start, raw max score. The Log
  screen draws bands at the sensitivity cut-offs (>= 0.85 "hidden even on Relaxed", 0.70-0.85,
  0.55-0.70); none for Precise (NudeNet's scale). Raw score keeps bands redrawable.
- **Unanalyzable:** one entry per protected stretch lasting >= 1 s, checked on the next frame or a
  1 s timer (a still secure screen sends no frames, D18). Filters the mid-rotation black frames (D36).
- **Off reasons:** user stop; screen off (`isInteractive` false at teardown); system ended (the
  projection ended with the screen on). **Inferred gap:** a last-alive time written once a minute
  while running; on the next start an ON with no OFF gets an OFF at that time, reason "phone
  restarted" if the boot count changed, else "app was closed" (a Child's force-stop). ON is written
  at `onProjectionAcquired`, so a failed start logs nothing. ON records preset and sensitivity;
  mid-session settings changes are not logged.
- **File:** `filesDir/log.csv`, one line per entry, wall clock. Appends drop lines older than 7 days;
  reads filter by age too. "Today" is since local midnight. SPEC §7 item 7 notes the wall clock.

## D40 — Settings changes apply through the cover path (2026-10-05, human decisions, `.scratch/m6-parent-app/` ticket 03)

- **Sensitivity is a live threshold** read by `PolicyEngine`; the raw slider writes the same value.
  `VerdictCache` stores scores and severity is recomputed every frame, so no detector rebuild or
  cache clear.
- **One rule for every judging change** (preset, sensitivity, raw slider, Precise falling back to
  Balanced or coming back): with anything masked, take the cover path; with nothing masked, just
  switch. Into tiles: D33's whole-screen cover, wait for a frame showing it (or 1 s), then every
  tile PROBING. Into Precise: keep the cover, take a window shot at once, draw its boxes, drop the
  cover (failed shot: after 1 s). Widens D29/D33's "a preset change takes the same path".
  Changes land on the first frame after the Parent leaves Sophiel (frames are dropped meanwhile).
- **Fixes a gap:** a judge switch (`wantedModel()` -> `judge.close()` -> `openJudge()`) cleared every
  mask and the new judge started from nothing (a new tile loop needs 2 flagged frames, ~400-600 ms
  on A plus model load), including Precise's fallback when the service is turned off mid-session.
- Cost: one whole-screen probe per change (73-218 ms, D33); none with peek on or into Precise.
  M6 V4 measures it. Rejected: cover only when loosening (a second branch to save a rare probe);
  no cover for sensitivity (a hash-locked false positive stays masked until a neighbour changes, D25).
- **Precise on screen:** disabled with "Android 14+" below API 34; when it falls back, Status shows
  "Precise — running Balanced (accessibility off)". Sensitivity stays selectable with Precise picked
  (it applies when Precise falls back).
- **Plumbing:** `SettingsRepository` in memory, loaded once from SharedPreferences, write-through;
  the session reads it per frame; Compose observes a `StateFlow`. Peek and show-label apply live with
  no cover (show-label redraws when the Parent leaves). `livePreset`, `precise`, `peekUnderMask`
  leave `AppContainer`; the debug `liveModel` stays there.

## D41 — Restricted settings and accessibility service state (2026-10-05, research, `.scratch/m6-parent-app/` ticket 04)

Facts from AOSP 13-16 source; One UI is closed source and unverified. Full findings:
`.scratch/m6-parent-app/research/04-restricted-settings.md`. Corrects D32's wording.

- **Only file-manager or browser installs are restricted** (package source `LOCAL_FILE` /
  `DOWNLOADED_FILE`). `adb install` and Android Studio are not, on 13-16. D32's "a sideloaded APK
  needs Allow restricted settings" holds only for those installs.
- **Allowing it:** tap the greyed service first (its dialog arms the option), then App info > ⋮ >
  "Allow restricted settings" (lock-screen check), then turn the service on. An already-enabled
  service is never greyed out.
- **The app can't detect it** (the app-op and Android 15+'s Enhanced Confirmation are system-only)
  and can't deep-link to its own service page (`ACTION_ACCESSIBILITY_DETAILS_SETTINGS` is guarded).
  Usable links: `ACTION_APPLICATION_DETAILS_SETTINGS`, `ACTION_ACCESSIBILITY_SETTINGS`.
- **Enabled vs bound:** `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` = ticked;
  `getEnabledAccessibilityServiceList()` = actually bound; `addAccessibilityServicesStateChangeListener`
  (API 33) fires on bind.
- **Force-stop un-ticks the service** (Settings or `am force-stop`): the Parent must turn it on
  again. An update keeps it; uninstall clears it and the restricted-settings allowance. SPEC §7 item 6.

## D42 — Setup wizard, live service check, masks follow the service (2026-10-05, human decisions + Device A run, `.scratch/m6-parent-app/` tickets 05 and 07)

**Device A run (ticket 05, One UI 5, debug build, wireless adb).**

- **D36's 0.79 start explained.** After an update the service stays ticked but unbound until ~1.8 s
  after the app opens, and Status read `MaskWindowService.instance` only in `onResume`, before the
  bind: the note "Accessibility off" stayed 25 s later. Debug builds start anyway (0.79); release
  would keep Start disabled until the next resume. The test (bound) was right; reading it once was not.
- Force-stop un-ticks the service (D41 confirmed). Enabling it in Settings binds at once. A Recents
  swipe and screen-off keep it bound.
- **The accessibility shortcut** (human used it) turns the service off in one tap, and the masks
  then stayed at 0.79 for the rest of the session even after it came back (D32).
- **Play Protect blocks file-manager installs** on A: developer verification passed (`[ADV]
  VERIFICATION_ALLOW`), the local scan was clean, then a server verdict `response=11 ...
  enable_ecm=true` rejected it ("App not installed", no override; likely enhanced fraud protection,
  inferred). With scanning off it installs, and One UI's restricted flow matches AOSP: "Restricted
  setting" dialog, App info > ⋮ > "Allow restricted settings" behind the lock screen, then the
  switch enables. adb installs have neither problem. SPEC §7 item 20.
- **Samsung sleeping apps:** "Put unused apps to sleep" is on by default. App info > Battery >
  Unrestricted put Sophiel on the deviceidle allow list but **not** on "Never auto sleeping apps"
  (count stayed 0). Both are needed. SPEC §7 item 21.
- `uiautomator dump` briefly unbinds accessibility services: never use it during a measured run.

**Wizard (ticket 07, human).**

- Runs on launch whenever no PIN exists; ends by starting protection, then Status (a cancelled
  consent lands on Status with setup complete).
- Steps: PIN + confirm (no recovery) -> overlay (required: D32's fallback) -> accessibility ->
  keep awake (Unrestricted + Never auto sleeping apps; text and an App info button, no new
  permission) -> notifications (skippable, degraded) -> preset + sensitivity (Balanced + Normal;
  Precise disabled below API 34, "experimental" above) -> start ("Entire screen" on Android 14
  QPR2+, D21). Linear with Back; granted steps show done and move on by themselves.
- Accessibility step: completes on **bound, checked live**; always-there collapsed help for a
  greyed switch (undetectable, D41); tells the Parent to leave the shortcut off; "Skip (debug)".
- After setup the wizard never re-runs: Status shows a "Needs attention" card per missing item
  (overlay, service, notifications) with a fix button, no PIN. Release Start is disabled while the
  service is unbound or overlay is missing, checked live. The battery item can't be detected, so
  it is wizard-only.
- **Masks follow the service (amends D32):** when the service binds again mid-session, the masks
  move back to the opaque accessibility window, new window added before the old is removed.
  Replaces D32's "takes effect on the next protection start". Reason: the run showed one shortcut
  tap downgrades the whole session.
- V3 is spelled out step by step in SPEC M6; it ends on `mask window: accessibility, alpha 1.0`.

## D43 — Parent UI language and theme (2026-10-05, human decision, `.scratch/m6-parent-app/` ticket 06 inputs)

Human scope addition to SPEC §1.2. All app text is bilingual, **Spanish first**: the default
`values/strings.xml` is Spanish, English lives in `values-en/`, and every user-facing string goes
through resources from the start. Material You dynamic colour (both devices are Android 12+, with a
fixed fallback scheme below API 31), light theme only. Feel: approachable, calm and helpful. The
look itself is settled by the ticket 06 prototype.

## D44 — Parent screens look: concept E2 (2026-10-06, human decision, `.scratch/m6-parent-app/` ticket 06)

The look of the Parent app is `.scratch/m6-parent-app/mockups/gnome2.html`. It settles the
"look itself" that D43 left to the prototype.

- **Shapes and layout:** libadwaita style. Flat grey header bar with a centred bold title, boxed
  preference groups (12 dp corners), pill buttons (blue = suggested, red = destructive), round
  switches, a status page with a big symbolic icon, a bottom sheet for the PIN. Light only.
- **Colour:** only the accent follows Material You (`colorPrimary`); green, orange and red are fixed
  state colours. Fixed blue fallback below API 31. This narrows D43: dynamic colour drives the
  accent, not the whole palette.
- **Status shows only what is wrong:** "Todo en orden" is a collapsed row; Revisión and Cuidado sit
  in one summary row. Only the full mask (accessibility) blocks Start; notifications are listed
  separately as "Recomendado". Lock icons on Ajustes and Detener say the PIN is needed.
- **States in scope of the screens:** Starting (capture consent, busy button), Stopped by itself
  (what happened, when, what is missing), Settings unlocked banner with the 2 min countdown and
  "Bloquear ahora", empty Log, confirm dialog before "Borrar historial".
- **PIN sheet:** 4-6 digit slots, Desbloquear button, wrong PIN shakes and says attempts left,
  lockout shows a countdown (D40's doubling), "¿Olvidaste el PIN?" says there is no recovery and
  that clearing app data also wipes the log.
- **Log:** 7-day strip, per-day counters Tapados / Sin revisar / Apagadas, a word chip per mask
  (Muy seguro / Seguro / Dudoso, none for Precise), rows expand to the technical detail, filter
  Todo / Tapados / Otros, off events carry their reason.
- **Copy (Spanish first):** Cuidado = Tapar más / Normal / Tapar menos (Strict / Normal / Relaxed);
  Cómo revisa = Ahorro / Equilibrado / Detallado (Light / Balanced / Precise). A helper line under
  each selector says the consequence. One word for the off state: "detenida" in the Log, "Sin
  protección" on Status. Settings has a "Sin internet" privacy row.
- **Motion:** sliding thumb on segmented controls, sheet rises from its trigger and leaves the same
  way, collapse and cross-fades only on state changes; nothing animates when Status opens. Honour
  reduced motion.

Follow-up: SPEC M6 and CONTEXT.md still use the code names (Strict/Normal/Relaxed,
Light/Balanced/Precise); the UI labels above map to them and live in `strings.xml`.


## D45 — UI details the mockup leaves open (2026-10-06, human decisions, `.scratch/m6-parent-app/` ticket 14)

- **Unanalyzable carries its length.** The entry is written when the protected stretch ends (the
  first unprotected frame, or teardown) with its seconds, so the Log can say "· 4 s". Amends D39's
  "one entry, written at the start" for this kind only. A stretch cut short by a process kill is
  lost; rare, accepted.
- **"Se detuvo" on Status:** shown when the last Log entry is an OFF whose reason isn't user stop
  (screen off, system ended, app was closed, phone restarted), with that reason and time, until the
  next successful start. Derived from the Log; no new state. Screen-off counts: protection did end.
- **Typeface:** IBM Plex Sans, bundled (400 / 500 / 600 TTF in `res/font`, OFL licence shipped
  with it), as in the D44 mockup.


## D46 — Release can start without the accessibility service (2026-10-06, human decision, M6 ticket 17)

Amends D32's 2026-10-04 amendment (release Start gated on `MaskWindowService`) and D42's "Release
Start is disabled while the service is unbound".

- **Rule:** protection starts in every build with only the overlay permission. Without the
  accessibility service the mask is the app overlay at alpha 0.79, as D32 already described for a
  service turned off mid-session. The opaque accessibility window stays the recommended path.
- **Status:** "Mostrar la máscara" is the only item needed to start. "Máscara completa" moves to
  "Recomendado" next to Avisos, with its fix button. When it is off, the page reads "Listo para
  iniciar" with the line "Con la máscara algo transparente", and the note under Start says "Sin la
  máscara completa, la máscara se verá algo transparente". Start is disabled only while the overlay
  permission is missing. The debug-only "debug: máscara al 0.79" note is gone: every build says it.
- **Cost, accepted:** about 21 % of masked content shows through (D28, SPEC section 7 item 14) in that
  mode. Precise still needs the service for window shots (D35), so it falls back to Balanced as before.
- **Wizard (ticket 20):** the accessibility step can be skipped in every build, not only debug, and
  says what is lost. It is still offered first and checks the bound state live.


## D47 — The debug pill has a switch in the debug menu (2026-10-06, human decision, M6 ticket 19)

Settles ticket 19's "debug pill" item. "Texto en la máscara" (the "Hidden by Sophiel" chip) stays a
Parent setting in Ajustes as D28 and D40 say.

- **Rule:** the debug menu has a "Etiqueta de depuración" switch for the pill at the top of the
  screen (model, tile, timings), shown only in debug-signed builds. Default on, in memory
  (`AppContainer.debugPill`), applied live on the next frame: off removes the pill.
- **Amended 2026-10-07 (human):** default off. Turn it on in the debug menu when needed; it
  still resets to off with the process.
- **Still debug-only:** the pill itself never shows in a release build (D15/D18).


## D48 — Thorough preset: a 3x3 GantMan grid (2026-10-06, human decision, M6)

Scope addition to SPEC §1.2, decided after a benchmark. Preset order is now Light, Balanced,
Thorough, Precise, with the one-line help under each ("Revisa más zonas. Usa más batería.").

- **What.** `Preset.THOROUGH(3, 3)`: Balanced's 2x3 plus one column, same GantMan model, skin gate,
  cache and Sensitivity. Parent name "Reforzado" / "Thorough" ("Detallado" stays Precise's name).
  Log and Status show it by name. Precise's fallback is still Balanced tiles.
- **Why 3x3 and not 2x4.** Both add about 2-3 tiles; a column splits the width, where feed images
  and thumbnails sit, and a phone is tall so rows are already tall enough. 3x3 costs ~10 % more.
- **Benchmark** (`TileBenchmark.sweepCost`, 360x744 frames, skin gate on, sweep ms p50/p90):

  | Preset | A (SM-A715F) | B (SM-S721B) |
  | --- | --- | --- |
  | Light | 53 / 63 | 38 / 39 |
  | Balanced 2x3 | 253 / 314 | 190 / 230 |
  | Thorough 3x3 | 304 / 454 | 227 / 340 |
  | 2x4 (not shipped) | 303 / 404 | 228 / 318 |

  Per classified tile ~51 ms on A, ~38 ms on B; the gate skips 43 % of tiles at 3x3. Gate off
  (worst case) 3x3 is 453 ms on A, 340 ms on B: a little over the ~400 ms budget on A, accepted
  for a preset sold as heavier.
- **Not changed:** Balanced stays the recommended default.


## D49 — Run the wizard again from the debug menu (2026-10-07, human request, M6)

- **Rule:** debug-signed builds get a "Repetir el asistente" row in the debug menu, next to the
  pill switch (D47). It opens the setup wizard (D42) at step 1, through the door like every
  debug-menu action. The PIN made in step 1 replaces the current one; nothing else is cleared
  (Log, settings, permissions stay). Back on step 1 leaves the app, and the next launch opens
  Status with the old PIN still set.
- **Why:** checking the wizard on device otherwise needs an uninstall, which also wipes the Log and
  the accessibility grant.
- **Release:** unchanged. The wizard runs only while no PIN exists.

## D50 — A waiting probe holds its frame and skips the 80 ms throttle (2026-10-07, human decision, M6)

- **Rule:** while any tile is PROBING, a frame the lane can't take yet (one in flight) is decoded
  and held, not dropped: one at a time, replaced by any newer image, offered again every 10 ms. A
  probing tile also skips `FrameThrottle`'s 80 ms floor; one frame in flight still holds. With no
  probe waiting, frames drop exactly as before (SPEC.md §4.5).
- **Why:** probe exposure was mostly waiting for a frame, not inference (a probe tile judges in
  4-6 ms median, mostly cache hits). On a still screen the lifted mask brings one or two frames;
  when the throttle or a busy lane dropped them, no frame came after, and the probe timed out at
  300 ms, re-masked and backed off. Every timeout had a frame offered and dropped.
- **Measured** (same content, human-driven, both devices; probe exposure median / p90):
  | | A | B |
  |---|---|---|
  | Before (dropping) | 246 / 247 ms, 21 of 53 timed out | 163 / 194 ms, 25 of 66 timed out |
  | Held frames | 90 / 185 ms, 0 timeouts | 81 / 152 ms, 0 timeouts |
  | + skip lane work for still-masked probes (`35bcd28`) | 90 / 143 ms | 107 / 211 ms (cause not found) |
  | + throttle skipped | 61 / 88 ms | 66 / 101 ms |
  After rotation (M5: A 220/304, B 129/210): A 86/214, B 81/159 ms; no tile released then
  re-masked within 1 s (B had 3 before). Cost: ~7-9 extra frames a minute.
- **Not the old M3 rule:** "one frame per 80 ms" is gone from SPEC.md; the floor is a code
  constant (`FRAME_INTERVAL_MS`). A held frame is not the unbounded queue §4.5 forbids (D-entry
  "Backpressure", ticket 03): it is one frame, never more.

## D51 — A still screen confirms a tile flagged once (2026-10-07, human decision, M6)

- **Rule:** a CLEAR tile flagged in a frame waits for its second flagged frame (ENGAGE_FRAMES) as
  before, but if no new frame starts within 200 ms of that frame's sweep ending, it masks then,
  locked on the flagged frame's hash, as a new episode (`TileMaskTracker.confirmStill`). While a
  tile waits like this, frames are held rather than dropped, as for a probe (D50), so a changing
  screen always delivers its next frame first and the two-frame rule still decides there.
- **Why:** a still screen sends no frames (D17). On Device A a sweep takes ~600 ms, so a Gallery
  image that settles in one frame was flagged once and never again: exposed 3.7-7.8 s, until a tap
  changed the screen (seen in three runs, 2026-10-07). Device B never showed it (its sweep is
  short enough that the opening animation brings a second flagged frame). Without the fix the
  exposure has no bound.
- **Not chosen:** ENGAGE_FRAMES = 1 everywhere. It brings back one-frame mask blips while
  scrolling, which ticket 07 tuned away. A still screen is not a blip: the content stays.
- **Amends D50 and SPEC.md §4.5:** frames are also held (not dropped) while a tile waits to
  engage, so D50's "with no probe waiting, frames drop exactly as before" now reads "with no probe
  and no pending tile waiting". The 80 ms throttle skip stays probe-only.
- **Review fixes (same day):** the still check runs only after a frame that judged every CLEAR
  tile (one that skipped them proves nothing), is skipped if the capture size changed meanwhile,
  and logs the flagged frame's score (it logged 0). One held-frame retry task, not one per image.


## D52 — M6 verified on A and B; feature freeze (2026-10-11, human decision, M6 ticket 21)

- **Result:** SPEC M6 V1-V4 pass. V1 PIN gate, lockout and no PIN on disk; V2 Log per episode; V3 wizard
  from uninstall on A and B (ticket 20); V4 sensitivity and preset changes apply live through the cover
  path. Cover seen after 1 frame in 25 of 27 changes, 2 in the other 2.
- **V4 exposure, Device A** (`tools/cover_log.py A`, 27 changes, a masked still image in split screen):
  median of per-change medians ~73 ms, worst 306 ms (next worst 161). Settles to 52-90 ms after the
  first five changes (98-170 ms). Device B (ticket 10): 83 / 175 ms. Reported, not gated; a still
  image, so not comparable with the video runs.
- **Look and copy:** wizard steps without a mockup reviewed on device; English strings verified on B;
  largest font scale on A (Spanish) fits, apart from the "Ajustes desbloqueado" banner, which takes
  about a fifth of the screen (cosmetic, accepted: Samsung's own UI handles it no better).
- **Accepted without a run:** animations off on A, the ticket 15 wallpaper-accent check on B, and the
  ticket 17 Precise fallback line on B (accessibility off mid-session).
- **Known limitation found:** turning the accessibility service off briefly uncovers the masks while they
  move to the 0.79 app overlay (SPEC §7 limitation 22). Turning it on is seamless (D42).
- **Feature freeze starts.** Only bug fixes for failing verification items and measurement
  instrumentation from here (CLAUDE.md). Next: M7.

## D53 — Feature freeze lifted for M8 — Solution fit (2026-10-11, human decision, `/wayfinder` charting)

- **Why:** the app works but does not feel like it solves the parental-control problem: the Child can
  turn it off in seconds, it needs fresh consent every session and dies on reboot, the Parent learns
  only "N masks today", and it never escalates. The schedule is ahead (about 3 weeks left), so the
  D52 freeze is lifted for one time-boxed milestone.
- **M8 — Solution fit**, inserted before M7: about 2 weeks, then about 3-4 days of UI polish (a
  separate effort), then about 3-4 days of M7. Planned in `.scratch/m8-solution-fit/map.md`; the
  map's destination is a SPEC M8 section plus D-entries.
- **Scope direction (details decided in the map's tickets):** survive reboot and lost consent
  (accessibility screenshots as a capture fallback, hybrid preferred); a settings guard + Device Admin
  + start on boot (tamper resistance moves out of SPEC §1.3, Device Owner stays out); app names in
  the Log (amends D39 and D32); escalation (repeated hits block an app for a while) and per-app rules
  (blocked / trusted); a PIN-gated report shared through the Android share sheet.
- **Kept:** C1, no `INTERNET`. Store and policy limits no longer apply (Device Admin, boot receiver,
  `QUERY_ALL_PACKAGES`, accessibility events). Nothing sends messages by itself (one SIM phone to test).

## D54 — Protection and capture mode: hybrid capture behind one switch (2026-10-11, human decisions, `.scratch/m8-solution-fit/` tickets 01-03)

- **Protection vs capture mode.** Protection is on from the Parent's Start until a PIN Stop. Reboot,
  screen-off and lost consent change only the capture mode: **screen share** (`MediaProjection`, someone
  accepted the dialog), **accessibility** (`AccessibilityService.takeScreenshot`, no dialog) or **none**.
  The on/off state is persisted so it survives a reboot. CONTEXT updated.
- **One switch, "Seguir protegiendo siempre"** (Ajustes and the wizard's accessibility step, behind the
  PIN, default on; greyed with "Necesita la máscara completa" while the service is unbound). On: screen
  share whenever someone accepted the dialog, accessibility otherwise. Off: M6 behaviour exactly
  (protection ends with the share; D21 notification and D39 OFF reasons as before). No "accessibility
  only" value: the hybrid never asks for consent by itself anyway.
- **Switching.** Screen share → accessibility whenever the projection ends with protection on (screen-off
  on API 35+, secure lock, chip stop, revoked): masks and tracker carry over, no cover; first shot
  ~0.4 s later. Accessibility → screen share only through Start or the notification (fresh consent); the
  new session starts the projection, then stops the shots.
- **No boot receiver, no FGS, no notification in accessibility mode.** `MaskWindowService` starts the
  screenshot loop when it binds if protection is on (the system rebinds it at the first unlock after a
  reboot). The loop stops on screen-off (shots come back black, timers stretch) and restarts on
  screen-on. From targetSdk 35 a boot receiver may not start a `mediaProjection` FGS anyway.
- **Shot loop.** One display shot every 400 ms (Android's limit is 333 ms per service, separate from
  window shots). The `HardwareBuffer` is copied to a software bitmap, converted to sRGB (B returns
  Display P3), scaled and cropped to the §4.6 capture size and fed to the unchanged frame path; the
  buffer is closed at once (C3). Measured in ticket 02: shot 41/25 ms median (A/B), copy ~45 ms,
  scale + crop 20-27 ms; frame → mask ~215 ms median on both.
- **Probes on accessibility** lift their mask about one frame before the next shot is due (estimate on
  A: 0.15-0.25 s exposure, vs 0.45 s measured untimed). Probe timeout per capture mode: 300 ms screen
  share, 700 ms accessibility (300 ms flickered and stuck in ticket 02). A still exposes 2-3x more than
  on screen share: a limitation, no extra Parent warning.
- **Peek under mask defaults to on** (amends D34; applies only on API 34+ with the service bound).
  **Precise runs on both capture modes**: window shots have their own budget.
- **Rotation** keeps the D33 cover path (accessibility uncovered 376-520 ms in ticket 02, inside the 1 s
  cover wait).
- **Notifications and Log (switch on):** screen share ending with the screen on → notification
  "Protección en modo lento · toca para volver al modo rápido" (opens consent) and a Log entry
  "Compartir pantalla detenido"; ending with the screen off → nothing. Service turning off with
  protection on → "Protección interrumpida · toca para reactivar" (opens Status) and a **Sin
  protección** stretch. ON/OFF are written only for the Parent's Start and Stop; OFF reasons
  `SCREEN_OFF` and `RESTARTED` go away (amends D39, D21). Other mode switches are not logged.
- **Sin protección** (new Log kind): a stretch where protection was on, the screen on and nothing
  captured: start, length, reason (accessibility off, app stopped, restarted). Boot-to-first-unlock is
  not a gap. D39's last-alive inference now produces this entry instead of an OFF. Status shows a red
  "Protección interrumpida" with the reason while it lasts.
- **Status line** shows the mode: "Protección activa · compartir pantalla" / "· accesibilidad". No new
  button.
- **D46 kept:** Start needs only the overlay permission. Without the service there is no fallback: each
  screen-off ends the share, posts the D21 resume notification and leaves a Sin protección stretch
  until the next Start. The "Máscara completa" Recomendado item adds "y sigue protegiendo tras apagar
  la pantalla o reiniciar".

## D55 — Screen share survives screen-off below API 35 (2026-10-12, human device check, M8 ticket 10)

Amends D18's screen-off teardown.

- **Rule:** below API 35, `ACTION_SCREEN_OFF` pauses frames (closes them unread) instead of ending the
  share; frames resume at screen-on with the same session. On API 35+ (Device B) the teardown stays: the
  platform ends the projection on a secure keyguard, as D18 found.
- **Checked:** on Device A with the teardown disabled, the share was alive after screen-off and unlock.
  Per-run details (secure lock or not, 10 s vs 2 min, `onStop()`) were not recorded; M8 V1 re-checks.
- **Consequence:** A stays on screen share all day after one consent; the screen-share FGS notification
  stays during screen-off. A falls to accessibility only when the share really ends (chip, system,
  reboot).

## D56 — Tamper guard (2026-10-12, human decisions, M8 tickets 04-05)

Amends D38 (Unlock) and D53's scope direction (no Device Admin, no boot receiver).

- **What:** while a PIN exists and no Unlock is active, the accessibility service closes system pages that
  could turn Sophiel off and opens Sophiel's PIN pad instead, with a line saying the setting is protected.
  Back goes Home. A page is guarded only while the thing it turns off is on, so turning something *on* is
  never blocked. The wizard runs before a PIN exists, so it is never guarded.
- **Guarded pages** (matched by package + on-screen text "Sophiel", never class names; One UI
  packages confirmed on A and B at build time): Accessibility > Sophiel and its "Stop Sophiel?" dialog
  (including Samsung's accessibility app); "Appear on top" for Sophiel; Sophiel's App info (Force stop,
  Clear storage, Uninstall); the uninstall dialog for Sophiel (covers launcher drag-to-uninstall); Quick
  Settings "Active apps" when it lists Sophiel (screen-share mode only).
- **Not guarded:** the screen-share "Stop sharing" (the hybrid falls to accessibility, D54); Sophiel's
  notification settings; the accessibility shortcut, safe mode, adb, factory reset, uninstall from the
  Play Store app. A tap faster than the guard (one-tap Active apps Stop) wins: the race is a limitation
  and the guard delay is measured on A.
- **Unlock from the guard (amends D38):** a correct PIN starts the normal Unlock and Sophiel steps aside,
  back to the page. That Unlock survives leaving the app; it still ends after 2 min or at screen-off.
  The guard is off while any Unlock is active.
- **Tamper attempt** (Log kind): time + target (accessibility off / Appear on top / App info / uninstall /
  Active apps). The same target within 60 s merges into one entry with a count. A force stop that wins
  the race, or comes from adb, shows as D54's Sin protección (reason: app stopped).
- **No Device Admin** (future work): no `DeviceAdminReceiver`, no wizard step; `adb uninstall` keeps
  working, so no debug escape hatch is needed. **No boot receiver** (D54).
- **Wizard and Status:** no new step. The accessibility step and the "Máscara completa" item add "y
  protege los ajustes de Sophiel". Without the service there is no guard.
- **Service config:** `typeWindowStateChanged` (no content-change events) and `flagReportViewIds`.
  Node text is read only on window-state events from Settings, the package installer, SystemUI and
  Samsung's accessibility app, and only to find "Sophiel". Event volume and battery fold into M7 V3.

## D57 — Episode app in the Log (2026-10-12, human decisions, M8 ticket 06)

Amends D39 ("no app names") and D32 (window list read for attribution).

- **Source:** at episode start, the topmost `TYPE_APPLICATION` window under the highest-scoring masked
  tile (the lookup `MaskWindowService.appWindowShot` already does); package from
  `window.root.packageName`. Never text, titles or nodes beyond the root. Works on API 33.
- **Unknown cases → blank** ("App desconocida"): no accessibility service (no `UsageStatsManager`
  fallback), only system UI under the tile, Sophiel itself. The launcher counts as an app.
- **Episodes are not split by an app change:** an episode keeps its first app (`ponytail:` comment in
  code naming the ceiling). D39 otherwise unchanged.
- **Schema:** package appended as the last field of `MASKED` and `UNANALYZABLE` (stamped at stretch
  start). Labels resolved at read time through `PackageManager`; an uninstalled app shows its package.
- **Amended 2026-10-15 (ticket 24, agent):** `<queries>` also lists MAIN/HOME beside MAIN/LAUNCHER. A launcher
  usually has no LAUNCHER activity, so without it the launcher (which counts as an app) would show its package.
  Old lines without the field read as blank: no migration. `<queries>` for the MAIN/LAUNCHER intent, not
  `QUERY_ALL_PACKAGES`.
- **Privacy line** (LIMITATIONS, README): "The Log names the app that was on screen when something was
  hidden: the app's name, never what was in it. No pixels, no text, no window titles. It stays on the
  phone for 7 days."

## D58 — Escalation and per-app rules (2026-10-12, human decisions, M8 ticket 07)

- **Escalation:** fixed rule, no tuning: 3 masked episodes with the same Episode app within a rolling
  10 min → that app is blocked 15 min. One switch "Bloquear apps que muestran contenido explícito
  repetidamente", default on. Unanalyzable stretches, blank app, trusted and excluded apps never count.
  The block fires at once, even with the app in front. The count restarts after a block ends. Not tied
  to Sensitivity.
- **Enforcement (accessibility only):** `typeWindowStateChanged` from **all** packages (D56's package
  filter is dropped; still no content events). A blocked app coming to the front → `GLOBAL_ACTION_HOME`
  + Sophiel's block screen. Only while protection is on, including during an Unlock. Without the service:
  no blocking and no trusting; the Apps rows show disabled with "Necesita el servicio de accesibilidad".
- **Block end times are wall clock** and survive reboot (re-checked when the service rebinds). A PIN Stop
  clears active escalation blocks.
- **Block screen** (full-screen activity): app icon + label; escalation "Bloqueada por Sophiel · vuelve
  en N min" with a small "Desbloquear con PIN" (D38 door; a correct PIN ends the block and resets the
  count); rule "El Padre bloqueó esta app", no PIN button. "Ir al inicio"; Back goes Home.
- **Trusted app in front:** masks drop at once and inference stops (screen share: images acquired and
  closed unread, VirtualDisplay kept; accessibility: shots stop). A split screen or PiP is trusted only
  if every app window is. Scanning resumes on the next window change to a non-trusted app.
- **Settings "Apps"** (behind the door): escalation switch, search field, launchable apps by label
  (changed ones pinned first), each Normal / Bloqueada / De confianza; one-line warning that trusting a
  browser lets anything through. **Excluded** from the list and from escalation: Sophiel, Settings, the
  default launcher, the default dialer.
- **Log kinds:** "App bloqueada" (escalation: time + app + minutes, one per block) and "Apertura
  bloqueada" (time + app + reason rule / escalation, 60 s merge as for tamper attempts). Trusted apps
  write nothing.
- **Not built:** `setPackagesSuspended()` (needs system or Device Owner), UsageStats polling, daily usage
  limits, temporary allowances.

## D59 — Log dashboard, text report, Status blocks (2026-10-12, human decisions, M8 ticket 08)

Amends D44/D45's Log layout. Prototype: branch `proto/m8-log-report` (794a92c), `mockups/log-report.html`.

- **Log screen:** top, summary chips (tapados · más en <app>, intentos, bloqueos), a 7-day bar chart of
  masked episodes per day, and a "Por app" list with bars (masked count per app, 7 days). Below, the
  Todo / Tapados / Otros filter and rows grouped under day headers with the day's total ("Hoy · 8
  tapados"). New kinds use the same row. No per-app drill-down.
- **Report:** "Informe" on the Log header (already behind the door). Bottom sheet: Hoy / 7 días, a
  plain-text preview, "Compartir…" → `ACTION_SEND` text/plain. Contents: period, date, counts of tapados
  / sin revisar / Sin protección (times) / tamper attempts / apps bloqueadas, top 3 apps, per-day counts
  for 7 días, and "Solo cifras. Sophiel no guarda imágenes." Text from string resources; counts only what
  is still in the 7-day Log. No FileProvider, no image card (future work). Sophiel itself sends nothing.
- **Status:** one orange line per active escalation block, "Instagram bloqueada · 12 min", counting
  down. Rule blocks are not shown.

## D60 — M8 written into SPEC; schedules are a stretch goal (2026-10-12, M8 ticket 09, human-reviewed)

- **SPEC:** M8 — Solution fit sits between M6 and M7 (D53), ~2 weeks, built in the map's priority order:
  capture mode, guard, episode app, escalation and rules, Log and report. §1, §3, §4, §7 and §9 are
  updated; M7's soak, feel numbers and demo runbook now cover both capture modes and the M8 story.
- **Schedules (stricter protection at night, time windows) are an M8 stretch goal** (human, on review). Not
  specified: plan them before building, only with slack once deliverables 1-5 pass. A reminder
  notification for the daily / weekly report (the Hoy / 7 días report, D59) can ride with them; Sophiel
  still never sends anything by itself.
- **Confirmed on review:** the guard's PIN-pad line "Este ajuste está protegido"; `ui/BlockActivity`;
  `isAccessibilityTool="true"` only if B hides the "Stop Sophiel?" page from the service.
- **SPEC §2 corrected:** `targetSdk = 37`, as in `app/build.gradle.kts` (found in ticket 04).

## D61 — A stretch found after a reboot with accessibility off starts at the boot (2026-10-14, human decision, M8 ticket 16 open item)

Amends D54's "boot-to-first-unlock is not a gap" for this one case.

- **Found (ticket 16, B):** reboot with protection on and the accessibility service off: nothing starts Sophiel
  (the system rebinds only an enabled service), so the Sin protección stretch opened only when Sophiel was
  opened, and the hours before were hidden.
- **Rule:** a stretch found at app start, in the first process since the boot, begins at the boot (wall clock
  at boot). A later process in the same boot begins it at app start, as before. The heartbeat's "restarted"
  stretch before the shutdown is unchanged, so a reboot reads as two entries: "El teléfono se reinició" and
  "La accesibilidad está apagada".
- **Trade-off:** boot to first unlock is counted in this case (usually seconds; longer if the phone sits
  locked after booting). It errs towards telling the Parent; power-off time is still never counted. No boot
  receiver (D54).
- **With the service on** nothing changes: the bind at the first unlock starts the process, capture resumes,
  no stretch is found.

## D62 — Log polish: one-ink day bars, folded lines (2026-10-16, human decision, UI polish)

Amends D44 / D59's Log rows and bars; the D59 layout is unchanged.

- **Day bars are one ink** (today brightest), not the day's highest band colour: on the dark hero the pink /
  brown read as unexplained. The band stays on the rows' tags.
- **Rows are a timeline:** time in its own left column; a line about an app leads with the app, its icon says
  what happened. On, off and pause lines are one plain grey line.
- **Folding in the list only** (counts and the report still see every entry): back-to-back masked episodes
  in one app (10 min gap) → one line that opens; unchecked stretches in one app → one line with the time
  summed; a stop and the turn-on after it → "En pausa 8 min".
- **Por app rows are their bars:** app icon and name over an accent fill as wide as the app's share of the
  top app, count at the end. Each fill takes the hue of its app's icon (grey for a grey icon).

## D63 — Card tint, no card borders, plainer Log labels (2026-10-16, human decision, UI polish)

- **`Palette.Card` is `#F9FBF7`, not white:** pure white glared under the dark hero (tester feedback). The
  1 dp outlines on Status, Apps and Log cards are gone; the tone step against the chalk sheet and the 1 dp
  row dividers do the separating.
- **Log labels say what happened** (ES and EN): "Veces sin protección" (time under it), "Intentos de apagar
  Sophiel", "Aperturas bloqueadas" (grey, not orange: the parent's own rules working), filter "Otros" →
  "Protección". Row titles unchanged.
- **Verdict line, then only non-zero figures:** "Todo en orden esta semana" (green) or "Para revisar esta semana"
  above the tiles. Unprotected and attempts make it "look"; blocks (the parent's own rules) never do. A tile at 0 is hidden.
- **Confidence is debug-only:** the Muy seguro / Seguro / Dudoso tag, the score and the band colours show only when
  `isDebuggable`; a release build shows every cover in one green, with the review level and zones in the detail.

## D64 — Warm paper: two surface roles (2026-10-16, human decision, UI polish)

Amends D63's tint. A tester found the app "too white" (GNOME white, not Apple white); four page/surface pairs were
compared in `.scratch/mockup-white.html` and the human picked the warm one (D).

- **Two roles, not four colours:** `Palette.Chalk` is the page, `#EAE8E3`; `Palette.Surface` is the one off-white that
  rises off it, `#F9F8F5`. `Card` and `Win` are aliases of `Surface`, so cards, tiles, the PIN sheet, dialogs, the block
  screen, overlays and the selected pill in the segmented control are one value. `Head` follows the page.
- **A lift, not an outline:** cards get `Modifier.lift` (1 dp warm shadow) in place of D63's removed borders; `Border`
  (dividers) is warm too. Switch thumbs stay white.
- **Seen on B:** Status, the PIN sheet. The rest of the screens move to the new visual style separately.

## D65 — One ink, one header (2026-10-17, human decision, UI polish)

Amends D44's accent and the flat grey header. An audit found Status and Log (dark tile grid, green) and the other screens
(grey bar, wallpaper-derived accent, teal on A) looked like two apps.

- **No Material You:** `primary` is `Palette.Moss` (`#12201A`, the hero's ink, as Status's buttons already were). Pill
  buttons, the PIN confirm key and the segmented thumb follow it. The accent for "on" and "chosen" things (switch track,
  check, wizard dots, links, Recomendado tag) is `Palette.GreenFg`, so state still reads as green.
- **One header:** `HeaderBar` is the dark tile strip with the back chip and a left-aligned title (Log's top bar), on
  Ajustes, Apps, the wizard, Cambiar PIN and the debug menu. The unlocked countdown is the `UnlockedChip` in its end slot;
  the tinted `UnlockedBanner` is gone. Status and Log keep their taller heroes.

## D66 — Wizard: welcome step, tile progress, a tile visual per step (2026-10-17, human decision, UI polish)

Amends D42/D44's wizard frame; the step order and the permission logic are unchanged.

- **Step 0, welcome:** the Status hero on the Protected page (the lock shuts, camo spreads) with one "Empezar" button and
  "Son 7 pasos cortos". It has no progress and no back; Back from step 1 returns to it.
- **No header bar:** a back chip and seven progress tiles (12 dp, filled green up to the current step) stay on top while the
  steps slide under them. Step 1 (PIN) is the keypad itself, with no visual.
- **A visual in the middle of steps 2-7** (`TileVisual`): a 5x3 tile grid on a 4.2 s loop, each doing what the step is about:
  a layer sweeping over (overlay), see-through then opaque (accessibility), three tiles breathing (keep awake), a ping
  (notifications), the scan over 4/8/12/15 tiles by preset (how it checks; follows the choice), the wave that covers all
  (start). Reduced motion draws one still frame.
- **Cómo revisa and Cuidado stay on one step (6)**, so the wizard stays at 7 steps.
- **Last step has two exits:** "Iniciar protección" and "Ir a la app" (leaves the wizard without starting).
- `GridHero` now clips its tiles to its own height (they drew one row past it, over whatever sat below).

## D67 — Log critique: quieter list, one bar ink (2026-10-18, human decision, UI polish)

- **One unchecked line per day** ("No se pudo revisar · … · N veces"), across apps; it names the app only when all agree. Replaces the per-app 10 min fold for unchecked stretches (counts still see every stretch).
- **Por app bars take one neutral ink**, minimum 40% wide so the label stays on its fill. Amends D59/D62's per-app icon hue; `appHue` is gone.
- **Band words are Explícito / Probable / Dudoso** (was Muy seguro / Seguro / Dudoso): debug builds only, and "Muy seguro" in red read as good news.
- **The hero's "Para revisar" names its cause** (gap time, else attempts). Stat tiles count in ink; colour stays on the icon and detail.
- **Borrar historial** sits 24 dp under "Mostrar más", neutral pill with red ink (Destructive fill dropped).

## D68 — Log by app: a screen for every app with something covered (2026-10-18, human decision, UI polish)

- **Por app card shows the top 4** and a last row, "Ver las N apps" (or "Ver detalle por app" up to 4), in body weight. The card sits under the hero, above the stat tiles; the tiles carry a down chevron because they are filters.
- **New screen inside the Log** (`LogAppsScreen`): a summary (the week's count large, "N apps"), then every app with a covered episode, most first: icon, name, count, a thin bar under the name (share of the top app, neutral ink, never behind text), last time. A final row, "N sin app identificada", makes the list add up to the hero. Tapping an app opens its episodes only, grouped by day, leading with what happened ("12 episodios") not the app's name. Back goes app, list, Log.
- **Counts only, no usage time.** Per-app screen time and `UsageStats` stay out (SPEC §1.3, D58); the metric is covered episodes. A ring chart was rejected: slower to compare than bars, needs a colour legend, and reopens D67's one ink.
- **The home launcher's covers read "Inicio y recientes"**: Recents is drawn by the launcher, so covers over other apps' thumbnails file under it. Kept (they protect the Child); renamed (the launcher's own name read as an app the Child opened). Resolved at read time from the default home app.
- **Fixes found on device:** the Log's top bar height was measured without the status bar, so the pinned filter sat about 24 dp too high and its labels clipped; the status-bar icons stayed dark on the dark hero after returning from the by-app screen.

## D69 — Motion pass: welcome on cold open, loading covers under 200 ms (2026-10-18, human decision, UI polish)

- **A welcome on the first Status hero of each process** (`GridHero`): the badge fades in and springs up from 80%; off Protected a ring pulses out. On Protected the padlock starts open and falls shut after about 0.4 s, with the usual ring and squash but no haptic (it is not an event). Returning to Status, or rotating, shows the hero as it is. **Amends D44** ("opening the screen shows it as it is") for a cold open only. Reduced motion: none.
- **Loading covers move 200 ms at most:** the Log's content and empty state fade in over 180 ms, and the skeleton no longer pulses (it was an 800 ms loop). Slides, the sheet rise, hero colours, the lock ring and the Wizard progress keep their longer times, on purpose.
- **Seams that teleported now move** (all through `motion()`, so Remove animations still snaps them): the Log filter fades the list in (160 ms); the radio tick fades and grows from .8 (`SelectTick`, 120 ms); disabled rows dim over 200 ms (`Modifier.dimmed`) when the service binds; PIN dots fill over 100 ms.
- **Tuned:** the hero height takes `Ease` (was the stock curve); the segmented thumb is 220 ms (was 280).
- **Left alone:** `motion()` still snaps colour and opacity fades under Remove animations (D44); a gentler fallback stays an open question.

## D70 — Bolder neutrals, an unlocking padlock, a glance card, regrouped Ajustes (2026-10-20, human request, UI polish)

Amends D64's surfaces and D65's ink. The human asked for a bolder look: pure white for light elements, a dark grey
(not black) for dark ones.

- **Neutrals:** `Surface` is `#FFFFFF`, the page `Chalk` a cool `#ECEDEF`. The ink is `#1C1E21` at full strength for
  text (`Fg`), `.74` for `Dim`. `Moss` is now a dark grey, `#26282C`; the hero and header tiles are greys too, with only a
  trace of green, red or amber per page. Light inks on the dark (`Text0`, `OnMoss`) are pure white. Cards round to 16 dp
  with a 2 dp lift; display and title sizes went up a step.
- **The PIN sheet's padlock:** a large upright padlock (the hero's drawing, `drawPadlock` with no tilt) heads the sheet.
  A wrong PIN rattles it, lockout turns it red; the right PIN springs the shackle open, turns it green, and shrinks it
  toward the top end, where the header's open-lock `UnlockedChip` takes over. Reduced motion: it snaps open, 450 ms, done.
- **De un vistazo:** unlocked, Status's proof card becomes a glance card: today, 7 days, time unprotected, attempts to turn
  it off, the top three apps with their launcher icons and counts, and Ver historial. Locked, the D44 proof card stays,
  since per-app figures are behind the door like the Log.
- **Ajustes groups:** Protección (Cuidado, Revisión) · Apps y actividad (Apps, showing the icons of apps with a rule;
  Historial) · Máscara · Seguridad (Seguir protegiendo siempre, Cambiar PIN).

**Amended 2026-10-20 (human):** the Moss family is back as D65 had it (`Moss` `#12201A`, the hero and tile greens, the
mint-white inks on them). Pure white surfaces and the dark grey text ink stay. The glance card is under review in
`.scratch/mockup-d71.html`.

## D71 — Critique pass: a wall that counts, one week row, a Historial that tells the day (2026-10-20, human request, UI polish)

From the `/impeccable critique` of 2026-10-20 (25/40); the human asked for all five fixes. Amends D44, D69, D70.

- **The hero wall counts (GridHero):** on Ready and Protected, today's masking episodes are tiles wearing the mask camo, one per
  episode, at fixed hash-ordered places off the text. Protected's ambient band is now a scan line sweeping top to bottom every
  6 s. Ready is still (its band is gone). The badge loses the mask-noise fill; the padlock stays (launcher mark, D70's PIN sheet).
- **Status sheet:** "Todo en orden" no longer gets a card on Ready or Protected (only failing checks do; D44's "show only what
  is wrong"). The "Revisión · Cuánto tapa" line above Start is gone. Proof and the D70 glance card become one `WeekRow`: the
  7-day count leads, today and the last cover under it, the day bars, and the row itself opens the Historial. Unlocked adds a
  "Para revisar" row (only when there is a gap or a protected setting opened) and the top three apps; the four figure tiles are
  gone. Resolves the glance-card review in `.scratch/mockup-d71.html`.
- **Historial:** each day opens with its title and one summary sentence ("12 tapados, casi todo en Galería · 7 h sin
  protección"). A stop and turn-on less than a minute apart leave no line. Wording: "Intento de apagar Sophiel" is "Abrió un
  ajuste protegido" (and its counts), the setting lines name only the setting ("Accesibilidad"), "N partes" is "N zonas tapadas", "N episodios" is "N veces".
- **Unlocked chip:** the open padlock in a ring that empties over the 2 min window; the time and "Bloquear ahora" are spoken,
  not printed. A tap still locks.
- **Sub-screen header:** plain Moss, no strip of the tile grid (it read as a cut). The grid is for the two heroes only.

## D72 — A language switch in Ajustes (2026-10-20, human scope addition, UI polish)

The human asked for a Spanish / English toggle. Ajustes gets an **Idioma** group under Seguridad: one segmented control,
"Español | English", each name in its own language. It sets the app's own locale through `LocaleManager` (Android 13+), so
Android keeps the choice and recreates the screen; `res/xml/locales_config.xml` also lists Sophiel in the system's per-app
language page. Below Android 13 the group is hidden and the app follows the system language (appcompat would be needed).
Also: Status's week row puts today and the last cover on two lines ("Hoy: 0" / "Última: hace 4 días"); joined, they wrapped
mid-phrase in Spanish.

## D73 — Second critique: a lit hero, one summary in Historial, the PIN lock in Moss (2026-10-20, human request, UI polish)

From the second `/impeccable critique` (28/40, up from 25); the human picked "glow + fade + clear tiles" and every other fix.

- **Hero light (GridHero):** a mint radial glow centred on the badge (.16 on Protected, .09 on Ready and Starting, none on
  the stopped, broken and off pages) and an edge fade into the page colour, so the wall reads lit from the middle. Today's
  covered tiles get a stronger camo and a 1 dp mint edge. The hero's floor is 360 dp (320 pushed the unlocked badge under
  the header). Not "light as status": the glow stays mint, the page colours already carry the state.
- **Historial:** the figure cards under "Por app" are gone; the hero's verdict ("Para revisar" and its cause) is the week's
  one summary and now runs full width under the count and bars. Blocks are still in the rows and the day lines.
- **PIN sheet:** the padlock disc is Moss with a light lock (mint on the right PIN, the hero's red ink on lockout), the
  hero's badge rather than a grey disc. Amends D70's colours, not its motion.
- **Small:** the week row's bars are 24 dp and centred on "Hoy / Última"; the countdown ring is 2.5 dp; "Revisar sin
  destapar" reads "Sin parpadeo. Necesita Máscara completa."

## D74 — The wizard joins the app's world; dates follow the app's language (2026-10-20, human request, UI polish)

From the third `/impeccable critique` (Protected state and the wizard); the human took all four fixes.

- **Wizard steps:** the top is a Moss band like every parent header (back chip on dark, a mint progress bar), so the light
  status icons belong. The tile drawing (`TileVisual`) is a small window onto the hero: a Moss panel with a mint glow, the
  hero's tiles, covered ones in lit camo with a mint edge. It is the same drawing in Ajustes's Revisión sheet.
- **PIN creation** (wizard and Cambiar PIN) opens with the PIN sheet's mark: the light padlock, shut, on a Moss disc.
- **"Todo listo"** is the welcome's bookend: the hero (Ready) says it, the capture notes and the buttons sit under it. No back
  chip; the system Back still steps back.
- **One button shape:** every text button is a 12 dp rounded rectangle, the main buttons' shape; the wizard's pills are gone.
- **Language bug (from D72):** day letters and "hace N minutos" read the Activity's locale (`appLocale()`, `relativeTime()`
  through ICU), not `Locale.getDefault()` or `DateUtils`, which could still be the phone's language after a cold start.

## D75 — Design A ported: Geist, Esmeralda, the state card and a bento (2026-10-21, human request, UI polish)

The human approved "A · Bento expresivo" (`.scratch/sophiel-a-final.html`, `-screens.html`) and chose Esmeralda and the cookie badge.
Reopens D45 (Plex), D64/D70 (paper and Moss hero) and D74's button shape; D73's mint glow is dropped.

- **Font:** Geist (OFL, bundled in `res/font`, licence in `assets/licenses`); Plex removed. Display titles are Bold 48 sp, -.04 em.
- **Colour:** Esmeralda `#00804A` (`Palette.GreenFg`, white on it 5.0:1) wherever green means protected; `#3FD08F` (`Mint`/`Green`) for green on dark.
  Ink is `#08201A` (`Moss`) and does every primary job (buttons, switches, ticks). Page is `#F1F2F1`.
- **Status:** no state chip in any state. A 28 dp state card (tile wall, cookie badge when armed/protected, rounded square when not, headline,
  subline) with the capture mode (D54) as one quiet line under the promise on Protegido only. Below it a bento: Historial (the 7-day count, green
  only while protected, white otherwise; "N hoy"; day bars), Apps (count of Bloqueada apps; `APPS_BADGE_COOKIE` in `StatusScreen.kt` flips it to a
  tile), Revisión (preset on a 3-step scale; Detallado lights no step). The nav bar stays gone: gear, tiles, one full-width action.
  The old "Ver en Historial" row, week row and top-apps glance are gone; unlocked, "Para revisar" stays as one row.
- **Buttons:** Status's Start/Stop are 60 dp, 22 dp radius; Stop is outlined in ink. Other text buttons keep D74's 12 dp.
- **Other screens:** parent headers are on the page (white 44 dp back disc, bold title, no Moss band); icons sit in soft tiles; Ajustes opens
  with two tiles (Cuánto tapa, Revisión) that show their scale; the wizard's top is the same header with an ink progress bar and its
  welcome/"Todo listo" heroes are the state card; the Parent-block screen is flat ink.
- **Not ported yet:** Historial's dark hero, the PIN sheet's tile slots, the wizard pictures' step-specific art and the Ajustes sheets' check
  style still follow D70-D74 (recoloured by the new palette).

## D76 — C1 reopened for M9 Remote (2026-10-20, human scope change, `/wayfinder` charting of `.scratch/m9-remote/`)

A professor requires a password-protected remote Parent function. This reopens C1, hard rule 1 and §1.3's "remote parent
alerts, accounts, sync, backend" on purpose, for a new milestone **M9 — Remote** after M8 sign-off and UI polish, before M7.

- **Two flavors:** `offline` keeps C1 and its `aapt` check unchanged; `remote` adds `INTERNET`. Details in M9 ticket 07.
- **One APK, two roles** chosen on first launch: Parent phone (needs a login for everything) and Child phone (never logs in; pairing replaces it, M9 ticket 04).
- **Remote does exactly four things:** fetch the newest Log entries, set apps Normal / Bloqueada / De confianza (D58), Start, Stop.
  Remote Start only works in accessibility capture.
- **Pure relay:** the server (Bun + Elysia, host by research) stores Parent accounts and Pairings only; every message is end-to-end
  encrypted and never written. An offline Child phone means the request fails. A mailbox that holds messages is a stretch goal.
- **Pairing by QR** carrying the key, shown on the Child phone behind the door (D38); one account, many named Child phones; the Child
  phone's Status says it is paired; either side unpairs, and unpairing deletes the key.

## D77 — The relay runs on one always-on Fly.io Machine (2026-10-20, research, M9 ticket 01)

Fly.io: one `shared-cpu-1x` 256 MB Machine, a 1 GB volume for the SQLite file, `auto_stop_machines = "off"`, about $2.34 a month
(shared IPv4 and `*.fly.dev` TLS free). Fly's proxy is reported to cut a connection after ~60 s of silence, so the server's
`idleTimeout` is 25 s and the server pings. Elysia pinned to 1.4.x (2.0 is only an rc); `.ws()` is built in, runs in an `oven/bun` image.

- **Fallback:** Railway Hobby, flat $5 a month, WebSockets exempt from time limits.
- **Rejected:** Render (free tier sleeps after 15 min, no disk), Koyeb (free scales to zero, volumes in preview), Hetzner (unavailable,
  self-managed TLS), Oracle free tier (reclaims idle instances). Anything that sleeps drops the Child phone's socket.
- Findings: branch `research/m9-hosting`, `.scratch/m9-remote/research/01-hosting.md`.

## D78 — The Child phone's socket lives in the process the accessibility service keeps alive (2026-10-20, research, M9 ticket 02)

The WebSocket is held by the app process while `MaskWindowService` is bound (protection on or off). AOSP binds accessibility services
with flags that keep network access through Doze, App Standby and Android 15's background restriction. With the screen off Doze ignores
the app's wakelocks unless it is battery-exempt, so Remote asks for `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (D82 says where). Phone-side
timers stop in deep sleep, so the **server** pings; the phone reconnects on network change, screen on, and a 5-minute
`setAndAllowWhileIdle` alarm. Client: OkHttp 5 behind a small `callbackFlow` (hard rule 5).

- **Worst case on A:** silently dead for up to one 5-minute check plus reconnect. Unreachable while the service is off (amended by D83)
  and before the first unlock after a reboot.
- **Rejected:** high-priority FCM (Firebase, 4 KB payload, demoted within 7 days, Google sees metadata); a foreground service for the
  socket (breaks D54's "no FGS in accessibility mode"; `dataSync` capped at 6 h a day on 15+); WorkManager (does not run in Doze).
- Findings: branch `research/m9-reachability`, `.scratch/m9-remote/research/02-reachability.md` (five device checks listed there).

## D79 — Protocol: email accounts, QR pairing, end-to-end AES-GCM through a pure relay (2026-10-20, human decision, M9 ticket 04)

- **Account:** email + password; the email is only a login name (no verification, no mail of any kind). Hash with `Bun.password`
  (argon2id). Forgotten password = new account and re-pair. No password change.
- **Session:** login returns a random token; the server keeps its hash; the Parent phone keeps it in app-private storage until logout
  or account deletion.
- **The Child phone never logs in** (rewords D76's "login optional"). Behind the door it opens a socket, gets a one-time **claim code**
  (10 min, memory only) and shows a QR with the claim code + a fresh AES-256 **pairing key**. The logged-in Parent phone scans it and
  sends the claim code; the server binds a pairing to the account and sends the Child phone a **device token** over the socket. The
  server URL is fixed in the build, not in the QR. "Mostrar código otra vez" mints a fresh claim code with the same key, so another
  Parent phone of the account joins the same pairing.
- **Names** live only on the Parent phone, in its local list (pairing id, name, key). A reinstalled Parent phone must scan again.
- **Envelope:** AES-256-GCM (`javax.crypto`), random 96-bit nonce per message, pairing id as associated data; inside, a message id and
  a timestamp. The Child phone rejects anything older than 2 min or an id already seen.
- **Transport:** Parent phone POSTs one request; the server forwards it down the Child socket and holds the POST until the reply (10 s
  timeout), or answers "sin conexión" at once if no socket is open.
- **Log fetch** sends the whole 7-day Log and the Apps list (each app with its `rule`; `rules` replies carry the list too), gzipped before encryption; the Parent phone holds it in memory only. Messages cap
  at 256 KB.
- **Unpair** from either side deletes key and token on the phones and the row on the server; the side that was offline cleans up on its
  next contact (rejected token or unknown pairing id). Logout deletes the session; "Borrar cuenta" deletes account, sessions, pairings.
- **Server stores, exactly:** on disk `accounts(id, email, password_hash, created_at)`, `sessions(token_hash, account_id, created_at)`,
  `pairings(id, account_id, device_token_hash, created_at)`; in memory claim codes, open sockets, requests awaiting a reply; never
  message bodies, Log entries, app lists, names, keys or last-seen times. It logs no bodies and no ids.

## D80 — Pairing QR: ZXing core to draw, Google code scanner to read (2026-10-20, research, M9 ticket 03)

- **Draw:** ZXing `core` 3.5.4, a plain JAR with no manifest; `QRCodeWriter().encode()` to a `BitMatrix` drawn on a Compose `Canvas`.
- **Read:** `play-services-code-scanner` 16.1.0: no CAMERA permission, a few lines (`GmsBarcodeScanning.getClient().startScan()`), needs
  Play services; the `barcode_ui` meta-data installs the module with the app.
- **Cost, accepted (D83):** the scanner pulls `transport-backend-cct`, which merges `INTERNET` + `ACCESS_NETWORK_STATE` and sends ML Kit
  usage metrics to Google; the terms require telling users. Both deps are `remoteImplementation`; never strip its permissions with
  `tools:node="remove"`.
- **Fallback, not taken:** zxing-android-embedded 4.3.0 (CAMERA, no INTERNET). ML Kit bundled + CameraX rejected (CAMERA, ~2.4 MB,
  still INTERNET).
- Findings: branch `research/m9-qr`, `.scratch/m9-remote/research/03-qr.md`.

## D81 — The Parent phone's screens: one state card per Child phone (2026-10-21, human decision, M9 ticket 05, variant B)

Chosen from `.scratch/m9-remote/prototype-parent-screens.html?variant=B`, in Design A's world (D75).

- **First launch** (Remote build only) asks the role, Hijo by default. Padre skips the protection wizard and goes to login.
- **Login / sign-up:** password 8+ characters, one amber "no recovery" warning on sign-up, one error for wrong email or password.
  Back to the Child role only while logged out, from the login screen's last link.
- **Home:** one small state card per Child phone, all fetched on open, each stamped "hace N s", with Start or Stop on the card. Cards
  for "Sin conexión" (Reintentar) and "Se desvinculó" (Quitar de la lista). Loading is a skeleton card; an in-flight action spins in its
  pill; a failure drops a banner under its card naming the reason ("Ya estaba protegido", "Protección iniciada", or the failure).
  The empty list says where the Child phone's QR is. No last-known state is kept for an offline phone.
- **Adding:** scan (with a line disclosing the Google scanner), then name the phone. A bad scan names the fix ("Mostrar código otra vez").
- **Per Child phone:** tabs Historial (the Child's Log rows) and Apps (D58's rows and rule sheet). Apps arrive as labels only, drawn as
  letter tiles; no icons on the wire.
- **Cuenta** (gear): Cerrar sesión, Borrar cuenta.

## D82 — What Remote changes on the Child phone (2026-10-20, human decision, M9 ticket 06)

- **Pairing screen:** Child role, Remote build only. An Ajustes row "Teléfono del Padre" behind the door (D38): QR, "Mostrar código otra
  vez", "Desvincular", and a connection line ("Conectado" / "Sin conexión"). The battery exemption is asked here before the QR shows,
  only if missing; no wizard step. While paired and not exempt, Status shows a Recomendado item "Sin restricción de batería".
- **No PIN on incoming commands:** the pairing key authorises them. The door already implies a PIN.
- **Remote Stop** = a PIN Stop: `CaptureModeController.stop()` (clears escalation blocks, D58), OFF reason `REMOTE`
  ("Detenido a distancia").
- **Remote Start:** service bound and "Seguir protegiendo siempre" on → protection on, ON entry with a remote marker ("Iniciado a
  distancia"). Already on → "Ya estaba protegido". Service unbound → "El servicio de accesibilidad está apagado en el teléfono del
  Hijo". Switch off → "‘Seguir protegiendo siempre’ está apagado en el teléfono del Hijo". A remote command never changes any other
  setting.
- **Remote app rules** are a local Apps change: same store, the local screen redraws, last write wins. Bloqueada with that app in front
  blocks at once; De confianza applies on the next window change. Exclusions kept.
- **Log:** no kinds for commands (Start/Stop carry the marker); rule changes and Log fetches are not logged. New kinds **Vinculado** and
  **Desvinculado** (by the Padre or by this phone).
- **Status:** paired → "Vinculado con el teléfono del Padre"; nothing while a claim code waits; after a "desvinculado" notice the line
  goes and the Log says "Desvinculado (por el Padre)". No notification.

## D83 — Offline and Remote flavors; C1 and the privacy claim reworded (2026-10-21, human decision, M9 ticket 07)

- **Split:** flavor dimension `net`: `offline` / `remote`, same `applicationId` `dev.sophiel`. The main manifest names neither network
  permission; `src/offline/AndroidManifest.xml` keeps the `tools:node="remove"` lines for `INTERNET` and `ACCESS_NETWORK_STATE`;
  `src/remote/AndroidManifest.xml` adds `INTERNET`, `ACCESS_NETWORK_STATE`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- **Code:** OkHttp, ZXing and the code scanner are `remoteImplementation`. All Remote code lives in `src/remote/kotlin`; `main` reaches it
  through one same-named object, empty in `src/offline/kotlin`. `offline` opens straight into today's app, no role choice.
- **Default target:** `installRemoteDebug` on Device A. C5 / hard rule 6: both flavors assemble and the default install launches.
- **Checks:** `offline`'s `aapt … | grep -i internet` prints nothing; the `aapt dump permissions` diff between the two APKs is exactly
  the three permissions above.
- **Code scanner metrics:** accepted and disclosed (D80), no CAMERA fallback.
- **C1:** "The `offline` flavor MUST NOT declare `INTERNET` in any manifest (debug and test included). The `remote` flavor adds exactly
  `INTERNET`, `ACCESS_NETWORK_STATE` and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, nothing else (D76)."
- **Remote privacy claim** (SPEC §7, `docs/LIMITATIONS.md`, one short in-app line on sign-up and before the Pairing QR): the server keeps
  only accounts (email, password hash), sessions and pairings; it passes messages along without being able to read them (end-to-end
  encrypted with a key that never leaves the two phones) and never stores the Log, app lists, phone names or commands; no frame or
  screen content ever leaves the Child phone; the QR scanner (Google) sends anonymous usage data to Google.
- **Amends D78:** the socket is also held whenever the process lives for another reason (screen-share FGS, Sophiel open). Then the Child
  phone answers Log fetch, Stop and app rules (saved, applied when the service returns); Start is rejected. Every reply carries an
  "accesibilidad apagada" flag; the Parent card shows it and disables Start.
- **Seven §7 items:** 27-32 added, 26 reworded.

## D84 — The relay lives in `server/`, deployed by hand to Fly (2026-10-21, human decision, M9 ticket 09)

- **Repo:** `server/` in this repo, Bun + Elysia 1.4.x, TypeScript. C6 / hard rule 5: "Kotlin only in the app; the relay in `server/`
  is TypeScript on Bun."
- **Backups:** Fly's automatic daily volume snapshots, default retention. Losing the volume means re-sign-up and re-pair; no Log is lost.
- **Abuse limits** (memory only, reset on restart): 5 failed logins per email in 15 min lock that email for 15 min; 20 login/sign-up
  attempts per IP per minute; a claim code dies after 5 wrong guesses. Nothing about IPs touches disk.
- **Server checks** (`bun test`): relay round trip; offline Child phone → "sin conexión" at once; 10 s timeout; unpair from each side;
  a deleted account's device token rejected; login lockout; schema check (only D79's three tables and columns). Replay rejection is a
  Kotlin test on the Child phone.
- **Deploy:** the human holds the Fly account. A `/wizard` script covers sign-up, `flyctl`, `fly launch --no-deploy`, the 1 GB volume
  and the first `fly deploy`; `fly.toml`, `Dockerfile` and `idleTimeout` 25 s live in `server/`. Later deploys are a by-hand
  `fly deploy`, no CI.
- **Server URL per build type:** `remoteRelease` uses `https://<app>.fly.dev`; `remoteDebug` uses `http://localhost:3000` through
  `adb reverse tcp:3000 tcp:3000` to a local `bun run`, with a debug-only network security config allowing cleartext to `localhost`
  only. The real server is verified with a release build.
- **Deployed (2026-10-21):** app `sophiel-relay`, `https://sophiel-relay.fly.dev`, region `dfw`, volume `sophiel_data` 1 GB; `relayUrl` in
  `gradle.properties` points at it. A bad login answers 401.

## D85 — M9 written into SPEC; the freeze is lifted for M9 only (2026-10-21, `/wayfinder` ticket 08)

M9 — Remote is in SPEC §5 with its deliverables in build order and V1-V9, and C1, C5, C6, §1, §3, §4.1, §7, §8, §9 and M7 amended to
match D76-D84. M9 is new feature work by D76's scope change, so the feature freeze that returns at the end of M8 (D53) is lifted again
for M9 only and returns at its end. The wire details SPEC fixes beyond D79 (endpoint list, frame shapes, QR text, token sizes) are
implementation choices made while writing, not human decisions: change them freely if the build finds a reason.

## D86 — M8 sign-off: V7 and V8 (2026-10-21, human confirmation + agent checks, M8 ticket 31)

- **V7 Log and report:** the human confirmed the Informe works as intended (share sheet opens with the
  text; Hoy and 7 días). The report text is `LogReport`, built from the same `LogCounts` as the dashboard,
  JVM-tested for both periods (`LogReportTest`).
- **V8 permissions:** `aapt dump permissions` on the debug APK: no `INTERNET`. Permissions are
  `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`, `POST_NOTIFICATIONS`
  (+ the framework's dynamic-receiver one); no `uses-permission` added since M6.
- **V8 manifest diff since M6** (343b03e): the `<queries>` block, `BlockActivity`, the service config
  (`typeWindowStateChanged`, `flagReportViewIds`, `isAccessibilityTool`), and `android:localeConfig`
  with `locales_config.xml` (the Idioma switch, D72: a UI-polish addition after M8's spec, not a permission).
- **V8 tests:** `:safecore:test` 65 pass; `:app:testDebugUnitTest` 137 pass (0 failures, 0 skipped);
  `ParityTest` 3/3 on the connected SM-A715F (instrumented, not JVM).
- **Open:** M5 V1 and V2 on screen share were not re-run in this session. The human runs them on A and B
  before SPEC §9 M8 is ticked and the freeze returns.

## D87 — Dark theme for the Parent app (amends D43) (2026-10-25, human decision, UI redesign)

The evaluator asked for a dark mode, so "light theme only" in D43 is dropped. Ajustes gets a **Tema**
switch next to Idioma: Sistema (default, follows the phone) / Claro / Oscuro, stored with the other
settings and applied at once. Each colour role gets a dark value
rather than an inversion: warm ink ground, the state colours keep their meaning (green only ever means
protected, amber stopped, red Sin protección). The Child-facing block and guard screens stay dark in
both themes. Everything else in D43 stands: Spanish first, English in `values-en/`, all text through
resources. The dark palette is drafted in `.scratch/mockup/next-v2/` and is still a proposal there;
DESIGN.md's "light only" lines (and its "don't follow system dark mode") are rewritten when the
redesign is ported.
