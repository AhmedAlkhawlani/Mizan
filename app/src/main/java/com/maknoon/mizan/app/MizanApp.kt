package com.maknoon.mizan.app

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.maknoon.mizan.R
import com.maknoon.mizan.core.designsystem.theme.MizanTheme
import com.maknoon.mizan.ui.apps.AppDetailScreen
import com.maknoon.mizan.ui.apps.AppsScreen
import com.maknoon.mizan.ui.budget.BudgetScreen
import com.maknoon.mizan.ui.dashboard.DashboardScreen
import com.maknoon.mizan.ui.history.HistoryScreen
import com.maknoon.mizan.ui.saver.SaverScreen

/**
 * The four top-level destinations of the shell in docs/04.
 */
enum class MizanDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    Dashboard("dashboard", R.string.nav_dashboard, R.drawable.ic_dashboard),
    Apps("apps", R.string.nav_apps, R.drawable.ic_apps),
    Saver("saver", R.string.nav_saver, R.drawable.ic_shield),
    History("history", R.string.nav_history, R.drawable.ic_history),
}

/** Per-app detail, reached from a row in the Apps list. */
private const val APP_DETAIL_ROUTE = "apps/detail/{packageName}"

/** The budget and cycle editor, reached from the top bar's settings action (docs/04). */
internal const val BUDGET_ROUTE = "budget"

private fun appDetailRoute(packageName: String): String =
    "apps/detail/${Uri.encode(packageName)}"

/**
 * The application shell: a bottom navigation bar over the four sections.
 *
 * Sections are placeholders on purpose. Phase 0 ships the structure and the Arabic, RTL frame;
 * each section is filled in by the phase that owns its feature, so no screen shows a number it
 * cannot actually measure yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MizanApp(
    modifier: Modifier = Modifier,
    shortcutDestination: String? = null,
    onShortcutDestinationHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // A shortcut arrives before the shell exists; navigate once the graph is in place, then
    // clear the request so a recomposition does not jump again.
    LaunchedEffect(shortcutDestination) {
        val route = shortcutDestination ?: return@LaunchedEffect
        val tab = MizanDestination.entries.firstOrNull { it.route == route }
        if (tab != null) {
            navController.navigateToTab(tab)
        } else if (route == BUDGET_ROUTE) {
            navController.navigate(BUDGET_ROUTE) { launchSingleTop = true }
        }
        onShortcutDestinationHandled()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.app_name)) },
                navigationIcon = {
                    if (currentRoute == BUDGET_ROUTE) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.budget_back),
                            )
                        }
                    }
                },
                actions = {
                    if (currentRoute != BUDGET_ROUTE) {
                        IconButton(
                            onClick = {
                                navController.navigate(BUDGET_ROUTE) { launchSingleTop = true }
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_settings),
                                contentDescription = stringResource(R.string.settings_open),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                MizanDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route ||
                            (destination == MizanDestination.Apps && currentRoute == APP_DETAIL_ROUTE),
                        onClick = { navController.navigateToTab(destination) },
                        icon = {
                            Icon(
                                painter = painterResource(destination.iconRes),
                                contentDescription = null,
                            )
                        },
                        label = { Text(text = stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MizanDestination.Dashboard.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(MizanDestination.Dashboard.route) { DashboardScreen() }
            composable(MizanDestination.Apps.route) {
                AppsScreen(onAppClick = { packageName ->
                    navController.navigate(appDetailRoute(packageName))
                })
            }
            composable(
                route = APP_DETAIL_ROUTE,
                arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
            ) { entry ->
                AppDetailScreen(
                    packageName = entry.arguments?.getString("packageName").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(MizanDestination.History.route) { HistoryScreen() }
            composable(MizanDestination.Saver.route) { SaverScreen() }
            composable(BUDGET_ROUTE) { BudgetScreen() }
        }
    }
}

/**
 * Switches tabs without stacking duplicates and without losing each tab's state, which keeps
 * the back button behaving the way users expect from a bottom bar.
 */
private fun NavHostController.navigateToTab(destination: MizanDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Preview(name = "Shell — Arabic RTL", locale = "ar", showBackground = true)
@Composable
private fun MizanAppPreview() {
    MizanTheme(dynamicColor = false) {
        MizanApp()
    }
}
