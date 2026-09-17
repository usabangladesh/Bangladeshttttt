package com.example.accessibility

import android.graphics.Rect

data class UiElementInfo(
    val id: String?,
    val text: String?,
    val contentDescription: String?,
    val className: String?,
    val bounds: Rect,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean
) {
    val displayName: String
        get() = when {
            !text.isNullOrBlank() -> text
            !contentDescription.isNullOrBlank() -> contentDescription
            !id.isNullOrBlank() -> id.substringAfterLast("/")
            else -> className?.substringAfterLast(".") ?: "Element"
        }

    val centerX: Float
        get() = bounds.exactCenterX()

    val centerY: Float
        get() = bounds.exactCenterY()
}

data class UiScreenSnapshot(
    val currentPackage: String?,
    val windowTitle: String?,
    val elements: List<UiElementInfo>
) {
    val clickableElements: List<UiElementInfo>
        get() = elements.filter { it.isClickable }

    val editableElements: List<UiElementInfo>
        get() = elements.filter { it.isEditable }

    fun findElement(query: String): UiElementInfo? {
        val q = query.trim().lowercase()
        // Exact matches first
        elements.firstOrNull { it.text?.trim()?.equals(q, ignoreCase = true) == true }?.let { return it }
        elements.firstOrNull { it.contentDescription?.trim()?.equals(q, ignoreCase = true) == true }?.let { return it }
        // Contains matches
        elements.firstOrNull { it.text?.lowercase()?.contains(q) == true }?.let { return it }
        elements.firstOrNull { it.contentDescription?.lowercase()?.contains(q) == true }?.let { return it }
        elements.firstOrNull { it.id?.lowercase()?.contains(q) == true }?.let { return it }
        return null
    }

    fun toSummary(): String {
        val visibleLabels = elements.mapNotNull { it.text ?: it.contentDescription }
            .filter { it.isNotBlank() }
            .take(20)
        return "App: $currentPackage | Visible Items: [${visibleLabels.joinToString(", ")}]"
    }
}

enum class ScrollDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT
}
