package com.bookwormbliss.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MenuBook
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.bookwormbliss.app.R
import com.bookwormbliss.app.data.model.BookEntity
import com.bookwormbliss.app.ui.components.BookCard
import com.bookwormbliss.app.ui.components.BookwormButton
import com.bookwormbliss.app.ui.components.BookwormOutlinedButton
import com.bookwormbliss.app.ui.components.EmptyState
import com.bookwormbliss.app.ui.theme.bookwormColors
import com.bookwormbliss.app.ui.theme.dimens
import com.bookwormbliss.app.ui.theme.radii
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    onOpenBook: (String) -> Unit,
    onOpenBookDetails: (String) -> Unit,
    onSelectView: (String) -> Unit,
    onSelectAuthor: (String) -> Unit,
    onSelectSeries: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
    val state by viewModel.uiState.collectAsState()
    val dimens = MaterialTheme.dimens

    if (state.isEmpty) {
        EmptyState(
            title = stringResource(R.string.home_empty_title),
            body = stringResource(R.string.home_empty_body),
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = dimens.lg, horizontal = dimens.screenHorizontalPhone),
        verticalArrangement = Arrangement.spacedBy(dimens.xl),
    ) {
        state.heroBook?.let { hero ->
            item {
                HeroContinueReadingCard(
                    hero = hero,
                    onSectionHeaderClick = { onSelectView("reading") },
                    onOpenReader = { onOpenBook(hero.id) },
                    onOpenDetails = { onOpenBookDetails(hero.id) },
                )
            }
        }

        item {
            HomeSectionHeader(
                icon = Icons.Filled.MenuBook,
                title = stringResource(R.string.home_recently_added),
                actionLabel = stringResource(R.string.nav_library),
                onActionClick = { onSelectView("library") },
            )
        }
        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.height(((state.recentlyAdded.size / 3 + 1) * 210).dp),
                horizontalArrangement = Arrangement.spacedBy(dimens.sm),
                verticalArrangement = Arrangement.spacedBy(dimens.sm),
                userScrollEnabled = false,
            ) {
                items(state.recentlyAdded, key = { it.id }) { book ->
                    BookCard(book = book, isGrid = true, onClick = { onOpenBook(book.id) })
                }
            }
        }

        if (state.favorites.isNotEmpty()) {
            item {
                HomeSectionHeader(
                    icon = Icons.Filled.Favorite,
                    title = stringResource(R.string.nav_favorites),
                    actionLabel = "View All",
                    onActionClick = { onSelectView("favorites") },
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(dimens.xs)) {
                    state.favorites.forEach { book ->
                        FavoriteListRow(book = book, onClick = { onOpenBook(book.id) })
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.md)) {
                if (state.topAuthors.isNotEmpty()) {
                    BrowseByCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.Groups,
                        title = "Browse by Author",
                        actionLabel = "All Authors",
                        onActionClick = { onSelectView("authors") },
                    ) {
                        state.topAuthors.forEach { author ->
                            BrowseByRow(
                                label = author.name,
                                countLabel = if (author.bookCount == 1) "1 book" else "${author.bookCount} books",
                                onClick = { onSelectAuthor(author.name) },
                            )
                        }
                    }
                }
                if (state.topSeries.isNotEmpty()) {
                    BrowseByCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.AutoStories,
                        title = "Browse by Series",
                        actionLabel = "All Series",
                        onActionClick = { onSelectView("series") },
                    ) {
                        state.topSeries.forEach { series ->
                            BrowseByRow(
                                label = series.name,
                                countLabel = if (series.bookCount == 1) "1 vol" else "${series.bookCount} vols",
                                onClick = { onSelectSeries(series.name) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroContinueReadingCard(
    hero: BookEntity,
    onSectionHeaderClick: () -> Unit,
    onOpenReader: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    val colors = MaterialTheme.bookwormColors
    val dimens = MaterialTheme.dimens

    Column {
        HomeSectionHeader(
            icon = Icons.Filled.BookmarkAdded,
            title = stringResource(R.string.home_continue_reading),
            actionLabel = "View All",
            onActionClick = onSectionHeaderClick,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(MaterialTheme.radii.hero))
                .background(colors.surface)
                .clickable(onClick = onOpenReader)
                .padding(dimens.lg),
            horizontalArrangement = Arrangement.spacedBy(dimens.lg),
        ) {
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .aspectRatio(0.68f)
                    .clip(RoundedCornerShape(MaterialTheme.radii.medium))
                    .background(colors.surfaceAlt),
            ) {
                if (hero.coverPath != null) {
                    AsyncImage(
                        model = java.io.File(hero.coverPath),
                        contentDescription = hero.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(hero.title, style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "by ${hero.author}" + (hero.series?.let { " • $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                )
                Column(Modifier.padding(top = dimens.sm)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Progress", style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
                        Text("${(hero.progress * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = colors.accent)
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .padding(top = dimens.xxs)
                            .background(colors.surfaceAccent, RoundedCornerShape(3.dp)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction = hero.progress.coerceIn(0f, 1f))
                                .height(6.dp)
                                .background(colors.accent, RoundedCornerShape(3.dp)),
                        )
                    }
                }
                Row(Modifier.padding(top = dimens.md), horizontalArrangement = Arrangement.spacedBy(dimens.xs)) {
                    BookwormButton(text = "Resume", onClick = onOpenReader)
                    BookwormOutlinedButton(text = "Details", onClick = onOpenDetails)
                }
            }
        }
    }
}

@Composable
private fun HomeSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    actionLabel: String,
    onActionClick: () -> Unit,
) {
    val colors = MaterialTheme.bookwormColors
    val dimens = MaterialTheme.dimens
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = dimens.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.xxs)) {
            Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onActionClick),
        ) {
            Text(actionLabel, style = MaterialTheme.typography.labelMedium, color = colors.accent)
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun FavoriteListRow(book: BookEntity, onClick: () -> Unit) {
    val colors = MaterialTheme.bookwormColors
    val dimens = MaterialTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MaterialTheme.radii.medium))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(dimens.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.sm),
    ) {
        Box(
            modifier = Modifier
                .width(44.dp)
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(MaterialTheme.radii.small))
                .background(colors.surfaceAlt),
        ) {
            if (book.coverPath != null) {
                AsyncImage(model = java.io.File(book.coverPath), contentDescription = book.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Column {
            Text(book.title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(book.author, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BrowseByCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    actionLabel: String,
    onActionClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.bookwormColors
    val dimens = MaterialTheme.dimens
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(MaterialTheme.radii.hero))
            .background(colors.surface)
            .padding(dimens.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = dimens.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.xxs)) {
                Icon(icon, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(16.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
            }
            Text(
                actionLabel,
                style = MaterialTheme.typography.labelSmall,
                color = colors.accent,
                modifier = Modifier.clickable(onClick = onActionClick),
            )
        }
        content()
    }
}

@Composable
private fun BrowseByRow(label: String, countLabel: String, onClick: () -> Unit) {
    val colors = MaterialTheme.bookwormColors
    val dimens = MaterialTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MaterialTheme.radii.small))
            .clickable(onClick = onClick)
            .padding(vertical = dimens.xs, horizontal = dimens.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(colors.surfaceAccent)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Text(countLabel, style = MaterialTheme.typography.labelSmall, color = colors.textPrimary)
        }
    }
}
