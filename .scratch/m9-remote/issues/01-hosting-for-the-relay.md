# 01: Where to host a Bun + Elysia WebSocket relay

Type: research
Status: resolved
Blocked by: none
Research: branch `research/m9-hosting` (a20c625), `.scratch/m9-remote/research/01-hosting.md`

## Question

Which host runs a small Bun + Elysia server with long-lived WebSockets and a `bun:sqlite` file, always on, for a student budget (free or a few dollars a month)? Primary sources only (provider docs and pricing pages, current as of 2026-10).

- Candidates at least: Fly.io, Railway, Render, Koyeb, a cheap VPS (Hetzner, Oracle free tier). Add any better fit.
- Per host: does the free / cheapest tier sleep or scale to zero (that drops the Child phone's socket)? WebSocket idle timeout and max connection length? Persistent disk for a SQLite file? TLS (`wss://`) included? Bun supported natively or via Dockerfile? Price.
- Elysia facts: current version, native WebSocket API (`.ws()`), ping/keepalive support, and whether it runs unchanged on the chosen host.
- Recommend one host and say what the human must do by hand (sign-up, card, CLI).

## Answer

**Fly.io**: one `shared-cpu-1x` 256 MB Machine + 1 GB volume, `auto_stop_machines = "off"`, ~$2.34/month (no plan fee; shared IPv4 and `*.fly.dev` TLS free). Fly's proxy drops a connection after ~60 s with no traffic (forum posts, unverified), so set Bun/Elysia `idleTimeout` to 20-30 s; Bun auto-pings. Runner-up: Railway Hobby, flat $5/month, docs say WebSockets are exempt from time limits.
Ruled out: Render (free sleeps after 15 min, no disk), Koyeb (free scales to zero, volumes preview), Hetzner (€5.99, unavailable, self-managed TLS), Oracle free (reclaims idle instances).
Elysia: pin 1.4.x (1.4.30; 2.0 only rc). `.ws()` is built in on Bun's uWebSockets; runs in an `oven/bun` image.
By hand: fly.io sign-up and a card after the trial, `flyctl` + `fly auth login`, Dockerfile + `fly.toml` (steps in the research doc).
