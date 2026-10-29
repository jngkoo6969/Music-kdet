package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.CartItem
import com.example.data.model.OrderEntity
import com.example.data.model.PaymentMethod
import com.example.ui.theme.SipAbaBlue
import com.example.ui.theme.SipCashGreen
import com.example.ui.theme.SipTealPrimary
import com.example.util.CurrencyFormatter
import kotlin.random.Random

@Composable
fun PaymentConfirmationDialog(
    cart: List<CartItem>,
    cashierName: String,
    paymentMethod: PaymentMethod,
    currencyMode: String,
    exchangeRate: Double,
    isProcessing: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val totalAmount = cart.sumOf { it.lineTotal }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        modifier = Modifier.testTag("payment_confirmation_dialog"),
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Confirm Payment",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Cashier & Method banner
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cashier: $cashierName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (paymentMethod == PaymentMethod.CASH) SipCashGreen.copy(alpha = 0.2f) else SipAbaBlue.copy(alpha = 0.2f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (paymentMethod == PaymentMethod.CASH) Icons.Default.Payments else Icons.Default.QrCode2,
                                contentDescription = null,
                                tint = if (paymentMethod == PaymentMethod.CASH) SipCashGreen else SipAbaBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = paymentMethod.label,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (paymentMethod == PaymentMethod.CASH) SipCashGreen else SipAbaBlue
                            )
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // Items list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(cart, key = { it.drink.id }) { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${item.quantity}x ${item.drink.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = CurrencyFormatter.format(item.lineTotal, currencyMode, exchangeRate),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // Total display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total Amount:",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = CurrencyFormatter.format(totalAmount, currencyMode, exchangeRate),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = SipTealPrimary
                            )
                        )
                        if (currencyMode == "USD") {
                            Text(
                                text = CurrencyFormatter.formatRiel(totalAmount, exchangeRate),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isProcessing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (paymentMethod == PaymentMethod.CASH) SipCashGreen else SipAbaBlue
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("confirm_charge_button")
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Confirm & Charge")
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isProcessing,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PaymentSuccessDialog(
    order: OrderEntity,
    currencyMode: String,
    exchangeRate: Double,
    onDismiss: () -> Unit
) {
    val checkScale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        checkScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("payment_success_dialog"),
            contentAlignment = Alignment.Center
        ) {
            // Confetti canvas falling down
            ConfettiEffect()

            // Success Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Checkmark icon
                    Box(
                        modifier = Modifier
                            .scale(checkScale.value)
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(SipCashGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Success",
                            tint = Color.White,
                            modifier = Modifier.size(46.dp)
                        )
                    }

                    Text(
                        text = "Payment Received!",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Order Details
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Order ID:", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    order.orderId,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Payment Method:", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    order.paymentMethod,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (order.paymentMethod == "CASH") SipCashGreen else SipAbaBlue
                                    )
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Cashier:", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    order.cashierName,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Amount Paid:", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        CurrencyFormatter.format(order.orderTotal, currencyMode, exchangeRate),
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = SipTealPrimary
                                        )
                                    )
                                    if (currencyMode == "USD") {
                                        Text(
                                            CurrencyFormatter.formatRiel(order.orderTotal, exchangeRate),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SipTealPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("dismiss_success_button")
                    ) {
                        Text(
                            text = "Start Next Order",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

private data class ConfettiParticle(
    val initialX: Float,
    val initialY: Float,
    val speedY: Float,
    val speedX: Float,
    val color: Color,
    val size: Float,
    val rotationSpeed: Float
)

@Composable
fun ConfettiEffect() {
    val particles = remember {
        val colors = listOf(
            Color(0xFF10B981), Color(0xFF0284C7), Color(0xFFF59E0B),
            Color(0xFFEC4899), Color(0xFF8B5CF6), Color(0xFFEAB308),
            Color(0xFF06B6D4)
        )
        List(60) {
            ConfettiParticle(
                initialX = Random.nextFloat(),
                initialY = Random.nextFloat() * -0.5f,
                speedY = 0.3f + Random.nextFloat() * 0.7f,
                speedX = (Random.nextFloat() - 0.5f) * 0.2f,
                color = colors.random(),
                size = 12f + Random.nextFloat() * 16f,
                rotationSpeed = (Random.nextFloat() - 0.5f) * 720f
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "confettiTransition")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "confettiProgress"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        for (p in particles) {
            val y = (p.initialY + p.speedY * progress) % 1.2f * canvasHeight
            val x = ((p.initialX + p.speedX * progress) % 1.0f + 1.0f) % 1.0f * canvasWidth
            val rot = progress * p.rotationSpeed

            rotate(rot, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color,
                    topLeft = Offset(x - p.size / 2, y - p.size / 4),
                    size = Size(p.size, p.size / 2)
                )
            }
        }
    }
}
