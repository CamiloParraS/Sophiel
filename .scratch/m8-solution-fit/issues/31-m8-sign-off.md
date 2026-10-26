# 31: M8 sign-off (SPEC M8 V7, V8; freeze returns)

**What to build:** Nothing new. The human runs SPEC M8 V7 and V8 on both devices and closes M8. Failures become bug tickets.

**Blocked by:** 29, 30

**Status:** ready-for-human

- [x] V7: the dashboard's counts match the rows; "Informe" opens the share sheet with text whose counts match the Log for Hoy and 7 días
- [x] V8: `aapt dump permissions` shows no `INTERNET` and no new permission; the manifest diff since M6 is the activity, `<queries>` and the service config
- [ ] V8: `ParityTest` and every M4-M8 JVM test pass (done, D86); M5 V1 and V2 still pass on screen share (open)
- [ ] SPEC §9 M8 ticked; a D-entry closes M8 with its numbers
- [ ] CLAUDE.md "Current state": M8 done, feature freeze returns, NEXT is UI polish (separate effort), then M7
