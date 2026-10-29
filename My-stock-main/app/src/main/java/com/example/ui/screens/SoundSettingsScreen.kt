package com.example.ui.screens

import com.example.ui.theme.LocalBarPadding
import com.example.ui.theme.AppCard
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ShopSettings
import com.example.data.model.SoundEvent
import com.example.data.model.SoundEventConfig
import com.example.data.model.SoundType
import com.example.ui.theme.SipTealPrimary
import com.example.ui.viewmodel.ShopViewModel
import kotlin.math.roundToInt

@Composable
fun SoundSettingsScreen(
    viewModel: ShopViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = LocalBarPadding.current.calculateTopPadding(), bottom = LocalBarPadding.current.calculateBottomPadding())
            .padding(16.dp)
            .testTag("sound_settings_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title & Reset Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Sound & Feedback",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Customize offline audio synthesizers and haptics",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(
                onClick = { viewModel.resetSoundSettings() },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("reset_sounds_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset")
            }
        }

        // Master Volume & Master Toggle Card
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Master Sound switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (settings.masterSoundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = null,
                            tint = if (settings.masterSoundEnabled) SipTealPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Master Sound Effects",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (settings.masterSoundEnabled) "Sound enabled" else "Muted completely",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = settings.masterSoundEnabled,
                        onCheckedChange = { isEnabled ->
                            viewModel.updateSettings(settings.copy(masterSoundEnabled = isEnabled))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SipTealPrimary,
                            checkedTrackColor = SipTealPrimary.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.testTag("master_sound_switch")
                    )
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // Master Volume Slider (0% to 200%)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Master Volume",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "${(settings.masterVolume * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = SipTealPrimary
                            )
                        )
                    }

                    Slider(
                        value = settings.masterVolume,
                        onValueChange = { newVol ->
                            viewModel.updateSettings(settings.copy(masterVolume = newVol))
                        },
                        valueRange = 0f..2f,
                        steps = 19,
                        enabled = settings.masterSoundEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = SipTealPrimary,
                            activeTrackColor = SipTealPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("master_volume_slider")
                    )
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // Vibration Haptics Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Vibration,
                            contentDescription = null,
                            tint = SipTealPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "Haptic Vibration",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Vibrate on button clicks and payment",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = settings.vibrationEnabled,
                        onCheckedChange = { isEnabled ->
                            viewModel.updateSettings(settings.copy(vibrationEnabled = isEnabled))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SipTealPrimary,
                            checkedTrackColor = SipTealPrimary.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }

        // Individual Events Configuration
        Text(
            text = "Event Sound Triggers",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(top = 4.dp)
        )

        SoundEvent.entries.forEach { event ->
            val config = settings.soundEvents[event] ?: SoundEventConfig()
            SoundEventCard(
                event = event,
                config = config,
                masterEnabled = settings.masterSoundEnabled,
                onUpdateConfig = { updatedConfig ->
                    val mutableConfigs = settings.soundEvents.toMutableMap()
                    mutableConfigs[event] = updatedConfig
                    viewModel.updateSettings(settings.copy(soundEvents = mutableConfigs))
                },
                onPreview = {
                    viewModel.previewSound(config.soundType, config.volume)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun SoundEventCard(
    event: SoundEvent,
    config: SoundEventConfig,
    masterEnabled: Boolean,
    onUpdateConfig: (SoundEventConfig) -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isDropdownOpen by remember { mutableStateOf(false) }

    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sound_event_${event.id}"),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header with event name, switch and preview button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.label,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Sound: ${config.soundType.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Preview Button
                    IconButton(
                        onClick = onPreview,
                        enabled = masterEnabled && config.enabled,
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = if (masterEnabled && config.enabled) SipTealPrimary.copy(alpha = 0.15f) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Preview sound",
                            tint = if (masterEnabled && config.enabled) SipTealPrimary else Color.LightGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Switch(
                        checked = config.enabled,
                        onCheckedChange = { isEnabled ->
                            onUpdateConfig(config.copy(enabled = isEnabled))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SipTealPrimary,
                            checkedTrackColor = SipTealPrimary.copy(alpha = 0.3f)
                        )
                    )
                }
            }

            // Sound Type Selector & Volume Slider (when enabled)
            if (config.enabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Sound Type:", style = MaterialTheme.typography.labelMedium)

                    Box {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { isDropdownOpen = true }
                        ) {
                            Text(
                                text = "${config.soundType.label} ▾",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = isDropdownOpen,
                            onDismissRequest = { isDropdownOpen = false }
                        ) {
                            SoundType.entries.forEach { st ->
                                DropdownMenuItem(
                                    text = { Text(st.label) },
                                    onClick = {
                                        onUpdateConfig(config.copy(soundType = st))
                                        isDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Event Volume Slider (0% to 100%)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Volume", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "${(config.volume * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Slider(
                        value = config.volume,
                        onValueChange = { newVol ->
                            onUpdateConfig(config.copy(volume = newVol))
                        },
                        valueRange = 0f..1f,
                        steps = 9,
                        colors = SliderDefaults.colors(
                            thumbColor = SipTealPrimary,
                            activeTrackColor = SipTealPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
