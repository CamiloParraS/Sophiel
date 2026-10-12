# Map: M8 — Solution fit  `wayfinder:map`

## Destination

A new `docs/SPEC.md` milestone section **M8 — Solution fit** (deliverables + verification) plus its D-entries in `docs/DECISIONS.md`, buildable by an agent with no scoping decisions left. M8 makes Sophiel credible as parental control: it survives normal life (reboot, lock, no consent), is hard for the Child to turn off, tells the Parent which apps caused masks, escalates on repeated hits, and gets a report to a Parent who isn't holding the phone.

## Notes

- Feature freeze lifted for M8 only (D53). Budget: ~2 weeks M8, then ~3-4 days UI polish (separate effort), then ~3-4 days M7 (soak, LIMITATIONS, README, demo video). Due ~3 weeks from 2026-10-11.
- Audience: a teacher who grades the UI and how well it solves the problem.
- **Keep C1 (no `INTERNET`).** Everything else store/policy-shaped is allowed: boot receiver, `QUERY_ALL_PACKAGES`, accessibility events and window reads, `PACKAGE_USAGE_STATS`. Not allowed: Device Owner (needs an account-free phone).
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
- [Capture backend, switching rules and start on boot](issues/03-capture-backend-and-boot.md): hybrid behind one switch "Seguir protegiendo siempre" (default on); Protection stays on until a PIN Stop and capture mode changes underneath; no boot receiver or FGS in accessibility mode; probes timed to shots; peek default on; new Log entries "Compartir pantalla detenido" and "Sin protección".
- [Tamper guard: what is guarded, how, and what gets logged](issues/05-tamper-guard.md): accessibility-only guard, armed while a PIN exists and no Unlock; covers Sophiel's accessibility toggle, Appear on top, App info, uninstall dialog, Active apps; opens the existing PIN pad, an Unlock from it survives leaving the app (amends D38); Tamper attempt Log entry (time + target, 60 s merge); no Device Admin.
- [Does screen share survive screen-off on A?](issues/10-screen-share-through-screen-off-on-a.md): yes; below API 35 screen-off pauses frames instead of ending the share (amends D18), so A stays on screen share all day after one consent; B keeps the teardown.
- [App attribution in the Log](issues/06-app-attribution-in-the-log.md): each episode (and unanalyzable stretch) names the app under its strongest mask at the start, package in `log.csv`, label at read time; blank without the service; no split on app change; `<queries>` launcher intent; rows show the app, summary names the top app; full breakdown left to 08.
- [Escalation and per-app rules](issues/07-escalation-and-app-rules.md): fixed 3 masked episodes in 10 min blocks the app 15 min (switch, default on); rule-blocked / trusted apps per app in a Settings "Apps" screen; accessibility service sends Home + block screen while Protection is on; PIN lifts escalation blocks only; trusted apps stop inference; Sophiel, Settings, launcher, dialer excluded; two new Log kinds; PiP and notifications are limitations.
- [Log upgrades and the shared report](issues/08-log-upgrades-and-shared-report.md): Log = bar chart + per-app list on top, day-grouped rows below; PIN-gated text-only report (Hoy / 7 días) via the share sheet; Status shows active escalation blocks. Prototype on `proto/m8-log-report`.
- [Write M8 into SPEC and DECISIONS](issues/09-write-m8-into-spec.md): SPEC M8 section (5 deliverables in priority order, V1-V8), new §4.7 capture modes, §1/§4/§7/§9 updated, M7 covers both modes and the M8 demo; D54-D60. Destination reached, pending human review.

## Not yet specified

- **Schedules** (stretch goal, D60): stricter protection at night, time windows; maybe a reminder notification for the daily / weekly report. Plan only with slack once M8 V1-V8 pass.

## Out of scope

- Sending messages by itself (SMS, push, email): needs a second SIM phone to test, or `INTERNET` (C1).
- Device Owner provisioning: needs an account-free phone; not realistic for a demo.
- Dropping C1 / any backend or accounts.
- UI polish of parent screens: separate effort after M8.
- Uninstall protection via Device Admin (blocks force stop, clear storage, every uninstall path): future work / stretch goal, human call in [Tamper guard](issues/05-tamper-guard.md); the guard covers App info and the uninstall dialog instead, with a tap race.
