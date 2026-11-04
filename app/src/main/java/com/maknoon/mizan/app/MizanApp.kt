package com.maknoon.mizan.app

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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

/** The budget and cycle editor, reached from the dashboard's budget action (docs/04). */
internal const val BUDGET_ROUTE = "budget"

private fun appDetailRoute(packageName: String): String =
    "apps/detail/${Uri.encode(packageName)}"

/**
 * The application shell: a bottom navigation bar over the four sections.
 *
 * There is deliberately no shared top bar. Each screen draws its own header through
 * `AppTopBar`, so its title and controls sit on the same surface as its content, the way the
 * concept board frames every phone.
 */
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
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                MizanDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route ||
                            (destination == MizanDestination.Apps &&
                                currentRoute == APP_DETAIL_ROUTE),
                        onClick = { navController.navigateToTab(destination) },
                        icon = {
                            Icon(
                                painter = painterResource(destination.iconRes),
                                contentDescription = null,
                            )
                        },
                        label = { Text(text = stringResource(destination.labelRes)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
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
            composable(MizanDestination.Dashboard.route) {
                DashboardScreen(
                    onOpenBudget = {
                        navController.navigate(BUDGET_ROUTE) { launchSingleTop = true }
                    },
                )
            }
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
            composable(BUDGET_ROUTE) { BudgetScreen(onBack = { navController.popBackStack() }) }
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
    MizanTheme(darkTheme = true, dynamicColor = false) {
        MizanApp()
    }
}
