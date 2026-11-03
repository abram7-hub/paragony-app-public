package pl.paragony.ui.screens

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pl.paragony.R
import pl.paragony.ui.theme.ParagonyTheme

/** Id used by the preview buttons until real purchases exist (S3+). */
private const val DEMO_ID = "demo"

@Composable
fun StartScreen(onAdd: () -> Unit) = PlaceholderScreen(
    title = R.string.nav_start,
    planned = R.array.planned_start,
    session = "S6",
    floatingAction = {
        ExtendedFloatingActionButton(
            onClick = onAdd,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.action_add)) },
        )
    },
)

@Composable
fun AddScreen(onBack: () -> Unit, onOpenReview: (String) -> Unit) = PlaceholderScreen(
    title = R.string.screen_add,
    planned = R.array.planned_add,
    session = "S4",
    onBack = onBack,
) {
    OutlinedButton(onClick = { onOpenReview(DEMO_ID) }) { Text(stringResource(R.string.demo_open_review)) }
}

@Composable
fun ReviewScreen(onBack: () -> Unit) = PlaceholderScreen(
    title = R.string.screen_review,
    planned = R.array.planned_review,
    session = "S5",
    onBack = onBack,
)

@Composable
fun PurchasesScreen(onOpenPurchase: (String) -> Unit) = PlaceholderScreen(
    title = R.string.nav_purchases,
    planned = R.array.planned_purchases,
    session = "S6",
) {
    OutlinedButton(onClick = { onOpenPurchase(DEMO_ID) }) { Text(stringResource(R.string.demo_open_details)) }
}

@Composable
fun PurchaseDetailsScreen(onBack: () -> Unit) = PlaceholderScreen(
    title = R.string.screen_purchase_details,
    planned = R.array.planned_purchase_details,
    session = "S6",
    onBack = onBack,
)

@Composable
fun ProductsScreen() = PlaceholderScreen(R.string.nav_products, R.array.planned_products, session = "S8")

@Composable
fun GroupsScreen() = PlaceholderScreen(R.string.nav_groups, R.array.planned_groups, session = "S6")

@Composable
fun SettingsScreen() = PlaceholderScreen(R.string.nav_settings, R.array.planned_settings, session = "S4")

/** Empty screen for S2: title, what will be here, and in which session. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceholderScreen(
    @StringRes title: Int,
    @ArrayRes planned: Int,
    session: String,
    onBack: (() -> Unit)? = null,
    floatingAction: @Composable () -> Unit = {},
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
            )
        },
        floatingActionButton = floatingAction,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.placeholder_planned), style = MaterialTheme.typography.titleMedium)
            stringArrayResource(planned).forEach { Text("•  $it", style = MaterialTheme.typography.bodyLarge) }
            Text(
                stringResource(R.string.placeholder_session, session),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            extra()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StartScreenPreview() {
    ParagonyTheme(dynamicColor = false) { StartScreen(onAdd = {}) }
}
