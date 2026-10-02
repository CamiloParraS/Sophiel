# Map: Rescope Sophiel into a parental-control app  `wayfinder:map`

## Destination

A rewritten `docs/SPEC.md` that an agent session can build from with no scoping decisions left: a UI-centred parental-control app that censors only the flagged areas of the live screen, in about 4 weeks.

## Notes

- Audience: a teacher who grades mostly on the UI and how well it solves the problem. Not on benchmark rigour.
- Keep: LiteRT model + preprocessing + score formula (D12), MediaProjection capture pipeline (M3, verified on both devices), no-INTERNET claim, no-persisted-frames rule, Kotlin only, Compose UI.
- Change: overlay (whole-frame blur becomes per-region masks), app UI and flows, scope in SPEC §1.
- Parental control = PIN-protected settings + parent-set sensitivity + local blocked-events log. No tamper resistance, no remote alerts.
- Eval/benchmark code is debug-only and may be deleted later.
- Devices: develop on Device A, demo on Device B.
- Vocabulary lives in `CONTEXT.md`.
- Prior art (marketing claims only, unverified, closed source):
  - Beta Blocker (itch.io, Android 14+): the page says full-screen censor, but the human watched its demo video and it censors specific zones in real time. How it is built is not published anywhere. Treat as proof that regional real-time masking is feasible, nothing more. Single-app mode ("click ONE APP MODE or it runs glitchy", which fits the feedback-loop finding). Four presets (Low/Medium/High/Ultra). Customizable censor styles (colours, text, animation). Session statistics. Extras we skip: safe browser, achievements, 10+ languages.
  - safescreen-ai: already studied, see old SPEC §6 (whole-frame blur, skin gate, hysteresis, no-INTERNET).
  - Our differentiators: regional masking, parent PIN and sensitivity, Android 13 support (Device A).
  - Feed into ticket 03 (censor styles) and ticket 04 (stats as the blocked-events log).
- Tracker: local markdown. Tickets in `docs/wayfinder/tickets/`. Blocking via a `Blocked by:` line. Claim by setting `Assignee:`.

## Decisions so far

- [Salvage inventory](tickets/01-salvage-inventory.md): keep model + capture stack; adapt the pipeline, cache, policy and UI to per-tile; cut M5 bench/eval and child tap-to-reveal; SPEC §1.3/§3.4/M4/M5 must be rewritten.
- [Tile-grid masking without a feedback loop](tickets/02-tile-feedback-loop.md): per-tile CLEAR/MASKED/PROBING state machine holding the last verdict; release on hash change or ~2 s TTL; 2x3 grid (2x2 fallback), cost estimated not measured; probes briefly expose pixels (goes to the censor-treatment ticket).
- [Censor treatment and reveal flow](tickets/03-censor-treatment.md): solid block with lock + label (variant C), PIN reveal, probe made rare by an exact-hash lock; one-frame exposure on the first probe still needs on-device measurement.
- [Parent flow and screens](tickets/04-parent-flow.md): Status/Setup/PIN/Settings/Log screens plus hidden debug menu; PIN gates anything that weakens protection, no recovery; Strict/Normal/Relaxed + Light/Balanced; no notification Stop; 7-day local log of masks, on/off and unanalyzable events.
- [New SPEC and four-week plan](tickets/05-new-spec.md): risk-first plan (week 0 gate, tiles, overlay, parent UI, hardening), freeze end of week 3, one stretch (suggestive tier), main verification list plus a "feels quick and light" measurement, old targets as stretch; the new SPEC keeps only salvaged content and the old one is archived in a PR.

## Open tickets

None. All tickets are closed; the destination is reached. Handoff: write the new `docs/SPEC.md` from the decisions below.

## Not yet specified

- Per-tile threshold and hysteresis tuning, once the tile approach is chosen.
- Probe and hash-lock behaviour: measure on-device (exposure length, how often probes fire) and fold into the new SPEC as a verification item.
- Stretch: a softer "suggestive" tier (pixelate/blur at mid scores) alongside the solid block; needs a second threshold in the Parent settings (`PolicyEngine` already has a SUGGESTIVE cutoff, D11).
- How the user picks Light vs Balanced (manual only, or a suggested default per device).
- Onboarding and permission-consent UX wording and flow.
- What happens to the debug pill, Test Feed and Profiler tooling (keep as a hidden debug menu?).
- Demo script and report outline.

## Out of scope

- Precise (bounding-box detector) preset: closed by the human, too risky for one month.
- Remote parent alerts, accounts, backend: needs INTERNET; Future Work.
- Tamper resistance (Device Admin, work profile): documented as a limitation.
- Formal ROC / 300-image evaluation set: debug-only, not a deliverable.
- Everything already in old SPEC §1.3 (deepfake, OCR, CLIP filters, NPU, Play Store).
