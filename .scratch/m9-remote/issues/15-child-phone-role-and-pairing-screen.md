# 15: Child phone: role choice and pairing screen

**What to build:** SPEC M9 deliverable 4, pairing half (D81, D82). First launch in `remote` asks the role (Hijo default; Hijo runs today's wizard; Padre goes to a placeholder until ticket 16). Ajustes row "Teléfono del Padre" behind the door opens the pairing screen: battery exemption asked first if missing, the privacy line (D83), the QR (ZXing core on a Compose `Canvas`), "Mostrar código otra vez" (fresh claim code, same key), "Conectado" / "Sin conexión". On `paired` the key and device token go to app-private storage. Status line "Vinculado con el teléfono del Padre" while paired; Recomendado item "Sin restricción de batería" while paired and not exempt. Log kind **Vinculado**. Spanish strings, English in `values-en/`, both under `src/remote/res`.

**Blocked by:** 10, 12, 14

**Type:** AFK, then human check on A

**Status:** open

- [ ] Role stored; `offline` never asks
- [ ] The claim socket is open only while the pairing screen shows; leaving relocks and drops it
- [ ] Pairing with a scripted Parent (curl against local Bun through `adb reverse`) shows Vinculado on Status and in the Log
- [ ] JVM: old Log lines parse as before
- [ ] Checked on A by the human
- [ ] Ends buildable
