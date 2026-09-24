package com.bookwormbliss.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bookwormbliss.app.data.model.BookEntity
import com.bookwormbliss.app.ui.theme.bookwormColors
import com.bookwormbliss.app.ui.theme.dimens
import com.bookwormbliss.app.ui.theme.radii

/**
 * The book-card look used across Home, Library, Authors and Series. Has a
 * grid variant (cover-forward, matching the web app's BookCard isGrid=true)
 * and a list variant (row layout, isGrid=false) — pass [isGrid] to switch.
 */
@Composable
fun BookCard(
    book: BookEntity,
    modifier: Modifier = Modifier,
    isGrid: Boolean = true,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)? = null,
) {
    if (isGrid) {
        GridBookCard(book, modifier, onClick, onToggleFavorite)
    } else {
        ListBookCard(book, modifier, onClick, onToggleFavorite)
    }
}

@Composable
private fun GridBookCard(
    book: BookEntity,
    modifier: Modifier,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)?,
) {
    val colors = MaterialTheme.bookwormColors
    val dimens = MaterialTheme.dimens
    val percent = (book.progress * 100).toInt()
    val isFinished = book.progress >= 0.98f

    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .background(colors.surface, RoundedCornerShape(MaterialTheme.radii.medium))
            .padding(dimens.xs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(MaterialTheme.radii.small))
                .background(colors.surfaceAlt),
        ) {
            CoverOrPlaceholder(book, colors)

            if (onToggleFavorite != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(dimens.xxs)
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        .clickable { onToggleFavorite() }
                        .padding(6.dp),
                ) {
                    Icon(
                        imageVector = if (book.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = if (book.isFavorite) colors.accent else Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            if (isFinished) {
                StatusBadge(text = "Finished", color = colors.purplePop, icon = Icons.Filled.Check, modifier = Modifier.align(Alignment.BottomStart).padding(dimens.xxs))
            } else if (percent > 0) {
                StatusBadge(text = "$percent%", color = colors.accent, modifier = Modifier.align(Alignment.BottomStart).padding(dimens.xxs))
            }

            book.series?.let { series ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(dimens.xxs)
                        .background(colors.surface.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = series + (book.seriesIndex?.let { " #${it.toInt()}" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Column(Modifier.padding(top = dimens.xs)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = book.author,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .padding(top = dimens.xxs)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(colors.surfaceAccent, RoundedCornerShape(2.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = (book.progress).coerceIn(0f, 1f))
                        .background(colors.warmPop, RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

@Composable
private fun ListBookCard(
    book: BookEntity,
    modifier: Modifier,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)?,
) {
    val colors = MaterialTheme.bookwormColors
    val dimens = MaterialTheme.dimens
    val percent = (book.progress * 100).toInt()
    val isFinished = book.progress >= 0.98f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(colors.surface, RoundedCornerShape(MaterialTheme.radii.medium))
            .padding(dimens.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.sm),
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(MaterialTheme.radii.small))
                .background(colors.surfaceAlt),
        ) {
            CoverOrPlaceholder(book, colors)
        }

        Column(Modifier.weight(1f)) {
            Text(book.title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = book.author + (book.series?.let { s -> " • $s" + (book.seriesIndex?.let { " #${it.toInt()}" } ?: "") } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = dimens.xxs).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.xs),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(colors.surfaceAccent, RoundedCornerShape(2.dp)),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = book.progress.coerceIn(0f, 1f))
                            .background(if (isFinished) colors.purplePop else colors.accent, RoundedCornerShape(2.dp)),
                    )
                }
                Text(
                    text = if (isFinished) "Done" else "$percent%",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                )
            }
        }

        if (onToggleFavorite != null) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (book.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    tint = if (book.isFavorite) colors.accent else colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun CoverOrPlaceholder(book: BookEntity, colors: com.bookwormbliss.app.ui.theme.BookwormColors) {
    if (book.coverPath != null) {
        AsyncImage(
            model = java.io.File(book.coverPath),
            contentDescription = book.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.Filled.MenuBook, contentDescription = null, tint = colors.textMuted)
        }
    }
}

@Composable
private fun StatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        modifier = modifier
            .background(color, RoundedCornerShape(999.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp)) }
        Text(text, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}
