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
    primary = NaturalRosePrimaryDark,
    onPrimary = NaturalRoseOnPrimary,
    primaryContainer = NaturalRoseContainerDark,
    secondary = NaturalLavenderSecondary,
    secondaryContainer = NaturalLavenderContainer,
    tertiary = NaturalPeachTertiary,
    background = NaturalBackgroundDark,
    surface = NaturalSurfaceDark,
    surfaceVariant = NaturalSurfaceVariantDark,
    error = PanicRed,
    errorContainer = PanicRedContainer
  )

private val LightColorScheme =
  lightColorScheme(
    primary = NaturalRosePrimary,
    onPrimary = NaturalRoseOnPrimary,
    primaryContainer = NaturalRoseContainer,
    onPrimaryContainer = NaturalRoseOnContainer,
    secondary = NaturalLavenderSecondary,
    secondaryContainer = NaturalLavenderContainer,
    onSecondaryContainer = NaturalLavenderOnContainer,
    tertiary = NaturalPeachTertiary,
    tertiaryContainer = NaturalPeachContainer,
    onTertiaryContainer = NaturalPeachOnContainer,
    background = NaturalBackgroundLight,
    onBackground = NaturalOnBackgroundLight,
    surface = NaturalSurfaceLight,
    surfaceVariant = NaturalSurfaceVariantLight,
    outline = NaturalOutlineLight,
    error = PanicRed,
    errorContainer = PanicRedContainer,
    onErrorContainer = PanicOnRedContainer
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
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
