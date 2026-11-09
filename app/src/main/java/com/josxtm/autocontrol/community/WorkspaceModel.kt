package com.josxtm.autocontrol.community

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josxtm.autocontrol.community.storage.WorkspaceStore
import com.josxtm.autocontrol.community.storage.PortableBackup
import com.josxtm.autocontrol.community.documents.DocumentEngine
import com.josxtm.autocontrol.community.ocr.LocalOcr
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WorkspaceModel(application: Application) : AndroidViewModel(application) {
    private val store = WorkspaceStore(application)
    private val ocr = LocalOcr(application)
    private val resolver = application.contentResolver
    private val preferences = application.getSharedPreferences("public-ui", 0)
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf("Preparado. Solo almacenamiento local."); private set
    var theme by mutableIntStateOf(preferences.getInt("theme", 0)); private set
    var tabletTheme by mutableIntStateOf(preferences.getInt("tabletTheme", 3).coerceIn(0, 9)); private set
    var identifier by mutableStateOf("id")
    var query by mutableStateOf("")
    var record by mutableStateOf<Map<String, String?>>(emptyMap()); private set
    var activeId by mutableStateOf<String?>(null); private set
    var note by mutableStateOf("")
    var journalText by mutableStateOf("")
    var journal by mutableStateOf<List<WorkspaceStore.Entry>>(emptyList()); private set
    var fields by mutableStateOf<List<String>>(emptyList()); private set
    var visible by mutableStateOf<List<String>>(emptyList()); private set
    var documents by mutableStateOf<List<WorkspaceStore.Document>>(emptyList()); private set
    var docId by mutableStateOf<String?>(null); private set
    var docTitle by mutableStateOf("")
    var docText by mutableStateOf("")
    var docEditable by mutableStateOf(true); private set
    var ocrReady by mutableStateOf(false); private set
    private var pendingPass: CharArray? = null
    init { refresh() }
    private fun <T> task(work: () -> T, apply: (T) -> Unit = {}) {
        if (busy) return
        busy = true; message = "Procesando en el dispositivo…"
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { work() }
                apply(result)
                if (message == "Procesando en el dispositivo…") message = "Operación completada."
            } catch (_: Exception) {
                message = "No se pudo completar. Revisa formato, límites, identificadores únicos, contraseña, espacio y acceso al archivo. Los datos anteriores se conservan si falla una sustitución."
            } finally { busy = false }
        }
    }
    private data class Snapshot(val field: String, val fields: List<String>, val visible: List<String>, val docs: List<WorkspaceStore.Document>, val diary: List<WorkspaceStore.Entry>, val ocr: Boolean)
    private fun snapshot() = Snapshot(store.setting("identifier"), store.fields(), store.visibleFields(), store.documents(), store.journal(Long.MAX_VALUE), ocr.available())
    private fun show(s: Snapshot) {
        if (s.field.isNotEmpty()) identifier = s.field
        fields = s.fields; visible = s.visible; documents = s.docs; journal = s.diary; ocrReady = s.ocr
    }
    fun refresh() = task({ snapshot() }, ::show)
    fun cycleTheme() { theme = (theme + 1) % 3; preferences.edit().putInt("theme", theme).apply() }
    fun selectTabletTheme(index: Int) { if (index in 0..9) { tabletTheme = index; preferences.edit().putInt("tabletTheme", index).apply() } }
    fun search() {
        val id = query
        task({ Pair(store.find(id), store.note(id)) }) { (value, body) ->
            record = value ?: emptyMap(); activeId = if (value == null) null else id; note = if (value == null) "" else body
            message = if (value == null) "Sin coincidencias." else "Registro encontrado."
        }
    }
    fun saveNote() {
        val id = activeId ?: return; val body = note
        task({ store.saveNote(id, body) }) { message = "Nota guardada." }
    }
    fun saveVisible(chosen: List<String>) = task({ store.visibleFields(chosen); store.visibleFields() }) { visible = it }
    fun addJournal() {
        val body = journalText
        task({ store.addJournal(body); store.journal(Long.MAX_VALUE) }) { journal = it; journalText = ""; message = "Entrada guardada." }
    }
    fun diaryPage(reset: Boolean) {
        val before = if (reset) Long.MAX_VALUE else journal.lastOrNull()?.id ?: Long.MAX_VALUE
        task({ store.journal(before) }) { if (it.isEmpty()) message = "No hay más entradas." else journal = it }
    }
    fun importCatalog(uri: Uri) {
        val field = identifier
        task({
            resolver.openInputStream(uri).use { input ->
                requireNotNull(input)
                store.importCatalog(InputStreamReader(input, StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)), field)
            }
            snapshot()
        }) { show(it); record = emptyMap(); activeId = null; note = ""; message = "Catálogo importado. El diario se conserva." }
    }
    fun importDocument(uri: Uri) = task({
        val title = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }?.take(128)?.ifBlank { null } ?: "Documento del usuario"
        val type = (resolver.getType(uri) ?: "application/octet-stream").take(128)
        val data = resolver.openInputStream(uri).use { DocumentEngine.read(requireNotNull(it)) }
        store.saveDocument(UUID.randomUUID().toString(), title, type, data); store.documents()
    }) { documents = it; message = "Documento importado como adjunto local." }
    fun openDocument(id: String) = task({ requireNotNull(store.document(id)) }) {
        docId = it.id; docTitle = it.name; val text = DocumentEngine.editable(it.data)
        docEditable = text != null; docText = text ?: ""; message = if (docEditable) "Texto listo para editar." else "Adjunto inerte. Puedes exportar su archivo original."
    }
    fun newDocument() { docId = null; docTitle = ""; docText = ""; docEditable = true }
    fun saveDocument() {
        if (!docEditable) return
        val id = docId ?: UUID.randomUUID().toString(); val title = docTitle; val body = docText
        task({ store.saveDocument(id, title, "text/plain", DocumentEngine.encode(body)); store.documents() }) {
            documents = it; docId = id; message = "Documento guardado."
        }
    }
    fun deleteDocument() {
        val id = docId ?: return
        task({ store.deleteDocument(id); store.documents() }) { documents = it; newDocument(); message = "Documento eliminado." }
    }
    fun exportDocument(uri: Uri) {
        val id = docId ?: return
        task({
            val document = requireNotNull(store.document(id))
            resolver.openOutputStream(uri, "wt").use { requireNotNull(it).write(document.data) }
        }) { message = "Documento guardado exportado. Los cambios pendientes deben guardarse antes de exportar." }
    }
    fun importModel(uri: Uri) = task({ resolver.openInputStream(uri).use { ocr.importModel(requireNotNull(it)) } }) {
        ocrReady = true; message = "Modelo local validado. SHA-256: $it"
    }
    fun recognize(uri: Uri) = task({ ocr.recognize(resolver, uri) }) {
        newDocument(); docTitle = "Texto OCR"; docText = it; message = "OCR terminado. Revisa el texto antes de guardarlo."
    }
    fun preparePassphrase(text: String): Boolean {
        clearPassphrase()
        if (text.length !in 12..256) { message = "Usa una contraseña de 12 a 256 caracteres. No se guarda ni se puede recuperar."; return false }
        pendingPass = text.toCharArray(); return true
    }
    fun selectorUnavailable() { clearPassphrase(); message = "No hay selector de documentos disponible en este dispositivo." }
    fun clearPassphrase() { pendingPass?.fill('\u0000'); pendingPass = null }
    fun backup(uri: Uri, restore: Boolean) {
        val pass = pendingPass ?: run { message = "Introduce de nuevo la contraseña de la copia."; return }
        pendingPass = null
        task({
            try {
                if (restore) resolver.openInputStream(uri).use { input ->
                    PortableBackup.restore(requireNotNull(input), pass, getApplication<Application>().cacheDir) { store.restore(it) }
                } else resolver.openOutputStream(uri,"wt").use { output ->
                    PortableBackup.export(requireNotNull(output), pass) { store.backup(it) }
                }
                snapshot()
            } finally { pass.fill('\u0000') }
        }) {
            show(it)
            if (restore) { record = emptyMap(); activeId = null; note = ""; newDocument(); journalText = "" }
            message = if (restore) "Copia autenticada y restaurada." else "Copia cifrada exportada. Conserva su contraseña fuera de la app."
        }
    }
    override fun onCleared() { clearPassphrase(); Thread({ store.close() }, "public-store-close").start() }
}
