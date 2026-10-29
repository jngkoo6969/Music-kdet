package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.normalizeHex
import com.example.ui.theme.parseHexColor

private val Swatches = listOf(
    "#FFFFFF", "#FFF7E6", "#F1F5F9", "#DCFCE7", "#E0F2FE", "#FCE7F3", "#EDE9FE", "#FDE68A",
    "#94A3B8", "#14B8A6", "#3B82F6", "#8B5CF6", "#EF4444", "#F97316",
    "#334155", "#1E293B", "#0F172A", "#000000"
)

/** One colour setting: tap a swatch, or type a hex code. "Default" clears it (value = ""). */
@Composable
fun ColorSettingRow(title: String, value: String, onChange: (String) -> Unit) {
    var hexDraft by remember(value) { mutableStateOf(value) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
            TextButton(onClick = { onChange("") }, enabled = value.isNotEmpty()) { Text("Default") }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Swatches.forEach { hex ->
                val selected = value.equals(hex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(parseHexColor(hex)!!)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                        .clickable { onChange(hex) }
                )
            }
        }
        OutlinedTextField(
            value = hexDraft,
            onValueChange = {
                hexDraft = it
                normalizeHex(it)?.let { ok -> onChange(ok) }
            },
            label = { Text("Hex code, e.g. #FFFFFF") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
