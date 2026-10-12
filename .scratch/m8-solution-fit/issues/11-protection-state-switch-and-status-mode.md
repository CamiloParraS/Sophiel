# 11: Protection state, "Seguir protegiendo siempre" switch, mode on Status

**What to build:** Protection (on/off) is persisted and set only by the Parent's Start and PIN Stop. A pure capture-mode state machine (`SCREEN_SHARE` / `ACCESSIBILITY` / `NONE`) encodes the SPEC §4.7 transitions. The Parent sees the switch "Seguir protegiendo siempre" in Ajustes (default on, behind the PIN, greyed "Necesita la máscara completa" without the accessibility service) and Status shows the mode: "Protección activa · compartir pantalla". Switch off behaves exactly as M6.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [ ] Protection state survives process death and reboot; only Start and PIN Stop change it (plus the switch-off case below, which SPEC §4.7 requires: the machine clears it and tickets 12/13 must persist that)
- [ ] Mode state machine is pure logic with JVM tests covering every arrow in the §4.7 diagram (D54)
- [ ] Switch in Ajustes: default on, greyed with "Necesita la máscara completa" when the service is unbound
- [ ] Status state line shows "Protección activa · compartir pantalla"
- [ ] Switch off: share ends with the screen, D21 notification, D39 OFF reasons, as M6
- [ ] Spanish strings, English in `values-en/`; `:safecore` stays UI-free; ends buildable
