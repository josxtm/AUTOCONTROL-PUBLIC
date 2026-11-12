package com.josxtm.autocontrol.community

import android.os.Bundle
import com.josxtm.autocontrol.community.sniffer.SnifferPanel
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private val model: WorkspaceModel by viewModels()
    private val catalog = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri == null) model.catalogSelectionCancelled() else model.importCatalog(uri) }
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
    var tab by remember { mutableIntStateOf(7) }
    var confirm by remember { mutableStateOf("") }
    var fieldsDialog by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<List<String>>(emptyList()) }
    var licenses by remember { mutableStateOf(false) }
    var notifications by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }
    val networkStatus = com.josxtm.autocontrol.community.sniffer.rememberLanStatus()
    var passphrase by remember { mutableStateOf("") }
    val appContext = LocalContext.current
    val displayName = appContext.applicationInfo.loadLabel(appContext.packageManager).toString()
    val enabled = !m.busy
    val nav = listOf(
        Triple("home", "Inicio", 7),Triple("ct", "Centros CT", 0),
        Triple("network", "SNIFFER LAN", 4),Triple("document", "PES / BDI", 6),
        Triple("document", "Documentos", 2),Triple("document", "Diario", 1),
        Triple("database", "Copias", 3),Triple("settings", "Configuración", 5)
    )
    val current=nav.first{it.third==tab}
    val charcoal = Color(0xFF303943)
    val charcoalDeep = Color(0xFF242C34)
    val accent = MaterialTheme.colorScheme.primary
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().fillMaxSize()) {
            // Persistent compact instrument header, outside scroll containers.
            Row(Modifier.fillMaxWidth().height(46.dp).background(charcoalDeep)
                .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TechIcon("network",Modifier.size(28.dp),Color(0xFFD4313D));Text("AutoControL", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Surface(color = accent, shape = RoundedCornerShape(5.dp)) {
                    Text("TABLET", Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
                Text(if(tab==4)"SNIFFER LAN · Diagnóstico y Terminal (MangoSSH)"else current.second, color = Color(0xFFCAD3DB),
                    style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text(displayName, style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFDBE1E6))
                Text(networkStatus, style=MaterialTheme.typography.labelSmall,color=Color.White)
                IconButton(onClick={notifications=true}) { TechIcon("bell",color=Color.White,description="Notificaciones") }
                Box {
                    IconButton(onClick={options=true}) { TechIcon("menu",color=Color.White,description="Opciones") }
                    DropdownMenu(options,{options=false}) {
                        DropdownMenuItem(text={Text("Configuración")},onClick={options=false;tab=5})
                        DropdownMenuItem(text={Text("Centros CT")},onClick={options=false;tab=0})
                    }
                }
            }
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(126.dp).fillMaxHeight().background(charcoal)
                    .padding(7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(" MÓDULOS", Modifier.padding(vertical = 10.dp),
                        style = MaterialTheme.typography.labelSmall, color = Color(0xFFBAC6CE))
                    nav.forEachIndexed { index, item ->
                        val chosen = item.third == tab
                        Surface(
                            onClick = { tab = item.third },
                            modifier = Modifier.fillMaxWidth().height(66.dp),
                            shape = RoundedCornerShape(7.dp),
                            color = if (chosen) accent else charcoal,
                            contentColor = Color.White,
                            border = if (chosen) null else BorderStroke(1.dp, Color(0xFF485560))
                        ) {
                            Column(Modifier.padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center) {
                                TechIcon(item.first,Modifier.size(28.dp),Color.White)
                                Text(item.second, color = Color.White, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    HorizontalDivider(color = Color(0xFF586571))
                    Text(autoThemes[m.tabletTheme].name.uppercase(), style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFBEC9D3))
                    Text("SIN TELEMANDOS", style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFBEC9D3))
                }
                Column(Modifier.weight(1f).fillMaxHeight().padding(9.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (m.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    // Visible on every module, including SNIFFER, even when its workspace is not scrollable.
                    if (m.message != "Preparado. Solo almacenamiento local." &&
                        m.message.isNotBlank()) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp)) {
                            Text(m.message, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (tab == 4) {
                        // SSH remains alive inside its own pane; unchanged transport.
                        SnifferPanel(Modifier.weight(1f).fillMaxWidth(),
                            selectedCt = m.activeId, selectedLan = m.selectedCtLan,selectedName=m.selectedCtName,query=m.query,onQuery={m.query=it},onSelect=m::search)
                    } else if(tab==7){
                        IndustrialPanel("Herramientas locales","home",Modifier.fillMaxWidth()){
                            nav.filter{it.third!=7}.forEach{item->IndustrialButton(item.second,item.first){tab=item.third}}
                            Text(m.message,Modifier.padding(12.dp))
                        }
                    } else if(tab==6){
                        IndustrialPanel("PES / BDI","document",Modifier.fillMaxWidth()){
                            Text("Esta edición pública conserva la biblioteca de documentos. No contiene un motor BDI oficial ni procedimientos PES verificados.",Modifier.padding(16.dp))
                            IndustrialButton("Abrir documentos","document"){tab=2}
                        }
                    } else if (tab == 0) {
                        IndustrialCtPanel(
                            model = m,
                            enabled = enabled,
                            importCatalog = { confirm = "catalog" },
                            chooseFields = { selected = m.visible; fieldsDialog = true },
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                    } else {
                        Surface(Modifier.weight(1f).fillMaxWidth(),
                            shape = RoundedCornerShape(11.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Spacer(Modifier.height(8.dp)); Text(displayName, style = MaterialTheme.typography.titleLarge) }
                item { Text("Edición independiente · trabajo local sin conexión") }
                item { Text("Tema: " + autoThemes[m.tabletTheme].name, style = MaterialTheme.typography.labelSmall) }
                item { Text(m.message, style = MaterialTheme.typography.bodyMedium); if (m.busy) LinearProgressIndicator(Modifier.fillMaxWidth()) }
                when (tab) {
                    0 -> {
                        item { Text("Catálogo local", style = MaterialTheme.typography.titleLarge) }
                        item { Text("JSON con lista de registros y campos simples. La búsqueda usa el identificador exacto configurado al importar.") }
                        item { Editor("Campo identificador", m.identifier, 128, enabled, true) { m.identifier = it } }
                        item { Action("Importar catálogo JSON", enabled) { confirm = "catalog" } }
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
                                    Text((if (m.tabletTheme == index) "Seleccionado: " else "") + theme.name)
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
            }
        }
    }
    if(notifications) AlertDialog(onDismissRequest={notifications=false},title={Text("Estado de operaciones")},text={Text(m.message)},confirmButton={TextButton(onClick={notifications=false}){Text("Cerrar")}})
    if (confirm.isNotEmpty()) AlertDialog(onDismissRequest = { confirm = ""; m.clearPassphrase() },
        title = { Text(if (confirm == "delete") "Eliminar documento" else "Sustituir datos locales") },
        text = { Text(when (confirm) {
            "catalog" -> "La importación sustituye el catálogo y conserva las notas de centros presentes. Si excluiría centros con notas, se rechaza. Diario y documentos se conservan; un error revierte la transacción."
            "delete" -> "Elimina el adjunto seleccionado de esta edición pública. Exporta una copia antes si deseas conservarlo."
            else -> "La restauración autenticada sustituye catálogo, notas, diario y documentos. Haz una copia antes. Una copia inválida conserva los datos actuales."
        }) },
        confirmButton = { TextButton(onClick = { val choice = confirm; confirm = ""; when (choice) { "catalog" -> pickCatalog(); "delete" -> m.deleteDocument(); else -> restoreBackup() } }) { Text("Continuar") } },
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
