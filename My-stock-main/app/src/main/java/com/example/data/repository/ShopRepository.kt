package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.model.CartItem
import com.example.data.model.DrinkEntity
import com.example.data.model.OrderEntity
import com.example.data.model.OrderItemEntity
import com.example.data.model.OrderStatus
import com.example.data.model.OrderWithItems
import com.example.data.model.PaymentMethod
import com.example.data.model.SettingEntity
import com.example.data.model.ShopSettings
import com.example.data.model.SoundEvent
import com.example.data.model.SoundEventConfig
import com.example.data.model.SoundType
import com.example.data.model.defaultSoundConfigs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShopRepository(private val database: AppDatabase) {

    private val drinkDao = database.drinkDao()
    private val orderDao = database.orderDao()
    private val settingDao = database.settingDao()

    val allDrinks: Flow<List<DrinkEntity>> = drinkDao.getAllDrinks()
    val activeDrinks: Flow<List<DrinkEntity>> = drinkDao.getActiveDrinks()
    val allOrders: Flow<List<OrderWithItems>> = orderDao.getAllOrders()
    val recentOrders: Flow<List<OrderWithItems>> = orderDao.getRecentOrders(50)

    val settings: Flow<ShopSettings> = settingDao.getAllSettings().map { list ->
        val map = list.associate { it.key to it.value }
        val soundConfigs = defaultSoundConfigs().toMutableMap()

        for (event in SoundEvent.entries) {
            val enabled = map["sound_${event.id}_enabled"]?.toBooleanStrictOrNull() ?: soundConfigs[event]?.enabled ?: true
            val typeStr = map["sound_${event.id}_type"]
            val soundType = SoundType.entries.firstOrNull { it.id == typeStr } ?: soundConfigs[event]?.soundType ?: SoundType.POP
            val volume = map["sound_${event.id}_volume"]?.toFloatOrNull() ?: soundConfigs[event]?.volume ?: 0.8f
            soundConfigs[event] = SoundEventConfig(enabled, soundType, volume)
        }

        ShopSettings(
            shopName = map["shop_name"] ?: "Sip Shop",
            currencyMode = map["currency_mode"] ?: "USD",
            khrExchangeRate = map["khr_exchange_rate"]?.toDoubleOrNull() ?: 4100.0,
            monthlySalesGoal = map["monthly_sales_goal"]?.toDoubleOrNull() ?: 1500.0,
            masterVolume = map["master_volume"]?.toFloatOrNull() ?: 1.0f,
            masterSoundEnabled = map["master_sound_enabled"]?.toBooleanStrictOrNull() ?: true,
            vibrationEnabled = map["vibration_enabled"]?.toBooleanStrictOrNull() ?: true,
            cashierName = map["cashier_name"] ?: "Cashier 1",
            themePreference = map["theme_preference"] ?: "SYSTEM",
            fontScale = (map["font_scale"]?.toFloatOrNull() ?: 1.0f).coerceIn(0.8f, 1.4f),
            spacingScale = (map["spacing_scale"]?.toFloatOrNull() ?: 1.0f).coerceIn(0.8f, 1.3f),
            backgroundColor = map["background_color"] ?: "",
            blockColor = map["block_color"] ?: "",
            textColor = map["text_color"] ?: "",
            soundEvents = soundConfigs
        )
    }

    suspend fun getDrinkCount(): Int = withContext(Dispatchers.IO) {
        drinkDao.getDrinkCount()
    }

    suspend fun saveDrink(drink: DrinkEntity): Long = withContext(Dispatchers.IO) {
        if (drink.id == 0L) {
            drinkDao.insertDrink(drink)
        } else {
            drinkDao.updateDrink(drink.copy(updatedAt = System.currentTimeMillis()))
            drink.id
        }
    }

    suspend fun deleteDrink(drink: DrinkEntity) = withContext(Dispatchers.IO) {
        drinkDao.deleteDrink(drink)
    }

    suspend fun adjustStock(drinkId: Long, delta: Int): Result<Int> = withContext(Dispatchers.IO) {
        database.withTransaction {
            val drink = drinkDao.getDrinkById(drinkId)
                ?: return@withTransaction Result.failure(Exception("Drink not found"))
            val newStock = (drink.stock + delta).coerceAtLeast(0)
            drinkDao.updateStock(drinkId, newStock)
            Result.success(newStock)
        }
    }

    suspend fun reorderDrinks(orderedDrinks: List<DrinkEntity>) = withContext(Dispatchers.IO) {
        database.withTransaction {
            orderedDrinks.forEachIndexed { index, drink ->
                drinkDao.updateDisplayOrder(drink.id, index)
            }
        }
    }

    suspend fun chargeOrder(
        cartItems: List<CartItem>,
        cashierName: String,
        paymentMethod: PaymentMethod
    ): Result<OrderEntity> = withContext(Dispatchers.IO) {
        if (cartItems.isEmpty()) {
            return@withContext Result.failure(Exception("Cart is empty"))
        }
        if (cashierName.trim().isEmpty()) {
            return@withContext Result.failure(Exception("Cashier name cannot be empty"))
        }

        database.withTransaction {
            // Step 1: Recheck stock for all drinks in cart
            for (item in cartItems) {
                val current = drinkDao.getDrinkById(item.drink.id)
                    ?: return@withTransaction Result.failure(Exception("Drink '${item.drink.name}' is no longer available"))
                if (current.stock < item.quantity) {
                    return@withTransaction Result.failure(
                        Exception("Insufficient stock for '${current.name}'. Available: ${current.stock}, requested: ${item.quantity}")
                    )
                }
            }

            // Step 2: Generate unique Order ID
            val now = System.currentTimeMillis()
            val dateStr = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(now))
            val randSuffix = (100..999).random()
            val orderId = "ORD-$dateStr-$randSuffix"

            val orderTotal = cartItems.sumOf { it.drink.price * it.quantity }
            val orderEntity = OrderEntity(
                orderId = orderId,
                orderTimestamp = now,
                cashierName = cashierName.trim(),
                paymentMethod = paymentMethod.name,
                orderTotal = orderTotal,
                status = OrderStatus.PAID.name,
                createdAt = now
            )

            // Step 3: Insert Order and Items
            orderDao.insertOrder(orderEntity)

            val orderItems = cartItems.map { item ->
                OrderItemEntity(
                    orderId = orderId,
                    drinkId = item.drink.id,
                    historicalDrinkName = item.drink.name,
                    quantity = item.quantity,
                    unitPrice = item.drink.price,
                    lineTotal = item.drink.price * item.quantity
                )
            }
            orderDao.insertOrderItems(orderItems)

            // Step 4: Atomic Inventory Deduction
            for (item in cartItems) {
                val current = drinkDao.getDrinkById(item.drink.id)!!
                val newStock = (current.stock - item.quantity).coerceAtLeast(0)
                drinkDao.updateStock(item.drink.id, newStock)
            }

            Result.success(orderEntity)
        }
    }

    suspend fun voidOrder(orderId: String): Result<Unit> = withContext(Dispatchers.IO) {
        database.withTransaction {
            val orderWithItems = orderDao.getOrderById(orderId)
                ?: return@withTransaction Result.failure(Exception("Order not found"))

            if (orderWithItems.order.status == OrderStatus.VOID.name) {
                return@withTransaction Result.failure(Exception("Order is already voided"))
            }

            // Restore inventory for each item
            for (item in orderWithItems.items) {
                item.drinkId?.let { dId ->
                    val drink = drinkDao.getDrinkById(dId)
                    if (drink != null) {
                        val restoredStock = drink.stock + item.quantity
                        drinkDao.updateStock(dId, restoredStock)
                    }
                }
            }

            // Update status
            orderDao.updateOrderStatus(orderId, OrderStatus.VOID.name)
            Result.success(Unit)
        }
    }

    /** Permanently removes an order. A PAID order returns its drinks to stock first. */
    suspend fun deleteOrder(orderId: String): Result<Unit> = withContext(Dispatchers.IO) {
        database.withTransaction {
            val o = orderDao.getOrderById(orderId)
                ?: return@withTransaction Result.failure(Exception("Order not found"))
            if (o.order.status == OrderStatus.PAID.name) {
                for (item in o.items) {
                    item.drinkId?.let { dId ->
                        val drink = drinkDao.getDrinkById(dId)
                        if (drink != null) drinkDao.updateStock(dId, drink.stock + item.quantity)
                    }
                }
            }
            orderDao.deleteItemsByOrderId(orderId)
            orderDao.deleteOrderById(orderId)
            Result.success(Unit)
        }
    }

    /**
     * Edits an order: cashier, payment method, date/time and item quantities.
     * Items with quantity 0 are removed. Stock is adjusted by the difference for PAID orders.
     * [newQuantities] maps order_items.id -> new quantity.
     */
    suspend fun editOrder(
        orderId: String,
        cashierName: String,
        paymentMethod: String,
        timestamp: Long,
        newQuantities: Map<Long, Int>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        database.withTransaction {
            val o = orderDao.getOrderById(orderId)
                ?: return@withTransaction Result.failure(Exception("Order not found"))
            if (cashierName.isBlank()) {
                return@withTransaction Result.failure(Exception("Cashier name cannot be empty"))
            }
            val isPaid = o.order.status == OrderStatus.PAID.name
            val kept = mutableListOf<OrderItemEntity>()

            for (item in o.items) {
                val newQty = (newQuantities[item.id] ?: item.quantity).coerceAtLeast(0)
                val delta = newQty - item.quantity // positive = sell more
                if (isPaid && delta != 0) {
                    item.drinkId?.let { dId ->
                        val drink = drinkDao.getDrinkById(dId)
                        if (drink != null) {
                            if (delta > 0 && drink.stock < delta) {
                                return@withTransaction Result.failure(
                                    Exception("Not enough stock for '${drink.name}'. Available: ${drink.stock}")
                                )
                            }
                            drinkDao.updateStock(dId, (drink.stock - delta).coerceAtLeast(0))
                        }
                    }
                }
                if (newQty > 0) {
                    kept.add(item.copy(quantity = newQty, lineTotal = item.unitPrice * newQty))
                }
            }

            if (kept.isEmpty()) {
                return@withTransaction Result.failure(
                    Exception("An order needs at least one item. Use Delete or Void instead.")
                )
            }

            orderDao.deleteItemsByOrderId(orderId)
            orderDao.insertOrderItems(kept.map { it.copy(id = 0) })
            orderDao.updateOrderDetails(
                orderId = orderId,
                cashier = cashierName.trim(),
                payment = paymentMethod,
                timestamp = timestamp,
                total = kept.sumOf { it.lineTotal }
            )
            Result.success(Unit)
        }
    }

    suspend fun updateSettings(update: ShopSettings) = withContext(Dispatchers.IO) {
        val list = mutableListOf(
            SettingEntity("shop_name", update.shopName),
            SettingEntity("currency_mode", update.currencyMode),
            SettingEntity("khr_exchange_rate", update.khrExchangeRate.toString()),
            SettingEntity("monthly_sales_goal", update.monthlySalesGoal.toString()),
            SettingEntity("master_volume", update.masterVolume.toString()),
            SettingEntity("master_sound_enabled", update.masterSoundEnabled.toString()),
            SettingEntity("vibration_enabled", update.vibrationEnabled.toString()),
            SettingEntity("cashier_name", update.cashierName),
            SettingEntity("theme_preference", update.themePreference),
            SettingEntity("font_scale", update.fontScale.toString()),
            SettingEntity("spacing_scale", update.spacingScale.toString()),
            SettingEntity("background_color", update.backgroundColor),
            SettingEntity("block_color", update.blockColor),
            SettingEntity("text_color", update.textColor)
        )
        for ((event, config) in update.soundEvents) {
            list.add(SettingEntity("sound_${event.id}_enabled", config.enabled.toString()))
            list.add(SettingEntity("sound_${event.id}_type", config.soundType.id))
            list.add(SettingEntity("sound_${event.id}_volume", config.volume.toString()))
        }
        settingDao.saveSettings(list)
    }

    suspend fun seedDemoDrinksIfEmpty() = withContext(Dispatchers.IO) {
        if (drinkDao.getDrinkCount() == 0) {
            val initialDrinks = listOf(
                DrinkEntity(
                    name = "Iced Milk Tea",
                    price = 2.25,
                    stock = 45,
                    lowStockThreshold = 10,
                    colorHex = "#FDE68A",
                    displayOrder = 0
                ),
                DrinkEntity(
                    name = "Matcha Green Tea Latte",
                    price = 2.75,
                    stock = 30,
                    lowStockThreshold = 8,
                    colorHex = "#BBF7D0",
                    displayOrder = 1
                ),
                DrinkEntity(
                    name = "Brown Sugar Boba Fresh Milk",
                    price = 3.20,
                    stock = 25,
                    lowStockThreshold = 5,
                    colorHex = "#FED7AA",
                    displayOrder = 2
                ),
                DrinkEntity(
                    name = "Iced Americano",
                    price = 1.85,
                    stock = 60,
                    lowStockThreshold = 12,
                    colorHex = "#E2E8F0",
                    displayOrder = 3
                ),
                DrinkEntity(
                    name = "Caramel Macchiato",
                    price = 2.95,
                    stock = 20,
                    lowStockThreshold = 6,
                    colorHex = "#FEF08A",
                    displayOrder = 4
                ),
                DrinkEntity(
                    name = "Passion Fruit Green Tea",
                    price = 2.30,
                    stock = 18,
                    lowStockThreshold = 5,
                    colorHex = "#FDE047",
                    displayOrder = 5
                ),
                DrinkEntity(
                    name = "Taro Coconut Milk",
                    price = 2.65,
                    stock = 3, // Low stock demo!
                    lowStockThreshold = 5,
                    colorHex = "#E9D5FF",
                    displayOrder = 6
                ),
                DrinkEntity(
                    name = "Strawberry Sparkling Soda",
                    price = 2.40,
                    stock = 0, // Sold out demo!
                    lowStockThreshold = 5,
                    colorHex = "#FECDD3",
                    displayOrder = 7
                )
            )
            drinkDao.insertDrinks(initialDrinks)
        }
    }
}
 
 
 
