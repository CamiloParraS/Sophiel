# 09: Write M8 into SPEC and DECISIONS

Type: task
Status: resolved
Blocked by: 03, 05, 06, 07, 08, 10

## Question

The destination. Assemble the resolved tickets into `docs/SPEC.md`: §1.2 scope additions, §1.3 removals (tamper resistance moves in as the accessibility guard, minus Device Owner and Device Admin), §3/§4 architecture and permission changes (manifest diff, still no `INTERNET`), a **M8 — Solution fit** milestone section with deliverables and V-items for A and B, §7 limitations rewritten, §9 checklist. Add the D-entries (D54+). Graduate the remaining fog (verification, limitations, pitch) here or into tickets first. Update `CLAUDE.md` current state. AFK, reviewed by the human.

## Answer

Written 2026-10-12, AFK; for human review.

- **`docs/DECISIONS.md`:** D54 capture modes (ticket 03), D55 screen share through screen-off below API 35 (ticket 10, amends D18), D56 tamper guard (tickets 04-05, amends D38), D57 episode app (ticket 06, amends D39/D32), D58 escalation and rules (ticket 07), D59 Log dashboard, report, Status blocks (ticket 08), D60 this write-up.
- **`docs/SPEC.md`:** §1.1/§1.2/§1.3 scope; §2 `compileSdk`/`targetSdk` 37 (as in the build file); §3.2 layout; §3.4 probe timeout per capture mode; §4.1 manifest diff (block activity, `<queries>`, service config; no new permission); §4.2 new platform facts; new **§4.7 Protection and capture mode**; **M8 — Solution fit** section (5 deliverables in priority order, V1-V8 on A and B); M7 now covers both capture modes and the M8 demo story; §7 items 5, 6, 7, 10, 17 rewritten, 23-26 added; §8 two adb lines; §9 checklist.
- **Fog graduated here:** verification → M8 V1-V8; limitations → §7; battery/feel → M7 V3 per capture mode; pitch → M7 runbook line.
- **Schedules (D60):** an M8 stretch goal (human, on review), unspecified; plan before building.
- **Small calls made while writing** (confirmed by the human on review): the guard's PIN-pad line "Este ajuste está protegido"; `BlockActivity` lives in `ui/`; `isAccessibilityTool="true"` only if B's "Stop Sophiel?" page is hidden from the service.
