package com.example.data.model

enum class PaymentMethod(val label: String) {
    CASH("Cash"),
    ABA("ABA")
}

enum class OrderStatus(val label: String) {
    PAID("Paid"),
    VOID("Void")
}

enum class DateFilter(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    LAST_7_DAYS("Last 7 Days"),
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month"),
    CUSTOM("Custom Range")
}

enum class SoundEvent(val id: String, val label: String) {
    ADD_DRINK("add_drink", "Add Drink"),
    REMOVE_DRINK("remove_drink", "Remove Drink"),
    ORDER_PAID("order_paid", "Order Paid"),
    ERROR("error", "Error"),
    NAV_TAP("nav_tap", "Navigation Tap")
}

enum class SoundType(val id: String, val label: String) {
    POP("pop", "Pop"),
    CLICK("click", "Click"),
    BUBBLE("bubble", "Bubble"),
    COIN("coin", "Coin"),
    CHIME("chime", "Chime"),
    BELL("bell", "Bell"),
    TADA("tada", "Tada"),
    LOW_POP("low_pop", "Low Pop"),
    BEEP("beep", "Beep"),
    BUZZ("buzz", "Buzz")
}

enum class NavigationTab(val label: String) {
    NEW_ORDER("New Order"),
    STOCK("Stock"),
    REPORTS("Reports"),
    ANALYSIS("Analysis"),
    SOUNDS("Sounds"),
    SETTINGS("Settings")
}
