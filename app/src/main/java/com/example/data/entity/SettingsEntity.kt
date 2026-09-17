package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val speechRate: Float = 1.05f,
    val speechPitch: Float = 1.08f,
    val voiceType: String = "Natural Female",
    val humorLevel: Float = 0.8f, // 0.0 - 1.0
    val sassLevel: Float = 0.7f,  // 0.0 - 1.0
    val verbosity: Float = 0.5f,  // 0.0 - 1.0 (concise to detailed)
    val autoListen: Boolean = true,
    val wakeWordEnabled: Boolean = true,
    val hapticFeedback: Boolean = true,
    val pcDeviceName: String = "Anisa Desktop Agent",
    val pcPairingCode: String = "ANI-7729-PRO",
    val isPcPaired: Boolean = false,
    val isOfflineMode: Boolean = false
)
