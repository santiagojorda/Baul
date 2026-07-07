package com.santiagojorda.baul.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Cascarón Card+Row+padding que se repetía en cada fila de lista (Historial, Reglas, Cuentas, Carpetas excluidas). */
@Composable
fun ListItemCard(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 6.dp)
    val rowModifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)

    if (onClick != null) {
        Card(onClick = onClick, modifier = cardModifier, colors = CardDefaults.cardColors()) {
            Row(
                modifier = rowModifier,
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    } else {
        Card(modifier = cardModifier, colors = CardDefaults.cardColors()) {
            Row(
                modifier = rowModifier,
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}
