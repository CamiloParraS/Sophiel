package dev.sophiel.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectionStateMachineTest {

    private val idle = ControllerState()

    @Test
    fun `starting from idle requests notifications`() {
        val next = ProjectionStateMachine.reduce(idle, ControllerEvent.StartRequested)

        assertEquals(ControllerPhase.NEED_NOTIFICATIONS, next.phase)
    }

    @Test
    fun `granted notifications move on to overlay, not degraded`() {
        val state = ControllerState(ControllerPhase.NEED_NOTIFICATIONS)

        val next = ProjectionStateMachine.reduce(state, ControllerEvent.NotificationsResult(granted = true))

        assertEquals(ControllerPhase.NEED_OVERLAY, next.phase)
        assertEquals(false, next.degraded)
    }

    @Test
    fun `denied notifications still proceed to overlay, but degraded`() {
        val state = ControllerState(ControllerPhase.NEED_NOTIFICATIONS)

        val next = ProjectionStateMachine.reduce(state, ControllerEvent.NotificationsResult(granted = false))

        assertEquals(ControllerPhase.NEED_OVERLAY, next.phase)
        assertEquals(true, next.degraded)
    }

    @Test
    fun `denied overlay permission blocks, does not proceed to consent`() {
        val state = ControllerState(ControllerPhase.NEED_OVERLAY, degraded = true)

        val next = ProjectionStateMachine.reduce(state, ControllerEvent.OverlayResult(granted = false))

        assertEquals(ControllerPhase.BLOCKED, next.phase)
        assertEquals("degraded flag survives into BLOCKED", true, next.degraded)
    }

    @Test
    fun `granted overlay permission proceeds to consent`() {
        val state = ControllerState(ControllerPhase.NEED_OVERLAY)

        val next = ProjectionStateMachine.reduce(state, ControllerEvent.OverlayResult(granted = true))

        assertEquals(ControllerPhase.NEED_CONSENT, next.phase)
    }

    @Test
    fun `retrying overlay permission from BLOCKED after granting in settings proceeds to consent`() {
        val state = ControllerState(ControllerPhase.BLOCKED)

        val next = ProjectionStateMachine.reduce(state, ControllerEvent.OverlayResult(granted = true))

        assertEquals(ControllerPhase.NEED_CONSENT, next.phase)
    }

    @Test
    fun `cancelling consent returns to idle silently, no block`() {
        val state = ControllerState(ControllerPhase.NEED_CONSENT, degraded = true)

        val next = ProjectionStateMachine.reduce(state, ControllerEvent.ConsentResult(granted = false))

        assertEquals(ControllerPhase.IDLE, next.phase)
        assertEquals("a fresh idle state carries no stale degraded flag", false, next.degraded)
    }

    @Test
    fun `granting consent starts the service`() {
        val state = ControllerState(ControllerPhase.NEED_CONSENT)

        val next = ProjectionStateMachine.reduce(state, ControllerEvent.ConsentResult(granted = true))

        assertEquals(ControllerPhase.STARTING_SERVICE, next.phase)
    }

    @Test
    fun `full happy path reaches RUNNING`() {
        var state = idle
        state = ProjectionStateMachine.reduce(state, ControllerEvent.StartRequested)
        state = ProjectionStateMachine.reduce(state, ControllerEvent.NotificationsResult(granted = true))
        state = ProjectionStateMachine.reduce(state, ControllerEvent.OverlayResult(granted = true))
        state = ProjectionStateMachine.reduce(state, ControllerEvent.ConsentResult(granted = true))
        state = ProjectionStateMachine.reduce(state, ControllerEvent.ServiceStarted)
        state = ProjectionStateMachine.reduce(state, ControllerEvent.ProjectionAcquired)

        assertEquals(ControllerPhase.RUNNING, state.phase)
    }

    @Test
    fun `stop request from running moves to stopping then idle on teardown`() {
        val running = ControllerState(ControllerPhase.RUNNING)

        val stopping = ProjectionStateMachine.reduce(running, ControllerEvent.StopRequested)
        assertEquals(ControllerPhase.STOPPING, stopping.phase)

        val idleAgain = ProjectionStateMachine.reduce(stopping, ControllerEvent.TeardownComplete)
        assertEquals(ControllerPhase.IDLE, idleAgain.phase)
        assertEquals(false, idleAgain.degraded)
    }

    @Test
    fun `teardown returns to idle from any phase the service can fail or be stopped in`() {
        // Missing consent / null projection tear down in ACQUIRING_PROJECTION; screen-off and
        // status-bar revocation tear down without a StopRequested first.
        listOf(ControllerPhase.STARTING_SERVICE, ControllerPhase.ACQUIRING_PROJECTION, ControllerPhase.RUNNING)
            .forEach { phase ->
                val next = ProjectionStateMachine.reduce(ControllerState(phase), ControllerEvent.TeardownComplete)

                assertEquals("from $phase", ControllerState(), next)
            }
    }

    @Test
    fun `an event that does not apply to the current phase is a no-op`() {
        val state = ControllerState(ControllerPhase.NEED_CONSENT)

        val next = ProjectionStateMachine.reduce(state, ControllerEvent.ProjectionAcquired)

        assertEquals(state, next)
    }
}
