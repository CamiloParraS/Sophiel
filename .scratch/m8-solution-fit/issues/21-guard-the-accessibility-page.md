# 21: Guard Sophiel's accessibility page

**What to build:** The first end-to-end slice of the tamper guard (D56). With a PIN set and no Unlock active, opening Accessibility > Sophiel (or its "Stop Sophiel?" dialog, including Samsung's accessibility app) while the service is on closes the page to Sophiel's PIN pad with the line "Este ajuste está protegido". Back goes Home. A correct PIN starts an Unlock that survives leaving the app (amends D38: today leaving the app locks) and Sophiel steps aside, back to the page, where the Parent can turn the service off. Each blocked attempt lands in the Log as **Intento de apagar**.

**Blocked by:** 20

**Status:** built, awaiting the device check on A (ready-for-human)

- [x] Service config per SPEC §4.1: `typeWindowStateChanged` from all packages (no `packageNames`, no content events), `flagReportViewIds`; node text read only on Settings, package-installer, SystemUI and Samsung-accessibility windows, only to find "Sophiel"
- [x] Page matching is pure logic, JVM-tested on ticket 20's recorded fields (A and B), never class names alone
- [x] Armed only while a PIN exists, no Unlock is active, and what the page turns off is on; the wizard (no PIN yet) is never guarded
- [x] PIN pad with "Este ajuste está protegido"; Back goes Home; a correct PIN starts an Unlock that survives leaving the app and still ends after 2 min or at screen-off; the guard is off while any Unlock is active
- [x] Log kind **Intento de apagar** (time + target); the same target within 60 s merges into one entry with a count, JVM-tested; row in the existing Log ("Otros")
- [x] Copy "y protege los ajustes de Sophiel" in the wizard's accessibility step and the "Máscara completa" Recomendado item (D56); Spanish, English in `values-en/`
- [x] `isAccessibilityTool="true"` only if ticket 20 found B hides the dialog
- [ ] Checked on A: the page closes to the PIN pad, a correct PIN returns to it and the toggle works
- [x] Ends buildable

## Notes (agent, 2026-10-14)

Built in 6cf1989 + review fixes. No device was attached, so the A check is open.

- **Matching:** on a Settings (or `com.samsung.accessibility`) window-state event, `findAccessibilityNodeInfosByText(service label)`, then `GuardPages.match`: the label as a node text whose id is not `android:id/title`. On p1 (B) that is the toolbar title and the main switch bar. The installed-services list (label only as a row title) is assumed, not captured: **check on A that opening the list doesn't fire.** The label is the app's own string, so Settings and the app must use the same language (no per-app language set).
- **Read packages:** only Settings and Samsung accessibility here. The package installer and SystemUI come with ticket 22's pages, so item 1 is complete for this page only.
- **Turning on is never blocked:** the service skips the page it was bound from (the Parent just turned it on there, including in the wizard, where the PIN already exists from step 1) until another window shows.
- **Covered, not closed:** `ui/BlockActivity` (single instance, `taskAffinity=""`, SPEC §4.1 updated) goes over the page, and finishing after a right PIN returns to it. Cancel and Back go Home. It is re-shown on every matching event; only the Log count is debounced (2 s, then the 60 s merge).
- **Unlock:** `extend(survivesLeaving = true)` from the guard. Screen-off ends it (`CaptureModeController`'s receiver), and so does `lockNow()` or the next normal `extend()`.
- **isAccessibilityTool="true"**, per ticket 20's checklist conclusion. Ticket 20 recorded no "Stop Sophiel?" box shown at all, not specifically one hidden from the service.
- **Service description** updated: it said "reads no text and receives no events", which is no longer true.

**For the A check:** guard delay (event → PIN pad), whether the page's title is drawn by the time the event arrives (`ponytail:` in `MaskWindowService.guard`), and Recents → Settings straight after Back.

## Device check on A (human, 2026-10-14)

- Opening accessibility showed the PIN pad. A correct PIN went on as normal; Cancel went to the home screen.
- Recents → Settings after that showed the PIN pad again.
- Guard delay not measured; "pretty much instant".
- Open: whether the pad fired on the Accessibility menu itself or only on Sophiel's page, and whether the switch works after the PIN.
