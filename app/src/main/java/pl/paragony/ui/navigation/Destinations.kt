package pl.paragony.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import pl.paragony.R

/** Navigation routes. Each screen has its own route. */
object Routes {
    const val START = "start"
    const val PURCHASES = "purchases"
    const val PRODUCTS = "products"
    const val GROUPS = "groups"
    const val SETTINGS = "settings"
    const val ADD = "add"

    const val ARG_PURCHASE_ID = "purchaseId"
    const val REVIEW = "review/{$ARG_PURCHASE_ID}"
    const val PURCHASE_DETAILS = "purchase/{$ARG_PURCHASE_ID}"

    fun review(purchaseId: String) = "review/$purchaseId"
    fun purchaseDetails(purchaseId: String) = "purchase/$purchaseId"
}

/** Tabs in the bottom bar. */
enum class TopLevelDestination(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    START(Routes.START, R.string.nav_start, Icons.Filled.Home),
    PURCHASES(Routes.PURCHASES, R.string.nav_purchases, Icons.AutoMirrored.Filled.List),
    PRODUCTS(Routes.PRODUCTS, R.string.nav_products, Icons.Filled.ShoppingCart),
    GROUPS(Routes.GROUPS, R.string.nav_groups, Icons.Filled.Person),
    SETTINGS(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
}
