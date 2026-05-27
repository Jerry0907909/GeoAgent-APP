package com.geoagent.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubble
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geoagent.R
import com.geoagent.ui.theme.BrandPrimary
import com.geoagent.ui.theme.TextMuted

data class BottomNavItem(
    val route: String,
    val labelResId: Int,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Routes.CHAT_LIST, R.string.nav_chat, Icons.Outlined.ChatBubble),
    BottomNavItem(Routes.DOCUMENTS_LIST, R.string.nav_documents, Icons.AutoMirrored.Outlined.LibraryBooks),
    BottomNavItem(Routes.SETTINGS, R.string.nav_settings, Icons.Outlined.Settings)
)

@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        tonalElevation = 0.dp
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = stringResource(item.labelResId),
                        tint = if (selected) BrandPrimary else TextMuted
                    )
                },
                label = {
                    Text(
                        text = stringResource(item.labelResId),
                        fontSize = 12.sp
                    )
                },
                selected = selected,
                onClick = { onNavigate(item.route) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BrandPrimary,
                    selectedTextColor = BrandPrimary,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted
                )
            )
        }
    }
}