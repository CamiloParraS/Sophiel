# 13: Accessibility shot loop and automatic fallback

**What to build:** `MaskWindowService` takes a display shot every 400 ms (sRGB, closed at once) and feeds the unchanged frame path. When the screen share ends with protection on and the switch on, the capture mode becomes `ACCESSIBILITY`: masks and tracker carry over, no cover, first shot ~0.4 s later. The loop stops at screen-off and restarts at screen-on. Probe timeout is per mode: 300 ms screen share, 700 ms accessibility (§3.4). Demo: stop sharing from the chip and the test video stays masked.

**Blocked by:** 11

**Status:** ready-for-agent

- [ ] Shot loop at 400 ms; Display P3 shots converted to sRGB on copy; each shot closed at once (no stall)
- [ ] Share ending (chip, system, revoked) with protection and switch on switches to `ACCESSIBILITY` with no cover and no mask loss
- [ ] Loop stops at screen-off and restarts at screen-on (visible in logcat)
- [ ] Probe timeout 300 ms on screen share, 700 ms on accessibility; no timeout storm
- [ ] Switch off or service unbound: no `ACCESSIBILITY` mode. Turning the switch off while in `ACCESSIBILITY` makes the machine clear `protecting`: persist it in `ProtectionStore` (ticket 11 note)
- [ ] Rotation keeps the D33 cover path
- [ ] A `FLAG_SECURE` app is logged unanalyzable and its mask releases within one probe
- [ ] Ends buildable
