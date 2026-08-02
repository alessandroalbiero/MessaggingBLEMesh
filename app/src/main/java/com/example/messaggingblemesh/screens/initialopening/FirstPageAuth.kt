package com.example.messaggingblemesh.screens.initialopening

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstPageScreen(
    viewModel: PersonalDataViewModel = viewModel(),
    onNavigateToHome: () -> Unit
){
    val username by viewModel.username.collectAsState()
    val name by viewModel.name.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Inserisci il tuo Username"
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { viewModel.setCredentials(it, name) },
            label = { Text("Username") }
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { viewModel.setCredentials(username, it) },
            label = { Text("Name") }
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { viewModel.generateOwnProfile(onSuccess = onNavigateToHome) },
            enabled = username.isNotBlank() && !isSaving
        ) {
            if (isSaving) {
                CircularProgressIndicator()
            } else {
                Text("Entra nella Rete")
            }
        }
    }
}