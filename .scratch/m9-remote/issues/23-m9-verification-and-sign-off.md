# 23: M9 verification and sign-off

**What to build:** Nothing new. The human runs SPEC M9 V1-V9 on A and B. Failures become bug tickets. Then sign-off: the feature freeze returns.

**Blocked by:** 17, 18, 19, 20, 21, 22

**Type:** HITL

**Status:** open

- [ ] V1 builds and claims; V2 `bun test`; V3 envelope tests and the measured Log size
- [ ] V4 pairing; V5 the four capabilities; V6 reachability (30 min screen-off, `force-idle`, reply time median/worst of 10 reported); V7 unpair
- [ ] V8 real server: release build through Fly, schema and `fly logs` clean
- [ ] V9 still true: `ParityTest`; M8 V1(a) and V6 spot-checked on `remote`
- [ ] SPEC §9 M9 ticked; CLAUDE.md "Current state" updated; NEXT points to M7
