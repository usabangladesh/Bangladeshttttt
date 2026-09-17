package com.example.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import com.example.accessibility.AnisaAccessibilityService
import com.example.accessibility.ScrollDirection

class AccessibilityHelperService {

    companion object {
        val instance: AnisaAccessibilityService?
            get() = AnisaAccessibilityService.instance

        fun isEnabled(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            val expectedComp = ComponentName(context, AnisaAccessibilityService::class.java).flattenToString()
            val expectedShort = ComponentName(context, AnisaAccessibilityService::class.java).flattenToShortString()
            while (colonSplitter.hasNext()) {
                val comp = colonSplitter.next()
                if (comp.equals(expectedComp, ignoreCase = true) || comp.equals(expectedShort, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }

        fun closeCurrentApp(): Boolean {
            val s = instance ?: return false
            return s.pressHome()
        }

        fun goBack(): Boolean {
            val s = instance ?: return false
            return s.pressBack()
        }

        suspend fun clickOnText(text: String): Boolean {
            val s = instance ?: return false
            return s.clickElement(text)
        }

        suspend fun typeText(text: String, targetField: String? = null): Boolean {
            val s = instance ?: return false
            return s.typeText(text, targetField)
        }

        suspend fun scrollDown(): Boolean {
            val s = instance ?: return false
            return s.scroll(ScrollDirection.DOWN)
        }

        suspend fun scrollUp(): Boolean {
            val s = instance ?: return false
            return s.scroll(ScrollDirection.UP)
        }
    }
}
