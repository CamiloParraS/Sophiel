# 23: Guard verification (SPEC M8 V4 on A and B)

**What to build:** Nothing new. The human runs SPEC M8 V4 on both devices. Failures become bug tickets.

**Blocked by:** 21, 22

**Status:** done (2026-10-15)

- [x] Each guarded page closes to the PIN pad on A and B; a correct PIN returns to the page and it works
- [x] One Intento de apagar per target; two tries within 60 s are one entry with count 2
- [x] With an Unlock active nothing is guarded; with Appear on top revoked its page opens
- [ ] Guard delay (event → PIN pad shown) on A, median / worst, written here and into SPEC §7 item 6
- [x] `adb uninstall dev.sophiel` still works
- [x] JVM tests pass; `aapt dump permissions` shows no `INTERNET`
- [x] CLAUDE.md "Current state" updated; NEXT points to deliverable 3 (episode app)

## Notes (agent, 2026-10-15)

Marked done by the human. No guard-delay numbers were handed to the agent: SPEC §7 item 6 still reads "guard delay measured on A" without them (ticket 21 noted "pretty much instant").
