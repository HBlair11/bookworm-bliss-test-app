@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.bookwormbliss.app.ui.screens.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bookwormbliss.app.R
import com.bookwormbliss.app.data.model.SortOption
import com.bookwormbliss.app.ui.components.BookCard
import com.bookwormbliss.app.ui.components.EmptyState
import com.bookwormbliss.app.ui.theme.bookwormColors
import com.bookwormbliss.app.ui.theme.dimens

@Composable
fun LibraryScreen(
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
    scope: LibraryScope = LibraryScope.ALL,
    filterValue: String? = null,
    titleOverride: String? = null,
    emptyBodyOverride: String? = null,
) {
    val viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(scope, filterValue))
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val dimens = MaterialTheme.dimens

    val pickEpubs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.importEpubs(uris) { uri ->
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        }
    }

    LaunchedEffect(state.importError) {
        state.importError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LibraryToolbar(
                title = titleOverride ?: stringResource(R.string.library_title),
                count = state.books.size,
                viewModeGrid = state.prefs.viewModeGrid,
                gridColumns = state.prefs.gridColumns,
                sortOption = state.prefs.sortOption,
                sortAscending = state.prefs.sortAscending,
                onSetGrid = { viewModel.updatePrefs { it.copy(viewModeGrid = true) } },
                onSetList = { viewModel.updatePrefs { it.copy(viewModeGrid = false) } },
                onSetColumns = { cols -> viewModel.updatePrefs { it.copy(gridColumns = cols, viewModeGrid = true) } },
                onSetSort = { option ->
                    viewModel.updatePrefs {
                        if (it.sortOption == option) it.copy(sortAscending = !it.sortAscending) else it.copy(sortOption = option)
                    }
                },
                onImport = { pickEpubs.launch(arrayOf("application/epub+zip", "application/octet-stream")) },
            )

            Box(Modifier.fillMaxSize()) {
                when {
                    state.isImporting && state.books.isEmpty() -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    state.books.isEmpty() -> {
                        EmptyState(
                            title = stringResource(R.string.library_empty_title),
                            body = emptyBodyOverride ?: stringResource(R.string.library_empty_body),
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    state.prefs.viewModeGrid -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(state.prefs.gridColumns),
                            contentPadding = PaddingValues(dimens.screenHorizontalPhone, dimens.md, dimens.screenHorizontalPhone, dimens.huge),
                            horizontalArrangement = Arrangement.spacedBy(dimens.md),
                            verticalArrangement = Arrangement.spacedBy(dimens.lg),
                        ) {
                            items(state.books, key = { it.id }) { book ->
                                BookCard(
                                    book = book,
                                    isGrid = true,
                                    onClick = { onOpenBook(book.id) },
                                    onToggleFavorite = { viewModel.toggleFavorite(book) },
                                )
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(dimens.screenHorizontalPhone, dimens.md, dimens.screenHorizontalPhone, dimens.huge),
                            verticalArrangement = Arrangement.spacedBy(dimens.sm),
                        ) {
                            items(state.books, key = { it.id }) { book ->
                                BookCard(
                                    book = book,
                                    isGrid = false,
                                    onClick = { onOpenBook(book.id) },
                                    onToggleFavorite = { viewModel.toggleFavorite(book) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryToolbar(
    title: String,
    count: Int,
    viewModeGrid: Boolean,
    gridColumns: Int,
    sortOption: SortOption,
    sortAscending: Boolean,
    onSetGrid: () -> Unit,
    onSetList: () -> Unit,
    onSetColumns: (Int) -> Unit,
    onSetSort: (SortOption) -> Unit,
    onImport: () -> Unit,
) {
    val dimens = MaterialTheme.dimens
    val colors = MaterialTheme.bookwormColors
    var showColumnsMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.screenHorizontalPhone, vertical = dimens.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
            Text(
                text = if (count == 1) "1 book" else "$count books",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                IconButton(onClick = { if (viewModeGrid) showColumnsMenu = true else onSetGrid() }) {
                    Icon(
                        Icons.Filled.GridView,
                        contentDescription = stringResource(R.string.library_grid_view),
                        tint = if (viewModeGrid) colors.accent else colors.textPrimary,
                    )
                }
                DropdownMenu(expanded = showColumnsMenu, onDismissRequest = { showColumnsMenu = false }) {
                    listOf(2, 3, 4).forEach { cols ->
                        DropdownMenuItem(
                            text = { Text("$cols columns") },
                            onClick = { onSetColumns(cols); showColumnsMenu = false },
                            trailingIcon = { if (viewModeGrid && gridColumns == cols) Text("✓") },
                        )
                    }
                }
            }
            IconButton(onClick = onSetList) {
                Icon(
                    Icons.Filled.ViewList,
                    contentDescription = stringResource(R.string.library_list_view),
                    tint = if (!viewModeGrid) colors.accent else colors.textPrimary,
                )
            }
            Box {
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(Icons.Filled.SwapVert, contentDescription = stringResource(R.string.library_sort), tint = colors.textPrimary)
                }
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    val options = listOf(
                        SortOption.RECENTLY_READ to "Recently Read",
                        SortOption.RECENTLY_ADDED to "Recently Added",
                        SortOption.TITLE to "Title",
                        SortOption.AUTHOR to "Author",
                        SortOption.SERIES to "Series",
                        SortOption.PROGRESS to "Progress",
                    )
                    options.forEach { (opt, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { onSetSort(opt); showSortMenu = false },
                            trailingIcon = {
                                if (sortOption == opt) Text(if (sortAscending) "ASC" else "DESC", style = MaterialTheme.typography.labelSmall)
                            },
                        )
                    }
                }
            }
            IconButton(onClick = onImport) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.library_import), tint = colors.accent)
            }
        }
    }
}
