# 06: Drive the tracker from live capture, behind a debug mask

**What to build:** During a real capture session, each captured frame goes through the tile tracker: masked tiles are skipped, CLEAR tiles and probing tiles are analysed, and each tile's result is applied as soon as it arrives. A crude, debug-only mask (a non-black solid block per masked tile, touches passing through) is drawn so that the capture really sees our mask. This is the point of the ticket: it tests the probe, probe validity and hash lock against the real feedback loop in week 1, instead of discovering a flicker in week 2. The polished overlay is M5.

**Blocked by:** 03, 04

**Status:** ready-for-agent

- [ ] Live frames update per-tile states; masked tiles are not analysed, so a frame costs less when tiles are masked.
- [ ] The debug mask appears over each masked tile in roughly the right place, never pure black, and lets every touch through; it is debug-only and disappears when protection stops.
- [ ] The "still shows the mask" input to the tracker comes from a cheap pixel check of the captured tile against the mask's known look.
- [ ] On a device, with the debug mask on, a static flagged image stays masked for 10 s with no flicker, and a static screen produces no probes.
- [ ] Changing the content on screen triggers a probe that releases or re-masks correctly; probe exposure (mask removed until it is back or released) is logged in milliseconds.
- [ ] The debug readout and Logcat show each tile's state (CLEAR, MASKED, PROBING, REVEALED) and probe events.
- [ ] The existing capture guarantees hold: one frame in flight, decide before decoding, owning-thread closes, clean teardown on stop and screen off.
- [ ] JVM tests pass; `:app:assembleDebug` succeeds; no `INTERNET` permission.
