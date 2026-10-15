# 25: Block an app by rule

**What to build:** The first end-to-end slice of per-app rules (D58). Ajustes gains an "Apps" row (already behind the door) opening a screen of launchable apps with a search field, sorted by label with changed ones pinned first, each Normal or Bloqueada. When a rule-blocked app comes to the front while protection is on (an Unlock included), the phone goes Home and Sophiel's block screen shows the app's icon and label with "El Padre bloqueó esta app", an "Ir al inicio" button and no PIN button; Back goes Home. Each blocked open is logged as **Apertura bloqueada**.

**Blocked by:** 24

**Status:** ready-for-agent

- [ ] Per-app rules persisted; the app list comes from ticket 24's `<queries>` launcher intent
- [ ] Excluded from the list (and later from escalation): Sophiel, Settings, the default launcher, the default dialer
- [ ] Without the service the rows show disabled with "Necesita el servicio de accesibilidad"
- [ ] Enforcement on `typeWindowStateChanged`: `GLOBAL_ACTION_HOME` + `ui/BlockActivity` (not exported, excluded from recents, SPEC §4.1)
- [ ] Only while protection is on
- [ ] Log kind **Apertura bloqueada** (time + app + reason rule / escalation), 60 s merge as for Intento de apagar, JVM-tested; row in the Log
- [ ] Spanish strings, English in `values-en/`
- [ ] Checked on A: a blocked app opened from the launcher and from recents lands on the block screen
- [ ] Ends buildable
