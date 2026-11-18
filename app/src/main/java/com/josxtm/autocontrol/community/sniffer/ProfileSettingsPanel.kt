package com.josxtm.autocontrol.community.sniffer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation

/** Maintenance stays in the existing Configuration tab; never part of the monthly Grid flow. */
@Composable internal fun ProfileSettingsPanel(model: ProfileMaintenanceModel, enabled: Boolean,
    pickProfile: () -> Unit, exportBackup: () -> Unit, restoreBackup: () -> Unit) {
    var confirm by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { model.refresh() }
    val available = enabled && !model.busy
    Column {
        Text("Direccionamiento IP privado", style = MaterialTheme.typography.titleLarge)
        val installed = model.installed
        if (installed == null) Text("Sin perfil instalado. Importa una sola vez el perfil autorizado.")
        else {
            Text("Perfil instalado y reutilizado automáticamente")
            Text("NORMAL /28 · ${installed.profiles.normal.revision}")
            Text("EXTENDIDO /26 · ${installed.profiles.extended.revision}")
            Text("Versión de configuración: ${installed.digest.take(12)}", style = MaterialTheme.typography.bodySmall)
        }
        Text("Todos los fabricantes utilizan las mismas reglas de topología. Cada mes solo necesitas actualizar el Grid MT.")
        Text(model.message, style = MaterialTheme.typography.bodySmall)
        if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        OutlinedButton(onClick = { confirm = "import" }, enabled = available) {
            Text(if (installed == null) "Instalar perfil autorizado" else "Actualizar perfil · mantenimiento")
        }
        Text("Respaldo privado: perfiles y confirmaciones por CT, cifrados con contraseña. La copia del catálogo y sus notas se gestiona en Copias.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(model.password, { model.password = it.take(256) }, label = { Text("Contraseña del respaldo privado (12–256)") },
            enabled = available, singleLine = true, visualTransformation = PasswordVisualTransformation())
        OutlinedButton(onClick = { if (model.preparePassword()) exportBackup() }, enabled = available && installed != null) { Text("Exportar respaldo privado cifrado") }
        OutlinedButton(onClick = { confirm = "restore" }, enabled = available) { Text("Restaurar respaldo privado cifrado") }
    }
    if (confirm.isNotEmpty()) AlertDialog(onDismissRequest = { confirm = "" },
        title = { Text(if (confirm == "import") "Perfil autorizado" else "Restaurar configuración privada") },
        text = { Text(if (confirm == "import") "Selecciona un perfil contrastado con la documentación autorizada. Una versión diferente sustituye el perfil e invalida las confirmaciones vinculadas al anterior. No es necesario hacerlo al actualizar el Grid MT." else "La copia autenticada sustituirá únicamente el perfil IP y sus confirmaciones. Conserva catálogo, notas, diario, documentos y apariencia. Una contraseña o copia inválida no sustituye la configuración.") },
        confirmButton = { TextButton(onClick = {
            val action = confirm; confirm = ""
            if (action == "import") pickProfile() else if (model.preparePassword()) restoreBackup()
        }) { Text("Continuar") } },
        dismissButton = { TextButton(onClick = { confirm = "" }) { Text("Cancelar") } })
}
