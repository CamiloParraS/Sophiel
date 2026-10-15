# 27: Escalation

**What to build:** Repeated hits block an app (D58). 3 masked episodes with the same Episode app within a rolling 10 min block that app for 15 min, at once, even with it in front: Home + the block screen, now with "Bloqueada por Sophiel · vuelve en N min" and a small "Desbloquear con PIN" (D38 door; a correct PIN ends the block and resets the count). Status shows one orange line per active escalation block, "Instagram bloqueada · 12 min", counting down (D59; here because V6 checks it). The Apps screen gets the switch "Bloquear apps que muestran contenido explícito repetidamente", default on.

**Blocked by:** 25

**Status:** built, device check on A pending (2026-10-15)

- [x] Escalation rule as pure logic, JVM-tested with a fake clock: 3 in a rolling 10 min → 15 min; count restarts after a block; never counts unanalyzable, blank, trusted or excluded apps
- [x] Block end times persisted on the wall clock, re-checked when the service binds after a reboot
- [x] A PIN Stop clears escalation blocks; the PIN on the block screen ends one and resets its count
- [x] Rule blocks (ticket 25) keep no PIN button and are not shown on Status
- [x] Log kind **App bloqueada** (time + app + minutes, one per block); blocked opens during it log Apertura bloqueada with reason escalation
- [x] Status line per active block, counting down
- [x] Spanish strings, English in `values-en/`
- [ ] Checked on A: three episodes in Gallery block it; the countdown and the PIN unblock work
- [x] Ends buildable

## Notes (agent, 2026-10-15)

- **Logic:** `settings/Escalation` (pure, fake-clock JVM test). Hits are in memory (a reboot restarts the count); block end
  times are wall clock in prefs `escalation`, read at construction, so a reboot keeps them and enforcement just compares
  the clock on each window event (nothing to re-check at bind).
- **Hit:** `CaptureSession.escalate`, on the off-lane after the Masked line, for a named Normal, non-excluded app, switch on.
  The third hit logs **App bloqueada** and `MaskWindowService.blockNow`: if the app has a window in front it goes through
  the same `resolve` path as a rule block (pop-ups included); if not, its next open is blocked.
- **Enforcement:** `block()` now fires for rule or active escalation; reason RULE wins. Block screen shows the countdown
  and "Desbloquear con PIN" only for an escalation block; a right PIN `end`s it (count reset) and finishes to Home.
- **Clears:** PIN Stop (`CaptureModeController.stop`) and turning the switch off.
- Status: one orange row per active block, ticking; the switch sits on the Apps screen.

**For the A check:** three episodes in Gallery (10 min) block it with it in front; Status counts down; PIN on the block
screen unblocks; a rule-blocked app shows no PIN button.
