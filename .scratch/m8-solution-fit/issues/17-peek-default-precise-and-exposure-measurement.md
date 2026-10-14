# 17: Peek default on, Precise on accessibility, exposure measurement

**What to build:** Peek under mask defaults to on where it works (API 34+, service bound). Precise is confirmed to work on accessibility mode on B. Probe exposure on accessibility mode is measured on A (probes) and B (peeks) with the test video and reported against ticket 02 (A 436 / 519 / 565 ms untimed). Measurement only; no new feature.

**Blocked by:** 13

**Status:** ready-for-agent

- [x] Peek default on (amends D34); unavailable cases stay off (2026-10-14: `peekUnderMask` default true; `peekMode()` still requires `canShoot()`, the Settings toggle shows off when it cannot shoot)
- [x] Precise masks boxes on accessibility on B
- [ ] Probe exposure median / p90 / worst reported for A and B, with no probe-timeout storm (SPEC V2)
- [x] Logcat shows the shot loop stopping at screen-off and restarting at screen-on
- [ ] Numbers recorded in the ticket and CLAUDE.md "Current state"

## Notes (2026-10-14, device B, Normal care, test video)

- Precise on accessibility: run-B.log, 52 box shots, totalMs median 136 / p90 186 / worst 238, masks 0-3 per shot. Boxes are drawn.
- Probe path with peek still off (setting not turned on): exposureMs n=54, median 251 / p90 315 / worst 435 ms; 30 CLEAR, 24 MASKED.
- Peek on (run-B2.log): peek verdict ms n=64, median 189 / p90 440 / worst 555 (mask stays up, not exposure); window shotMs n=61, median 77 / p90 102 / worst 116. No probes (exposureMs none).
- Screen-off: `shot loop stopped (screen off...)`, then `shot loop started`; capture mode ACCESSIBILITY -> NONE -> ACCESSIBILITY.
- Still to do: device A probe exposure (median / p90 / worst) vs ticket 02 (436 / 519 / 565), then CLAUDE.md "Current state".
