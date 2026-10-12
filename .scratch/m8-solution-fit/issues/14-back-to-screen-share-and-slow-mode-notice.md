# 14: Back to screen share by fresh consent, slow-mode notification, Log entry

**What to build:** When the share ends with the screen on (chip, system), the Parent gets "Protección en modo lento · toca para volver al modo rápido"; tapping it, or Start, asks for fresh consent, and Status returns to "· compartir pantalla" (the projection starts, then the shots stop). The Log gains a **Compartir pantalla detenido** entry. Sophiel never asks for consent by itself.

**Blocked by:** 13

**Status:** ready-for-human (built 2026-10-12; device check on A and B pending)

- [x] Notification posted only when the share ends with the screen on and the switch is on
- [ ] No notification and no Log entry when the share ends with the screen off
- [ ] Tapping it opens consent; a fresh consent `Intent` every time; §4.4 ordering (FGS before projection) kept
- [x] Order on return: projection starts, then shots stop; no uncovered gap
- [x] New Log kind **Compartir pantalla detenido** (Spanish + `values-en/`), old lines still parse
- [ ] SPEC V1d on A: chip stop keeps masks, notification, Log entry, consent returns Status to "· compartir pantalla"
- [x] Ends buildable

## Notes (built 2026-10-12)

- **Rule.** `CaptureModeMachine.slowMode(old, new)`: SCREEN_SHARE -> ACCESSIBILITY, which only a `ShareEnded` with the screen on, the switch on and the service bound can do (JVM tests). `CaptureModeController` then writes `SHARE_STOPPED` and posts "Protección en modo lento" through the D21 `ACTION_RESUME` notification (tap -> `start()` -> fresh consent). Screen-off ends, Stop, switch off and unbound post nothing.
- **Return.** `shareStarted` hands the projection to the live session (`CaptureSession.startShare`): the display opens first, then the shot loop is cancelled. Masks and tracker stay, no second ON. A session with a share already, or none, is replaced as before.
- **Log.** `SHARE_STOPPED` row "Compartir pantalla detenido" (es + en), in the Others filter; unknown kinds still parse to nothing.
- **No Start button in accessibility.** Status shows the protected page there (SPEC §4.7: "No new button"), so the notification is the only way back; "or Start" is the PIN-Stop-then-Start path.
- **Review fixes.** A shot still in the air when the share takes over is dropped (`offerShot` checks `shots`); a failed `openDisplay` closes its `FrameSource`; the notice is cancelled when protection ends (PIN Stop, switch off) so a stale tap cannot start a session. Open: a notice cancelled by swipe or a cancelled consent leaves no way back to fast mode except PIN Stop then Start; a Status button was left out by SPEC §4.7 ("No new button").
- **Device check still to do (A and B):** V1d: chip stop keeps masks, notification and Log entry appear, tap -> consent -> Status "· compartir pantalla" and logcat shows the shot loop stopping after the display opens. Screen-off share end: no notification, no entry. A has no chip: use B for the chip route.
