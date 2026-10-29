package com.example.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object CurrencyFormatter {

    private val usdSymbols = DecimalFormatSymbols(Locale.US)
    private val usdFormat = DecimalFormat("$#,##0.00", usdSymbols)
    private val rielSymbols = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = ','
    }
    private val rielFormat = DecimalFormat("#,##0", rielSymbols)

    fun format(amountUsd: Double, currencyMode: String, exchangeRate: Double = 4100.0): String {
        return if (currencyMode.equals("KHR", ignoreCase = true)) {
            val amountRiel = (amountUsd * exchangeRate).toLong()
            "${rielFormat.format(amountRiel)} ៛"
        } else {
            usdFormat.format(amountUsd)
        }
    }

    fun formatUsd(amountUsd: Double): String {
        return usdFormat.format(amountUsd)
    }

    fun formatRiel(amountUsd: Double, exchangeRate: Double = 4100.0): String {
        val amountRiel = (amountUsd * exchangeRate).toLong()
        return "${rielFormat.format(amountRiel)} ៛"
    }

    fun formatDual(amountUsd: Double, exchangeRate: Double = 4100.0, primaryMode: String = "USD"): String {
        val usd = formatUsd(amountUsd)
        val khr = formatRiel(amountUsd, exchangeRate)
        return if (primaryMode.equals("KHR", ignoreCase = true)) {
            "$khr ($usd)"
        } else {
            "$usd ($khr)"
        }
    }
}
