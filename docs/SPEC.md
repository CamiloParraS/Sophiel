# SPEC.md — Sophiel

**Parental-control screen filter for Android.**
Watches the screen live, on the device, and covers only the flagged areas. A parent sets it up behind a PIN.
No network. No cloud. No `INTERNET` permission.

Rescoped from the original whole-frame design (decision D22; the old spec is tagged `spec-v1`). Planning record: `docs/wayfinder/map.md`. M8 (solution fit, D53-D60) was planned in `.scratch/m8-solution-fit/map.md`.

---

## 0. Agent operating rules

This document is the source of truth. Read this section before writing any code. `CONTEXT.md` defines the vocabulary (Parent, Child, Preset, Tile, Mask, Log entry).

### 0.1 Non-negotiable constraints

| ID     | Rule                                                                                                                                                                           |
| ------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **C1** | The `INTERNET` permission MUST NOT appear in any manifest, including debug and test manifests. This is a verifiable product claim.                                             |
| **C2** | No model training, fine-tuning, or architecture modification. Use pre-trained weights only.                                                                                    |
| **C3** | No captured frame is ever written to persistent storage. Frames live in memory and are recycled. Only derived scalars (hashes, scores, timings, log entries) may be persisted. |
| **C4** | Milestones are strictly sequential. Do not begin milestone N+1 until milestone N's verification block passes.                                                                  |
| **C5** | Every milestone ends in a state where `./gradlew :app:installDebug` produces a launchable app.                                                                                 |
| **C6** | Kotlin only. No Java sources. No RxJava. Coroutines + Flow for all async work.                                                                                                 |
| **C7** | `:safecore` stays UI-free: no `MediaProjection`, `WindowManager`, or Compose symbol.                                                                                           |

### 0.2 When to stop and ask the human

Do not improvise around these. Stop, state the problem, and wait:

- The model parity test (`ParityTest`) stops passing.
- A permission flow fails on a physical device in a way not covered by §4.
- Any milestone runs more than 2× its stated budget.
- You are tempted to add a feature not listed in §1.2.

### 0.3 Style

- Conventional Commits (see `CLAUDE.md`).
- KDoc on every public symbol in `:safecore`.
- No comments that restate the code. Comment _why_, not _what_.
- Pure logic is test-first (JVM unit tests). On-device verification is done by the human.

---

## 1. Scope

### 1.1 What this is

A parental-control Android app. A **Parent** sets a PIN, a preset, and a sensitivity. **Protection** stays on from the Parent's Start until a PIN Stop; reboot, screen-off and lost consent change only the **capture mode** (D54). While it runs, the app:

1. Captures the screen at a low frame rate: by **screen share** (`MediaProjection`) when someone accepted the system dialog, otherwise by **accessibility** screenshots (no dialog, survives reboot).
2. Cuts each frame into **Tiles** and judges them with a local pipeline (cheap skin gate, then NSFW classifier).
3. Covers only the flagged tiles with a solid **Mask**. Touches pass through masks, so the **Child** can keep using the phone around them.
4. Keeps a local, 7-day **Log** the Parent can read behind the PIN, naming the app each mask was over, and can share a text report through another app.
5. **Guards** its own system settings behind the PIN, and **blocks** apps the Parent marked, or that showed explicit content 3 times in 10 minutes.

The UI and the experience of it (it should feel quick and light) are the product. The model is a given.

### 1.2 In scope

- Three presets: **Light** (whole frame as one tile), **Balanced** (2 columns × 3 rows), and **Precise** (experimental, Android 14+): NudeNet 320n's boxes masked, refreshed by window screenshots (D35). Where screenshots aren't possible (Device A, the service turned off) Precise runs Balanced.
- Noise masks (optional lock chip + "Hidden by Sophiel" label, a Parent setting) that let touches pass through.
- An accessibility service (`MaskWindowService`) that hosts the mask window, so masks draw opaque (D32). Optional: protection starts without it on the 0.79 app overlay (D46), losing everything below that needs it. It also:
  - screenshots the app window under a mask (API 34+): for Precise (D35), and so a tile probe can judge it without lifting the mask (peek, default on, D34/D54);
  - screenshots the whole display as the **accessibility** capture mode (M8, D54);
  - reads the window list to name the **Episode app** (D57);
  - listens to `typeWindowStateChanged` only (no content events) for the **guard** (D56) and app **blocking** (D58).
- **Hybrid capture** behind one Parent switch, "Seguir protegiendo siempre" (M8, D54, D55).
- **Tamper guard** on Sophiel's own system pages (M8, D56).
- **Escalation and per-app rules**: blocked / trusted apps, a block screen (M8, D58).
- Parent screens: Status, Setup wizard, PIN unlock, Settings (Strict / Normal / Relaxed, preset, show-label toggle, peek under mask, "Seguir protegiendo siempre", Apps, Change PIN), Log with a dashboard and a text **Report** (M8, D59), the block screen. Change PIN is a human scope addition (D38). All app text is Spanish first, with English; Material You colour, light theme (D43, human scope addition).
- A hidden debug menu in every build (Test Feed, Benchmark, mask look, debug pill, model pick, raw threshold slider).
- A permission-free **Test Feed** for development and as the demo fallback.

### 1.3 Explicitly out of scope

Do not build these. If asked mid-project, refuse and cite this section.

| Feature                                                                                | Reason                                                                                |
| -------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| Remote parent alerts, accounts, sync, backend                                          | Needs `INTERNET`; breaks C1.                                                          |
| Sophiel sending anything by itself (SMS, push, email)                                  | Needs `INTERNET` or a second SIM phone to test. The report leaves only via the share sheet (D59). |
| Tamper resistance beyond the guard: Device Admin, Device Owner, work profile, boot receiver | Device Admin is future work; Device Owner needs an account-free phone (D56).      |
| App suspension (`setPackagesSuspended`), usage-time limits, `UsageStats` fallbacks     | Needs system or Device Owner (D58).                                                   |
| Report as an image or PDF card                                                         | Future work; text only (D59).                                                         |
| Formal evaluation set, ROC, UI corpus as deliverables                                  | Debug-only tooling. May appear as a stretch measurement (§5), never as a deliverable. |
| Deepfake detection, CLIP filters, OCR, NPU delegation, Play Store readiness, telemetry | Not required, or does not work.                                                       |

### 1.4 Devices

- **Device A (budget, API 33)**: primary development device. Performance problems surface here first.
- **Device B (flagship)**: demo and headline numbers.

Roles are fixed in `docs/DECISIONS.md` (D1). Never swap them. Every verification item in §5 runs on both.

---

## 2. Setup

- Template: Empty Activity (Compose). Kotlin DSL, Gradle version catalog. Package and application ID: `dev.sophiel`.
- `compileSdk = 37`, `minSdk = 26` (`TYPE_APPLICATION_OVERLAY` needs 26+), `targetSdk = 37` (D60).
- Versions live in `gradle/libs.versions.toml` (source of truth for versions; they are a known-good set, resolve conflicts with the AGP Upgrade Assistant and log them in `DECISIONS.md`; do not spend over 30 minutes fighting dependency resolution).
- No Hilt: constructor injection plus the hand-rolled `AppContainer`.
- No NNAPI (deprecated in Android 15). LiteRT CPU path; GPU delegate only if measured latency demands it.
- No Compose inside overlay windows (lifecycle-owner plumbing is a reliable source of lost days). Overlay windows are plain `View`s drawn on a `Canvas`. Compose is for in-app screens only.
- `androidResources { noCompress += "tflite" }`: a compressed model cannot be memory-mapped.
- Release: `isMinifyEnabled = true`, with `-keep class com.google.ai.edge.litert.** { *; }` and `-keep class org.tensorflow.lite.** { *; }` in `proguard-rules.pro`.

---

## 3. Architecture

### 3.1 Modules

| Module      | Type                      | Purpose                                                                   |
| ----------- | ------------------------- | ------------------------------------------------------------------------- |
| `:safecore` | `com.android.library`     | Detection and tile logic. Pure logic + LiteRT. Declares zero permissions. |
| `:app`      | `com.android.application` | Capture, overlay, parent UI, PIN, log, debug tools.                       |

Resist a third module.

### 3.2 Layout

```
safecore/src/main/kotlin/dev/sophiel/core/
  Detector.kt  Verdict.kt  DetectionPipeline.kt
  gate/SkinGate.kt  gate/PerceptualHash.kt
  model/NsfwClassifier.kt  model/Preprocessor.kt      ← Preprocessor is the single source of truth
  policy/PolicyEngine.kt   cache/VerdictCache.kt
  tile/TileGrid.kt  tile/TileMaskTracker.kt           ← NEW (M4)
safecore/src/androidTest/.../ParityTest.kt            ← stays green forever

app/src/main/kotlin/dev/sophiel/
  SophielApp.kt  AppContainer.kt  MainActivity.kt
  capture/   ProjectionService, ProjectionController, ProjectionStateMachine,
             CaptureSession, FrameSource, FrameThrottle, BlackFrameDetector   (done, verified)
             MaskWindowService: + display-shot loop, window events, app lookup  ← M8 (D54-D58)
  overlay/   OverlayController, TileMaskView                                    ← NEW (M5)
  pin/       PinStore (PBKDF2 hash, persisted lockout); PIN pad is a Compose screen ← NEW (M6, D38)
  log/       EventLog (log.csv in app storage, pruned on write)                 ← NEW (M6, D39)
             report text builder                                                ← M8 (D59)
  settings/  SettingsRepository (in memory, SharedPreferences write-through)    ← NEW (M6, D40)
             + protection on/off, always-protect switch, app rules, block ends  ← M8
  guard/     guard page matching, escalation rule (pure logic, JVM-tested)      ← M8 (D56, D58)
  ui/        Status, Setup, Unlock, Settings, Log screens, theme                ← NEW (M5/M6)
             Apps screen, report sheet; BlockActivity (block screen)            ← M8
  feed/      TestFeedScreen
  debug/     DebugPillOverlay, debug menu
tools/       convert_model.py, reference_infer.py
docs/        SPEC.md, DECISIONS.md, LIMITATIONS.md, wayfinder/
```

`eval/images/` stays gitignored; image data is never committed.

No Room, KSP or DataStore. None is configured, and a few hundred scalar rows plus a handful of settings don't need them.

### 3.3 `:safecore` contract (per tile)

This replaces the old single-score `Verdict`. `:app` may use nothing else from `:safecore`. Exact signatures settle in M4, tests first; the shape is fixed.

```kotlin
enum class Severity { SAFE, SUGGESTIVE, EXPLICIT }            // ordinal order is meaningful
enum class Preset(val cols: Int, val rows: Int) { LIGHT(1, 1), BALANCED(2, 3) }   // landscape swaps cols and rows

data class TileVerdict(
    val index: Int,            // row * cols + col
    val severity: Severity,    // stateless mapping of score; all timing lives in TileMaskTracker
    val score: Float,          // raw unsafe probability in [0,1]
    val gated: Boolean,        // skin gate short-circuited the classifier
    val cacheHit: Boolean,
    val hash: Long,            // dHash of this tile's pixels
    val latencyMs: Long,       // from analyze() start to this tile's result
)

interface Detector {
    /** Judge the given tiles of [frame] in [only]'s order (all, by index, if null), emitting each tile as soon as
     *  it is judged, so its mask can go up without waiting for the rest of the sweep.
     *  Caller keeps ownership of [frame] and must not recycle it until collection completes.
     *  Serialises internally onto one inference thread. */
    fun analyze(frame: android.graphics.Bitmap, preset: Preset, only: List<Int>? = null): Flow<TileVerdict>
    fun close()
}
```

Per-tile emission exists for feel: a flagged tile is masked after its own classification, not after the slowest tile in the sweep. `DetectorFactory.create(context, threshold)` stays; the threshold comes from the Parent's sensitivity (§3.5) and is a provider read per tile, so a change applies without a rebuild (D40). `PolicyEngine` becomes a stateless score-to-severity mapping; its old engage/release counters move into the tracker.

### 3.4 Tile state machine (`TileMaskTracker`, pure logic)

The mask is itself captured (§4.2), so a masked tile's own score is meaningless: it would see the mask, score SAFE, release, and strobe. Each tile therefore has three states:

```
CLEAR ──score ≥ threshold for ENGAGE frames──▶ MASKED     (lockedHash = hash of the content)
MASKED ──probe trigger──▶ PROBING                          (mask window removed)
PROBING ──captured tile still shows our mask──▶ keep waiting (cap ~300 ms, then back to MASKED)
PROBING ──hash == lockedHash──▶ MASKED                     (no classification)
PROBING ──hash differs──▶ classify ──≥ threshold──▶ MASKED (new lockedHash)
                                   └─< threshold──▶ CLEAR
```

- **The tracker owns all timing.** The engage count starts at the old `PolicyEngine` value (2 frames). There is no release count: a tile only leaves MASKED through a probe.
- While MASKED, ignore the tile's captured score.
- **Probe validity.** Removing an overlay window does not reach the capture instantly; the next captured frame can still show the mask. A probe frame counts only once the captured tile no longer shows our mask (we know its exact look, so a pixel check is enough). If it has not cleared within the probe timeout, re-mask and wait for the next trigger. Without this rule every probe would read the mask as SAFE, release, and flash.
- **Probe timeout per capture mode (D54):** 300 ms on screen share, 700 ms on accessibility (it must exceed the 400 ms shot gap). On accessibility a probe lifts its mask about one frame before the next shot is due, not at the trigger. Peek (D34) defaults to on.
- **Probe trigger, Balanced (D25):** a frame arrives AND at least half of the masked tile's own CLEAR neighbours (4-adjacent) changed hash since the previous frame. Activity elsewhere on screen never uncovers it. A masked tile with no CLEAR neighbour (every neighbour masked) falls back to the ~2 s timer, counted per tile since it was masked or last probed.
- **Probe trigger, Light:** the only tile is masked, so there are no CLEAR tiles to watch. Probe on the ~2 s timer only, when frames arrive.
- At most one probe per second per tile. Each probe that ends re-masked (or times out) doubles that tile's minimum gap, up to 8 s; a probe that releases the tile resets it (D31). A neighbour change inside the gap is owed a probe when the gap ends, started on a timer if no frame comes (D31). The ~2 s timer is never shorter than the gap. A static screen delivers no new frames (D18), so it never probes.
- Hash lock is **exact match only**. Near-miss matching was rejected in `c7eff9c`.
- **Our own screens.** While a Sophiel activity is in the foreground, hide every mask and pause the tracker (states frozen). Our screens are opaque, so nothing is exposed, and the PIN prompt can never sit under a mask.
- **Mask colour is never pure black (`0x000000`).** `BlackFrameDetector` counts exact-black pixels; a black full-screen Light mask would read as a secure app.
- **Rotation (D29).** The grid follows orientation (Balanced is 3×2 in landscape). If any tile is masked when the configuration changes, cover the whole content area at once. Frames are then ignored until one shows the cover on every tile (the first frames can be the system rotation animation), or for at most 1 s; then every tile of the new grid starts PROBING and the cover comes down (D33). The normal probe rules then apply (one flagged verdict re-masks, 300 ms cap). The cover cannot itself be judged, since the capture sees it, so one whole-screen probe of exposure per rotation is the price. Nothing masked: no cover. A preset change takes the same reset.
- **Protected probe frame (D29).** If a probe frame is protected (`FLAG_SECURE`, mostly black), every PROBING tile goes CLEAR. Otherwise a mask over a secure app never releases.
- The 2×3 grid cost (estimated ~70 ms/tile on Device A, never measured) is settled by measurement in M4. If a 2×3 sweep exceeds ~400 ms on Device A, drop Balanced to 2×2.
- The Light preset is the same machine on a 1×1 grid.

### 3.5 Sensitivity and presets

Parent-facing: **Strict / Normal / Relaxed**, each mapped to a score threshold in code (values tuned in M4; Normal starts at the old 0.70). `PolicyEngine` is a stateless mapping (§3.3); its SUGGESTIVE cutoff already exists (D11) and is reserved for the stretch tier. Preset is a separate **Light / Balanced / Thorough / Precise** choice with a one-line speed-vs-precision description (D48). Sensitivity applies to Light, Balanced and Thorough, including Precise when it falls back to Balanced; Precise masks NudeNet boxes scoring 0.3 or more (D35). The raw threshold slider exists only in the debug menu. A change applies mid-session through the rotation cover path (D40).

### 3.6 Skin gate

A cheap pre-filter that runs before the classifier, on a 64×64 copy, per tile. Below `minRatio` (~0.05) it returns SAFE without classifying. It saves most of the battery budget and keeps ordinary app screens from being flagged.

- YCbCr box: roughly `Cr ∈ [133, 173]`, `Cb ∈ [77, 127]`. Ignore near-black pixels (`Y < 40`).
- **Do not add a minimum-chroma guard** (`Cr − Cb ≥ 20` gated 9 explicit test images; real skin sits at 8–15, D19).
- Known blind spot: true black-and-white imagery has no skin chroma and is gated SAFE (§8).
- `minRatio` and the luma floor are retuned in M4 on real tiles.

---

## 4. The permission model

The highest-risk area. Implement exactly as specified; it is already built and verified (M3).

### 4.1 Manifest

```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<!-- DELIBERATELY ABSENT: android.permission.INTERNET -->
<service android:name=".capture.ProjectionService"
         android:foregroundServiceType="mediaProjection" android:exported="false" />
<!-- D32: hosts the opaque mask window. M8: display shots, window list, window-state events (D54-D58). -->
<service android:name=".capture.MaskWindowService" android:exported="false"
         android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE" />
<!-- M8 (D56, D58): the guard's PIN pad, then the block screen. Its own task, so finishing returns to the page below. -->
<activity android:name=".ui.BlockActivity" android:exported="false" android:excludeFromRecents="true"
          android:launchMode="singleInstance" android:taskAffinity="" />
<!-- M8 (D57): labels for the Log, the Apps list. Not QUERY_ALL_PACKAGES. -->
<queries>
    <intent>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent>
</queries>
```

`res/xml/mask_window_service.xml` (M8): keeps `canTakeScreenshot`, `canRetrieveWindowContent`, `flagRetrieveInteractiveWindows`; adds `accessibilityEventTypes="typeWindowStateChanged"` (all packages, no `packageNames` filter, no content-change events) and `flagReportViewIds`. Node text is read only for the guard, on Settings, package-installer, SystemUI and Samsung-accessibility windows, and only to find "Sophiel" (D56). If Android 14+ hides the "Stop Sophiel?" page from the service, add `isAccessibilityTool="true"` (ticket 04 findings).

**No new permission.** The M8 manifest diff is the activity, the `<queries>` block and the service config. `SYSTEM_ALERT_WINDOW` stays: the app overlay is the start option without the service and the fallback when it is turned off mid-session (D32, D46).

### 4.2 Platform facts the implementation must respect

| Fact                                                                                                                                                      | Consequence                                                                                                          |
| --------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| Consent is required before **each** screen-share session.                                                                                                 | No "remember my choice". Sophiel never asks by itself; without a share it captures by accessibility (D54).           |
| On API 34+, reusing a `createScreenCaptureIntent()` result throws `SecurityException`.                                                                    | Never cache the consent `Intent`.                                                                                    |
| On API 34+, calling `createVirtualDisplay()` twice on one `MediaProjection` throws.                                                                       | On rotation, `VirtualDisplay.resize()` then `setSurface()`. Never recreate.                                          |
| The foreground service (type `mediaProjection`) must be running **before** `getMediaProjection()`.                                                        | Ordering in §4.4 is mandatory.                                                                                       |
| Apps using `FLAG_SECURE` yield black frames.                                                                                                              | Detect them (≥ 90% black pixels, D18) and log **unanalyzable**; never score them.                                    |
| The user can end the session from the system status bar at any time.                                                                                      | Register `MediaProjection.Callback.onStop()` and tear down the share cleanly; protection continues on accessibility (D54). Not guarded (D56). |
| Android 12+ drops touches through an app's overlays when the windows covering the touch point exceed 0.8 combined opacity (per window alpha, not pixels). | Accessibility overlays are trusted and exempt: one window at alpha 1.0 (D32), or 0.79 as an app overlay (D28); never stack another over it.                                         |
| Overlays cannot cover system permission dialogs or parts of system UI.                                                                                    | Never claim total coverage.                                                                                          |
| Android 15+ stops the projection on a secure keyguard; a session cannot outlive screen-off there. Below API 35 (Device A) it survives (D55).              | On API 35+ tear down the share on `ACTION_SCREEN_OFF` (D18); below 35 pause frames and keep the share. What follows is §4.7. |
| Android 15+ hides other apps' notifications while screen sharing (D16).                                                                                   | Not a bug; mention in the limitations. Does not happen in accessibility mode.                                        |
| `MediaProjection` mirrors the composited display, **including our own overlay windows**. So do accessibility display shots.                                | The tile state machine (§3.4) exists because of this.                                                                |
| `AccessibilityService.takeScreenshot()` (API 30+): no consent, no chip, no FGS; at most one shot per 333 ms per service, separate from window shots.          | Shot every 400 ms. Full-resolution `HardwareBuffer`: copy, convert to sRGB (B returns Display P3), scale to §4.6 size, close the buffer at once (C3). |
| Accessibility shots with the screen off succeed but come back black; `FLAG_SECURE` windows are black with no error.                                         | Stop the shot loop on screen-off, restart on screen-on. The black-frame check (D18) works unchanged.                  |
| An enabled accessibility service is rebound by the system at the first unlock after a reboot. From targetSdk 35 a boot receiver may not start a `mediaProjection` FGS. | No boot receiver. The service resumes protection when it binds (D54).                                                |
| `performGlobalAction(GLOBAL_ACTION_HOME)` returns before Home shows; the guarded page is already drawn when its event arrives.                              | A fast tap can beat the guard and the app block. Stated limitation; guard delay measured on A (D56).                 |

### 4.3 `SYSTEM_ALERT_WINDOW` is not a runtime permission

Do not call `requestPermissions()` for it. If `!Settings.canDrawOverlays(context)`, send the user to `ACTION_MANAGE_OVERLAY_PERMISSION` for the package. There is no result callback; re-check in `onResume()`.

### 4.4 Startup sequence (state machine, `ProjectionStateMachine`)

```
IDLE → NEED_NOTIFICATIONS (API 33+; denied → DEGRADED) → NEED_OVERLAY (denied → BLOCKED)
     → NEED_CONSENT (cancel → IDLE, silently)
     → STARTING_SERVICE   ◄── startForegroundService, then startForeground(type=MEDIA_PROJECTION)
     → ACQUIRING_PROJECTION ◄── getMediaProjection only now, inside the service; registerCallback(onStop)
     → RUNNING            ◄── createVirtualDisplay exactly once
     → STOPPING → IDLE
```

### 4.5 `ImageReader` traps

- `rowStride` is padded: allocate `width + rowPadding / pixelStride` and crop, or the bitmap is skewed.
- Every acquired `Image` MUST be closed, or the reader stalls after `maxImages` with no exception.
- `ImageReader.newInstance(w, h, RGBA_8888, 2)` + `acquireLatestImage()`. Drop frames rather than queue them; while a probe waits (D50) or a tile flagged once waits for its next frame (D51), the newest frame is held instead (one, replaced by any newer); a waiting probe also skips the 80 ms throttle (D50). **Decide before decoding** (throttle and in-flight check first). **One frame in flight.** **Close on the owning thread** (reader on its handler thread, interpreter on the inference thread; anything else is a native use-after-free).

### 4.6 Capture resolution

Downscale the `VirtualDisplay` so the short side is 360 px, both dimensions even; pass the real `densityDpi`. Crop the system bars and display cutout (via `WindowMetrics` insets, recomputed in `onConfigurationChanged()`), and **only** those. Never center-crop; recall matters more than tidiness. Tiles (§3.4) are cut from the cropped frame. Each tile is squashed to the classifier's 224×224 input by `Preprocessor`.

### 4.7 Protection and capture mode (M8, D54, D55)

Protection is a persisted on/off set only by the Parent's Start and PIN Stop. Underneath, the capture mode changes on its own. §4.4 still governs starting a screen share; it is now one of three modes, not the whole session.

```
Start ──consent──▶ SCREEN_SHARE ──share ends, protection on──▶ ACCESSIBILITY ──screen off──▶ NONE (shots stopped)
                        ▲          (chip, system, revoked,          │   ▲                         │
                        │           screen-off on API 35+)          │   └──────screen on──────────┘
                        └────Start / notification (fresh consent)───┘
Service unbound with protection on, screen on, no share ──▶ NONE: "Sin protección" stretch
Reboot ──first unlock, service binds──▶ ACCESSIBILITY
Below API 35: screen-off in SCREEN_SHARE pauses frames (closed unread); screen-on resumes the same share.
```

- **Switch "Seguir protegiendo siempre"** (default on; greyed "Necesita la máscara completa" without the service). Off, or service unbound: no ACCESSIBILITY mode. Switch off behaves exactly as M6 (protection ends with the share, D21 notification, D39 OFF reasons). Service unbound with the switch on: the share ends at screen-off as before, the D21 notification is posted, and a Sin protección stretch runs until the next Start.
- **Screen share → accessibility:** masks and tracker carry over, no cover; first shot ~0.4 s later. **Accessibility → screen share:** only by a fresh consent; the projection starts, then the shots stop.
- **No boot receiver, no FGS in accessibility mode.** The screen-share FGS and its notification are unchanged.
- **Trusted app in front (D58):** screen share acquires and closes images unread (VirtualDisplay kept); accessibility stops shots. Scanning resumes on the next window change to a non-trusted app.
- **Notifications (switch on):** share ends with the screen on → "Protección en modo lento · toca para volver al modo rápido" (opens consent). Share ends with the screen off → none. Service turns off with protection on → "Protección interrumpida · toca para reactivar" (opens Status).

---

## 5. Milestones

M0–M3 are **done and reused**: skeleton, model + parity gate, pipeline + Test Feed, capture (M3 V1–V7 verified on both devices). The plan below is ~4 weeks, risk-first, plus **M8**, inserted after M6 and before M7 (D53): order is M6 → M8 → UI polish (a separate effort) → M7. Each milestone ends with a verification block that must pass on **both** devices (the human runs on-device checks).

### M3.5 — Gate (first days of week 1)

- Merge the rescope PR into `main` (`develop`, `feat/pipeline` and `feat/capture` are already fully merged).
- Re-run M3 V1–V7 on a device, and the D21 resume-notification check (D20 and D21 were never verified on-device).
- **Tile work does not start until this passes.**

### M4 — Tile pipeline (week 1)

**Deliverables.** `TileGrid`, `TileMaskTracker` (§3.4), per-tile `Detector.analyze` with per-tile emission, exact-hash lock, Light/Balanced presets. Tests first for the state machine. A temporary on-screen readout of per-tile verdicts. A **crude debug-only mask**: a non-black solid block over each masked tile, touch pass-through. It exists so the probe and hash lock are tested against a mask the capture really sees in week 1, not week 2. Measure real per-tile cost on both devices. Measure whether content straddling a tile boundary is missed. If it is, turn on a **whole-frame safety net**: one extra whole-frame classification, and when the whole frame flags but no tile does, mask the tiles that passed the skin gate.

**Verification**

- `V1` — JVM tests pass for `TileMaskTracker` (every transition in §3.4, including probe validity, Light's timer-only trigger and the own-screen pause), tiling, and exact-hash lock.
- `V2` — Balanced on the Test Feed judges only the tiles it should; Light behaves as one tile.
- `V3` — Real per-tile cost and per-frame sweep time are measured on both devices and written to `DECISIONS.md`. 2×2 fallback applied if needed. Straddling-content result and the safety-net decision recorded.
- `V4` — With the debug mask on, a static flagged image stays masked for 10 s with no flicker, and probe exposure is logged in milliseconds.
- `V5` — `ParityTest` still passes.

### M5 — Overlay (week 2)

**Deliverables.**

- `OverlayController` + `TileMaskView` replace the debug mask: **one full-screen** window that draws every mask (D28): a `TYPE_ACCESSIBILITY_OVERLAY` at alpha 1.0 hosted by `MaskWindowService` (D32), or in debug builds without the service a `TYPE_APPLICATION_OVERLAY` at alpha 0.79. Service turned off mid-session: the masks move to the app overlay. Debug boxes share this window; nothing else touch-through may overlap it.
- Flags `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE | FLAG_LAYOUT_IN_SCREEN`. Every touch, including one on a mask, passes through to the app beneath, so the Child can scroll past masked content.
- Mask look: multi-scale "camo" noise, high-contrast blobs up to 16 px plus ~1 px grain (never pure black), in a muted tint, generated once, tiled and anchored to the screen, never redrawn differently (D30). A small centred chip (lock + "Hidden by Sophiel") that a Parent setting can turn off. Chosen on device (D28); blur-behind is not available on either device.
- The "still shows our mask" check correlates the captured tile's luminance with the pattern the overlay drew at the same screen pixels, centre excluded; colour-free, so any tint works. The threshold is set from device data (D30).
- Masks cover the content area only; system bars stay visible (D28).
- **Coordinate mapping** from capture tiles back to screen pixels: undo the 360 px downscale and add back the system-bar and cutout insets. Recomputed on configuration change. Rotation per §3.4.
- Masks hidden and tracker paused while a Sophiel activity is resumed and not in multi-window mode (§3.4, D28).
- A basic **Status** screen with the on/off switch. Running end-to-end on hard-coded settings. In release builds Start is disabled until `MaskWindowService` is on, with a button to Accessibility settings (D32).

**Verification**

- `V1` — Masks sit exactly over the flagged tiles in portrait and landscape; every touch passes through, including over a mask.
- `V2` — A static flagged image stays masked for 10 s with no flicker; it releases within a few seconds after the content changes.
- `V3` — Probe exposure (mask removed until it is back or the tile is released) is measured in milliseconds, median and worst case, on both devices. At most one probe per second per tile, backing off to one per 8 s while probes keep re-masking (D31). Rotation exposure is measured the same way.
- `V4` — Ending protection from outside the app while the process lives (status bar "Stop sharing", Quick Settings "Active apps" Stop) leaves no overlay window behind. Swiping it out of Recents does **not** stop protection (D28).
- `V5` — With a tile masked, switching to a `FLAG_SECURE` app releases the mask within one probe and it does not flicker (D29).

### M6 — Parent app (week 3) — **FEATURE FREEZE AT END OF WEEK** (lifted for M8, D53)

**Deliverables.**

- **Setup wizard (D42):** runs on launch whenever no PIN exists, and does not depend on the unlock. Create a 4–6 digit PIN (confirm; no PIN recovery, the wizard says so) → overlay permission (required) → accessibility service → keep Sophiel awake → notification permission (may be denied: degraded, the step says so) → preset + sensitivity (Balanced + Normal; Precise disabled with "Android 14+" below API 34) → start (consent; pick "Entire screen" where the dialog offers "A single app"), then Status. Linear with Back; granted steps show done and move on by themselves; only accessibility and notifications can be skipped, each saying what is lost (D46).
  - **Accessibility step:** opens Accessibility settings and completes by itself when the service is **bound**, checked live (D42: an update leaves it unbound ~2 s after the app opens). Always-available, collapsed help for a greyed switch (an APK installed from a file manager, D41): tap it and close "Restricted setting", App info > ⋮ > "Allow restricted settings" (lock-screen check), come back. Tells the Parent to leave the accessibility shortcut off.
  - **Keep Sophiel awake:** App info > Battery > Unrestricted, **and** on Samsung Battery > Background usage limits > Never auto sleeping apps > add Sophiel (Unrestricted alone does not add it, D42).
- **Status** (opens with no PIN): on/off, active preset (the effective one when Precise falls back: "Precise — running Balanced (accessibility off)"), Start, "Settings (parent)". Starting needs no PIN. A "Needs attention" card per missing item (overlay, accessibility service, notifications) with a fix button, no PIN. Start is disabled only while the overlay permission is missing; without the accessibility service it still starts on the 0.79 app overlay, with a note saying the mask will look see-through (D46). Checked live (D42). When protection ended on its own (the last Log entry is an off not by the user), Status says what happened and when until the next start (D45).
- **Mask window follows the service (D42):** when `MaskWindowService` binds again mid-session, the masks move back from the 0.79 app overlay to the opaque accessibility window, adding the new window before removing the old.
- **One door (D38):** **Stop** and **opening Settings** need the PIN; everything behind Settings is gated by being there (preset, sensitivity, peek, show-label, Change PIN, Log, Clear log, debug menu). PIN pad is a Compose screen in `MainActivity`. An unlock ends 2 minutes after the last PIN-gated action, or at once when Sophiel goes to the background or the screen turns off (not on rotation), or when the process dies.
- **Settings:** Strict / Normal / Relaxed, Light / Balanced / Precise (Precise disabled with "Android 14+" below API 34), show-label toggle, peek under mask (D34), Change PIN (the wizard's create + confirm). `SettingsRepository` (D40). The PIN is a `PBKDF2WithHmacSHA256` hash with a 16-byte salt, ~150 ms per check on Device A, off the main thread. Five wrong attempts lock the prompt for 30 s, doubling to at most 1 h; the lockout is persisted (`elapsedRealtime` + boot count) and survives force-stop and reboot.
- **Log** (`log.csv`, pruned on write, D39): kinds masked (time, effective preset, tile or box count, raw max score; the screen shows bands at 0.85 / 0.70 / 0.55, none for Precise), protection on (preset, sensitivity), protection off (reason: user stop / screen off / system ended), unanalyzable (a protected stretch of 1 s or more, written when it ends, with its length, D45). **One masked entry per masking episode**: the screen going from no masks to some, in any preset; probes don't split it, and masks that return within 3 s of the screen clearing continue it. Scalars only: no frames, no app names. Kept 7 days; "Clear log" behind the PIN; summary "N masks today" (since local midnight). A gap from a killed app or reboot is inferred on next start from a once-a-minute last-alive time, as "app was closed" or "phone restarted".
- **Notification:** no Stop action in any build; stopping from the app needs the PIN. "Paused — tap to resume" stays.
- **Debug menu:** 7 taps on the version label at the bottom of Settings (already behind the PIN), every build. Holds Test Feed, Benchmark, mask look, debug pill, model pick, raw slider (in memory only). Starting without the accessibility service is allowed in every build (D46).

**Verification**

- `V1` — Stop and opening Settings prompt for the PIN; the PIN is not stored in plaintext; five wrong attempts lock the prompt, and a force-stop does not clear the lockout.
- `V2` — The log records masks (one per per-screen episode), on/off with a reason, and unanalyzable screens; unit tests with a fake clock prove the episode rule and that entries older than 7 days are pruned.
- `V3` — Wizard completes from a cold install on both devices: uninstall, install over adb, open (wizard shows), PIN + confirm, overlay, accessibility (moves on by itself within ~2 s), keep awake (Sophiel listed in Never auto sleeping apps on Samsung), notifications, preset (Precise selectable on B, disabled on A), Start ("Entire screen" on B). Pass: Status shows protection on and logcat shows `mask window: accessibility, alpha 1.0`.
- `V4` — Changing sensitivity or preset takes effect without restarting the service. With something masked it takes the cover path (D40); its exposure is measured in ms, median and worst, as M5 V3 measured rotation.

**At the end of this week, stop adding features.** Week 4 allows only bug fixes and docs. _Lifted once, for M8 only (D53); the freeze returns at the end of M8._

### M8 — Solution fit (~2 weeks, from 2026-10-12) — **FREEZE RETURNS AT END**

Makes Sophiel credible as parental control: it survives normal life, is hard to turn off, tells the Parent which apps caused masks, escalates on repeated hits, and gets a report to a Parent who isn't holding the phone. Decisions: D54-D60. Build in this order (the map's priority); if the budget is at risk, stop and ask, never drop an item silently.

**Deliverables.**

1. **Capture modes (§4.7, D54, D55).** Persisted protection state; `MaskWindowService` display-shot loop (400 ms, sRGB, closed at once) feeding the unchanged frame path; switching rules; below API 35 screen-off pauses the share; probe timeout and timing per mode (§3.4); peek default on. Switch "Seguir protegiendo siempre" in Ajustes and the wizard's accessibility step. Status state line "Protección activa · compartir pantalla" / "· accesibilidad"; red "Protección interrumpida" with the reason while a Sin protección stretch lasts. Log: ON/OFF only for Start and PIN Stop (OFF reasons `SCREEN_OFF`, `RESTARTED` go with the switch on); new kinds **Compartir pantalla detenido** and **Sin protección** (start, length, reason: accessibility off / app stopped / restarted; boot-to-first-unlock is not a gap, except after a reboot with the service off, where the stretch starts at the boot (D61); the D39 last-alive inference now writes this). Copy changes in the wizard and the Recomendado item (D54, D56).
2. **Tamper guard (D56).** Armed while a PIN exists and no Unlock is active; guards a page only while what it turns off is on. Pages: Accessibility > Sophiel and "Stop Sophiel?" (incl. Samsung's accessibility app), Appear on top, App info, uninstall dialog, Active apps (screen share only). Match by package + text "Sophiel"; confirm One UI package names on A and B first (commands in ticket 04's findings, branch `research/guard-and-attribution`). Fires: open the PIN pad with "Este ajuste está protegido"; Back goes Home; a correct PIN starts an Unlock that survives leaving the app. Log kind **Intento de apagar** (time + target, 60 s merge with a count). Page matching is pure logic, JVM-tested on recorded event fields.
3. **Episode app (D57).** Package of the topmost app window under the strongest mask at episode or unanalyzable-stretch start, last field of `MASKED` / `UNANALYZABLE`; blank without the service, on system UI only, or on Sophiel. Labels at read time. `<queries>` block. Old lines read as blank. Log rows show the app; summary "7 hoy · más en Instagram (4)".
4. **Escalation and per-app rules (D58).** Escalation rule as pure logic (3 masked episodes, same app, rolling 10 min → 15 min block; count restarts after a block; never counts unanalyzable, blank, trusted or excluded apps), JVM-tested with a fake clock. Block end times persisted (wall clock). Enforcement on `typeWindowStateChanged`: Home + `BlockActivity`, while protection is on (Unlock included). Settings "Apps" screen with the escalation switch, search, Normal / Bloqueada / De confianza; excluded apps (Sophiel, Settings, default launcher, default dialer). Trusted apps stop inference (§4.7). PIN on the block screen ends an escalation block. PIN Stop clears escalation blocks. Log kinds **App bloqueada** and **Apertura bloqueada** (60 s merge).
5. **Log and report (D59).** Log dashboard (summary chips, 7-day bars, Por app list) over day-grouped rows with the Todo / Tapados / Otros filter. "Informe" bottom sheet (Hoy / 7 días, text preview, `ACTION_SEND` text/plain); the report text built by a pure function, JVM-tested. Status shows one line per active escalation block, counting down. Spanish strings, English in `values-en/`.

**Verification** (both devices unless stated; the switch on unless stated)

- `V1` — **Survives normal life.** (a) A: Start (screen share), screen off ~10 s and ~2 min, with a secure lock: after unlock Status reads "· compartir pantalla", `dumpsys media_projection` shows the session, the test video is masked. (b) B: same steps: Status reads "· accesibilidad", no consent dialog, the video is masked. (c) Reboot, unlock, do not open Sophiel: the video is masked within ~1 s of playing and Status reads "· accesibilidad". (d) Stop sharing from the chip with the screen on: masks stay, "modo lento" notification, Log "Compartir pantalla detenido"; tapping it asks consent and Status returns to "· compartir pantalla".
- `V2` — **Accessibility capture.** Probe exposure on accessibility, median / p90 / worst, measured on A (probes) and B (peeks) with the test video and reported against ticket 02 (A 436 / 519 / 565 ms untimed); no probe-timeout storm (tiles release when content changes). Logcat shows the shot loop stopping at screen-off and restarting at screen-on. A `FLAG_SECURE` app is logged unanalyzable and its mask releases within one probe. Precise on B masks boxes on accessibility.
- `V3` — **Interruptions.** Turning the service off with protection on posts "Protección interrumpida", Status turns red with the reason, and the Log gains a Sin protección stretch once it ends. `adb shell am force-stop dev.sophiel`, then reopening: Sin protección with reason "app stopped". The switch off: B behaves as M6 (share ends at screen-off, D21 notification, OFF "screen off").
- `V4` — **Guard.** With a PIN and no Unlock, each guarded page is closed to the PIN pad on A and B; a correct PIN returns to the page and it works; the Log shows one Intento de apagar per target (two tries within 60 s are one entry with count 2). With an Unlock active nothing is guarded; with Appear on top revoked, its page opens (turning on is never blocked). Guard delay (event → PIN pad shown) on A, median / worst, reported. `adb uninstall dev.sophiel` still works.
- `V5` — **Episode app.** Masked episodes in Gallery and Chrome show their labels in the Log; in split screen the app under the strongest mask is named; started without the service, rows read "App desconocida". JVM: old lines without the field parse as blank.
- `V6` — **Escalation and rules.** 3 masked episodes in one app within 10 min send the phone Home with the block screen and its countdown; Log "App bloqueada"; reopening shows the block screen and one "Apertura bloqueada"; Status shows the block line; the PIN on the block screen ends it. A block survives a reboot (blocked after the first unlock until its end time). A rule-blocked app shows the block screen with no PIN button. A trusted app shows no masks and logcat shows inference stopped; a split screen with one non-trusted app is still scanned. Excluded apps are not in the Apps list. JVM tests for the escalation rule pass.
- `V7` — **Log and report.** The dashboard's counts match the rows; "Informe" opens the share sheet with the text, whose counts match the Log for Hoy and 7 días (JVM test of the report text).
- `V8` — **Still true.** `aapt dump permissions` shows no `INTERNET` and no new permission; the manifest diff since M6 is the activity, `<queries>` and the service config. `ParityTest` and every M4-M6 JVM test pass. M5 V1 and V2 still pass on screen share.

**Stretch, only with slack once V1-V8 pass:** schedules (stricter protection at night, time windows), optionally with a reminder notification for the daily / weekly report (D60). Not specified: plan them with the human before building.

**At the end of M8, the feature freeze returns** (CLAUDE.md): bug fixes for failing verification items and measurement only.

### M7 — Harden and ship (after M8 and UI polish)

**Deliverables.** On-device verification of M4–M8 on both devices; `docs/LIMITATIONS.md` from §7 with measured numbers, including the privacy line (D57); `README.md`; a written demo runbook and a recorded demo video (record early in the week). The runbook shows the M8 story: survive a reboot without opening Sophiel, a guarded settings page asking for the PIN, an escalation block, the report shared through a chat app. Stretch, only with slack: the softer "suggestive" tier (pixelate or blur at mid scores, needs a second Parent threshold).

**Verification**

- `V1` — 10-minute soak on both devices, on each capture mode: no stall or crash.
- `V2` — `aapt dump permissions` shows no `INTERNET`; ordinary app screens (settings, chat, maps) trigger no masks (spot check).
- `V3` — **Feel.** End-to-end latency (frame available → mask drawn) and CPU, memory and battery during the soak are measured and reported per preset and per capture mode (the accessibility event listening is included in the accessibility-on numbers). These are reported, not pass/fail.
- `V4` — A clean clone builds with `./gradlew :app:installDebug` and no manual steps; the demo runbook is reproducible by someone else.

**Stretch targets** (reported, not gated; the original targets): p50 latency under 800 ms on Device A and under 400 ms on Device B; throughput at least 4 fps on A and 8 fps on B; a formal evaluation set and UI false-positive corpus.

---

## 6. Data and demo policy

- **Never commit image data.** Commit labels and metrics, never pixels. `eval/images/` is gitignored.
- **Live demo content:** beach, swimwear and fitness imagery scores in the middle band, so the demo shows threshold behaviour. Keep genuinely explicit material out of the demo and the repo.
- **Test Feed** is the permission-free fallback demo if capture or the overlay misbehaves in front of an examiner.

---

## 7. Known limitations — state these, do not hide them

Copy into `docs/LIMITATIONS.md` and expand with measured numbers.

1. **Reactive, not preventive.** Analysis happens after content is drawn. A fast reader may glimpse it before the mask lands.
2. **Probe exposure.** To find out whether masked content has changed, a probe uncovers the tile until the capture confirms what is underneath. Bounded by the one-per-second rule and the exact-hash lock, and reported in milliseconds, but not zero.
3. **Tile granularity.** Masks are tile-sized, not object-sized. Content that straddles tiles is judged per tile and can score below the threshold in each. Measured in M4 (D26): feed-sized content is still caught (6-7 of 8 straddling the centre line), but content about one tile in size that sits on a boundary is mostly missed, and a whole-frame pass does not catch it either, so there is no safety net. A mask can also cover more than the content.
4. **`FLAG_SECURE` blindness.** Secure windows (banking apps, incognito) yield black frames and cannot be analysed; the Log records them as unanalyzable.
5. **Consent friction and the slower mode.** Screen share needs fresh consent every session. With the accessibility service on, protection survives reboot, screen-off and a stopped share by capturing with accessibility screenshots, which mask more slowly (about 0.2-0.8 s from content to mask, vs 0.1-0.25 s). Getting back to screen share takes one tap and a consent (D54). Device A keeps its share through screen-off (D55); Device B (Android 15+) drops to accessibility at every screen-off. Without the service, a screen-off ends protection until the next Start.
6. **The Child can still end it.** The guard (D56) closes Sophiel's accessibility toggle, "Appear on top", App info and uninstall dialog, and "Active apps" Stop, and asks for the PIN. It cannot stop: the accessibility shortcut (volume keys, if assigned), safe mode, adb, a factory reset, uninstalling from the Play Store app, or a tap faster than the guard (one-tap "Active apps" Stop is the likely winner; guard delay measured on A). Stopping the screen share is allowed and only slows masking. A force-stop that wins the race turns the accessibility service off until the Parent turns it back on (D41), and shows in the Log as Sin protección. There is no Device Admin, so nothing blocks uninstall at the system level, and no PIN recovery. Without the accessibility service there is no guard at all.
7. **Log gaps.** If the app is killed or the phone reboots, the gap is inferred from a last-alive time kept once a minute, so its end is accurate to about a minute; it shows as Sin protección (D54). After a reboot with the accessibility service off, the stretch starts at the boot, so time before the first unlock counts too (D61). Log times and escalation block ends use the wall clock, which the Child can change to scramble the Log or shorten a block (D39, D58).
8. **Threshold is a value judgement.** "Explicit" is contextual; sensitivity is Parent-set because no single value is right.
9. **Single-model bias.** Inherits the biases of its training data (GantMan MobileNetV2, D12).
10. **Overlay gaps.** System dialogs and parts of system UI cannot be covered; Android 15+ hides other apps' notifications while screen sharing (D16), not in accessibility mode.
11. **Grayscale blindness in the skin gate.** True black-and-white imagery has no skin chroma and is gated SAFE. Do not "fix" it by classifying every achromatic tile: dark-mode UIs are achromatic too.
12. **Energy figures are whole-device estimates**, valid only unplugged.
13. **Low capture resolution.** Frames are 360 px on the short side; small or distant content may be missed.
14. **Masks are not opaque without the accessibility service.** Android blocks touches through another app's overlay above 0.8 opacity, so if the service is turned off mid-session (or in a debug build started without it) masks draw at 0.79 and about 21 % of the masked content shows through under the noise (D28, D32). Turning it off is one more thing the Child can do (item 6), in one tap if the accessibility shortcut is assigned; it weakens the masks but does not remove them, and they return to opaque when the service is back on (D42).
15. **System bars are never masked or judged.** Full-screen apps (video, gallery) draw under the hidden bars, and the capture always crops the bar area (§4.6), so that strip is not covered.
16. **Change only under a mask stays masked.** A masked tile probes when its CLEAR neighbours change (D25), so content that changes only inside it (a video exactly under the mask) stays masked until something next to it moves. With Reveal gone (D27), the Parent's fix is stop and restart.
17. **Rotation exposure.** After a rotation with something masked, the whole screen is uncovered for one probe while the new grid is judged (§3.4): measured 73-161 ms on Device B, 94-218 ms on Device A on screen share (D33); 376-520 ms on accessibility (ticket 02, untimed probes; re-measured in M8 V2).
18. **Opaque masks can hide a system button.** The accessibility window sits above system UI panels, so the rotate-suggestion button (auto-rotate off, gesture navigation) can be hidden under a mask; tapping there still works (D32).
19. **Precise is Android 14+ and model-bound (D35).** It needs window screenshots, so Device A (Android 13) runs Balanced instead. While scrolling, new content shows until NudeNet's frame verdict lands (~100-200 ms on Device B), and masks trail moving content by up to the 400 ms screenshot gap (Android allows one per 333 ms). NudeNet's weights are gitignored: copy `nudenet_320n.onnx` into `app/src/main/assets/` before building (D24).
20. **Install over adb.** Play Protect blocks installing the APK from a file manager (Device A: "App not installed", no override), and such an install also needs "Allow restricted settings" before the accessibility service can be turned on. `adb install` / Android Studio has neither problem (D41, D42).
21. **Samsung may put Sophiel to sleep.** One UI sleeps unused apps by default; a deep-slept app likely loses its accessibility service like a force-stop. The wizard has the Parent add Sophiel to "Never auto sleeping apps"; the app can't check it later (D42).
22. **Masks flicker when the accessibility service turns off.** The system destroys the accessibility window with the service, before `onUnbind` runs, so the masks can't be added to the 0.79 app overlay first and are briefly uncovered. Turning it back on is seamless (add new window, then remove old; D42). Seen on Device A in ticket 11's check. A fix needs a second standing overlay window, which is out of scope after the freeze.
23. **Device A's slower mode exposes more.** Without peek (Android 14+), a probe on accessibility uncovers its tile 2-3x longer than on screen share (measured untimed 436 ms median on A, vs 61 ms; D54). A uses this mode after a reboot or a stopped share.
24. **The Log names apps.** The Log names the app that was on screen when something was hidden: the app's name, never what was in it. No pixels, no text, no window titles. It stays on the phone for 7 days (D57). An episode that carries across an app switch within 3 s is counted against the first app. Without the accessibility service the app is unknown.
25. **App blocking is best effort.** It needs the accessibility service. A blocked app already in picture-in-picture keeps playing until closed (masking still covers it); a blocked app's notifications still arrive; a fast tap can briefly show the app before Home (D58). Trusting an app (a browser, say) lets anything in it through unscanned.
26. **The report leaves through another app.** Sophiel sends nothing; the Parent shares a text summary through the share sheet, and the receiving app (chat, email) sends it under its own policy (D59).

---

## 8. Quick reference

```bash
./gradlew :app:installDebug                                   # build + install (default: Device A)
aapt dump permissions app/build/outputs/apk/debug/app-debug.apk | grep -i internet   # must be empty
./gradlew :safecore:test                                      # JVM: gate, hash, policy, tile tracker
./gradlew :app:testDebugUnitTest                              # JVM: state machine, throttle, log
./gradlew :safecore:connectedAndroidTest                      # device: ParityTest
adb logcat -s Sophiel:D
adb shell dumpsys media_projection
adb shell dumpsys activity services dev.sophiel | grep -i foreground
adb shell dumpsys accessibility | grep -i sophiel             # service bound? (M8 capture mode, guard)
adb shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp"  # package/class of a page to guard (M8)
adb shell screenrecord --time-limit 60 /sdcard/demo.mp4 && adb pull /sdcard/demo.mp4
python tools/convert_model.py --out safecore/src/main/assets/nsfw.tflite
python tools/reference_infer.py --fixtures safecore/src/androidTest/assets/fixtures
```

---

## 9. Milestone checklist

- [x] **M0** Skeleton — builds, installs, no `INTERNET`
- [x] **M1** Model — converted, parity gate passed
- [x] **M2** Pipeline + Test Feed — permission-free, end-to-end
- [x] **M3** Capture — frames flowing, all permission paths handled
- [x] **M3.5** Gate — branches merged; M3 V1–V7 and D21 re-verified on device (2026-10-03)
- [x] **M4** Tile pipeline — state machine, hash lock, measured cost (D25, D26)
- [x] **M5** Overlay — one full-screen noise-mask window, Status screen (D28, D29, D36)
- [x] **M6** Parent app — wizard, PIN, settings, 7-day log · **FEATURE FREEZE**
- [ ] **M8** Solution fit — capture modes, guard, episode app, escalation + rules, Log dashboard + report (D53-D60) · freeze returns
- [ ] **M7** Harden + ship — soak, feel numbers, limitations, demo video
