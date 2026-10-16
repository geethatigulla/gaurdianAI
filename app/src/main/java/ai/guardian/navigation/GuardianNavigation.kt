package ai.guardian.navigation

import ai.guardian.feature.about.AboutScreen
import ai.guardian.feature.assistant.HelpScreen
import ai.guardian.feature.history.ActivityScreen
import ai.guardian.feature.home.HomeScreen
import ai.guardian.feature.qr.QrScanScreen
import ai.guardian.feature.result.ResultScreen
import ai.guardian.feature.scan.AnalyzeInputScreen
import ai.guardian.feature.scan.InputMode
import ai.guardian.feature.settings.SettingsScreen
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

object Routes {
    const val HOME = "home"
    const val ACTIVITY = "activity"
    const val SETTINGS = "settings"
    const val HELP = "help"
    const val ABOUT = "about"
    const val QR = "qr"

    const val ARG_MODE = "mode"
    const val ARG_ID = "id"
    const val ANALYZE = "analyze/{$ARG_MODE}"
    const val RESULT = "result/{$ARG_ID}"

    fun analyze(mode: InputMode) = "analyze/${mode.name}"
    fun result(id: String) = "result/$id"
}

enum class TopLevelDestination(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME(Routes.HOME, "Home", Icons.Outlined.Home, Icons.Filled.Home),
    ACTIVITY(Routes.ACTIVITY, "History", Icons.Outlined.History, Icons.Filled.History),
    SETTINGS(Routes.SETTINGS, "Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
}

@Composable
fun GuardianRoot(
    navController: NavHostController = rememberNavController(),
    incoming: IncomingViewModel = hiltViewModel(),
) {
    val incomingState by incoming.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        incoming.nav.collect { target ->
            when (target) {
                is IncomingNav.Result -> navController.navigate(Routes.result(target.id)) { launchSingleTop = true }
                is IncomingNav.Input -> navController.navigate(Routes.analyze(target.mode)) { launchSingleTop = true }
                IncomingNav.QrScanner -> navController.navigate(Routes.QR) { launchSingleTop = true }
            }
        }
    }
    if (incomingState.checking) {
        Dialog(onDismissRequest = {}) {
            Surface(shape = MaterialTheme.shapes.large) {
                Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text("Checking…", Modifier.padding(start = 16.dp))
                }
            }
        }
    }
    incomingState.error?.let { message ->
        AlertDialog(
            onDismissRequest = incoming::dismissError,
            text = { Text(message) },
            confirmButton = { TextButton(onClick = incoming::dismissError) { Text("OK") } },
        )
    }

    val backStack by navController.currentBackStackEntryAsState()
    val topLevel = TopLevelDestination.entries.firstOrNull { it.route == backStack?.destination?.route }

    Scaffold(
        // Each screen draws its own top bar and handles status-bar insets.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (topLevel != null) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { dest ->
                        val selected = dest == topLevel
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTopLevel(dest.route) },
                            icon = { Icon(if (selected) dest.selectedIcon else dest.icon, contentDescription = null) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onStartScan = { navController.navigate(Routes.analyze(it)) },
                    onScanQr = { navController.navigate(Routes.QR) },
                    onOpenResult = { navController.navigate(Routes.result(it)) },
                    onSeeAllActivity = { navController.navigateToTopLevel(Routes.ACTIVITY) },
                    onOpenSettings = { navController.navigateToTopLevel(Routes.SETTINGS) },
                    onOpenHelp = { navController.navigate(Routes.HELP) },
                )
            }
            composable(Routes.ACTIVITY) {
                ActivityScreen(onOpenResult = { navController.navigate(Routes.result(it)) })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                    onOpenHelp = { navController.navigate(Routes.HELP) },
                )
            }
            composable(Routes.HELP) {
                HelpScreen(onBack = { navController.popBackStack() }, onOpenResult = { navController.navigate(Routes.result(it)) })
            }
            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.QR) {
                QrScanScreen(
                    onBack = { navController.popBackStack() },
                    // Drop the camera from the back stack so "back" doesn't instantly re-scan the same code.
                    onResult = { id -> navController.navigate(Routes.result(id)) { popUpTo(Routes.QR) { inclusive = true } } },
                )
            }
            composable(Routes.ANALYZE, arguments = listOf(navArgument(Routes.ARG_MODE) { type = NavType.StringType })) {
                AnalyzeInputScreen(
                    onBack = { navController.popBackStack() },
                    onResult = { id -> navController.navigate(Routes.result(id)) },
                )
            }
            composable(Routes.RESULT, arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.StringType })) {
                ResultScreen(
                    onBack = { navController.popBackStack() },
                    onScanAnother = { navController.navigateToTopLevel(Routes.HOME) },
                )
            }
        }
    }
}

private fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
