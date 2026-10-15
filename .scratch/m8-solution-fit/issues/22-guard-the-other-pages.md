# 22: Guard the other pages

**What to build:** The guard (ticket 21) widens to the rest of D56's pages: "Appear on top" for Sophiel (only while granted, so granting it is never blocked), Sophiel's App info (Force stop, Clear storage, Uninstall), the uninstall dialog for Sophiel (covers launcher drag-to-uninstall), and Quick Settings "Active apps" when it lists Sophiel (screen-share mode only). Each closes to the same PIN pad and logs its own target.

**Blocked by:** 21

**Status:** ready-for-agent

- [ ] Each page added to the matcher with a JVM test from ticket 20's fixtures (A and B)
- [ ] "Appear on top" guarded only while the permission is granted; Active apps only while a share runs
- [ ] Intento de apagar targets: Appear on top / App info / uninstall / Active apps, each with its own label in the Log row
- [ ] Not guarded (D56): the screen-share "Stop sharing", Sophiel's notification settings
- [ ] Checked on A: each page closes to the PIN pad; `adb uninstall dev.sophiel` still works
- [ ] Ends buildable
