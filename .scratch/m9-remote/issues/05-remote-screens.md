# 05: The Parent phone's screens

Type: prototype
Status: resolved
Blocked by: none

## Question

What do the Parent phone's screens look like and how do they flow, in Design A's world (D75)? Rough prototype to react to.

- First-launch role choice (Parent phone / Child phone), and how to change it later.
- Login / sign-up, Child phone list (named, "sin conexión" state), Pairing by scanning.
- Per Child phone: Log (reuse the Historial rows and dashboard parts?), Apps (the D58 rows, set remotely), Start / Stop with the result (e.g. "No se pudo iniciar: el servicio de accesibilidad está apagado").
- Loading, offline and error states for each.
- From the protocol ticket: account screens are login, sign-up, logout and "Borrar cuenta" (no password change, no recovery); the Child phone list is local to this Parent phone (a reinstall shows it empty until a re-scan); states "sin conexión" and "Este teléfono se desvinculó"; the Apps list arrives from the Child phone: labels only, or icons too (size within the 256 KB cap)?

## Assets

- Prototype: `.scratch/m9-remote/prototype-parent-screens.html` (`?variant=A|B|C`), built by `.scratch/m9-remote/prototype-build-parent.py` from Design A's CSS.

## Answer

**Variant B (Tarjetas)**, chosen by the human 2026-10-21, with the shared flow as prototyped.

- **Shared flow:** first launch asks the role (Hijo default; Padre skips the protection wizard and goes to login). Login, sign-up (8+ chars, one amber "no recovery" warning), one error for wrong email or password. Empty list teaches where the QR is. Scan via Google code scanner with a disclosure line; name the phone after a good scan (name is local). Bad scan names the fix ("Mostrar código otra vez"). Gear opens Cuenta: Cerrar sesión, Borrar cuenta. Back to the Child role only when logged out, from the login screen's last link.
- **Home:** one small state card per Child phone, all fetched on open, each stamped "hace N s", with Start or Stop on the card. Cards for "Sin conexión" (Reintentar) and "Se desvinculó" (Quitar de la lista). Loading is a skeleton card; an in-flight action spins in its pill; a failure drops a banner under its card naming the reason.
- **Per Child phone:** tap a card for Historial and Apps tabs. Historial reuses the Child's rows; Apps reuses D58's rows and rule sheet. Apps arrive as labels only, drawn as letter tiles (no icons on the wire).
- Result banners use C's wording: "Ya estaba protegido", "Protección iniciada", or the failure reason.
- No last-known state is kept when a phone is offline (pure relay).
