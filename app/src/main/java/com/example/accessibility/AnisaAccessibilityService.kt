package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AnisaAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AnisaHandsService"

        @Volatile
        var instance: AnisaAccessibilityService? = null
            private set

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        private val _currentAppPackage = MutableStateFlow<String?>(null)
        val currentAppPackage: StateFlow<String?> = _currentAppPackage.asStateFlow()

        fun isAccessibilityEnabled(context: Context): Boolean {
            return instance != null
        }

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceConnected.value = true
        Log.i(TAG, "Anisa Hands Accessibility Service connected and ready!")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
            _isServiceConnected.value = false
        }
        Log.i(TAG, "Anisa Hands Accessibility Service disconnected.")
    }

    override fun onInterrupt() {
        Log.w(TAG, "Anisa Hands Accessibility Service interrupted.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.let {
                _currentAppPackage.value = it.toString()
            }
        }
    }

    // ==========================================
    // 👁️ SCREEN / UI VISION INSPECTION
    // ==========================================

    fun inspectScreen(): UiScreenSnapshot {
        val rootNode = rootInActiveWindow ?: return UiScreenSnapshot(
            currentPackage = _currentAppPackage.value,
            windowTitle = null,
            elements = emptyList()
        )

        val elements = mutableListOf<UiElementInfo>()
        traverseNode(rootNode, elements)

        return UiScreenSnapshot(
            currentPackage = rootNode.packageName?.toString() ?: _currentAppPackage.value,
            windowTitle = null,
            elements = elements
        )
    }

    private fun traverseNode(node: AccessibilityNodeInfo?, list: MutableList<UiElementInfo>) {
        if (node == null) return

        val rect = Rect()
        node.getBoundsInScreen(rect)

        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        val resId = node.viewIdResourceName
        val className = node.className?.toString()

        if ((!text.isNullOrBlank() || !desc.isNullOrBlank() || node.isClickable || node.isEditable || node.isScrollable)
            && rect.width() > 0 && rect.height() > 0
        ) {
            list.add(
                UiElementInfo(
                    id = resId,
                    text = text,
                    contentDescription = desc,
                    className = className,
                    bounds = rect,
                    isClickable = node.isClickable,
                    isEditable = node.isEditable,
                    isScrollable = node.isScrollable
                )
            )
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            traverseNode(child, list)
        }
    }

    // ==========================================
    // 🖐️ ANISA'S HANDS: GESTURES & TAP ACTIONS
    // ==========================================

    suspend fun clickAt(x: Float, y: Float, durationMs: Long = 100): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureAsync(gesture)
    }

    suspend fun longPressAt(x: Float, y: Float, durationMs: Long = 800): Boolean {
        return clickAt(x, y, durationMs)
    }

    suspend fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureAsync(gesture)
    }

    suspend fun scroll(direction: ScrollDirection): Boolean {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()

        val midX = width / 2f
        val midY = height / 2f

        return when (direction) {
            ScrollDirection.DOWN -> {
                // Swipe up to scroll down
                swipe(midX, height * 0.75f, midX, height * 0.25f, 400)
            }
            ScrollDirection.UP -> {
                // Swipe down to scroll up
                swipe(midX, height * 0.25f, midX, height * 0.75f, 400)
            }
            ScrollDirection.RIGHT -> {
                // Swipe left to scroll right
                swipe(width * 0.8f, midY, width * 0.2f, midY, 350)
            }
            ScrollDirection.LEFT -> {
                // Swipe right to scroll left
                swipe(width * 0.2f, midY, width * 0.8f, midY, 350)
            }
        }
    }

    private suspend fun dispatchGestureAsync(gesture: GestureDescription): Boolean {
        return suspendCancellableCoroutine { continuation ->
            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    continuation.resume(false)
                }
            }
            val dispatched = dispatchGesture(gesture, callback, null)
            if (!dispatched) {
                continuation.resume(false)
            }
        }
    }

    // ==========================================
    // 🖐️ ANISA'S HANDS: NODE-LEVEL ACTIONS
    // ==========================================

    suspend fun clickElement(query: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val foundNode = findNodeByQuery(root, query.trim().lowercase())
        if (foundNode != null) {
            // If node is directly clickable, click it
            var nodeToClick: AccessibilityNodeInfo? = foundNode
            while (nodeToClick != null && !nodeToClick.isClickable) {
                nodeToClick = nodeToClick.parent
            }
            if (nodeToClick != null && nodeToClick.isClickable) {
                val clicked = nodeToClick.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) return true
            }

            // Fallback: tap center of the element via gesture
            val rect = Rect()
            foundNode.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                return clickAt(rect.exactCenterX(), rect.exactCenterY())
            }
        }
        return false
    }

    private fun findNodeByQuery(root: AccessibilityNodeInfo, query: String): AccessibilityNodeInfo? {
        val text = root.text?.toString()?.lowercase()
        val desc = root.contentDescription?.toString()?.lowercase()
        val id = root.viewIdResourceName?.lowercase()

        if (text?.contains(query) == true || desc?.contains(query) == true || id?.contains(query) == true) {
            return root
        }

        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val match = findNodeByQuery(child, query)
            if (match != null) return match
        }
        return null
    }

    suspend fun typeText(text: String, targetField: String? = null): Boolean {
        val root = rootInActiveWindow ?: return false

        val targetNode: AccessibilityNodeInfo? = if (!targetField.isNullOrBlank()) {
            findNodeByQuery(root, targetField.lowercase())
        } else {
            findFirstEditableNode(root)
        }

        if (targetNode != null) {
            targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            return targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }

        return false
    }

    private fun findFirstEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isEditable) return root

        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val editable = findFirstEditableNode(child)
            if (editable != null) return editable
        }
        return null
    }

    // ==========================================
    // 🧭 GLOBAL HARDWARE & SYSTEM KEYS
    // ==========================================

    fun pressBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun pressHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun pressRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun openNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun openQuickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

    // ==========================================
    // 🚀 APP LAUNCHER
    // ==========================================

    fun openApp(appNameOrPackage: String): Boolean {
        val pm = packageManager
        val query = appNameOrPackage.trim().lowercase()

        val knownPackages = mapOf(
            "youtube" to "com.google.android.youtube",
            "facebook" to "com.facebook.katana",
            "fb" to "com.facebook.katana",
            "chrome" to "com.android.chrome",
            "browser" to "com.android.chrome",
            "whatsapp" to "com.whatsapp",
            "instagram" to "com.instagram.android",
            "camera" to "com.android.camera",
            "settings" to "com.android.settings",
            "maps" to "com.google.android.apps.maps",
            "calculator" to "com.google.android.calculator",
            "spotify" to "com.spotify.music",
            "gmail" to "com.google.android.gm",
            "playstore" to "com.android.vending",
            "play store" to "com.android.vending",
            "telegram" to "org.telegram.messenger"
        )

        val targetPackage = knownPackages[query] ?: if (query.contains(".")) query else null

        if (targetPackage != null) {
            val launchIntent = pm.getLaunchIntentForPackage(targetPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                return true
            }
        }

        // Fallback: search installed apps by label
        try {
            val packages = pm.getInstalledApplications(0)
            for (appInfo in packages) {
                val label = pm.getApplicationLabel(appInfo).toString().lowercase()
                if (label.contains(query) || appInfo.packageName.contains(query)) {
                    val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launchIntent)
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching installed apps: ${e.message}")
        }

        return false
    }
}
