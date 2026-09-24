package com.bookwormbliss.app.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bookwormbliss.app.R
import com.bookwormbliss.app.ui.components.EmptyState
import com.bookwormbliss.app.ui.theme.bookwormColors
import com.bookwormbliss.app.ui.theme.dimens
import com.bookwormbliss.app.ui.theme.radii

@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    val viewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory)
    val state by viewModel.uiState.collectAsState()
    val dimens = MaterialTheme.dimens
    val colors = MaterialTheme.bookwormColors

    if (!state.hasAnyData) {
        EmptyState(
            title = stringResource(R.string.coming_soon_title),
            body = stringResource(R.string.coming_soon_stats),
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    val hours = state.totalMinutesRead / 60
    val minutes = state.totalMinutesRead % 60

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(dimens.screenHorizontalPhone),
        horizontalArrangement = Arrangement.spacedBy(dimens.md),
        verticalArrangement = Arrangement.spacedBy(dimens.md),
    ) {
        items(
            listOf(
                Triple(Icons.Filled.LocalFireDepartment, "Current streak", "${state.currentStreakDays} ${if (state.currentStreakDays == 1) "day" else "days"}"),
                Triple(Icons.Filled.Timer, "Time reading", if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"),
                Triple(Icons.Filled.CheckCircle, "Finished", "${state.finishedBooks} ${if (state.finishedBooks == 1) "book" else "books"}"),
                Triple(Icons.Filled.MenuBook, "In your library", "${state.totalBooks} ${if (state.totalBooks == 1) "book" else "books"}"),
            ),
        ) { (icon, label, value) ->
            StatCard(icon = icon, label = label, value = value)
        }
    }
}

@Composable
private fun StatCard(icon: ImageVector, label: String, value: String) {
    val dimens = MaterialTheme.dimens
    val colors = MaterialTheme.bookwormColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(MaterialTheme.radii.large))
            .padding(dimens.md),
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent)
        Text(value, style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary, modifier = Modifier.padding(top = dimens.xs))
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
    }
}
