package com.example.jobsearch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.jobsearch.data.SyncService
import com.example.jobsearch.ui.addjob.AddJobScreen
import com.example.jobsearch.ui.documented.DocumentViewScreen
import com.example.jobsearch.ui.jobdetail.JobDetailScreen
import com.example.jobsearch.ui.joblist.JobListScreen
import com.example.jobsearch.ui.settings.SettingsScreen
import com.example.jobsearch.ui.settings.SettingsSection
import com.example.jobsearch.ui.theme.ExampleJobSearchTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main entry point for the application's UI.
 * Sets up the theme and the navigation host.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var sharedUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SyncService.start(this)
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            ExampleJobSearchTheme {
                JobSearchNavHost(sharedUrl) { 
                    sharedUrl = null 
                    intent?.action = null
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        try {
            if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                    sharedUrl = it
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to handle intent", e)
        }
    }
}

/**
 * Navigation host defining all screens and their routes.
 */
@Composable
private fun JobSearchNavHost(
    sharedUrl: String? = null,
    onUrlHandled: () -> Unit = {}
) {
    val navController = rememberNavController()

    LaunchedEffect(sharedUrl) {
        sharedUrl?.let { raw ->
            val extracted = extractUrl(raw)
            navController.navigate("add?url=${Uri.encode(extracted)}") {
                launchSingleTop = true
            }
            onUrlHandled()
        }
    }

    NavHost(navController = navController, startDestination = "jobs") {
        composable("jobs") {
            JobListScreen(
                onAddJob = { navController.navigate("add") },
                onOpenJob = { id -> navController.navigate("job/$id") },
                onOpenSettings = { section -> navController.navigate("settings?section=$section") },
                onAddJobWithId = { id -> navController.navigate("add?jobId=$id") }
            )
        }
        composable(
            route = "add?url={url}&jobId={jobId}",
            arguments = listOf(
                navArgument("url") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("jobId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url")
            val jobId = backStackEntry.arguments?.getLong("jobId") ?: -1L
            AddJobScreen(
                sharedUrl = url,
                jobId = if (jobId == -1L) null else jobId,
                onBack = { navController.popBackStack() },
                onJobSaved = { id, generate ->
                    val query = if (generate.isNullOrBlank()) "" else "?generate=$generate"
                    navController.navigate("job/$id$query") {
                        popUpTo("jobs") { inclusive = false }
                    }
                }
            )
        }
        composable(
            route = "job/{jobId}?generate={generate}",
            arguments = listOf(
                navArgument("jobId") { type = NavType.LongType },
                navArgument("generate") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val generate = backStackEntry.arguments?.getString("generate") ?: ""
            JobDetailScreen(
                generate = generate,
                onBack = { navController.popBackStack() },
                onViewDocument = { jobId, type, edit ->
                    val query = if (edit) "?edit=true" else ""
                    navController.navigate("job/$jobId/view/$type$query")
                },
                onEditJob = { id ->
                    navController.navigate("add?jobId=$id")
                }
            )
        }
        composable(
            route = "job/{jobId}/view/{type}?edit={edit}",
            arguments = listOf(
                navArgument("jobId") { type = NavType.LongType },
                navArgument("type") { type = NavType.StringType },
                navArgument("edit") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val edit = backStackEntry.arguments?.getBoolean("edit") ?: false
            DocumentViewScreen(
                onBack = { navController.popBackStack() },
                initialEdit = edit
            )
        }
        composable(
            route = "settings?section={section}",
            arguments = listOf(
                navArgument("section") {
                    type = NavType.StringType
                    defaultValue = "RESUME"
                }
            )
        ) { backStackEntry ->
            val sectionName = backStackEntry.arguments?.getString("section") ?: "RESUME"
            val initialSection = runCatching {
                SettingsSection.valueOf(sectionName)
            }.getOrDefault(SettingsSection.RESUME)

            SettingsScreen(
                initialSection = initialSection,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

private fun extractUrl(text: String): String {
    // Find first HTTP/S link, stopping at whitespace, quotes, or angle brackets
    val regex = Regex("https?://[^\\s\"<>]+")
    val match = regex.find(text)
    return match?.value ?: text.trim()
}
