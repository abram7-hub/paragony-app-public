package pl.paragony.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import pl.paragony.ui.navigation.Routes
import pl.paragony.ui.navigation.TopLevelDestination
import pl.paragony.ui.screens.AddScreen
import pl.paragony.ui.screens.GroupsScreen
import pl.paragony.ui.screens.ProductsScreen
import pl.paragony.ui.screens.PurchaseDetailsScreen
import pl.paragony.ui.screens.PurchasesScreen
import pl.paragony.ui.screens.ReviewScreen
import pl.paragony.ui.screens.SettingsScreen
import pl.paragony.ui.screens.StartScreen

@Composable
fun ParagonyApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = TopLevelDestination.entries.any { it.route == currentDestination?.route }

    // Each screen has its own Scaffold with a top bar; this one only adds the bottom bar.
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { dest ->
                        val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTab(dest.route) },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(stringResource(dest.label)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.START,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable(Routes.START) {
                StartScreen(onAdd = { navController.navigate(Routes.ADD) })
            }
            composable(Routes.PURCHASES) {
                PurchasesScreen(onOpenPurchase = { id -> navController.navigate(Routes.purchaseDetails(id)) })
            }
            composable(Routes.PRODUCTS) { ProductsScreen() }
            composable(Routes.GROUPS) { GroupsScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(Routes.ADD) {
                AddScreen(
                    onBack = { navController.popBackStack() },
                    onOpenReview = { id -> navController.navigate(Routes.review(id)) },
                )
            }
            composable(
                Routes.REVIEW,
                arguments = listOf(navArgument(Routes.ARG_PURCHASE_ID) { type = NavType.StringType }),
            ) {
                ReviewScreen(onBack = { navController.popBackStack() })
            }
            composable(
                Routes.PURCHASE_DETAILS,
                arguments = listOf(navArgument(Routes.ARG_PURCHASE_ID) { type = NavType.StringType }),
            ) {
                PurchaseDetailsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/** Switch tabs keeping one copy of each tab and its state. */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
