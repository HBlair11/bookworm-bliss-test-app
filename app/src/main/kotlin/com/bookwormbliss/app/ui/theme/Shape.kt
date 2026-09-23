package com.bookwormbliss.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * SINGLE SOURCE OF TRUTH — shape scale (section 8), built from the same
 * radius values as [BookwormRadii]. Screens should reach for
 * `MaterialTheme.shapes.xxx` rather than writing `RoundedCornerShape(16.dp)`
 * inline; the extra tokens the Material3 scale doesn't cover (pill, hero)
 * live as top-level vals here.
 */
val BookwormShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

val PillShape = RoundedCornerShape(999.dp)
val HeroShape = RoundedCornerShape(28.dp)
