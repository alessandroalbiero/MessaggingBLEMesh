package com.example.messaggingblemesh.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.messaggingblemesh.data.local.entities.Contact
import com.example.messaggingblemesh.qrcode.QrCodeBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePage(
    homePageViewmodel: HomePageViewmodel = viewModel(),
    onNavigateToChat: (String) -> Unit
){
    var showPersonalDataSheet by remember { mutableStateOf(false) }
    val contactList by homePageViewmodel.contactsListFlow.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Le tue chat", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showPersonalDataSheet = true },
                containerColor = MaterialTheme.colorScheme.secondary,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = "Aggiungi Contatto",
                    tint = Color.White
                )
            }
        }
    ) { paddingValues ->

        if(contactList.isEmpty()){
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ){
                Text(
                    text = "Nessun contatto salvato. \n Premi il tasto + per scansionare un QR code",
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }else{
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                items(contactList) {contact ->
                    ContactSingleRow(
                        contact = contact,
                        onClick = { onNavigateToChat(contact.contactId)}
                    )
                }
            }
        }
    }

    if(showPersonalDataSheet) {
        val dataJson = homePageViewmodel.getDataQrJson()

        QrCodeBottomSheet(
            dataJson,
            onDismiss = {showPersonalDataSheet = false},
            onScanned = { scannedJson ->
                homePageViewmodel.scannedQrInsertContact(scannedJson)
            }
        )
    }
}

@Composable
fun ContactSingleRow(contact: Contact, onClick: () -> Unit){
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.username.take(1).uppercase(),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = contact.username,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Inizia a chattare",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}