# 18: Wizard accessibility-step switch and copy changes

**What to build:** The wizard's accessibility step offers the "Seguir protegiendo siempre" switch, and the copy in the wizard and the service's "Recomendado" item reflects the new behaviour ("y sigue protegiendo tras apagar la pantalla o reiniciar", D54, D56). Copy: "Sophiel sigue tapando contenido aunque se apague la pantalla o se reinicie el teléfono, sin pedirte permiso cada vez. En ese modo tapa un poco más lento."

**Blocked by:** 11

**Status:** built 2026-10-14; wizard V3 flow on A and B still to check

- [x] Switch on the wizard step, same state as the Ajustes switch, greyed without the service
- [x] Copy updated in the wizard and "Recomendado" item
- [x] Spanish strings, English in `values-en/`
- [ ] Wizard still passes its M6 V3 flow on A and B
- [x] Ends buildable
