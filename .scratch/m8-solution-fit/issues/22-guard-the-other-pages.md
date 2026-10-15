# 22: Guard the other pages

**What to build:** The guard (ticket 21) widens to the rest of D56's pages: "Appear on top" for Sophiel (only while granted, so granting it is never blocked), Sophiel's App info (Force stop, Clear storage, Uninstall), the uninstall dialog for Sophiel (covers launcher drag-to-uninstall), and Quick Settings "Active apps" when it lists Sophiel (screen-share mode only). Each closes to the same PIN pad and logs its own target.

**Blocked by:** 21

**Status:** built, device check on A open (2026-10-14)

- [x] Each page added to the matcher with a JVM test from ticket 20's fixtures (A and B)
- [x] "Appear on top" guarded only while the permission is granted; Active apps only while a share runs
- [x] Intento de apagar targets: Appear on top / App info / uninstall / Active apps, each with its own label in the Log row
- [x] Not guarded (D56): the screen-share "Stop sharing", Sophiel's notification settings
- [ ] Checked on A: each page closes to the PIN pad; `adb uninstall dev.sophiel` still works
- [x] Ends buildable

## Notes (agent, 2026-10-14)

Fixtures are B's p2-p5 captures (A not dumped, ticket 20). Matching is by view id, not words, so it is language-free:
App info = header "Sophiel" + `forcestop_button`; Appear on top = header + permission text id + `switch_widget` **checked**;
uninstall = installer `:id/app_label`; Active apps = SystemUI `fgs_manager_app_item_label` (the row exists only while the share runs).
Service now also reads the package installer and SystemUI windows (one text search + 3 id lookups per event).

**For the A check:** each page closes to the PIN pad; Appear on top with the switch off does not; a notification-settings page for
Sophiel (not guarded, D56) must not fire (it may share the header id, only the id/switch rules separate it); `adb uninstall dev.sophiel` works.
