# 05: Tamper guard: what is guarded, how, and what gets logged

Type: grilling
Status: open
Blocked by: 04

## Question

Decided at charting: settings guard (accessibility) + Device Admin + start on boot; not Device Owner. Each blocked attempt is logged. Decide the details (gap 2: hard to turn off):

- Which targets the guard covers (from ticket 04's table), and which are left as limitations.
- What the Child sees when blocked: just sent Home, or a "Protegido por Sophiel" screen with a PIN entry for the Parent?
- How the guard fits D38 "one door": an active unlock disarms the guard for its 2 min; anything else?
- Device Admin: enrolled in the wizard as a new step (required or skippable?), and the deactivation message.
- New **Tamper attempt** Log kind: fields (time, target, app), episode-like dedupe (one per attempt burst?).
- Wizard and Status changes: new "Needs attention" items (admin off, guard unavailable).
- Debug escape hatch so development (adb install over, uninstall) isn't blocked: debug builds only? Debug-menu switch?
