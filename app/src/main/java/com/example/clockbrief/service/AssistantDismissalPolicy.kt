package com.example.clockbrief.service

/** Main-thread session state shared by the activity and accessibility service. */
internal object ClockAssistantSession {
    val policy = AssistantDismissalPolicy()
}

internal class AssistantDismissalPolicy {
    companion object {
        const val CLOCK_PACKAGE = "com.example.clockbrief"
        const val ASSISTANT_PACKAGE = "com.google.android.googlequicksearchbox"
        // Confirmed on the test tablet. Unknown assistant surfaces fail closed.
        const val ASSISTANT_ACTIVITY =
            "com.google.android.apps.search.assistant.surfaces.voice.ui.host.activity.defaultactivity.FragmentHostDefaultActivity"
    }

    private var clockHasFocus = false
    private var sessionAvailable = false
    private var armed = false

    fun onClockFocusChanged(hasFocus: Boolean) {
        clockHasFocus = hasFocus
        if (hasFocus) {
            sessionAvailable = true
            armed = false
        }
    }

    fun onWindowChanged(packageName: String?, className: String?): Boolean {
        armed = false
        if (packageName == CLOCK_PACKAGE) return false
        if (packageName != ASSISTANT_PACKAGE || className != ASSISTANT_ACTIVITY) {
            cancelSession()
            return false
        }
        armed = sessionAvailable && !clockHasFocus
        return armed
    }

    /** Consume before sending Back so dismissal events cannot arm a second action. */
    fun consumeDismissal(): Boolean {
        val allowed = armed && sessionAvailable && !clockHasFocus
        cancelSession()
        return allowed
    }

    fun cancelSession() {
        armed = false
        sessionAvailable = false
    }
}
