package com.bookwormbliss.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalBookwormColors = staticCompositionLocalOf { lightBookwormColors() }

/**
 * Every screen/component reads design tokens through these three
 * extension properties. This is the single point of contact between UI
 * code and the design system — nothing else should import BookwormPalette,
 * BookwormSpacing or BookwormRadii directly.
 */
val MaterialTheme.bookwormColors: BookwormColors
    @Composable get() = LocalBookwormColors.current

val MaterialTheme.dimens: BookwormSpacing
    @Composable get() = LocalBookwormSpacing.current

val MaterialTheme.radii: BookwormRadii
    @Composable get() = LocalBookwormRadii.current

@Composable
fun BookwormBlissTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val bookwormColors = if (darkTheme) darkBookwormColors() else lightBookwormColors()

    // Material3's own color scheme is derived from the same tokens so that
    // stock Material components (rare — we prefer our own) still land on
    // the right palette instead of default Android purple/teal.
    val materialScheme = if (darkTheme) {
        darkColorScheme(
            primary = bookwormColors.accent,
            onPrimary = bookwormColors.surface,
            secondary = bookwormColors.secondary,
            background = bookwormColors.background,
            onBackground = bookwormColors.textPrimary,
            surface = bookwormColors.surface,
            onSurface = bookwormColors.textPrimary,
            surfaceVariant = bookwormColors.surfaceAlt,
            error = bookwormColors.error,
            outline = bookwormColors.border,
        )
    } else {
        lightColorScheme(
            primary = bookwormColors.accent,
            onPrimary = bookwormColors.surface,
            secondary = bookwormColors.secondary,
            background = bookwormColors.background,
            onBackground = bookwormColors.textPrimary,
            surface = bookwormColors.surface,
            onSurface = bookwormColors.textPrimary,
            surfaceVariant = bookwormColors.surfaceAlt,
            error = bookwormColors.error,
            outline = bookwormColors.border,
        )
    }

    CompositionLocalProvider(
        LocalBookwormColors provides bookwormColors,
        LocalBookwormSpacing provides BookwormSpacing(),
        LocalBookwormRadii provides BookwormRadii(),
    ) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = BookwormType,
            shapes = BookwormShapes,
            content = content,
        )
    }
}
