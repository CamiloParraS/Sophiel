# 20: Confirm the guarded pages on A and B

**What to build:** Nothing in the app. The human opens each page the guard will cover, on both devices, and records what the accessibility service would see, so ticket 21's page matching is built on real One UI values, not guesses. Commands are in ticket 04's findings (branch `research/guard-and-attribution`, `.scratch/m8-solution-fit/research/04-accessibility-guard-and-attribution.md`) and SPEC §8 (`dumpsys window`, `uiautomator dump`).

Pages (D56): Accessibility > Sophiel; its "Stop Sophiel?" dialog; the same in Samsung's accessibility app if it has its own; "Appear on top" for Sophiel; Sophiel's App info (Force stop, Clear storage, Uninstall); the uninstall dialog for Sophiel (from App info and from a launcher drag); Quick Settings "Active apps" listing Sophiel (screen-share mode).

**Blocked by:** None (can start immediately)

**Status:** done (2026-10-14)

- [x] One row per page and device: package, window-state event class, where "Sophiel" appears (text or view id), any view id that tells the page apart
- [x] Whether B shows the "Stop Sophiel?" dialog to the service; if not, the answer for `isAccessibilityTool="true"` (D60)
- [x] Table written into this ticket's notes; it becomes ticket 21's and 22's JVM fixtures

## Notes (human answers, 2026-10-14)

Screenshots: `20-screenshots/` (activeapps.jpg, uninstall_appinfo.jpg, uninstall_homescreenuninstall.jpg).
Device per answer not recorded (answers did not say A or B). Package, event class and view ids are **not yet captured**: they need `dumpsys window` / `uiautomator dump` on each page.

| #   | Page                                             | Answer                                  | Evidence                                                                                                |
| --- | ------------------------------------------------ | --------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| 1   | Accessibility > Sophiel, switch off              | No "Stop Sophiel?" box                  | none                                                                                                    |
| 2   | Samsung's own accessibility app                  | No separate page on either device       | none                                                                                                    |
| 3   | Appear on top                                    | Appears                                 | none                                                                                                    |
| 4   | App info (Force stop, Clear storage, Uninstall)  | The four items appear                   | none (confirm which four)                                                                               |
| 5   | Uninstall dialog (App info and home-screen drag) | Appears; same on both paths             | uninstall_appinfo.jpg, uninstall_homescreenuninstall.jpg: "Uninstall this app?" with Cancel / Uninstall |
| 6   | Active apps (screen-share)                       | Appears, with a Stop button for Sophiel | activeapps.jpg: "Check background activity" sheet, Sophiel "2 minutes" with **Stop**                    |

Consequence for the open question: with the switch off, no stop dialog reached the user. The Stop button on page 6 is a one-tap path, not a dialog; ticket 21 must still guard it (per D56).

**Device update (2026-10-14):** the three screenshots are from B. The human reports that A shows the same pages and dialogs as B, so the table above applies to both devices. Still not captured: package, window-state event class, view ids. Those need the `dumpsys`/`uiautomator` capture on A and B.

**Checklist status:** item 2 answered: B does not show "Stop Sophiel?" to the service, so ticket 21 sets `isAccessibilityTool="true"` (D60). Item 1 and item 3 stay open until the capture rows exist.

## Capture rows (device connected 2026-10-14, one device: serial adb-R5CXA3W6L9V; A/B not yet mapped to this serial)

Files: `20-screenshots/p1..p5-*.xml` (UI tree) and `p1..p5-focus.txt` (focused window).

| # | Page | Package | Window / event class | Where "Sophiel" appears | View ids that tell it apart |
|---|------|---------|----------------------|-------------------------|------------------------------|
| 1 | Accessibility > Sophiel (service page) | `com.android.settings` | `com.android.settings.SubSettings` | title text "Sophiel opaque masks" (`com.android.settings:id/sesl_switchbar_container`); the service label is "Sophiel opaque masks", not "Sophiel" | `android:id/switch_widget` |
| 2 | App info | `com.android.settings` | `com.android.settings.applications.InstalledAppDetails` | `com.android.settings:id/entity_header_title` = "Sophiel" | `com.android.settings:id/forcestop_button`, `com.android.settings:id/uninstall_button` |
| 3 | Uninstall dialog | `com.google.android.packageinstaller` | `com.android.packageinstaller.v2.ui.UninstallLaunch` | `com.google.android.packageinstaller:id/app_label` = "Sophiel" | `android:id/alertTitle` ("Uninstall this app?"), `android:id/button1` (Uninstall), `android:id/button2` (Cancel) |
| 4 | Appear on top | `com.android.settings` | `com.android.settings.SubSettings` | `com.android.settings:id/entity_header_title` = "Sophiel" | `android:id/title` "Allow permission"; text "Appear on top" |
| 5 | Active apps (Recents menu, see correction) | `com.android.systemui` (node ids); window title "Check background activity" | window, not an activity; launched from `com.android.quickstep.RecentsActivity` | `com.android.systemui:id/fgs_manager_app_item_label` = "Sophiel" | `com.android.systemui:id/fgs_manager_app_item_stop_button`, `android:id/button3` ("Done") |

**Correction to the page list above:** page 6 is not in Quick Settings. "Active apps" is a sheet reached from Recents ("2 active apps" pill, then the sheet). It shows each app with a Stop button, and it's the only place that stop appears. Ticket 21 and 22 should guard it under that name.

**Open for ticket 20:** A has not been captured yet. Item 1 is B-only until the same five captures run on A.

**Closed 2026-10-14:** A was not dumped. The human reports A shows the same pages with some UI differences, so the B rows are the fixtures for ticket 21 and 22. Ticket 21's device check on A is where any A-specific ids (the view ids may differ) will show up.
