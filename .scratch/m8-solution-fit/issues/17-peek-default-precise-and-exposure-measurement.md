# 17: Peek default on, Precise on accessibility, exposure measurement

**What to build:** Peek under mask defaults to on where it works (API 34+, service bound). Precise is confirmed to work on accessibility mode on B. Probe exposure on accessibility mode is measured on A (probes) and B (peeks) with the test video and reported against ticket 02 (A 436 / 519 / 565 ms untimed). Measurement only; no new feature.

**Blocked by:** 13

**Status:** ready-for-agent

- [x] Peek default on (amends D34); unavailable cases stay off (2026-10-14: `peekUnderMask` default true; `peekMode()` still requires `canShoot()`, the Settings toggle shows off when it cannot shoot)
- [ ] Precise masks boxes on accessibility on B
- [ ] Probe exposure median / p90 / worst reported for A and B, with no probe-timeout storm (SPEC V2)
- [ ] Logcat shows the shot loop stopping at screen-off and restarting at screen-on
- [ ] Numbers recorded in the ticket and CLAUDE.md "Current state"
