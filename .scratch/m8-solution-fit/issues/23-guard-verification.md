# 23: Guard verification (SPEC M8 V4 on A and B)

**What to build:** Nothing new. The human runs SPEC M8 V4 on both devices. Failures become bug tickets.

**Blocked by:** 21, 22

**Status:** ready-for-human

- [ ] Each guarded page closes to the PIN pad on A and B; a correct PIN returns to the page and it works
- [ ] One Intento de apagar per target; two tries within 60 s are one entry with count 2
- [ ] With an Unlock active nothing is guarded; with Appear on top revoked its page opens
- [ ] Guard delay (event → PIN pad shown) on A, median / worst, written here and into SPEC §7 item 6
- [ ] `adb uninstall dev.sophiel` still works
- [ ] JVM tests pass; `aapt dump permissions` shows no `INTERNET`
- [ ] CLAUDE.md "Current state" updated; NEXT points to deliverable 3 (episode app)
