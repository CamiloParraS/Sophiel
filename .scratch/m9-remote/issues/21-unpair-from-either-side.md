# 21: Unpair from either side

**What to build:** SPEC M9 deliverables 4 and 5, unpair (D79, D82). Child: "Desvincular" on the pairing screen sends `unpair`, deletes key and token, logs **Desvinculado (este teléfono)**; the Status line goes. Parent: "Desvincular" on the per-phone screen sends `DELETE /pairings/:id` and drops the entry and key. Either side offline: cleanup on next contact (410 on the Parent, rejected `hello` on the Child). Borrar cuenta: Child phones clean up on next connect.

**Blocked by:** 19

**Type:** AFK, then human check on A + B

**Status:** open

- [ ] SPEC M9 V7 on A + B, including each side offline and Borrar cuenta
- [ ] Ends buildable
