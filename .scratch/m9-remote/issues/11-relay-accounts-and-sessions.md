# 11: Relay: accounts and sessions

**What to build:** SPEC M9 deliverable 2, first slice (D79, D84). Create `server/`: Bun + Elysia 1.4.x, TypeScript, `bun:sqlite`, the three-table schema exactly as SPEC, `POST /signup`, `POST /login`, `POST /logout`, `DELETE /account`. Tokens 256-bit random base64url, only SHA-256 stored; `Bun.password` argon2id, 8+ characters; one error text for wrong email or password. Abuse limits in memory: 5 failed logins per email in 15 min lock it 15 min; 20 attempts per IP per minute. No body or id logging. `bun run dev` serves on :3000.

**Blocked by:** none

**Type:** AFK

**Status:** open

- [ ] `bun test`: login lockout; schema check (only the three tables and their columns)
- [ ] `bun test`: sign-up, login, logout (token rejected after), delete account (sessions and pairings gone)
- [ ] The DB path comes from an env var (the Fly volume later, a temp file in tests)
- [ ] No request bodies, tokens, emails or ids in logs
