# 06: What Remote changes on the Child phone

Type: grilling
Status: resolved
Blocked by: none

## Question

How do the four Remote commands land on the Child phone's existing rules?

- Where Pairing lives (Ajustes behind the door, D38), the QR screen, the "Vinculado con un teléfono del Padre" line on Status, unpair.
- Remote Stop: same as a PIN Stop (clears escalation blocks, D58)? Remote Start: only in accessibility capture; what if the service is unbound?
- Remote app rules: applied like a local Apps change (D40 cover path)? Conflict with a Parent editing locally at the same moment.
- New Log kinds ("Iniciado a distancia", "Detenido a distancia", "Apps cambiadas a distancia"?), and whether a Log fetch is itself logged.
- Does any of this depend on a PIN existing on the Child phone?
- The wizard's new battery-optimization exemption step (ticket 02): only in `remote`, only once paired, or always? What Status says if it is missing.
- From the protocol ticket: the Child phone never logs in; its pairing screen (behind the door) shows the QR and "Mostrar código otra vez"; what Status says while a claim code is waiting, and after a "desvinculado" notice.

## Answer

Human decisions, 2026-10-20 (one grilling round, every recommendation accepted).

- **Where Pairing lives:** Child role, `remote` flavor only. An Ajustes row "Teléfono del Padre" (behind the door, D38) opens one screen: QR, "Mostrar código otra vez", "Desvincular", and a connection line ("Conectado" / "Sin conexión").
- **PIN:** nothing new. The door implies a PIN (the wizard runs only while none exists). Incoming remote commands are authorised by the pairing key and never ask the PIN on the Child phone.
- **Remote Stop** = a PIN Stop: the same `CaptureModeController.stop()` path (clears escalation blocks, D58), logged as OFF with a new reason `REMOTE` ("Detenido a distancia").
- **Remote Start:** service bound and "Seguir protegiendo siempre" on → protection on, shot loop starts (or waits for screen-on), ON written with a `remote` field ("Iniciado a distancia"). Already protecting → "Ya estaba protegido". Service unbound → reject "El servicio de accesibilidad está apagado en el teléfono del Hijo". Switch off → reject "‘Seguir protegiendo siempre’ está apagado en el teléfono del Hijo". A remote command never changes any other setting.
- **Remote app rules:** applied exactly like a local Apps change (same store; the local Apps screen redraws). Last write wins, no locking. Bloqueada with that app in front blocks at once, like escalation; De confianza applies on the next window change. The list sent is D58's (exclusions kept). Service unbound → the reply says "Necesita el servicio de accesibilidad" and the Parent's Apps rows are disabled, as on the Child phone.
- **Log:** no new kinds for commands. Start/Stop carry the remote marker only. Rule changes are not logged (local ones aren't either). Log fetches are not logged. New Pairing entries: "Vinculado" and "Desvinculado" (saying which side unpaired: Padre / este teléfono).
- **Battery exemption:** asked on the pairing screen before the QR appears, only if not already exempt. No wizard step. While paired and not exempt, a Recomendado item "Sin restricción de batería" shows on Status.
- **Status:** paired → "Vinculado con el teléfono del Padre"; connection state stays on the pairing screen. A waiting claim code shows nothing (leaving the screen relocks; the code expires in 10 min). After a "desvinculado" notice the line disappears and the Log gets "Desvinculado (por el Padre)". No notification.
