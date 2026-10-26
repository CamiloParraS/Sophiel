# 22: Deploy to Fly and the release build

**What to build:** SPEC M9 deliverable 6 (D77, D84). `server/Dockerfile` (`oven/bun`) and `server/fly.toml` (`auto_stop_machines = "off"`, a 1 GB volume mounted for the DB, internal port 3000). A `/wizard` script for the human: Fly sign-up and card, `flyctl`, `fly auth login`, `fly launch --no-deploy`, the volume, the first `fly deploy`. Set `remoteRelease`'s `RELAY_URL` to the deployed `https://<app>.fly.dev`.

**Blocked by:** 13

**Type:** HITL

**Status:** resolved (deployed 2026-10-21)

- [x] The human ran the wizard; the app URL recorded in DECISIONS
- [x] `https://<app>.fly.dev` answers a login
- [ ] `fly ssh console` + `sqlite3 .schema` shows only the three tables
- [ ] `remoteRelease` builds and pairs A and B through Fly
