# 28: Escalation and rules verification (SPEC M8 V6 on A and B)

**What to build:** Nothing new. The human runs SPEC M8 V6 on both devices. Failures become bug tickets.

**Blocked by:** 26, 27

**Status:** ready-for-human

- [ ] 3 masked episodes in one app within 10 min → Home + block screen with countdown; Log "App bloqueada"; reopening shows the block screen and one "Apertura bloqueada"; Status shows the block line; the PIN on the block screen ends it
- [ ] A block survives a reboot (blocked after the first unlock until its end time)
- [ ] A rule-blocked app shows the block screen with no PIN button
- [ ] A trusted app shows no masks and logcat shows inference stopped; a split screen with one non-trusted app is still scanned
- [ ] Excluded apps are not in the Apps list
- [ ] JVM tests pass; `aapt dump permissions` shows no `INTERNET`
- [ ] CLAUDE.md "Current state" updated; NEXT points to deliverable 5
