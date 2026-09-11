package com.hostchecker.pro

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hostchecker.pro.ui.screens.ResultDetailScreen
import com.hostchecker.pro.ui.screens.ScanProgressScreen
import com.hostchecker.pro.ui.screens.ScanSetupScreen
import com.hostchecker.pro.ui.screens.SessionListScreen
import com.hostchecker.pro.ui.screens.SettingsScreen
import com.hostchecker.pro.ui.theme.Background
import com.hostchecker.pro.ui.theme.HostCheckerTheme
import com.hostchecker.pro.ui.viewmodel.DetailViewModel
import com.hostchecker.pro.ui.viewmodel.ScanViewModel
import com.hostchecker.pro.ui.viewmodel.SessionListViewModel
import com.hostchecker.pro.ui.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appModule = (application as HostCheckerApp).appModule

        setContent {
            HostCheckerTheme {
                // Request Notification permission for Android 13+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { _ -> }

                    LaunchedEffect(Unit) {
                        if (ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Background
                ) {
                    AppNavigation(appModule = appModule)
                }
            }
        }
    }
}

object Routes {
    const val SESSION_LIST = "sessions"
    const val SCAN_SETUP = "setup"
    const val SCAN_PROGRESS = "progress/{sessionId}"
    const val RESULT_DETAIL = "detail/{resultId}"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavigation(appModule: com.hostchecker.pro.di.AppModule) {
    val navController = rememberNavController()

    // Shared ScanViewModel so setup and progress screens coordinate effortlessly
    val scanViewModel: ScanViewModel = viewModel(
        factory = ScanViewModel.Factory(
            sessionRepository = appModule.sessionRepository,
            resultRepository = appModule.resultRepository,
            hostScanner = appModule.hostScanner,
            settingsDataStore = appModule.settingsDataStore,
            notificationHelper = appModule.notificationHelper
        )
    )

    // Shared SessionListViewModel
    val sessionListViewModel: SessionListViewModel = viewModel(
        factory = SessionListViewModel.Factory(
            sessionRepository = appModule.sessionRepository
        )
    )

    // Shared SettingsViewModel
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(
            settingsDataStore = appModule.settingsDataStore
        )
    )

    NavHost(
        navController = navController,
        startDestination = Routes.SESSION_LIST
    ) {
        composable(Routes.SESSION_LIST) {
            SessionListScreen(
                viewModel = sessionListViewModel,
                onNavigateToSetupWithFile = { fileUri ->
                    navController.navigate("${Routes.SCAN_SETUP}?fileUri=${Uri.encode(fileUri)}")
                },
                onNavigateToSetupWithText = { text ->
                    navController.navigate("${Routes.SCAN_SETUP}?rawText=${Uri.encode(text)}")
                },
                onOpenSession = { sessionId ->
                    navController.navigate("progress/$sessionId")
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        composable(
            route = "${Routes.SCAN_SETUP}?fileUri={fileUri}&rawText={rawText}",
            arguments = listOf(
                navArgument("fileUri") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("rawText") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val fileUri = backStackEntry.arguments?.getString("fileUri")
            val rawText = backStackEntry.arguments?.getString("rawText")

            ScanSetupScreen(
                fileUriString = fileUri,
                rawPastedText = rawText,
                sessionRepository = appModule.sessionRepository,
                settingsDataStore = appModule.settingsDataStore,
                onStartScan = { sessionId, hosts, config ->
                    scanViewModel.startOrResumeScan(
                        sessionId = sessionId,
                        hosts = hosts,
                        config = config,
                        startIndex = 0
                    )
                    navController.navigate("progress/$sessionId") {
                        popUpTo(Routes.SESSION_LIST)
                    }
                },
                onCancel = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Routes.SCAN_PROGRESS,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: 0L

            ScanProgressScreen(
                sessionId = sessionId,
                viewModel = scanViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onOpenResultDetail = { resultId ->
                    navController.navigate("detail/$resultId")
                }
            )
        }

        composable(
            route = Routes.RESULT_DETAIL,
            arguments = listOf(
                navArgument("resultId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val resultId = backStackEntry.arguments?.getLong("resultId") ?: 0L
            val detailViewModel: DetailViewModel = viewModel(
                factory = DetailViewModel.Factory(
                    resultRepository = appModule.resultRepository,
                    hostScanner = appModule.hostScanner
                )
            )

            ResultDetailScreen(
                resultId = resultId,
                viewModel = detailViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
