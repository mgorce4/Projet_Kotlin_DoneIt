package com.example.doneit.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = MainPurple,
    onPrimary = White,
    background = DarkGrey,
    onBackground = White,
    surface = Grey,
    onSurface = White,
    secondary = LightPurple,
    onSecondary = Black,
    error = MainPurple, // Utilisation de MainPurple comme couleur d'erreur par défaut
    onError = White,
    tertiary = LightGrey
)

private val LightColorScheme = lightColorScheme(
    primary = MainPurple,
    onPrimary = White,
    background = White, // Fond principal blanc
    onBackground = DarkGrey, // Texte principal foncé
    surface = LightGrey, // Surfaces claires (inputs, cartes)
    onSurface = DarkGrey, // Texte sur surface
    secondary = LightPurple,
    onSecondary = Black,
    error = MainPurple,
    onError = White,
    tertiary = Grey // Pour éléments secondaires

)

@Composable
fun DoneItTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}