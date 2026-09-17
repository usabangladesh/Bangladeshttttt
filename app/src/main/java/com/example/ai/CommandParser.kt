package com.example.ai

import com.example.model.AppCommand

object CommandParser {

    fun parse(input: String): AppCommand? {
        val text = input.trim().lowercase()
        if (text.isBlank()) return null

        // Prime Contact commands
        if (text.contains("close friend") || text.contains("mere close friend") || text.contains("close friend ko call")) {
            return AppCommand(AppCommand.PRIME_CALL, mapOf("index" to "0"))
        }
        if (text.contains("second contact") || text.contains("doosre contact")) {
            return AppCommand(AppCommand.PRIME_CALL, mapOf("index" to "1"))
        }
        if (text.contains("meri jaan") || text.contains("my love") || text.contains("love ko message")) {
            return AppCommand(AppCommand.PRIME_MSG, mapOf("index" to "0"))
        }

        // Flashlight / Torch
        if (text.contains("torch on") || text.contains("flashlight on") || text.contains("লাইট জ্বালাও") || text.contains("टॉर्च ऑन")) {
            return AppCommand(AppCommand.FLASHLIGHT_ON)
        }
        if (text.contains("torch off") || text.contains("flashlight off") || text.contains("লাইট নেভাও") || text.contains("টর্চ বন্ধ") || text.contains("टॉर्च बंद")) {
            return AppCommand(AppCommand.FLASHLIGHT_OFF)
        }

        // Volume control
        if (text.contains("volume badhao") || text.contains("volume up") || text.contains("sound up") || text.contains("আওয়াজ বাড়াও")) {
            return AppCommand(AppCommand.VOLUME_UP)
        }
        if (text.contains("volume kam karo") || text.contains("volume down") || text.contains("sound down") || text.contains("আওয়াজ কমাও")) {
            return AppCommand(AppCommand.VOLUME_DOWN)
        }

        // WiFi
        if (text.contains("wifi on") || text.contains("ওয়াইফাই চালু")) {
            return AppCommand(AppCommand.WIFI_ON)
        }
        if (text.contains("wifi off") || text.contains("ওয়াইফাই বন্ধ")) {
            return AppCommand(AppCommand.WIFI_OFF)
        }

        // Bluetooth
        if (text.contains("bluetooth on") || text.contains("ব্লুটুথ চালু")) {
            return AppCommand(AppCommand.BLUETOOTH_ON)
        }
        if (text.contains("bluetooth off") || text.contains("ব্লুটুথ বন্ধ")) {
            return AppCommand(AppCommand.BLUETOOTH_OFF)
        }

        // YouTube Search & Play
        if ((text.contains("youtube") || text.contains("ইউটিউব") || text.contains("यूट्यूब")) &&
            (text.contains("chalao") || text.contains("play") || text.contains("চালাও") || text.contains("sunao") || text.contains("video") || text.contains("গান"))) {
            val query = text
                .replace("youtube", "")
                .replace("ইউটিউব", "")
                .replace("यूट्यूब", "")
                .replace("chalao", "")
                .replace("play", "")
                .replace("চালাও", "")
                .replace("karke", "")
                .replace("open", "")
                .replace("me", "")
                .replace("pe", "")
                .replace("-e", "")
                .trim()
                .ifBlank { "popular music" }
            return AppCommand(AppCommand.YOUTUBE_SEARCH_PLAY, mapOf("query" to query))
        }

        // Close app / Band karo
        if (text.contains("band karo") || text.contains("close ") || text.contains("বন্ধ করো")) {
            val app = text.replace("band karo", "")
                .replace("close", "")
                .replace("বন্ধ করো", "")
                .trim()
            return AppCommand(AppCommand.CLOSE_APP, mapOf("app_name" to app))
        }

        // WhatsApp call / msg
        if (text.contains("whatsapp karo") || text.contains("whatsapp message") || text.contains("হোয়াটসঅ্যাপ করো")) {
            val name = extractName(text, listOf("whatsapp karo", "whatsapp message", "ko message", "ko msg"))
            return AppCommand(AppCommand.WHATSAPP_MSG, mapOf("name" to name))
        }

        // Call [name]
        if (text.contains("call ") || text.contains("ko call karo") || text.contains("কল করো") || text.contains("ফোন করো")) {
            val name = extractName(text, listOf("call", "ko call karo", "কল করো", "ফোন করো", "ko phone lagao"))
            if (name.isNotBlank()) {
                return AppCommand(AppCommand.CALL, mapOf("name" to name))
            }
        }

        // SMS [name]
        if (text.contains("sms") || text.contains("message bhejo") || text.contains("মেসেজ পাঠাও")) {
            val name = extractName(text, listOf("sms bhejo", "message bhejo", "ko sms", "ko message", "মেসেজ পাঠাও"))
            return AppCommand(AppCommand.SMS, mapOf("name" to name))
        }

        // Open App
        if (text.contains("kholo") || text.contains("open ") || text.contains("খোলো") || text.contains("चालू करो")) {
            val app = text.replace("kholo", "")
                .replace("open", "")
                .replace("খোলো", "")
                .replace("চালू करो", "")
                .replace("anisa", "")
                .replace("myra", "")
                .replace(",", "")
                .trim()
            if (app.isNotBlank()) {
                return AppCommand(AppCommand.OPEN_APP, mapOf("app_name" to app))
            }
        }

        return null
    }

    private fun extractName(text: String, keywords: List<String>): String {
        var clean = text
        for (kw in keywords) {
            clean = clean.replace(kw, " ")
        }
        return clean.replace("myra", "")
            .replace("anisa", "")
            .replace("ko", "")
            .replace("ke liye", "")
            .replace("please", "")
            .replace("karo", "")
            .trim()
    }
}
