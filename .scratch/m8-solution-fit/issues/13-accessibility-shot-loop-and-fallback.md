# 13: Accessibility shot loop and automatic fallback

**What to build:** `MaskWindowService` takes a display shot every 400 ms (sRGB, closed at once) and feeds the unchanged frame path. When the screen share ends with protection on and the switch on, the capture mode becomes `ACCESSIBILITY`: masks and tracker carry over, no cover, first shot ~0.4 s later. The loop stops at screen-off and restarts at screen-on. Probe timeout is per mode: 300 ms screen share, 700 ms accessibility (§3.4). Demo: stop sharing from the chip and the test video stays masked.

**Blocked by:** 11

**Status:** ready-for-human (built 2026-10-12; device check on A and B pending)

- [ ] Shot loop at 400 ms; Display P3 shots converted to sRGB on copy; each shot closed at once (no stall)
- [ ] Share ending (chip, system, revoked) with protection and switch on switches to `ACCESSIBILITY` with no cover and no mask loss
- [ ] Loop stops at screen-off and restarts at screen-on (visible in logcat)
- [ ] Probe timeout 300 ms on screen share, 700 ms on accessibility; no timeout storm
- [x] Switch off or service unbound: no `ACCESSIBILITY` mode. Turning the switch off while in `ACCESSIBILITY` makes the machine clear `protecting`: persist it in `ProtectionStore` (ticket 11 note)
- [ ] Rotation keeps the D33 cover path
- [ ] A `FLAG_SECURE` app is logged unanalyzable and its mask releases within one probe
- [x] Route the live rules through `CaptureModeMachine.reduce` (review of 11-12): today `ProjectionService` re-derives the screen-off pause (`keepProtecting && MaskWindowService.instance != null`) and the switch-off clear at teardown, and `StatusModel.mode` is hardcoded. One source for the "switch on and service bound" check. The machine also needs a `SwitchOn` event (ticket 18 too)
- [x] Ends buildable

## Notes (built 2026-10-12)

- **Design.** New `CaptureModeController` (in `AppContainer`) owns `CaptureModeMachine` and the one `CaptureSession`; it is the only writer of `ProtectionStore` and of the Log's ON / OFF. The rules are pure in the machine: `reduce`, `offReason`, `holdsSession` (JVM tests). `ProjectionService` only acquires the projection and hands it over (`shareStarted`) and reports its end (`shareEnded`); its screen receiver, session and OFF logic are gone, and rotation reaches the session through an Application `ComponentCallbacks`. `StatusModel.mode` is the live mode.
- **Share → accessibility.** Same session: `endShare()` releases the display and the projection, then the shot loop starts. Masks and tracker carry over, no cover. On API 35+ a screen-off ends the share in the machine (the session waits, shots paused) and screen-on starts the shots.
- **Shots.** `MaskWindowService.displayShot()` every 400 ms, fixed cadence, first 400 ms after the start; the buffer is closed in a `finally` (and if the loop is cancelled while a shot is in the air). One filtered draw scales, crops and converts to sRGB. A shot from before a rotation is dropped. The shot goes through the same `decideFrame` hold as share frames (D50, D51).
- **Probes on accessibility.** Timeout 700 ms (read live by the tracker, so a probe that spans the mode change uses the new one). A probe lifts its mask 80 ms before the next shot (`PROBE_LIFT_LEAD_MS`, calibration knob for ticket 17), and its exposure counts from the lift. D51's 200 ms still-confirm is skipped on shots: the next shot is the second frame.
- **Log.** Every new session writes an ON, so a restart into accessibility (a bind after a reboot or a dead process) is an ON after the inferred OFF and the heartbeat runs. SwitchOff in accessibility writes OFF(USER). A share ending with the switch on but the service unbound still writes OFF + the D21 notification (M6) until ticket 16. Protection now turns on when the projection is acquired, not at consent.
- **Side effect for ticket 15.** A bind with protection and the switch on resumes `ACCESSIBILITY` already (also at process start), so ticket 15 is mostly its device check.
- **Ticket 14 ponytail.** Start from accessibility closes the shot session and opens a share session: a short gap with no masks and a second ON. Ticket 14 hands the projection to the live session instead.
- **Open, for ticket 17:** on a still screen shots keep coming, so a masked tile with no CLEAR neighbour (Light, or surrounded) probes on the 2 s timer, backing off to 8 s, where screen share never probes (§3.4 "A static screen delivers no new frames"). With peek (B) that exposes nothing; on A it is a lifted mask every 8 s. Skipping identical shots would stop it but could leave a mask stuck when content changes under it.
- **Logcat.** `shot loop started` / `shot loop stopped (screen off or our screen in front)`, `<Event>: capture mode X -> Y, protecting=… screenOn=…`, `display shot shotMs=… frameMs=… cs=…` (debug), `display shot failed, error N`.
- **Device check still to do (A and B):** stop sharing from the chip with the test video playing and it stays masked; screen off/on in accessibility (logcat lines above); no probe timeout storm; rotation in accessibility keeps the D33 cover; a `FLAG_SECURE` app: shots should come back black (logged unanalyzable, mask released within one probe). If a device fails the shot instead, nothing is logged and masks freeze: report it.
