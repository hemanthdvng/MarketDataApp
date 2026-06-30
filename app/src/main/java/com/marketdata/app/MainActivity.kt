package com.marketdata.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.marketdata.app.ui.screens.*
import com.marketdata.app.ui.theme.*
import com.marketdata.app.viewmodel.*

sealed class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Auth : Screen("auth", "Login", Icons.Default.Key)
    object Download : Screen("download", "Download", Icons.Default.Download)
    object Live : Screen("live", "Live", Icons.Default.ShowChart)
    object Agent : Screen("agent", "Agent", Icons.Default.SmartToy)
    object Files : Screen("files", "Files", Icons.Default.Folder)
}

val bottomNavItems = listOf(Screen.Auth, Screen.Download, Screen.Live, Screen.Agent, Screen.Files)

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val downloadViewModel: DownloadViewModel by viewModels()
    private val liveQuotesViewModel: LiveQuotesViewModel by viewModels()
    private val agentViewModel: AgentViewModel by viewModels()
    private val filesViewModel: FilesViewModel by viewModels()

    private val kiteAuthLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val requestToken = result.data?.getStringExtra(KiteAuthActivity.RESULT_REQUEST_TOKEN)
            if (!requestToken.isNullOrEmpty()) {
                authViewModel.handleRequestToken(requestToken)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MarketDataAppTheme {
                MainScreen(
                    authViewModel = authViewModel,
                    downloadViewModel = downloadViewModel,
                    liveQuotesViewModel = liveQuotesViewModel,
                    agentViewModel = agentViewModel,
                    filesViewModel = filesViewModel,
                    onLoginClick = { startKiteLogin() }
                )
            }
        }
    }

    private fun startKiteLogin() {
        val apiKey = authViewModel.state.value.apiKey
        if (apiKey.isEmpty()) return
        val intent = Intent(this, KiteAuthActivity::class.java)
        intent.putExtra(KiteAuthActivity.EXTRA_API_KEY, apiKey)
        kiteAuthLauncher.launch(intent)
    }
}

@Composable
fun MainScreen(
    authViewModel: AuthViewModel,
    downloadViewModel: DownloadViewModel,
    liveQuotesViewModel: LiveQuotesViewModel,
    agentViewModel: AgentViewModel,
    filesViewModel: FilesViewModel,
    onLoginClick: () -> Unit
) {
    val navController = rememberNavController()

    Scaffold(
        containerColor = DarkBackground,
        bottomBar = {
            NavigationBar(containerColor = DarkSurface, contentColor = TextPrimary) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                bottomNavItems.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label, style = MaterialTheme.typography.bodySmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AccentBlue,
                            selectedTextColor = AccentBlue,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = DarkCard
                        )
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Auth.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Auth.route) {
                AuthScreen(viewModel = authViewModel, onLoginClick = onLoginClick)
            }
            composable(Screen.Download.route) {
                DownloadScreen(viewModel = downloadViewModel)
            }
            composable(Screen.Live.route) {
                LiveQuotesScreen(viewModel = liveQuotesViewModel)
            }
            composable(Screen.Agent.route) {
                AgentScreen(viewModel = agentViewModel)
            }
            composable(Screen.Files.route) {
                FilesScreen(viewModel = filesViewModel)
            }
        }
    }
}
