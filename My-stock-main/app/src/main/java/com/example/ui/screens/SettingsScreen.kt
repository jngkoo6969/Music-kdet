package com.example.ui.screens

import com.example.ui.theme.LocalBarPadding
import com.example.ui.components.ColorSettingRow
import com.example.ui.theme.AppCard
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.luminance
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.SipErrorRed
import com.example.ui.theme.SipTealPrimary
import com.example.ui.viewmodel.ShopViewModel
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: ShopViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var shopName by remember(settings.shopName) { mutableStateOf(settings.shopName) }
    var exchangeRateStr by remember(settings.khrExchangeRate) { mutableStateOf(settings.khrExchangeRate.toInt().toString()) }
    var monthlyGoalStr by remember(settings.monthlySalesGoal) { mutableStateOf(settings.monthlySalesGoal.toInt().toString()) }

    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var pendingRestoreJson by remember { mutableStateOf<String?>(null) }
    var overwriteExistingOnRestore by remember { mutableStateOf(true) }

    // File Picker for Restoring JSON Backup
    val restoreFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val reader = BufferedReader(InputStreamReader(stream))
                    val content = reader.readText()
                    pendingRestoreJson = content
                    showRestoreConfirmDialog = true
                }
            } catch (e: Exception) {
                // Handled gracefully
            }
        }
    }

    fun readText(uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { BufferedReader(InputStreamReader(it)).readText() }
    } catch (_: Exception) { null }

    val importDrinksCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) readText(uri)?.let { viewModel.importDrinksCsv(it) }
    }
    val importOrdersCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) readText(uri)?.let { viewModel.importOrdersCsv(it) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = LocalBarPadding.current.calculateTopPadding(), bottom = LocalBarPadding.current.calculateBottomPadding())
            .padding(16.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Column {
            Text(
                text = "Shop Settings",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Preferences, currency, theme, and data import / export",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Offline Standalone Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SipTealPrimary.copy(alpha = 0.12f))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = SipTealPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "100% Offline Standalone POS",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = SipTealPrimary)
                    )
                    Text(
                        text = "All drinks, photos, orders, inventory, and analytics are stored locally on your device in SQLite. No account or internet connection required.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Shop Profile & Currency Card
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Store, contentDescription = null, tint = SipTealPrimary)
                    Text("Shop Details", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }

                OutlinedTextField(
                    value = shopName,
                    onValueChange = {
                        shopName = it
                        viewModel.updateSettings(settings.copy(shopName = it))
                    },
                    label = { Text("Shop Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Currency Preference: USD vs Cambodian Riel (KHR)
                Text("Primary Currency Display:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = settings.currencyMode == "USD",
                        onClick = { viewModel.updateSettings(settings.copy(currencyMode = "USD")) },
                        label = { Text("$ USD (Dollars)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SipTealPrimary,
                            selectedLabelColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = settings.currencyMode == "KHR",
                        onClick = { viewModel.updateSettings(settings.copy(currencyMode = "KHR")) },
                        label = { Text("៛ KHR (Cambodian Riel)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SipTealPrimary,
                            selectedLabelColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Riel Exchange Rate
                OutlinedTextField(
                    value = exchangeRateStr,
                    onValueChange = {
                        exchangeRateStr = it
                        val rate = it.toDoubleOrNull() ?: 4100.0
                        viewModel.updateSettings(settings.copy(khrExchangeRate = rate))
                    },
                    label = { Text("USD to KHR Exchange Rate (៛ per $1)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Monthly Sales Goal
                OutlinedTextField(
                    value = monthlyGoalStr,
                    onValueChange = {
                        monthlyGoalStr = it
                        val goal = it.toDoubleOrNull() ?: 1500.0
                        viewModel.updateSettings(settings.copy(monthlySalesGoal = goal))
                    },
                    label = { Text("Monthly Sales Goal ($ USD)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // Appearance & Themes Card
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.BrightnessMedium, contentDescription = null, tint = SipTealPrimary)
                    Text("Theme Appearance", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = settings.themePreference == "SYSTEM",
                        onClick = { viewModel.updateSettings(settings.copy(themePreference = "SYSTEM")) },
                        label = { Text("System") },
                        leadingIcon = { Icon(Icons.Default.BrightnessMedium, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SipTealPrimary,
                            selectedLabelColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = settings.themePreference == "LIGHT",
                        onClick = { viewModel.updateSettings(settings.copy(themePreference = "LIGHT")) },
                        label = { Text("Light") },
                        leadingIcon = { Icon(Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SipTealPrimary,
                            selectedLabelColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = settings.themePreference == "DARK",
                        onClick = { viewModel.updateSettings(settings.copy(themePreference = "DARK")) },
                        label = { Text("Dark") },
                        leadingIcon = { Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SipTealPrimary,
                            selectedLabelColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Text Size & Spacing Card
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = SipTealPrimary)
                    Text("Text Size & Spacing", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }

                // Local slider state: the app only re-scales when the finger is released,
                // so the slider doesn't move/resize under the user's finger while dragging.
                var fontDraft by remember(settings.fontScale) { mutableFloatStateOf(settings.fontScale) }
                var spacingDraft by remember(settings.spacingScale) { mutableFloatStateOf(settings.spacingScale) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Font size", style = MaterialTheme.typography.labelLarge)
                    Text("${(fontDraft * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge, color = SipTealPrimary)
                }
                Slider(
                    value = fontDraft,
                    onValueChange = { fontDraft = it },
                    onValueChangeFinished = {
                        viewModel.updateSettings(settings.copy(fontScale = (fontDraft * 20).roundToInt() / 20f))
                    },
                    valueRange = 0.8f..1.4f,
                    steps = 11,
                    colors = SliderDefaults.colors(thumbColor = SipTealPrimary, activeTrackColor = SipTealPrimary),
                    modifier = Modifier.testTag("font_scale_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Spacing & layout size", style = MaterialTheme.typography.labelLarge)
                    Text("${(spacingDraft * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge, color = SipTealPrimary)
                }
                Slider(
                    value = spacingDraft,
                    onValueChange = { spacingDraft = it },
                    onValueChangeFinished = {
                        viewModel.updateSettings(settings.copy(spacingScale = (spacingDraft * 20).roundToInt() / 20f))
                    },
                    valueRange = 0.8f..1.3f,
                    steps = 9,
                    colors = SliderDefaults.colors(thumbColor = SipTealPrimary, activeTrackColor = SipTealPrimary),
                    modifier = Modifier.testTag("spacing_scale_slider")
                )

                Text(
                    text = "Smaller = fits more on screen. Larger = easier to read and tap.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = { viewModel.updateSettings(settings.copy(fontScale = 1.0f, spacingScale = 1.0f)) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Reset to default") }
            }
        }

        // Colours Card
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = SipTealPrimary)
                    Text("Colours", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }

                ColorSettingRow("Page background", settings.backgroundColor) {
                    viewModel.updateSettings(settings.copy(backgroundColor = it))
                }
                ColorSettingRow("Blocks (cards, top bar, bottom menu, dialogs)", settings.blockColor) {
                    viewModel.updateSettings(settings.copy(blockColor = it))
                }
                ColorSettingRow("Text", settings.textColor) {
                    viewModel.updateSettings(settings.copy(textColor = it))
                }

                Text(
                    text = "Pick a colour or type a hex code. Make sure text and blocks contrast so everything stays readable.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = {
                        viewModel.updateSettings(settings.copy(backgroundColor = "", blockColor = "", textColor = ""))
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Reset all colours") }
            }
        }

        // Backup and Restore Card
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Backup, contentDescription = null, tint = SipTealPrimary)
                    Text("Import & Export Data", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }

                // --- Full backup (JSON) ---
                Text("Full backup (JSON)", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    text = "Everything: drinks, categories, orders and settings. Use it to move to another phone or keep a safe copy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.exportBackup() },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SipTealPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_backup_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export JSON")
                    }

                    OutlinedButton(
                        onClick = {
                            restoreFileLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("restore_backup_button")
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import JSON")
                    }
                }

                Divider()

                // --- Drinks (CSV) ---
                Text("Menu & stock (CSV)", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    text = "Columns: name, category, price, stock, lowStockThreshold, isActive, displayOrder, colorHex. Import adds new drinks and updates existing ones by name.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.exportDrinksCsv() },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SipTealPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_drinks_csv_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export CSV")
                    }
                    OutlinedButton(
                        onClick = { importDrinksCsvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/*", "*/*")) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_drinks_csv_button")
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import CSV")
                    }
                }

                Divider()

                // --- Orders (CSV) ---
                Text("Orders (CSV)", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    text = "One row per item: orderId, dateTime, cashier, paymentMethod, status, orderTotal, drinkName, quantity, unitPrice, lineTotal. Import skips orders that already exist and does not change stock.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.exportOrdersCsv() },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SipTealPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_orders_csv_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export CSV")
                    }
                    OutlinedButton(
                        onClick = { importOrdersCsvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/*", "*/*")) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_orders_csv_button")
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import CSV")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Restore Backup Confirmation Dialog
    if (showRestoreConfirmDialog && pendingRestoreJson != null) {
        AlertDialog(
            onDismissRequest = {
                showRestoreConfirmDialog = false
                pendingRestoreJson = null
            },
            shape = RoundedCornerShape(20.dp),
            title = { Text("Import Shop Backup?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Restoring this backup will import drinks, settings, and orders into your local database.")
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.clickable { overwriteExistingOnRestore = !overwriteExistingOnRestore }
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = overwriteExistingOnRestore,
                            onCheckedChange = { overwriteExistingOnRestore = it }
                        )
                        Text("Replace existing data (recommended for clean restore)")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val json = pendingRestoreJson ?: ""
                        showRestoreConfirmDialog = false
                        pendingRestoreJson = null
                        viewModel.restoreBackup(json, overwriteExistingOnRestore)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SipTealPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Proceed with Restore")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showRestoreConfirmDialog = false
                        pendingRestoreJson = null
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
