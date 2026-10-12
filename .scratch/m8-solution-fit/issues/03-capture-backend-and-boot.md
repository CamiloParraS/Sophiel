# 03: Capture backend, switching rules and start on boot

Type: grilling
Status: resolved
Blocked by: 01, 02

## Question

Decide how protection gets frames and comes back on its own (gap 1: survive normal life). Starting point: human prefers **hybrid** (MediaProjection when the Parent started it, accessibility screenshots as fallback), and wants the screenshot backend kept **as a toggle** (said after ticket 02). Research may favour replacing MediaProjection outright.

- Toggle: who sets it (the Parent in Settings, or a debug switch), what it chooses (always screenshots / hybrid / MediaProjection only), and whether the fallback still happens automatically when it is off.

- Hybrid vs replace, given ticket 02's numbers on A.
- Switching rules: when does it fall back (reboot, consent revoked, Android 15+ secure-lock end, Child stops via the screen-share chip) and when, if ever, does it go back to MediaProjection? What the Child sees during a switch (exposure).
- Start on boot: boot receiver vs the accessibility service's own rebind; foreground service type for a start with no Activity; what the notification says. Does "Paused — tap to resume" (D21) survive or go away?
- Status screen and Log: how the backend shows (e.g. "Protección activa · modo accesibilidad"); new protection on/off reasons.
- Does Precise (D35) still work on the screenshot backend? (Peek does: ticket 02 ran 171 peeks on B with no failures; window shots have their own budget.)
- From ticket 02: the probe timeout must exceed the shot interval on the screenshot backend (300 ms flickered and got stuck; 700 ms was fine). Is it per backend, and what value? Should peek under mask be on by default wherever it works (B), now that it's the difference between "flickers" and "barely flickers"? Stop shots on screen off (they come back black).
- Is A's ~0.45 s exposure per probe on the fallback acceptable, or does A need something more (for example, telling the Parent that fallback protection is weaker on this phone)?
- Which SPEC §7 limitations (5, 6, 10, 17) change, and which D-entries are amended (D28/D33 rotation path, D46 starting without the service).

## Answer

Grilled with the human 2026-10-11. Terms added to `CONTEXT.md`: **Protection**, **Capture mode**.

- **Protection vs capture mode.** Protection is on from the Parent's Start until a PIN Stop; reboot, screen-off and lost consent change only the capture mode (**screen share**, **accessibility**, **none**). Force-stop/crash still shows as interrupted.
- **Hybrid behind one switch, "Seguir protegiendo siempre"** (Ajustes + the wizard's accessibility step, behind the PIN, default on; greyed with "Necesita la máscara completa" without the service). On: screen share whenever someone accepted the dialog, accessibility otherwise. Off: today's screen-share-only behaviour (D21 notifications and Log as now). No "accessibility only" value: the hybrid never asks for consent by itself anyway. Copy: "Sophiel sigue tapando contenido aunque se apague la pantalla o se reinicie el teléfono, sin pedirte permiso cada vez. En ese modo tapa un poco más lento."
- **Switching.** Screen share → accessibility whenever the projection ends with protection on (screen-off, secure lock, chip stop, revoked): masks and tracker carry over, no cover; first shot ~0.4 s later. Accessibility → screen share only through Start or the notification (fresh consent); the new session starts the projection, then stops the shots. Sophiel never asks for consent by itself. Status adds no button; its state line shows the mode ("Protección activa · compartir pantalla" / "· accesibilidad").
- **Boot / screenshot loop.** No boot receiver, no foreground service, no notification in accessibility mode. `MaskWindowService` starts the loop when it binds if protection is on (first unlock after reboot); the loop stops on screen-off (shots come back black) and restarts on screen-on. Screen share keeps its required FGS notification.
- **Probes on accessibility** lift their mask about one frame before the next shot is due (estimate on A: 0.15-0.25 s exposure, vs 0.45 s measured untimed). Probe timeout per capture mode: 300 ms screen share, 700 ms accessibility. A still exposes 2-3x more than on screen share: LIMITATIONS, no extra Parent warning.
- **Peek under mask defaults to on** (applies where it works: API 34+, service bound). **Precise works on both capture modes** (window shots have their own budget); a verification item on B.
- **Notifications and Log** (switch on):

  | Event | Notification | Log |
  |---|---|---|
  | Screen share ends with the screen on (chip, system) | "Protección en modo lento · toca para volver al modo rápido" → consent | new entry "Compartir pantalla detenido" |
  | Screen share ends with the screen off | none | none |
  | Accessibility service turns off with protection on | "Protección interrumpida · toca para reactivar" → Status fix button | the "Sin protección" stretch |

  ON/OFF are written only for the Parent's Start and Stop; OFF reasons SCREEN_OFF and RESTARTED go away. Other mode switches are not logged.
- **"Sin protección" Log entry:** a stretch where protection was on, the screen on, and nothing captured; start, duration, reason (accessibility off, app stopped, restarted). Boot-to-first-unlock is not a gap. Status shows a red "Protección interrumpida" with the reason while it lasts.
- **D46 kept:** Start needs only the overlay permission. Without the service there's no fallback: each screen-off posts the resume notification and leaves a "Sin protección" stretch until the next Start; the service's "Recomendado" item adds "y sigue protegiendo tras apagar la pantalla o reiniciar".
- **Settled without asking:** B's Display P3 shots are converted to sRGB on copy. Rotation keeps the D33 cover path (the measurement run uncovered for 376-520 ms on accessibility, inside the 1 s cover wait).
- **Open, own ticket:** does screen share survive screen-off on A? → [Does screen share survive screen-off on A?](10-screen-share-through-screen-off-on-a.md). If yes, below API 35 Sophiel pauses frames at screen-off instead of ending the share (amends D18); if not, D18 stands and LIMITATIONS says A runs on the slower mode most of the day.
- **For the SPEC write-up:** rewrite §7 items 5 (consent), 10 (D16 doesn't apply in accessibility mode) and 17 (rotation on accessibility); item 6 waits for the tamper guard ticket; new limitation: A's fallback exposes 2-3x more. D-entries amended: D18 (pending ticket 10), D21, D34 (peek default), D39 (Log kinds), D46 (Status line).
