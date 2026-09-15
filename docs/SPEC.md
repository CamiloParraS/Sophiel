# SPEC.md — Sophiel

**On-device visual safety layer for Android.**
Captures the screen, classifies frames locally, and masks flagged content with a system overlay.
No network. No cloud. No `INTERNET` permission.

---

## 0. Agent operating rules

This document is the source of truth. Read this section before writing any code.

### 0.1 Non-negotiable constraints

| ID     | Rule                                                                                                                                                     |
| ------ | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **C1** | The `INTERNET` permission MUST NOT appear in any manifest, including debug and test manifests. This is a verifiable product claim.                       |
| **C2** | No model training, fine-tuning, or architecture modification. Use pre-trained weights only.                                                              |
| **C3** | No captured frame is ever written to persistent storage. Frames live in memory and are recycled. Only derived scalars (hashes, scores) may be persisted. |
| **C4** | Milestones are strictly sequential. Do not begin milestone N+1 until milestone N's verification block passes.                                            |
| **C5** | Every milestone ends in a state where `./gradlew :app:installDebug` produces a launchable app. Never leave the tree broken between milestones.           |
| **C6** | Kotlin only. No Java sources. No RxJava. Coroutines + Flow for all async work.                                                                           |

### 0.2 When to stop and ask the human

Do not improvise around these. Stop, state the problem, and wait:

- On-device model output does not match the reference Python output (see M1.V3).
- A permission flow fails on the target device in a way not covered by §4.
- Any milestone runs more than 2× its stated budget.
- You are tempted to add a feature not listed in §1.2.

### 0.3 Style

- Imperative commit messages, prefixed with the milestone: `M2: add skin gate to DetectionPipeline`.
- KDoc on every public symbol in `:safecore`.
- No comments that restate the code. Comment _why_, not _what_.

---

## 1. Scope

### 1.1 What this is

A single-user Android app that:

1. Captures the device screen at a low frame rate via `MediaProjection`.
2. Runs a two-stage local pipeline (cheap skin gate → NSFW classifier) on each captured frame.
3. Draws a full-screen blur overlay when the score crosses a user-set threshold.
4. Reports its own latency, throughput, and accuracy through a built-in benchmark screen.

### 1.2 In scope

- Explicit-imagery detection (whole-frame classification).
- System-wide overlay intervention (blur + score readout + tap-to-reveal).
- A permission-free **Test Feed** mode for development, evaluation, and demo fallback.
- An on-device benchmark harness.
- An offline evaluation script producing precision / recall / F1 / ROC.

### 1.3 Explicitly out of scope

Do not build these. If asked mid-project, refuse and cite this section.

| Feature                                         | Reason                                                                            |
| ----------------------------------------------- | --------------------------------------------------------------------------------- |
| Deepfake / manipulated-media **detection**      | Generalizes at ~chance out-of-distribution. See §6.3 for the approved substitute. |
| Sub-region localization / selective redaction   | Requires an object detector. Whole-frame blur only.                               |
| User-defined semantic filters (CLIP)            | Requires porting a BPE tokenizer to Kotlin. Documented as Future Work.            |
| OCR / text moderation                           | Different modality, different pipeline.                                           |
| NPU / QNN delegation                            | The reference project never landed this. CPU meets the latency target.            |
| Play Store readiness, accounts, sync, telemetry | Not a shipping product.                                                           |

### 1.4 Success criteria

Two physical devices are available and both are part of the contract. Designate them once, in `docs/DECISIONS.md`, and never swap the roles:

- **Device A (budget)** — the _primary development device_. All day-to-day work runs here.
- **Device B (flagship)** — the _demo and headline-numbers device_.

Developing on the slower device is deliberate: performance problems surface while there is still time to fix them, instead of on the flagship where everything feels fine until the examiner picks up the other phone.

The project is complete when:

| Criterion                                                | Device A (budget) | Device B (flagship) |
| -------------------------------------------------------- | ----------------- | ------------------- |
| p50 end-to-end latency (frame available → overlay drawn) | **< 800 ms**      | **< 400 ms**        |
| Sustained throughput, skin gate on                       | **≥ 4 fps**       | **≥ 8 fps**         |
| 10-minute capture session without stall or crash         | required          | required            |

And on both:

- Recall **≥ 0.90** on the held-out evaluation set at the shipped threshold.
- False-positive rate on the _UI corpus_ (§7.3) **≤ 0.05**.
- `aapt dump permissions` confirms no `INTERNET` permission.
- Benchmark screen renders real measured numbers, not placeholders.

---

## 2. Android Studio setup

### 2.1 Project creation

- Template: **Empty Activity** (the Compose variant; _not_ "Empty Views Activity").
- Language: Kotlin. Build DSL: **Kotlin DSL (`.kts`)**. Gradle **version catalog** enabled.
- Package: `dev.sophiel`
- Application ID: `dev.sophiel`

### 2.2 SDK levels

```kotlin
compileSdk = 36
minSdk     = 26   // Android 8.0 — TYPE_APPLICATION_OVERLAY requires 26+
targetSdk  = 36   // Android 16
```

`minSdk 26` is a hard floor: `TYPE_APPLICATION_OVERLAY` was introduced in API 26 and the pre-26 overlay types are removed.

### 2.3 `gradle/libs.versions.toml`

```toml
[versions]
agp              = "8.7.3"
kotlin           = "2.0.21"
composeBom       = "2024.12.01"
coreKtx          = "1.15.0"
lifecycle        = "2.8.7"
activityCompose  = "1.9.3"
room             = "2.6.1"
datastore        = "1.1.1"
litert           = "1.0.1"
ksp              = "2.0.21-1.0.28"
junit            = "4.13.2"
androidxTest     = "1.6.1"
truth            = "1.4.4"
coroutines       = "1.9.0"

[libraries]
androidx-core-ktx             = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime    = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-service    = { group = "androidx.lifecycle", name = "lifecycle-service", version.ref = "lifecycle" }
androidx-activity-compose     = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom          = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui           = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics  = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling   = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-material3    = { group = "androidx.compose.material3", name = "material3" }
androidx-room-runtime         = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx             = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler        = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-datastore-prefs      = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
kotlinx-coroutines-android    = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
litert                        = { group = "com.google.ai.edge.litert", name = "litert", version.ref = "litert" }
litert-gpu                    = { group = "com.google.ai.edge.litert", name = "litert-gpu", version.ref = "litert" }
litert-support                = { group = "com.google.ai.edge.litert", name = "litert-support", version.ref = "litert" }
junit                         = { group = "junit", name = "junit", version.ref = "junit" }
androidx-test-runner          = { group = "androidx.test", name = "runner", version.ref = "androidxTest" }
truth                         = { group = "com.google.truth", name = "truth", version.ref = "truth" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library     = { id = "com.android.library", version.ref = "agp" }
kotlin-android      = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose      = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp                 = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

> **Version note.** These versions are a known-good starting set, not gospel. If Gradle sync fails, let Android Studio's AGP Upgrade Assistant resolve it and record the resulting versions in `docs/DECISIONS.md`. Do not spend more than 30 minutes fighting dependency resolution.

### 2.4 Deliberate omissions

- **No Hilt.** Two modules and one pipeline object do not justify a DI framework or its build cost. Use constructor injection and a single hand-rolled `AppContainer`.
- **No NNAPI delegate.** NNAPI was deprecated in Android 15. Use LiteRT's CPU path; add the GPU delegate only if M5 benchmarks show CPU missing the latency target.
- **No Compose inside the overlay window.** Overlay `ComposeView`s require manually attaching `ViewTreeLifecycleOwner` and `SavedStateRegistryOwner`, which is a reliable source of lost days. The overlay is a plain `View` subclass drawing on a `Canvas`. Compose is for the normal in-app UI only.

### 2.5 `app/build.gradle.kts` essentials

```kotlin
android {
    // Keep .tflite out of the asset compressor — a compressed model
    // cannot be memory-mapped and will fail to load.
    androidResources { noCompress += "tflite" }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
```

Add to `proguard-rules.pro`:

```
-keep class com.google.ai.edge.litert.** { *; }
-keep class org.tensorflow.lite.** { *; }
```

---

## 3. Repository architecture

### 3.1 Modules

Two modules. Resist adding a third.

| Module      | Type                      | Depends on Android UI? | Purpose                                                                                                   |
| ----------- | ------------------------- | ---------------------- | --------------------------------------------------------------------------------------------------------- |
| `:safecore` | `com.android.library`     | No                     | Detection pipeline. Pure logic + LiteRT. Unit-testable on the JVM where possible, instrumented where not. |
| `:app`      | `com.android.application` | Yes                    | Capture, overlay, UI, benchmark, settings.                                                                |

The separation exists so the detection pipeline can be tested and benchmarked without permissions, a device UI, or a running service. **`:safecore` must not reference `MediaProjection`, `WindowManager`, or any Compose symbol.**

### 3.2 Directory structure

```
sophiel/
├── SPEC.md                        ← this file
├── CLAUDE.md                      ← agent entrypoint; points here
├── README.md
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   └── libs.versions.toml
│
├── safecore/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml          ← must declare ZERO permissions
│       │   ├── assets/
│       │   │   └── nsfw.tflite
│       │   └── kotlin/dev/sophiel/core/
│       │       ├── Detector.kt              ← public API surface (§3.4)
│       │       ├── Verdict.kt               ← result model
│       │       ├── DetectionPipeline.kt     ← orchestrates gate → classify → cache
│       │       ├── gate/
│       │       │   ├── SkinGate.kt          ← cheap pre-filter (§6.1)
│       │       │   └── PerceptualHash.kt    ← dHash for frame dedupe
│       │       ├── model/
│       │       │   ├── NsfwClassifier.kt    ← LiteRT interpreter wrapper
│       │       │   └── Preprocessor.kt      ← resize + normalize; SINGLE source of truth
│       │       ├── policy/
│       │       │   └── PolicyEngine.kt      ← scores → Severity, with hysteresis
│       │       └── cache/
│       │           └── VerdictCache.kt      ← in-memory LRU keyed by dHash
│       ├── test/                            ← JVM tests: gate, hash, policy
│       └── androidTest/
│           ├── assets/fixtures/             ← reference images + expected logits
│           └── kotlin/.../ParityTest.kt     ← M1.V3 parity check
│
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── kotlin/dev/sophiel/
│           ├── SophielApp.kt
│           ├── AppContainer.kt              ← manual DI
│           ├── MainActivity.kt
│           ├── capture/
│           │   ├── ProjectionService.kt     ← foreground service, owns MediaProjection
│           │   ├── ProjectionController.kt  ← permission state machine (§4.4)
│           │   └── FrameSource.kt           ← ImageReader → Bitmap, handles rowStride
│           ├── overlay/
│           │   ├── OverlayController.kt     ← WindowManager add/remove/update
│           │   └── MaskView.kt              ← plain View, draws scrim + label
│           ├── feed/
│           │   └── testFeedScreen.kt        ← permission-free pipeline exerciser
│           ├── bench/
│           │   ├── BenchmarkRunner.kt
│           │   └── BenchmarkScreen.kt
│           ├── settings/
│           │   ├── SettingsRepository.kt    ← DataStore
│           │   └── SettingsScreen.kt
│           └── ui/theme/
│
├── tools/
│   ├── convert_model.py            ← Keras SavedModel → .tflite
│   ├── reference_infer.py          ← produces expected logits for ParityTest
│   ├── eval.py                     ← P/R/F1/ROC over the eval set
│   └── requirements.txt
│
├── docs/
│   ├── DECISIONS.md                ← running log: decision, alternatives, why
│   ├── PERMISSIONS.md              ← the flow diagram from §4
│   ├── RESULTS.md                  ← benchmark + eval output; regenerate, don't hand-edit
│   └── LIMITATIONS.md              ← §8, written honestly
│
└── eval/
    ├── images/                     ← gitignored. NEVER commit image data.
    └── labels.csv                  ← filename,label  (committed; labels only)
```

### 3.3 `.gitignore` additions

```
eval/images/
*.tflite.bak
/tools/.venv/
local.properties
```

`eval/images/` is gitignored unconditionally. `docs/RESULTS.md` and `eval/labels.csv` are committed — the numbers and labels are the academic artifact, the pixels are not.

### 3.4 `:safecore` public API

This is the contract. `:app` may use nothing else from `:safecore`.

```kotlin
package dev.sophiel.core

/** Severity ladder. Ordinal order is meaningful; do not reorder. */
enum class Severity { SAFE, SUGGESTIVE, EXPLICIT }

/**
 * Outcome of analysing one frame.
 *
 * @param severity  bucketed decision after policy + hysteresis
 * @param score     raw unsafe probability in [0,1]
 * @param gated     true if the skin gate short-circuited before the classifier ran
 * @param cacheHit  true if this verdict was reused from a near-identical prior frame
 * @param latencyMs wall-clock time inside [Detector.analyze]
 */
data class Verdict(
    val severity: Severity,
    val score: Float,
    val gated: Boolean,
    val cacheHit: Boolean,
    val latencyMs: Long,
)

interface Detector {
    /**
     * Analyse a single frame.
     *
     * The caller retains ownership of [frame]; this method must not recycle it
     * and must not retain a reference past return.
     *
     * Safe to call from any thread; implementations serialise internally onto a
     * single inference thread. Concurrent callers are queued, not parallelised.
     */
    suspend fun analyze(frame: android.graphics.Bitmap): Verdict

    /** Releases the interpreter. The instance is unusable afterwards. */
    fun close()
}

object DetectorFactory {
    /** @param threshold unsafe-probability cutoff for [Severity.EXPLICIT], in [0,1] */
    fun create(context: android.content.Context, threshold: Float = 0.70f): Detector
}
```

---

## 4. The permission model

This is the highest-risk area of the project. Implement it exactly as specified.

### 4.1 Manifest

```xml
<!-- Overlay. NOT a runtime permission — see §4.3. -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

<!-- Foreground service for screen capture. -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />

<!-- Required to show the mandatory FGS notification on API 33+. -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- DELIBERATELY ABSENT: android.permission.INTERNET -->

<service
    android:name=".capture.ProjectionService"
    android:foregroundServiceType="mediaProjection"
    android:exported="false" />
```

### 4.2 Platform facts the implementation must respect

| Fact                                                                                                                            | Consequence                                                                                                          |
| ------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| User consent is required before **each** capture session; a session is one `createVirtualDisplay()` call.                       | No "remember my choice". The prompt appears every time protection starts. Design the UX around this, don't fight it. |
| On API 34+, passing a `createScreenCaptureIntent()` result to `getMediaProjection()` more than once throws `SecurityException`. | Never cache or reuse the consent `Intent`. Request fresh every time.                                                 |
| On API 34+, calling `createVirtualDisplay()` twice on one `MediaProjection` throws `SecurityException`.                         | On rotation, call `VirtualDisplay.resize()` then `VirtualDisplay.setSurface()`. Do **not** recreate.                 |
| The foreground service must be running with type `mediaProjection` **before** `getMediaProjection()` is called.                 | Ordering in §4.4 is mandatory. Getting it backwards is the most common crash in this design.                         |
| Apps using `FLAG_SECURE` yield black frames.                                                                                    | Detect all-black frames and surface "protected content — not analyzable" rather than scoring them.                   |
| The user can revoke the session from the status bar at any time.                                                                | Register `MediaProjection.Callback.onStop()` and tear down cleanly. Untested teardown leaks the `VirtualDisplay`.    |
| Overlays cannot be drawn over system permission dialogs or parts of system UI.                                                  | Never claim total coverage. State this in `docs/LIMITATIONS.md`.                                                     |
| Android 15+ stops the projection when a secure keyguard (PIN/fingerprint) locks the device. A session cannot outlive screen-off. | Tear down on `ACTION_SCREEN_OFF` (DECISIONS.md D18). Resuming needs fresh consent; there is no silent re-init.        |
| `MediaProjection` mirrors the composited display, **including our own overlay windows** (observed with the debug pill, D18).    | Anything we draw is in the next frame. See M4's feedback-loop note.                                                   |

### 4.3 `SYSTEM_ALERT_WINDOW` is not a runtime permission

Do not call `requestPermissions()` for it. The flow is:

```kotlin
if (!Settings.canDrawOverlays(context)) {
    startActivity(Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    ))
}
```

There is no result callback. Re-check `Settings.canDrawOverlays()` in `onResume()`.

### 4.4 Mandatory startup sequence

Implement as an explicit state machine in `ProjectionController`. Each step gates the next.

```
[IDLE]
  │  user taps "Start protection"
  ▼
[NEED_NOTIFICATIONS]  API 33+ only
  │  requestPermissions(POST_NOTIFICATIONS)
  │  denied → [DEGRADED] (service still runs; warn the notification is hidden)
  ▼
[NEED_OVERLAY]
  │  Settings.canDrawOverlays()? no → ACTION_MANAGE_OVERLAY_PERMISSION
  │  re-check in onResume(); still denied → [BLOCKED] with explanation
  ▼
[NEED_CONSENT]
  │  launcher.launch(mgr.createScreenCaptureIntent())
  │  RESULT_CANCELED → [IDLE], no error toast (cancelling is a valid choice)
  ▼
[STARTING_SERVICE]                        ◄── ORDER IS LOAD-BEARING
  │  startForegroundService(ProjectionService, extras = resultCode + data)
  │  service calls startForeground(id, notif, FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
  ▼
[ACQUIRING_PROJECTION]                    ◄── only now, inside the service
  │  mgr.getMediaProjection(resultCode, data)
  │  registerCallback(onStop → [STOPPING])
  ▼
[RUNNING]
  │  createVirtualDisplay(...) exactly once
  │  ImageReader → DetectionPipeline → OverlayController
  ▼
[STOPPING] → release VirtualDisplay, ImageReader, Surface, overlay → [IDLE]
```

### 4.5 `ImageReader` traps

Both of these will produce silent, confusing failures. Handle them from the first commit.

```kotlin
// TRAP 1: rowStride is padded. Ignoring it yields a diagonally skewed image
// that still "looks like a picture" in the debugger — hard to spot, easy to
// waste days on.
val plane = image.planes[0]
val rowPadding = plane.rowStride - plane.pixelStride * width
val bitmap = Bitmap.createBitmap(
    width + rowPadding / plane.pixelStride, height, Bitmap.Config.ARGB_8888
).apply { copyPixelsFromBuffer(plane.buffer) }
val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)

// TRAP 2: every acquired Image MUST be closed. Miss one and the reader
// stalls permanently after maxImages frames, with no exception thrown.
image.close()
```

Configure the reader as `ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, /* maxImages = */ 2)` and use `acquireLatestImage()`, which discards backlog. **Drop frames rather than queue them** — the pipeline must never build a backlog. Concretely (DECISIONS.md D19):

- **Decide before decoding.** Check the throttle and "is a frame already being analysed?" _before_ copying the `Image` into a `Bitmap`. The display produces 60–120 frames/s; decoding each one only to drop it is wasted CPU and GC.
- **One frame in flight.** The 80 ms throttle alone is not backpressure: if analysis takes longer than 80 ms, frames queue on the inference thread without bound. Drop new frames while one is in flight.
- **Close on the owning thread.** Close the `ImageReader` on its own handler thread, and the interpreter on the inference thread. Closing either one from another thread mid-use is a native use-after-free: SIGSEGV with no Java stack trace (observed on Device B).

### 4.6 Capture resolution

Do not capture at native resolution. Set the `VirtualDisplay` to a downscaled size:

```kotlin
val scale = 360f / minOf(screenWidth, screenHeight)
val captureW = (screenWidth * scale).toInt() and 0xFFFFFFFE.toInt()   // force even
val captureH = (screenHeight * scale).toInt() and 0xFFFFFFFE.toInt()
```

The classifier input is 224×224. Capturing 1440p to downscale to 224 wastes bandwidth, memory, and battery for no accuracy gain. Pass the real device `densityDpi` to `createVirtualDisplay()`.

**Crop the system bars, and only the system bars.** Before a frame reaches `Detector.analyze()`, crop the status bar, navigation bar, and display cutout. Get them from `WindowMetrics` insets via `getInsetsIgnoringVisibility(systemBars() | displayCutout())`, scaled to capture size and recomputed in `onConfigurationChanged()`. They are never content, but they are always in the frame. Do **not** center-crop: a portrait screen is ~2:1, so a center square discards about half of it, and explicit content at the top or bottom of a feed would be silently missed. Recall matters more than tidiness here. The remaining aspect mismatch is handled by squashing to 224×224 in `Preprocessor`, which matches how the model was trained (Keras `load_img(target_size=…)` also ignores aspect). Content-aware ROI segmentation needs a detector and is out of scope (§1.3). If M5 shows recall loss on tall frames, the upgrade is tiling: two square crops, take the max score, at 2× classifier cost.

---

## 5. Milestone roadmap

Six milestones over six weeks. Each has an objective, deliverables, and a **verification block that must pass before proceeding**.

---

### M0 — Skeleton (Week 1, first half)

**Objective.** A two-module project that builds and installs, with permissions declared but unused.

**Deliverables**

- `:app` + `:safecore` modules wired via the version catalog.
- `MainActivity` with a Compose scaffold and three empty destinations: Protection, Test Feed, Benchmark.
- Manifest per §4.1.
- `docs/DECISIONS.md` seeded with the choices in §2.4.

**Verification**

- `V1` — `./gradlew :app:installDebug` succeeds; the app launches without crashing.
- `V2` — `aapt dump permissions app/build/outputs/apk/debug/app-debug.apk | grep -i internet` returns **nothing**. Wire this into CI now, not later.
- `V3` — `:safecore`'s `AndroidManifest.xml` declares zero permissions.

---

### M1 — Model conversion and parity (Week 1, second half)

**Objective.** A `.tflite` model in `assets/` whose on-device output provably matches the Python reference.

This milestone contains the project's highest-variance risk. Budget generously.

**Deliverables**

- `tools/convert_model.py` — converts the chosen NSFW model to `.tflite` with dynamic-range quantization.
- `tools/reference_infer.py` — runs 10 fixture images through the Python model, writes `expected_logits.json`.
- `safecore/src/main/assets/nsfw.tflite`.
- `NsfwClassifier.kt` and `Preprocessor.kt`.
- `androidTest/.../ParityTest.kt`.

**Model choice.** Primary: a Keras/SavedModel NSFW classifier, because `TFLiteConverter.from_saved_model()` is a one-liner. Fallback if conversion fights back: a pre-converted `.tflite` from an existing open-source Android NSFW project. Record which you used and why in `docs/DECISIONS.md`.

> The reference project used `taufiqdp/mobilenetv4_conv_small` (Apache-2.0) under ExecuTorch. That is a PyTorch/timm checkpoint; reaching `.tflite` from there adds a conversion hop. Prefer a Keras-native model unless you have time to spare.

**Quantization.** Use dynamic-range (`converter.optimizations = [tf.lite.Optimize.DEFAULT]`) or float16. **Do not attempt full-integer quantization** — it requires a representative calibration dataset and will consume days for a few milliseconds.

**Verification**

- `V1` — Model file is 3–30 MB. Larger means quantization silently failed.
- `V2` — `NsfwClassifier` loads the model and returns a score for a fixture image without throwing.
- `V3` — **THE PARITY GATE.** For all 10 fixtures, on-device output matches `expected_logits.json` to within `1e-2` absolute. **Do not proceed to M2 until this passes.**

> If V3 fails, the cause is almost certainly `Preprocessor.kt`. Check, in order: (1) channel order — RGB vs BGR; (2) normalization range — `[0,1]` vs `[-1,1]` vs ImageNet mean/std; (3) resize interpolation — bilinear vs nearest; (4) input tensor layout — NHWC vs NCHW. Do not add a "calibration offset" to paper over a mismatch. Fix the preprocessing.

---

### M2 — Pipeline and Test Feed (Week 2)

**Objective.** The full detection pipeline, exercised end-to-end with **zero permissions**. This is your safety net: from here on, you always have something submittable.

**Deliverables**

- `SkinGate.kt` per §6.1.
- `PerceptualHash.kt` — 64-bit dHash, Hamming distance.
- `VerdictCache.kt` — LRU (capacity 256) keyed by hash, hit on Hamming distance ≤ 5.
- `PolicyEngine.kt` — score → `Severity` with hysteresis per §6.2.
- `DetectionPipeline.kt` — the orchestration: hash → cache → gate → classify → policy.
- `testFeedScreen.kt` — a `LazyColumn` of bundled test images; each tile shows its live verdict, score, gate state, and latency.
- `DetectorFactory` wired to a single-thread dispatcher.

**Verification**

- `V1` — JVM unit tests pass for `SkinGate`, `PerceptualHash`, `PolicyEngine`.
- `V2` — Test Feed scrolls smoothly; every tile displays a verdict.
- `V3` — Scrolling back to a previously-seen tile shows `cacheHit = true`.
- `V4` — A screenshot of a plain settings screen is `gated = true` (the skin gate rejects it without invoking the classifier).
- `V5` — `Detector.close()` followed by re-creation does not leak; verified via Android Studio Profiler across 5 cycles.

---

### M3 — Capture (Week 3)

**Objective.** Screen frames reaching the pipeline. **No overlay yet** — results go to the notification and Logcat.

Separating capture from overlay is deliberate. Two hard subsystems debugged at once is one subsystem too many.

**Deliverables**

- `ProjectionController.kt` — the §4.4 state machine.
- `ProjectionService.kt` — foreground service, correct type, notification, `MediaProjection.Callback`.
- `FrameSource.kt` — `ImageReader` → `Bitmap`, honouring both §4.5 traps.
- Frame-rate limiter: process at most one frame per 80 ms; drop the rest.
- Notification shows the live verdict and score.

**Verification**

- `V1` — Full permission flow completes on a physical device from a cold install.
- `V2` — Denying overlay permission reaches `BLOCKED` with a clear message, no crash.
- `V3` — Cancelling the consent dialog returns to `IDLE` silently, no crash.
- `V4` — Rotating the device mid-session does **not** throw `SecurityException`. (If it does, you are recreating the `VirtualDisplay` — use `resize()` + `setSurface()`.)
- `V5` — Stopping the session from the status bar tears down cleanly; `onStop()` fires; `adb shell dumpsys media_projection` shows no active session.
- `V6` — 10 minutes of continuous capture: no `IllegalStateException`, no stall. (A stall after exactly 2 frames means a missing `image.close()`.)
- `V7` — Pointing at a `FLAG_SECURE` app (a banking app) surfaces "protected content", not a score of 0.0.

---

### M4 — Overlay (Week 4) — **FEATURE FREEZE AT END OF WEEK**

**Objective.** Visible intervention.

**Deliverables**

- `MaskView.kt` — plain `View`; draws a translucent scrim, a blur or pixelation effect, and a score label.
- `OverlayController.kt` — adds/removes the window via `WindowManager`.
- Tap-to-reveal: a 3-second temporary dismissal.
- Threshold slider in Settings, persisted via DataStore.

**Window parameters** — these flags matter:

```kotlin
WindowManager.LayoutParams(
    MATCH_PARENT, MATCH_PARENT,
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
    PixelFormat.TRANSLUCENT,
)
```

Omitting `FLAG_NOT_FOCUSABLE` steals input from every other app and makes the device feel bricked. On API 31+, prefer `RenderEffect.createBlurEffect()` over manually blurring bitmaps.

**Feedback-loop trap.** The mask is itself captured (§4.2). Once engaged, the next frames show the scrim, not the content. They score SAFE, hysteresis releases after 3 frames, the content reappears, and the mask re-engages: it strobes. Decide how frames are treated while masked before writing `OverlayController` (for example, freeze the verdict while masked and re-evaluate only on tap-to-reveal or a large dHash change), and cover it in V2.

**Verification**

- `V1` — Explicit content in a gallery app triggers the overlay within 400 ms.
- `V2` — Overlay clears within 400 ms of the content leaving the screen.
- `V3` — With the overlay visible, the underlying app still receives touches (scroll a list beneath it).
- `V4` — Tap-to-reveal hides the mask for 3 s, then it returns.
- `V5` — Moving the threshold slider changes behaviour without restarting the service.
- `V6` — Killing the app from Recents removes the overlay; no orphan window survives.

**At the end of this week, stop adding features.** Weeks 5 and 6 are measurement and writing. This is the discipline that separates a finished project from an unfinished one.

---

### M5 — Benchmark and evaluation (Week 5)

**Objective.** Numbers. This milestone is where the engineering half of the grade lives.

**Deliverables**

- `BenchmarkRunner.kt` — runs N frames through the pipeline, reports p50/p95/p99 latency, throughput, gate hit rate, cache hit rate.
- `BenchmarkScreen.kt` — runs it and renders results on-device.
- **Ablation mode**: run the suite with the skin gate and cache disabled, then enabled. This before/after comparison is your first headline result.
- **Cross-device comparison**: the identical suite on Device A and Device B, exported as a single table. This is your second headline result, and it is free — you already have both phones.
- CSV export of raw per-frame timings (`adb pull`), so `eval.py` can regenerate every chart without re-running on-device.

The cross-device table is worth more than it looks. A 2×2 of {budget, flagship} × {gate off, gate on} lets you say something most student projects cannot: _the optimization matters more on weak hardware than strong._ If the gate buys 15% on the flagship and 60% on the budget device, that asymmetry is a real finding, and it is the paragraph an examiner remembers. Report it even if it comes out flat — a null result you predicted and measured is still a result.

- `tools/eval.py` — precision, recall, F1, and a ROC curve over `eval/labels.csv`; marks the shipped threshold on the curve.
- `docs/RESULTS.md` — generated output, on at least two physical devices.

**Verification**

- `V1` — Benchmark completes on **both** devices and shows non-placeholder numbers.
- `V2` — Ablation shows a measurable gate/cache improvement in throughput and mean latency, reported per device.
- `V3` — `eval.py` emits a ROC curve PNG and a metrics table.
- `V4` — The 2×2 cross-device × ablation table is in `docs/RESULTS.md`.
- `V5` — Success criteria in §1.4 are met on both devices at their respective targets, or each shortfall is documented with a hypothesis in `docs/LIMITATIONS.md`.

> **Benchmark on physical hardware only.** The emulator has no meaningful GPU or NPU path; its latency figures are noise. Run each configuration at least 3 times and report the median — single runs on a phone are dominated by thermal state and background activity. Note ambient temperature and battery level in `docs/RESULTS.md`; a thermally throttled phone can be 2× slower, and an unexplained outlier looks like sloppiness rather than physics.

---

### M6 — Documentation and demo (Week 6)

**Objective.** Ship the artifact.

**Deliverables**

- `README.md` — what it does, the architecture diagram, how to build, honest results.
- `docs/LIMITATIONS.md` — §8, written without hedging.
- `docs/DECISIONS.md` — finalized.
- Demo video, recorded via `adb shell screenrecord`, following a written runbook.
- A "Future Work" section covering CLIP filters, sub-region redaction, and C2PA provenance.

**Verification**

- `V1` — A clean clone builds with `./gradlew :app:installDebug` and no manual steps.
- `V2` — The demo runbook is reproducible by someone else.
- `V3` — Video recorded and saved. **Record it early in the week.** Live demos fail.

---

## 6. Lessons from `safescreen-ai`

The reference implementation (`github.com/doctarpolmz/safescreen-ai`, Qualcomm × Meta ExecuTorch Hackathon) shipped this design and hit real problems. Adopt these findings directly rather than rediscovering them.

### 6.1 The skin gate — adopt this first

They found that ordinary app UIs produced false positives, and fixed it with a cheap skin-tone pre-filter that runs **before** the classifier. This is the highest-value idea in the repo. It costs no ML knowledge, it is pure arithmetic, and it improves accuracy _and_ battery simultaneously.

```kotlin
/**
 * Cheap pre-filter. Returns true if the frame plausibly contains skin and is
 * therefore worth classifying.
 *
 * Runs on a downscaled copy (64x64 is enough). If the skin-pixel ratio is
 * below [minRatio], skip the classifier entirely and return SAFE.
 *
 * Set [minRatio] LOW (~0.05). A false negative here is invisible to the user
 * and unrecoverable; a false positive merely costs one classifier run.
 */
class SkinGate(private val minRatio: Float = 0.05f) {
    fun shouldClassify(frame: Bitmap): Boolean { /* ... */ }
}
```

Use the standard YCbCr rule — roughly `Cr ∈ [133, 173]`, `Cb ∈ [77, 127]` — which is far more robust across skin tones than an RGB threshold. Ignore near-black pixels (`Y < 40`): their chroma is mostly noise (DECISIONS.md D19).

**Do not add a minimum-chroma guard.** The box's lower `Cr` edge sits close to neutral gray, so warm-tinted grayscale does pass. But measured skin pixels on real and rendered images sit at `Cr − Cb` 8–15, which is the same band. A `Cr − Cb ≥ 20` guard gated 9 explicit test-feed images (D19). A colour rule cannot separate warm gray from pale skin, and letting warm gray through only costs a classifier run.

Tune `minRatio` and `MIN_LUMA` in M5 against the UI corpus (§7.3) plus low-light skin images, and report the chosen values.

**Known blind spot:** any colour-based gate sees a true black-and-white image as zero skin, so it is gated SAFE without being classified. This is the unrecoverable direction. Measure it in M5 (include B&W images in the eval set) and state it in §8. Do not "fix" it by classifying every achromatic frame: dark-mode UIs are achromatic too, and the gate's hit rate would collapse.

Expect the gate to reject 60–80% of typical screen content. That is most of your battery budget saved, and it is exactly the kind of measurable engineering decision to feature in your report.

### 6.2 Hysteresis stops the flicker

Whole-frame classification on scrolling content produces scores that oscillate around the threshold, and the overlay strobes. Require **2 consecutive frames above threshold** to engage the mask, and **3 consecutive frames below** to release it. Asymmetry is intentional: engage fast, release slow.

### 6.3 Their deepfake result vindicates cutting it

They measured **~0.54 accuracy out-of-distribution** — indistinguishable from chance — and responded by surfacing it as a "possibly manipulated (NN%)" badge, never as a verdict.

Take the lesson, not the feature. §1.3 removes deepfake detection entirely, and this is **confirmed with the project owner — it is not required by the brief**. Do not reintroduce it under any framing.

In your report, cite their measured number as the justification and write it up as a deliberate scoping decision with evidence, not as an omission. _Correctly declining to ship a feature that does not work, and showing why, is a stronger result than shipping one that does not work._ Reserve roughly half a page for this; it is one of the easier places to demonstrate engineering judgement.

**C2PA / Content Credentials** — parsing a provenance manifest and verifying its signature chain — belongs in Future Work only. It is the right way to answer "is this media manipulated", it is deterministic, and naming it shows you understand why the classifier approach fails. Do not implement it.

### 6.4 The permission-free test feed

Their "Open test feed" mode shows detection without permissions. This is why M2 precedes M3 in this spec. It gives you a pipeline you can develop, benchmark, and demo before `MediaProjection` works — and a fallback demo if the overlay misbehaves in front of your examiners.

### 6.5 CPU is enough — skip the NPU

Their Hexagon NPU port via ExecuTorch QNN **never landed**; the README still lists it as blocked on the QNN SDK. Meanwhile they measured **25.5 ms/frame at ~12 fps on CPU**. Treat CPU-only as the plan, not a compromise, and treat their numbers as your sanity check: if you are far off 25 ms on a modern device, something is wrong in preprocessing.

### 6.6 Whole-frame blur, not localization

They blur the whole screen because a classifier gives no spatial information. Do the same, and state it plainly as a limitation rather than implying selective redaction.

### 6.7 The no-INTERNET claim

They made "no `INTERNET` permission" a headline feature with an in-app badge and airplane-mode verification. Copy this. It costs nothing, it is independently verifiable by anyone with `aapt`, and it is a genuinely strong privacy property. Wire the check into CI at M0 so a stray dependency can never introduce it.

### 6.8 Where to deviate

| They did            | You should      | Why                                                                |
| ------------------- | --------------- | ------------------------------------------------------------------ |
| ExecuTorch + QNN    | LiteRT, CPU     | Mature tooling, better docs; you have 6 weeks and no ML background |
| Two models (~26 MB) | One model       | Halves the conversion risk, which is your critical path            |
| Hackathon sprint    | Milestone gates | You are graded on rigour, not on demo dazzle                       |

---

## 7. Data and demo policy

### 7.1 Never commit image data

`eval/images/` is gitignored. Commit `labels.csv` and the metrics, never the pixels.

### 7.2 Evaluation set

200–400 images, hand-labeled, is defensible at undergraduate level **provided the methodology and its limits are stated**. Source from an established licensed research dataset rather than scraping. Document provenance, labeling procedure, and inter-rater agreement (even if the rater is only you — say so).

### 7.3 The UI corpus

Assemble ~100 screenshots of ordinary app interfaces: settings screens, chat threads, maps, code editors, spreadsheets. This is your **false-positive** test set and the thing the skin gate is tuned against. It is also trivially safe to collect, and its results are a strong section in your report.

### 7.4 Live demo content

Use beach photos, swimwear, and fitness imagery. The model scores these in the middle band, which lets you demonstrate **threshold behaviour** live — a better demo than a binary flip, because it shows a system reasoning about degrees. Keep genuinely explicit material to the offline evaluation run and report those results as a table, never as images.

---

## 8. Known limitations — state these, do not hide them

Copy into `docs/LIMITATIONS.md` and expand with your measured numbers.

1. **Reactive, not preventive.** Analysis happens after the content is drawn. Masking lands in ~200–400 ms; a fast reader may glimpse content. "Before you engage" is a UX goal, not a technical guarantee.
2. **`FLAG_SECURE` blindness.** Apps that mark their windows secure yield black frames and cannot be analyzed.
3. **Consent friction.** The system requires fresh consent for every capture session. Protection cannot survive a reboot, or turning the screen off, silently, by design. Android 15+ itself ends the projection on a secure lock.
4. **Whole-frame decisions.** A classifier, not a localizer. The entire screen is masked.
5. **Threshold is a value judgement.** "Explicit" is contextual and culturally variable. The threshold is user-configurable precisely because no single value is correct.
6. **Single-model bias.** Inherits the biases of its training data. State what is known about the source dataset.
7. **Small evaluation set.** N is a few hundred, self-labeled. Report confidence intervals, not just point estimates.
8. **Overlay gaps.** System dialogs and portions of system UI cannot be covered.
9. **Energy figures are whole-device estimates**, valid only unplugged. Isolating NPU/CPU power requires vendor profiling tools.
10. **Grayscale blindness in the skin gate.** True black-and-white imagery has no skin chroma and is gated SAFE without classification (§6.1). Report the measured miss rate.

---

## 9. Quick reference

```bash
# Build and install
./gradlew :app:installDebug

# Verify the no-INTERNET claim
aapt dump permissions app/build/outputs/apk/debug/app-debug.apk | grep -i internet   # must be empty

# Watch the pipeline
adb logcat -s Sophiel:D

# Inspect active projection sessions
adb shell dumpsys media_projection

# Confirm the foreground service type
adb shell dumpsys activity services dev.sophiel | grep -i foreground

# Record the demo
adb shell screenrecord --time-limit 60 /sdcard/demo.mp4 && adb pull /sdcard/demo.mp4

# Model conversion
python tools/convert_model.py --out safecore/src/main/assets/nsfw.tflite
python tools/reference_infer.py --fixtures safecore/src/androidTest/assets/fixtures

# Offline evaluation
python tools/eval.py --labels eval/labels.csv --images eval/images --out docs/RESULTS.md
```

---

## 10. Milestone checklist

- [x] **M0** Skeleton — builds, installs, no `INTERNET`
- [x] **M1** Model — converted, **parity gate passed**
- [x] **M2** Pipeline + Test Feed — permission-free, end-to-end
- [x] **M3** Capture — frames flowing, all permission paths handled
- [ ] **M4** Overlay — visible intervention · **FEATURE FREEZE**
- [ ] **M5** Benchmark + eval — numbers on two devices, ablation done
- [ ] **M6** Docs + demo — video recorded early
