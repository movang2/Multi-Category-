package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = ElegantDarkPrimary,
    onPrimary = ElegantDarkOnPrimary,
    primaryContainer = ElegantDarkPrimaryContainer,
    onPrimaryContainer = ElegantDarkOnPrimaryContainer,
    secondaryContainer = ElegantDarkSecondaryContainer,
    onSecondaryContainer = ElegantDarkOnSecondaryContainer,
    background = ElegantDarkBg,
    onBackground = ElegantDarkOnBackground,
    surface = ElegantDarkSurface,
    onSurface = ElegantDarkOnSurface,
    surfaceVariant = ElegantDarkSurfaceVariant,
    onSurfaceVariant = ElegantDarkOnSurfaceVariant,
    outline = ElegantDarkOutline
  )

private val LightColorScheme =
  lightColorScheme(
    primary = ElegantDarkPrimaryLight,
    onPrimary = ElegantDarkOnBgLight,
    primaryContainer = ElegantDarkPrimaryContainerLight,
    onPrimaryContainer = ElegantDarkPrimary,
    secondaryContainer = ElegantDarkSecondaryContainer,
    onSecondaryContainer = ElegantDarkOnSecondaryContainer,
    background = ElegantDarkBgLight,
    onBackground = ElegantDarkOnBgLight,
    surface = ElegantDarkSurfaceLight,
    onSurface = ElegantDarkOnBgLight,
    surfaceVariant = ElegantDarkBgLight,
    onSurfaceVariant = ElegantDarkOutlineLight,
    outline = ElegantDarkOutlineLight
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Default to true to force 'Elegant Dark' design aesthetic on launch
  dynamicColor: Boolean = false, // Disable dynamic colors by default so custom brand colors stand out
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
