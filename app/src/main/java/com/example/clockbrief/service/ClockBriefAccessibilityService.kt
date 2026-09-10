package com.example.clockbrief.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ClockBriefAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var dismissJob: Job? = null

    companion object {
        private const val TAG = "ClockBriefA11y"
        private const val AUTO_DISMISS_DELAY_MS = 30_000L
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        dismissJob?.cancel()
        dismissJob = null
        val shouldArm = ClockAssistantSession.policy.onWindowChanged(
            event.packageName?.toString(), event.className?.toString()
        )
        Log.d(TAG, "Window package=${event.packageName} class=${event.className} armed=$shouldArm")
        if (shouldArm) {
            dismissJob = serviceScope.launch {
                delay(AUTO_DISMISS_DELAY_MS)
                if (ClockAssistantSession.policy.consumeDismissal()) {
                    val dispatched = performGlobalAction(GLOBAL_ACTION_BACK)
                    Log.d(TAG, "Assistant dismissal dispatched=$dispatched")
                } else {
                    Log.d(TAG, "Skipped dismissal: session no longer eligible")
                }
                dismissJob = null
            }
        }
    }

    override fun onInterrupt() {
        ClockAssistantSession.policy.cancelSession()
        dismissJob?.cancel()
        dismissJob = null
    }

    override fun onDestroy() {
        ClockAssistantSession.policy.cancelSession()
        serviceScope.cancel()
        super.onDestroy()
        dismissJob?.cancel()
        dismissJob = null
    }
}
