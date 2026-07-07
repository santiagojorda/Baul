package com.santiagojorda.baul.ui.accounts

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.santiagojorda.baul.domain.model.ConnectedAccount
import com.santiagojorda.baul.ui.common.EmptyState
import com.santiagojorda.baul.ui.common.ListItemCard

@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val accounts by viewModel.accounts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.onSignInResult(result.data)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AccountsEvent.Error -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Button(
            onClick = { activity?.let { signInLauncher.launch(viewModel.signInIntent(it)) } },
            enabled = !isLoading && activity != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isLoading) "Conectando…" else "Agregar cuenta de Google")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (accounts.isEmpty()) {
            EmptyState(message = "Todavía no conectaste ninguna cuenta.")
        } else {
            if (accounts.size > 1) {
                Text(
                    text = "La cuenta con la estrella es la que usa el auto-sync de carpetas nuevas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            LazyColumn {
                items(accounts, key = { it.email }) { account ->
                    AccountRow(
                        account = account,
                        onRemove = { viewModel.removeAccount(account) },
                        onSetDefault = { viewModel.setDefault(account) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountRow(account: ConnectedAccount, onRemove: () -> Unit, onSetDefault: () -> Unit) {
    ListItemCard {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = account.displayName ?: account.email, style = MaterialTheme.typography.titleMedium)
            Text(text = account.email, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onSetDefault) {
            Icon(
                imageVector = if (account.isDefault) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = if (account.isDefault) "Cuenta default del auto-sync" else "Marcar como default",
            )
        }
        IconButton(onClick = onRemove) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = "Quitar cuenta")
        }
    }
}
