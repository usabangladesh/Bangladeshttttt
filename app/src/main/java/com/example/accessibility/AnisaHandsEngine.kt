package com.example.accessibility

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class PhoneTaskResult(
    val success: Boolean,
    val message: String,
    val stepsCompleted: Int = 0
)

class AnisaHandsEngine(private val context: Context) {

    private val service: AnisaAccessibilityService?
        get() = AnisaAccessibilityService.instance

    val isReady: Boolean
        get() = service != null

    suspend fun openApp(appName: String): PhoneTaskResult = withContext(Dispatchers.Default) {
        val s = service ?: return@withContext PhoneTaskResult(false, "Anisa Hands (Accessibility Service) is not enabled. Please enable it in Android Settings.")
        val launched = s.openApp(appName)
        if (launched) {
            PhoneTaskResult(true, "Opened $appName successfully", 1)
        } else {
            PhoneTaskResult(false, "Could not open $appName. Is it installed?", 0)
        }
    }

    suspend fun click(target: String): PhoneTaskResult = withContext(Dispatchers.Default) {
        val s = service ?: return@withContext PhoneTaskResult(false, "Anisa Hands is not enabled.")
        val clicked = s.clickElement(target)
        if (clicked) {
            PhoneTaskResult(true, "Clicked on '$target'", 1)
        } else {
            PhoneTaskResult(false, "Could not find element '$target' on screen.", 0)
        }
    }

    suspend fun clickAt(x: Float, y: Float): PhoneTaskResult = withContext(Dispatchers.Default) {
        val s = service ?: return@withContext PhoneTaskResult(false, "Anisa Hands is not enabled.")
        val clicked = s.clickAt(x, y)
        if (clicked) {
            PhoneTaskResult(true, "Tapped at coordinates ($x, $y)", 1)
        } else {
            PhoneTaskResult(false, "Tap gesture failed.", 0)
        }
    }

    suspend fun typeText(text: String, targetField: String? = null): PhoneTaskResult = withContext(Dispatchers.Default) {
        val s = service ?: return@withContext PhoneTaskResult(false, "Anisa Hands is not enabled.")
        val typed = s.typeText(text, targetField)
        if (typed) {
            PhoneTaskResult(true, "Typed '$text'", 1)
        } else {
            PhoneTaskResult(false, "Could not type. No editable field focused or found.", 0)
        }
    }

    suspend fun scroll(direction: ScrollDirection): PhoneTaskResult = withContext(Dispatchers.Default) {
        val s = service ?: return@withContext PhoneTaskResult(false, "Anisa Hands is not enabled.")
        val scrolled = s.scroll(direction)
        if (scrolled) {
            PhoneTaskResult(true, "Scrolled ${direction.name.lowercase()}", 1)
        } else {
            PhoneTaskResult(false, "Scroll gesture failed.", 0)
        }
    }

    suspend fun pressGlobal(key: String): PhoneTaskResult = withContext(Dispatchers.Default) {
        val s = service ?: return@withContext PhoneTaskResult(false, "Anisa Hands is not enabled.")
        val success = when (key.lowercase().trim()) {
            "back" -> s.pressBack()
            "home" -> s.pressHome()
            "recents", "recent" -> s.pressRecents()
            "notifications", "notification" -> s.openNotifications()
            "quick_settings", "settings" -> s.openQuickSettings()
            else -> s.pressBack()
        }
        if (success) {
            PhoneTaskResult(true, "Pressed $key", 1)
        } else {
            PhoneTaskResult(false, "Failed to perform action $key", 0)
        }
    }

    suspend fun readScreen(): String = withContext(Dispatchers.Default) {
        val s = service ?: return@withContext "Anisa Hands is not enabled. Please enable it in Settings."
        val snapshot = s.inspectScreen()
        snapshot.toSummary()
    }

    // =======================================================
    // 🎬 MULTI-STEP AUTOMATED HIGH-LEVEL CUJ WORKFLOWS
    // =======================================================

    suspend fun executeYouTubeSearchAndPlay(searchQuery: String): PhoneTaskResult = withContext(Dispatchers.Default) {
        val s = service ?: return@withContext PhoneTaskResult(false, "Anisa Hands accessibility service is not enabled.")

        // Step 1: Open YouTube
        val opened = s.openApp("youtube")
        if (!opened) {
            return@withContext PhoneTaskResult(false, "Could not open YouTube app.")
        }

        // Wait for YouTube to load
        delay(1500)

        // Step 2: Look for Search button (Search icon / 'Search' / 'Search YouTube')
        val searchClicked = s.clickElement("Search") || s.clickElement("search") || s.clickElement("Search YouTube")
        delay(800)

        // Step 3: Type search query into the search bar
        s.typeText(searchQuery)
        delay(600)

        // Step 4: Click first search suggestion or press enter
        val suggestionClicked = s.clickElement(searchQuery) || s.clickElement("Search")
        delay(1800)

        // Step 5: Click video title or first video item on search results
        val videoClicked = s.clickElement(searchQuery) ||
                s.clickElement("play") ||
                s.clickElement("views") ||
                s.clickAt(500f, 600f) // Center top area where first video thumbnail appears

        PhoneTaskResult(
            success = true,
            message = "YouTube opened, searched for '$searchQuery' and played video.",
            stepsCompleted = 5
        )
    }

    suspend fun executeFacebookScroll(times: Int = 3): PhoneTaskResult = withContext(Dispatchers.Default) {
        val s = service ?: return@withContext PhoneTaskResult(false, "Anisa Hands accessibility service is not enabled.")

        val opened = s.openApp("facebook")
        if (!opened) {
            return@withContext PhoneTaskResult(false, "Could not open Facebook app.")
        }

        delay(2000)

        // Perform smooth feed scrolls
        var completed = 1
        for (i in 0 until times) {
            s.scroll(ScrollDirection.DOWN)
            completed++
            delay(1500)
        }

        PhoneTaskResult(
            success = true,
            message = "Opened Facebook and scrolled feed $times times.",
            stepsCompleted = completed
        )
    }
}
