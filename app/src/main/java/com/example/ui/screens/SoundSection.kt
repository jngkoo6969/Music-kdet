package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.playback.EqPresets
import com.example.playback.SoundSettings
import com.example.ui.theme.AppleMusicRed
import kotlin.math.roundToInt

@Composable
fun SoundSection(
    settings: SoundSettings,
    onChange: (SoundSettings) -> Unit
) {
    val sliderColors = SliderDefaults.colors(
        thumbColor = AppleMusicRed,
        activeTrackColor = AppleMusicRed
    )
    val switchColors = SwitchDefaults.colors(checkedTrackColor = AppleMusicRed, checkedThumbColor = Color.White)
    val presetName = EqPresets.nameFor(settings.bands)

    Text(
        text = "SOUND",
        style = MaterialTheme.typography.labelMedium.copy(
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
    Spacer(modifier = Modifier.height(8.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {
        // ---- Equalizer on / off ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.Equalizer, contentDescription = null, tint = AppleMusicRed)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Equalizer",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (settings.eqEnabled) "Preset: $presetName" else "Off",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = settings.eqEnabled,
                onCheckedChange = { onChange(settings.copy(eqEnabled = it)) },
                colors = switchColors
            )
        }

        if (settings.eqEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (EqPresets.all.keys + "Custom").forEach { name ->
                    if (name == "Custom" && presetName != "Custom") return@forEach
                    FilterChip(
                        selected = presetName == name,
                        onClick = {
                            EqPresets.all[name]?.let { onChange(settings.copy(bands = it, preset = name)) }
                        },
                        label = { Text(name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppleMusicRed,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            EqPresets.bandLabels.forEachIndexed { i, label ->
                val value = settings.bands.getOrElse(i) { 0f }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(56.dp)
                    )
                    Slider(
                        value = value,
                        onValueChange = { v ->
                            val snapped = (v * 2f).roundToInt() / 2f // 0.5 dB steps
                            val bands = settings.bands.toMutableList().also { it[i] = snapped }
                            onChange(settings.copy(bands = bands, preset = EqPresets.nameFor(bands)))
                        },
                        valueRange = -12f..12f,
                        colors = sliderColors,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = (if (value > 0f) "+" else "") + String.format("%.1f", value),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(40.dp)
                    )
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
            modifier = Modifier.padding(vertical = 12.dp)
        )

        // ---- Bass ----
        SliderRow(
            title = "Bass",
            subtitle = "Clean low-end lift (works on every phone)",
            value = settings.bass,
            colors = sliderColors,
            onValue = { onChange(settings.copy(bass = it)) }
        )
        Spacer(modifier = Modifier.height(8.dp))

        // ---- Stereo width ----
        SliderRow(
            title = "Stereo width",
            subtitle = "Wider, more spacious sound - best on headphones",
            value = settings.stereoWidth,
            colors = sliderColors,
            onValue = { onChange(settings.copy(stereoWidth = it)) }
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
            modifier = Modifier.padding(vertical = 12.dp)
        )

        // ---- Volume leveling ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Volume leveling",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Every song plays at a similar loudness, so no song jumps out or disappears - also inside Automix blends.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = settings.leveling,
                onCheckedChange = { onChange(settings.copy(leveling = it)) },
                colors = switchColors
            )
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    subtitle: String,
    value: Int,
    colors: androidx.compose.material3.SliderColors,
    onValue: (Int) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$value%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValue(it.roundToInt()) },
            valueRange = 0f..100f,
            colors = colors
        )
    }
}
