package com.example.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "orders",
    indices = [Index(value = ["orderTimestamp"]), Index(value = ["status"])]
)
data class OrderEntity(
    @PrimaryKey
    val orderId: String,
    val orderTimestamp: Long, // epoch millis
    val cashierName: String,
    val paymentMethod: String, // "CASH" or "ABA"
    val orderTotal: Double,
    val status: String = "PAID", // "PAID" or "VOID"
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "order_items",
    indices = [Index(value = ["orderId"]), Index(value = ["drinkId"])]
)
data class OrderItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val orderId: String,
    val drinkId: Long?,
    val historicalDrinkName: String,
    val quantity: Int,
    val unitPrice: Double,
    val lineTotal: Double
)

data class OrderWithItems(
    @Embedded
    val order: OrderEntity,
    @Relation(
        parentColumn = "orderId",
        entityColumn = "orderId"
    )
    val items: List<OrderItemEntity>
)

data class CartItem(
    val drink: DrinkEntity,
    val quantity: Int
) {
    val lineTotal: Double get() = drink.price * quantity
}
