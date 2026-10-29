package com.example.ui.screens

import com.example.ui.theme.LocalBarPadding
import com.example.ui.theme.AppCard
import android.app.DatePickerDialog
import androidx.compose.foundation.background
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DateFilter
import com.example.ui.components.DailySalesBarChart
import com.example.ui.components.HourlySalesChart
import com.example.ui.components.PaymentMethodSplitCard
import com.example.ui.components.StatisticCard
import com.example.ui.components.TopDrinksRevenueCard
import com.example.ui.components.WeekdaySalesChart
import com.example.ui.theme.SipCashGreen
import com.example.ui.theme.SipErrorRed
import com.example.ui.theme.SipOceanBlue
import com.example.ui.theme.SipTealPrimary
import com.example.ui.theme.SipWarningAmber
import com.example.ui.viewmodel.ShopViewModel
import com.example.util.AnalysisReport
import com.example.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun AnalysisScreen(
    viewModel: ShopViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val report by viewModel.analysisReport.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val currentFilter by viewModel.analysisFilter.collectAsStateWithLifecycle()
    val customRange by viewModel.customDateRange.collectAsStateWithLifecycle()

    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = LocalBarPadding.current.calculateTopPadding(), bottom = LocalBarPadding.current.calculateBottomPadding())
            .padding(16.dp)
            .testTag("analysis_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Title & Share Summary
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Order Analysis",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "${dateFormat.format(Date(report.startMillis))} - ${dateFormat.format(Date(report.endMillis))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { viewModel.shareSalesSummary(report, settings) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SipTealPrimary),
                modifier = Modifier.testTag("share_summary_button")
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share Summary")
            }
        }

        // Filter Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val quickFilters = listOf(
                DateFilter.TODAY,
                DateFilter.YESTERDAY,
                DateFilter.LAST_7_DAYS,
                DateFilter.THIS_MONTH,
                DateFilter.LAST_MONTH
            )
            quickFilters.forEach { filter ->
                FilterChip(
                    selected = currentFilter == filter,
                    onClick = { viewModel.setAnalysisFilter(filter) },
                    label = { Text(filter.label, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SipTealPrimary,
                        selectedLabelColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        // Custom Range Button
        OutlinedButton(
            onClick = {
                val cal = Calendar.getInstance().apply { timeInMillis = customRange.first }
                DatePickerDialog(
                    context,
                    { _, y1, m1, d1 ->
                        val startCal = Calendar.getInstance().apply {
                            set(y1, m1, d1, 0, 0, 0)
                        }
                        // Open end date picker
                        DatePickerDialog(
                            context,
                            { _, y2, m2, d2 ->
                                val endCal = Calendar.getInstance().apply {
                                    set(y2, m2, d2, 23, 59, 59)
                                }
                                viewModel.setCustomAnalysisRange(startCal.timeInMillis, endCal.timeInMillis)
                            },
                            y1, m1, d1
                        ).show()
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (currentFilter == DateFilter.CUSTOM) {
                    "Custom Range: ${dateFormat.format(Date(report.startMillis))} to ${dateFormat.format(Date(report.endMillis))}"
                } else {
                    "Pick Custom Date Range..."
                }
            )
        }

        // Core Metrics with Previous Period Comparisons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ComparisonStatCard(
                title = "Total Revenue",
                value = CurrencyFormatter.format(report.revenue, settings.currencyMode, settings.khrExchangeRate),
                changePercent = report.revenueChangePercent,
                modifier = Modifier.weight(1f)
            )
            ComparisonStatCard(
                title = "Paid Orders",
                value = "${report.paidOrdersCount}",
                changePercent = report.ordersChangePercent,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ComparisonStatCard(
                title = "Avg Order Value",
                value = CurrencyFormatter.format(report.averageOrderValue, settings.currencyMode, settings.khrExchangeRate),
                changePercent = report.avgOrderChangePercent,
                modifier = Modifier.weight(1f)
            )
            ComparisonStatCard(
                title = "Drinks Sold",
                value = "${report.drinksSoldCount}",
                changePercent = report.drinksSoldChangePercent,
                modifier = Modifier.weight(1f)
            )
        }

        // Void orders metric
        if (report.voidOrdersCount > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SipErrorRed.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Voided Orders", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = SipErrorRed))
                        Text(
                            "${report.voidOrdersCount} orders voided (${String.format(Locale.US, "%.1f", report.voidRate)}% void rate)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        CurrencyFormatter.format(report.voidAmount, settings.currencyMode, settings.khrExchangeRate),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = SipErrorRed)
                    )
                }
            }
        }

        // Monthly Sales Goal & Run-Rate Estimate
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Monthly Goal - ${report.goalPeriodLabel}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.1f", report.monthlyGoalProgressPercent)}%",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = SipTealPrimary
                        )
                    )
                }

                LinearProgressIndicator(
                    progress = { (report.monthlyGoalProgressPercent / 100f).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = SipTealPrimary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Goal: ${CurrencyFormatter.format(report.monthlyGoal, settings.currencyMode, settings.khrExchangeRate)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${if (report.isGoalMonthFinal) "Final" else "Est. Month-End"}: ${CurrencyFormatter.format(report.estimatedMonthEndRevenue, settings.currencyMode, settings.khrExchangeRate)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = SipOceanBlue
                    )
                }
            }
        }

        // Key Highlights Row: Busiest Hour, Best Day, Best Weekday, Top Cashier
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
                Text(
                    text = "Key Highlights",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                HighlightRow(
                    icon = Icons.Default.Schedule,
                    title = "Busiest Hour",
                    value = if (report.busiestHour >= 0) "${report.busiestHour}:00 - ${report.busiestHour + 1}:00" else "N/A"
                )
                HighlightRow(
                    icon = Icons.Default.Star,
                    title = "Best Sales Day",
                    value = if (report.bestSalesDay != "N/A") "${report.bestSalesDay} (${CurrencyFormatter.format(report.bestSalesDayRevenue, settings.currencyMode)})" else "N/A"
                )
                HighlightRow(
                    icon = Icons.Default.DateRange,
                    title = "Best Weekday",
                    value = report.bestWeekday
                )
                HighlightRow(
                    icon = Icons.Default.LocalDrink,
                    title = "Most Sold Drink",
                    value = if (report.mostSoldDrink != "N/A") "${report.mostSoldDrink} (${report.mostSoldDrinkQty} cups)" else "N/A"
                )
                HighlightRow(
                    icon = Icons.Default.AttachMoney,
                    title = "Top-Earning Drink",
                    value = if (report.topEarningDrink != "N/A") "${report.topEarningDrink} (${CurrencyFormatter.format(report.topEarningDrinkRevenue, settings.currencyMode)})" else "N/A"
                )
                HighlightRow(
                    icon = Icons.Default.Person,
                    title = "Top Cashier",
                    value = if (report.topCashier != "N/A") "${report.topCashier} (${CurrencyFormatter.format(report.topCashierRevenue, settings.currencyMode)})" else "N/A"
                )
            }
        }

        // Drink Trends (Current vs Previous period)
        if (report.drinkTrends.isNotEmpty()) {
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
                    Text(
                        text = "Drink Sales Trends",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Compared with previous equivalent period",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    report.drinkTrends.forEach { trend ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(trend.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text(
                                    "${trend.quantityCurrent} sold (was ${trend.quantityPrevious})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                val isUp = trend.percentChange >= 0
                                Icon(
                                    imageVector = if (isUp) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    tint = if (isUp) SipCashGreen else SipErrorRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${if (isUp) "+" else ""}${String.format(Locale.US, "%.1f", trend.percentChange)}%",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isUp) SipCashGreen else SipErrorRed
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Interactive Charts: Daily Trend, Hourly, Weekday, Payment Split, Drink Revenue
        DailySalesBarChart(
            dailyStats = report.dailyStats,
            currencyMode = settings.currencyMode
        )

        HourlySalesChart(hourlyStats = report.hourlyStats)

        WeekdaySalesChart(weekdayStats = report.weekdayStats)

        PaymentMethodSplitCard(
            cashRevenue = report.cashRevenue,
            cashPercentage = report.cashPercentage,
            abaRevenue = report.abaRevenue,
            abaPercentage = report.abaPercentage,
            currencyMode = settings.currencyMode
        )

        TopDrinksRevenueCard(
            topDrinks = report.topDrinksByRevenue,
            currencyMode = settings.currencyMode
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ComparisonStatCard(
    title: String,
    value: String,
    changePercent: Double,
    modifier: Modifier = Modifier
) {
    val isPositive = changePercent >= 0

    AppCard(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = if (isPositive) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = null,
                    tint = if (isPositive) SipCashGreen else SipErrorRed,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "${if (isPositive) "+" else ""}${String.format(Locale.US, "%.1f", changePercent)}% vs prior",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isPositive) SipCashGreen else SipErrorRed
                    )
                )
            }
        }
    }
}

@Composable
private fun HighlightRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = SipTealPrimary, modifier = Modifier.size(18.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
    }
}
