package com.santiagojorda.baul.domain.upload

private const val PERCENT_SCALE = 100

/**
 * Porcentaje 0-100 de [uploaded] sobre [total], o `null` si [total] todavía no se conoce (0 o
 * negativo) — se usa tanto en el historial como en la notificación de "subiendo ahora" para que
 * las dos pantallas calculen el mismo número de la misma forma.
 */
fun uploadPercent(uploaded: Long, total: Long): Int? =
    if (total > 0) (uploaded * PERCENT_SCALE / total).toInt().coerceIn(0, PERCENT_SCALE) else null
