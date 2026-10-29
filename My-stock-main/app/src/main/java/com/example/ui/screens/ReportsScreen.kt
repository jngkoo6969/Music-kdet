package com.example.ui.screens

import com.example.ui.theme.LocalBarPadding
import com.example.ui.theme.AppCard
import android.app.DatePickerDialog
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.PaddingValues
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import java.io.File
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.OrderStatus
import com.example.data.model.OrderWithItems
import com.example.data.model.PaymentMethod
import com.example.ui.components.StatisticCard
import com.example.ui.theme.SipAbaBlue
import com.example.ui.theme.SipCashGreen
import com.example.ui.theme.SipErrorRed
import com.example.ui.theme.SipOceanBlue
import com.example.ui.theme.SipTealPrimary
import com.example.ui.viewmodel.ShopViewModel
import com.example.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ReportsScreen(
    viewModel: ShopViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allOrders by viewModel.allOrders.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val allDrinks by viewModel.allDrinks.collectAsStateWithLifecycle()
    val drinkById = remember(allDrinks) { allDrinks.associateBy { it.id } }
    val selectedDateMillis by viewModel.reportsDateMillis.collectAsStateWithLifecycle()

    var showAllDates by rememberSaveable { mutableStateOf(false) }
    var showVoidConfirmOrder by remember { mutableStateOf<OrderWithItems?>(null) }
    var selectedOrderForDetail by remember { mutableStateOf<OrderWithItems?>(null) }
    var showEditOrder by remember { mutableStateOf<OrderWithItems?>(null) }
    var showDeleteConfirmOrder by remember { mutableStateOf<OrderWithItems?>(null) }

    val dateFormat = remember { SimpleDateFormat("EEE, MMM d, yyyy", Locale.US) }
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.US) }

    // Filter orders by chosen date
    val cal = Calendar.getInstance().apply {
        timeInMillis = selectedDateMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val dayStart = cal.timeInMillis
    val dayEnd = dayStart + 86_400_000L - 1

    val dateOrders = if (showAllDates) allOrders else allOrders.filter { it.order.orderTimestamp in dayStart..dayEnd }
    val paidOrders = dateOrders.filter { it.order.status == OrderStatus.PAID.name }

    val daySales = paidOrders.sumOf { it.order.orderTotal }
    val paidCount = paidOrders.size
    val avgOrderValue = if (paidCount > 0) daySales / paidCount else 0.0

    val cashRevenue = paidOrders
        .filter { it.order.paymentMethod.equals(PaymentMethod.CASH.name, ignoreCase = true) }
        .sumOf { it.order.orderTotal }
    val abaRevenue = paidOrders
        .filter { it.order.paymentMethod.equals(PaymentMethod.ABA.name, ignoreCase = true) }
        .sumOf { it.order.orderTotal }

    // Top 5 selling drinks on chosen date
    val drinkSalesMap = mutableMapOf<String, Pair<Int, Double>>()
    for (o in paidOrders) {
        for (item in o.items) {
            val cur = drinkSalesMap[item.historicalDrinkName] ?: Pair(0, 0.0)
            drinkSalesMap[item.historicalDrinkName] = Pair(cur.first + item.quantity, cur.second + item.lineTotal)
        }
    }
    val top5Drinks = drinkSalesMap.entries
        .sortedByDescending { it.value.second }
        .take(5)

    // Order card shared by the "Day" and "All dates" lists
    val orderCard: @Composable (OrderWithItems) -> Unit = { orderWithItems ->
                    val o = orderWithItems.order
                    val isVoid = o.status == OrderStatus.VOID.name

                    val accent = when {
                        isVoid -> SipErrorRed
                        o.paymentMethod.equals("CASH", ignoreCase = true) -> SipCashGreen
                        else -> SipAbaBlue
                    }
                    val itemCount = orderWithItems.items.sumOf { it.quantity }
                    var menuOpen by remember { mutableStateOf(false) }

                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedOrderForDetail = orderWithItems },
                        shape = RoundedCornerShape(16.dp),
                        tint = if (isVoid) SipErrorRed.copy(alpha = 0.10f) else Color.Unspecified
                    ) {
                        Row(modifier = Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                            // Colour strip: green = cash, blue = ABA, red = void
                            Box(
                                modifier = Modifier
                                    .width(5.dp)
                                    .fillMaxHeight()
                                    .background(accent)
                            )

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Row 1: time + cashier  |  total
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = timeFormat.format(Date(o.orderTimestamp)),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (isVoid) Color.Gray else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${o.cashierName}  ·  ${o.orderId}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = CurrencyFormatter.format(o.orderTotal, settings.currencyMode, settings.khrExchangeRate),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isVoid) Color.Gray else SipTealPrimary,
                                            textDecoration = if (isVoid) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                        )
                                    )
                                }

                                // Row 2: items
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    orderWithItems.items.forEach { item ->
                                        Text(
                                            text = "${item.quantity}x ${item.historicalDrinkName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // Row 3: pills + actions
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(shape = RoundedCornerShape(50), color = accent.copy(alpha = 0.15f)) {
                                        Text(
                                            text = if (isVoid) "VOID" else o.paymentMethod.uppercase(),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = accent),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                        )
                                    }
                                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                                        Text(
                                            text = if (itemCount == 1) "1 item" else "$itemCount items",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    Box {
                                        IconButton(
                                            onClick = { menuOpen = true },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .testTag("order_menu_button")
                                        ) {
                                            Icon(
                                                Icons.Default.MoreVert,
                                                contentDescription = "Order actions",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                            DropdownMenuItem(
                                                text = { Text("Edit order") },
                                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                                onClick = { menuOpen = false; showEditOrder = orderWithItems }
                                            )
                                            if (!isVoid) {
                                                DropdownMenuItem(
                                                    text = { Text("Void order", color = SipErrorRed) },
                                                    leadingIcon = { Icon(Icons.Default.Cancel, contentDescription = null, tint = SipErrorRed) },
                                                    onClick = { menuOpen = false; showVoidConfirmOrder = orderWithItems }
                                                )
                                            }
                                            DropdownMenuItem(
                                                text = { Text("Delete order", color = SipErrorRed) },
                                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = SipErrorRed) },
                                                onClick = { menuOpen = false; showDeleteConfirmOrder = orderWithItems }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
    }

    // One scrolling list for the whole page so every order can be reached down to the bottom.
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("reports_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp + LocalBarPadding.current.calculateTopPadding(), bottom = 40.dp + LocalBarPadding.current.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // View mode: one day or every date
        item(key = "mode_toggle") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !showAllDates,
                    onClick = { showAllDates = false },
                    label = { Text("By day") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SipTealPrimary,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.testTag("reports_mode_day")
                )
                FilterChip(
                    selected = showAllDates,
                    onClick = { showAllDates = true },
                    label = { Text("All dates") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SipTealPrimary,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.testTag("reports_mode_all")
                )
            }
        }

        // Date selector Bar & Export actions
        item(key = "date_bar") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .clickable {
                        val currentCal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val pickedCal = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, year)
                                    set(Calendar.MONTH, month)
                                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                }
                                showAllDates = false
                                viewModel.setReportsDate(pickedCal.timeInMillis)
                            },
                            currentCal.get(Calendar.YEAR),
                            currentCal.get(Calendar.MONTH),
                            currentCal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }
                    .testTag("reports_date_picker_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = "Select Date",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (showAllDates) "Pick a date" else dateFormat.format(Date(selectedDateMillis)),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { viewModel.exportReportsCsv(dateOrders) },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("CSV")
                }
            }
        }

        }

        // Summary KPI statistics cards
        item(key = "kpis") {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatisticCard(
                title = "Total Sales",
                value = CurrencyFormatter.format(daySales, settings.currencyMode, settings.khrExchangeRate),
                icon = Icons.Default.AttachMoney,
                iconTint = SipCashGreen,
                modifier = Modifier.weight(1f)
            )
            StatisticCard(
                title = "Paid Orders",
                value = "$paidCount",
                icon = Icons.Default.Receipt,
                iconTint = SipOceanBlue,
                modifier = Modifier.weight(1f)
            )
            StatisticCard(
                title = "Avg Order",
                value = CurrencyFormatter.format(avgOrderValue, settings.currencyMode, settings.khrExchangeRate),
                icon = Icons.Default.Payments,
                iconTint = SipTealPrimary,
                modifier = Modifier.weight(1f)
            )
        }
        }

        // Cash vs ABA split row
        item(key = "split") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SipCashGreen.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null, tint = SipCashGreen)
                    Column {
                        Text("Cash Revenue", style = MaterialTheme.typography.labelSmall)
                        Text(
                            CurrencyFormatter.format(cashRevenue, settings.currencyMode, settings.khrExchangeRate),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = SipCashGreen)
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SipAbaBlue.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.QrCode2, contentDescription = null, tint = SipAbaBlue)
                    Column {
                        Text("ABA Revenue", style = MaterialTheme.typography.labelSmall)
                        Text(
                            CurrencyFormatter.format(abaRevenue, settings.currencyMode, settings.khrExchangeRate),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = SipAbaBlue)
                        )
                    }
                }
            }
        }
        }

        // Top 5 Selling Drinks
        if (top5Drinks.isNotEmpty()) {
        item(key = "top5") {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Top Selling Drinks",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    top5Drinks.forEachIndexed { i, entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${i + 1}. ${entry.key} (${entry.value.first} sold)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = CurrencyFormatter.format(entry.value.second, settings.currencyMode, settings.khrExchangeRate),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = SipTealPrimary
                                )
                            )
                        }
                    }
                }
            }
        }
        }

        // Orders header
        item(key = "orders_header") {
            Text(
                text = if (showAllDates) "All orders (${dateOrders.size})" else "Orders (${dateOrders.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (dateOrders.isEmpty()) {
            item(key = "orders_empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (showAllDates) "No orders recorded yet." else "No orders recorded for this date.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (showAllDates) {
            // Every order, grouped by day (newest first) with a sticky day header.
            val grouped = dateOrders
                .sortedByDescending { it.order.orderTimestamp }
                .groupBy { dayStartOf(it.order.orderTimestamp) }
            grouped.forEach { (dayMillis, list) ->
                stickyHeader(key = "day_$dayMillis") {
                    val dayPaid = list.filter { it.order.status == OrderStatus.PAID.name }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = dayLabel(dayMillis, dateFormat),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "${list.size} order${if (list.size == 1) "" else "s"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(shape = RoundedCornerShape(50), color = SipTealPrimary.copy(alpha = 0.12f)) {
                                Text(
                                    text = CurrencyFormatter.format(dayPaid.sumOf { it.order.orderTotal }, settings.currencyMode, settings.khrExchangeRate),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = SipTealPrimary),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
                items(list, key = { it.order.orderId }) { Box(Modifier.animateItem()) { orderCard(it) } }
            }
        } else {
            items(dateOrders, key = { it.order.orderId }) { Box(Modifier.animateItem()) { orderCard(it) } }
        }
    }

    // Two-Tap Void Order Confirmation Dialog
    showVoidConfirmOrder?.let { orderWithItems ->
        val o = orderWithItems.order
        AlertDialog(
            onDismissRequest = { showVoidConfirmOrder = null },
            shape = RoundedCornerShape(20.dp),
            title = { Text("Void Order ${o.orderId}?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Are you sure you want to void this paid order?")
                    Text(
                        text = "• Order will be marked as VOID.\n• Stored revenue of ${CurrencyFormatter.format(o.orderTotal, settings.currencyMode)} will be removed from sales totals.\n• Sold quantities (${orderWithItems.items.sumOf { it.quantity }} items) will be automatically returned to inventory stock.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.voidOrder(o.orderId)
                        showVoidConfirmOrder = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SipErrorRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("confirm_void_button")
                ) {
                    Text("Confirm Void")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showVoidConfirmOrder = null },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Order Itemized Receipt Detail Dialog
    selectedOrderForDetail?.let { orderWithItems ->
        val o = orderWithItems.order
        AlertDialog(
            onDismissRequest = { selectedOrderForDetail = null },
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Order Receipt", fontWeight = FontWeight.Bold)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (o.status == "PAID") SipCashGreen.copy(alpha = 0.2f) else SipErrorRed.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = o.status,
                            color = if (o.status == "PAID") SipCashGreen else SipErrorRed,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Order ID: ${o.orderId}", style = MaterialTheme.typography.labelSmall)
                    Text("Date & Time: ${dateFormat.format(Date(o.orderTimestamp))} at ${timeFormat.format(Date(o.orderTimestamp))}", style = MaterialTheme.typography.labelSmall)
                    Text("Cashier: ${o.cashierName} • Payment: ${o.paymentMethod}", style = MaterialTheme.typography.labelSmall)
                    Divider(modifier = Modifier.padding(vertical = 4.dp))

                    // Items grouped by the category they were ordered from, each with its drink photo
                    val grouped = orderWithItems.items.groupBy { item ->
                        item.drinkId?.let { drinkById[it]?.category } ?: "Other"
                    }
                    grouped.entries.sortedBy { it.key }.forEach { (category, itemsInCategory) ->
                        Text(
                            text = category.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = SipTealPrimary,
                                letterSpacing = 1.sp
                            ),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        itemsInCategory.forEach { item ->
                            val drink = item.drinkId?.let { drinkById[it] }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(SipTealPrimary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val img = drink?.imageUri
                                    if (!img.isNullOrBlank() && File(img).exists()) {
                                        AsyncImage(
                                            model = File(img),
                                            contentDescription = item.historicalDrinkName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.LocalDrink,
                                            contentDescription = null,
                                            tint = SipTealPrimary.copy(alpha = 0.7f),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Text(
                                    "${item.quantity}x ${item.historicalDrinkName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    CurrencyFormatter.format(item.lineTotal, settings.currencyMode, settings.khrExchangeRate),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Order Total:", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text(CurrencyFormatter.formatDual(o.orderTotal, settings.khrExchangeRate, settings.currencyMode), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = SipTealPrimary))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedOrderForDetail = null },
                    colors = ButtonDefaults.buttonColors(containerColor = SipTealPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showEditOrder = orderWithItems
                        selectedOrderForDetail = null
                    }) { Text("Edit") }
                    if (o.status != OrderStatus.VOID.name) {
                        TextButton(onClick = {
                            showVoidConfirmOrder = orderWithItems
                            selectedOrderForDetail = null
                        }) { Text("Void", color = SipErrorRed) }
                    }
                    TextButton(onClick = {
                        showDeleteConfirmOrder = orderWithItems
                        selectedOrderForDetail = null
                    }) { Text("Delete", color = SipErrorRed) }
                }
            }
        )
    }

    // Delete order confirmation
    showDeleteConfirmOrder?.let { orderWithItems ->
        val o = orderWithItems.order
        val isPaid = o.status == OrderStatus.PAID.name
        AlertDialog(
            onDismissRequest = { showDeleteConfirmOrder = null },
            shape = RoundedCornerShape(20.dp),
            title = { Text("Delete Order ${o.orderId}?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (isPaid)
                        "This permanently removes the order and its ${CurrencyFormatter.format(o.orderTotal, settings.currencyMode, settings.khrExchangeRate)} from all reports. The ${orderWithItems.items.sumOf { it.quantity }} sold items will be returned to stock. This cannot be undone."
                    else
                        "This permanently removes the voided order from your reports. Stock is not changed (it was already returned when voided). This cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteOrder(o.orderId)
                        showDeleteConfirmOrder = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SipErrorRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("confirm_delete_order_button")
                ) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmOrder = null }, shape = RoundedCornerShape(12.dp)) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit order dialog
    showEditOrder?.let { orderWithItems ->
        EditOrderDialog(
            orderWithItems = orderWithItems,
            currencyMode = settings.currencyMode,
            exchangeRate = settings.khrExchangeRate,
            onSave = { cashier, payment, timestamp, quantities ->
                viewModel.editOrder(orderWithItems.order.orderId, cashier, payment, timestamp, quantities)
                showEditOrder = null
            },
            onDismiss = { showEditOrder = null }
        )
    }
}

private fun dayStartOf(timestamp: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun dayLabel(dayMillis: Long, fmt: SimpleDateFormat): String {
    val today = dayStartOf(System.currentTimeMillis())
    val yesterday = Calendar.getInstance().apply {
        timeInMillis = today
        add(Calendar.DATE, -1)
    }.timeInMillis
    return when (dayMillis) {
        today -> "Today"
        yesterday -> "Yesterday"
        else -> fmt.format(Date(dayMillis))
    }
}

@Composable
private fun EditOrderDialog(
    orderWithItems: OrderWithItems,
    currencyMode: String,
    exchangeRate: Double,
    onSave: (cashier: String, payment: String, timestamp: Long, quantities: Map<Long, Int>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val o = orderWithItems.order
    var cashier by remember { mutableStateOf(o.cashierName) }
    var payment by remember { mutableStateOf(o.paymentMethod.uppercase()) }
    var timestamp by remember { mutableStateOf(o.orderTimestamp) }
    val quantities = remember {
        mutableStateMapOf<Long, Int>().apply { orderWithItems.items.forEach { put(it.id, it.quantity) } }
    }
    val fmt = remember { SimpleDateFormat("EEE, MMM d yyyy  hh:mm a", Locale.US) }
    val newTotal = orderWithItems.items.sumOf { it.unitPrice * (quantities[it.id] ?: it.quantity) }
    val hasItems = quantities.values.any { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Edit Order", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(o.orderId, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                OutlinedTextField(
                    value = cashier,
                    onValueChange = { cashier = it },
                    label = { Text("Cashier") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(PaymentMethod.CASH, PaymentMethod.ABA).forEach { m ->
                        FilterChip(
                            selected = payment == m.name,
                            onClick = { payment = m.name },
                            label = { Text(m.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SipTealPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        val c = Calendar.getInstance().apply { timeInMillis = timestamp }
                        DatePickerDialog(context, { _, y, m, d ->
                            TimePickerDialog(context, { _, h, min ->
                                timestamp = Calendar.getInstance().apply {
                                    set(y, m, d, h, min, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }.timeInMillis
                            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show()
                        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(fmt.format(Date(timestamp)))
                }

                Divider()

                orderWithItems.items.forEach { item ->
                    val q = quantities[item.id] ?: item.quantity
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.historicalDrinkName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (q == 0) Color.Gray else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (q == 0) "Will be removed" else CurrencyFormatter.format(item.unitPrice * q, currencyMode, exchangeRate),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (q == 0) SipErrorRed else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { if (q > 0) quantities[item.id] = q - 1 }) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease")
                        }
                        Text("$q", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        IconButton(onClick = { quantities[item.id] = q + 1 }) {
                            Icon(Icons.Default.Add, contentDescription = "Increase")
                        }
                    }
                }

                Divider()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("New Total", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        CurrencyFormatter.format(newTotal, currencyMode, exchangeRate),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = SipTealPrimary)
                    )
                }
                if (o.status == OrderStatus.PAID.name) {
                    Text(
                        "Changing quantities also adjusts stock.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(cashier, payment, timestamp, quantities.toMap()) },
                enabled = hasItems && cashier.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = SipTealPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("save_order_edit_button")
            ) { Text("Save") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) { Text("Cancel") }
        }
    )
}
