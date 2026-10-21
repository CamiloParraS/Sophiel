# 18: Child phone: the socket and answering commands

**What to build:** SPEC M9 deliverable 4, command half (D78, D82, D83). While paired and the process lives, hold the socket: OkHttp 5 behind a `callbackFlow`, `hello` with the device token, reconnect on network change, screen on, and a 5-min `setAndAllowWhileIdle` alarm. Open each `req` with the envelope and replay guard and answer: `log` (7-day rows + Apps list, labels only, exclusions applied), `rules` (through the Apps store), `stop` (`CaptureModeController.stop()`, OFF reason `REMOTE`), `start` (D82's three outcomes and two rejection texts; ON with the remote marker). Every reply carries `protecting`, `captureMode`, `accessibilityOff`. `unpaired` or a rejected `hello` deletes key and token and logs **Desvinculado (por el Padre)**.

**Blocked by:** 13, 14, 15

**Type:** AFK, then human check on A

**Status:** resolved (code; human check on A pending)

- [ ] Each command driven by a scripted Parent against local Bun (`server/scripts/parent.ts "<QR text>" log stop start rules:pkg=Bloqueada`)
- [ ] Remote Stop clears escalation blocks, as a PIN Stop does
- [ ] Service off, Sophiel open: `log`, `stop`, `rules` answer with `accessibilityOff`; `start` is rejected
- [ ] Logcat shows a reconnect after airplane mode off and after screen on
- [x] JVM: command handling over a fake capture controller and rule store (`ChildCommandsTest`)
- [x] Ends buildable

## Answer

Built in 3316ca0, merged 61cf6d2. ChildCommands/ChildLink/ChildSocket; start rejections go out as codes (ALREADY_ON, ACCESSIBILITY_OFF, SWITCH_OFF) for the Parent to word. Scripted Parent: `bun scripts/parent.ts` in server/. Escalation clear on remote stop not JVM-tested (needs Android context).
