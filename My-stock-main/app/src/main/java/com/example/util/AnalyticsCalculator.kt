package com.example.util

import com.example.data.model.DateFilter
import com.example.data.model.OrderStatus
import com.example.data.model.OrderWithItems
import com.example.data.model.PaymentMethod
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DateRange(
    val startMillis: Long,
    val endMillis: Long,
    val previousStartMillis: Long,
    val previousEndMillis: Long
)

data class DrinkTrend(
    val name: String,
    val quantityCurrent: Int,
    val quantityPrevious: Int,
    val percentChange: Double
)

data class HourlyStat(
    val hour: Int,
    val revenue: Double,
    val orderCount: Int
)

data class WeekdayStat(
    val dayName: String,
    val dayIndex: Int,
    val revenue: Double
)

data class DayStat(
    val dateLabel: String,
    val dateMillis: Long,
    val revenue: Double,
    val orderCount: Int
)

data class DrinkRevenueStat(
    val drinkName: String,
    val quantitySold: Int,
    val revenue: Double
)

data class AnalysisReport(
    val filter: DateFilter,
    val startMillis: Long,
    val endMillis: Long,
    val revenue: Double,
    val paidOrdersCount: Int,
    val averageOrderValue: Double,
    val drinksSoldCount: Int,
    val voidOrdersCount: Int,
    val voidAmount: Double,
    val voidRate: Double, // in percent
    // Comparison with previous equivalent period
    val previousRevenue: Double,
    val revenueChangePercent: Double,
    val previousOrdersCount: Int,
    val ordersChangePercent: Double,
    val previousAvgOrderValue: Double,
    val avgOrderChangePercent: Double,
    val previousDrinksSold: Int,
    val drinksSoldChangePercent: Double,
    // Payment split
    val cashRevenue: Double,
    val cashPercentage: Double,
    val abaRevenue: Double,
    val abaPercentage: Double,
    // Same time yesterday
    val sameTimeYesterdayRevenue: Double,
    val todayRevenueUpToNow: Double,
    // Monthly goal & estimate
    val monthlyGoal: Double,
    val monthlyGoalProgressPercent: Double,
    val estimatedMonthEndRevenue: Double,
    val goalPeriodLabel: String,
    val isGoalMonthFinal: Boolean,
    // Highlights
    val busiestHour: Int, // 0..23, -1 if no orders
    val bestSalesDay: String,
    val bestSalesDayRevenue: Double,
    val bestWeekday: String,
    val dailyAverageRevenue: Double,
    val mostSoldDrink: String,
    val mostSoldDrinkQty: Int,
    val topEarningDrink: String,
    val topEarningDrinkRevenue: Double,
    val topCashier: String,
    val topCashierRevenue: Double,
    // Trend & Breakdown Charts
    val drinkTrends: List<DrinkTrend>,
    val hourlyStats: List<HourlyStat>,
    val weekdayStats: List<WeekdayStat>,
    val dailyStats: List<DayStat>,
    val topDrinksByRevenue: List<DrinkRevenueStat>,
    val heatmapDays: Map<String, Double> // YYYY-MM-DD to revenue
)

object AnalyticsCalculator {

    private const val DAY_MS = 86_400_000L

    private fun startOfDay(base: Calendar = Calendar.getInstance()): Calendar =
        (base.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    private fun endOfDay(base: Calendar): Calendar =
        (base.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

    /** First day 00:00:00.000 of the month that is [monthsAgo] months before now. */
    private fun monthStart(monthsAgo: Int): Calendar {
        val c = startOfDay()
        c.set(Calendar.DAY_OF_MONTH, 1) // set day first so add(MONTH) can never overflow (e.g. Oct 31 -> Sep 31)
        c.add(Calendar.MONTH, -monthsAgo)
        return c
    }

    /** Last day 23:59:59.999 of the month that starts at [start]. */
    private fun monthEnd(start: Calendar): Calendar {
        val c = start.clone() as Calendar
        c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH))
        return endOfDay(c)
    }

    fun computeDateRange(filter: DateFilter, customStart: Long = 0, customEnd: Long = 0): DateRange {
        val todayStart = startOfDay()

        return when (filter) {
            DateFilter.TODAY -> {
                val start = todayStart.timeInMillis
                val end = endOfDay(todayStart).timeInMillis
                val prevStartCal = (todayStart.clone() as Calendar).apply { add(Calendar.DATE, -1) }
                DateRange(start, end, prevStartCal.timeInMillis, start - 1)
            }
            DateFilter.YESTERDAY -> {
                val yStart = (todayStart.clone() as Calendar).apply { add(Calendar.DATE, -1) }
                val start = yStart.timeInMillis
                val end = todayStart.timeInMillis - 1
                val prevStartCal = (yStart.clone() as Calendar).apply { add(Calendar.DATE, -1) }
                DateRange(start, end, prevStartCal.timeInMillis, start - 1)
            }
            DateFilter.LAST_7_DAYS -> {
                val end = endOfDay(todayStart).timeInMillis
                val startCal = (todayStart.clone() as Calendar).apply { add(Calendar.DATE, -6) }
                val start = startCal.timeInMillis
                val prevStartCal = (startCal.clone() as Calendar).apply { add(Calendar.DATE, -7) }
                DateRange(start, end, prevStartCal.timeInMillis, start - 1)
            }
            DateFilter.THIS_MONTH -> {
                val startCal = monthStart(0)
                val start = startCal.timeInMillis
                val end = monthEnd(startCal).timeInMillis

                val prevStartCal = monthStart(1)
                val prevStart = prevStartCal.timeInMillis
                val prevFullEnd = monthEnd(prevStartCal).timeInMillis
                // Compare like-for-like: same number of elapsed days as this month so far.
                val elapsed = minOf(System.currentTimeMillis(), end) - start
                val prevEnd = minOf(prevFullEnd, prevStart + elapsed)
                DateRange(start, end, prevStart, prevEnd)
            }
            DateFilter.LAST_MONTH -> {
                val startCal = monthStart(1)
                val start = startCal.timeInMillis
                val end = monthEnd(startCal).timeInMillis

                val prevStartCal = monthStart(2)
                val prevStart = prevStartCal.timeInMillis
                val prevEnd = monthEnd(prevStartCal).timeInMillis
                DateRange(start, end, prevStart, prevEnd)
            }
            DateFilter.CUSTOM -> {
                val start = startOfDay(Calendar.getInstance().apply { timeInMillis = customStart }).timeInMillis
                val end = endOfDay(Calendar.getInstance().apply { timeInMillis = maxOf(customEnd, customStart) }).timeInMillis
                val length = end - start + 1
                val prevEnd = start - 1
                val prevStart = prevEnd - length + 1
                DateRange(start, end, prevStart, prevEnd)
            }
        }
    }

    fun calculateReport(
        allOrders: List<OrderWithItems>,
        filter: DateFilter,
        dateRange: DateRange,
        monthlyGoal: Double = 1500.0
    ): AnalysisReport {
        val currentOrders = allOrders.filter { it.order.orderTimestamp in dateRange.startMillis..dateRange.endMillis }
        val previousOrders = allOrders.filter { it.order.orderTimestamp in dateRange.previousStartMillis..dateRange.previousEndMillis }

        val currentPaid = currentOrders.filter { it.order.status == OrderStatus.PAID.name }
        val currentVoid = currentOrders.filter { it.order.status == OrderStatus.VOID.name }

        val previousPaid = previousOrders.filter { it.order.status == OrderStatus.PAID.name }

        val revenue = currentPaid.sumOf { it.order.orderTotal }
        val paidOrdersCount = currentPaid.size
        val averageOrderValue = if (paidOrdersCount > 0) revenue / paidOrdersCount else 0.0

        val drinksSoldCount = currentPaid.sumOf { o -> o.items.sumOf { it.quantity } }
        val voidOrdersCount = currentVoid.size
        val voidAmount = currentVoid.sumOf { it.order.orderTotal }
        val totalOrderAttempts = paidOrdersCount + voidOrdersCount
        val voidRate = if (totalOrderAttempts > 0) (voidOrdersCount.toDouble() / totalOrderAttempts) * 100.0 else 0.0

        // Previous period metrics
        val previousRevenue = previousPaid.sumOf { it.order.orderTotal }
        val previousOrdersCount = previousPaid.size
        val previousAvgOrderValue = if (previousOrdersCount > 0) previousRevenue / previousOrdersCount else 0.0
        val previousDrinksSold = previousPaid.sumOf { o -> o.items.sumOf { it.quantity } }

        val revenueChangePercent = calculatePercentChange(previousRevenue, revenue)
        val ordersChangePercent = calculatePercentChange(previousOrdersCount.toDouble(), paidOrdersCount.toDouble())
        val avgOrderChangePercent = calculatePercentChange(previousAvgOrderValue, averageOrderValue)
        val drinksSoldChangePercent = calculatePercentChange(previousDrinksSold.toDouble(), drinksSoldCount.toDouble())

        // Cash vs ABA split
        val cashOrders = currentPaid.filter { it.order.paymentMethod.equals(PaymentMethod.CASH.name, ignoreCase = true) }
        val abaOrders = currentPaid.filter { it.order.paymentMethod.equals(PaymentMethod.ABA.name, ignoreCase = true) }
        val cashRevenue = cashOrders.sumOf { it.order.orderTotal }
        val abaRevenue = abaOrders.sumOf { it.order.orderTotal }
        val cashPercentage = if (revenue > 0) (cashRevenue / revenue) * 100.0 else 0.0
        val abaPercentage = if (revenue > 0) (abaRevenue / revenue) * 100.0 else 0.0

        // Same time yesterday calculation
        val now = System.currentTimeMillis()
        val calNow = Calendar.getInstance()
        val currentHour = calNow.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calNow.get(Calendar.MINUTE)

        val calTodayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val calYestStart = (calTodayStart.clone() as Calendar).apply { add(Calendar.DATE, -1) }
        val calYestUpToNow = (calYestStart.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, currentHour)
            set(Calendar.MINUTE, currentMinute)
            set(Calendar.SECOND, 59)
        }

        val todayRevenueUpToNow = allOrders.filter {
            it.order.status == OrderStatus.PAID.name &&
            it.order.orderTimestamp in calTodayStart.timeInMillis..now
        }.sumOf { it.order.orderTotal }

        val sameTimeYesterdayRevenue = allOrders.filter {
            it.order.status == OrderStatus.PAID.name &&
            it.order.orderTimestamp in calYestStart.timeInMillis..calYestUpToNow.timeInMillis
        }.sumOf { it.order.orderTotal }

        // Monthly goal progress & estimate.
        // For "Last Month" we report that finished month; otherwise month-to-date for the current month.
        val isLastMonthView = filter == DateFilter.LAST_MONTH
        val thisMonthCal = Calendar.getInstance()
        val dayOfMonth = thisMonthCal.get(Calendar.DAY_OF_MONTH)
        val maxDaysInMonth = thisMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val monthToDateRevenue = if (isLastMonthView) {
            revenue
        } else {
            val startOfMonth = monthStart(0).timeInMillis
            allOrders.filter {
                it.order.status == OrderStatus.PAID.name && it.order.orderTimestamp >= startOfMonth
            }.sumOf { it.order.orderTotal }
        }

        val monthlyGoalProgressPercent = if (monthlyGoal > 0) (monthToDateRevenue / monthlyGoal) * 100.0 else 0.0
        val estimatedMonthEndRevenue = when {
            isLastMonthView -> monthToDateRevenue // month is over: actual total
            dayOfMonth > 0 -> (monthToDateRevenue / dayOfMonth) * maxDaysInMonth
            else -> monthToDateRevenue
        }
        val goalPeriodLabel = if (isLastMonthView) {
            SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(dateRange.startMillis))
        } else {
            SimpleDateFormat("MMMM yyyy", Locale.US).format(Date())
        }

        // Hourly breakdown (0..23)
        val hourMap = (0..23).associateWith { mutableListOf<OrderWithItems>() }
        val calItem = Calendar.getInstance()
        for (order in currentPaid) {
            calItem.timeInMillis = order.order.orderTimestamp
            val h = calItem.get(Calendar.HOUR_OF_DAY)
            hourMap[h]?.add(order)
        }
        val hourlyStats = (0..23).map { h ->
            val list = hourMap[h] ?: emptyList()
            HourlyStat(
                hour = h,
                revenue = list.sumOf { it.order.orderTotal },
                orderCount = list.size
            )
        }
        val busiestHour = hourlyStats.maxByOrNull { it.revenue }?.takeIf { it.revenue > 0 }?.hour ?: -1

        // Weekday breakdown (Mon..Sun)
        val weekdayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val weekdayRevenue = DoubleArray(7)
        for (order in currentPaid) {
            calItem.timeInMillis = order.order.orderTimestamp
            val dayOfWeek = calItem.get(Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon...
            val idx = (dayOfWeek + 5) % 7 // Convert to 0=Mon, 6=Sun
            weekdayRevenue[idx] += order.order.orderTotal
        }
        val weekdayStats = weekdayNames.mapIndexed { idx, name ->
            WeekdayStat(dayName = name, dayIndex = idx, revenue = weekdayRevenue[idx])
        }
        val bestWeekday = weekdayStats.maxByOrNull { it.revenue }?.takeIf { it.revenue > 0 }?.dayName ?: "N/A"

        // Daily breakdown
        val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val displayDayFormat = SimpleDateFormat("MMM d", Locale.US)
        val dailyMap = mutableMapOf<String, Pair<Double, Int>>()
        val heatmapDays = mutableMapOf<String, Double>()

        for (order in currentPaid) {
            val key = dayFormat.format(Date(order.order.orderTimestamp))
            val current = dailyMap[key] ?: Pair(0.0, 0)
            dailyMap[key] = Pair(current.first + order.order.orderTotal, current.second + 1)
            heatmapDays[key] = (heatmapDays[key] ?: 0.0) + order.order.orderTotal
        }

        val dailyStats = dailyMap.map { (key, pair) ->
            val date = try { dayFormat.parse(key) } catch (_: Exception) { null }
            val label = if (date != null) displayDayFormat.format(date) else key
            DayStat(
                dateLabel = label,
                dateMillis = date?.time ?: 0L,
                revenue = pair.first,
                orderCount = pair.second
            )
        }.sortedBy { it.dateMillis }

        val bestDayEntry = dailyMap.maxByOrNull { it.value.first }
        val bestSalesDay = bestDayEntry?.key?.let {
            try {
                displayDayFormat.format(dayFormat.parse(it)!!)
            } catch (_: Exception) {
                it
            }
        } ?: "N/A"
        val bestSalesDayRevenue = bestDayEntry?.value?.first ?: 0.0

        // Count whole days in the range; for an in-progress range only count days elapsed so far.
        val effectiveEnd = minOf(dateRange.endMillis, System.currentTimeMillis())
        val daysCount = maxOf(
            1,
            Math.ceil((effectiveEnd - dateRange.startMillis + 1).toDouble() / DAY_MS).toInt()
        )
        val dailyAverageRevenue = revenue / daysCount

        // Drink statistics
        val drinkQtyMap = mutableMapOf<String, Int>()
        val drinkRevMap = mutableMapOf<String, Double>()
        for (order in currentPaid) {
            for (item in order.items) {
                val name = item.historicalDrinkName
                drinkQtyMap[name] = (drinkQtyMap[name] ?: 0) + item.quantity
                drinkRevMap[name] = (drinkRevMap[name] ?: 0.0) + item.lineTotal
            }
        }

        val mostSoldEntry = drinkQtyMap.maxByOrNull { it.value }
        val mostSoldDrink = mostSoldEntry?.key ?: "N/A"
        val mostSoldDrinkQty = mostSoldEntry?.value ?: 0

        val topEarningEntry = drinkRevMap.maxByOrNull { it.value }
        val topEarningDrink = topEarningEntry?.key ?: "N/A"
        val topEarningDrinkRevenue = topEarningEntry?.value ?: 0.0

        // Cashier statistics
        val cashierMap = mutableMapOf<String, Double>()
        for (order in currentPaid) {
            val name = order.order.cashierName.ifBlank { "Unknown" }
            cashierMap[name] = (cashierMap[name] ?: 0.0) + order.order.orderTotal
        }
        val topCashierEntry = cashierMap.maxByOrNull { it.value }
        val topCashier = topCashierEntry?.key ?: "N/A"
        val topCashierRevenue = topCashierEntry?.value ?: 0.0

        // Drink Trends (comparing current vs previous period quantities)
        val prevDrinkQtyMap = mutableMapOf<String, Int>()
        for (order in previousPaid) {
            for (item in order.items) {
                val name = item.historicalDrinkName
                prevDrinkQtyMap[name] = (prevDrinkQtyMap[name] ?: 0) + item.quantity
            }
        }

        val allDrinkNames = (drinkQtyMap.keys + prevDrinkQtyMap.keys).distinct()
        val drinkTrends = allDrinkNames.map { name ->
            val curQty = drinkQtyMap[name] ?: 0
            val prevQty = prevDrinkQtyMap[name] ?: 0
            val pChange = calculatePercentChange(prevQty.toDouble(), curQty.toDouble())
            DrinkTrend(name, curQty, prevQty, pChange)
        }.sortedByDescending { it.quantityCurrent }.take(8)

        val topDrinksByRevenue = drinkRevMap.map { (name, rev) ->
            DrinkRevenueStat(
                drinkName = name,
                quantitySold = drinkQtyMap[name] ?: 0,
                revenue = rev
            )
        }.sortedByDescending { it.revenue }.take(6)

        return AnalysisReport(
            filter = filter,
            startMillis = dateRange.startMillis,
            endMillis = dateRange.endMillis,
            revenue = revenue,
            paidOrdersCount = paidOrdersCount,
            averageOrderValue = averageOrderValue,
            drinksSoldCount = drinksSoldCount,
            voidOrdersCount = voidOrdersCount,
            voidAmount = voidAmount,
            voidRate = voidRate,
            previousRevenue = previousRevenue,
            revenueChangePercent = revenueChangePercent,
            previousOrdersCount = previousOrdersCount,
            ordersChangePercent = ordersChangePercent,
            previousAvgOrderValue = previousAvgOrderValue,
            avgOrderChangePercent = avgOrderChangePercent,
            previousDrinksSold = previousDrinksSold,
            drinksSoldChangePercent = drinksSoldChangePercent,
            cashRevenue = cashRevenue,
            cashPercentage = cashPercentage,
            abaRevenue = abaRevenue,
            abaPercentage = abaPercentage,
            sameTimeYesterdayRevenue = sameTimeYesterdayRevenue,
            todayRevenueUpToNow = todayRevenueUpToNow,
            monthlyGoal = monthlyGoal,
            monthlyGoalProgressPercent = monthlyGoalProgressPercent,
            estimatedMonthEndRevenue = estimatedMonthEndRevenue,
            goalPeriodLabel = goalPeriodLabel,
            isGoalMonthFinal = isLastMonthView,
            busiestHour = busiestHour,
            bestSalesDay = bestSalesDay,
            bestSalesDayRevenue = bestSalesDayRevenue,
            bestWeekday = bestWeekday,
            dailyAverageRevenue = dailyAverageRevenue,
            mostSoldDrink = mostSoldDrink,
            mostSoldDrinkQty = mostSoldDrinkQty,
            topEarningDrink = topEarningDrink,
            topEarningDrinkRevenue = topEarningDrinkRevenue,
            topCashier = topCashier,
            topCashierRevenue = topCashierRevenue,
            drinkTrends = drinkTrends,
            hourlyStats = hourlyStats,
            weekdayStats = weekdayStats,
            dailyStats = dailyStats,
            topDrinksByRevenue = topDrinksByRevenue,
            heatmapDays = heatmapDays
        )
    }

    fun calculatePercentChange(previous: Double, current: Double): Double {
        if (previous == 0.0) {
            return if (current > 0.0) 100.0 else 0.0
        }
        return ((current - previous) / previous) * 100.0
    }
}
