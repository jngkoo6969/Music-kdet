package com.example.ui.components

import com.example.ui.theme.AppCard
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.DrinkEntity
import com.example.ui.theme.SipErrorRed
import com.example.ui.theme.SipTealPrimary
import com.example.ui.theme.SipWarningAmber
import com.example.util.CurrencyFormatter
import java.io.File

@Composable
fun DrinkCard(
    drink: DrinkEntity,
    quantityInCart: Int,
    currencyMode: String,
    exchangeRate: Double,
    onAdd: () -> Unit,
    onRemoveOne: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 420f),
        label = "pressScale"
    )

    val isSoldOut = drink.stock <= 0
    val isLowStock = !isSoldOut && drink.stock <= drink.lowStockThreshold

    val cardBaseColor = try {
        Color(android.graphics.Color.parseColor(drink.colorHex))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.surfaceVariant
    }

    AppCard(
        modifier = modifier
            .scale(scale)
            .testTag("drink_card_${drink.id}")
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isSoldOut,
                onClick = onAdd
            ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Image area with Stock badge and Cart badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                if (!drink.imageUri.isNullOrBlank() && File(drink.imageUri).exists()) {
                    AsyncImage(
                        model = File(drink.imageUri),
                        contentDescription = drink.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalDrink,
                            contentDescription = drink.name,
                            tint = SipTealPrimary.copy(alpha = 0.6f),
                            modifier = Modifier.size(52.dp)
                        )
                    }
                }

                // Sold Out Overlay
                if (isSoldOut) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SipErrorRed
                        ) {
                            Text(
                                text = "SOLD OUT",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Stock indicator badge (top start)
                if (!isSoldOut) {
                    val stockBadgeColor = if (isLowStock) SipWarningAmber else Color(0xFF10B981)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = stockBadgeColor,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = if (isLowStock) "${drink.stock} left!" else "Stock: ${drink.stock}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Cart Quantity Badge (top end)
                androidx.compose.animation.AnimatedVisibility(
                    visible = quantityInCart > 0,
                    modifier = Modifier.align(Alignment.TopEnd),
                    enter = androidx.compose.animation.scaleIn(
                        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 500f)
                    ) + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(SipTealPrimary)
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$quantityInCart",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }

            // Info and Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp) // fixed so every card is exactly the same size
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = drink.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = CurrencyFormatter.format(drink.price, currencyMode, exchangeRate),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = SipTealPrimary
                            )
                        )
                        if (currencyMode == "USD") {
                            Text(
                                text = CurrencyFormatter.formatRiel(drink.price, exchangeRate),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Plus / Minus actions
                    if (quantityInCart > 0 && !isSoldOut) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable(onClick = onRemoveOne),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Remove one",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(SipTealPrimary)
                                    .clickable(onClick = onAdd),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add one",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    } else if (!isSoldOut) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(SipTealPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add to cart",
                                tint = SipTealPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
