# 16: Parent phone: login, sign-up and Cuenta

**What to build:** SPEC M9 deliverable 5, account half (D81). The Padre role skips the wizard and opens login. Login and sign-up in Design A's world (D75): password 8+ chars, one amber "no recovery" warning and the privacy line on sign-up, one error for wrong email or password, the lockout message. Token in app-private storage; a 401 anywhere returns to login. The home (empty for now) has a gear to Cuenta: Cerrar sesión, Borrar cuenta (confirmed). "Volver a teléfono del Hijo" as the login screen's last link, only while logged out. Client: OkHttp POSTs on coroutines.

**Blocked by:** 10, 11

**Type:** AFK, then human check on B

**Status:** open

- [ ] Sign-up, login, logout, Borrar cuenta work against local Bun
- [ ] The Parent role never shows protection screens, the wizard or the door
- [ ] The empty home says where the Child phone's QR is
- [ ] Checked on B by the human
- [ ] Ends buildable
