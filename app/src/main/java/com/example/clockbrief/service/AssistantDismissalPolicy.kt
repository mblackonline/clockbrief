package com.example.clockbrief.service

/** Main-thread state shared by the clock activity and accessibility service. */
internal object ClockAssistantSession {
    val policy = AssistantDismissalPolicy()
}

/** Tracks a foreground app over time, independent of accessibility event/window IDs. */
internal class AssistantDismissalPolicy {
    companion object {
        const val CLOCK_PACKAGE = "com.example.clockbrief"
        const val ASSISTANT_PACKAGE = "com.google.android.googlequicksearchbox"
        const val DISMISS_AFTER_MS = 90_000L
    }

    private var clockHasFocus = false
    private var sessionAvailable = false
    private var assistantStartedAt: Long? = null
    val isCountingDown: Boolean get() = assistantStartedAt != null

    fun onClockFocusChanged(hasFocus: Boolean) {
        clockHasFocus = hasFocus
        if (hasFocus) {
            sessionAvailable = true
            assistantStartedAt = null
        }
    }

    /** Called with the currently focused window's owner, never an event's sender. */
    fun observeFocusedPackage(packageName: String?, elapsedMillis: Long): Boolean {
        if (clockHasFocus || packageName == CLOCK_PACKAGE) {
            if (packageName == CLOCK_PACKAGE || clockHasFocus) sessionAvailable = true
            assistantStartedAt = null
            return false
        }
        // Missing metadata and temporary system surfaces cannot trigger Back.
        if (packageName == null || packageName == "com.android.systemui") return false
        if (packageName != ASSISTANT_PACKAGE) {
            cancelSession()
            return false
        }
        if (!sessionAvailable) return false
        val startedAt = assistantStartedAt
        if (startedAt == null) {
            assistantStartedAt = elapsedMillis
            return false
        }
        if (elapsedMillis - startedAt < DISMISS_AFTER_MS) return false
        // Consume before Back: the same session can never dispatch it twice.
        cancelSession()
        return true
    }

    fun cancelSession() {
        sessionAvailable = false
        assistantStartedAt = null
    }
}
