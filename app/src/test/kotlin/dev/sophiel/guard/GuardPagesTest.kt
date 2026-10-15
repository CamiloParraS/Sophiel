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
        assertFalse(GuardPages.reads("com.whatsapp"))
        assertFalse(GuardPages.reads("dev.sophiel"))
        assertNull(GuardPages.match("com.whatsapp", listOf(GuardNode(label, null)), label))
    }
}
