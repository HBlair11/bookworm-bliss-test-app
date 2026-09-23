package com.bookwormbliss.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bookwormbliss.app.R
import com.bookwormbliss.app.ui.screens.ComingSoonScreen
import com.bookwormbliss.app.ui.screens.details.BookDetailsScreen
import com.bookwormbliss.app.ui.screens.home.HomeScreen
import com.bookwormbliss.app.ui.screens.library.LibraryScreen
import com.bookwormbliss.app.ui.screens.reader.ReaderScreen
import com.bookwormbliss.app.ui.screens.settings.SettingsScreen
import com.bookwormbliss.app.ui.theme.dimens
import kotlinx.coroutines.launch

private data class DrawerDestination(val route: String, val labelRes: Int, val icon: ImageVector)

private val drawerDestinations = listOf(
    DrawerDestination(Routes.HOME, R.string.nav_home, Icons.Filled.Home),
    DrawerDestination(Routes.LIBRARY, R.string.nav_library, Icons.Filled.MenuBook),
    DrawerDestination(Routes.FAVORITES, R.string.nav_favorites, Icons.Filled.Favorite),
    DrawerDestination(Routes.AUTHORS, R.string.nav_authors, Icons.Filled.Groups),
    DrawerDestination(Routes.SERIES, R.string.nav_series, Icons.Filled.AutoStories),
    DrawerDestination(Routes.READING_NOOK, R.string.nav_reading_nook, Icons.Filled.Spa),
    DrawerDestination(Routes.STATS, R.string.nav_stats, Icons.Filled.Insights),
    DrawerDestination(Routes.VOCABULARY, R.string.nav_vocabulary, Icons.Filled.Translate),
    DrawerDestination(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
)

/**
 * App shell: a Material navigation drawer + top bar wrapping a single
 * NavHost. The Reader screen draws its own full-screen chrome, so when the
 * back stack is on Routes.READER the drawer/top bar are simply not drawn
 * around the same NavHost — there is only ever one NavHost/NavController
 * for the whole app.
 */
@Composable
fun BookwormNavGraph() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isReaderRoute = currentRoute == Routes.READER

    val navHost: @Composable (Modifier) -> Unit = { modifier ->
        NavHost(navController = navController, startDestination = Routes.HOME, modifier = modifier) {
            composable(Routes.HOME) {
                HomeScreen(onOpenBook = { id -> navController.navigate(Routes.bookDetails(id)) })
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(onOpenBook = { id -> navController.navigate(Routes.bookDetails(id)) })
            }
            composable(Routes.FAVORITES) { ComingSoonScreen(R.string.coming_soon_authors) }
            composable(Routes.AUTHORS) { ComingSoonScreen(R.string.coming_soon_authors) }
            composable(Routes.SERIES) { ComingSoonScreen(R.string.coming_soon_series) }
            composable(Routes.READING_NOOK) { ComingSoonScreen(R.string.coming_soon_nook) }
            composable(Routes.STATS) { ComingSoonScreen(R.string.coming_soon_stats) }
            composable(Routes.VOCABULARY) { ComingSoonScreen(R.string.coming_soon_vocabulary) }
            composable(Routes.SEARCH) { ComingSoonScreen(R.string.coming_soon_search) }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(
                route = Routes.BOOK_DETAILS,
                arguments = listOf(navArgument(Routes.ARG_BOOK_ID) { type = NavType.StringType }),
            ) { backStack ->
                val bookId = backStack.arguments?.getString(Routes.ARG_BOOK_ID).orEmpty()
                BookDetailsScreen(
                    bookId = bookId,
                    onStartReading = { id -> navController.navigate(Routes.reader(id)) },
                )
            }
            composable(
                route = Routes.READER,
                arguments = listOf(navArgument(Routes.ARG_BOOK_ID) { type = NavType.StringType }),
            ) { backStack ->
                val bookId = backStack.arguments?.getString(Routes.ARG_BOOK_ID).orEmpty()
                ReaderScreen(bookId = bookId, onBack = { navController.popBackStack() })
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isReaderRoute,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(MaterialTheme.dimens.lg),
                )
                drawerDestinations.forEach { dest ->
                    NavigationDrawerItem(
                        icon = { Icon(dest.icon, contentDescription = null) },
                        label = { Text(stringResource(dest.labelRes)) },
                        selected = currentRoute == dest.route,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        },
    ) {
        if (isReaderRoute) {
            Box(Modifier.fillMaxSize()) { navHost(Modifier.fillMaxSize()) }
        } else {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(titleFor(currentRoute)) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = null)
                            }
                        },
                        actions = {
                            IconButton(onClick = { navController.navigate(Routes.SEARCH) }) {
                                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.nav_search))
                            }
                        },
                    )
                },
            ) { padding ->
                navHost(Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun titleFor(route: String?): String = when (route) {
    Routes.LIBRARY -> stringResource(R.string.nav_library)
    Routes.SETTINGS -> stringResource(R.string.settings_title)
    Routes.STATS -> stringResource(R.string.nav_stats)
    Routes.VOCABULARY -> stringResource(R.string.nav_vocabulary)
    Routes.AUTHORS -> stringResource(R.string.nav_authors)
    Routes.SERIES -> stringResource(R.string.nav_series)
    Routes.READING_NOOK -> stringResource(R.string.nav_reading_nook)
    Routes.SEARCH -> stringResource(R.string.nav_search)
    else -> stringResource(R.string.app_name)
}
