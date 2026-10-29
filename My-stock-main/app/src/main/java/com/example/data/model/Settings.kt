package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey
    val key: String,
    val value: String
)

data class ShopSettings(
    val shopName: String = "Sip Shop",
    val currencyMode: String = "USD", // "USD" or "KHR"
    val khrExchangeRate: Double = 4100.0,
    val monthlySalesGoal: Double = 1500.0,
    val masterVolume: Float = 1.0f,
    val masterSoundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val cashierName: String = "Cashier 1",
    val themePreference: String = "SYSTEM", // "SYSTEM", "LIGHT", "DARK"
    val fontScale: Float = 1.0f,    // text size multiplier (0.8 - 1.4)
    val spacingScale: Float = 1.0f, // layout / spacing multiplier (0.8 - 1.3)
    val backgroundColor: String = "", // page background, "#RRGGBB" ("" = default)
    val blockColor: String = "",      // blocks: cards, bars, dialogs ("" = default)
    val textColor: String = "",       // text colour ("" = default)
    val soundEvents: Map<SoundEvent, SoundEventConfig> = defaultSoundConfigs()
)

data class SoundEventConfig(
    val enabled: Boolean = true,
    val soundType: SoundType = SoundType.POP,
    val volume: Float = 0.8f
)

fun defaultSoundConfigs(): Map<SoundEvent, SoundEventConfig> = mapOf(
    SoundEvent.ADD_DRINK to SoundEventConfig(enabled = true, soundType = SoundType.POP, volume = 0.8f),
    SoundEvent.REMOVE_DRINK to SoundEventConfig(enabled = true, soundType = SoundType.LOW_POP, volume = 0.8f),
    SoundEvent.ORDER_PAID to SoundEventConfig(enabled = true, soundType = SoundType.TADA, volume = 1.0f),
    SoundEvent.ERROR to SoundEventConfig(enabled = true, soundType = SoundType.BUZZ, volume = 0.9f),
    SoundEvent.NAV_TAP to SoundEventConfig(enabled = true, soundType = SoundType.CLICK, volume = 0.5f)
)
