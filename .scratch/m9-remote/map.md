# Map: M9 — Remote  `wayfinder:map`

## Destination

A new `docs/SPEC.md` milestone section **M9 — Remote** (deliverables + verification) plus its D-entries, buildable by an agent with no scoping decisions left. M9 lets a Parent, from their own phone, log in and, per paired Child phone: fetch the newest Log entries, set apps Normal / Bloqueada / De confianza, and Start or Stop protection, through a server that can read nothing and keeps no logs.

## Notes

- **C1 reopened on purpose** (D76, a professor's requirement). `INTERNET` is allowed only in the new `remote` flavor; the `offline` flavor keeps C1 and its `aapt` check exactly as today.
- Settled at charting (D76), do not relitigate: one APK, role chosen on first launch (Parent phone / Child phone); the Child phone never logs in (pairing replaces it), the Parent phone does nothing without a login; pure relay (server stores Parent accounts and pairings only, passes end-to-end encrypted messages through, never writes them; offline Child phone = request fails); Pairing by QR carrying the key, key never touches the server; one account, many named Child phones; Child phone Status shows "Vinculado"; either side unpairs, the Child phone only behind the PIN door, and unpairing deletes the key.
- Remote capabilities, exactly: Log fetch, app rules (D58's three states), Start, Stop. Remote Start only works in accessibility capture (screen share needs a tap on the Child phone).
- Stack default: Bun + Elysia + `bun:sqlite`, WebSockets for the relay; host chosen by research (always-on, no sleeping).
- Placement: M9 after M8 sign-off and UI polish, before M7, so M7 verifies Remote too. Budget ~1 week.
- Stretch goal: a **mailbox** (encrypted commands wait for an offline Child phone; last encrypted Log snapshot kept). Only with slack.
- Grilling tickets: call the Skill tool for "grilling" and "domain-modeling". Vocabulary in `CONTEXT.md` (Parent phone, Child phone, Pairing, Remote); decisions in `docs/DECISIONS.md`.
- Hard rules 3 and 4 still hold: no frame ever leaves the phone or touches disk; only the scalar Log crosses the wire.
- Tracker conventions: `docs/agents/issue-tracker.md` (Wayfinding operations).
- **Build tickets** (after the map): 10-23 in `issues/`, in SPEC M9's build order, each with a `Blocked by:` line. 10 and 11 can start at once; 23 is the sign-off.

## Decisions so far

<!-- one line per closed ticket -->

- [Where to host a Bun + Elysia WebSocket relay](issues/01-hosting-for-the-relay.md): Fly.io, one always-on 256 MB Machine + 1 GB volume, ~$2.34/month; `idleTimeout` 20-30 s against the ~60 s proxy cut; Elysia 1.4.x. Railway Hobby ($5) is the fallback.
- [Keeping the Child phone reachable in the background](issues/02-keeping-the-child-phone-reachable.md): WebSocket in the process the accessibility service keeps alive (network survives Doze per AOSP), plus a battery-exemption wizard step; server pings, 5-min alarm reconnect; OkHttp 5; no FCM, no extra FGS. Unreachable while the service is off.
- [Protocol, accounts and end-to-end encryption](issues/04-protocol-accounts-and-encryption.md): email login, token until logout; the Child phone never logs in, a QR (claim code + AES-256 key) pairs it; GCM with replay check; Parent POSTs, server forwards down the Child socket (10 s); whole 7-day Log in memory only; server keeps only account, session and pairing rows.
- [Showing and scanning the Pairing QR](issues/03-qr-pairing-libraries.md): ZXing `core` to draw it, Google code scanner to read it (no CAMERA permission), both `remote`-only; the scanner adds `INTERNET` and Google usage metrics, which the `remote` privacy text must disclose.
- [The Parent phone's screens](issues/05-remote-screens.md): variant B, one state card per Child phone on the home with Start/Stop on it, tabs for Historial and Apps; role choice on first launch, login/sign-up without recovery, scan then name; apps as letter tiles.
- [What Remote changes on the Child phone](issues/06-child-phone-side.md): pairing screen as an Ajustes row behind the door; remote Stop = PIN Stop (OFF reason `REMOTE`); remote Start rejects with service off or "Seguir protegiendo siempre" off; remote rules = local Apps change, last write wins; Log adds only Vinculado/Desvinculado; battery exemption asked before the QR, a Recomendado item if missing.
- [Flavors and the claim that replaces C1](issues/07-flavors-and-the-new-claim.md): `offline`/`remote` flavors, same app id, Remote code in `src/remote`, default `installRemoteDebug`; `remote` adds exactly INTERNET, ACCESS_NETWORK_STATE, battery exemption (aapt diff checks it); C1 and the privacy claim reworded, scanner metrics disclosed; with accessibility off the Child phone still answers Log/Stop/rules if its process lives; seven new §7 items.
- [Deploying and running the relay](issues/09-relay-deploy-and-operations.md): `server/` in this repo (rule 5 = app only); Fly daily snapshots; in-memory login/claim-code limits; seven `bun test` checks incl. a schema check; wizard for first deploy, then hand `fly deploy`; debug talks to local Bun via `adb reverse`, release to Fly.
- [Write M9 into SPEC and DECISIONS](issues/08-write-m9-into-spec.md): SPEC §5 M9 (6 deliverables, V1-V9) plus C1/C5/C6, §1-§4.1, M7, §7 27-32, §8, §9; D77-D85; CLAUDE.md rules 1, 5, 6. Destination reached; pending human review.

## Not yet specified

- **Mailbox** stretch goal: what waits, for how long, how it stays unreadable. Only once M9's V-items pass.

## Out of scope

- A web dashboard or a separate Parent APK: the Parent phone is the same APK (D76).
- Remote sensitivity, preset, lifting an escalation block, changing the PIN: not in the four capabilities (D76).
- Any frame, thumbnail or screen content leaving the Child phone (hard rule 3).
- Server-side storage of Log entries or commands (pure relay, D76), except the mailbox stretch.
- Account recovery and password change (D79; SPEC §1.3). Revisit only if the professor asks, as a fresh effort.
