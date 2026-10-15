package dev.sophiel.guard

import dev.sophiel.log.TamperTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures: ticket 20's B captures (`20-screenshots/p*.xml`), the nodes a search for the service label returns. */
class GuardPagesTest {
    private val label = "Sophiel opaque masks"
    private val settings = "com.android.settings"

    @Test fun `the service page on B is the accessibility target`() {
        val p1 = listOf(
            GuardNode("Sophiel opaque masks", null), // toolbar title
            GuardNode("Sophiel opaque masks", "com.android.settings:id/sesl_switchbar_container"),
            GuardNode("Sophiel opaque masks shortcut", "android:id/title"),
        )
        assertEquals(TamperTarget.ACCESSIBILITY, GuardPages.match(settings, p1, label))
        assertEquals(TamperTarget.ACCESSIBILITY, GuardPages.match("com.samsung.accessibility", p1, label))
    }

    @Test fun `the installed services list, where the label is only a row title, is not guarded`() {
        // Not captured: a Preference row, as the shortcut row on p1 is.
        val list = listOf(GuardNode("Sophiel opaque masks", "android:id/title"))
        assertNull(GuardPages.match(settings, list, label))
    }

    @Test fun `app info and appear on top show the app label, not the service label`() {
        // p2 / p4: "Sophiel" in entity_header_title; a search for the service label finds nothing.
        assertNull(GuardPages.match(settings, emptyList(), label))
        assertNull(GuardPages.match(settings, listOf(GuardNode("Sophiel", "com.android.settings:id/entity_header_title")), label))
    }

    @Test fun `only the settings windows are read`() {
        assertTrue(GuardPages.reads(settings))
        assertTrue(GuardPages.reads("com.samsung.accessibility"))
        assertTrue(GuardPages.reads("com.google.android.packageinstaller"))
        assertTrue(GuardPages.reads("com.android.systemui"))
        assertFalse(GuardPages.reads("com.whatsapp"))
        assertFalse(GuardPages.reads("dev.sophiel"))
        assertNull(GuardPages.match("com.whatsapp", listOf(GuardNode(label, null)), label))
    }

    // Ticket 20's B captures p2..p5. The app label is "Sophiel"; the nodes are a search for it plus the ids below.
    private val header = GuardNode("Sophiel", "com.android.settings:id/entity_header_title")

    @Test fun `app info is the app info target`() {
        val p2 = listOf(header, GuardNode("", "com.android.settings:id/forcestop_button"), GuardNode("", "com.android.settings:id/uninstall_button"))
        assertEquals(TamperTarget.APP_INFO, GuardPages.match(settings, p2, label))
    }

    @Test fun `appear on top is guarded only while granted`() {
        val title = GuardNode("This permission allows an app...", "com.android.settings:id/title")
        val on = listOf(header, title, GuardNode("", "android:id/switch_widget", checked = true))
        val off = listOf(header, title, GuardNode("", "android:id/switch_widget", checked = false))
        assertEquals(TamperTarget.OVERLAY, GuardPages.match(settings, on, label))
        assertNull(GuardPages.match(settings, off, label))
    }

    @Test fun `a settings page that only shows the app header is not guarded`() {
        assertNull(GuardPages.match(settings, listOf(header, GuardNode("", "android:id/switch_widget", checked = true)), label))
    }

    @Test fun `the uninstall dialog for Sophiel is guarded, another app's is not`() {
        val installer = "com.google.android.packageinstaller"
        val mine = listOf(GuardNode("Sophiel", "com.android.packageinstaller:id/app_label"))
        assertEquals(TamperTarget.UNINSTALL, GuardPages.match(installer, mine, label))
        assertNull(GuardPages.match(installer, emptyList(), label))
    }

    @Test fun `active apps is guarded when it lists Sophiel`() {
        val systemui = "com.android.systemui"
        val row = listOf(GuardNode("Sophiel", "com.android.systemui:id/fgs_manager_app_item_label"))
        assertEquals(TamperTarget.ACTIVE_APPS, GuardPages.match(systemui, row, label))
        assertNull(GuardPages.match(systemui, emptyList(), label))
    }
}
