package com.phantom.ghost.actions

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Optional user-enabled navigation helper. It does not read or store screen text or notifications. */
class PhantomAccessibilityService : AccessibilityService() {
    private data class PendingSearch(val packageName: String, val query: String, val expiresAt: Long)
    @Volatile private var pendingSearch: PendingSearch? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Observe only event type and package; do not retain event text or dump the view tree.
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) tryApplyPendingSearch()
    }

    override fun onInterrupt() { pendingSearch = null }

    override fun onDestroy() {
        if (activeService === this) activeService = null
        pendingSearch = null
        super.onDestroy()
    }

    /** Must be called only after the app has obtained an explicit user confirmation. */
    fun requestOneShotSearch(packageName: String, query: String): Boolean {
        if (packageName !in APPROVED_SEARCH_PACKAGES || query.isBlank() || query.length > 200) return false
        pendingSearch = PendingSearch(packageName, query, System.currentTimeMillis() + 10_000L)
        mainHandler.post { tryApplyPendingSearch() }
        return true
    }

    private fun tryApplyPendingSearch() {
        val request = pendingSearch ?: return
        if (System.currentTimeMillis() > request.expiresAt) { pendingSearch = null; return }
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != request.packageName) return
        val nodes = sequenceOf("Search", "Search here", "Search maps")
            .flatMap { root.findAccessibilityNodeInfosByText(it).orEmpty().asSequence() }
            .distinctBy { it.hashCode() }
        val field = nodes.firstOrNull { it.isEditable && it.isEnabled } ?: return
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, request.query)
        }
        if (!field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) return
        if (field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) pendingSearch = null
    }

    companion object {
        private val APPROVED_SEARCH_PACKAGES = setOf(
            "com.android.chrome",
            "com.google.android.apps.maps",
            "com.google.android.googlequicksearchbox"
        )
        @Volatile private var activeService: PhantomAccessibilityService? = null

        fun requestSearchAfterUserConfirmation(packageName: String, query: String): Boolean =
            activeService?.requestOneShotSearch(packageName, query) ?: false
    }
}
