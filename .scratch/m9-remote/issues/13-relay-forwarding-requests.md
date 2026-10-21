# 13: Relay: forwarding requests

**What to build:** SPEC M9 deliverable 2, relay slice. `POST /pairings/:id/request {body}` tags the request with a random `rid`, sends `req` down the pairing's socket, and answers with the matching `res` body. No socket: 503 at once. No reply in 10 s: 504. Not this account's or gone: 410. Bad token: 401. Over 256 KB: 413. Pending requests live in memory only; `body` is never parsed or logged.

**Blocked by:** 12

**Type:** AFK

**Status:** open

- [ ] `bun test`: relay round trip
- [ ] `bun test`: offline Child phone gets 503 at once
- [ ] `bun test`: 10 s timeout gives 504 (injectable timeout)
- [ ] `bun test`: 410 for another account's pairing; 413 over the cap
- [ ] All seven D84 checks pass (SPEC M9 V2)
