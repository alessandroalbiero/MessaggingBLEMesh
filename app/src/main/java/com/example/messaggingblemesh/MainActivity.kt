package com.example.messaggingblemesh

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.messaggingblemesh.network_mesh.BleMeshService
import com.example.messaggingblemesh.screens.chat.SingleChatPage
import com.example.messaggingblemesh.screens.home.HomePage
import com.example.messaggingblemesh.screens.initialopening.FirstPageScreen

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPrefs = getSharedPreferences("MeshPrefs", MODE_PRIVATE)
        val isFirstTime = !sharedPrefs.getBoolean("isCreated", false)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()

                    val startDestination = if (isFirstTime) "credentials" else "home"

                    NavHost(navController = navController, startDestination = startDestination) {
                        composable("credentials") {
                            FirstPageScreen(
                                onNavigateToHome = {
                                    startMeshService()
                                    navController.navigate("home")
                                }
                            )
                        }
                        composable("home") {
                            startMeshService()
                            HomePage(
                                onNavigateToChat = { contactId ->
                                    navController.navigate("chat/${contactId}")
                                }
                            )
                        }
                        composable("chat/{contactId}") { backStackEntry ->
                            val contactId = backStackEntry.arguments?.getString("contactId") ?: return@composable
                            SingleChatPage(
                                contactId = contactId,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startMeshService() {
        val serviceIntent = Intent(this, BleMeshService::class.java)
        androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)
    }
}