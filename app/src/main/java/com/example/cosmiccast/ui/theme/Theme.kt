package com.example.cosmiccast.ui.theme

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
    primary = PumpkinOrangeNight,
    secondary = OffWhiteNight,
    background = DarkGreyNight,
    surface = DarkGreyNight,
    onPrimary = DarkGreyNight,
    onSecondary = DarkGreyNight,
    onBackground = OffWhiteNight,
    onSurface = OffWhiteNight
)

private val LightColorScheme = lightColorScheme(
    primary = PumpkinOrange,
    secondary = DarkGrey,
    background = OffWhite,
    surface = OffWhite,
    onPrimary = OffWhite,
    onSecondary = OffWhite,
    onBackground = DarkGrey,
    onSurface = DarkGrey
)

@Composable
fun CosmicCastTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
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