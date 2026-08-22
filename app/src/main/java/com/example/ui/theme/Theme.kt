package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

fun parseHexColor(hexString: String, fallback: Color): Color {
    return try {
        val clean = hexString.trim().removePrefix("#")
        val colorInt = when (clean.length) {
            6 -> (0xFF000000 or clean.toLong(16)).toInt()
            8 -> clean.toLong(16).toInt()
            3 -> {
                val r = clean[0].toString().repeat(2)
                val g = clean[1].toString().repeat(2)
                val b = clean[2].toString().repeat(2)
                (0xFF000000 or "$r$g$b".toLong(16)).toInt()
            }
            else -> return fallback
        }
        Color(colorInt)
    } catch (e: Exception) {
        fallback
    }
}

private fun createDynamicColorScheme(
    accentColor: Color,
    useDarkTheme: Boolean
): androidx.compose.material3.ColorScheme {
    return if (useDarkTheme) {
        darkColorScheme(
            primary = accentColor,
            secondary = SecondaryCyanDark,
            tertiary = SecondaryCyanDark,
            background = DarkBackground,
            surface = DarkSurface,
            surfaceVariant = DarkSurfaceVariant,
            onPrimary = Color.Black,
            onSecondary = Color.Black,
            onBackground = TextPrimaryDark,
            onSurface = TextPrimaryDark,
            onSurfaceVariant = TextSecondaryDark,
            outline = FrostedBorderDark,
            error = ErrorRed
        )
    } else {
        lightColorScheme(
            primary = accentColor,
            secondary = SecondaryCyan,
            tertiary = SecondaryCyan,
            background = LightBackground,
            surface = LightSurface,
            surfaceVariant = LightSurfaceVariant,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = TextPrimaryLight,
            onSurface = TextPrimaryLight,
            onSurfaceVariant = TextSecondaryLight,
            outline = FrostedBorderLight,
            error = ErrorRed
        )
    }
}

@Composable
fun MyApplicationTheme(
    themeMode: String = "SYSTEM",
    accentColorHex: String = "#4F46E5",
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val useDarkTheme = when (themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> darkTheme
    }

    val dynamicAccent = parseHexColor(
        accentColorHex,
        if (useDarkTheme) PrimaryIndigoDark else PrimaryIndigo
    )

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        accentColorHex.isNotBlank() && accentColorHex != "#4F46E5" -> {
            createDynamicColorScheme(dynamicAccent, useDarkTheme)
        }
        useDarkTheme -> createDynamicColorScheme(PrimaryIndigoDark, true)
        else -> createDynamicColorScheme(PrimaryIndigo, false)
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}


