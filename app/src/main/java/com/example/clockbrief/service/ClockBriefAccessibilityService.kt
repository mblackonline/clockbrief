package com.example.clockbrief.service

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ClockBriefAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var monitorJob: Job? = null

    companion object {
        private const val TAG = "ClockBriefA11y"
        private const val SAMPLE_INTERVAL_MS = 500L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        monitorJob?.cancel()
        Log.d(TAG, "Foreground monitor connected")
        monitorJob = serviceScope.launch {
            var lastPackage: String? = null
            var wasCountingDown = false
            while (isActive) {
                val packageName = focusedPackage()
                val policy = ClockAssistantSession.policy
                val shouldDismiss = policy.observeFocusedPackage(packageName, SystemClock.elapsedRealtime())
                if (packageName != lastPackage || policy.isCountingDown != wasCountingDown) {
                    Log.d(TAG, "Focused package=$packageName countdown=${policy.isCountingDown}")
                    lastPackage = packageName
                    wasCountingDown = policy.isCountingDown
                }
                if (shouldDismiss) {
                    // Recheck the current owner, not the original window ID.
                    if (focusedPackage() == AssistantDismissalPolicy.ASSISTANT_PACKAGE) {
                        val dispatched = performGlobalAction(GLOBAL_ACTION_BACK)
                        Log.d(TAG, "Assistant dismissal dispatched=$dispatched")
                    } else {
                        Log.d(TAG, "Skipped dismissal: focused app changed at deadline")
                    }
                }
                delay(SAMPLE_INTERVAL_MS)
            }
        }
    }

    private fun focusedPackage(): String? {
        return try {
            val focusedWindow = windows.firstOrNull { it.isFocused } ?: return null
            val root = focusedWindow.root ?: return null
            try {
                // Only owner metadata is accessed. Do not read text or traverse children.
                root.packageName?.toString()
            } finally {
                @Suppress("DEPRECATION")
                root.recycle()
            }
        } catch (error: RuntimeException) {
            Log.d(TAG, "Focused window unavailable: ${error.javaClass.simpleName}")
            null
        }
    }

    // Event ordering is unreliable on the tablet. The monitor uses live window state.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        ClockAssistantSession.policy.cancelSession()
    }

    override fun onDestroy() {
        ClockAssistantSession.policy.cancelSession()
        serviceScope.cancel()
        monitorJob = null
        super.onDestroy()
    }
}
