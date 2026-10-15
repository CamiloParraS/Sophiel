# 29: Log dashboard

**What to build:** The Log screen becomes D59's dashboard over day-grouped rows. Top: summary chips (tapados · más en <app>, intentos, bloqueos), a 7-day bar chart of masked episodes per day, and a "Por app" list with bars (masked count per app, 7 days). Below: the Todo / Tapados / Otros filter and rows grouped under day headers with the day's total ("Hoy · 8 tapados"). Every M8 kind (Sin protección, Compartir pantalla detenido, Intento de apagar, App bloqueada, Apertura bloqueada) uses the same row. No per-app drill-down. Prototype: branch `proto/m8-log-report` (794a92c), `mockups/log-report.html`.

**Blocked by:** 28

**Status:** ready-for-agent

- [ ] The counting (per day, per app, per kind, for a period; "Hoy" = since local midnight; only what is in the 7-day Log) is one pure function, JVM-tested, so ticket 30's report reuses it
- [ ] Summary chips, 7-day bars, Por app list
- [ ] Day headers with the day's total; the filter unchanged
- [ ] Spanish strings, English in `values-en/`
- [ ] Checked on A: the dashboard's counts match the rows
- [ ] Ends buildable
