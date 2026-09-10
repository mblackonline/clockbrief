package com.example.clockbrief.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ClockBriefAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var dismissJob: Job? = null

    companion object {
        private const val TAG = "ClockBriefA11y"
        private const val AUTO_DISMISS_DELAY_MS = 30_000L
        private const val OUR_PACKAGE = "com.example.clockbrief"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return

            if (packageName == OUR_PACKAGE) {
                // ClockBrief is back in front: cancel any pending dismiss timer
                dismissJob?.cancel()
                dismissJob = null
            } else {
                // Reset timer so user gets a full 30 seconds after the assistant finishes loading/updating
                dismissJob?.cancel()
                dismissJob = serviceScope.launch {
                    delay(AUTO_DISMISS_DELAY_MS)
                    Log.d(TAG, "Dismissing overlay after timeout")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    dismissJob = null
                }
            }
        }
    }

    override fun onInterrupt() {
        dismissJob?.cancel()
        dismissJob = null
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissJob?.cancel()
        dismissJob = null
    }
}
