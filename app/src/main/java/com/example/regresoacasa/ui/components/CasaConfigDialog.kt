package com.example.regresoacasa.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CasaConfigDialog(
    direccionActual: String?,
    onDismiss: () -> Unit,
    onGuardar: (direccion: String) -> Unit,
    estaCargando: Boolean
) {
    var direccion by remember { mutableStateOf(direccionActual ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurar Dirección de Casa") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Ingresa la dirección de tu casa para calcular la ruta.",
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = direccion,
                    onValueChange = { direccion = it },
                    label = { Text("Dirección de casa") },
                    placeholder = { Text("Ej: Calle Principal 123, Ciudad") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onGuardar(direccion) },
                enabled = direccion.isNotBlank() && !estaCargando
            ) {
                if (estaCargando) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Guardar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
