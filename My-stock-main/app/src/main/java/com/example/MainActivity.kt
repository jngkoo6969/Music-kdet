package com.example

import com.example.ui.theme.LocalBarPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedContent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.NavigationTab
import com.example.data.model.OrderStatus
import com.example.ui.components.SipBottomNavBar
import com.example.ui.components.SipTopBar
import com.example.ui.screens.AnalysisScreen
import com.example.ui.screens.NewOrderScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SoundSettingsScreen
import com.example.ui.screens.StockScreen
import com.example.ui.theme.SipShopTheme
import com.example.ui.viewmodel.ShopViewModel
import com.example.util.CurrencyFormatter
import java.util.Calendar

class MainActivity : ComponentActivity() {

    private val viewModel: ShopViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
            val cart by viewModel.cart.collectAsStateWithLifecycle()
            val drinks by viewModel.allDrinks.collectAsStateWithLifecycle()
            val allOrders by viewModel.allOrders.collectAsStateWithLifecycle()
            val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

            val snackbarHostState = remember { SnackbarHostState() }

            // Status-bar / nav-bar icons must follow the APP theme (Settings), not only the phone theme,
            // otherwise white icons sit on a light bar (or black on dark) and cannot be seen.
            val isDark = when (settings.themePreference) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }
            DisposableEffect(isDark) {
                this@MainActivity.enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT
                    ) { isDark },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT
                    ) { isDark }
                )
                onDispose { }
            }

            // Handle Toast / Snackbar messages
            LaunchedEffect(toastMessage) {
                toastMessage?.let { msg ->
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    viewModel.clearToast()
                }
            }

            // Calculate today's sales for TopBar badge
            // Only recomputed when orders change (not on every recomposition)
            val todaySales = remember(allOrders) {
                val calToday = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startOfDay = calToday.timeInMillis
                allOrders
                    .filter { it.order.status == OrderStatus.PAID.name && it.order.orderTimestamp >= startOfDay }
                    .sumOf { it.order.orderTotal }
            }

            val totalCartItems = remember(cart) { cart.sumOf { it.quantity } }
            val lowStockCount = remember(drinks) { drinks.count { it.isActive && it.stock in 1..it.lowStockThreshold } }

            // BackHandler: Return to New Order tab if on another screen
            BackHandler(enabled = currentTab != NavigationTab.NEW_ORDER) {
                viewModel.selectTab(NavigationTab.NEW_ORDER)
            }

            SipShopTheme(
                themePreference = settings.themePreference,
                fontScale = settings.fontScale,
                spacingScale = settings.spacingScale,
                backgroundColor = settings.backgroundColor,
                blockColor = settings.blockColor,
                textColor = settings.textColor
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    // FIX: a transparent Scaffold gives plain Text a BLACK default colour -> invisible in dark mode
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    topBar = {
                        SipTopBar(
                            shopName = settings.shopName,
                            cashierName = settings.cashierName,
                            currencyMode = settings.currencyMode,
                            todaySalesFormatted = CurrencyFormatter.format(
                                todaySales,
                                settings.currencyMode,
                                settings.khrExchangeRate
                            )
                        )
                    },
                    bottomBar = {
                        SipBottomNavBar(
                            currentTab = currentTab,
                            onTabSelected = { viewModel.selectTab(it) },
                            cartItemCount = totalCartItems,
                            lowStockCount = lowStockCount
                        )
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    // Content fills the whole screen (scrolls under the glass bars); each screen
                    // adds LocalBarPadding itself so nothing is hidden behind the bars.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                      CompositionLocalProvider(LocalBarPadding provides innerPadding) {
                        // Direction-aware slide + fade + soft scale between tabs
                        AnimatedContent(
                            targetState = currentTab,
                            transitionSpec = {
                                val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                                (slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { (it * 0.12f).toInt() * dir } +
                                    fadeIn(tween(320, delayMillis = 60)) +
                                    scaleIn(tween(380, easing = FastOutSlowInEasing), initialScale = 0.96f)) togetherWith
                                    (slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -(it * 0.08f).toInt() * dir } +
                                        fadeOut(tween(200)) +
                                        scaleOut(tween(300), targetScale = 0.98f))
                            },
                            label = "tabTransition"
                        ) { tab ->
                            when (tab) {
                                NavigationTab.NEW_ORDER -> NewOrderScreen(viewModel = viewModel)
                                NavigationTab.STOCK -> StockScreen(viewModel = viewModel)
                                NavigationTab.REPORTS -> ReportsScreen(viewModel = viewModel)
                                NavigationTab.ANALYSIS -> AnalysisScreen(viewModel = viewModel)
                                NavigationTab.SOUNDS -> SoundSettingsScreen(viewModel = viewModel)
                                NavigationTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
                            }
                        }
                      }
                    }
                }
            }
        }
    }
}
