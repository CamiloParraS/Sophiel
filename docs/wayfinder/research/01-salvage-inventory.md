# Research 01: Salvage inventory

Ticket: `docs/wayfinder/tickets/01-salvage-inventory.md`. Destination: parental-control app with per-region (tile) masking, PIN-protected settings, local blocked-event log (`map.md`).

Sources: every claim below comes from the repo itself (primary source): `docs/SPEC.md`, `docs/DECISIONS.md`, `CLAUDE.md`, `CONTEXT.md`, and the Kotlin sources, read at main (`0b1a965`). Nothing here depends on outside documentation. Verdicts: KEEP = reuse as is, ADAPT = reuse with a stated change, CUT = delete or do not carry into the new SPEC.

Facts that shape every verdict:

- The repo has no `bench/`, `settings/`, `overlay/` or `eval/` code and no `tools/eval.py`. SPEC §3.2 and M4/M5 list them, but only the M0-M3 code exists. The Benchmark tab is a "coming soon" stub (`MainActivity.kt`). So "CUT benchmark/eval" means cutting SPEC text, not code.
- Detection takes a whole `Bitmap` and returns one `Verdict` (`Detector.kt`). Nothing in `:safecore` knows about regions yet.

## 1. `:safecore` (detection)

| File | Verdict | Reason |
| --- | --- | --- |
| `core/Detector.kt` (`Severity`, `Verdict`, `Detector`, `DetectorFactory`) | ADAPT | The frozen contract (SPEC §3.4) is one verdict per frame; tiles need a per-tile result (e.g. a list of scores or flagged cells) while Light keeps the single score. `DetectorFactory.create(threshold)` also needs the Parent-set sensitivity at runtime, not only at construction. |
| `core/DetectionPipeline.kt` | ADAPT | Orchestration, single inference thread, close-on-owning-thread (D19) are all correct and hard-won; hash/cache/gate/policy must run per tile (or per frame then per tile) in Balanced. |
| `core/model/NsfwClassifier.kt` | KEEP | GantMan MobileNetV2, score = hentai+porn+0.5*sexy (D12) is a stated Keep in `map.md`; it already classifies a 224x224 input, which fits a tile. Note the `sexy`-only cap of 0.5 (D12) interacts with Parent sensitivity: a slider range above 0.5 can never flag sexy-only content. |
| `core/model/Preprocessor.kt` | KEEP | Single source of truth for RGB/0-1/NHWC and the ParityTest; works per tile with no change except that tiles are squashed to 224 from a non-square crop (SPEC §4.6 squash rationale was for whole frames). |
| `core/gate/SkinGate.kt` | ADAPT | Luma floor only (D19). Applied per tile it becomes the main cost saver (skip tiles with no skin), but the known B&W blind spot (SPEC §6.1) is now a masking miss on a specific region, and thumbnail-sized tiles were already measured at 0.013 skin ratio (D19), so `minRatio` needs retuning for tiles. |
| `core/gate/PerceptualHash.kt` | ADAPT | Works on any bitmap; per-tile hashes are useful for dedupe but the whole-screen 9x8 hash is too coarse (the reason for the D-cache fix at `c7eff9c`). Decide whether to hash per tile. |
| `core/cache/VerdictCache.kt` | ADAPT | Now exact-hash only. CaptureSession itself notes the hit rate may be ~0 and the cache "should go" (D17 platform finding: no frames for a static screen). Keep only if per-tile hit rate is measured non-trivial; otherwise CUT. |
| `core/policy/PolicyEngine.kt` | ADAPT | 2-up/3-down hysteresis (SPEC §6.2) is still the anti-strobe rule but is per-region state now; threshold becomes Parent sensitivity. SUGGESTIVE (D11: threshold/2) has no role in "mask or don't", so likely drop that tier. |
| `safecore` assets `nsfw.tflite` | KEEP | Map says keep model. |
| `safecore` tests (`VerdictCacheTest`, `PerceptualHashTest`, `SkinGateTest`, `NsfwClassifierTest`, `PreprocessorTest`, `PolicyEngineTest`) | KEEP / ADAPT | Follow their subject: KEEP for classifier/preprocessor; ADAPT policy/cache/gate tests with the per-tile changes. |
| `androidTest`: `ParityTest`, `fixtures/`, `expected_logits.json` | KEEP | Cheap regression guard on preprocessing (the project's highest silent-failure risk). |
| `androidTest`: `DetectorLeakTest` | KEEP | Guards close/recreate leaks (D13); close/recreate will happen more often when presets switch. |
| `androidTest`: `PipelineHysteresisTest` | ADAPT | Follows PolicyEngine changes. |
| `safecore` manifest (zero permissions) | KEEP | No-INTERNET claim (SPEC V3). |
| `tools/convert_model.py`, `tools/reference_infer.py`, `requirements.txt` | KEEP | Pin the model by SHA-256 and produce parity fixtures; only needed if the model or preprocessing changes. |

## 2. `:app` capture (verified M3)

All of this is the stated Keep ("MediaProjection capture pipeline, verified on both devices"), with the caveat that D20/D21 changes are not re-verified on-device (CLAUDE.md BLOCKED ON).

| File | Verdict | Reason |
| --- | --- | --- |
| `capture/ProjectionStateMachine.kt` + `ProjectionStateMachineTest` | KEEP | Pure reducer implementing the load-bearing SPEC §4.4 order; reused by the parent "Start protection" flow. |
| `capture/ProjectionController.kt` | KEEP | Effects-on-phase-entry design fixed the Settings loop (D17/D20); parent UI just binds to its `StateFlow`. |
| `capture/ProjectionService.kt` | KEEP | startForeground-before-projection, ACTION_STOP, screen-off teardown, resume notification (D21). Notification text will change (parental wording). Note: D21 resume notification and D20 refactor not re-verified on-device. |
| `capture/CaptureSession.kt` | ADAPT | Owns display, FrameSource, detector, backpressure. It is the one place where `detector.analyze(bitmap)` returns a single verdict and `onStatus` is called; this is the seam where per-tile results go to a mask controller and the blocked-event log. |
| `capture/FrameSource.kt` | KEEP | rowStride crop, always closes Image, decide-before-decode, owns-thread close (D19). |
| `capture/FrameThrottle.kt` + test | KEEP | 80 ms floor; may need a Light vs Balanced interval, but the class is generic. |
| `capture/BlackFrameDetector.kt` + test | KEEP | FLAG_SECURE detection (D18); a parental app must show "protected content" rather than silently scoring 0. A secure app is also the obvious bypass, worth stating in limitations. |
| `capture/DebugPillOverlay.kt` | ADAPT | Debug-only (D18), plain View per D4. Keep as hidden debug menu or delete; either way it is not the mask. It is also a working template for `OverlayController`'s plain-View `TYPE_APPLICATION_OVERLAY` plumbing (main-thread Handler). |
| `AppContainer.kt`, `SophielApp.kt` | ADAPT | Hand-rolled DI (D2/D14); gains settings repository, PIN store, event log. |
| `MainActivity.kt` | ADAPT | The permission effects and resume handling are KEEP; the `Destination` bottom bar (Protection / TestFeed / Benchmark) and the raw-state-name `protectionScreen` are placeholder UI and are replaced by the parent flow and screens (ticket 04). |
| `feed/TestFeedScreen.kt` + `assets/testfeed/*.png` | ADAPT | Permission-free exerciser is valuable as a demo fallback and tile-grid dev harness (SPEC §6.4, human-labelled "Test Feed" in `map.md` not-yet-specified), but currently shows per-image verdicts, not per-tile. Candidate for the hidden debug menu. |
| Manifest permissions (SYSTEM_ALERT_WINDOW, FOREGROUND_SERVICE(+MEDIA_PROJECTION), POST_NOTIFICATIONS) | KEEP | Needed for capture and overlay; INTERNET stays absent. |
| Tests: `BlackFrameDetectorTest`, `FrameThrottleTest` | KEEP | Pure JVM. |

## 3. Overlay, settings, benchmark, eval (specified, not built)

| Item | Verdict | Reason |
| --- | --- | --- |
| `MaskView` / `OverlayController` (SPEC M4) | ADAPT (spec) | Not built. The spec describes one full-screen scrim; replace with a plain-View that draws masks over flagged tiles, same window flags (`FLAG_NOT_FOCUSABLE`, touch pass-through). The feedback-loop trap (mask is captured, D19/SPEC M4) is the central open problem and belongs to ticket 02. |
| Tap-to-reveal (3 s) | CUT/ADAPT (spec) | `CONTEXT.md` says Reveal requires the Parent's PIN; a child tap-to-reveal contradicts the product. |
| Threshold slider + DataStore (SPEC M4) | ADAPT (spec) | Becomes Parent-set sensitivity behind the PIN. |
| `bench/BenchmarkRunner`, `BenchmarkScreen`, ablation, CSV (SPEC M5) | CUT | Not built; `map.md` makes eval/benchmark debug-only and not a deliverable. |
| `tools/eval.py`, `eval/labels.csv`, ROC, 300-image set (SPEC §7.2, M5) | CUT | `map.md` out-of-scope. Keep `eval/images/` gitignored (hard rule 4) if anything is added later. |
| UI corpus (SPEC §7.3) | CUT | It tuned the skin gate for FP rate; informal sanity screenshots are enough now. |

## 4. DECISIONS D1-D21

| # | Verdict | Reason |
| --- | --- | --- |
| D1 Device roles | KEEP | `map.md` Notes repeats it. |
| D2 No Hilt | KEEP | AppContainer grows a little; still no DI framework. |
| D3 No NNAPI | KEEP | |
| D4 No Compose in overlay | KEEP | Tile masks are canvas-drawn plain Views. Compose is for the parent UI only. |
| D5 One model | KEEP | |
| D6 Toolchain versions | KEEP | |
| D7 `dev.sophiel` package | KEEP | |
| D8 `:safecore` contract stubbed | ADAPT | The frozen `Verdict` contract is what per-tile masking changes. |
| D9 AutoML model | CUT | Superseded by D12; history only. |
| D10 `org.tensorflow:tensorflow-lite` | KEEP | |
| D11 SUGGESTIVE = EXPLICIT/2 | ADAPT | Likely dropped: only flagged vs not matters for masking. |
| D12 GantMan model + score formula | KEEP | Stated Keep. Its "sexy caps at 0.5" consequence must be reconciled with sensitivity. Its deferred "Region blur... out of scope" is now the destination, so that line flips. |
| D13 Leak re-verification | KEEP | Evidence only. |
| D14 AppContainer / controller wiring | KEEP | |
| D15 Debug pill deferred | CUT | Superseded by D18. |
| D16 Android 15+ notification redaction during screen share | KEEP | Directly relevant to a child who uses messaging apps; belongs in limitations. |
| D17 M3 findings (Device A) | KEEP | Redraw-driven frames platform fact stays true. |
| D18 Debug pill, V7, screen-off | KEEP | |
| D19 Hardening (crash, backpressure, crop, skin gate) | KEEP | Its "Crop" rationale (no center-crop; squash) must be revisited for tiles. |
| D20 Capture code-quality pass | KEEP | Not re-verified on-device. |
| D21 Resume notification | KEEP | Not re-verified on-device. Fits parental use: consent is required again after every screen-off. |

## 5. Contradictions in `docs/SPEC.md` and `docs/DECISIONS.md` with the new destination

1. **SPEC §1.1/§1.3/§6.6/§8.4 and D12 "Region blur" say whole-frame blur only and refuse sub-region localization.** The new destination is exactly sub-region masking. Reverse §1.3 and rewrite §6.6.
2. **§1.1 "single-user app", §1.3 "accounts... not a shipping product".** A Parent/Child role split, PIN, and per-parent sensitivity are new scope. Accounts/sync can stay out.
3. **§1.2 "tap-to-reveal" and M4 "3-second temporary dismissal"** vs `CONTEXT.md` Reveal needs the PIN.
4. **M4 "Threshold slider in Settings"** has no PIN gate; the new SPEC needs the PIN-protected settings screen. The old text is user-facing for the user, the new one is Parent-facing.
5. **§3.4 frozen `Verdict` / `Detector` contract** is single-score per frame; tiles break it.
6. **§3.2 module layout and §3.1 "Two modules. Resist adding a third."** lists `bench/`, `settings/`, `overlay/`; the new SPEC needs `events/` (blocked-event log, scalars only) and a PIN store, with Room already in the version catalog (SPEC §2.3) but never wired. Hard rule 3 (no persisted frames) still permits the log.
7. **§4.2 "MediaProjection mirrors our own overlay windows"** and M4 feedback-loop trap: per-tile masks make the problem worse (the mask hides exactly the pixels needed to decide to keep masking) and the M4 sketch (freeze verdict while masked) was whole-frame. Ticket 02 owns this.
8. **§1.4 success criteria** (recall >= 0.90 on a held-out set, FP <= 0.05 on the UI corpus, 2x2 benchmark table) and **§7, M5, §8 items 7 and 9** assume the eval harness that `map.md` cuts. Also latency target "frame available -> overlay drawn < 400/800 ms" needs redefinition for tile masks.
9. **§5 six-week milestone plan, M4 feature freeze** vs the new four-week plan: the milestones M0-M3 are done, M4 changes meaning, M5/M6 shrink.
10. **CLAUDE.md** "CURRENT MILESTONE" and "Hard rule 8: no features outside SPEC §1.2" become wrong until the SPEC is rewritten; "Device A/B success thresholds" likewise.
11. **D15 and the `DebugPillOverlay` note** say the pill must not be conflated with M4. Still true, but "gated out of any release build" (D18) conflicts with `map.md`'s open question of a hidden debug menu.
12. **D17/D18 tell the Parent-visible story wrongly if reused**: the only mention of Chrome Incognito/Secure Folder as partial FLAG_SECURE shows the system cannot guarantee coverage; a parental pitch must state it, which SPEC §8.2 already does but frames as a minor limitation.
13. **SPEC §2.2/§2.3 version tables** are stale vs D6, already recorded; no new contradiction.

## 6. Risks surfaced by the inventory

- `CaptureSession` is the only coupling point between detection and any future mask; it is small (147 lines), so the rewrite risk is low there.
- D20 and D21 and the cache fix are not verified on-device; the next device session should re-run M3 V1-V7 before building on them (already CLAUDE.md's blocker).
- Per-tile classification multiplies model calls by tile count; the skin gate is the only cost guard, and it was measured to over-gate thumbnail-sized regions (D19: ratio 0.013). Cost on Device A is unmeasured (D1 reports ~20-30 ms extra per call vs Device B; p50 43 ms on Device B).
