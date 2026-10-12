# Map: M8 — Solution fit  `wayfinder:map`

## Destination

A new `docs/SPEC.md` milestone section **M8 — Solution fit** (deliverables + verification) plus its D-entries in `docs/DECISIONS.md`, buildable by an agent with no scoping decisions left. M8 makes Sophiel credible as parental control: it survives normal life (reboot, lock, no consent), is hard for the Child to turn off, tells the Parent which apps caused masks, escalates on repeated hits, and gets a report to a Parent who isn't holding the phone.

## Notes

- Feature freeze lifted for M8 only (D53). Budget: ~2 weeks M8, then ~3-4 days UI polish (separate effort), then ~3-4 days M7 (soak, LIMITATIONS, README, demo video). Due ~3 weeks from 2026-10-11.
- Audience: a teacher who grades the UI and how well it solves the problem.
- **Keep C1 (no `INTERNET`).** Everything else store/policy-shaped is allowed: Device Admin, boot receiver, `QUERY_ALL_PACKAGES`, accessibility events and window reads, `PACKAGE_USAGE_STATS`. Not allowed: Device Owner (needs an account-free phone).
- Only one SIM-capable phone (Device B): nothing that sends messages by itself. Data leaves only via the Parent tapping Share (Android share sheet; the receiving app sends it).
- Priority order of the gaps: (1) survive normal life, (2) hard to turn off, (3) Parent learns which app, (4) escalation + per-app rules.
- Grilling tickets: call the Skill tool for "grilling" and "domain-modeling". Vocabulary in `CONTEXT.md`; decisions in `docs/DECISIONS.md` (do not relitigate D1-D52 without asking; D32 and D39 are being amended on purpose).
- Device A (API 33) is the budget/primary device; every capture decision must work there.
- Tracker conventions: `docs/agents/issue-tracker.md` (Wayfinding operations).

## Decisions so far

<!-- one line per closed ticket -->

- [Can whole-display accessibility screenshots replace or back up MediaProjection?](issues/01-accessibility-screenshot-capture-facts.md): yes on A and B with no consent and survives reboot, but ~2.5 fps, our masks show in the shot, probe exposure ~350-450 ms on A (estimate); favours hybrid.
- [Accessibility facts for the settings guard, Device Admin and app attribution](issues/04-accessibility-guard-and-attribution-facts.md): Device Admin blocks force-stop, clear-storage and every uninstall path (AOSP; One UI unverified); the guard covers deactivate, accessibility, overlay and share-stop pages by package + text, with a tap race; no boot receiver needed (service rebinds at first unlock, MediaProjection can't auto-start); attribute masks to the topmost app window under the tile.
- [Measure accessibility-screenshot capture on A and B](issues/02-measure-screenshot-capture-on-a-and-b.md): works with no consent and comes back after a reboot on both; 2.5 shots/s; the probe timeout must exceed the shot interval (300 ms flickered and stuck, 700 ms ok); B with peeks hides almost everything, A exposes ~0.45 s per probe (7x MediaProjection).

## Not yet specified

- **Schedules** (stricter at night, time windows): in scope, deferred behind escalation and per-app rules; revisit if M8 has slack.
- **M8 verification items** for each feature (how the human proves it on A and B), and how they fold into M7's soak.
- **LIMITATIONS changes**: what stays bypassable after the guard (safe mode, factory reset, adb, Quick Settings "Active apps" stop if the guard can't see it), and §7 items 5, 6, 14, 22 rewritten.
- **Battery and feel impact** of trusted apps skipping scanning and of the screenshot backend (M7 V3 numbers per backend?).
- **Pitch/README story**: how "survives reboot, can't be uninstalled, report to Parent" changes the demo runbook.

## Out of scope

- Sending messages by itself (SMS, push, email): needs a second SIM phone to test, or `INTERNET` (C1).
- Device Owner provisioning: needs an account-free phone; not realistic for a demo.
- Dropping C1 / any backend or accounts.
- UI polish of parent screens: separate effort after M8.
