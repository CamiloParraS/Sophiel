# 19: Capture modes verification (SPEC M8 V1-V3 on A and B)

**What to build:** Nothing new. The human runs SPEC M8 V1 (survives normal life), V2 (accessibility capture) and V3 (interruptions) on both devices, as M6 ticket 21 did. Failures become bug tickets.

**Blocked by:** 12, 14, 15, 16, 17, 18

**Status:** ready-for-agent

- [ ] V1 (a)-(d), V2 and V3 pass on A and B, or each failure has a ticket
- [ ] `aapt dump permissions` shows no `INTERNET` and no new permission
- [ ] `:safecore:test` and app JVM tests pass
- [ ] CLAUDE.md "Current state" updated; NEXT points to deliverable 2 (tamper guard)
