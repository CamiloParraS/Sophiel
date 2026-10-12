# 05: Tamper guard: what is guarded, how, and what gets logged

Type: grilling
Status: resolved
Blocked by: 04

## Question

Decided at charting: settings guard (accessibility) + Device Admin + start on boot; not Device Owner. Each blocked attempt is logged. Decide the details (gap 2: hard to turn off):

- Which targets the guard covers (from ticket 04's table), and which are left as limitations.
- What the Child sees when blocked: just sent Home, or a "Protegido por Sophiel" screen with a PIN entry for the Parent?
- How the guard fits D38 "one door": an active unlock disarms the guard for its 2 min; anything else?
- Device Admin: enrolled in the wizard as a new step (required or skippable?), and the deactivation message.
- New **Tamper attempt** Log kind: fields (time, target, app), episode-like dedupe (one per attempt burst?).
- Wizard and Status changes: new "Needs attention" items (admin off, guard unavailable).
- Debug escape hatch so development (adb install over, uninstall) isn't blocked: debug builds only? Debug-menu switch?

## Answer

Grilled with the human 2026-10-12. Terms in `CONTEXT.md`: **Guard** (new), **Tamper attempt** (rewritten), **Unlock** (amended).

- **No Device Admin.** Uninstall protection is future work / stretch goal (map: Out of scope). No wizard step, no `DeviceAdminReceiver`, no Parent "Desinstalar" row; `adb uninstall` keeps working, so no debug escape hatch is needed.
- **Armed** whenever a PIN exists and no Unlock is active, whether or not Protection is on. A page is guarded only while the thing it turns off is on (e.g. "Appear on top" only while granted), so turning something *on* is never blocked. The wizard runs before a PIN exists, so it is never guarded.
- **Guarded pages** (matched by package + text "Sophiel", not class names; ticket 04 table): Accessibility > Sophiel and its "Stop Sophiel?" dialog (incl. Samsung's accessibility app); "Appear on top" for Sophiel; Sophiel's App info (Force stop, Clear storage, Uninstall); the uninstall dialog for Sophiel (covers launcher drag-to-uninstall); Quick Settings "Active apps" when it lists Sophiel (screen-share mode only, one tap, race).
- **Not guarded:** screen-share "Stop sharing" (hybrid falls to accessibility and logs "Compartir pantalla detenido", ticket 03); Sophiel's notification settings.
- **Limitations:** accessibility shortcut (volume keys), safe mode, adb, factory reset, uninstall from the Play Store app, the tap race (one-tap Active apps Stop, very fast taps). Guard delay is measured on A as a verification item.
- **When it fires:** Sophiel opens at its existing PIN pad (D38 door) with a line saying the setting is protected; Back goes Home. A correct PIN starts the normal Unlock and Sophiel steps aside, back to the page. **D38 amended:** an Unlock started from the guard survives leaving the app; it still ends after 2 min or at screen-off. The guard is off while any Unlock is active.
- **Wizard and Status:** no new step; copy only. The accessibility step and Status's "Máscara completa" Recomendado item add "y protege los ajustes de Sophiel". Without the service there is no guard and nothing more is said.
- **Tamper attempt Log entry:** time + target (accessibility off / Appear on top / App info / uninstall / Active apps). Same target within 60 s merges into one entry with a count. No result field (everything logged was closed by the guard). A force stop that wins the race, or comes from adb, shows as ticket 03's "Sin protección" (reason: app stopped). Whether Status shows recent attempts: ticket 08.
- **Service config:** add `typeWindowStateChanged` only (no content-change events), `flagReportViewIds`, `packageNames` limited to Settings, the package installer, SystemUI and Samsung's accessibility package (exact Samsung names confirmed on A and B at build time, commands in ticket 04's findings). Battery cost folds into M7 V3.
- **For the SPEC write-up:** D38 amendment (Unlock from the guard); D39 Log kinds gain Tamper attempt; §7 item 6 rewritten from the limitations above; manifest diff is the service config only (no new permission).
