package com.example.regresoacasa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.regresoacasa.data.safety.EmergencyContact
import com.example.regresoacasa.di.AppModule
import com.example.regresoacasa.ui.viewmodel.NavigationViewModel
import kotlinx.coroutines.launch

/**
 * EmergencyContactsScreen - Gestión de contactos de emergencia
 * Permite agregar, editar, eliminar y marcar como principal
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyContactsScreen(
    viewModel: NavigationViewModel,
    onBack: () -> Unit
) {
    var contacts by remember { mutableStateOf<List<EmergencyContact>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<EmergencyContact?>(null) }
    var showDeleteDialog by remember { mutableStateOf<EmergencyContact?>(null) }

    // Load contacts from database
    androidx.lifecycle.compose.LaunchedEffect(Unit) {
        viewModel.viewModelScope.launch {
            try {
                val contactEntities = viewModel.appModule.database.emergencyContactDao().getAllContacts().collect { entities ->
                    contacts = entities.map { entity ->
                        EmergencyContact(
                            id = entity.id,
                            name = entity.name,
                            phoneNumber = entity.phoneNumber,
                            relationship = entity.relationship,
                            isPrimary = entity.isPrimary
                        )
                    }
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Contactos de Emergencia",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar contacto")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (contacts.isEmpty()) {
                EmptyState()
            } else {
                ContactsList(
                    contacts = contacts,
                    onEditClick = { editingContact = it },
                    onDeleteClick = { showDeleteDialog = it },
                    onSetPrimary = { contact ->
                        viewModel.viewModelScope.launch {
                            try {
                                viewModel.appModule.database.emergencyContactDao().clearPrimaryContact()
                                viewModel.appModule.database.emergencyContactDao().setPrimaryContact(contact.id)
                            } catch (e: Exception) {
                                // Handle error
                            }
                        }
                    }
                )
            }
        }
    }

    // Add/Edit Dialog
    if (showAddDialog || editingContact != null) {
        AddEditContactDialog(
            contact = editingContact,
            onDismiss = {
                showAddDialog = false
                editingContact = null
            },
            onSave = { name, phone, relationship ->
                viewModel.viewModelScope.launch {
                    try {
                        val entity = com.example.regresoacasa.data.local.entity.EmergencyContactEntity(
                            id = editingContact?.id ?: 0,
                            name = name,
                            phoneNumber = phone,
                            relationship = relationship,
                            isPrimary = editingContact?.isPrimary ?: false
                        )
                        if (editingContact == null) {
                            viewModel.appModule.database.emergencyContactDao().insertContact(entity)
                        } else {
                            viewModel.appModule.database.emergencyContactDao().updateContact(entity)
                        }
                    } catch (e: Exception) {
                        // Handle error
                    }
                }
                showAddDialog = false
                editingContact = null
            }
        )
    }

    // Delete Confirmation Dialog
    showDeleteDialog?.let { contact ->
        DeleteContactDialog(
            contact = contact,
            onDismiss = { showDeleteDialog = null },
            onConfirm = {
                viewModel.viewModelScope.launch {
                    try {
                        viewModel.appModule.database.emergencyContactDao().deleteContactById(contact.id)
                    } catch (e: Exception) {
                        // Handle error
                    }
                }
                showDeleteDialog = null
            }
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Sin contactos",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Agrega contactos de confianza para recibir alertas de emergencia",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun ContactsList(
    contacts: List<EmergencyContact>,
    onEditClick: (EmergencyContact) -> Unit,
    onDeleteClick: (EmergencyContact) -> Unit,
    onSetPrimary: (EmergencyContact) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(contacts) { contact ->
            ContactCard(
                contact = contact,
                onEditClick = { onEditClick(contact) },
                onDeleteClick = { onDeleteClick(contact) },
                onSetPrimary = { onSetPrimary(contact) }
            )
        }
    }
}

@Composable
private fun ContactCard(
    contact: EmergencyContact,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSetPrimary: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (contact.isPrimary) {
                Color(0xFFE8F5E9)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = contact.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (contact.isPrimary) {
                        Spacer(modifier = Modifier.size(8.dp))
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Principal",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = contact.phoneNumber,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (contact.relationship.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = contact.relationship,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            
            Row {
                IconButton(onClick = onSetPrimary) {
                    Icon(
                        imageVector = if (contact.isPrimary) Icons.Default.Star else Icons.Outlined.Star,
                        contentDescription = "Marcar como principal",
                        tint = if (contact.isPrimary) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEditClick) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = Color(0xFFD32F2F)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddEditContactDialog(
    contact: EmergencyContact?,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(contact?.name ?: "") }
    var phone by remember { mutableStateOf(contact?.phoneNumber ?: "") }
    var relationship by remember { mutableStateOf(contact?.relationship ?: "") }
    var nameError by remember { mutableStateOf(false) }
    var phoneError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (contact == null) "Agregar Contacto" else "Editar Contacto",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { 
                        name = it
                        nameError = false
                    },
                    label = { Text("Nombre *") },
                    isError = nameError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (nameError) {
                    Text(
                        text = "El nombre es requerido",
                        color = Color(0xFFD32F2F),
                        fontSize = 12.sp
                    )
                }
                
                OutlinedTextField(
                    value = phone,
                    onValueChange = { 
                        phone = it
                        phoneError = false
                    },
                    label = { Text("Teléfono *") },
                    isError = phoneError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (phoneError) {
                    Text(
                        text = "El teléfono es requerido",
                        color = Color(0xFFD32F2F),
                        fontSize = 12.sp
                    )
                }
                
                OutlinedTextField(
                    value = relationship,
                    onValueChange = { relationship = it },
                    label = { Text("Relación (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                        return@Button
                    }
                    if (phone.isBlank()) {
                        phoneError = true
                        return@Button
                    }
                    onSave(name, phone, relationship)
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun DeleteContactDialog(
    contact: EmergencyContact,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Eliminar Contacto",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text("¿Estás seguro de eliminar a ${contact.name} de tus contactos de emergencia?")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD32F2F)
                )
            ) {
                Text("Eliminar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
