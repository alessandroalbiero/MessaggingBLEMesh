package com.example.messaggingblemesh

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.messaggingblemesh.network_mesh.BleMeshService
import com.example.messaggingblemesh.screens.chat.SingleChatPage
import com.example.messaggingblemesh.screens.home.HomePage
import com.example.messaggingblemesh.screens.initialopening.FirstPageScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPrefs = getSharedPreferences("MeshPrefs", Context.MODE_PRIVATE)
        val isFirstTime = !sharedPrefs.getBoolean("isCreated", false)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {

                    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        arrayOf(
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_ADVERTISE,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.CAMERA
                        )
                    } else {
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.CAMERA
                        )
                    }

                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { permissionsMap ->
                        val allGranted = permissionsMap.values.all { it }
                        if (allGranted && !isFirstTime) {
                            startMeshService()
                        }
                    }

                    LaunchedEffect(Unit) {
                        permissionLauncher.launch(permissionsToRequest)
                    }

                    val navController = rememberNavController()
                    val startDestination = if (isFirstTime) "credentials" else "home"

                    NavHost(navController = navController, startDestination = startDestination) {
                        composable("credentials") {
                            FirstPageScreen(
                                onNavigateToHome = {
                                    startMeshService()
                                    navController.navigate("home") {
                                        popUpTo("credentials") { inclusive = true }
                                    }
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
        val hasBluetoothPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        val hasCameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

        if (hasBluetoothPermission && hasCameraPermission) {
            val serviceIntent = Intent(this, BleMeshService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
        }
    }
}