package com.example.tools

import com.example.accessibility.AnisaHandsEngine
import com.example.accessibility.ScrollDirection
import com.example.network.GeminiParametersSchema
import com.example.network.GeminiSchemaProperty

class OpenAppTool(private val handsEngine: AnisaHandsEngine) : AnisaTool {
    override val name: String = "open_app"
    override val description: String = "Launch an application on the user's phone, such as YouTube, Facebook, Chrome, Camera, WhatsApp, Settings, etc."
    override val parametersSchema: GeminiParametersSchema = GeminiParametersSchema(
        type = "OBJECT",
        properties = mapOf(
            "app_name" to GeminiSchemaProperty("STRING", "Name or package of the app, e.g. 'youtube', 'facebook', 'chrome', 'camera', 'whatsapp'")
        ),
        required = listOf("app_name")
    )

    override suspend fun execute(args: Map<String, Any?>): Map<String, Any?> {
        val app = (args["app_name"] as? String) ?: "youtube"
        val result = handsEngine.openApp(app)
        return mapOf(
            "status" to if (result.success) "success" else "error",
            "message" to result.message,
            "app" to app
        )
    }
}

class ClickElementTool(private val handsEngine: AnisaHandsEngine) : AnisaTool {
    override val name: String = "click_screen_element"
    override val description: String = "Tap on a specific button, icon, tab, or text label visible on the phone screen."
    override val parametersSchema: GeminiParametersSchema = GeminiParametersSchema(
        type = "OBJECT",
        properties = mapOf(
            "target" to GeminiSchemaProperty("STRING", "The text label, button name, or description to tap on, e.g. 'Search', 'Play', 'Subscribe', 'Comments'")
        ),
        required = listOf("target")
    )

    override suspend fun execute(args: Map<String, Any?>): Map<String, Any?> {
        val target = (args["target"] as? String) ?: ""
        val result = handsEngine.click(target)
        return mapOf(
            "status" to if (result.success) "success" else "error",
            "message" to result.message
        )
    }
}

class ClickAtCoordinateTool(private val handsEngine: AnisaHandsEngine) : AnisaTool {
    override val name: String = "click_at_coordinate"
    override val description: String = "Tap at exact screen coordinates (x, y)."
    override val parametersSchema: GeminiParametersSchema = GeminiParametersSchema(
        type = "OBJECT",
        properties = mapOf(
            "x" to GeminiSchemaProperty("STRING", "X pixel coordinate"),
            "y" to GeminiSchemaProperty("STRING", "Y pixel coordinate")
        ),
        required = listOf("x", "y")
    )

    override suspend fun execute(args: Map<String, Any?>): Map<String, Any?> {
        val x = (args["x"] as? String)?.toFloatOrNull() ?: 500f
        val y = (args["y"] as? String)?.toFloatOrNull() ?: 500f
        val result = handsEngine.clickAt(x, y)
        return mapOf(
            "status" to if (result.success) "success" else "error",
            "message" to result.message
        )
    }
}

class ScrollScreenTool(private val handsEngine: AnisaHandsEngine) : AnisaTool {
    override val name: String = "scroll_screen"
    override val description: String = "Scroll the active screen up, down, left, or right (e.g. scrolling Facebook, Instagram, feed, webpage)."
    override val parametersSchema: GeminiParametersSchema = GeminiParametersSchema(
        type = "OBJECT",
        properties = mapOf(
            "direction" to GeminiSchemaProperty("STRING", "'down' (to see lower content), 'up', 'left', or 'right'")
        ),
        required = listOf("direction")
    )

    override suspend fun execute(args: Map<String, Any?>): Map<String, Any?> {
        val dirStr = (args["direction"] as? String)?.lowercase() ?: "down"
        val dir = when (dirStr) {
            "up" -> ScrollDirection.UP
            "down" -> ScrollDirection.DOWN
            "left" -> ScrollDirection.LEFT
            "right" -> ScrollDirection.RIGHT
            else -> ScrollDirection.DOWN
        }
        val result = handsEngine.scroll(dir)
        return mapOf(
            "status" to if (result.success) "success" else "error",
            "message" to result.message
        )
    }
}

class TypeTextTool(private val handsEngine: AnisaHandsEngine) : AnisaTool {
    override val name: String = "type_text"
    override val description: String = "Type text into a search bar, text box, or message input on the screen."
    override val parametersSchema: GeminiParametersSchema = GeminiParametersSchema(
        type = "OBJECT",
        properties = mapOf(
            "text" to GeminiSchemaProperty("STRING", "The text to type"),
            "target_field" to GeminiSchemaProperty("STRING", "Optional name or label of the field to type into, e.g. 'Search'")
        ),
        required = listOf("text")
    )

    override suspend fun execute(args: Map<String, Any?>): Map<String, Any?> {
        val text = (args["text"] as? String) ?: ""
        val target = args["target_field"] as? String
        val result = handsEngine.typeText(text, target)
        return mapOf(
            "status" to if (result.success) "success" else "error",
            "message" to result.message
        )
    }
}

class GlobalSystemKeyTool(private val handsEngine: AnisaHandsEngine) : AnisaTool {
    override val name: String = "press_system_key"
    override val description: String = "Press a phone navigation or hardware button: 'back', 'home', 'recents', 'notifications'."
    override val parametersSchema: GeminiParametersSchema = GeminiParametersSchema(
        type = "OBJECT",
        properties = mapOf(
            "key" to GeminiSchemaProperty("STRING", "'back', 'home', 'recents', or 'notifications'")
        ),
        required = listOf("key")
    )

    override suspend fun execute(args: Map<String, Any?>): Map<String, Any?> {
        val key = (args["key"] as? String) ?: "back"
        val result = handsEngine.pressGlobal(key)
        return mapOf(
            "status" to if (result.success) "success" else "error",
            "message" to result.message
        )
    }
}

class ReadScreenUiTool(private val handsEngine: AnisaHandsEngine) : AnisaTool {
    override val name: String = "read_screen_ui"
    override val description: String = "Inspect and read the phone's current active screen: discovers what app is open, what buttons, icons, and text are visible."
    override val parametersSchema: GeminiParametersSchema = GeminiParametersSchema(
        type = "OBJECT",
        properties = mapOf()
    )

    override suspend fun execute(args: Map<String, Any?>): Map<String, Any?> {
        val summary = handsEngine.readScreen()
        return mapOf(
            "status" to "success",
            "screen_summary" to summary
        )
    }
}

class ExecutePhoneTaskTool(private val handsEngine: AnisaHandsEngine) : AnisaTool {
    override val name: String = "execute_phone_task"
    override val description: String = "Perform end-to-end multi-step phone automation, such as opening YouTube and playing a video, or opening Facebook and scrolling the feed."
    override val parametersSchema: GeminiParametersSchema = GeminiParametersSchema(
        type = "OBJECT",
        properties = mapOf(
            "task_type" to GeminiSchemaProperty("STRING", "'youtube_play', 'facebook_scroll', or 'open_and_click'"),
            "query" to GeminiSchemaProperty("STRING", "Search query or target name, e.g. 'Arijit Singh songs', 'Relaxing lofi', 'Feed'")
        ),
        required = listOf("task_type")
    )

    override suspend fun execute(args: Map<String, Any?>): Map<String, Any?> {
        val type = (args["task_type"] as? String)?.lowercase() ?: "youtube_play"
        val query = (args["query"] as? String) ?: "popular music"

        val result = when (type) {
            "youtube_play", "youtube" -> handsEngine.executeYouTubeSearchAndPlay(query)
            "facebook_scroll", "facebook" -> handsEngine.executeFacebookScroll(3)
            else -> handsEngine.executeYouTubeSearchAndPlay(query)
        }

        return mapOf(
            "status" to if (result.success) "success" else "error",
            "message" to result.message,
            "steps" to result.stepsCompleted
        )
    }
}
