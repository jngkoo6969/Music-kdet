package com.example.ui.components

import com.example.ui.theme.AppCard
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SipAbaBlue
import com.example.ui.theme.SipCashGreen
import com.example.ui.theme.SipOceanBlue
import com.example.ui.theme.SipTealPrimary
import com.example.util.CurrencyFormatter
import com.example.util.DayStat
import com.example.util.DrinkRevenueStat
import com.example.util.HourlyStat
import com.example.util.WeekdayStat
import java.util.Locale

@Composable
fun DailySalesBarChart(
    dailyStats: List<DayStat>,
    currencyMode: String,
    modifier: Modifier = Modifier
) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Daily Sales Trend",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            if (dailyStats.isEmpty() || dailyStats.all { it.revenue == 0.0 }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No sales recorded in this period",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val maxRevenue = dailyStats.maxOf { it.revenue }.coerceAtLeast(10.0)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(top = 16.dp, bottom = 4.dp)
                ) {
                    val barCount = dailyStats.size
                    val spacing = 12.dp.toPx()
                    val totalSpacing = spacing * (barCount + 1)
                    val barWidth = ((size.width - totalSpacing) / barCount).coerceIn(12f, 40f)

                    dailyStats.forEachIndexed { index, stat ->
                        val barHeight = (stat.revenue / maxRevenue).toFloat() * (size.height - 30.dp.toPx())
                        val x = spacing + index * (barWidth + spacing)
                        val y = size.height - 20.dp.toPx() - barHeight

                        // Draw Bar
                        drawRoundRect(
                            color = SipTealPrimary,
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight.coerceAtLeast(4f)),
                            cornerRadius = CornerRadius(8f, 8f)
                        )

                        // Label
                        drawContext.canvas.nativeCanvas.apply {
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.GRAY
                                textSize = 22f
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                            drawText(stat.dateLabel, x + barWidth / 2, size.height, paint)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HourlySalesChart(
    hourlyStats: List<HourlyStat>,
    modifier: Modifier = Modifier
) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Sales by Hour (24h)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            val maxRev = hourlyStats.maxOfOrNull { it.revenue }?.coerceAtLeast(1.0) ?: 1.0

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(top = 16.dp)
            ) {
                val barWidth = (size.width / 24f) * 0.7f
                val spacing = (size.width / 24f) * 0.3f

                hourlyStats.forEach { stat ->
                    val barH = (stat.revenue / maxRev).toFloat() * (size.height - 20.dp.toPx())
                    val x = stat.hour * (barWidth + spacing)
                    val y = size.height - 18.dp.toPx() - barH

                    val barColor = if (stat.revenue > 0) SipOceanBlue else Color.LightGray.copy(alpha = 0.3f)

                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barH.coerceAtLeast(3f)),
                        cornerRadius = CornerRadius(4f, 4f)
                    )

                    if (stat.hour % 4 == 0) {
                        drawContext.canvas.nativeCanvas.apply {
                            val paint = android.graphics.Paint().apply {
                                this.color = android.graphics.Color.GRAY
                                textSize = 20f
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                            drawText("${stat.hour}h", x + barWidth / 2, size.height, paint)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeekdaySalesChart(
    weekdayStats: List<WeekdayStat>,
    modifier: Modifier = Modifier
) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Sales by Weekday",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            val maxRev = weekdayStats.maxOfOrNull { it.revenue }?.coerceAtLeast(1.0) ?: 1.0

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .padding(top = 14.dp)
            ) {
                val count = weekdayStats.size
                val barW = (size.width / count) * 0.6f
                val spacing = (size.width / count) * 0.4f

                weekdayStats.forEachIndexed { index, stat ->
                    val barH = (stat.revenue / maxRev).toFloat() * (size.height - 20.dp.toPx())
                    val x = index * (barW + spacing) + spacing / 2
                    val y = size.height - 18.dp.toPx() - barH

                    drawRoundRect(
                        color = SipTealPrimary,
                        topLeft = Offset(x, y),
                        size = Size(barW, barH.coerceAtLeast(4f)),
                        cornerRadius = CornerRadius(6f, 6f)
                    )

                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.GRAY
                            textSize = 22f
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        drawText(stat.dayName, x + barW / 2, size.height, paint)
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentMethodSplitCard(
    cashRevenue: Double,
    cashPercentage: Double,
    abaRevenue: Double,
    abaPercentage: Double,
    currencyMode: String,
    modifier: Modifier = Modifier
) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Payment Method Split",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            // Split horizontal bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (cashPercentage > 0) {
                    Box(
                        modifier = Modifier
                            .weight(cashPercentage.toFloat().coerceAtLeast(0.01f))
                            .fillMaxWidth()
                            .background(SipCashGreen)
                    )
                }
                if (abaPercentage > 0) {
                    Box(
                        modifier = Modifier
                            .weight(abaPercentage.toFloat().coerceAtLeast(0.01f))
                            .fillMaxWidth()
                            .background(SipAbaBlue)
                    )
                }
            }

            // Legend & Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(SipCashGreen)
                    )
                    Column {
                        Text(
                            text = "Cash (${String.format(Locale.US, "%.1f", cashPercentage)}%)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = CurrencyFormatter.format(cashRevenue, currencyMode),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(SipAbaBlue)
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "ABA (${String.format(Locale.US, "%.1f", abaPercentage)}%)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = CurrencyFormatter.format(abaRevenue, currencyMode),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TopDrinksRevenueCard(
    topDrinks: List<DrinkRevenueStat>,
    currencyMode: String,
    modifier: Modifier = Modifier
) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Revenue by Drink",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            if (topDrinks.isEmpty()) {
                Text(
                    text = "No sales data available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val maxRev = topDrinks.maxOf { it.revenue }.coerceAtLeast(1.0)
                topDrinks.forEach { drink ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${drink.drinkName} (${drink.quantitySold} sold)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = CurrencyFormatter.format(drink.revenue, currencyMode),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = SipTealPrimary
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((drink.revenue / maxRev).toFloat())
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SipTealPrimary)
                            )
                        }
                    }
                }
            }
        }
    }
}
