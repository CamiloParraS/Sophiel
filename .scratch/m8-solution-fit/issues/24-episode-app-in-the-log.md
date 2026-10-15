# 24: Name the app in each Log episode

**What to build:** Deliverable 3 (D57), end to end. Each masked episode and unanalyzable stretch names the **Episode app**: the package of the topmost `TYPE_APPLICATION` window under the highest-scoring masked tile at its start (the lookup `appWindowShot` already does; works on API 33). The Log rows show the app's label and the summary reads "7 hoy · más en Instagram (4)". Blank ("App desconocida") without the service, with only system UI under the tile, or on Sophiel itself; the launcher counts as an app.

**Blocked by:** 23

**Status:** built, device check (V5) on A and B open (2026-10-15)

- [x] Package appended as the last field of `MASKED` and `UNANALYZABLE` (stamped at stretch start); old lines without it read as blank, no migration (JVM test)
- [x] Episodes are not split by an app change: an episode keeps its first app, with a `ponytail:` comment naming the ceiling (D57)
- [x] `<queries>` for the MAIN/LAUNCHER intent (SPEC §4.1), not `QUERY_ALL_PACKAGES`; labels resolved at read time, an uninstalled app shows its package
- [x] Only `window.root.packageName` is read: no text, titles or nodes; the `EventLog` doc comment "no app names" goes
- [x] Log rows show the app; the summary line names today's top app
- [ ] SPEC M8 V5 on A and B: Gallery and Chrome episodes show their labels; split screen names the app under the strongest mask; started without the service, rows read "App desconocida"
- [x] CLAUDE.md "Current state" updated; NEXT points to deliverable 4
- [x] Ends buildable

## Notes (agent, 2026-10-15)

- `MaskWindowService.appAt(x, y)`: same window pick as `appWindowShot` (top `TYPE_APPLICATION` under the point), then
  `window.root.packageName` only; null on Sophiel. No API 34 gate, so it works on A (API 33).
- Tiles: the masked tile with the highest last raw score, its centre. Precise: box masks are sorted strongest first, the
  first one's centre. Unanalyzable: no mask on a secure screen, so the screen centre at the stretch's first black frame.
- `<queries>` also has MAIN/HOME, beside MAIN/LAUNCHER: a launcher usually has no LAUNCHER activity, and the ticket counts
  it as an app. Without it the launcher's row would show its package. SPEC §4.1 snippet updated.
- Rows: "Chrome · 2 partes de la pantalla", "Chrome · protege su pantalla · 4 s". Summary under the week line, today only,
  when a masked episode today has a known app: "7 hoy · más en Instagram (4)".

**For the V5 check (A and B):** Gallery and Chrome episodes show their labels; split screen names the app under the
strongest mask; a debug build started without the service reads "App desconocida"; a launcher-screen episode shows the
launcher's label, not its package.
