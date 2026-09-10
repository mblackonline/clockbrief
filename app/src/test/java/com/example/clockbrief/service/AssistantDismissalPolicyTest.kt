package com.example.clockbrief.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantDismissalPolicyTest {
    private val policy = AssistantDismissalPolicy()

    private fun openAssistant(): Boolean = policy.onWindowChanged(
        AssistantDismissalPolicy.ASSISTANT_PACKAGE,
        AssistantDismissalPolicy.ASSISTANT_ACTIVITY
    )

    private fun leaveClock() {
        policy.onClockFocusChanged(true)
        policy.onClockFocusChanged(false)
    }

    @Test fun assistantOpenedOverClockCanDismissOnlyOnce() {
        leaveClock()
        assertTrue(openAssistant())
        assertTrue(policy.consumeDismissal())
        assertFalse(openAssistant())
        assertFalse(policy.consumeDismissal())
    }

    @Test fun clockRegainingFocusBlocksBackEvenWithoutAccessibilityEvent() {
        leaveClock()
        assertTrue(openAssistant())
        policy.onClockFocusChanged(true)
        assertFalse(policy.consumeDismissal())
    }

    @Test fun assistantCannotArmWhileClockHasFocus() {
        policy.onClockFocusChanged(true)
        assertFalse(openAssistant())
        assertFalse(policy.consumeDismissal())
    }

    @Test fun assistantOpenedWithoutClockSessionCannotDismiss() {
        assertFalse(openAssistant())
        assertFalse(policy.consumeDismissal())
    }

    @Test fun switchingToAnotherAppCancelsSession() {
        leaveClock()
        assertTrue(openAssistant())
        assertFalse(policy.onWindowChanged("com.android.settings", "Settings"))
        assertFalse(policy.consumeDismissal())
        assertFalse(openAssistant())
    }

    @Test fun otherGoogleScreensCannotArmDismissal() {
        leaveClock()
        assertFalse(policy.onWindowChanged(AssistantDismissalPolicy.ASSISTANT_PACKAGE, "SearchActivity"))
        assertFalse(policy.consumeDismissal())
    }

    @Test fun unknownWindowCancelsPendingDismissal() {
        leaveClock()
        assertTrue(openAssistant())
        assertFalse(policy.onWindowChanged(null, null))
        assertFalse(policy.consumeDismissal())
    }

    @Test fun returningToClockAllowsANewAssistantSession() {
        leaveClock()
        assertTrue(openAssistant())
        assertTrue(policy.consumeDismissal())
        leaveClock()
        assertTrue(openAssistant())
        assertTrue(policy.consumeDismissal())
    }

    @Test fun serviceInterruptionOrActivityDestructionCancelsDismissal() {
        leaveClock()
        assertTrue(openAssistant())
        policy.cancelSession()
        assertFalse(policy.consumeDismissal())
    }
}
