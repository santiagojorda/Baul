package com.santiagojorda.mediasync.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import com.santiagojorda.mediasync.MediaSyncApplication
import com.santiagojorda.mediasync.ui.accounts.AccountsScreen
import com.santiagojorda.mediasync.ui.history.HistoryScreen
import com.santiagojorda.mediasync.ui.history.HistoryViewModel
import com.santiagojorda.mediasync.ui.rulelist.RuleListScreen
import com.santiagojorda.mediasync.ui.rulelist.RuleListViewModel
import com.santiagojorda.mediasync.ui.ruleeditor.RuleEditorScreen
import com.santiagojorda.mediasync.ui.ruleeditor.RuleEditorViewModel

private val bottomBarRoutes = setOf(MediaSyncDestinations.RULE_LIST, MediaSyncDestinations.HISTORY, MediaSyncDestinations.ACCOUNTS)

@Composable
fun MediaSyncApp() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as MediaSyncApplication
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in bottomBarRoutes) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == MediaSyncDestinations.RULE_LIST,
                        onClick = { navController.navigateToBottomBarRoute(MediaSyncDestinations.RULE_LIST) },
                        icon = { Icon(Icons.Default.List, contentDescription = null) },
                        label = { Text("Reglas") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == MediaSyncDestinations.HISTORY,
                        onClick = { navController.navigateToBottomBarRoute(MediaSyncDestinations.HISTORY) },
                        icon = { Icon(Icons.Default.History, contentDescription = null) },
                        label = { Text("Historial") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == MediaSyncDestinations.ACCOUNTS,
                        onClick = { navController.navigateToBottomBarRoute(MediaSyncDestinations.ACCOUNTS) },
                        icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                        label = { Text("Cuentas") },
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == MediaSyncDestinations.RULE_LIST) {
                FloatingActionButton(onClick = { navController.navigate(MediaSyncDestinations.ruleEditorRoute()) }) {
                    Icon(Icons.Default.Add, contentDescription = "Nueva regla")
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MediaSyncDestinations.RULE_LIST,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(MediaSyncDestinations.RULE_LIST) {
                val viewModel: RuleListViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { RuleListViewModel(app.ruleRepository, app.uploadLogRepository) }
                    },
                )
                RuleListScreen(
                    viewModel = viewModel,
                    onEditRule = { ruleId -> navController.navigate(MediaSyncDestinations.ruleEditorRoute(ruleId)) },
                )
            }
            composable(
                route = MediaSyncDestinations.RULE_EDITOR_ROUTE,
                arguments = listOf(
                    navArgument(MediaSyncDestinations.RULE_EDITOR_ARG_RULE_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                ),
            ) { entry ->
                val ruleId = entry.arguments?.getLong(MediaSyncDestinations.RULE_EDITOR_ARG_RULE_ID) ?: -1L
                val viewModel: RuleEditorViewModel = viewModel(
                    key = "rule_editor_$ruleId",
                    factory = viewModelFactory {
                        initializer { RuleEditorViewModel(app.ruleRepository, ruleId.takeIf { it != -1L }) }
                    },
                )
                RuleEditorScreen(viewModel = viewModel, onDone = { navController.popBackStack() })
            }
            composable(MediaSyncDestinations.HISTORY) {
                val viewModel: HistoryViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { HistoryViewModel(app.uploadLogRepository) }
                    },
                )
                HistoryScreen(viewModel = viewModel)
            }
            composable(MediaSyncDestinations.ACCOUNTS) {
                AccountsScreen()
            }
        }
    }
}

private fun NavController.navigateToBottomBarRoute(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
