package com.santiagojorda.mediasync.ui.accounts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Placeholder: todavía no hay integración con Credential Manager / Google Sign-In. Por ahora
 * la cuenta de una regla se carga como texto libre en el editor de reglas. Esta pantalla se
 * completa cuando se implemente el flujo de OAuth multi-cuenta.
 */
@Composable
fun AccountsScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "La gestión de cuentas de Google todavía no está implementada.\n" +
                "Por ahora, escribí el email de la cuenta directamente al crear una regla.",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp),
        )
    }
}
