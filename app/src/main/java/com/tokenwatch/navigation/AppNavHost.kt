package com.tokenwatch.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tokenwatch.core.ui.components.Glyph
import com.tokenwatch.core.ui.components.GlyphIcon
import com.tokenwatch.core.ui.components.LocalBottomBarPadding
import com.tokenwatch.core.ui.components.Rule
import com.tokenwatch.core.ui.components.SandBench
import com.tokenwatch.core.ui.components.rememberHaptics
import com.tokenwatch.domain.model.AiProvider
import com.tokenwatch.feature.connections.ConnectionsScreen
import com.tokenwatch.feature.dashboard.DashboardScreen
import com.tokenwatch.feature.providerdetail.ProviderDetailScreen
import com.tokenwatch.feature.providerdetail.ProviderDetailViewModel
import com.tokenwatch.feature.settings.SettingsScreen
import com.tokenwatch.feature.websession.UsageRefreshHost
import com.tokenwatch.feature.websession.WebSessionScreen
import com.tokenwatch.feature.websession.WebSessionViewModel

enum class TopLevelDestination(val route: String, val label: String, val glyph: Glyph) {
    DASHBOARD("dashboard", "Usage", Glyph.USAGE),
    CONNECTIONS("connections", "Accounts", Glyph.ACCOUNTS),
    SETTINGS("settings", "Settings", Glyph.SETTINGS),
}

private const val PROVIDER_ROUTE = "provider/{${ProviderDetailViewModel.ARG_PROVIDER}}"
private const val WEB_SESSION_ROUTE = "web-session/{${WebSessionViewModel.ARG_PROVIDER}}"

private fun providerRoute(provider: AiProvider) = "provider/${provider.name}"
private fun webSessionRoute(provider: AiProvider) = "web-session/${provider.name}"

private fun NavHostController.navigateTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = TopLevelDestination.entries.any { it.route == currentRoute }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                BenchBar(currentRoute) { navController.navigateTopLevel(it) }
            }
        },
    ) { padding ->
        SandBench {
        // Drawn first so the screens cover its invisible web view.
        UsageRefreshHost()
        // Screens pad their scrolling content with LocalBottomBarPadding so it ends above the bar.
        CompositionLocalProvider(LocalBottomBarPadding provides padding.calculateBottomPadding()) {
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.DASHBOARD.route,
            // Screens slide a little and settle on a spring; Back reverses it.
            enterTransition = { slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it / 6 } + fadeIn(tween(180)) },
            exitTransition = { slideOutHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { -it / 10 } + fadeOut(tween(140)) },
            popEnterTransition = { slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { -it / 6 } + fadeIn(tween(180)) },
            popExitTransition = { slideOutHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { it / 10 } + fadeOut(tween(140)) },
            modifier = Modifier.padding(top = padding.calculateTopPadding()),
        ) {
            composable(TopLevelDestination.DASHBOARD.route) {
                DashboardScreen(
                    onProviderClick = { navController.navigate(providerRoute(it)) },
                    onOpenConnections = { navController.navigateTopLevel(TopLevelDestination.CONNECTIONS) },
                    onSignIn = { navController.navigate(webSessionRoute(it)) },
                )
            }
            composable(TopLevelDestination.CONNECTIONS.route) {
                ConnectionsScreen(
                    onManage = { navController.navigate(providerRoute(it)) },
                    onWebSession = { navController.navigate(webSessionRoute(it)) },
                )
            }
            composable(TopLevelDestination.SETTINGS.route) { SettingsScreen() }
            composable(
                route = PROVIDER_ROUTE,
                arguments = listOf(navArgument(ProviderDetailViewModel.ARG_PROVIDER) { type = NavType.StringType }),
            ) {
                ProviderDetailScreen(
                    onBack = { navController.popBackStack() },
                    onWebSession = { navController.navigate(webSessionRoute(it)) },
                )
            }
            composable(
                route = WEB_SESSION_ROUTE,
                arguments = listOf(navArgument(WebSessionViewModel.ARG_PROVIDER) { type = NavType.StringType }),
            ) {
                WebSessionScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
        }
        }
    }
    }
}

/**
 * The app's own tab bar: three glyph tabs on the bench, an ink rule that springs to the chosen tab,
 * and a haptic tick on each switch.
 */
@Composable
private fun BenchBar(currentRoute: String?, onSelect: (TopLevelDestination) -> Unit) {
    val haptics = rememberHaptics()
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val index = TopLevelDestination.entries.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    Column(Modifier.background(MaterialTheme.colorScheme.background)) {
        Rule()
        BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding().height(68.dp)) {
            val tab = maxWidth / TopLevelDestination.entries.size
            val ruleX by animateDpAsState(
                targetValue = tab * index + (tab - 28.dp) / 2,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
                label = "bench-rule",
            )
            Box(Modifier.offset(x = ruleX).width(28.dp).height(3.dp).background(ink, RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp)))
            Row(Modifier.fillMaxWidth().fillMaxHeight().selectableGroup()) {
                TopLevelDestination.entries.forEachIndexed { i, destination ->
                    val selected = i == index
                    val lift by animateDpAsState(
                        targetValue = if (selected) (-2).dp else 0.dp,
                        animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium),
                        label = "lift",
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(selected = selected, role = Role.Tab) {
                                if (!selected) haptics.tick()
                                onSelect(destination)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        GlyphIcon(destination.glyph, Modifier.offset(y = lift), tint = if (selected) ink else muted)
                        Text(
                            destination.label,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                            color = if (selected) ink else muted,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
