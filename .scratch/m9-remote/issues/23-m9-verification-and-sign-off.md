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

## Results (2026-10-26, human on A + B, `remoteRelease` through `sophiel-relay.fly.dev`)

- **V4, V5:** pass (human report). **V6:** Stop after 30 min screen-off passes; airplane mode passes. A's logcat (11:47-12:28): no exceptions; the 5-min alarm fired every ~6 min, each forcing a reconnect that reopened in ~1 s (one 31 s, 12:09, Doze). `force-idle`: works, but a `log` fetch was slow to respond.
- **V6 reply time** (B's logcat, `remote: <TYPE> reply N B, M ms`, request sent to reply opened, 35 requests, screen on): log median 560 ms / p90 1854 / worst 3250 (n=20); start+stop median 456 / p90 1262 / worst 1799 (n=15). Not B tap -> banner, so add a few hundred ms of UI.
- **V3 size:** `log` replies were 2.6-2.8 KB wire (grows ~10 B per fetch: the test Log is short). That is NOT a real 7-day Log; the cap check still rests on the JVM worst case (114,056 B vs 262,144). Re-measure after A has a week of use, or note the gap in DECISIONS.
- **V8:** pass. `sqlite3 /data/sophiel.db .schema` shows exactly accounts, sessions, pairings. `fly logs` over a full link / log fetch / start / stop run holds only Fly's own SSH-login line: the relay logged no bodies, ids or emails.
- **V9:** `ParityTest` passes (`:safecore:test`, `:app:testRemoteDebugUnitTest`). M8 V1(a)/V6 on `remote`: the human reports they hold in normal use, not run step by step. The unpaired-Child no-socket logcat was not run. Gap accepted at sign-off.
- **V7:** pass (human, all cases incl. each side offline and Borrar cuenta). V7-A.log (886 lines) and V7-B.log: no exceptions; A logs `relay heard unpair` and `unpaired by the relay` on reconnect. Offline was Wi-Fi/data off via airplane mode, logs dumped with `logcat -d` after.
