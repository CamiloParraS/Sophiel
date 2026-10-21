# 12: Relay: pairing and the Child socket

**What to build:** SPEC M9 deliverable 2, pairing slice. `GET /child` WebSocket with `hello` (device token) and `claim` frames; claim codes in memory (10 min, dead after 5 wrong guesses); `POST /pairings` binds a claim code to the account and sends `paired` with a fresh device token; `DELETE /pairings/:id` sends `unpaired`; the Child's `unpair` frame deletes the row. A rejected device token gets `unpaired` and a close. `idleTimeout` 25 s; the server pings every socket. One socket per pairing (a new `hello` replaces the old one).

**Blocked by:** 11

**Type:** AFK

**Status:** resolved

- [ ] `bun test`: claim, paired, then `hello` accepted
- [ ] `bun test`: unpair from the Parent side (row gone, `unpaired` sent) and from the Child side (row gone)
- [ ] `bun test`: a deleted account's device token gets `unpaired` on `hello`
- [ ] `bun test`: an expired or over-guessed claim code is 404

## Answer

Built in `server/` (commits 109f1c7, 7f91ee6); `bun test` 12 pass. Claim-code guess limit is per Parent account, not per code.
