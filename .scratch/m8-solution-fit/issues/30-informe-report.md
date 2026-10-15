# 30: "Informe" report

**What to build:** An "Informe" button on the Log header (already behind the door) opens a bottom sheet with a Hoy / 7 días selector, a plain-text preview, and "Compartir…" opening the Android share sheet (`ACTION_SEND` text/plain). Sophiel sends nothing itself (D59). Contents: period, date, counts of tapados / sin revisar / Sin protección (times) / Intento de apagar / apps bloqueadas, top 3 apps by masked count, per-day counts for 7 días, and "Solo cifras. Sophiel no guarda imágenes."

**Blocked by:** 29

**Status:** ready-for-agent

- [ ] Report text built by a pure function from ticket 29's counts and string resources, JVM-tested for Hoy and 7 días
- [ ] No FileProvider, no image card, no new permission
- [ ] Spanish strings, English in `values-en/`
- [ ] Checked on B: the text arrives in a chat app and its counts match the Log
- [ ] Ends buildable
