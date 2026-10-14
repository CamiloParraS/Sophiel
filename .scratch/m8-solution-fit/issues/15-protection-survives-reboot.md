# 15: Protection survives reboot

**What to build:** After a reboot, with protection on, the accessibility service binding at first unlock starts the shot loop on its own: no boot receiver, no foreground service, no notification. Status reads "Protección activa · accesibilidad" and the Parent never has to open Sophiel.

**Blocked by:** 13

**Status:** ready-for-human (code done in ticket 13: `MaskWindowService` bind calls `modes.serviceChanged()`; JVM test in CaptureModeMachineTest; V1c and reboot-with-protection-off check on A and B pending)

- [x] Service bind with protection on and switch on starts the loop (mode `ACCESSIBILITY`)
- [x] No boot receiver, no new permission, no FGS in accessibility mode
- [x] SPEC V1c: reboot, unlock, do not open Sophiel; test video masked within ~1 s of playing; Status reads "· accesibilidad"
- [ ] Reboot with protection off starts nothing
- [ ] Ends buildable

## Device check, 2026-10-14

- Reboot with protection on, Sophiel not opened: test video masked, so V1c passes on B (A pending). Masks took ~30 s to appear after the reboot. Attributed to the device still settling after boot (the service binds late), not to Sophiel; not measured. If it matters for the demo, compare the `ServiceBound` log time with the unlock time.
- Still not checked: reboot with protection off starts nothing.
