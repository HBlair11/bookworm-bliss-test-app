package com.bookwormbliss.app.ui.screens.authorsseries

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.bookwormbliss.app.R
import com.bookwormbliss.app.ui.components.EmptyState
import com.bookwormbliss.app.ui.theme.bookwormColors
import com.bookwormbliss.app.ui.theme.dimens
import com.bookwormbliss.app.ui.theme.radii

@Composable
fun AuthorsScreen(onSelectAuthor: (String) -> Unit, modifier: Modifier = Modifier) {
    val viewModel: AuthorsSeriesViewModel = viewModel(factory = AuthorsSeriesViewModel.Factory)
    val authors by viewModel.authors.collectAsState()
    NamedGroupList(
        groups = authors,
        emptyBody = stringResource(R.string.coming_soon_authors),
        onSelect = onSelectAuthor,
        countSuffix = { if (it == 1) "book" else "books" },
        modifier = modifier,
    )
}

@Composable
fun SeriesScreen(onSelectSeries: (String) -> Unit, modifier: Modifier = Modifier) {
    val viewModel: AuthorsSeriesViewModel = viewModel(factory = AuthorsSeriesViewModel.Factory)
    val series by viewModel.series.collectAsState()
    NamedGroupList(
        groups = series,
        emptyBody = stringResource(R.string.coming_soon_series),
        onSelect = onSelectSeries,
        countSuffix = { if (it == 1) "vol" else "vols" },
        modifier = modifier,
    )
}

@Composable
private fun NamedGroupList(
    groups: List<NamedGroup>,
    emptyBody: String,
    onSelect: (String) -> Unit,
    countSuffix: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    val dimens = MaterialTheme.dimens
    val colors = MaterialTheme.bookwormColors

    if (groups.isEmpty()) {
        EmptyState(title = stringResource(R.string.coming_soon_title), body = emptyBody, modifier = modifier.fillMaxSize())
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(dimens.screenHorizontalPhone, dimens.md, dimens.screenHorizontalPhone, dimens.huge),
        verticalArrangement = Arrangement.spacedBy(dimens.xs),
    ) {
        items(groups, key = { it.name }) { group ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MaterialTheme.radii.medium))
                    .background(colors.surface)
                    .clickable { onSelect(group.name) }
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
                    if (group.coverPath != null) {
                        AsyncImage(model = java.io.File(group.coverPath), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = colors.textMuted)
                        }
                    }
                }
                Text(group.name, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.surfaceAccent)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text("${group.bookCount} ${countSuffix(group.bookCount)}", style = MaterialTheme.typography.labelSmall, color = colors.textPrimary)
                }
            }
        }
    }
}
