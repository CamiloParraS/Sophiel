# 27: Escalation

**What to build:** Repeated hits block an app (D58). 3 masked episodes with the same Episode app within a rolling 10 min block that app for 15 min, at once, even with it in front: Home + the block screen, now with "Bloqueada por Sophiel · vuelve en N min" and a small "Desbloquear con PIN" (D38 door; a correct PIN ends the block and resets the count). Status shows one orange line per active escalation block, "Instagram bloqueada · 12 min", counting down (D59; here because V6 checks it). The Apps screen gets the switch "Bloquear apps que muestran contenido explícito repetidamente", default on.

**Blocked by:** 25

**Status:** ready-for-agent

- [ ] Escalation rule as pure logic, JVM-tested with a fake clock: 3 in a rolling 10 min → 15 min; count restarts after a block; never counts unanalyzable, blank, trusted or excluded apps
- [ ] Block end times persisted on the wall clock, re-checked when the service binds after a reboot
- [ ] A PIN Stop clears escalation blocks; the PIN on the block screen ends one and resets its count
- [ ] Rule blocks (ticket 25) keep no PIN button and are not shown on Status
- [ ] Log kind **App bloqueada** (time + app + minutes, one per block); blocked opens during it log Apertura bloqueada with reason escalation
- [ ] Status line per active block, counting down
- [ ] Spanish strings, English in `values-en/`
- [ ] Checked on A: three episodes in Gallery block it; the countdown and the PIN unblock work
- [ ] Ends buildable
