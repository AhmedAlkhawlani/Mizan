package com.maknoon.mizan.app

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.maknoon.mizan.R
import com.maknoon.mizan.core.designsystem.theme.MizanTheme
import com.maknoon.mizan.core.designsystem.theme.statusColors
import com.maknoon.mizan.ui.apps.AppsScreen
import com.maknoon.mizan.ui.dashboard.DashboardScreen
import com.maknoon.mizan.ui.history.HistoryScreen

/**
 * The four top-level destinations of the shell in docs/04.
 *
 * [bodyRes] describes a section that still shows a placeholder; the dashboard, apps and
 * history screens are built, so only the saver has one.
 */
enum class MizanDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
    @param:StringRes val bodyRes: Int? = null,
) {
    Dashboard("dashboard", R.string.nav_dashboard, R.drawable.ic_dashboard),
    Apps("apps", R.string.nav_apps, R.drawable.ic_apps),
    Saver("saver", R.string.nav_saver, R.drawable.ic_shield, R.string.saver_body),
    History("history", R.string.nav_history, R.drawable.ic_history),
}

/**
 * The application shell: a bottom navigation bar over the four sections.
 *
 * Sections are placeholders on purpose. Phase 0 ships the structure and the Arabic, RTL frame;
 * each section is filled in by the phase that owns its feature, so no screen shows a number it
 * cannot actually measure yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MizanApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text(text = stringResource(R.string.app_name)) })
        },
        bottomBar = {
            NavigationBar {
                MizanDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
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
            composable(MizanDestination.Apps.route) { AppsScreen() }
            composable(MizanDestination.History.route) { HistoryScreen() }
            MizanDestination.entries
                .filter { it.bodyRes != null }
                .forEach { destination ->
                    composable(destination.route) {
                        val bodyRes = destination.bodyRes ?: return@composable
                        SectionPlaceholder(destination, bodyRes)
                    }
                }
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

@Composable
private fun SectionPlaceholder(
    destination: MizanDestination,
    @StringRes bodyRes: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(destination.iconRes),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(destination.labelRes),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.section_in_progress),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.statusColors.guard,
        )
    }
}

@Preview(name = "Shell — Arabic RTL", locale = "ar", showBackground = true)
@Composable
private fun MizanAppPreview() {
    MizanTheme(dynamicColor = false) {
        MizanApp()
    }
}
