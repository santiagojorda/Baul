package com.santiagojorda.mediasync.ui.navigation

import android.app.Activity
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.santiagojorda.mediasync.domain.model.UploadLogEntry
import com.santiagojorda.mediasync.ui.accounts.AccountsScreen
import com.santiagojorda.mediasync.ui.accounts.AccountsViewModel
import com.santiagojorda.mediasync.ui.excludedfolders.ExcludedFoldersScreen
import com.santiagojorda.mediasync.ui.excludedfolders.ExcludedFoldersViewModel
import com.santiagojorda.mediasync.ui.history.HistoryScreen
import com.santiagojorda.mediasync.ui.history.HistoryViewModel
import com.santiagojorda.mediasync.ui.rulelist.RuleListScreen
import com.santiagojorda.mediasync.ui.rulelist.RuleListViewModel
import com.santiagojorda.mediasync.ui.ruleeditor.RuleEditorScreen
import com.santiagojorda.mediasync.ui.ruleeditor.RuleEditorViewModel
import kotlinx.coroutines.launch

private val bottomBarRoutes = setOf(MediaSyncDestinations.RULE_LIST, MediaSyncDestinations.HISTORY, MediaSyncDestinations.ACCOUNTS)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaSyncApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as MediaSyncApplication
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    DeleteUploadedSourcesEffect(app)
    ScanExistingFoldersEffect(app)

    Scaffold(
        topBar = {
            if (currentRoute == MediaSyncDestinations.RULE_LIST) {
                TopAppBar(
                    title = { Text("Reglas") },
                    actions = {
                        IconButton(onClick = { navController.navigate(MediaSyncDestinations.EXCLUDED_FOLDERS) }) {
                            Icon(Icons.Default.Block, contentDescription = "Carpetas excluidas del auto-sync")
                        }
                    },
                )
            }
        },
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
                        initializer {
                            RuleEditorViewModel(
                                app.ruleRepository,
                                app.connectedAccountRepository,
                                app.mediaSyncCoordinator,
                                ruleId.takeIf { it != -1L },
                            )
                        }
                    },
                )
                RuleEditorScreen(viewModel = viewModel, onDone = { navController.popBackStack() })
            }
            composable(MediaSyncDestinations.HISTORY) {
                val viewModel: HistoryViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { HistoryViewModel(app.uploadLogRepository, app.ruleRepository) }
                    },
                )
                HistoryScreen(viewModel = viewModel)
            }
            composable(MediaSyncDestinations.ACCOUNTS) {
                val viewModel: AccountsViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { AccountsViewModel(app.connectedAccountRepository, app.googleAuthManager) }
                    },
                )
                AccountsScreen(viewModel = viewModel)
            }
            composable(MediaSyncDestinations.EXCLUDED_FOLDERS) {
                val viewModel: ExcludedFoldersViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { ExcludedFoldersViewModel(app.excludedFolderRepository) }
                    },
                )
                ExcludedFoldersScreen(viewModel = viewModel)
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

/**
 * El borrado del original necesita confirmación del sistema (Activity), no lo puede hacer el
 * UploadWorker solo en background. Al abrir la app, si hay archivos subidos con éxito cuya regla
 * pide borrar el original y todavía no se confirmó, se pide todo junto en un solo diálogo.
 */
@Composable
private fun DeleteUploadedSourcesEffect(app: MediaSyncApplication) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    var pendingEntries by remember { mutableStateOf<List<UploadLogEntry>>(emptyList()) }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            scope.launch { app.uploadLogRepository.markSourceDeleted(pendingEntries) }
        }
        pendingEntries = emptyList()
    }

    LaunchedEffect(Unit) {
        val pending = app.uploadLogRepository.getPendingDeletions()
        if (pending.isNotEmpty() && activity != null) {
            pendingEntries = pending
            val uris = pending.map { Uri.parse(it.mediaUri) }
            val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, uris)
            deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        }
    }
}

/**
 * Carpetas que ya tenían fotos/videos de antes de usar la app nunca generan un evento de
 * ContentObserver (no cambian), así que el auto-sync reactivo nunca las encuentra solo. Al abrir
 * la app se corre un barrido completo de la MediaStore para agarrarlas también.
 */
@Composable
private fun ScanExistingFoldersEffect(app: MediaSyncApplication) {
    LaunchedEffect(Unit) {
        app.mediaSyncCoordinator.scanExistingFoldersForAutoSync()
    }
}
