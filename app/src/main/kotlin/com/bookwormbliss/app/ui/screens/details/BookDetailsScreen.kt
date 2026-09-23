package com.bookwormbliss.app.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bookwormbliss.app.R
import com.bookwormbliss.app.ui.components.BookwormButton
import com.bookwormbliss.app.ui.theme.bookwormColors
import com.bookwormbliss.app.ui.theme.dimens
import com.bookwormbliss.app.ui.theme.radii

@Composable
fun BookDetailsScreen(
    bookId: String,
    onStartReading: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: BookDetailsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = BookDetailsViewModel.factory(bookId))
    val book by viewModel.book.collectAsState()
    val colors = MaterialTheme.bookwormColors
    val dimens = MaterialTheme.dimens

    val current = book
    if (current == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(dimens.screenHorizontalPhone),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.md),
    ) {
        Box(
            modifier = Modifier
                .width(160.dp)
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(MaterialTheme.radii.medium))
                .background(colors.surfaceAlt),
        ) {
            current.coverPath?.let { path ->
                AsyncImage(
                    model = java.io.File(path),
                    contentDescription = current.title,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        Text(current.title, style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary)
        Text(
            stringResource(R.string.details_by_author, current.author),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textMuted,
        )

        Icon(
            imageVector = if (current.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = stringResource(if (current.isFavorite) R.string.details_unfavorite else R.string.details_favorite),
            tint = colors.accent,
            modifier = Modifier.clickable { viewModel.toggleFavorite(current) },
        )

        BookwormButton(
            text = stringResource(if (current.progress > 0f) R.string.details_continue else R.string.details_start),
            onClick = { onStartReading(current.id) },
            modifier = Modifier.fillMaxWidth(),
        )

        current.description?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
        }
    }
}
