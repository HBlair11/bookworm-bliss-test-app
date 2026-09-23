package com.bookwormbliss.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * SINGLE SOURCE OF TRUTH — spacing (section 9) and corner radius (section 8)
 * tokens. Never hard-code a `.dp` padding/radius value in a screen; reference
 * `MaterialTheme.dimens.xxx` / `MaterialTheme.radii.xxx` instead so the whole
 * app's rhythm can be tuned from one place.
 */
data class BookwormSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 40.dp,
    val huge: Dp = 48.dp,
    val screenHorizontalPhone: Dp = 16.dp,
    val screenHorizontalTablet: Dp = 24.dp,
)

data class BookwormRadii(
    val small: Dp = 8.dp,
    val control: Dp = 12.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 20.dp,
    val xl: Dp = 24.dp,
    val hero: Dp = 28.dp,
    val pill: Dp = 999.dp,
)

val LocalBookwormSpacing = staticCompositionLocalOf { BookwormSpacing() }
val LocalBookwormRadii = staticCompositionLocalOf { BookwormRadii() }
