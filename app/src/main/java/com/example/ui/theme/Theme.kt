package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun FinancasTheme(
    themeMode: String = "dark", // "light" | "dark"
    primaryColorHex: String = "#1A73E8",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val primaryColor = try {
        Color(android.graphics.Color.parseColor(primaryColorHex))
    } catch (e: Exception) {
        Color(0xFF1A73E8)
    }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = primaryColor,
            secondary = primaryColor.copy(alpha = 0.8f),
            tertiary = Color(0xFF4DB6AC),
            background = SlateDarkBackground,
            surface = SlateDarkSurface,
            surfaceVariant = SlateDarkSurfaceVariant,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = TextPrimaryDark,
            onSurface = TextPrimaryDark,
            onSurfaceVariant = TextSecondaryDark
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            secondary = primaryColor.copy(alpha = 0.8f),
            tertiary = Color(0xFF00796B),
            background = LightBackground,
            surface = LightSurface,
            surfaceVariant = LightSurfaceVariant,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = TextPrimaryLight,
            onSurface = TextPrimaryLight,
            onSurfaceVariant = TextSecondaryLight
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
