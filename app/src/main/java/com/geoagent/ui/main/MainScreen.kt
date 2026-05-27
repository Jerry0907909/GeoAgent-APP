package com.geoagent.ui.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.geoagent.navigation.Routes
import com.geoagent.ui.chat.ChatDetailScreen
import com.geoagent.ui.chat.ChatListViewModel
import com.geoagent.ui.documents.DocumentDetailScreen
import com.geoagent.ui.documents.DocumentListScreen
import com.geoagent.ui.documents.UploadScreen
import com.geoagent.ui.navigation.GeoDrawerSheet
import com.geoagent.ui.settings.AccountSecurityScreen
import com.geoagent.ui.settings.AppearanceScreen
import com.geoagent.ui.settings.SettingsScreen
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun MainScreen(navController: NavHostController) {
    val innerNavController = rememberNavController()
    val navBackStackEntry by innerNavController.currentBackStackEntryAsState()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val chatListViewModel: ChatListViewModel = koinViewModel()
    val listState by chatListViewModel.state.collectAsState()

    val selectedConversationId = navBackStackEntry
        ?.takeIf { it.destination.route?.startsWith("chat/detail") == true }
        ?.arguments
        ?.getInt("conversationId")

    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            chatListViewModel.refresh()
        }
    }

    fun closeDrawer() {
        scope.launch { drawerState.close() }
    }

    fun navigateFromDrawer(route: String) {
        if (route.isBlank()) return
        closeDrawer()
        runCatching {
            innerNavController.navigate(route) {
                launchSingleTop = true
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            GeoDrawerSheet(
                conversations = listState.conversations,
                isLoading = listState.isLoading,
                selectedConversationId = selectedConversationId?.takeIf { it > 0 },
                user = listState.user,
                onConversationClick = { id ->
                    if (id > 0) navigateFromDrawer(Routes.chatDetail(id))
                },
                onOpenSettings = { navigateFromDrawer(Routes.SETTINGS) }
            )
        }
    ) {
        NavHost(
            navController = innerNavController,
            startDestination = Routes.chatDetail(0),
            modifier = Modifier.fillMaxSize()
        ) {
            composable(
                Routes.CHAT_DETAIL,
                arguments = listOf(
                    navArgument("conversationId") {
                        type = NavType.IntType
                        defaultValue = 0
                    }
                )
            ) { entry ->
                val conversationId = entry.arguments?.getInt("conversationId") ?: 0
                ChatDetailScreen(
                    conversationId = conversationId,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onNewChat = {
                        innerNavController.navigate(Routes.chatDetail(0)) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Routes.DOCUMENTS_LIST) {
                DocumentListScreen(
                    onBack = { innerNavController.popBackStack() },
                    onUploadClick = { innerNavController.navigate(Routes.DOCUMENTS_UPLOAD) },
                    onDocumentClick = { doc ->
                        innerNavController.navigate(
                            Routes.documentDetail(doc.source, doc.collection)
                        )
                    }
                )
            }
            composable(
                route = Routes.DOCUMENT_DETAIL,
                arguments = listOf(
                    navArgument("source") { type = NavType.StringType },
                    navArgument("collection") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { entry ->
                val source = entry.arguments?.getString("source").orEmpty()
                val collection = entry.arguments?.getString("collection").orEmpty()
                DocumentDetailScreen(
                    source = source,
                    collection = collection,
                    onBack = { innerNavController.popBackStack() }
                )
            }
            composable(Routes.DOCUMENTS_UPLOAD) {
                UploadScreen(onBack = { innerNavController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { innerNavController.popBackStack() },
                    onNavigateToAccount = { innerNavController.navigate(Routes.SETTINGS_ACCOUNT) },
                    onNavigateToKnowledgeBase = { innerNavController.navigate(Routes.DOCUMENTS_LIST) },
                    onNavigateToAppearance = { innerNavController.navigate(Routes.SETTINGS_APPEARANCE) },
                    onLogout = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.SETTINGS_ACCOUNT) {
                AccountSecurityScreen(
                    onBack = { innerNavController.popBackStack() },
                    onSwitchAccount = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.SETTINGS_APPEARANCE) {
                AppearanceScreen(onBack = { innerNavController.popBackStack() })
            }
        }
    }
}
