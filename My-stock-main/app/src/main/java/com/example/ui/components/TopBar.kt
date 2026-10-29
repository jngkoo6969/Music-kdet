package com.example.ui.components

import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import com.example.ui.theme.SipTealSecondary
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NavigationTab
import com.example.ui.theme.SipOceanBlue
import com.example.ui.theme.SipTealPrimary

@Composable
fun SipTopBar(
    shopName: String,
    cashierName: String,
    currencyMode: String,
    todaySalesFormatted: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp), clip = false)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(SipTealSecondary, SipOceanBlue))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🍹",
                        fontSize = 20.sp
                    )
                }

                Column {
                    Text(
                        text = shopName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Cashier",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = cashierName.ifBlank { "Cashier" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Sales pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .testTag("top_bar_sales_badge")
                    .background(Brush.horizontalGradient(listOf(SipTealPrimary, SipOceanBlue)), RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Today:",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                    Text(
                        text = todaySalesFormatted,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun SipBottomNavBar(
    currentTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    cartItemCount: Int,
    lowStockCount: Int,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        Triple(NavigationTab.NEW_ORDER, Icons.Default.PointOfSale, cartItemCount),
        Triple(NavigationTab.STOCK, Icons.Default.Inventory, lowStockCount),
        Triple(NavigationTab.REPORTS, Icons.Default.Assessment, 0),
        Triple(NavigationTab.ANALYSIS, Icons.Default.Analytics, 0),
        Triple(NavigationTab.SOUNDS, Icons.Default.VolumeUp, 0),
        Triple(NavigationTab.SETTINGS, Icons.Default.Settings, 0)
    )
    val selectedIndex = tabs.indexOfFirst { it.first == currentTab }.coerceAtLeast(0)
    val accent = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant
    val barShape = RoundedCornerShape(33.dp)

    BoxWithConstraints(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(66.dp)
            .shadow(4.dp, barShape, clip = false)
            .background(MaterialTheme.colorScheme.surface, barShape)
            .clip(barShape)
            .testTag("bottom_nav_bar")
    ) {
        val itemWidth = maxWidth / tabs.size
        val indicatorX by animateDpAsState(
            targetValue = itemWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f),
            label = "navIndicatorX"
        )

        // Sliding "active" pill. It is inset on every side so it always stays inside the bar.
        Box(
            modifier = Modifier
                .offset(x = indicatorX)
                .width(itemWidth)
                .fillMaxHeight()
                .padding(horizontal = 3.dp, vertical = 5.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(accent.copy(alpha = 0.18f))
        )

        Row(modifier = Modifier.fillMaxSize()) {
            tabs.forEach { (tab, icon, badgeCount) ->
                val isSelected = currentTab == tab
                val tint by animateColorAsState(
                    targetValue = if (isSelected) accent else inactive,
                    label = "navItemColor"
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.1f else 1f,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 380f),
                    label = "navIconScale"
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { onTabSelected(tab) }
                        )
                        .testTag("nav_tab_${tab.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(modifier = Modifier.scale(iconScale)) {
                        if (badgeCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = if (tab == NavigationTab.STOCK) MaterialTheme.colorScheme.error else SipOceanBlue
                                    ) { Text("$badgeCount") }
                                }
                            ) {
                                Icon(icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(22.dp))
                            }
                        } else {
                            Icon(icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(22.dp))
                        }
                    }
                    Text(
                        text = if (tab == NavigationTab.NEW_ORDER) "Order" else tab.label,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        color = tint,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Clip
                    )
                }
            }
        }
    }
}

@Composable
fun SipNavigationRail(
    currentTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    cartItemCount: Int,
    lowStockCount: Int,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier,
        containerColor = Color.Transparent,
        header = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SipTealPrimary),
                contentAlignment = Alignment.Center
            ) {
                Text("🍹", fontSize = 24.sp)
            }
        }
    ) {
        val tabs = listOf(
            Triple(NavigationTab.NEW_ORDER, Icons.Default.PointOfSale, cartItemCount),
            Triple(NavigationTab.STOCK, Icons.Default.Inventory, lowStockCount),
            Triple(NavigationTab.REPORTS, Icons.Default.Assessment, 0),
            Triple(NavigationTab.ANALYSIS, Icons.Default.Analytics, 0),
            Triple(NavigationTab.SOUNDS, Icons.Default.VolumeUp, 0),
            Triple(NavigationTab.SETTINGS, Icons.Default.Settings, 0)
        )

        tabs.forEach { (tab, icon, badgeCount) ->
            NavigationRailItem(
                selected = currentTab == tab,
                onClick = { onTabSelected(tab) },
                icon = {
                    if (badgeCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge { Text("$badgeCount") }
                            }
                        ) {
                            Icon(icon, contentDescription = tab.label)
                        }
                    } else {
                        Icon(icon, contentDescription = tab.label)
                    }
                },
                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}
