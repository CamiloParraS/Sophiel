# New SPEC and four-week plan  `wayfinder:grilling`
Assignee: Camilo Parra (claimed 2026-10-01)
Blocked by: 01, 02, 03, 04
Status: CLOSED

## Resolution

Plan (~4 weeks, risk-first):
- **Week 0 gate (folded into the first days of week 1):** merge current branches into `main`; re-run M3 V1-V7 and the D21 resume-notification check on a device (D20/D21 never verified on-device). Tile work does not start until this passes.
- **Week 1:** tile pipeline (2x3 grid, CLEAR/MASKED/PROBING state machine, exact-hash lock), unit tests first. Measure real per-tile cost on both devices; 2x2 fallback if the sweep exceeds ~400 ms.
- **Week 2:** overlay (solid-block tiles, FLAG_NOT_FOCUSABLE, PIN reveal), end-to-end on hardcoded settings. Basic Status screen with the on/off switch, so something is visible early.
- **Week 3:** full parent UI (PIN, Status, wizard, Settings, Log with 7-day persistence, hidden debug menu). **Feature freeze at the end of week 3.**
- **Week 4:** hardening, on-device verification on both devices, SPEC/LIMITATIONS cleanup, demo script. Bug fixes and docs only.
- **Stretch (only with slack in week 4):** the softer "suggestive" tier.

Main verification items (both devices):
1. Balanced masks only the flagged tiles on the Test Feed.
2. A static flagged image stays masked 10 s with no flicker; releases within a few seconds after the content changes.
3. Probe exposure at most one captured frame, at most one probe per second.
4. Every action that weakens protection prompts for the PIN.
5. Log records masks, on/off (with reason) and unanalyzable events; entries older than 7 days are pruned (unit test, fake clock).
6. 10-minute soak, no stall or crash.
7. `aapt` shows no INTERNET; ordinary app screens trigger no masks (spot check).
8. **Feel (human priority):** end-to-end latency and CPU/memory/battery are measured and reported per preset. It should feel quick and light, but these are reported, not pass/fail.

Stretch verification (previous targets, reported not gated): p50 latency < 800 ms Device A / < 400 ms Device B, throughput >= 4 fps A / >= 8 fps B, formal evaluation set and UI false-positive corpus.

SPEC: keep only what is salvaged. The new SPEC contains the salvaged parts (setup, capture/permission model §4, model/preprocessing, data policy) plus the new scope, per-tile contract, milestones, and limitations. Everything else (old M4/M5/M6 text, whole-frame-only rules, §6 lessons) is removed from the live SPEC and preserved in an archive PR for reference. `CLAUDE.md` "Current state" is refreshed. Hard rules (no INTERNET, no persisted frames, Kotlin only) stay.

## Question

With the salvage list, the tile approach, the censor look and the screen list settled, what is the milestone plan for ~4 weeks, with verification items per milestone, and what exactly changes in SPEC §1, §5 and §8? Closing this ticket = the destination.
