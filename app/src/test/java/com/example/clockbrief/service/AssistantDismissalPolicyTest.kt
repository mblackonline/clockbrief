package com.example.clockbrief.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantDismissalPolicyTest {
    private val policy = AssistantDismissalPolicy()
    private val assistant = AssistantDismissalPolicy.ASSISTANT_PACKAGE
    private val clock = AssistantDismissalPolicy.CLOCK_PACKAGE

    private fun leaveClock() {
        policy.onClockFocusChanged(true)
        policy.onClockFocusChanged(false)
    }

    @Test fun dismissesAtThirtySecondsWithoutAnyAccessibilityEvents() {
        leaveClock()
        assertFalse(policy.observeFocusedPackage(assistant, 0))
        assertFalse(policy.observeFocusedPackage(assistant, 29_999))
        assertTrue(policy.observeFocusedPackage(assistant, 30_000))
    }

    @Test fun assistantWindowReplacementDoesNotRestartOrCancelTimer() {
        leaveClock()
        // Voice window 481 and activity window 484 have the same live owner.
        assertFalse(policy.observeFocusedPackage(assistant, 0))
        assertFalse(policy.observeFocusedPackage(assistant, 4_000))
        assertTrue(policy.observeFocusedPackage(assistant, 30_000))
    }

    @Test fun sameSessionCannotDismissAgainEvenIfAssistantRemainsVisible() {
        leaveClock()
        policy.observeFocusedPackage(assistant, 0)
        assertTrue(policy.observeFocusedPackage(assistant, 30_000))
        assertFalse(policy.observeFocusedPackage(assistant, 60_000))
        assertFalse(policy.observeFocusedPackage(assistant, 90_000))
    }

    @Test fun clockRegainingFocusPreventsBackWithoutAWindowEvent() {
        leaveClock()
        policy.observeFocusedPackage(assistant, 0)
        policy.onClockFocusChanged(true)
        assertFalse(policy.observeFocusedPackage(assistant, 30_000))
    }

    @Test fun clockObservedAtDeadlinePreventsBack() {
        leaveClock()
        policy.observeFocusedPackage(assistant, 0)
        assertFalse(policy.observeFocusedPackage(clock, 30_000))
    }

    @Test fun otherForegroundAppCancelsSession() {
        leaveClock()
        policy.observeFocusedPackage(assistant, 0)
        assertFalse(policy.observeFocusedPackage("com.android.settings", 5_000))
        assertFalse(policy.observeFocusedPackage(assistant, 30_000))
    }

    @Test fun actualLauncherFocusCancelsSession() {
        leaveClock()
        policy.observeFocusedPackage(assistant, 0)
        assertFalse(policy.observeFocusedPackage("com.sec.android.app.launcher", 5_000))
        assertFalse(policy.observeFocusedPackage(assistant, 30_000))
    }

    @Test fun assistantOpenedWithoutClockDoesNotDismiss() {
        policy.observeFocusedPackage(assistant, 0)
        assertFalse(policy.observeFocusedPackage(assistant, 30_000))
    }

    @Test fun temporarySystemUiDoesNotEraseSessionButCannotReceiveBack() {
        leaveClock()
        assertFalse(policy.observeFocusedPackage("com.android.systemui", 0))
        policy.observeFocusedPackage(assistant, 1_000)
        assertFalse(policy.observeFocusedPackage("com.android.systemui", 31_000))
        assertTrue(policy.observeFocusedPackage(assistant, 32_000))
    }

    @Test fun missingMetadataCannotReceiveBack() {
        leaveClock()
        policy.observeFocusedPackage(assistant, 0)
        assertFalse(policy.observeFocusedPackage(null, 30_000))
        assertTrue(policy.observeFocusedPackage(assistant, 31_000))
    }

    @Test fun returningToClockAllowsANewSessionWithItsOwnDeadline() {
        leaveClock()
        policy.observeFocusedPackage(assistant, 0)
        assertTrue(policy.observeFocusedPackage(assistant, 30_000))
        policy.observeFocusedPackage(clock, 31_000)
        policy.observeFocusedPackage(assistant, 40_000)
        assertFalse(policy.observeFocusedPackage(assistant, 69_999))
        assertTrue(policy.observeFocusedPackage(assistant, 70_000))
    }

    @Test fun interruptOrDestroyCancelsPendingDismissal() {
        leaveClock()
        policy.observeFocusedPackage(assistant, 0)
        policy.cancelSession()
        assertFalse(policy.observeFocusedPackage(assistant, 30_000))
    }
}
