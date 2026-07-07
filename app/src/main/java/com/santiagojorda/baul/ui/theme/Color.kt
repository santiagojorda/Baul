package com.santiagojorda.baul.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val Blue40 = Color(0xFF3E5F91)
private val BlueGrey40 = Color(0xFF565F71)
private val Teal40 = Color(0xFF3C6472)

private val Blue80 = Color(0xFFAAC7FF)
private val BlueGrey80 = Color(0xFFBEC6DC)
private val Teal80 = Color(0xFFA2CEDE)

val LightColorScheme: ColorScheme = lightColorScheme(
    primary = Blue40,
    secondary = BlueGrey40,
    tertiary = Teal40,
)

val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = Blue80,
    secondary = BlueGrey80,
    tertiary = Teal80,
)

/** Material3 no trae un rol semántico de "éxito" — se agrega acá para no hardcodear el color suelto. */
private val SuccessLight = Color(0xFF2E7D32)
private val SuccessDark = Color(0xFF8BD088)

val ColorScheme.success: Color
    get() = if (this === DarkColorScheme) SuccessDark else SuccessLight
