package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.model.OrderEntity
import com.example.data.model.OrderItemEntity
import com.example.data.model.OrderWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {

    @Transaction
    @Query("SELECT * FROM orders ORDER BY orderTimestamp DESC")
    fun getAllOrders(): Flow<List<OrderWithItems>>

    @Transaction
    @Query("SELECT * FROM orders WHERE orderTimestamp >= :startTime AND orderTimestamp <= :endTime ORDER BY orderTimestamp DESC")
    fun getOrdersBetween(startTime: Long, endTime: Long): Flow<List<OrderWithItems>>

    @Transaction
    @Query("SELECT * FROM orders ORDER BY orderTimestamp DESC LIMIT :limit")
    fun getRecentOrders(limit: Int = 20): Flow<List<OrderWithItems>>

    @Transaction
    @Query("SELECT * FROM orders WHERE orderId = :orderId LIMIT 1")
    suspend fun getOrderById(orderId: String): OrderWithItems?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOrder(order: OrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItems(items: List<OrderItemEntity>)

    @Query("UPDATE orders SET status = :status WHERE orderId = :orderId")
    suspend fun updateOrderStatus(orderId: String, status: String)

    @Query("UPDATE orders SET cashierName = :cashier, paymentMethod = :payment, orderTimestamp = :timestamp, orderTotal = :total WHERE orderId = :orderId")
    suspend fun updateOrderDetails(orderId: String, cashier: String, payment: String, timestamp: Long, total: Double)

    @Query("DELETE FROM orders WHERE orderId = :orderId")
    suspend fun deleteOrderById(orderId: String)

    @Query("DELETE FROM order_items WHERE orderId = :orderId")
    suspend fun deleteItemsByOrderId(orderId: String)

    @Query("SELECT * FROM order_items")
    suspend fun getAllOrderItems(): List<OrderItemEntity>

    @Query("DELETE FROM orders")
    suspend fun deleteAllOrders()

    @Query("DELETE FROM order_items")
    suspend fun deleteAllOrderItems()
}
