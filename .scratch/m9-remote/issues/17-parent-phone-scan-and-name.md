# 17: Parent phone: scan and name a Child phone

**What to build:** SPEC M9 deliverable 5, pairing half (D80, D81). "+" on the home shows the scanner disclosure line, then `GmsBarcodeScanning.getClient().startScan()`. A good scan sends `POST /pairings`, then asks a name; the local list (pairing id, name, key) goes to app-private storage. A bad or expired scan names the fix ("Mostrar código otra vez"). First end-to-end pairing: A shows Vinculado.

**Blocked by:** 15, 16

**Type:** AFK, then human check on A + B

**Status:** resolved (code; human check on A + B pending)

- [ ] A (Child) paired with B (Parent) end to end on `remoteDebug` (SPEC M9 V4 first half)
- [ ] Reinstalling the Parent app empties the list; "Mostrar código otra vez" and a scan restore it with the same key
- [ ] Noted: the first scan with no network before the scanner module installed (D80 open question)
- [ ] Ends buildable

## Answer

Built in f545113, merged ff1a12a. PairingTest (5) + ParentSessionTest (8) pass. Device checks and the D80 no-network first-scan note are for the human.
