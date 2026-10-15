package dev.sophiel.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppRulesTest {
    private val apps = listOf(
        AppEntry("com.whatsapp", "WhatsApp"), AppEntry("com.android.chrome", "Chrome"), AppEntry("dev.sophiel", "Sophiel"),
        AppEntry("com.instagram.android", "Instagram"), AppEntry("com.android.camera", "cámara"),
    )

    @Test fun `the list drops excluded apps, pins changed ones first, then sorts by label`() {
        val list = appList(apps, mapOf("com.whatsapp" to AppRule.BLOCKED), excluded = setOf("dev.sophiel"), query = "")
        assertEquals(listOf("WhatsApp", "cámara", "Chrome", "Instagram"), list.map { it.label })
    }

    @Test fun `search matches the label anywhere, any case`() {
        assertEquals(listOf("Instagram"), appList(apps, emptyMap(), emptySet(), " GRAM ").map { it.label })
        assertEquals(listOf("Chrome"), appList(apps, emptyMap(), emptySet(), "CHR").map { it.label })
    }

    private val rules = mapOf("com.gallery" to AppRule.TRUSTED, "com.notes" to AppRule.TRUSTED, "com.launcher" to AppRule.TRUSTED, "com.chat" to AppRule.BLOCKED)
    private fun trusted(vararg front: String?) = trustedInFront(front.toList(), rules, excluded = setOf("com.launcher"))

    @Test fun `trusted only when every app window in front is trusted`() {
        assertEquals(true, trusted("com.gallery"))
        assertEquals(true, trusted("com.gallery", "com.notes")) // split screen or PiP, all trusted
        assertEquals(false, trusted("com.gallery", "com.browser")) // one scanned app keeps the scan
        assertEquals(false, trusted("com.gallery", "com.chat"))
    }

    @Test fun `no app window, an unreadable one, or an excluded app is never trusted`() {
        assertEquals(false, trusted()) // system UI only
        assertEquals(false, trusted("com.gallery", null))
        assertEquals(false, trusted("com.launcher")) // excluded cannot be trusted, whatever is stored
    }
}
