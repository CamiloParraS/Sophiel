# 09: Deploying and running the relay

Type: grilling
Status: resolved
Blocked by: none

## Question

The host (Fly.io, ticket 01) and what the server holds (ticket 04) are fixed. Decide how the relay is run.

- Repo layout: the server in this repo (`server/`, Bun + Elysia 1.4.x) or its own repo; does hard rule 5 (Kotlin only) cover only the app?
- Backups of the accounts volume: Fly's volume snapshots as they come, or none (losing it means everyone re-registers and re-pairs)?
- Login abuse: rate limit per IP / per email on login and sign-up, how strict.
- Server tests: which checks the build must leave (e.g. a relay round trip, a rejected replay, a deleted account's Child phone cleanup).
- How the human deploys: a `/wizard` for Fly sign-up, `fly launch`, volume, `idleTimeout` 20-30 s; who holds the account.

## Answer

Human decisions, 2026-10-21 (two grilling rounds).

- **Repo:** the relay lives in this repo under `server/` (Bun + Elysia 1.4.x, TypeScript). Hard rule 5 reworded: "Kotlin only in the app; the relay in `server/` is TypeScript on Bun."
- **Backups:** Fly's automatic daily volume snapshots, default retention; nothing else. Losing the volume means re-sign-up and re-pair, no Log is lost.
- **Abuse limits (memory only, reset on restart):** 5 failed logins per email in 15 min locks that email for 15 min; 20 login/sign-up attempts per IP per minute; a claim code dies after 5 wrong guesses. Nothing about IPs touches disk.
- **Server checks (`bun test`, M9 V-items):** relay round trip; offline Child phone gets "sin conexión" at once; 10 s timeout; unpair from each side; deleted account's device token rejected and cleaned up; login lockout; schema check that SQLite holds only the three tables and columns of ticket 04. Replay rejection is a Kotlin test on the Child phone.
- **Deploy:** the human holds the Fly account. A `/wizard` script covers sign-up, `flyctl`, `fly launch --no-deploy`, the 1 GB volume and the first `fly deploy`; `fly.toml`, Dockerfile and `idleTimeout` 25 s live in `server/`. Later deploys are a by-hand `fly deploy`, no CI.
- **Server URL per build type:** `remoteRelease` uses `https://<app>.fly.dev` (fixed in the build). `remoteDebug` uses `http://localhost:3000` through `adb reverse tcp:3000 tcp:3000` to a local `bun run`, with a debug-only network security config allowing cleartext to `localhost` only. The real Fly server is verified in M9 with a release build.
