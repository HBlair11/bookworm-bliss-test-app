package com.bookwormbliss.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bookwormbliss.app.R
import com.bookwormbliss.app.ui.components.BookCard
import com.bookwormbliss.app.ui.components.EmptyState
import com.bookwormbliss.app.ui.components.SectionHeading
import com.bookwormbliss.app.ui.theme.dimens

@Composable
fun HomeScreen(
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
    val state by viewModel.uiState.collectAsState()
    val dimens = MaterialTheme.dimens

    if (state.isEmpty) {
        EmptyState(
            title = stringRes(R.string.home_empty_title),
            body = stringRes(R.string.home_empty_body),
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = dimens.lg),
        verticalArrangement = Arrangement.spacedBy(dimens.lg),
    ) {
        if (state.currentlyReading.isNotEmpty()) {
            item {
                SectionHeading(
                    text = stringRes(R.string.home_continue_reading),
                    modifier = Modifier.padding(horizontal = dimens.screenHorizontalPhone),
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = dimens.screenHorizontalPhone),
                    horizontalArrangement = Arrangement.spacedBy(dimens.md),
                ) {
                    items(state.currentlyReading, key = { it.id }) { book ->
                        BookCard(book = book, modifier = Modifier.width(140.dp), onClick = { onOpenBook(book.id) })
                    }
                }
            }
        }

        item {
            SectionHeading(
                text = stringRes(R.string.home_recently_added),
                modifier = Modifier.padding(horizontal = dimens.screenHorizontalPhone),
            )
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = dimens.screenHorizontalPhone),
                horizontalArrangement = Arrangement.spacedBy(dimens.md),
            ) {
                items(state.recentlyAdded, key = { it.id }) { book ->
                    BookCard(book = book, modifier = Modifier.width(140.dp), onClick = { onOpenBook(book.id) })
                }
            }
        }
    }
}

@Composable
private fun stringRes(id: Int): String = androidx.compose.ui.res.stringResource(id)
