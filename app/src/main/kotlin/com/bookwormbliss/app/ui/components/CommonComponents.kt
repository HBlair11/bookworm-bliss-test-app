package com.bookwormbliss.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.bookwormbliss.app.ui.theme.bookwormColors
import com.bookwormbliss.app.ui.theme.dimens
import com.bookwormbliss.app.ui.theme.radii

/**
 * The web-style rounded accent button described in the spec (section 2):
 * a deliberately-shaped control, not a raw Material default with a color
 * swapped in. Every primary call-to-action in the app should use this.
 */
@Composable
fun BookwormButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val colors = MaterialTheme.bookwormColors
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(MaterialTheme.radii.control),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = colors.surface,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = MaterialTheme.dimens.lg,
            vertical = MaterialTheme.dimens.sm,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(end = MaterialTheme.dimens.xs))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun BookwormOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.bookwormColors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(MaterialTheme.radii.control),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accentDark),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** A Cinzel-styled section heading, used sparingly per the spec (section 6). */
@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.bookwormColors.textPrimary,
        modifier = modifier,
    )
}

/** Shared empty/placeholder state — used both for genuinely-empty lists and for not-yet-built screens. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.bookwormColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(MaterialTheme.dimens.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.dimens.sm),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.padding(bottom = MaterialTheme.dimens.xs),
        )
        Text(title, style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
        action?.invoke()
    }
}

/** A small pill-shaped chip, e.g. for genres/subjects or filter toggles. */
@Composable
fun BookwormChip(text: String, modifier: Modifier = Modifier, selected: Boolean = false) {
    val colors = MaterialTheme.bookwormColors
    Row(
        modifier = modifier
            .background(
                color = if (selected) colors.accent else colors.surfaceAccent,
                shape = RoundedCornerShape(MaterialTheme.radii.pill),
            )
            .padding(horizontal = MaterialTheme.dimens.sm, vertical = MaterialTheme.dimens.xxs),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.surface else colors.textPrimary,
        )
    }
}
