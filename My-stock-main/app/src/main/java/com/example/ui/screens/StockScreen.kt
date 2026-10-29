package com.example.ui.screens

import com.example.ui.theme.LocalBarPadding
import com.example.ui.theme.AppCard
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.DrinkEntity
import com.example.ui.components.ImageCropperDialog
import com.example.ui.theme.SipErrorRed
import com.example.ui.theme.SipTealPrimary
import com.example.ui.theme.SipWarningAmber
import com.example.ui.viewmodel.ShopViewModel
import com.example.util.CurrencyFormatter
import java.io.File

@Composable
fun StockScreen(
    viewModel: ShopViewModel,
    modifier: Modifier = Modifier
) {
    val drinks by viewModel.allDrinks.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isAddingDrink by viewModel.isAddingDrink.collectAsStateWithLifecycle()
    val editingDrink by viewModel.editingDrink.collectAsStateWithLifecycle()
    val imageCropperUri by viewModel.imageCropperUri.collectAsStateWithLifecycle()

    var showDeleteConfirmDrink by remember { mutableStateOf<DrinkEntity?>(null) }
    var showOnlyLowStock by remember { mutableStateOf(false) }
    // Path of a freshly cropped photo, waiting to be shown in the Add / Edit form
    var pendingImagePath by remember { mutableStateOf<String?>(null) }

    // Android Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setCropperUri(uri, editingDrink?.id)
        }
    }

    val displayedDrinks = drinks.filter { drink ->
        if (showOnlyLowStock) drink.stock <= drink.lowStockThreshold else true
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("stock_screen"),
        containerColor = Color.Transparent,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.startAddDrink() },
                containerColor = SipTealPrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(bottom = LocalBarPadding.current.calculateBottomPadding())
                    .testTag("add_drink_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Drink")
                    Text("Add Drink", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(top = LocalBarPadding.current.calculateTopPadding())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
        ) {
            // Header stats & toggles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Stock & Inventory",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${drinks.size} total drinks (${drinks.count { it.stock <= it.lowStockThreshold }} low stock)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (showOnlyLowStock) SipWarningAmber.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { showOnlyLowStock = !showOnlyLowStock }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (showOnlyLowStock) SipWarningAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (showOnlyLowStock) "Low Stock Only" else "Filter Low Stock",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (showOnlyLowStock) SipWarningAmber else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Inventory List with quick +/- stock controls, reordering and edit/delete
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("stock_items_list"),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp + LocalBarPadding.current.calculateBottomPadding())
            ) {
                itemsIndexed(displayedDrinks, key = { _, item -> item.id }) { index, drink ->
                    StockItemCard(
                        modifier = Modifier.animateItem(),
                        drink = drink,
                        currencyMode = settings.currencyMode,
                        exchangeRate = settings.khrExchangeRate,
                        onAddOne = { viewModel.adjustStock(drink.id, 1) },
                        onAddTen = { viewModel.adjustStock(drink.id, 10) },
                        onMinusOne = { viewModel.adjustStock(drink.id, -1) },
                        onToggleActive = { viewModel.toggleDrinkActive(drink) },
                        onEdit = { viewModel.startEditDrink(drink) },
                        onDelete = { showDeleteConfirmDrink = drink },
                        canMoveUp = index > 0,
                        canMoveDown = index < displayedDrinks.size - 1,
                        onMoveUp = {
                            val mutableList = drinks.toMutableList()
                            val curIdx = mutableList.indexOfFirst { it.id == drink.id }
                            if (curIdx > 0) {
                                val temp = mutableList[curIdx]
                                mutableList[curIdx] = mutableList[curIdx - 1]
                                mutableList[curIdx - 1] = temp
                                viewModel.reorderDrinks(mutableList)
                            }
                        },
                        onMoveDown = {
                            val mutableList = drinks.toMutableList()
                            val curIdx = mutableList.indexOfFirst { it.id == drink.id }
                            if (curIdx in 0 until mutableList.size - 1) {
                                val temp = mutableList[curIdx]
                                mutableList[curIdx] = mutableList[curIdx + 1]
                                mutableList[curIdx + 1] = temp
                                viewModel.reorderDrinks(mutableList)
                            }
                        }
                    )
                }
            }
        }
    }

    // Add / Edit Drink Dialog
    if (isAddingDrink || editingDrink != null) {
        DrinkFormDialog(
            initialDrink = editingDrink,
            currencyMode = settings.currencyMode,
            exchangeRate = settings.khrExchangeRate,
            pickedImagePath = pendingImagePath,
            onPickedConsumed = { pendingImagePath = null },
            onPickPhoto = {
                photoPickerLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            },
            existingCategories = drinks.map { it.category }.filter { it.isNotBlank() }.distinct(),
            onSave = { name, price, stock, threshold, colorHex, imageUri, isActive, category ->
                viewModel.saveDrink(
                    id = editingDrink?.id ?: 0L,
                    name = name,
                    price = price,
                    stock = stock,
                    lowStockThreshold = threshold,
                    colorHex = colorHex,
                    imageUri = imageUri,
                    isActive = isActive,
                    category = category
                )
            },
            onDismiss = { viewModel.closeDrinkDialog() },
            onRemovePhoto = { path, callback ->
                viewModel.removeDrinkPhoto(path, callback)
            }
        )
    }

    // Image Cropper Modal
    imageCropperUri?.let { uri ->
        ImageCropperDialog(
            imageUri = uri,
            imageStorageManager = viewModel.imageStorageManager,
            onCropped = { croppedBitmap ->
                viewModel.saveCroppedImage(croppedBitmap) { savedPath ->
                    pendingImagePath = savedPath
                }
            },
            onCancel = { viewModel.setCropperUri(null) }
        )
    }

    // 2-Tap Delete Drink Confirmation Dialog
    showDeleteConfirmDrink?.let { drink ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDrink = null },
            shape = RoundedCornerShape(20.dp),
            title = { Text("Delete '${drink.name}'?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Are you sure you want to delete this drink? Historical sales orders, receipts, and revenue reports will remain completely safe and accurate."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDrink(drink)
                        showDeleteConfirmDrink = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SipErrorRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete Drink")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteConfirmDrink = null },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun StockItemCard(
    drink: DrinkEntity,
    currencyMode: String,
    exchangeRate: Double,
    onAddOne: () -> Unit,
    onAddTen: () -> Unit,
    onMinusOne: () -> Unit,
    onToggleActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLowStock = drink.stock in 1..drink.lowStockThreshold
    val isSoldOut = drink.stock <= 0

    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("stock_item_${drink.id}"),
        shape = RoundedCornerShape(18.dp),
        tint = if (!drink.isActive) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else Color.Unspecified
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Reorder arrows
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = canMoveUp,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Move Up",
                            tint = if (canMoveUp) MaterialTheme.colorScheme.onSurfaceVariant else Color.LightGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = canMoveDown,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Move Down",
                            tint = if (canMoveDown) MaterialTheme.colorScheme.onSurfaceVariant else Color.LightGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Photo thumbnail
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (!drink.imageUri.isNullOrBlank() && File(drink.imageUri).exists()) {
                        AsyncImage(
                            model = File(drink.imageUri),
                            contentDescription = drink.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.LocalDrink,
                            contentDescription = drink.name,
                            tint = SipTealPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Drink Name, Price, and Status
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = drink.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = SipTealPrimary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = drink.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = SipTealPrimary,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        if (!drink.isActive) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            ) {
                                Text(
                                    text = "Hidden",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = CurrencyFormatter.formatDual(drink.price, exchangeRate, currencyMode),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = SipTealPrimary
                        )
                    )

                    // Stock indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Stock: ${drink.stock}",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isSoldOut -> SipErrorRed
                                    isLowStock -> SipWarningAmber
                                    else -> Color(0xFF10B981)
                                }
                            )
                        )
                        if (isLowStock) {
                            Text(
                                text = "(Low: ≤${drink.lowStockThreshold})",
                                style = MaterialTheme.typography.labelSmall,
                                color = SipWarningAmber
                            )
                        }
                    }
                }

                // Edit & Delete icons
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Drink",
                            tint = SipTealPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Drink",
                            tint = SipErrorRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Stock buttons (-1, +1, +10) and Active toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable(onClick = onMinusOne)
                    ) {
                        Text(
                            text = "-1",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SipTealPrimary.copy(alpha = 0.15f),
                        modifier = Modifier.clickable(onClick = onAddOne)
                    ) {
                        Text(
                            text = "+1",
                            fontWeight = FontWeight.Bold,
                            color = SipTealPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SipTealPrimary,
                        modifier = Modifier.clickable(onClick = onAddTen)
                    ) {
                        Text(
                            text = "+10",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (drink.isActive) "Active" else "Hidden",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Switch(
                        checked = drink.isActive,
                        onCheckedChange = { onToggleActive() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SipTealPrimary,
                            checkedTrackColor = SipTealPrimary.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun DrinkFormDialog(
    initialDrink: DrinkEntity?,
    currencyMode: String,
    exchangeRate: Double,
    onPickPhoto: () -> Unit,
    pickedImagePath: String? = null,
    onPickedConsumed: () -> Unit = {},
    existingCategories: List<String>,
    onSave: (name: String, price: Double, stock: Int, threshold: Int, colorHex: String, imageUri: String?, isActive: Boolean, category: String) -> Unit,
    onDismiss: () -> Unit,
    onRemovePhoto: (String?, () -> Unit) -> Unit
) {
    var name by remember { mutableStateOf(initialDrink?.name ?: "") }
    var priceStr by remember { mutableStateOf(initialDrink?.price?.toString() ?: "2.50") }
    var stockStr by remember { mutableStateOf(initialDrink?.stock?.toString() ?: "20") }
    var thresholdStr by remember { mutableStateOf(initialDrink?.lowStockThreshold?.toString() ?: "5") }
    var colorHex by remember { mutableStateOf(initialDrink?.colorHex ?: "#E0F2FE") }
    var imageUri by remember { mutableStateOf(initialDrink?.imageUri) }
    var isActive by remember { mutableStateOf(initialDrink?.isActive ?: true) }
    var category by remember { mutableStateOf(initialDrink?.category ?: "Water") }

    // A new cropped photo arrived -> show it in the form (Save stores it with the drink)
    LaunchedEffect(pickedImagePath) {
        val newPath = pickedImagePath
        if (newPath != null) {
            val old = imageUri
            // Clean up an earlier photo picked in this same dialog but never saved
            if (old != null && old != initialDrink?.imageUri && old != newPath) {
                onRemovePhoto(old) {}
            }
            imageUri = newPath
            onPickedConsumed()
        }
    }

    val categorySuggestions = remember(existingCategories) {
        (listOf("Water", "Energy Drink", "Tea", "Coffee", "Soda", "Juice", "Milk", "Smoothie", "Other") + existingCategories)
            .distinctBy { it.lowercase() }
    }

    val presetColors = listOf(
        "#E0F2FE", "#BBF7D0", "#FED7AA", "#FDE68A",
        "#FECDD3", "#E9D5FF", "#CFFAFE", "#E2E8F0"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(
                text = if (initialDrink == null) "Add New Drink" else "Edit Drink",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Photo area with Import and Remove Photo buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!imageUri.isNullOrBlank() && File(imageUri!!).exists()) {
                            AsyncImage(
                                model = File(imageUri!!),
                                contentDescription = "Drink Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.LocalDrink,
                                contentDescription = null,
                                tint = SipTealPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = onPickPhoto,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SipTealPrimary),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (imageUri != null) "Change Photo" else "Pick & Crop Photo", style = MaterialTheme.typography.labelSmall)
                        }

                        if (imageUri != null) {
                            OutlinedButton(
                                onClick = {
                                    onRemovePhoto(imageUri) {
                                        imageUri = null
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = SipErrorRed),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.NoPhotography, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Remove Photo", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // Drink Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Drink Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Category (shown as filter chips on the order screen)
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category (e.g. Water, Energy Drink)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categorySuggestions.forEach { c ->
                        androidx.compose.material3.FilterChip(
                            selected = category.trim().equals(c, ignoreCase = true),
                            onClick = { category = c },
                            label = { Text(c, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                // Price (USD canonical)
                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { priceStr = it },
                    label = { Text("Selling Price ($ USD)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Initial Stock
                    OutlinedTextField(
                        value = stockStr,
                        onValueChange = { stockStr = it },
                        label = { Text("Stock Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Low Stock Alert Threshold
                    OutlinedTextField(
                        value = thresholdStr,
                        onValueChange = { thresholdStr = it },
                        label = { Text("Low Alert") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Card Color Tint selector
                Text(
                    text = "Card Background Tint:",
                    style = MaterialTheme.typography.labelMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetColors.forEach { hex ->
                        val parsedColor = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.LightGray }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(parsedColor)
                                .border(
                                    width = if (colorHex == hex) 3.dp else 1.dp,
                                    color = if (colorHex == hex) SipTealPrimary else Color.Gray.copy(alpha = 0.4f),
                                    shape = CircleShape
                                )
                                .clickable { colorHex = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val price = priceStr.toDoubleOrNull() ?: 0.0
                    val stock = stockStr.toIntOrNull() ?: 0
                    val threshold = thresholdStr.toIntOrNull() ?: 5
                    onSave(name, price, stock, threshold, colorHex, imageUri, isActive, category.trim().ifBlank { "Other" })
                },
                colors = ButtonDefaults.buttonColors(containerColor = SipTealPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Drink")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text("Cancel")
            }
        }
    )
}
