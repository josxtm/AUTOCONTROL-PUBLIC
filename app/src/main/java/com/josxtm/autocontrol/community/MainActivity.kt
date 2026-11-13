package com.josxtm.autocontrol.community

import android.os.Bundle
import com.josxtm.autocontrol.community.sniffer.SnifferPanel
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private val model: WorkspaceModel by viewModels()
    private val catalog = registerForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::previewCsv) }
    private val document = registerForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::importDocument) }
    private val ocrModel = registerForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::importModel) }
    private val image = registerForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::recognize) }
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { it?.let(model::exportDocument) }
    private val backup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        if (it == null) model.clearPassphrase() else model.backup(it, false)
    }
    private val restore = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it == null) model.clearPassphrase() else model.backup(it, true)
    }
    private fun choose(action: () -> Unit) {
        try { action() } catch (_: android.content.ActivityNotFoundException) { model.selectorUnavailable() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            val dark = model.theme == 2 || (model.theme == 0 && isSystemInDarkTheme())
            MaterialTheme(colorScheme = autoColorScheme(model.tabletTheme)) {
                Workspace(model,
                    pickCatalog = { choose { catalog.launch(arrayOf("*/*")) } },
                    pickDocument = { choose { document.launch(arrayOf("*/*")) } },
                    pickModel = { choose { ocrModel.launch(arrayOf("*/*")) } },
                    pickImage = { choose { image.launch(arrayOf("image/*")) } },
                    exportDocument = { choose { export.launch(model.docTitle.ifBlank { "documento" }) } },
                    createBackup = { choose { backup.launch("autocontrol-community.acpb") } },
                    restoreBackup = { choose { restore.launch(arrayOf("*/*")) } })
            }
        }
    }
}

@Composable private fun Editor(label: String, value: String, limit: Int, enabled: Boolean, single: Boolean = false, change: (String) -> Unit) {
    OutlinedTextField(value, { if (it.length <= limit) change(it) }, label = { Text(label) }, enabled = enabled,
        modifier = Modifier.fillMaxWidth(), singleLine = single, minLines = if (single) 1 else 3,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false))
}

@Composable private fun Action(text: String, enabled: Boolean, action: () -> Unit) {
    Button(onClick = action, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(text) }
}

@Composable private fun Workspace(m: WorkspaceModel, pickCatalog: () -> Unit, pickDocument: () -> Unit,
    pickModel: () -> Unit, pickImage: () -> Unit, exportDocument: () -> Unit, createBackup: () -> Unit, restoreBackup: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf("") }
    var fieldsDialog by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<List<String>>(emptyList()) }
    var licenses by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    val appContext = LocalContext.current
    val displayName = appContext.applicationInfo.loadLabel(appContext.packageManager).toString()
    val enabled = !m.busy
    Surface(Modifier.fillMaxSize()) {
        Row(Modifier.safeDrawingPadding().fillMaxSize()) {
            Column(Modifier.width(132.dp).fillMaxHeight().padding(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("AutoControL", style = MaterialTheme.typography.titleMedium)
                listOf("CT", "Diario", "Documentos", "Copias", "SNIFFER", "Config.").forEachIndexed { index, label ->
                    if (tab == index) Button(onClick = { tab = index }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(5.dp)) { Text(label, style = MaterialTheme.typography.labelMedium) }
                    else TextButton(onClick = { tab = index }, modifier = Modifier.fillMaxWidth()) { Text(label, style = MaterialTheme.typography.labelMedium) }
                }
            }
            if (tab == 4) {
                // The active SSH session must not be disposed by LazyColumn virtualization.
                SnifferPanel(Modifier.weight(1f).fillMaxHeight(), selectedCt = m.activeId, selectedLan = m.selectedCtLan)
            } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Spacer(Modifier.height(8.dp)); Text(displayName, style = MaterialTheme.typography.titleLarge) }
                item { Text("Edición independiente · trabajo local sin conexión") }
                item { Text("Tema: " + autoThemes[m.tabletTheme].name, style = MaterialTheme.typography.labelSmall) }
                item { Text(m.message, style = MaterialTheme.typography.bodyMedium); if (m.busy) LinearProgressIndicator(Modifier.fillMaxWidth()) }
                when (tab) {
                    0 -> {
                        item { Text("GRID MT · eSir", style = MaterialTheme.typography.titleLarge) }
                        item { Text("Importa el CSV original de eSir (.csv, .tsv o .txt). No necesitas convertirlo a JSON. Actualiza por matrícula, sin borrar CT históricos, notas ni diario.") }
                        item { Action("Seleccionar actualización CSV", enabled, pickCatalog) }
                        m.csvPreview?.let { preview ->
                            item { Text("Vista previa: ${preview.rows} CT del archivo · ${preview.added} nuevos · ${preview.changed} modificados · ${preview.unchanged} sin cambios · ${preview.retained} históricos conservados.") }
                            item { Text("Identificador: ${preview.identifier}. Los CT ausentes se conservan sin eliminar sus notas. Se actualizarán datos e IP del catálogo, nunca las anotaciones.") }
                            item { Action("Confirmar actualización", enabled, m::applyCsv) }
                            item { Action("Cancelar actualización", enabled, m::discardCsv) }
                        }
                        item { Editor("Identificador exacto", m.query, 256, enabled, true) { m.query = it } }
                        item { Action("Buscar registro", enabled, m::search) }
                        item { Action("Campos visibles", enabled && m.fields.isNotEmpty()) { selected = m.visible; fieldsDialog = true } }
                        m.record.filterKeys { it in m.visible }.forEach { (key, value) -> item { Text("$key: ${value ?: "(vacío)"}") } }
                        if (m.activeId != null) {
                            item { Editor("Nota del registro", m.note, 16000, enabled) { m.note = it } }
                            item { Action("Guardar nota", enabled, m::saveNote) }
                        }
                    }
                    1 -> {
                        item { Text("Diario independiente", style = MaterialTheme.typography.titleLarge) }
                        item { Editor("Nueva entrada", m.journalText, 16000, enabled) { m.journalText = it } }
                        item { Action("Añadir entrada", enabled && m.journalText.isNotBlank(), m::addJournal) }
                        m.journal.forEach { entry -> item { Text(DateFormat.getDateTimeInstance().format(Date(entry.time)) + "\n" + entry.body) } }
                        item { Action("Entradas más recientes", enabled) { m.diaryPage(true) } }
                        item { Action("Página siguiente", enabled && m.journal.isNotEmpty()) { m.diaryPage(false) } }
                    }
                    2 -> {
                        item { Text("Documentos del usuario", style = MaterialTheme.typography.titleLarge) }
                        item { Text("Adjuntos locales sin ejecutar macros ni interpretar plantillas. Edición de texto UTF-8 hasta 16000 caracteres; los demás archivos se conservan para exportar.") }
                        item { Action("Importar documento", enabled, pickDocument) }
                        item { Action("Nuevo texto en blanco", enabled, m::newDocument) }
                        m.documents.forEach { doc -> item { OutlinedButton(onClick = { m.openDocument(doc.id) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(doc.name) } } }
                        item { Editor("Nombre del documento", m.docTitle, 128, enabled && m.docEditable, true) { m.docTitle = it } }
                        if (m.docEditable) {
                            item { Editor("Texto editable", m.docText, 16000, enabled) { m.docText = it } }
                            item { Action("Guardar documento", enabled && m.docTitle.isNotBlank(), m::saveDocument) }
                        }
                        if (m.docId != null) {
                            item { Action("Exportar documento guardado", enabled, exportDocument) }
                            item { Action("Eliminar documento", enabled) { confirm = "delete" } }
                        }
                        item { Text("OCR local", style = MaterialTheme.typography.titleLarge) }
                        item { Text("Tesseract. Aporta un modelo traineddata compatible con licencia de uso adecuada. No se incluyen modelos ni imágenes. El resultado se abre como texto sin guardar para revisarlo.") }
                        item { Action("Importar modelo OCR", enabled, pickModel) }
                        item { Action("Reconocer imagen local", enabled && m.ocrReady, pickImage) }
                        item { TextButton(onClick = { licenses = true }) { Text("Licencias del motor OCR y bibliotecas") } }
                    }
                    3 -> {
                        item { Text("Copias cifradas", style = MaterialTheme.typography.titleLarge) }
                        item { Text("Incluyen catálogo, campos, notas, diario y adjuntos. El modelo OCR y el tema no se incluyen. Contraseña de 12 a 256 caracteres: no se guarda ni se puede recuperar. Elige un proveedor local autorizado en el selector.") }
                        item { OutlinedTextField(passphrase, { if (it.length <= 256) passphrase = it }, label = { Text("Contraseña de la copia") },
                            modifier = Modifier.fillMaxWidth(), enabled = enabled, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)) }
                        item { Action("Exportar copia cifrada", enabled) { if (m.preparePassphrase(passphrase)) { passphrase = ""; createBackup() } } }
                        item { Action("Restaurar copia cifrada", enabled) { if (m.preparePassphrase(passphrase)) { passphrase = ""; confirm = "restore" } } }
                        item { Text("Diagnóstico local: esquema público 1 · ${m.documents.size} documentos · modelo OCR ${if (m.ocrReady) "disponible" else "no importado"}. Sin diagnósticos de redes.") }
                    }
                    5 -> {
                        item { Text("Configuración · Apariencia", style = MaterialTheme.typography.titleLarge) }
                        item { Text("Diez temas aprobados · iconografía V10") }
                        autoThemes.forEachIndexed { index, theme ->
                            item {
                                OutlinedButton(onClick = { m.selectTabletTheme(index) }, modifier = Modifier.fillMaxWidth()) {
                                    Text((if (m.tabletTheme == index) "✓  " else "") + theme.name)
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
            }
        }
    }
    if (confirm.isNotEmpty()) AlertDialog(onDismissRequest = { confirm = ""; m.clearPassphrase() },
        title = { Text(if (confirm == "delete") "Eliminar documento" else "Sustituir datos locales") },
        text = { Text(when (confirm) {
            "delete" -> "Elimina el adjunto seleccionado de esta edición pública. Exporta una copia antes si deseas conservarlo."
            else -> "La restauración autenticada sustituye catálogo, notas, diario y documentos. Haz una copia antes. Una copia inválida conserva los datos actuales."
        }) },
        confirmButton = { TextButton(onClick = { val choice = confirm; confirm = ""; when (choice) { "delete" -> m.deleteDocument(); else -> restoreBackup() } }) { Text("Continuar") } },
        dismissButton = { TextButton(onClick = { confirm = ""; m.clearPassphrase() }) { Text("Cancelar") } })
    if (fieldsDialog) AlertDialog(onDismissRequest = { fieldsDialog = false }, title = { Text("Campos visibles") },
        text = { LazyColumn { m.fields.forEach { field -> item { Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(field in selected, { checked -> selected = if (checked) selected + field else selected - field }); Text(field)
        } } } } },
        confirmButton = { TextButton(onClick = { fieldsDialog = false; m.saveVisible(selected) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = { fieldsDialog = false }) { Text("Cancelar") } })
    if (licenses) {
        val context = LocalContext.current
        AlertDialog(onDismissRequest = { licenses = false }, title = { Text("Avisos de software libre") },
            text = { LazyColumn { item { Text(context.getString(com.josxtm.autocontrol.community.ocr.R.string.third_party_notices)) } } },
            confirmButton = { TextButton(onClick = { licenses = false }) { Text("Cerrar") } })
    }
}
