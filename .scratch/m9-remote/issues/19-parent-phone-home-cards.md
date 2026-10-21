# 19: Parent phone: home cards with Start and Stop

**What to build:** SPEC M9 deliverable 5, home (D81, variant B of `.scratch/m9-remote/prototype-parent-screens.html`). One state card per Child phone, all fetched on open (a `log` request draws the state), stamped "hace N s", Start or Stop on the card. Skeleton while loading; a spinning pill while an action runs; a banner under the card for the result ("Ya estaba protegido", "Protección iniciada", or the reason). "Sin conexión" card with Reintentar (503/504); "Se desvinculó" card with Quitar de la lista (410), which drops the entry and its key; the accessibility-off flag disables Start. No last-known state kept.

**Blocked by:** 17, 18

**Type:** AFK, then human check on A + B

**Status:** open

- [ ] SPEC M9 V5's Start/Stop half on A + B
- [ ] Airplane mode on A: B shows "Sin conexión" within 1 s
- [ ] Ends buildable
