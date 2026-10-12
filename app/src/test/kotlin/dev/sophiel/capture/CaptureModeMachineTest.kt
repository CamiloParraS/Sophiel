package dev.sophiel.capture

import dev.sophiel.capture.CaptureMode.ACCESSIBILITY
import dev.sophiel.capture.CaptureMode.NONE
import dev.sophiel.capture.CaptureMode.SCREEN_SHARE
import dev.sophiel.capture.ModeEvent.ScreenOff
import dev.sophiel.capture.ModeEvent.ScreenOn
import dev.sophiel.capture.ModeEvent.ServiceBound
import dev.sophiel.capture.ModeEvent.ServiceUnbound
import dev.sophiel.capture.ModeEvent.ShareEnded
import dev.sophiel.capture.ModeEvent.ShareStarted
import dev.sophiel.capture.ModeEvent.Stop
import dev.sophiel.capture.ModeEvent.SwitchOff
import org.junit.Assert.assertEquals
import org.junit.Test

/** One test per arrow of the SPEC.md §4.7 diagram (D54). */
class CaptureModeMachineTest {
    private fun run(s: ModeState, vararg e: ModeEvent) = e.fold(s) { acc, ev -> CaptureModeMachine.reduce(acc, ev) }

    private val sharing = ModeState(protecting = true, mode = SCREEN_SHARE, screenOn = true, serviceBound = true, keepProtecting = true)
    private val idle = ModeState()

    @Test
    fun startWithConsentIsScreenShareAndProtecting() {
        assertEquals(sharing, run(idle.copy(serviceBound = true), ShareStarted))
    }

    @Test
    fun shareEndsWithScreenOnFallsToAccessibility() {
        assertEquals(ACCESSIBILITY, run(sharing, ShareEnded).mode)
        assertEquals(true, run(sharing, ShareEnded).protecting)
    }

    @Test
    fun shareEndsWithScreenOffIsNoneButStillProtecting() { // API 35+: the share dies at screen-off
        val s = run(sharing, ScreenOff, ShareEnded)
        assertEquals(NONE, s.mode)
        assertEquals(true, s.protecting)
        assertEquals(ACCESSIBILITY, run(s, ScreenOn).mode)
    }

    @Test
    fun accessibilityScreenOffStopsShotsAndScreenOnResumes() {
        val acc = run(sharing, ShareEnded)
        val off = run(acc, ScreenOff)
        assertEquals(NONE, off.mode)
        assertEquals(ACCESSIBILITY, run(off, ScreenOn).mode)
    }

    @Test
    fun belowApi35ScreenOffKeepsTheShare() { // D55: frames pause, the mode does not change
        val s = run(sharing, ScreenOff)
        assertEquals(SCREEN_SHARE, s.mode)
        assertEquals(SCREEN_SHARE, run(s, ScreenOn).mode)
    }

    @Test
    fun freshConsentFromAccessibilityOrNoneReturnsToScreenShare() {
        assertEquals(SCREEN_SHARE, run(sharing, ShareEnded, ShareStarted).mode)
        assertEquals(SCREEN_SHARE, run(sharing, ShareEnded, ScreenOff, ShareStarted).mode)
    }

    @Test
    fun serviceUnboundWithNoShareIsNoneAndStaysProtecting() { // the Sin protección stretch
        val s = run(sharing, ShareEnded, ServiceUnbound)
        assertEquals(NONE, s.mode)
        assertEquals(true, s.protecting)
        assertEquals(NONE, run(s, ScreenOff, ScreenOn).mode) // nothing to resume with
    }

    @Test
    fun serviceBindingResumesAccessibility() { // reboot, first unlock
        val s = run(sharing, ShareEnded, ServiceUnbound)
        assertEquals(ACCESSIBILITY, run(s, ServiceBound).mode)
        // Screen off at bind time: wait for screen on.
        assertEquals(NONE, run(s, ScreenOff, ServiceBound).mode)
    }

    @Test
    fun serviceUnboundDuringAShareDoesNotEndTheShare() {
        assertEquals(SCREEN_SHARE, run(sharing, ServiceUnbound).mode)
    }

    @Test
    fun shareEndingWithoutTheServiceOrWithSwitchOffBehavesAsM6() {
        // Switch off: protection ends with the share.
        val off = run(sharing.copy(keepProtecting = false), ShareEnded)
        assertEquals(NONE, off.mode)
        assertEquals(false, off.protecting)
        // Service unbound, switch on: protection stays on, Sin protección until the next Start.
        val unbound = run(sharing.copy(serviceBound = false), ShareEnded)
        assertEquals(NONE, unbound.mode)
        assertEquals(true, unbound.protecting)
    }

    @Test
    fun switchOffInAccessibilityEndsProtectionButNotAShare() {
        val s = run(sharing, ShareEnded, SwitchOff)
        assertEquals(NONE, s.mode)
        assertEquals(false, s.protecting)
        assertEquals(SCREEN_SHARE, run(sharing, SwitchOff).mode)
        assertEquals(false, run(sharing, SwitchOff, ShareEnded).protecting)
    }

    @Test
    fun onlyPinStopTurnsProtectionOffFromAnyMode() {
        for (s in listOf(sharing, run(sharing, ShareEnded), run(sharing, ShareEnded, ScreenOff))) {
            val stopped = run(s, Stop)
            assertEquals(NONE, stopped.mode)
            assertEquals(false, stopped.protecting)
        }
        // Not protecting: nothing resumes by itself.
        assertEquals(NONE, run(run(sharing, Stop), ServiceBound, ScreenOn).mode)
    }
}
