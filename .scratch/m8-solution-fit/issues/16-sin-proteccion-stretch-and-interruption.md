# 16: "Sin protección" stretch and interruption

**What to build:** When protection is on, the screen on and nothing is captured, the Parent is told. A **Sin protección** Log entry records start, length and reason (accessibility off / app stopped / restarted). Status turns red "Protección interrumpida" with the reason while it lasts. Turning the service off with protection on posts "Protección interrumpida · toca para reactivar" (opens Status). The D39 last-alive inference now writes this entry; boot-to-first-unlock is not a gap.

**Blocked by:** 13, 15

**Status:** ready-for-human (built 2026-10-14; V3 checked on B, A pending)

- [x] Stretch detection is pure logic, JVM-tested (service off, force-stop, restart, boot-to-unlock not a gap)
- [x] Log kind **Sin protección** with start, length, reason; old lines still parse
- [x] Red Status state with the reason while the stretch lasts
- [x] Notification on service-off with protection on; tap opens Status
- [x] Without the service (D46): each screen-off posts the resume notification and leaves a stretch until the next Start
- [x] OFF reasons `SCREEN_OFF` / `RESTARTED` kept only for switch off
- [ ] SPEC V3 on A and B: service off, `am force-stop dev.sophiel` (reason "app stopped"), switch off behaves as M6
- [x] Ends buildable

## Notes (built 2026-10-14)

- **Rules (pure, `CaptureModeMachine`, JVM tests).** `uncovered` = protection on and no session will capture (`protecting && !holdsSession`). `gapOpens`: uncovered with the screen on; `gapEnds`: no longer uncovered (Start, PIN Stop, switch off, or a bind after a kill). One stretch per interruption: screen-offs inside it count (D46 "until the next Start"). **Open for the human:** SPEC's definition says "the screen on"; if the night should not count, split or subtract screen-off time. `offReason` now only where protection ends, so `SCREEN_OFF` / `SYSTEM` / inferred OFFs only happen with the switch off. `serviceLost`: "Protección interrumpida" (opens Status, no consent). `lostShare`: a share ended with protection on and no fallback posts the D21 resume notice (D46).
- **Log (`EventLog`).** The open stretch lives in prefs (`gap_at`, `gap_reason`) and is written as `UNPROTECTED,<seconds>,<reason>` when it ends, stamped at its start. `capturing(on)` runs the heartbeat while a session lives (no longer tied to ON). `recoverGap(keepProtecting)`: switch off, M6's OFF; switch on, a restart is a closed stretch from the last heartbeat to boot, capped at one heartbeat (boot-to-unlock and power-off time are not a gap); an app stopped opens a stretch at the last heartbeat that ends when capture comes back. ON only at the Parent's Start: from off, or a Start that ends a stretch (not the slow-mode return).
- **Status.** `Page.INTERRUPTED` (red `Tone.Alert`): "Protección interrumpida · Nada revisa la pantalla desde las HH:MM", a "Qué pasó" row with the reason, missing rows, and Start. Log row "Sin protección · <reason> · 12 min" in Otros.
- **Known, kept:** the service turning off during a share posts nothing until the share ends (then D21's notice, per §4.7 "the share ends at screen-off as before, the D21 notification is posted"). After a force-stop, re-enabling accessibility without opening Sophiel resumes protection (the bind starts a new process, `interrupted` is not persisted) and ends the stretch; with Sophiel opened first it waits for Start.
- **Device check to do (A and B), SPEC V3:** (1) accessibility mode, turn the service off in Settings: notification "Protección interrumpida · toca para reactivar", tap opens Status red with "La accesibilidad está apagada"; Start (consent) ends it and the Log shows Sin protección with its length. (2) `adb shell am force-stop dev.sophiel`, reopen: Status red "La app se cerró"; after Start the Log shows Sin protección, reason "La app se cerró". (3) Switch off: share ends at screen-off, D21 notice, Log OFF "La pantalla se apagó", no Sin protección. (4) Without the service (D46): screen off ends the share with the D21 notice; at screen-on Status is red until Start. (5) Reboot with protection on (service on): Log shows a short Sin protección "El teléfono se reinició", then masks without opening Sophiel.

## Device check, 2026-10-14 (B, by the human)

- V3 steps 1-5 done on B. Step 5 (reboot): the Log showed "Unprotected · The phone restarted · 22 s" (English UI): last heartbeat to boot, under the 60 s cap; boot-to-unlock not counted.
- Step 5 ran with accessibility on, as intended. A pending for all steps.
- **Found, open:** reboot with accessibility **off** and protection on: nothing resumes, and the live stretch ("La accesibilidad está apagada") opens only when Sophiel is opened, so unlock-to-open time is not counted. Starting it at boot would count boot-to-unlock instead (no unlock time without a receiver). Decide before ticket 19.
  **Decided 2026-10-14 (D61):** the stretch found at the first app start after a boot begins at the boot. Built (`EventLog.gapFound`, JVM test). Device check on B still to do: reboot with protection on and accessibility off, wait a few minutes, open Sophiel: Status red "desde las <boot time>", and after Start the Log shows "El teléfono se reinició" then "La accesibilidad está apagada" from the boot.

## Device check, 2026-10-14 (B, by the human): D61

- Reboot with protection on and accessibility off, Sophiel opened a few minutes later. Log (English UI):
  "Unprotected · The phone restarted · 19 s" and "Unprotected · Accessibility is off · 3 min": two
  stretches, as D61 says. The open item is closed on B; A not checked.
