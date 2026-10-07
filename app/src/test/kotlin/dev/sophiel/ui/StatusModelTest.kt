package dev.sophiel.ui

import dev.sophiel.capture.ControllerPhase
import dev.sophiel.core.Sensitivity
import dev.sophiel.log.Entry
import dev.sophiel.log.OffReason
import dev.sophiel.settings.ParentPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusModelTest {
    private fun model(
        phase: ControllerPhase = ControllerPhase.IDLE,
        bound: Boolean = true,
        overlay: Boolean = true,
        notifications: Boolean = true,
        last: Entry? = null,
        preset: ParentPreset = ParentPreset.BALANCED,
        sdk: Int = 35,
    ) = StatusModel.of(StatusInput(phase, bound, overlay, notifications, last, preset, Sensitivity.NORMAL, sdk))

    private fun off(reason: String) = Entry(1_000, "OFF", listOf(reason))

    @Test
    fun runningIsProtectedAndItemsThatFailDropOut() {
        val ok = model(ControllerPhase.RUNNING)
        assertEquals(Page.PROTECTED, ok.page)
        assertTrue(ok.missing.isEmpty())
        // The service gets turned off mid-session: its row leaves the collapsed "Todo en orden".
        assertEquals(listOf(Need.SERVICE), model(ControllerPhase.RUNNING, bound = false).missing)
        assertTrue(model(ControllerPhase.STOPPING).stopBusy)
    }

    @Test
    fun withoutTheServiceItStillStartsAndWarnsOfThePartialMask() { // D46
        val m = model(bound = false)
        assertEquals(Page.READY, m.page)
        assertTrue(m.startEnabled)
        assertEquals(StartNote.PARTIAL_MASK, m.note)
        assertEquals(listOf(Need.SERVICE, Need.NOTIFICATIONS), model(bound = false, notifications = false).missing)
        // Turning it on removes the warning.
        assertNull(model(bound = true).note)
    }

    @Test
    fun onlyTheOverlayBlocksStart() {
        val m = model(overlay = false)
        assertEquals(Page.OFF, m.page)
        assertFalse(m.startEnabled)
        assertEquals(StartNote.TURN_ON_OVERLAY, m.note)
        assertEquals(StartNote.TURN_ON_OVERLAY, model(overlay = false, bound = false).note)
        // Notifications are only recommended.
        val n = model(notifications = false)
        assertEquals(Page.READY, n.page)
        assertTrue(n.startEnabled)
        assertNull(n.note)
    }

    @Test
    fun everyPhaseBetweenStartAndRunningIsStarting() {
        listOf(
            ControllerPhase.NEED_NOTIFICATIONS, ControllerPhase.NEED_OVERLAY, ControllerPhase.NEED_CONSENT,
            ControllerPhase.STARTING_SERVICE, ControllerPhase.ACQUIRING_PROJECTION,
        ).forEach { assertEquals(it.name, Page.STARTING, model(it, last = off(OffReason.SYSTEM)).page) }
        assertNull(model(ControllerPhase.NEED_CONSENT).note)
    }

    @Test
    fun seDetuvoFollowsTheLastOffUnlessTheUserStopped() {
        listOf(OffReason.SCREEN_OFF, OffReason.SYSTEM, OffReason.RESTARTED, OffReason.APP_CLOSED).forEach {
            val m = model(last = off(it))
            assertEquals(it, Page.STOPPED, m.page)
            assertEquals(it, m.stopped!!.fields[0])
        }
        assertEquals(Page.READY, model(last = off(OffReason.USER)).page)
        // The next start logs an ON, which ends it.
        assertEquals(Page.READY, model(last = Entry(2_000, "ON", listOf("BALANCED", "NORMAL"))).page)
        // After a force-stop the service is un-ticked: stopped, listed as missing, Start still works (D46).
        val forced = model(bound = false, last = off(OffReason.APP_CLOSED))
        assertEquals(Page.STOPPED, forced.page)
        assertEquals(listOf(Need.SERVICE), forced.missing)
        assertTrue(forced.startEnabled)
        assertEquals(StartNote.PARTIAL_MASK, forced.note)
    }

    @Test
    fun preciseFallbackSaysWhy() {
        assertNull(model(preset = ParentPreset.BALANCED, bound = false).fallback)
        assertNull(model(preset = ParentPreset.PRECISE).fallback)
        assertEquals(Fallback.ACCESSIBILITY_OFF, model(preset = ParentPreset.PRECISE, bound = false).fallback)
        assertEquals(Fallback.ANDROID_BELOW_14, model(preset = ParentPreset.PRECISE, sdk = 33).fallback)
        assertEquals(Fallback.ANDROID_BELOW_14, model(preset = ParentPreset.PRECISE, sdk = 33, bound = false).fallback)
    }
}
