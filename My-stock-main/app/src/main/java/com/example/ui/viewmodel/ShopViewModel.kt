package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SoundManager
import com.example.data.local.AppDatabase
import com.example.data.model.CartItem
import com.example.data.model.DateFilter
import com.example.data.model.DrinkEntity
import com.example.data.model.NavigationTab
import com.example.data.model.OrderEntity
import com.example.data.model.OrderWithItems
import com.example.data.model.PaymentMethod
import com.example.data.model.ShopSettings
import com.example.data.model.SoundEvent
import com.example.data.model.SoundType
import com.example.data.model.defaultSoundConfigs
import com.example.data.repository.ShopRepository
import com.example.util.AnalyticsCalculator
import com.example.util.AnalysisReport
import com.example.util.BackupRestoreManager
import com.example.util.CurrencyFormatter
import com.example.util.ImageStorageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ShopViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    val repository = ShopRepository(database)
    val soundManager = SoundManager(application)
    val imageStorageManager = ImageStorageManager(application)
    val backupRestoreManager = BackupRestoreManager(application, database)

    // Navigation Tab
    private val _currentTab = MutableStateFlow(NavigationTab.NEW_ORDER)
    val currentTab = _currentTab.asStateFlow()

    // Drinks and Cart
    val allDrinks: StateFlow<List<DrinkEntity>> = repository.allDrinks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<ShopSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShopSettings())

    val allOrders: StateFlow<List<OrderWithItems>> = repository.allOrders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart = _cart.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _hideSoldOutInOrder = MutableStateFlow(true)
    val hideSoldOutInOrder = _hideSoldOutInOrder.asStateFlow()

    // Payment Flow
    private val _paymentMethod = MutableStateFlow(PaymentMethod.CASH)
    val paymentMethod = _paymentMethod.asStateFlow()

    private val _isPaymentConfirmOpen = MutableStateFlow(false)
    val isPaymentConfirmOpen = _isPaymentConfirmOpen.asStateFlow()

    private val _completedOrder = MutableStateFlow<OrderEntity?>(null)
    val completedOrder = _completedOrder.asStateFlow()

    private val _isProcessingOrder = MutableStateFlow(false)
    val isProcessingOrder = _isProcessingOrder.asStateFlow()

    // Reports Date Selection
    private val _reportsDateMillis = MutableStateFlow(System.currentTimeMillis())
    val reportsDateMillis = _reportsDateMillis.asStateFlow()

    // Analysis Date Filter
    private val _analysisFilter = MutableStateFlow(DateFilter.TODAY)
    val analysisFilter = _analysisFilter.asStateFlow()

    private val _customDateRange = MutableStateFlow(
        Pair(
            System.currentTimeMillis() - 7 * 86_400_000L,
            System.currentTimeMillis()
        )
    )
    val customDateRange = _customDateRange.asStateFlow()

    // UI Feedback
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage = _toastMessage.asStateFlow()

    // Modals / Dialogs
    private val _editingDrink = MutableStateFlow<DrinkEntity?>(null)
    val editingDrink = _editingDrink.asStateFlow()

    private val _isAddingDrink = MutableStateFlow(false)
    val isAddingDrink = _isAddingDrink.asStateFlow()

    private val _imageCropperUri = MutableStateFlow<Uri?>(null)
    val imageCropperUri = _imageCropperUri.asStateFlow()

    private val _activeCroppingDrinkId = MutableStateFlow<Long?>(null)

    // Analysis Report flow
    val analysisReport: StateFlow<AnalysisReport> = combine(
        allOrders,
        _analysisFilter,
        _customDateRange,
        settings
    ) { orders, filter, customRange, shopSettings ->
        val range = AnalyticsCalculator.computeDateRange(
            filter,
            customRange.first,
            customRange.second
        )
        AnalyticsCalculator.calculateReport(
            allOrders = orders,
            filter = filter,
            dateRange = range,
            monthlyGoal = shopSettings.monthlySalesGoal
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AnalyticsCalculator.calculateReport(
            emptyList(),
            DateFilter.TODAY,
            AnalyticsCalculator.computeDateRange(DateFilter.TODAY)
        )
    )

    init {
        viewModelScope.launch {
            repository.seedDemoDrinksIfEmpty()
        }
    }

    fun selectTab(tab: NavigationTab) {
        if (_currentTab.value != tab) {
            _currentTab.value = tab
            soundManager.playSoundForEvent(SoundEvent.NAV_TAP, settings.value)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleHideSoldOutInOrder() {
        _hideSoldOutInOrder.value = !_hideSoldOutInOrder.value
    }

    fun addToCart(drink: DrinkEntity) {
        val currentCart = _cart.value.toMutableList()
        val index = currentCart.indexOfFirst { it.drink.id == drink.id }
        val currentQuantity = if (index >= 0) currentCart[index].quantity else 0

        if (currentQuantity + 1 > drink.stock) {
            _toastMessage.value = "Cannot add more than available stock (${drink.stock})"
            soundManager.playSoundForEvent(SoundEvent.ERROR, settings.value)
            return
        }

        if (index >= 0) {
            currentCart[index] = currentCart[index].copy(quantity = currentQuantity + 1)
        } else {
            currentCart.add(CartItem(drink = drink, quantity = 1))
        }
        _cart.value = currentCart
        soundManager.playSoundForEvent(SoundEvent.ADD_DRINK, settings.value)
    }

    fun removeFromCart(drink: DrinkEntity) {
        val currentCart = _cart.value.toMutableList()
        val index = currentCart.indexOfFirst { it.drink.id == drink.id }
        if (index >= 0) {
            val item = currentCart[index]
            if (item.quantity > 1) {
                currentCart[index] = item.copy(quantity = item.quantity - 1)
            } else {
                currentCart.removeAt(index)
            }
            _cart.value = currentCart
            soundManager.playSoundForEvent(SoundEvent.REMOVE_DRINK, settings.value)
        }
    }

    fun removeEntireLineFromCart(drinkId: Long) {
        val currentCart = _cart.value.toMutableList()
        val removed = currentCart.removeAll { it.drink.id == drinkId }
        if (removed) {
            _cart.value = currentCart
            soundManager.playSoundForEvent(SoundEvent.REMOVE_DRINK, settings.value)
        }
    }

    fun clearCart() {
        if (_cart.value.isNotEmpty()) {
            _cart.value = emptyList()
            soundManager.playSoundForEvent(SoundEvent.REMOVE_DRINK, settings.value)
        }
    }

    fun selectPaymentMethod(method: PaymentMethod) {
        _paymentMethod.value = method
        soundManager.playSoundForEvent(SoundEvent.NAV_TAP, settings.value)
    }

    fun setCashierName(name: String) {
        viewModelScope.launch {
            repository.updateSettings(settings.value.copy(cashierName = name))
        }
    }

    fun openPaymentConfirmation() {
        if (_cart.value.isEmpty()) {
            _toastMessage.value = "Cart is empty"
            soundManager.playSoundForEvent(SoundEvent.ERROR, settings.value)
            return
        }
        if (settings.value.cashierName.trim().isEmpty()) {
            _toastMessage.value = "Please enter Cashier Name first"
            soundManager.playSoundForEvent(SoundEvent.ERROR, settings.value)
            return
        }
        _isPaymentConfirmOpen.value = true
        soundManager.playSoundForEvent(SoundEvent.NAV_TAP, settings.value)
    }

    fun closePaymentConfirmation() {
        _isPaymentConfirmOpen.value = false
    }

    fun processOrder(method: PaymentMethod = _paymentMethod.value) {
        if (_isProcessingOrder.value) return
        _isProcessingOrder.value = true

        viewModelScope.launch {
            val result = repository.chargeOrder(
                cartItems = _cart.value,
                cashierName = settings.value.cashierName,
                paymentMethod = method
            )

            _isProcessingOrder.value = false
            _isPaymentConfirmOpen.value = false

            result.onSuccess { order ->
                _completedOrder.value = order
                _cart.value = emptyList()
                soundManager.playSoundForEvent(SoundEvent.ORDER_PAID, settings.value)
            }.onFailure { err ->
                _toastMessage.value = err.message ?: "Failed to process order"
                soundManager.playSoundForEvent(SoundEvent.ERROR, settings.value)
            }
        }
    }

    fun dismissCompletedOrder() {
        _completedOrder.value = null
    }

    fun adjustStock(drinkId: Long, delta: Int) {
        viewModelScope.launch {
            repository.adjustStock(drinkId, delta).onSuccess {
                soundManager.playSoundForEvent(if (delta > 0) SoundEvent.ADD_DRINK else SoundEvent.REMOVE_DRINK, settings.value)
            }.onFailure { err ->
                _toastMessage.value = err.message ?: "Failed to adjust stock"
                soundManager.playSoundForEvent(SoundEvent.ERROR, settings.value)
            }
        }
    }

    fun startAddDrink() {
        _isAddingDrink.value = true
        _editingDrink.value = null
    }

    fun startEditDrink(drink: DrinkEntity) {
        _editingDrink.value = drink
        _isAddingDrink.value = false
    }

    fun closeDrinkDialog() {
        _isAddingDrink.value = false
        _editingDrink.value = null
    }

    fun saveDrink(
        id: Long,
        name: String,
        price: Double,
        stock: Int,
        lowStockThreshold: Int,
        colorHex: String,
        imageUri: String?,
        isActive: Boolean = true,
        category: String = "Other"
    ) {
        if (name.trim().isEmpty()) {
            _toastMessage.value = "Drink name cannot be empty"
            soundManager.playSoundForEvent(SoundEvent.ERROR, settings.value)
            return
        }
        if (price < 0 || stock < 0 || lowStockThreshold < 0) {
            _toastMessage.value = "Values cannot be negative"
            soundManager.playSoundForEvent(SoundEvent.ERROR, settings.value)
            return
        }

        viewModelScope.launch {
            val existing = if (id > 0) allDrinks.value.firstOrNull { it.id == id } else null
            val drink = DrinkEntity(
                id = id,
                name = name.trim(),
                price = price,
                stock = stock,
                lowStockThreshold = lowStockThreshold,
                colorHex = colorHex,
                imageUri = imageUri,
                isActive = isActive,
                category = category.trim().ifBlank { "Other" },
                displayOrder = existing?.displayOrder ?: allDrinks.value.size,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            repository.saveDrink(drink)
            closeDrinkDialog()
            _toastMessage.value = if (id == 0L) "Drink added" else "Drink updated"
            soundManager.playSoundForEvent(SoundEvent.ADD_DRINK, settings.value)
        }
    }

    fun toggleDrinkActive(drink: DrinkEntity) {
        viewModelScope.launch {
            repository.saveDrink(drink.copy(isActive = !drink.isActive))
            _toastMessage.value = if (drink.isActive) "Drink hidden" else "Drink activated"
            soundManager.playSoundForEvent(SoundEvent.NAV_TAP, settings.value)
        }
    }

    fun deleteDrink(drink: DrinkEntity) {
        viewModelScope.launch {
            drink.imageUri?.let { imageStorageManager.deleteImage(it) }
            repository.deleteDrink(drink)
            _toastMessage.value = "Drink deleted. Sales history retained."
            soundManager.playSoundForEvent(SoundEvent.REMOVE_DRINK, settings.value)
        }
    }

    fun reorderDrinks(orderedList: List<DrinkEntity>) {
        viewModelScope.launch {
            repository.reorderDrinks(orderedList)
        }
    }

    fun setCropperUri(uri: Uri?, drinkId: Long? = null) {
        _imageCropperUri.value = uri
        _activeCroppingDrinkId.value = drinkId
    }

    fun saveCroppedImage(bitmap: Bitmap, onSaved: (String) -> Unit) {
        viewModelScope.launch {
            val path = imageStorageManager.saveCroppedBitmap(bitmap, "drink_${_activeCroppingDrinkId.value ?: "new"}")
            _imageCropperUri.value = null
            onSaved(path)
            soundManager.playSoundForEvent(SoundEvent.ADD_DRINK, settings.value)
        }
    }

    fun removeDrinkPhoto(existingPath: String?, onRemoved: () -> Unit) {
        viewModelScope.launch {
            existingPath?.let { imageStorageManager.deleteImage(it) }
            onRemoved()
            soundManager.playSoundForEvent(SoundEvent.REMOVE_DRINK, settings.value)
        }
    }

    fun voidOrder(orderId: String) {
        viewModelScope.launch {
            repository.voidOrder(orderId).onSuccess {
                _toastMessage.value = "Order voided & stock restored"
                soundManager.playSoundForEvent(SoundEvent.ORDER_PAID, settings.value)
            }.onFailure { err ->
                _toastMessage.value = err.message ?: "Failed to void order"
                soundManager.playSoundForEvent(SoundEvent.ERROR, settings.value)
            }
        }
    }

    fun deleteOrder(orderId: String) {
        viewModelScope.launch {
            repository.deleteOrder(orderId).onSuccess {
                _toastMessage.value = "Order deleted"
                soundManager.playSoundForEvent(SoundEvent.REMOVE_DRINK, settings.value)
            }.onFailure { err ->
                _toastMessage.value = err.message ?: "Failed to delete order"
                soundManager.playSoundForEvent(SoundEvent.ERROR, settings.value)
            }
        }
    }

    fun editOrder(
        orderId: String,
        cashierName: String,
        paymentMethod: String,
        timestamp: Long,
        newQuantities: Map<Long, Int>
    ) {
        viewModelScope.launch {
            repository.editOrder(orderId, cashierName, paymentMethod, timestamp, newQuantities).onSuccess {
                _toastMessage.value = "Order updated"
                soundManager.playSoundForEvent(SoundEvent.ORDER_PAID, settings.value)
            }.onFailure { err ->
                _toastMessage.value = err.message ?: "Failed to update order"
                soundManager.playSoundForEvent(SoundEvent.ERROR, settings.value)
            }
        }
    }

    fun setReportsDate(millis: Long) {
        _reportsDateMillis.value = millis
    }

    fun setAnalysisFilter(filter: DateFilter) {
        _analysisFilter.value = filter
        soundManager.playSoundForEvent(SoundEvent.NAV_TAP, settings.value)
    }

    fun setCustomAnalysisRange(startMillis: Long, endMillis: Long) {
        _customDateRange.value = Pair(startMillis, endMillis)
        _analysisFilter.value = DateFilter.CUSTOM
    }

    fun updateSettings(newSettings: ShopSettings) {
        viewModelScope.launch {
            repository.updateSettings(newSettings)
            _toastMessage.value = "Settings saved"
        }
    }

    fun previewSound(soundType: SoundType, volume: Float) {
        soundManager.previewSound(soundType, volume, settings.value)
    }

    fun resetSoundSettings() {
        val defaultConfigs = defaultSoundConfigs()
        val updated = settings.value.copy(
            masterVolume = 1.0f,
            masterSoundEnabled = true,
            vibrationEnabled = true,
            soundEvents = defaultConfigs
        )
        updateSettings(updated)
        _toastMessage.value = "Sound settings reset to default"
    }

    fun exportBackup() {
        viewModelScope.launch {
            try {
                val file = backupRestoreManager.exportBackupJson()
                backupRestoreManager.shareFile(file, "application/json", "Sip Shop Backup")
            } catch (e: Exception) {
                _toastMessage.value = "Backup export failed: ${e.message}"
            }
        }
    }

    fun restoreBackup(jsonString: String, overwrite: Boolean) {
        viewModelScope.launch {
            backupRestoreManager.restoreFromJson(jsonString, overwrite)
                .onSuccess { msg ->
                    _toastMessage.value = msg
                    soundManager.playSoundForEvent(SoundEvent.ORDER_PAID, settings.value)
                }
                .onFailure { err ->
                    _toastMessage.value = err.message ?: "Restore failed"
                    soundManager.playSoundForEvent(SoundEvent.ERROR, settings.value)
                }
        }
    }

    fun exportDrinksCsv() {
        viewModelScope.launch {
            try {
                val file = backupRestoreManager.exportDrinksCsv()
                backupRestoreManager.shareFile(file, "text/csv", "Sip Shop Drinks")
            } catch (e: Exception) {
                _toastMessage.value = "Drinks CSV export failed: ${e.message}"
            }
        }
    }

    fun exportOrdersCsv() {
        viewModelScope.launch {
            try {
                val file = backupRestoreManager.exportOrdersCsvFull()
                backupRestoreManager.shareFile(file, "text/csv", "Sip Shop Orders")
            } catch (e: Exception) {
                _toastMessage.value = "Orders CSV export failed: ${e.message}"
            }
        }
    }

    fun importDrinksCsv(csv: String) {
        viewModelScope.launch {
            backupRestoreManager.importDrinksCsv(csv)
                .onSuccess { _toastMessage.value = it; soundManager.playSoundForEvent(SoundEvent.ORDER_PAID, settings.value) }
                .onFailure { _toastMessage.value = it.message ?: "Import failed"; soundManager.playSoundForEvent(SoundEvent.ERROR, settings.value) }
        }
    }

    fun importOrdersCsv(csv: String) {
        viewModelScope.launch {
            backupRestoreManager.importOrdersCsv(csv)
                .onSuccess { _toastMessage.value = it; soundManager.playSoundForEvent(SoundEvent.ORDER_PAID, settings.value) }
                .onFailure { _toastMessage.value = it.message ?: "Import failed"; soundManager.playSoundForEvent(SoundEvent.ERROR, settings.value) }
        }
    }

    fun exportReportsCsv(orders: List<OrderWithItems>) {
        viewModelScope.launch {
            try {
                val file = backupRestoreManager.exportReportsCsv(orders)
                backupRestoreManager.shareFile(file, "text/csv", "Sip Shop Sales Report")
            } catch (e: Exception) {
                _toastMessage.value = "CSV export failed: ${e.message}"
            }
        }
    }

    fun shareSalesSummary(report: AnalysisReport, settings: ShopSettings) {
        val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
        val text = buildString {
            append("🍹 Sip Shop Manager - Sales Summary\n")
            append("Period: ${report.filter.label} (${dateFormat.format(Date(report.startMillis))} - ${dateFormat.format(Date(report.endMillis))})\n")
            append("Revenue: ${CurrencyFormatter.formatDual(report.revenue, settings.khrExchangeRate, settings.currencyMode)}\n")
            append("Paid Orders: ${report.paidOrdersCount}\n")
            append("Drinks Sold: ${report.drinksSoldCount}\n")
            append("Average Order: ${CurrencyFormatter.format(report.averageOrderValue, settings.currencyMode, settings.khrExchangeRate)}\n")
            append("Cash: ${CurrencyFormatter.format(report.cashRevenue, settings.currencyMode)} (${String.format(Locale.US, "%.1f", report.cashPercentage)}%)\n")
            append("ABA: ${CurrencyFormatter.format(report.abaRevenue, settings.currencyMode)} (${String.format(Locale.US, "%.1f", report.abaPercentage)}%)\n")
            if (report.voidOrdersCount > 0) {
                append("Void Orders: ${report.voidOrdersCount} (${CurrencyFormatter.format(report.voidAmount, settings.currencyMode)})\n")
            }
            append("Top Drink: ${report.mostSoldDrink} (${report.mostSoldDrinkQty} sold)\n")
        }
        backupRestoreManager.shareSalesSummaryText(text)
    }

    fun clearToast() {
        _toastMessage.value = null
    }
}
