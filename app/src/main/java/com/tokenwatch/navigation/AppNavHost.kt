package com.tokenwatch.navigation

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import com.tokenwatch.core.ui.components.LocalBottomBarPadding
import com.tokenwatch.domain.model.AiProvider
import com.tokenwatch.feature.connections.ConnectionsScreen
import com.tokenwatch.feature.dashboard.DashboardScreen
import com.tokenwatch.feature.providerdetail.ProviderDetailScreen
import com.tokenwatch.feature.providerdetail.ProviderDetailViewModel
import com.tokenwatch.feature.settings.SettingsScreen
import com.tokenwatch.feature.websession.UsageRefreshHost
import com.tokenwatch.feature.websession.WebSessionScreen
import com.tokenwatch.feature.websession.WebSessionViewModel

enum class TopLevelDestination(val route: String, val label: String, val icon: ImageVector) {
    DASHBOARD("dashboard", "Usage", Icons.Default.Home),
    CONNECTIONS("connections", "Accounts", Icons.Default.AccountCircle),
    SETTINGS("settings", "Settings", Icons.Default.Settings),
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

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = TopLevelDestination.entries.any { it.route == currentRoute }
    val hazeState = remember { HazeState() }
    val glassShape = RoundedCornerShape(28.dp)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                // Frosted glass: blurs the screen scrolling underneath (API 31+; older phones get a translucent tint).
                Surface(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(vertical = 12.dp)
                        .fillMaxWidth()
                        .wrapContentWidth()
                        // Fixed width, centered: about 72dp per icon plus inner padding, enough for the 64dp selection pill.
                        .width(236.dp)
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.08f))),
                            shape = glassShape,
                        ),
                    shape = glassShape,
                    color = Color.Transparent,
                ) {
                    NavigationBar(
                        // Icon-only and shorter than the default 80dp bar.
                        modifier = Modifier.height(60.dp).hazeChild(hazeState, HazeMaterials.ultraThin()).padding(horizontal = 10.dp),
                        containerColor = Color.Transparent,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                    ) {
                        TopLevelDestination.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = currentRoute == destination.route,
                                onClick = { navController.navigateTopLevel(destination) },
                                icon = { Icon(destination.icon, contentDescription = destination.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        // Drawn first so the screens cover its invisible web view.
        UsageRefreshHost()
        // No bottom padding: screens scroll under the glass bar and pad their lists with LocalBottomBarPadding.
        CompositionLocalProvider(LocalBottomBarPadding provides padding.calculateBottomPadding()) {
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.DASHBOARD.route,
            modifier = Modifier.padding(top = padding.calculateTopPadding()).haze(hazeState),
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
