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
import dev.sophiel.capture.ModeEvent.UiOpened
import dev.sophiel.capture.ModeEvent.SwitchOff
import dev.sophiel.capture.ModeEvent.SwitchOn
import dev.sophiel.log.OffReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        val s = run(sharing.copy(shareSurvivesScreenOff = true), ScreenOff)
        assertEquals(SCREEN_SHARE, s.mode)
        assertEquals(SCREEN_SHARE, run(s, ScreenOn).mode)
    }

    @Test
    fun screenOffEndsTheShareWhenItCannotPause() {
        // API 35+: the share ends at screen-off, protection goes on in accessibility at screen-on.
        val b = run(sharing, ScreenOff)
        assertEquals(NONE, b.mode)
        assertEquals(true, b.protecting)
        assertEquals(ACCESSIBILITY, run(b, ScreenOn).mode)
        // Below 35 with nothing to fall back on (switch off, service unbound): the share ends at screen-off as in M6.
        val a = sharing.copy(shareSurvivesScreenOff = true)
        assertEquals(false, run(a.copy(keepProtecting = false), ScreenOff).protecting)
        assertEquals(NONE, run(a.copy(serviceBound = false), ScreenOff).mode)
    }

    @Test
    fun aLateShareEndChangesNothing() { // the system's onStop after the screen-off already ended it
        val acc = run(sharing, ShareEnded)
        assertEquals(acc, run(acc, ShareEnded))
        val off = run(sharing, ScreenOff)
        assertEquals(off, run(off, ShareEnded))
    }

    @Test
    fun switchOnResumesNothingByItselfButAllowsTheFallback() {
        val noKeep = sharing.copy(keepProtecting = false)
        assertEquals(SCREEN_SHARE, run(noKeep, SwitchOn).mode)
        assertEquals(ACCESSIBILITY, run(noKeep, SwitchOn, ShareEnded).mode)
        assertEquals(NONE, run(idle.copy(serviceBound = true, keepProtecting = false), SwitchOn).mode) // not protecting
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
    fun serviceBindingResumesAccessibility() { // reboot, first unlock: protection was persisted on, nothing was unbound
        val s = idle.copy(protecting = true)
        assertEquals(ACCESSIBILITY, run(s, ServiceBound).mode)
        // Screen off at bind time: wait for screen on.
        assertEquals(NONE, run(s, ScreenOff, ServiceBound).mode)
    }

    @Test
    fun turningTheServiceBackOnDoesNotResumeUntilStart() { // the Parent flips it in Settings from the Status row
        val s = run(sharing, ShareEnded, ServiceUnbound)
        assertEquals(NONE, run(s, ServiceBound).mode)
        assertEquals(NONE, run(s, ServiceBound, ScreenOff, ScreenOn).mode)
        assertEquals(false, run(s, ServiceBound).holdsSession)
        // The switch is no Start either.
        assertEquals(NONE, run(s, ServiceBound, SwitchOff, SwitchOn).mode)
        // Start does: and from then on the fallback is back.
        assertEquals(ACCESSIBILITY, run(s, ServiceBound, ShareStarted, ShareEnded).mode)
    }

    @Test
    fun anUnboundServiceFoundWhenTheAppOpensIsTheSameInterruption() { // process died, then the Parent turns it on
        val s = idle.copy(protecting = true)
        assertEquals(NONE, run(s, UiOpened, ServiceBound).mode)
        assertEquals(ACCESSIBILITY, run(s, ServiceBound, UiOpened).mode) // already capturing: nothing to interrupt
        assertEquals(false, run(sharing, UiOpened).interrupted)
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

    /** D39's OFF for the last event of [e] (the Log line [CaptureModeController] writes). */
    private fun offAfter(s: ModeState, vararg e: ModeEvent): String? {
        val old = run(s, *e.dropLast(1).toTypedArray())
        return CaptureModeMachine.offReason(old, CaptureModeMachine.reduce(old, e.last()), e.last())
    }

    @Test
    fun offIsLoggedOnlyWhereProtectionOrTheLastCaptureEnds() {
        assertEquals(OffReason.USER, offAfter(sharing, Stop))
        assertEquals(OffReason.USER, offAfter(sharing, ShareEnded, SwitchOff))
        val switchOff = sharing.copy(keepProtecting = false)
        assertEquals(OffReason.SYSTEM, offAfter(switchOff, ShareEnded))
        assertEquals(OffReason.SCREEN_OFF, offAfter(switchOff, ScreenOff))
        assertNull(offAfter(switchOff, ScreenOff, ShareEnded)) // the late onStop: one OFF, not two
        // Switch on, unbound: protection stays on, so no OFF: a Sin protección stretch instead (D54).
        assertNull(offAfter(sharing.copy(serviceBound = false), ShareEnded))
        assertNull(offAfter(sharing.copy(serviceBound = false, shareSurvivesScreenOff = true), ScreenOff))
        // A fallback, or a paused share (D55): no OFF.
        assertNull(offAfter(sharing, ShareEnded))
        assertNull(offAfter(sharing, ScreenOff, ShareEnded))
        assertNull(offAfter(sharing.copy(shareSurvivesScreenOff = true), ScreenOff))
        assertNull(offAfter(sharing, ShareEnded, ScreenOff))
    }

    @Test
    fun theSessionLivesWhileCapturingOrWaitingForScreenOn() {
        assertEquals(true, run(sharing, ShareEnded).holdsSession)
        assertEquals(true, run(sharing, ShareEnded, ScreenOff).holdsSession) // paused for screen-on
        assertEquals(false, run(sharing, ShareEnded, ServiceUnbound).holdsSession)
        assertEquals(false, run(sharing, Stop).holdsSession)
    }

    /** D54: the slow-mode notice and the Log's "Compartir pantalla detenido" for the last event of [e]. */
    private fun slowAfter(s: ModeState, vararg e: ModeEvent): Boolean {
        val old = run(s, *e.dropLast(1).toTypedArray())
        return CaptureModeMachine.slowMode(old, CaptureModeMachine.reduce(old, e.last()))
    }

    @Test
    fun slowModeIsNoticedOnlyWhenAShareEndsWithTheScreenOnAndAFallback() {
        assertEquals(true, slowAfter(sharing, ShareEnded))
        assertEquals(false, slowAfter(sharing, ScreenOff)) // API 35+: the screen-off ends it, nobody stopped it
        assertEquals(false, slowAfter(sharing, ScreenOff, ShareEnded))
        assertEquals(false, slowAfter(sharing, ShareEnded, ScreenOff)) // already accessibility
        assertEquals(false, slowAfter(sharing.copy(keepProtecting = false), ShareEnded))
        assertEquals(false, slowAfter(sharing.copy(serviceBound = false), ShareEnded))
        assertEquals(false, slowAfter(sharing, Stop))
        assertEquals(false, slowAfter(sharing.copy(shareSurvivesScreenOff = true), ScreenOff)) // D55: paused, not ended
    }

    @Test
    fun aFreshShareFromAccessibilityIsScreenShareAgain() {
        val s = run(sharing, ShareEnded, ShareStarted)
        assertEquals(sharing, s)
        assertEquals(false, CaptureModeMachine.slowMode(run(sharing, ShareEnded), s))
    }

    /** Sin protección (D54): whether the controller opens / ends a stretch after the last event of [e], from a closed / open one. */
    private fun opensAfter(s: ModeState, vararg e: ModeEvent) = CaptureModeMachine.gapOpens(false, run(s, *e))
    private fun endsAfter(s: ModeState, vararg e: ModeEvent) = CaptureModeMachine.gapEnds(true, run(s, *e))

    @Test
    fun serviceOffWithProtectionOnIsAStretchUntilStart() {
        assertEquals(true, opensAfter(sharing, ShareEnded, ServiceUnbound))
        assertEquals(false, endsAfter(sharing, ShareEnded, ServiceUnbound, ServiceBound)) // binding again is no Start
        assertEquals(true, endsAfter(sharing, ShareEnded, ServiceUnbound, ShareStarted))
        assertEquals(true, endsAfter(sharing, ShareEnded, ServiceUnbound, Stop))
        assertEquals(true, endsAfter(sharing, ShareEnded, ServiceUnbound, SwitchOff))
        assertEquals(false, endsAfter(sharing, ShareEnded, ServiceUnbound, ScreenOff)) // one stretch, screen-offs inside it included
    }

    @Test
    fun aShareStillRunningIsNoStretch() {
        assertEquals(false, opensAfter(sharing, ServiceUnbound))
        assertEquals(false, opensAfter(sharing, ShareEnded)) // accessibility took over
        assertEquals(false, opensAfter(sharing, ScreenOff, ShareEnded)) // API 35+: waits for screen-on with a fallback
        assertEquals(false, opensAfter(sharing.copy(keepProtecting = false), ShareEnded)) // switch off: protection ended, an OFF
    }

    @Test
    fun withoutTheServiceEachScreenOffLeavesAStretchFromScreenOn() { // D46
        val a = sharing.copy(serviceBound = false, shareSurvivesScreenOff = true)
        assertEquals(false, opensAfter(a, ScreenOff)) // screen off: nothing uncovered yet
        assertEquals(true, opensAfter(a, ScreenOff, ScreenOn))
        assertEquals(true, opensAfter(sharing.copy(serviceBound = false), ShareEnded)) // chip stop, screen on
        assertEquals(true, endsAfter(a, ScreenOff, ScreenOn, ShareStarted))
    }

    @Test
    fun aProcessBackWithProtectionOnAndNoServiceIsAStretch() { // force-stop: the service is unticked (D41)
        assertEquals(true, opensAfter(idle.copy(protecting = true)))
        assertEquals(true, opensAfter(idle.copy(protecting = true), UiOpened))
        // A kill the system recovers from: the service binds and capture resumes, the recovered stretch ends.
        assertEquals(true, endsAfter(idle.copy(protecting = true, serviceBound = true)))
        assertEquals(false, opensAfter(idle)) // not protecting
        assertEquals(false, opensAfter(idle.copy(protecting = true, screenOn = false))) // reboot before first unlock
    }

    /** "Protección interrumpida · toca para reactivar" after the last event of [e]. */
    private fun noticeAfter(s: ModeState, vararg e: ModeEvent): Boolean {
        val old = run(s, *e.dropLast(1).toTypedArray())
        return CaptureModeMachine.serviceLost(old, CaptureModeMachine.reduce(old, e.last()))
    }

    @Test
    fun theServiceTurningOffWithProtectionOnIsNoticed() {
        assertEquals(true, noticeAfter(sharing, ShareEnded, ServiceUnbound))
        assertEquals(false, noticeAfter(sharing, ServiceUnbound)) // the share goes on; its end posts D21's notice
        assertEquals(false, noticeAfter(sharing, Stop, ServiceUnbound))
    }

    /** D21's resume notice for a share lost with protection on and nothing to fall back on. */
    private fun lostAfter(s: ModeState, vararg e: ModeEvent): Boolean {
        val old = run(s, *e.dropLast(1).toTypedArray())
        return CaptureModeMachine.lostShare(old, CaptureModeMachine.reduce(old, e.last()))
    }

    @Test
    fun aShareLostWithNoFallbackPostsTheResumeNotice() {
        assertEquals(true, lostAfter(sharing.copy(serviceBound = false), ShareEnded))
        assertEquals(true, lostAfter(sharing.copy(serviceBound = false, shareSurvivesScreenOff = true), ScreenOff))
        assertEquals(true, lostAfter(sharing, ServiceUnbound, ShareEnded))
        assertEquals(false, lostAfter(sharing, ShareEnded))
        assertEquals(false, lostAfter(sharing, ScreenOff)) // API 35+: resumes at screen-on
        assertEquals(false, lostAfter(sharing.copy(keepProtecting = false), ShareEnded)) // switch off: the OFF posts it
    }

    @Test
    fun remoteStopEndsProtectionWithReasonRemote() { // D82: a PIN Stop, logged as REMOTE
        val s = run(sharing, ModeEvent.RemoteStop)
        assertEquals(false, s.protecting)
        assertEquals(NONE, s.mode)
        assertEquals(OffReason.REMOTE, offAfter(sharing, ModeEvent.RemoteStop))
        assertNull(offAfter(idle, ModeEvent.RemoteStop))
    }

    @Test
    fun remoteStartIsAccessibilityCaptureOrWaitsForScreenOn() { // D82
        val ready = idle.copy(serviceBound = true, keepProtecting = true)
        assertEquals(ModeState(protecting = true, mode = ACCESSIBILITY, serviceBound = true), run(ready, ModeEvent.RemoteStart))
        val dark = run(ready.copy(screenOn = false), ModeEvent.RemoteStart)
        assertEquals(NONE, dark.mode)
        assertEquals(true, dark.holdsSession)
        assertEquals(ACCESSIBILITY, run(dark, ScreenOn).mode)
        // an interrupted protection (service came back without a Start) resumes
        val interrupted = run(ready.copy(protecting = true, mode = ACCESSIBILITY), ServiceUnbound, ServiceBound)
        assertEquals(true, interrupted.uncovered)
        assertEquals(ACCESSIBILITY, run(interrupted, ModeEvent.RemoteStart).mode)
    }
}
