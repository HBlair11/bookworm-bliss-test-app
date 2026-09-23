package com.bookwormbliss.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
 * The book-card look used across Home, Library, Authors and Series (16dp
 * radius per the design spec's "book card: 16dp" rule). Every screen that
 * shows a book cover uses this composable rather than rolling its own.
 */
@Composable
fun BookCard(
    book: BookEntity,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.bookwormColors
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(MaterialTheme.radii.medium))
                .background(colors.surfaceAlt),
        ) {
            if (book.coverPath != null) {
                AsyncImage(
                    model = java.io.File(book.coverPath),
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.MenuBook,
                        contentDescription = null,
                        tint = colors.textMuted,
                    )
                }
            }
            if (book.progress in 0.01f..0.995f) {
                LinearProgressIndicator(
                    progress = { book.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    color = colors.accent,
                    trackColor = Color.Transparent,
                )
            }
        }
        Column(Modifier.padding(top = MaterialTheme.dimens.xs)) {
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
        }
    }
}
