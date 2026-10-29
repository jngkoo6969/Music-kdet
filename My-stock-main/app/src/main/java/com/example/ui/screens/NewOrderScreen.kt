package com.example.ui.screens

import com.example.ui.theme.LocalBarPadding
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CartItem
import com.example.data.model.DrinkEntity
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentMethod
import com.example.ui.components.DrinkCard
import com.example.ui.components.OrderBottomSheet
import com.example.ui.components.PaymentConfirmationDialog
import com.example.ui.components.PaymentSuccessDialog
import com.example.ui.components.StatisticCard
import com.example.ui.theme.SipCashGreen
import com.example.ui.theme.SipOceanBlue
import com.example.ui.theme.SipTealPrimary
import com.example.ui.theme.SipWarningAmber
import com.example.ui.viewmodel.ShopViewModel
import com.example.util.CurrencyFormatter
import java.util.Calendar

@Composable
fun NewOrderScreen(
    viewModel: ShopViewModel,
    modifier: Modifier = Modifier
) {
    val drinks by viewModel.allDrinks.collectAsStateWithLifecycle()
    val cart by viewModel.cart.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val allOrders by viewModel.allOrders.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val hideSoldOut by viewModel.hideSoldOutInOrder.collectAsStateWithLifecycle()
    val paymentMethod by viewModel.paymentMethod.collectAsStateWithLifecycle()
    val isPaymentConfirmOpen by viewModel.isPaymentConfirmOpen.collectAsStateWithLifecycle()
    val completedOrder by viewModel.completedOrder.collectAsStateWithLifecycle()
    val isProcessingOrder by viewModel.isProcessingOrder.collectAsStateWithLifecycle()

    var isOrderSheetExpanded by remember { mutableStateOf(false) }

    // Smart-hide search: hide while scrolling down the drinks, reveal as soon as you scroll up.
    var searchBarVisible by remember { mutableStateOf(true) }
    val smartHideConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -6f) searchBarVisible = false
                else if (available.y > 6f) searchBarVisible = true
                return Offset.Zero
            }
        }
    }
    val showSearchBar = searchBarVisible || searchQuery.isNotBlank()

    // Collapse the order sheet again once the order is empty so the next order starts compact.
    LaunchedEffect(cart.isEmpty()) {
        if (cart.isEmpty()) isOrderSheetExpanded = false
    }
    var selectedCategoryRaw by rememberSaveable { mutableStateOf<String?>(null) }

    // Calculate today's stats
    val calToday = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val todayStart = calToday.timeInMillis
    val todayPaidOrders = allOrders.filter {
        it.order.status == OrderStatus.PAID.name && it.order.orderTimestamp >= todayStart
    }
    val todaySales = todayPaidOrders.sumOf { it.order.orderTotal }
    val todayPaidCount = todayPaidOrders.size

    val lowStockDrinksCount = drinks.count { it.isActive && it.stock in 1..it.lowStockThreshold }
    val soldOutDrinksCount = drinks.count { it.isActive && it.stock <= 0 }

    // Filter drinks
    val activeDrinks = drinks.filter { it.isActive }
    val categories = activeDrinks.map { it.category.ifBlank { "Other" } }
        .distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }
    val selectedCategory = selectedCategoryRaw?.takeIf { sel -> categories.any { it.equals(sel, ignoreCase = true) } }
    val filteredDrinks = activeDrinks.filter { drink ->
        val matchesCategory = selectedCategory == null ||
            drink.category.ifBlank { "Other" }.equals(selectedCategory, ignoreCase = true)
        val matchesSearch = searchQuery.isBlank() || drink.name.contains(searchQuery.trim(), ignoreCase = true)
        val matchesSoldOut = !hideSoldOut || drink.stock > 0
        matchesCategory && matchesSearch && matchesSoldOut
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(top = LocalBarPadding.current.calculateTopPadding())
            .testTag("new_order_screen")
    ) {
        val isWideScreen = maxWidth >= 760.dp
        val barBottom = LocalBarPadding.current.calculateBottomPadding()

        if (isWideScreen) {
            // Multi-column side-by-side tablet layout
            Row(modifier = Modifier.fillMaxSize()) {
                // Main Drink Catalog Grid (left 65%)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .nestedScroll(smartHideConnection)
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                ) {
                    AnimatedVisibility(
                        visible = showSearchBar,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            OrderSearchAndFilterRow(
                        searchQuery = searchQuery,
                        onSearchChange = { viewModel.setSearchQuery(it) },
                        hideSoldOut = hideSoldOut,
                        soldOutCount = soldOutDrinksCount,
                        onToggleHideSoldOut = { viewModel.toggleHideSoldOutInOrder() },
                        categories = categories,
                        selectedCategory = selectedCategory,
                        onCategorySelected = { selectedCategoryRaw = it }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    DrinkGrid(
                        drinks = filteredDrinks,
                        cart = cart,
                        currencyMode = settings.currencyMode,
                        exchangeRate = settings.khrExchangeRate,
                        onAdd = { viewModel.addToCart(it) },
                        onRemoveOne = { viewModel.removeFromCart(it) },
                        columns = 3,
                        bottomPadding = barBottom + 16.dp,
                        header = {
                            OrderDashboardHeader(
                                todaySales = todaySales,
                                todayPaidCount = todayPaidCount,
                                lowStockCount = lowStockDrinksCount,
                                currencyMode = settings.currencyMode,
                                exchangeRate = settings.khrExchangeRate
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Permanent Order Panel (right 35%)
                AnimatedVisibility(
                    visible = cart.isNotEmpty(),
                    enter = slideInHorizontally { it } + fadeIn(),
                    exit = slideOutHorizontally { it } + fadeOut()
                ) {
                Surface(
                    modifier = Modifier
                        .width(372.dp)
                        .fillMaxHeight()
                        .padding(end = 12.dp, top = 12.dp, bottom = barBottom + 12.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    OrderBottomSheet(
                        cart = cart,
                        cashierName = settings.cashierName,
                        onCashierNameChange = { viewModel.setCashierName(it) },
                        paymentMethod = paymentMethod,
                        onPaymentMethodChange = { viewModel.selectPaymentMethod(it) },
                        onAddToCart = { viewModel.addToCart(it) },
                        onRemoveFromCart = { viewModel.removeFromCart(it) },
                        onRemoveEntireLine = { viewModel.removeEntireLineFromCart(it) },
                        onClearCart = { viewModel.clearCart() },
                        onPayClicked = { viewModel.openPaymentConfirmation() },
                        onHoldPaymentSelected = { method ->
                            viewModel.selectPaymentMethod(method)
                            viewModel.processOrder(method)
                        },
                        currencyMode = settings.currencyMode,
                        exchangeRate = settings.khrExchangeRate,
                        isExpanded = true,
                        onToggleExpanded = {},
                        modifier = Modifier.fillMaxHeight()
                    )
                }
                }
            }
        } else {
            // Mobile layout with animated bottom sheet
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(smartHideConnection)
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                ) {
                    AnimatedVisibility(
                        visible = showSearchBar,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            OrderSearchAndFilterRow(
                        searchQuery = searchQuery,
                        onSearchChange = { viewModel.setSearchQuery(it) },
                        hideSoldOut = hideSoldOut,
                        soldOutCount = soldOutDrinksCount,
                        onToggleHideSoldOut = { viewModel.toggleHideSoldOutInOrder() },
                        categories = categories,
                        selectedCategory = selectedCategory,
                        onCategorySelected = { selectedCategoryRaw = it }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    DrinkGrid(
                        drinks = filteredDrinks,
                        cart = cart,
                        currencyMode = settings.currencyMode,
                        exchangeRate = settings.khrExchangeRate,
                        onAdd = { viewModel.addToCart(it) },
                        onRemoveOne = { viewModel.removeFromCart(it) },
                        columns = 2,
                        bottomPadding = barBottom + if (cart.isNotEmpty()) 96.dp else 16.dp,
                        header = {
                            OrderDashboardHeader(
                                todaySales = todaySales,
                                todayPaidCount = todayPaidCount,
                                lowStockCount = lowStockDrinksCount,
                                currencyMode = settings.currencyMode,
                                exchangeRate = settings.khrExchangeRate
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Animated Mobile Bottom Sheet
                AnimatedVisibility(
                    visible = cart.isNotEmpty(),
                    // Floating rounded card above the bar, like the cards on the other pages
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = barBottom + 8.dp),
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                OrderBottomSheet(
                    cart = cart,
                    cashierName = settings.cashierName,
                    onCashierNameChange = { viewModel.setCashierName(it) },
                    paymentMethod = paymentMethod,
                    onPaymentMethodChange = { viewModel.selectPaymentMethod(it) },
                    onAddToCart = { viewModel.addToCart(it) },
                    onRemoveFromCart = { viewModel.removeFromCart(it) },
                    onRemoveEntireLine = { viewModel.removeEntireLineFromCart(it) },
                    onClearCart = { viewModel.clearCart() },
                    onPayClicked = { viewModel.openPaymentConfirmation() },
                    onHoldPaymentSelected = { method ->
                        viewModel.selectPaymentMethod(method)
                        viewModel.processOrder(method)
                    },
                    currencyMode = settings.currencyMode,
                    exchangeRate = settings.khrExchangeRate,
                    isExpanded = isOrderSheetExpanded,
                    onToggleExpanded = { isOrderSheetExpanded = !isOrderSheetExpanded },
                    modifier = Modifier
                        .heightIn(max = this@BoxWithConstraints.maxHeight * 0.8f)
                )
                }
            }
        }

        // Payment Confirmation Dialog
        if (isPaymentConfirmOpen) {
            PaymentConfirmationDialog(
                cart = cart,
                cashierName = settings.cashierName,
                paymentMethod = paymentMethod,
                currencyMode = settings.currencyMode,
                exchangeRate = settings.khrExchangeRate,
                isProcessing = isProcessingOrder,
                onConfirm = { viewModel.processOrder() },
                onDismiss = { viewModel.closePaymentConfirmation() }
            )
        }

        // Payment Success Dialog with Confetti
        completedOrder?.let { order ->
            PaymentSuccessDialog(
                order = order,
                currencyMode = settings.currencyMode,
                exchangeRate = settings.khrExchangeRate,
                onDismiss = { viewModel.dismissCompletedOrder() }
            )
        }
    }
}

@Composable
private fun OrderDashboardHeader(
    todaySales: Double,
    todayPaidCount: Int,
    lowStockCount: Int,
    currencyMode: String,
    exchangeRate: Double
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatisticCard(
            title = "Sales Today",
            value = CurrencyFormatter.format(todaySales, currencyMode, exchangeRate),
            icon = Icons.Default.AttachMoney,
            iconTint = SipCashGreen,
            modifier = Modifier.weight(1f)
        )
        StatisticCard(
            title = "Paid Orders",
            value = "$todayPaidCount",
            icon = Icons.Default.Receipt,
            iconTint = SipOceanBlue,
            modifier = Modifier.weight(1f)
        )
        StatisticCard(
            title = "Low Stock",
            value = "$lowStockCount",
            subtitle = if (lowStockCount > 0) "Needs restock" else "All good",
            icon = Icons.Default.Warning,
            iconTint = if (lowStockCount > 0) SipWarningAmber else Color(0xFF10B981),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun OrderSearchAndFilterRow(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    hideSoldOut: Boolean,
    soldOutCount: Int,
    onToggleHideSoldOut: () -> Unit,
    categories: List<String>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit
) {
    // Pinned above the grid so search + categories never scroll away.
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search drinks...", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_drinks_input")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onCategorySelected(null) },
                label = { Text("All", style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SipTealPrimary,
                    selectedLabelColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("category_chip_all")
            )
            categories.forEach { cat ->
                FilterChip(
                    selected = selectedCategory.equals(cat, ignoreCase = true),
                    onClick = { onCategorySelected(cat) },
                    label = { Text(cat, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SipTealPrimary,
                        selectedLabelColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
            FilterChip(
                selected = hideSoldOut,
                onClick = onToggleHideSoldOut,
                label = {
                    Text(
                        text = if (hideSoldOut) "Sold out hidden ($soldOutCount)" else "Hide sold out ($soldOutCount)",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (hideSoldOut) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("toggle_hide_sold_out")
            )
        }
    }
}

@Composable
private fun DrinkGrid(
    drinks: List<DrinkEntity>,
    cart: List<CartItem>,
    currencyMode: String,
    exchangeRate: Double,
    onAdd: (DrinkEntity) -> Unit,
    onRemoveOne: (DrinkEntity) -> Unit,
    columns: Int,
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp = 20.dp,
    header: (@Composable () -> Unit)? = null
) {
    run {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = modifier.testTag("drink_grid"),
            contentPadding = PaddingValues(top = 2.dp, bottom = bottomPadding),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (header != null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "dashboard_header") { header() }
            }
            if (drinks.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "empty_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No matching drinks found.\nCheck your search, category, or add drinks in the Stock tab.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            items(drinks, key = { it.id }) { drink ->
                val inCart = cart.firstOrNull { it.drink.id == drink.id }?.quantity ?: 0
                DrinkCard(
                    drink = drink,
                    quantityInCart = inCart,
                    currencyMode = currencyMode,
                    exchangeRate = exchangeRate,
                    onAdd = { onAdd(drink) },
                    onRemoveOne = { onRemoveOne(drink) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}
