@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.bookwormbliss.app.ui.screens.search

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.bookwormbliss.app.ui.theme.bookwormColors
import com.bookwormbliss.app.ui.theme.dimens
import com.bookwormbliss.app.ui.theme.radii

@Composable
fun SearchScreen(
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory)
    val state by viewModel.uiState.collectAsState()
    val dimens = MaterialTheme.dimens
    val colors = MaterialTheme.bookwormColors

    Column(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .padding(dimens.md),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search books, authors, series, or tags…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(MaterialTheme.radii.large),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search, keyboardType = KeyboardType.Text),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { viewModel.commitSearch() }),
            )

            Row(
                modifier = Modifier.padding(top = dimens.sm),
                horizontalArrangement = Arrangement.spacedBy(dimens.xs),
            ) {
                listOf(
                    SearchFilter.ALL to "All",
                    SearchFilter.TITLE to "Title",
                    SearchFilter.AUTHOR to "Author",
                    SearchFilter.SERIES to "Series",
                ).forEach { (filterValue, label) ->
                    val selected = state.filter == filterValue
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) colors.accent else colors.surface)
                            .clickable { viewModel.setFilter(filterValue) }
                            .padding(horizontal = dimens.sm, vertical = dimens.xxs),
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) colors.surface else colors.textPrimary)
                    }
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            when {
                state.query.isBlank() -> SearchHistorySection(state.history, onSelect = viewModel::selectHistoryTerm, onDelete = viewModel::deleteHistoryTerm, onClear = viewModel::clearHistory)
                state.results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No results found", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        Text("Try another keyword or change your filter.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                }
                else -> SearchResultsList(
                    results = state.results,
                    onOpenBook = { book ->
                        viewModel.commitSearch()
                        onOpenBook(book)
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchHistorySection(
    history: List<String>,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
) {
    val dimens = MaterialTheme.dimens
    val colors = MaterialTheme.bookwormColors
    Column(Modifier.fillMaxSize().padding(dimens.md)) {
        if (history.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.xxs)) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = colors.accent)
                    Text("Recent Searches", style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
                }
                Text(
                    "Clear",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                    modifier = Modifier.clickable(onClick = onClear),
                )
            }
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.padding(top = dimens.sm),
                horizontalArrangement = Arrangement.spacedBy(dimens.xs),
                verticalArrangement = Arrangement.spacedBy(dimens.xs),
            ) {
                history.forEach { term ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.surface)
                            .clickable { onSelect(term) }
                            .padding(horizontal = dimens.sm, vertical = dimens.xxs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dimens.xxs),
                    ) {
                        Text(term, style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(14.dp).clickable { onDelete(term) },
                        )
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = colors.textMuted)
                    Text("Type to search your personal offline library.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                }
            }
        }
    }
}

@Composable
private fun SearchResultsList(
    results: List<com.bookwormbliss.app.data.model.BookEntity>,
    onOpenBook: (com.bookwormbliss.app.data.model.BookEntity) -> Unit,
) {
    val dimens = MaterialTheme.dimens
    val colors = MaterialTheme.bookwormColors
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(dimens.md),
        verticalArrangement = Arrangement.spacedBy(dimens.sm),
    ) {
        item {
            Text(
                "Found ${results.size} ${if (results.size == 1) "match" else "matches"}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
            )
        }
        items(results, key = { it.id }) { book ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MaterialTheme.radii.medium))
                    .background(colors.surface)
                    .clickable { onOpenBook(book) }
                    .padding(dimens.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.sm),
            ) {
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .aspectRatio(0.68f)
                        .clip(RoundedCornerShape(MaterialTheme.radii.small))
                        .background(colors.surfaceAlt),
                ) {
                    if (book.coverPath != null) {
                        AsyncImage(model = java.io.File(book.coverPath), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.MenuBook, contentDescription = null, tint = colors.textMuted)
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = book.author + (book.series?.let { " • $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
