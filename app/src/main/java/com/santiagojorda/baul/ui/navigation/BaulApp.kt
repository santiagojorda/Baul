package com.santiagojorda.baul.ui.navigation

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.santiagojorda.baul.BaulApplication
import com.santiagojorda.baul.domain.model.UploadLogEntry
import com.santiagojorda.baul.media.RuleMatcher
import com.santiagojorda.baul.storage.AllFilesAccess
import com.santiagojorda.baul.storage.FolderPlaceholder
import com.santiagojorda.baul.ui.accounts.AccountsScreen
import com.santiagojorda.baul.ui.accounts.AccountsViewModel
import com.santiagojorda.baul.ui.excludedfolders.ExcludedFoldersScreen
import com.santiagojorda.baul.ui.excludedfolders.ExcludedFoldersViewModel
import com.santiagojorda.baul.ui.history.HistoryScreen
import com.santiagojorda.baul.ui.history.HistoryViewModel
import com.santiagojorda.baul.ui.logs.LogsScreen
import com.santiagojorda.baul.ui.logs.LogsViewModel
import com.santiagojorda.baul.ui.rulelist.RuleListScreen
import com.santiagojorda.baul.ui.rulelist.RuleListViewModel
import com.santiagojorda.baul.ui.ruleeditor.RuleEditorScreen
import com.santiagojorda.baul.ui.ruleeditor.RuleEditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val bottomBarRoutes = setOf(BaulDestinations.RULE_LIST, BaulDestinations.HISTORY, BaulDestinations.ACCOUNTS)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaulApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as BaulApplication
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }

    DeleteUploadedSourcesEffect(app)
    ScanExistingFoldersEffect(app)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (currentRoute == BaulDestinations.RULE_LIST) {
                TopAppBar(
                    title = { Text("Reglas") },
                    actions = {
                        IconButton(onClick = { navController.navigate(BaulDestinations.EXCLUDED_FOLDERS) }) {
                            Icon(Icons.Default.Block, contentDescription = "Carpetas excluidas del auto-sync")
                        }
                    },
                )
            }
            if (currentRoute == BaulDestinations.HISTORY) {
                TopAppBar(
                    title = { Text("Historial") },
                    actions = {
                        IconButton(onClick = { navController.navigate(BaulDestinations.LOGS) }) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = "Ver registro de errores")
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (currentRoute in bottomBarRoutes) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == BaulDestinations.RULE_LIST,
                        onClick = { navController.navigateToBottomBarRoute(BaulDestinations.RULE_LIST) },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        label = { Text("Reglas") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == BaulDestinations.HISTORY,
                        onClick = { navController.navigateToBottomBarRoute(BaulDestinations.HISTORY) },
                        icon = { Icon(Icons.Default.History, contentDescription = null) },
                        label = { Text("Historial") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == BaulDestinations.ACCOUNTS,
                        onClick = { navController.navigateToBottomBarRoute(BaulDestinations.ACCOUNTS) },
                        icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                        label = { Text("Cuentas") },
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == BaulDestinations.RULE_LIST) {
                FloatingActionButton(onClick = { navController.navigate(BaulDestinations.ruleEditorRoute()) }) {
                    Icon(Icons.Default.Add, contentDescription = "Nueva regla")
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BaulDestinations.RULE_LIST,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(BaulDestinations.RULE_LIST) {
                val viewModel: RuleListViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            RuleListViewModel(
                                app.ruleRepository,
                                app.uploadLogRepository,
                                app.syncCoordinator,
                                app.excludedFolderRepository,
                            )
                        }
                    },
                )
                RuleListScreen(
                    viewModel = viewModel,
                    onEditRule = { ruleId -> navController.navigate(BaulDestinations.ruleEditorRoute(ruleId)) },
                )
            }
            composable(
                route = BaulDestinations.RULE_EDITOR_ROUTE,
                arguments = listOf(
                    navArgument(BaulDestinations.RULE_EDITOR_ARG_RULE_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                ),
            ) { entry ->
                val ruleId = entry.arguments?.getLong(BaulDestinations.RULE_EDITOR_ARG_RULE_ID) ?: -1L
                val viewModel: RuleEditorViewModel = viewModel(
                    key = "rule_editor_$ruleId",
                    factory = viewModelFactory {
                        initializer {
                            RuleEditorViewModel(
                                app.ruleRepository,
                                app.connectedAccountRepository,
                                app.syncCoordinator,
                                ruleId.takeIf { it != -1L },
                            )
                        }
                    },
                )
                RuleEditorScreen(viewModel = viewModel, onDone = { navController.popBackStack() })
            }
            composable(BaulDestinations.HISTORY) {
                val viewModel: HistoryViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { HistoryViewModel(app.uploadLogRepository, app.ruleRepository) }
                    },
                )
                HistoryScreen(viewModel = viewModel)
            }
            composable(BaulDestinations.ACCOUNTS) {
                val viewModel: AccountsViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { AccountsViewModel(app.connectedAccountRepository, app.googleAuthManager) }
                    },
                )
                AccountsScreen(viewModel = viewModel, snackbarHostState = snackbarHostState)
            }
            composable(BaulDestinations.EXCLUDED_FOLDERS) {
                val viewModel: ExcludedFoldersViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { ExcludedFoldersViewModel(app.excludedFolderRepository) }
                    },
                )
                ExcludedFoldersScreen(viewModel = viewModel)
            }
            composable(BaulDestinations.LOGS) {
                val viewModel: LogsViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { LogsViewModel(app.uploadLogRepository, app.ruleRepository) }
                    },
                )
                LogsScreen(viewModel = viewModel)
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
 * El UploadWorker no puede confirmar el borrado del original en background (ver comentario en
 * [com.santiagojorda.baul.work.UploadWorker.doWork]), así que queda pendiente hasta la próxima
 * vez que se abre la app. Con "Acceso a todos los archivos" otorgado ([AllFilesAccess]) se borra
 * directo acá, sin preguntar nada; si no, se pide confirmación al sistema en un solo diálogo para
 * todo el lote (requiere una Activity).
 */
@Composable
private fun DeleteUploadedSourcesEffect(app: BaulApplication) {
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
        if (pending.isEmpty()) return@LaunchedEffect

        // Si el archivo ya no existe en MediaStore (borrado a mano, por otra app, o ya borrado
        // en una corrida anterior que no llegó a marcar la fila), createDeleteRequest tira
        // IllegalArgumentException para TODO el lote y sin este filtro la app queda en un loop
        // de crash al abrir, porque la fila pendiente nunca deja de estar pendiente.
        val (missing, existing) = withContext(Dispatchers.IO) {
            pending.partition { entry -> !mediaStoreRowExists(context, Uri.parse(entry.mediaUri)) }
        }
        if (missing.isNotEmpty()) {
            app.uploadLogRepository.markSourceDeleted(missing)
        }
        if (existing.isEmpty()) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            // Antes de borrar el/los últimos archivos de una carpeta, se deja un placeholder ahí
            // para que la carpeta nunca quede vacía (ver FolderPlaceholder).
            existing.map { it.ruleId }.distinct().forEach { ruleId ->
                val rule = app.database.ruleDao().getRuleById(ruleId) ?: return@forEach
                val relativePath = RuleMatcher.expectedRelativePath(rule) ?: return@forEach
                FolderPlaceholder.ensure(context.contentResolver, relativePath)
            }
        }

        if (AllFilesAccess.isGranted()) {
            // Con "Acceso a todos los archivos" otorgado no hace falta pedir confirmación al
            // sistema: se borra directo, sin diálogo, ni al abrir la app ni nunca.
            withContext(Dispatchers.IO) {
                existing.forEach { entry -> context.contentResolver.delete(Uri.parse(entry.mediaUri), null, null) }
            }
            app.uploadLogRepository.markSourceDeleted(existing)
            return@LaunchedEffect
        }

        if (activity == null) return@LaunchedEffect
        pendingEntries = existing
        try {
            val uris = existing.map { Uri.parse(it.mediaUri) }
            val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, uris)
            deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        } catch (e: IllegalArgumentException) {
            // Condición de carrera (se borró entre el chequeo de arriba y esta llamada): se
            // reintenta la próxima vez que se abra la app, no vale la pena crashear por esto.
            pendingEntries = emptyList()
        }
    }
}

private fun mediaStoreRowExists(context: Context, uri: Uri): Boolean =
    try {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
            ?.use { it.moveToFirst() }
            ?: false
    } catch (e: SecurityException) {
        false
    }

/**
 * Carpetas que ya tenían fotos/videos de antes de usar la app nunca generan un evento de
 * ContentObserver (no cambian), así que el auto-sync reactivo nunca las encuentra solo. Al abrir
 * la app se corre un barrido completo de la MediaStore para agarrarlas también.
 */
@Composable
private fun ScanExistingFoldersEffect(app: BaulApplication) {
    LaunchedEffect(Unit) {
        app.syncCoordinator.scanExistingFoldersForAutoSync()
    }
}
