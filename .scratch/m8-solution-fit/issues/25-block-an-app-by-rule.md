# 25: Block an app by rule

**What to build:** The first end-to-end slice of per-app rules (D58). Ajustes gains an "Apps" row (already behind the door) opening a screen of launchable apps with a search field, sorted by label with changed ones pinned first, each Normal or Bloqueada. When a rule-blocked app comes to the front while protection is on (an Unlock included), the phone goes Home and Sophiel's block screen shows the app's icon and label with "El Padre bloqueó esta app", an "Ir al inicio" button and no PIN button; Back goes Home. Each blocked open is logged as **Apertura bloqueada**.

**Blocked by:** 24

**Status:** built, A check open

- [x] Per-app rules persisted; the app list comes from ticket 24's `<queries>` launcher intent
- [x] Excluded from the list (and later from escalation): Sophiel, Settings, the default launcher, the default dialer
- [x] Without the service the rows show disabled with "Necesita el servicio de accesibilidad"
- [x] Enforcement on `typeWindowStateChanged`: `GLOBAL_ACTION_HOME` + `ui/BlockActivity` (not exported, excluded from recents, SPEC §4.1)
- [x] Only while protection is on
- [x] Log kind **Apertura bloqueada** (time + app + reason rule / escalation), 60 s merge as for Intento de apagar, JVM-tested; row in the Log
- [x] Spanish strings, English in `values-en/`
- [ ] Checked on A: a blocked app opened from the launcher and from recents lands on the block screen
- [x] Ends buildable

## Notes (agent, 2026-10-15)

No device was attached, so the A check is open.

- **Rules:** `settings/AppRules` (SharedPreferences `app_rules`, package -> rule, Normal not stored). The list is
  `appList`, pure and JVM-tested: excluded apps out, search on the label, changed ones first as they were when the
  screen opened (a row does not jump under the finger), then by label with the phone's `Collator` ("cámara" before
  "Chrome").
- **Excluded:** `excludedApps`: Sophiel, whatever resolves `Settings.ACTION_SETTINGS` and MAIN/HOME, and
  `TelecomManager.defaultDialerPackage`, plus the default keyboard (not in D58: its windows send window-state events
  in every app, so a blocked keyboard would send the phone Home anywhere). Also checked at enforcement: an app blocked before it became the launcher or
  dialer is let through, so it can't trap the phone.
- **Enforcement:** `MaskWindowService.block`, on every window-state event whose package is Bloqueada while
  `captureModes.state.protecting`: `BlockActivity.block(pkg)` starts Home and the block screen in one
  `startActivities` call (the same single-instance activity as the guard; the package is an extra, `onNewIntent`
  switches it). **Not `GLOBAL_ACTION_HOME`** as the ticket says: that global action is asynchronous and could land over
  the block screen (review finding). Same result: the phone is at Home with the block screen over it. Logged once per 2 s per app, then the
  60 s merge (`EventLog.merge`, now shared with `tamper`).
- **Log:** `BLOCKED_OPEN,app,reason,count` (reason `RULE`; `ESCALATION` comes with ticket 27). Row "Apertura
  bloqueada", "Instagram · la bloqueó el Padre · 3 veces", under Otros.
- Settings: an "Apps" row in its own group under Protección.

**For the A check:** a Bloqueada app opened from the launcher and from Recents lands on the block screen, not on the
app, and Home never lands over the block screen. Also: rotating the block screen keeps it (not the PIN pad), and Back and "Ir al inicio" go Home;
protection off lets the app open; the Log shows one "Apertura bloqueada" for several opens within a minute.
