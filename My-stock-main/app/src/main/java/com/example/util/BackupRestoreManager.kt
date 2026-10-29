package com.example.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.local.AppDatabase
import com.example.data.model.DrinkEntity
import com.example.data.model.OrderEntity
import com.example.data.model.OrderItemEntity
import com.example.data.model.OrderStatus
import com.example.data.model.OrderWithItems
import com.example.data.model.SettingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupRestoreManager(
    private val context: Context,
    private val database: AppDatabase
) {

    private val drinkDao = database.drinkDao()
    private val orderDao = database.orderDao()
    private val settingDao = database.settingDao()

    suspend fun exportBackupJson(): File = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("app", "Sip Shop Manager")
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        // Drinks
        val drinks = drinkDao.getAllDrinks().first()
        val drinksArray = JSONArray()
        for (d in drinks) {
            val obj = JSONObject()
            obj.put("id", d.id)
            obj.put("name", d.name)
            obj.put("price", d.price)
            obj.put("stock", d.stock)
            obj.put("lowStockThreshold", d.lowStockThreshold)
            obj.put("isActive", d.isActive)
            obj.put("displayOrder", d.displayOrder)
            obj.put("colorHex", d.colorHex)
            obj.put("category", d.category)
            obj.put("imageUri", d.imageUri ?: "")
            obj.put("createdAt", d.createdAt)
            obj.put("updatedAt", d.updatedAt)
            drinksArray.put(obj)
        }
        root.put("drinks", drinksArray)

        // Orders
        val orders = orderDao.getAllOrders().first()
        val ordersArray = JSONArray()
        for (o in orders) {
            val obj = JSONObject()
            obj.put("orderId", o.order.orderId)
            obj.put("orderTimestamp", o.order.orderTimestamp)
            obj.put("cashierName", o.order.cashierName)
            obj.put("paymentMethod", o.order.paymentMethod)
            obj.put("orderTotal", o.order.orderTotal)
            obj.put("status", o.order.status)
            obj.put("createdAt", o.order.createdAt)

            val itemsArray = JSONArray()
            for (item in o.items) {
                val itemObj = JSONObject()
                itemObj.put("drinkId", item.drinkId ?: -1L)
                itemObj.put("historicalDrinkName", item.historicalDrinkName)
                itemObj.put("quantity", item.quantity)
                itemObj.put("unitPrice", item.unitPrice)
                itemObj.put("lineTotal", item.lineTotal)
                itemsArray.put(itemObj)
            }
            obj.put("items", itemsArray)
            ordersArray.put(obj)
        }
        root.put("orders", ordersArray)

        // Settings
        val settings = settingDao.getAllSettings().first()
        val settingsObj = JSONObject()
        for (s in settings) {
            settingsObj.put(s.key, s.value)
        }
        root.put("settings", settingsObj)

        val fileName = "sip_shop_backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.json"
        val exportFile = File(context.cacheDir, fileName)
        FileOutputStream(exportFile).use { it.write(root.toString(2).toByteArray()) }
        exportFile
    }

    suspend fun restoreFromJson(jsonString: String, overwriteExisting: Boolean): Result<String> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            val app = root.optString("app", "")
            if (!app.contains("Sip Shop", ignoreCase = true)) {
                return@withContext Result.failure(Exception("Invalid backup file: App identifier mismatch."))
            }

            val drinksArray = root.optJSONArray("drinks") ?: JSONArray()
            val ordersArray = root.optJSONArray("orders") ?: JSONArray()
            val settingsObj = root.optJSONObject("settings") ?: JSONObject()

            val importedDrinks = mutableListOf<DrinkEntity>()
            for (i in 0 until drinksArray.length()) {
                val obj = drinksArray.getJSONObject(i)
                importedDrinks.add(
                    DrinkEntity(
                        name = obj.getString("name"),
                        price = obj.getDouble("price"),
                        stock = obj.getInt("stock"),
                        lowStockThreshold = obj.optInt("lowStockThreshold", 5),
                        isActive = obj.optBoolean("isActive", true),
                        displayOrder = obj.optInt("displayOrder", i),
                        colorHex = obj.optString("colorHex", "#E0F2FE"),
                        category = obj.optString("category", "Other").ifBlank { "Other" },
                        imageUri = obj.optString("imageUri").takeIf { it.isNotBlank() },
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }

            if (overwriteExisting) {
                drinkDao.deleteAllDrinks()
                orderDao.deleteAllOrders()
                orderDao.deleteAllOrderItems()
                settingDao.deleteAllSettings()
            }

            if (importedDrinks.isNotEmpty()) {
                drinkDao.insertDrinks(importedDrinks)
            }

            // Restore orders
            var restoredOrdersCount = 0
            for (i in 0 until ordersArray.length()) {
                val obj = ordersArray.getJSONObject(i)
                val orderId = obj.getString("orderId")

                val existing = orderDao.getOrderById(orderId)
                if (existing != null) continue // Avoid duplicate orders

                val orderEntity = OrderEntity(
                    orderId = orderId,
                    orderTimestamp = obj.getLong("orderTimestamp"),
                    cashierName = obj.optString("cashierName", "Cashier"),
                    paymentMethod = obj.optString("paymentMethod", "CASH"),
                    orderTotal = obj.getDouble("orderTotal"),
                    status = obj.optString("status", OrderStatus.PAID.name),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
                orderDao.insertOrder(orderEntity)

                val itemsArray = obj.optJSONArray("items") ?: JSONArray()
                val orderItems = mutableListOf<OrderItemEntity>()
                for (j in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(j)
                    val drinkId = itemObj.optLong("drinkId", -1L).takeIf { it > 0 }
                    orderItems.add(
                        OrderItemEntity(
                            orderId = orderId,
                            drinkId = drinkId,
                            historicalDrinkName = itemObj.getString("historicalDrinkName"),
                            quantity = itemObj.getInt("quantity"),
                            unitPrice = itemObj.getDouble("unitPrice"),
                            lineTotal = itemObj.getDouble("lineTotal")
                        )
                    )
                }
                orderDao.insertOrderItems(orderItems)
                restoredOrdersCount++
            }

            // Restore settings
            val settingsList = mutableListOf<SettingEntity>()
            val keys = settingsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                settingsList.add(SettingEntity(key, settingsObj.getString(key)))
            }
            if (settingsList.isNotEmpty()) {
                settingDao.saveSettings(settingsList)
            }

            Result.success("Restored ${importedDrinks.size} drinks and $restoredOrdersCount orders.")
        } catch (e: Exception) {
            Result.failure(Exception("Failed to restore backup: ${e.message}"))
        }
    }

    suspend fun exportReportsCsv(orders: List<OrderWithItems>): File = withContext(Dispatchers.IO) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.append("Order ID,Date Time,Cashier,Payment Method,Status,Order Total,Items Summary\n")

        for (o in orders) {
            val dateStr = dateFormat.format(Date(o.order.orderTimestamp))
            val itemsStr = o.items.joinToString("; ") { "${it.historicalDrinkName} x${it.quantity} ($${String.format(Locale.US, "%.2f", it.unitPrice)})" }
                .replace("\"", "\"\"")

            sb.append("\"${o.order.orderId}\",")
            sb.append("\"$dateStr\",")
            sb.append("\"${o.order.cashierName}\",")
            sb.append("\"${o.order.paymentMethod}\",")
            sb.append("\"${o.order.status}\",")
            sb.append("${String.format(Locale.US, "%.2f", o.order.orderTotal)},")
            sb.append("\"$itemsStr\"\n")
        }

        val fileName = "sip_shop_report_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.csv"
        val csvFile = File(context.cacheDir, fileName)
        FileOutputStream(csvFile).use { it.write(sb.toString().toByteArray()) }
        csvFile
    }

    // ---------------------------------------------------------------------
    // CSV: drinks (menu & stock)
    // ---------------------------------------------------------------------

    suspend fun exportDrinksCsv(): File = withContext(Dispatchers.IO) {
        val drinks = drinkDao.getAllDrinksOnce()
        val sb = StringBuilder()
        sb.append(CsvUtil.row("name", "category", "price", "stock", "lowStockThreshold", "isActive", "displayOrder", "colorHex"))
        for (d in drinks) {
            sb.append(
                CsvUtil.row(
                    d.name, d.category, String.format(Locale.US, "%.2f", d.price), d.stock,
                    d.lowStockThreshold, d.isActive, d.displayOrder, d.colorHex
                )
            )
        }
        writeCache("sip_shop_drinks", "csv", sb.toString())
    }

    /** Adds new drinks and updates existing ones (matched by name, case-insensitive). */
    suspend fun importDrinksCsv(csv: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val rows = CsvUtil.parse(csv)
            if (rows.size < 2) return@withContext Result.failure(Exception("CSV has no data rows."))
            val h = CsvUtil.headerIndex(rows[0])
            val nameIdx = h["name"] ?: h["drink"] ?: h["drinkname"]
                ?: return@withContext Result.failure(Exception("CSV needs a 'name' column."))
            val priceIdx = h["price"] ?: return@withContext Result.failure(Exception("CSV needs a 'price' column."))

            fun cell(r: List<String>, key: String): String? =
                h[key]?.let { r.getOrNull(it)?.trim() }?.takeIf { it.isNotEmpty() }

            val existing = drinkDao.getAllDrinksOnce().toMutableList()
            var added = 0
            var updated = 0
            var skipped = 0
            var nextOrder = (existing.maxOfOrNull { it.displayOrder } ?: -1) + 1

            for (r in rows.drop(1)) {
                val name = r.getOrNull(nameIdx)?.trim().orEmpty()
                val price = r.getOrNull(priceIdx)?.trim()?.replace("$", "")?.toDoubleOrNull()
                if (name.isEmpty() || price == null || price < 0) { skipped++; continue }

                val match = existing.firstOrNull { it.name.equals(name, ignoreCase = true) }
                val stock = cell(r, "stock")?.toIntOrNull()?.coerceAtLeast(0)
                val low = cell(r, "lowstockthreshold")?.toIntOrNull()?.coerceAtLeast(0)
                val active = cell(r, "isactive")?.toBooleanStrictOrNull()
                val color = cell(r, "colorhex")
                val category = cell(r, "category")

                if (match != null) {
                    val upd = match.copy(
                        price = price,
                        stock = stock ?: match.stock,
                        lowStockThreshold = low ?: match.lowStockThreshold,
                        isActive = active ?: match.isActive,
                        colorHex = color ?: match.colorHex,
                        category = category ?: match.category,
                        updatedAt = System.currentTimeMillis()
                    )
                    drinkDao.updateDrink(upd)
                    existing[existing.indexOf(match)] = upd
                    updated++
                } else {
                    val order = cell(r, "displayorder")?.toIntOrNull() ?: nextOrder
                    nextOrder = maxOf(nextOrder, order) + 1
                    val drink = DrinkEntity(
                        name = name,
                        price = price,
                        stock = stock ?: 0,
                        lowStockThreshold = low ?: 5,
                        isActive = active ?: true,
                        displayOrder = order,
                        colorHex = color ?: "#E0F2FE",
                        category = category ?: "Other"
                    )
                    val id = drinkDao.insertDrink(drink)
                    existing.add(drink.copy(id = id))
                    added++
                }
            }
            Result.success("Drinks CSV: $added added, $updated updated" + if (skipped > 0) ", $skipped skipped." else ".")
        } catch (e: Exception) {
            Result.failure(Exception("Failed to import drinks CSV: ${e.message}"))
        }
    }

    // ---------------------------------------------------------------------
    // CSV: orders (one row per item so it can be re-imported)
    // ---------------------------------------------------------------------

    suspend fun exportOrdersCsvFull(): File = withContext(Dispatchers.IO) {
        val orders = orderDao.getAllOrders().first()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.append(CsvUtil.row("orderId", "dateTime", "cashier", "paymentMethod", "status", "orderTotal", "drinkName", "quantity", "unitPrice", "lineTotal"))
        for (o in orders) {
            val dt = fmt.format(Date(o.order.orderTimestamp))
            val total = String.format(Locale.US, "%.2f", o.order.orderTotal)
            if (o.items.isEmpty()) {
                sb.append(CsvUtil.row(o.order.orderId, dt, o.order.cashierName, o.order.paymentMethod, o.order.status, total, "", 0, "0.00", "0.00"))
            }
            for (item in o.items) {
                sb.append(
                    CsvUtil.row(
                        o.order.orderId, dt, o.order.cashierName, o.order.paymentMethod, o.order.status, total,
                        item.historicalDrinkName, item.quantity,
                        String.format(Locale.US, "%.2f", item.unitPrice),
                        String.format(Locale.US, "%.2f", item.lineTotal)
                    )
                )
            }
        }
        writeCache("sip_shop_orders", "csv", sb.toString())
    }

    /** Imports historical orders. Existing order IDs are skipped. Stock is NOT changed. */
    suspend fun importOrdersCsv(csv: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val rows = CsvUtil.parse(csv)
            if (rows.size < 2) return@withContext Result.failure(Exception("CSV has no data rows."))
            val h = CsvUtil.headerIndex(rows[0])
            val idIdx = h["orderid"] ?: return@withContext Result.failure(Exception("CSV needs an 'orderId' column."))
            val dateIdx = h["datetime"] ?: h["date"] ?: h["timestamp"] ?: h["ordertimestamp"]
                ?: return@withContext Result.failure(Exception("CSV needs a 'dateTime' column."))

            fun cell(r: List<String>, vararg keys: String): String? {
                for (k in keys) h[k]?.let { idx -> r.getOrNull(idx)?.trim()?.takeIf { v -> v.isNotEmpty() }?.let { v -> return v } }
                return null
            }

            val fmts = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd")
                .map { SimpleDateFormat(it, Locale.US).apply { isLenient = false } }
            fun parseTime(v: String): Long? {
                v.toLongOrNull()?.let { return it }
                for (f in fmts) { try { return f.parse(v)?.time } catch (_: Exception) {} }
                return null
            }

            // Group rows by orderId, preserving file order
            val grouped = LinkedHashMap<String, MutableList<List<String>>>()
            for (r in rows.drop(1)) {
                val id = r.getOrNull(idIdx)?.trim().orEmpty()
                if (id.isNotEmpty()) grouped.getOrPut(id) { mutableListOf() }.add(r)
            }

            val drinksByName = drinkDao.getAllDrinksOnce().associateBy { it.name.lowercase() }
            var imported = 0
            var skippedExisting = 0
            var skippedBad = 0

            for ((orderId, group) in grouped) {
                if (orderDao.getOrderById(orderId) != null) { skippedExisting++; continue }
                val first = group.first()
                val ts = first.getOrNull(dateIdx)?.trim()?.let { parseTime(it) }
                if (ts == null) { skippedBad++; continue }

                val items = mutableListOf<OrderItemEntity>()
                for (r in group) {
                    val name = cell(r, "drinkname", "drink", "item") ?: continue
                    val qty = cell(r, "quantity", "qty")?.toIntOrNull() ?: continue
                    if (qty <= 0) continue
                    val unit = cell(r, "unitprice", "price")?.replace("$", "")?.toDoubleOrNull() ?: 0.0
                    val line = cell(r, "linetotal")?.replace("$", "")?.toDoubleOrNull() ?: (unit * qty)
                    items.add(
                        OrderItemEntity(
                            orderId = orderId,
                            drinkId = drinksByName[name.lowercase()]?.id,
                            historicalDrinkName = name,
                            quantity = qty,
                            unitPrice = unit,
                            lineTotal = line
                        )
                    )
                }
                if (items.isEmpty()) { skippedBad++; continue }

                val status = (cell(first, "status") ?: OrderStatus.PAID.name).uppercase()
                    .let { if (it == OrderStatus.VOID.name) it else OrderStatus.PAID.name }
                val payment = (cell(first, "paymentmethod", "payment") ?: "CASH").uppercase()
                    .let { if (it == "ABA") "ABA" else "CASH" }
                val total = cell(first, "ordertotal", "total")?.replace("$", "")?.toDoubleOrNull()
                    ?: items.sumOf { it.lineTotal }

                orderDao.insertOrder(
                    OrderEntity(
                        orderId = orderId,
                        orderTimestamp = ts,
                        cashierName = cell(first, "cashier", "cashiername") ?: "Cashier",
                        paymentMethod = payment,
                        orderTotal = total,
                        status = status,
                        createdAt = ts
                    )
                )
                orderDao.insertOrderItems(items)
                imported++
            }

            Result.success(
                "Orders CSV: $imported imported" +
                    (if (skippedExisting > 0) ", $skippedExisting already existed" else "") +
                    (if (skippedBad > 0) ", $skippedBad invalid" else "") + ". Stock was not changed."
            )
        } catch (e: Exception) {
            Result.failure(Exception("Failed to import orders CSV: ${e.message}"))
        }
    }

    private fun writeCache(prefix: String, ext: String, content: String): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val f = File(context.cacheDir, "${prefix}_$stamp.$ext")
        FileOutputStream(f).use { it.write(content.toByteArray(Charsets.UTF_8)) }
        return f
    }

    fun shareFile(file: File, mimeType: String, title: String) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            // Intent handling error fallback
        }
    }

    fun shareSalesSummaryText(summaryText: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Sip Shop Sales Summary")
            putExtra(Intent.EXTRA_TEXT, summaryText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share Sales Summary").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
