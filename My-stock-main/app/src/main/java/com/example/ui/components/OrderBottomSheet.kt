package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.CartItem
import com.example.data.model.PaymentMethod
import com.example.ui.theme.SipAbaBlue
import com.example.ui.theme.SipCashGreen
import com.example.ui.theme.SipErrorRed
import com.example.ui.theme.SipTealPrimary
import com.example.util.CurrencyFormatter
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@Composable
fun OrderBottomSheet(
    cart: List<CartItem>,
    cashierName: String,
    onCashierNameChange: (String) -> Unit,
    paymentMethod: PaymentMethod,
    onPaymentMethodChange: (PaymentMethod) -> Unit,
    onAddToCart: (com.example.data.model.DrinkEntity) -> Unit,
    onRemoveFromCart: (com.example.data.model.DrinkEntity) -> Unit,
    onRemoveEntireLine: (Long) -> Unit,
    onClearCart: () -> Unit,
    onPayClicked: () -> Unit,
    onHoldPaymentSelected: (PaymentMethod) -> Unit,
    currencyMode: String,
    exchangeRate: Double,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalAmount = cart.sumOf { it.lineTotal }
    val totalItemsCount = cart.sumOf { it.quantity }

    // Id of the single cart row whose Delete action is currently revealed (null = none)
    var openItemId by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                // Tap on any empty spot (not consumed by a button/field) closes the open row
                detectTapGestures { openItemId = null }
            }
            .testTag("order_panel"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 380f))
        ) {
            // Peek Bar Header (always visible)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (cart.isNotEmpty()) SipTealPrimary else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = "Cart",
                            tint = if (cart.isNotEmpty()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = if (cart.isEmpty()) "Current Order (Empty)" else "Current Order ($totalItemsCount items)",
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (cart.isNotEmpty()) {
                            Text(
                                text = CurrencyFormatter.formatDual(totalAmount, exchangeRate, currencyMode),
                                style = MaterialTheme.typography.labelSmall,
                                color = SipTealPrimary
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (cart.isNotEmpty() && !isExpanded) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SipTealPrimary
                        ) {
                            Text(
                                text = CurrencyFormatter.format(totalAmount, currencyMode, exchangeRate),
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expanded Order Content
            AnimatedVisibility(
                visible = isExpanded,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 0.dp)
                ) {
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    if (cart.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Your order is empty.\nTap drinks above to add them to your order.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        // Cart items list with swipe-to-reveal Remove button
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .padding(vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(cart, key = { it.drink.id }) { item ->
                                SwipeableCartItemRow(
                                    modifier = Modifier.animateItem(),
                                    item = item,
                                    isOpen = openItemId == item.drink.id.toString(),
                                    onOpenChanged = { open ->
                                        val id = item.drink.id.toString()
                                        if (open) openItemId = id
                                        else if (openItemId == id) openItemId = null
                                    },
                                    currencyMode = currencyMode,
                                    exchangeRate = exchangeRate,
                                    onIncrease = { onAddToCart(item.drink) },
                                    onDecrease = { onRemoveFromCart(item.drink) },
                                    onDeleteLine = { onRemoveEntireLine(item.drink.id) }
                                )
                            }
                        }

                        // Order Controls: Cashier input
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = cashierName,
                                onValueChange = onCashierNameChange,
                                label = { Text("Cashier Name") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("cashier_name_input"),
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedButton(
                                onClick = onClearCart,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = SipErrorRed),
                                modifier = Modifier.testTag("clear_cart_button")
                            ) {
                                Text("Clear")
                            }
                        }

                        // Payment Method Selection: Cash vs ABA
                        Text(
                            text = "Payment Method:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val isCash = paymentMethod == PaymentMethod.CASH
                            val isAba = paymentMethod == PaymentMethod.ABA

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onPaymentMethodChange(PaymentMethod.CASH) }
                                    .testTag("pay_method_cash"),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCash) SipCashGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = if (isCash) ButtonDefaults.outlinedButtonBorder else null
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Payments,
                                        contentDescription = "Cash",
                                        tint = if (isCash) SipCashGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Cash",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = if (isCash) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isCash) SipCashGreen else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                            }

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onPaymentMethodChange(PaymentMethod.ABA) }
                                    .testTag("pay_method_aba"),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isAba) SipAbaBlue.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = if (isAba) ButtonDefaults.outlinedButtonBorder else null
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCode2,
                                        contentDescription = "ABA",
                                        tint = if (isAba) SipAbaBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "ABA",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = if (isAba) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isAba) SipAbaBlue else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Pay Button with Hold-to-select shortcut
                        HoldToPayButton(
                            totalAmountFormatted = CurrencyFormatter.format(totalAmount, currencyMode, exchangeRate),
                            currentMethod = paymentMethod,
                            onTap = onPayClicked,
                            onHoldSelected = onHoldPaymentSelected,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SwipeableCartItemRow(
    item: CartItem,
    isOpen: Boolean,
    onOpenChanged: (Boolean) -> Unit,
    currencyMode: String,
    exchangeRate: Double,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onDeleteLine: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val actionWidth = 84.dp
    val actionWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { actionWidth.toPx() }
    val rowShape = RoundedCornerShape(14.dp)

    // 0f = closed, 1f = fully revealed. Drives fade/scale of the delete action.
    val revealProgress = (-offsetX.value / actionWidthPx).coerceIn(0f, 1f)

    val dragState = rememberDraggableState { delta ->
        coroutineScope.launch {
            offsetX.snapTo((offsetX.value + delta).coerceIn(-actionWidthPx, 0f))
        }
    }

    // Parent says this row is no longer the open one -> glide it back closed
    LaunchedEffect(isOpen) {
        if (!isOpen && offsetX.value != 0f) {
            offsetX.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
        }
    }

    fun settle(target: Float) {
        coroutineScope.launch {
            offsetX.animateTo(
                target,
                spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(rowShape)
    ) {
        // Delete action behind the row. Only composed while revealing, so the
        // closed row never shows red corners, and it fades/scales in with the swipe.
        if (revealProgress > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = revealProgress }
                    .background(SipErrorRed)
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(actionWidth)
                        .clickable {
                            // Delete right away. (Animating closed first raced with the
                            // close animation and cancelled the delete.)
                            onOpenChanged(false)
                            onDeleteLine()
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Item",
                        tint = Color.White,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer {
                                val s = 0.6f + 0.4f * revealProgress
                                scaleX = s
                                scaleY = s
                            }
                    )
                    Text(
                        text = "Delete",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        // Foreground content with draggable swipe
        Surface(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .fillMaxWidth()
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStarted = { onOpenChanged(true) },
                    onDragStopped = {
                        val open = offsetX.value < -actionWidthPx / 2
                        onOpenChanged(open)
                        settle(if (open) -actionWidthPx else 0f)
                    }
                ),
            shape = rowShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Thumbnail
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (!item.drink.imageUri.isNullOrBlank() && File(item.drink.imageUri).exists()) {
                        AsyncImage(
                            model = File(item.drink.imageUri),
                            contentDescription = item.drink.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.LocalDrink,
                            contentDescription = item.drink.name,
                            tint = SipTealPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Name & Price
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.drink.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                    Text(
                        text = "${CurrencyFormatter.format(item.drink.price, currencyMode, exchangeRate)} each",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Line Total
                Text(
                    text = CurrencyFormatter.format(item.lineTotal, currencyMode, exchangeRate),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = SipTealPrimary
                    )
                )

                // Quantity Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(onClick = onDecrease),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Decrease",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Text(
                        text = "${item.quantity}",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(SipTealPrimary)
                            .clickable(onClick = onIncrease),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Increase",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HoldToPayButton(
    totalAmountFormatted: String,
    currentMethod: PaymentMethod,
    onTap: () -> Unit,
    onHoldSelected: (PaymentMethod) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragX by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val activeColor = when {
        dragX < -60f -> SipCashGreen
        dragX > 60f -> SipAbaBlue
        currentMethod == PaymentMethod.CASH -> SipCashGreen
        else -> SipAbaBlue
    }

    val actionLabel = when {
        dragX < -60f -> "Release for CASH"
        dragX > 60f -> "Release for ABA"
        else -> "Pay $totalAmountFormatted (${currentMethod.label})"
    }

    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(activeColor)
            .testTag("pay_button")
            .draggable(
                state = rememberDraggableState { delta ->
                    dragX += delta
                    isDragging = true
                },
                orientation = Orientation.Horizontal,
                onDragStopped = {
                    if (dragX < -60f) {
                        onHoldSelected(PaymentMethod.CASH)
                    } else if (dragX > 60f) {
                        onHoldSelected(PaymentMethod.ABA)
                    } else {
                        onTap()
                    }
                    dragX = 0f
                    isDragging = false
                }
            )
            .clickable {
                if (!isDragging) {
                    onTap()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "← Cash",
                color = Color.White.copy(alpha = if (dragX < -30f) 1.0f else 0.5f),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
            )

            Text(
                text = actionLabel,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Text(
                text = "ABA →",
                color = Color.White.copy(alpha = if (dragX > 30f) 1.0f else 0.5f),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}
