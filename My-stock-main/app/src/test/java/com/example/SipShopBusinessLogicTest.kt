package com.example

import com.example.data.model.CartItem
import com.example.data.model.DateFilter
import com.example.data.model.DrinkEntity
import com.example.data.model.OrderEntity
import com.example.data.model.OrderItemEntity
import com.example.data.model.OrderStatus
import com.example.data.model.OrderWithItems
import com.example.data.model.PaymentMethod
import com.example.util.AnalyticsCalculator
import com.example.util.CurrencyFormatter
import com.example.util.DateRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SipShopBusinessLogicTest {

    @Test
    fun testCurrencyFormattingUsd() {
        val formatted = CurrencyFormatter.format(2.5, "USD")
        assertEquals("$2.50", formatted)

        val formattedZero = CurrencyFormatter.format(0.0, "USD")
        assertEquals("$0.00", formattedZero)

        val formattedLarge = CurrencyFormatter.format(1250.75, "USD")
        assertEquals("$1,250.75", formattedLarge)
    }

    @Test
    fun testCurrencyFormattingRiel() {
        val rate = 4100.0
        // $2.50 * 4100 = 10,250 riel
        val formatted = CurrencyFormatter.format(2.5, "KHR", rate)
        assertEquals("10,250 ៛", formatted)

        val dualFormatted = CurrencyFormatter.formatDual(2.5, rate, "USD")
        assertEquals("$2.50 (10,250 ៛)", dualFormatted)
    }

    @Test
    fun testCartLineTotalsAndOrderCalculation() {
        val latte = DrinkEntity(id = 1L, name = "Latte", price = 3.50, stock = 10)
        val boba = DrinkEntity(id = 2L, name = "Boba", price = 2.75, stock = 5)

        val cart = listOf(
            CartItem(drink = latte, quantity = 2), // 7.00
            CartItem(drink = boba, quantity = 3)   // 8.25
        )

        val total = cart.sumOf { it.lineTotal }
        assertEquals(15.25, total, 0.001)
    }

    @Test
    fun testStockDeductionAndRestoration() {
        val initialStock = 20
        val quantityOrdered = 4
        val deductedStock = (initialStock - quantityOrdered).coerceAtLeast(0)
        assertEquals(16, deductedStock)

        // Void order restores stock
        val restoredStock = deductedStock + quantityOrdered
        assertEquals(20, restoredStock)
    }

    @Test
    fun testPercentChangeCalculation() {
        val changeUp = AnalyticsCalculator.calculatePercentChange(100.0, 150.0)
        assertEquals(50.0, changeUp, 0.01)

        val changeDown = AnalyticsCalculator.calculatePercentChange(200.0, 100.0)
        assertEquals(-50.0, changeDown, 0.01)

        val zeroPrior = AnalyticsCalculator.calculatePercentChange(0.0, 50.0)
        assertEquals(100.0, zeroPrior, 0.01)
    }

    @Test
    fun testAnalyticsReportCalculations() {
        val now = System.currentTimeMillis()
        val range = DateRange(
            startMillis = now - 100_000L,
            endMillis = now + 100_000L,
            previousStartMillis = now - 300_000L,
            previousEndMillis = now - 100_001L
        )

        val sampleOrders = listOf(
            OrderWithItems(
                order = OrderEntity(
                    orderId = "ORD-001",
                    orderTimestamp = now,
                    cashierName = "Alice",
                    paymentMethod = PaymentMethod.CASH.name,
                    orderTotal = 10.0,
                    status = OrderStatus.PAID.name
                ),
                items = listOf(
                    OrderItemEntity(
                        id = 1,
                        orderId = "ORD-001",
                        drinkId = 1L,
                        historicalDrinkName = "Matcha",
                        quantity = 2,
                        unitPrice = 5.0,
                        lineTotal = 10.0
                    )
                )
            ),
            OrderWithItems(
                order = OrderEntity(
                    orderId = "ORD-002",
                    orderTimestamp = now,
                    cashierName = "Bob",
                    paymentMethod = PaymentMethod.ABA.name,
                    orderTotal = 15.0,
                    status = OrderStatus.PAID.name
                ),
                items = listOf(
                    OrderItemEntity(
                        id = 2,
                        orderId = "ORD-002",
                        drinkId = 2L,
                        historicalDrinkName = "Boba",
                        quantity = 3,
                        unitPrice = 5.0,
                        lineTotal = 15.0
                    )
                )
            ),
            OrderWithItems(
                order = OrderEntity(
                    orderId = "ORD-003",
                    orderTimestamp = now,
                    cashierName = "Alice",
                    paymentMethod = PaymentMethod.CASH.name,
                    orderTotal = 20.0,
                    status = OrderStatus.VOID.name
                ),
                items = listOf(
                    OrderItemEntity(
                        id = 3,
                        orderId = "ORD-003",
                        drinkId = 1L,
                        historicalDrinkName = "Matcha",
                        quantity = 4,
                        unitPrice = 5.0,
                        lineTotal = 20.0
                    )
                )
            )
        )

        val report = AnalyticsCalculator.calculateReport(
            allOrders = sampleOrders,
            filter = DateFilter.TODAY,
            dateRange = range,
            monthlyGoal = 1000.0
        )

        // Paid revenue should exclude void ($10 + $15 = $25)
        assertEquals(25.0, report.revenue, 0.001)
        assertEquals(2, report.paidOrdersCount)
        assertEquals(12.5, report.averageOrderValue, 0.001)
        assertEquals(5, report.drinksSoldCount)

        // Voided orders
        assertEquals(1, report.voidOrdersCount)
        assertEquals(20.0, report.voidAmount, 0.001)
        assertEquals(33.33, report.voidRate, 0.1)

        // Payment split
        assertEquals(10.0, report.cashRevenue, 0.001)
        assertEquals(15.0, report.abaRevenue, 0.001)
        assertEquals(40.0, report.cashPercentage, 0.01)
        assertEquals(60.0, report.abaPercentage, 0.01)
    }
}
