# 06: Drive the tracker from live capture, behind a debug mask

**What to build:** During a real capture session, each captured frame goes through the tile tracker: masked tiles are skipped, CLEAR tiles and probing tiles are analysed, and each tile's result is applied as soon as it arrives. A crude, debug-only mask (a non-black solid block per masked tile, touches passing through) is drawn so that the capture really sees our mask. This is the point of the ticket: it tests the probe, probe validity and hash lock against the real feedback loop in week 1, instead of discovering a flicker in week 2. The polished overlay is M5.

**Blocked by:** 03, 04

**Status:** ready-for-agent

- [x] Live frames update per-tile states; masked tiles are not analysed, so a frame costs less when tiles are masked.
- [x] The debug mask appears over each masked tile in roughly the right place, never pure black, and lets every touch through; it is debug-only and disappears when protection stops.
- [x] The "still shows the mask" input to the tracker comes from a cheap pixel check of the captured tile against the mask's known look.
- [x] On a device, with the debug mask on, a static flagged image stays masked for 10 s with no flicker, and a static screen produces no probes.
- [x] Changing the content on screen triggers a probe that releases or re-masks correctly; probe exposure (mask removed until it is back or released) is logged in milliseconds.
- [x] The debug readout and Logcat show each tile's state (CLEAR, MASKED, PROBING, REVEALED) and probe events.
- [ ] The existing capture guarantees hold: one frame in flight, decide before decoding, owning-thread closes, clean teardown on stop and screen off.
- [x] JVM tests pass; `:app:assembleDebug` succeeds; no `INTERNET` permission.

## Comments

Code done, not yet run on a device (both were locked; the visual checks need a person anyway). Built on top of the NudeNet spike (human decision): the tracker and debug mask drive the GantMan judge only; NudeNet stays whole-frame.

- Protection screen has LIGHT/BALANCED chips (`AppContainer.livePreset`); preset or rotation change resets the tracker.
- Masked tiles are skipped via `Detector.analyze(..., only)`; the debug pill shows per-tile states (`M C C P C C`) and how many tiles were analysed.
- Logcat (`adb logcat -s Sophiel`): `tile i A -> B`, `mask episode tile=i`, `probe tile=i exposureMs=N -> STATE`, and per-tile score lines.
- `DebugMask.looksMasked` samples 8x8 points against the mask colour (purple, never black), tolerant of the 0.8 window alpha; errs towards "masked".
- `TileMaskTracker.expireProbes` runs 300 ms after a frame that left a tile PROBING, so a screen that goes static after the mask lifts still re-masks.

Human checks (Device A first): start protection on BALANCED, open a flagged image full screen in a gallery, leave it 10 s (expect one episode, no `PROBING` lines, no flicker); scroll to new content (expect a probe with `exposureMs` logged, then release or re-mask); tap through a mask; rotate (masks reset); stop protection (mask gone).

**2026-10-02, first device run: every touch on the phone was blocked** ("Sophiel isn't optimized for the latest Android version… touching some areas may not work"). Cause: Android 12+ untrusted touch occlusion counts the *combined* opacity of one app's overlays, by window alpha, not pixels. The spike's full-screen box window (alpha 0.8) plus a second full-screen mask window (alpha 0.8) = 1 − 0.2² = 0.96 > 0.8. Fix: masks are drawn in the same window as the boxes (`DebugBoxOverlay.updateMasks`). **M5 constraint:** all touch-through overlay windows together must stay ≤ 0.8 combined opacity, i.e. one full-screen window at 0.8, never one window per mask. Worth a row in SPEC §4.2.

**2026-10-02, device results (human-run, logs pulled by agent).**
- Device A: tiles masked on flagged content; two static stretches of 30 s and 35 s with masked tiles and **no probes**; tile 4 re-masked on its locked hash ~15 times (exposure 70-100 ms). Touches pass through.
- Device B, 127 probe frames: the mask check is cleanly bimodal. Mask still captured: `match=64/64`, mean ≈ #8c2c9a-#ae4ebb (purple at 0.8 alpha). Mask gone: `match=0/64` (one 1/64), content colours. Nothing in between, so the tolerance has wide margin. B's capture is often fast enough that the first probe frame no longer shows the mask.
- Outcomes on B: 114 re-masks, 13 releases.
- **Open for ticket 07 (feel):** whenever anything on screen moves, a masked tile probes every 1-2 s (hash trigger or timer) and its flagged content is visible for ~70-100 ms each time, i.e. ~5-8% of the time while scrolling. Per SPEC §3.4, but visible. Candidates: longer timer, or skip the hash-trigger when the masked tile's own neighbourhood is unchanged.
- Not separately re-tested: the capture guarantees (one frame in flight, teardown on stop and screen off); no code in those paths changed beyond the shared scope becoming single-lane.
