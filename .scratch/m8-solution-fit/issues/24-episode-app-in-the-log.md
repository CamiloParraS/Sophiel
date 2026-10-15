# 24: Name the app in each Log episode

**What to build:** Deliverable 3 (D57), end to end. Each masked episode and unanalyzable stretch names the **Episode app**: the package of the topmost `TYPE_APPLICATION` window under the highest-scoring masked tile at its start (the lookup `appWindowShot` already does; works on API 33). The Log rows show the app's label and the summary reads "7 hoy · más en Instagram (4)". Blank ("App desconocida") without the service, with only system UI under the tile, or on Sophiel itself; the launcher counts as an app.

**Blocked by:** 23

**Status:** ready-for-agent

- [ ] Package appended as the last field of `MASKED` and `UNANALYZABLE` (stamped at stretch start); old lines without it read as blank, no migration (JVM test)
- [ ] Episodes are not split by an app change: an episode keeps its first app, with a `ponytail:` comment naming the ceiling (D57)
- [ ] `<queries>` for the MAIN/LAUNCHER intent (SPEC §4.1), not `QUERY_ALL_PACKAGES`; labels resolved at read time, an uninstalled app shows its package
- [ ] Only `window.root.packageName` is read: no text, titles or nodes; the `EventLog` doc comment "no app names" goes
- [ ] Log rows show the app; the summary line names today's top app
- [ ] SPEC M8 V5 on A and B: Gallery and Chrome episodes show their labels; split screen names the app under the strongest mask; started without the service, rows read "App desconocida"
- [ ] CLAUDE.md "Current state" updated; NEXT points to deliverable 4
- [ ] Ends buildable
