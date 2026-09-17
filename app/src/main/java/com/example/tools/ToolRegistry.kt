package com.example.tools

import android.content.Context
import com.example.accessibility.AnisaHandsEngine
import com.example.data.repository.AnisaRepository
import com.example.network.GeminiToolDeclaration
import com.example.notifications.AnisaNotificationManager

class ToolRegistry(
    context: Context,
    repository: AnisaRepository,
    notificationManager: AnisaNotificationManager
) {
    private val tools = mutableMapOf<String, AnisaTool>()
    val handsEngine: AnisaHandsEngine = AnisaHandsEngine(context)
    val androidController: com.example.accessibility.AndroidController = com.example.accessibility.AndroidController(context)

    init {
        register(WeatherTool())
        register(CalculatorTool())
        register(MemoryTool(repository))
        register(TaskPlannerTool(repository))
        register(DeviceStatusTool(context))
        register(ReminderTool(context, notificationManager))

        // 🖐️ Anisa's Hands (Accessibility Service & UI Vision Tools)
        register(OpenAppTool(handsEngine))
        register(ClickElementTool(handsEngine))
        register(ClickAtCoordinateTool(handsEngine))
        register(ScrollScreenTool(handsEngine))
        register(TypeTextTool(handsEngine))
        register(GlobalSystemKeyTool(handsEngine))
        register(ReadScreenUiTool(handsEngine))
        register(ExecutePhoneTaskTool(handsEngine))
    }

    fun register(tool: AnisaTool) {
        tools[tool.name] = tool
    }

    fun getTool(name: String): AnisaTool? = tools[name]

    fun getAllTools(): List<AnisaTool> = tools.values.toList()

    fun getGeminiToolDeclaration(): GeminiToolDeclaration {
        return GeminiToolDeclaration(
            functionDeclarations = tools.values.map { it.toGeminiFunctionDef() }
        )
    }

    suspend fun executeTool(name: String, args: Map<String, Any?>): Map<String, Any?> {
        val tool = tools[name] ?: return mapOf("error" to "Tool '$name' not found", "status" to "error")
        return try {
            tool.execute(args)
        } catch (e: Exception) {
            mapOf("error" to "Tool execution failed: ${e.message}", "status" to "error")
        }
    }
}
