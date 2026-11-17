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
import com.josxtm.autocontrol.community.storage.CsvOperation
import com.josxtm.autocontrol.community.storage.WorkspaceStore
import com.josxtm.autocontrol.community.storage.PortableBackup
import com.josxtm.autocontrol.community.documents.DocumentEngine
import com.josxtm.autocontrol.community.ocr.LocalOcr
import java.io.IOException
import java.io.BufferedInputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
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
    // Read only from the currently selected private catalog record, never logged or exported.
    val selectedCtLan: String?
        get() = record.entries.firstOrNull { entry ->
            val key = entry.key.trim().uppercase(java.util.Locale.ROOT)
                .replace("_", " ").replace(Regex("\\s+"), " ")
            key == "IP LAN" || key == "IP_LAN"
        }?.value?.trim()?.takeIf { it.isNotEmpty() }

    var csvProgress by mutableStateOf<CsvOperation.Progress?>(null); private set
    var csvCancelling by mutableStateOf(false); private set
    private var csvOperation: CsvOperation? = null
    private val csvHandler = android.os.Handler(android.os.Looper.getMainLooper())
    fun cancelCsv() {
        val operation = csvOperation ?: return
        csvCancelling = true
        operation.cancel() // Sets Java's token immediately; closes active stream on a separate worker.
    }
    private fun <T> csvTask(writing: Boolean, total: Long, work: (CsvOperation) -> T, apply: (T) -> Unit) {
        if (busy) return
        busy = true; csvCancelling = false
        lateinit var operation: CsvOperation
        operation = CsvOperation { progress ->
            csvHandler.post { if (csvOperation === operation) csvProgress = progress }
        }
        csvOperation = operation
        operation.start(writing, total, -1)
        message = if (writing) "Actualizando base de datos…" else "Leyendo archivo Grid MT…"
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { work(operation) }
                operation.check()
                if (csvOperation === operation) apply(result)
            } catch (error: Exception) {
                if (csvOperation === operation) message = csvError(error, operation)
            } finally {
                if (csvOperation === operation) {
                    csvOperation = null; csvProgress = null; csvCancelling = false; busy = false
                }
            }
        }
    }
    private fun csvError(error: Exception, operation: CsvOperation): String {
        if (operation.isCancelled || error is CsvOperation.Cancelled)
            return "Operación cancelada. Datos anteriores conservados."
        val reason = when (error) {
            is SecurityException, is java.io.FileNotFoundException -> "No se puede acceder al documento. Revisa sus permisos y vuelve a seleccionarlo."
            is java.util.zip.ZipException, is java.io.EOFException -> "El archivo GZIP está corrupto o incompleto. Descárgalo de nuevo."
            is android.database.sqlite.SQLiteFullException -> "No hay espacio suficiente para actualizar la base de datos."
            is android.database.sqlite.SQLiteException -> "Error de base de datos durante la actualización."
            is IOException -> when {
                error.message?.contains("cambió desde la vista previa") == true -> "El archivo cambió desde la vista previa. Selecciónalo de nuevo."
                error.message?.contains("duplicada en el CSV") == true -> "El archivo contiene matrículas duplicadas."
                error.message?.contains("cabecera", ignoreCase = true) == true ||
                    error.message?.contains("MATRICULA") == true -> "Cabeceras incompatibles o estructura CSV incorrecta. Revisa el Grid MT original."
                error.message?.contains("GZIP") == true -> "El archivo CSV.GZ no contiene un GZIP válido."
                error.message?.contains("zonas seleccionadas") == true -> "El archivo no contiene CT de las zonas seleccionadas."
                else -> "No se pudo leer el Grid MT. Revisa el formato, la estructura y el acceso al archivo."
            }
            else -> "No se pudo completar la importación. Revisa el archivo y el almacenamiento disponible."
        }
        return "$reason Los datos anteriores se conservan."
    }
    var csvPreview by mutableStateOf<WorkspaceStore.CsvUpdate?>(null); private set
    private var csvCandidate: Uri? = null
    private var csvCandidatePrefixes: Set<String>? = null
    var csvInclude28 by mutableStateOf(true); private set
    var csvInclude45 by mutableStateOf(true); private set
    var csvInclude82 by mutableStateOf(true); private set

    val selectedCsvPrefixes: Set<String>
        get() = linkedSetOf<String>().apply {
            if (csvInclude28) add("28")
            if (csvInclude45) add("45")
            if (csvInclude82) add("82")
        }

    fun selectCsvPrefix(prefix: String, checked: Boolean) {
        if (busy) return
        when (prefix) {
            "28" -> csvInclude28 = checked
            "45" -> csvInclude45 = checked
            "82" -> csvInclude82 = checked
            else -> return
        }
        // Any change invalidates a preview computed for a different selection.
        csvCandidate = null; csvCandidatePrefixes = null; csvPreview = null
    }
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
        val id = query.trim()
        if (!(id.startsWith("82") || id.startsWith("28") || id.startsWith("45"))) {
            record = emptyMap(); activeId = null; note = ""
            message = "Solo se consultan matrículas que empiezan por 82, 28 o 45."
            return
        }
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
    /** Streaming source. Preview only reads SQLite; apply writes one atomic transaction. */
    private fun csvFromUri(uri: Uri, apply: Boolean, prefixes: Set<String>,
                           operation: CsvOperation, field: String, expected: Long): WorkspaceStore.CsvUpdate {
        operation.check()
        var size = -1L
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) size = cursor.getLong(sizeColumn)
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            } else null
        }?.lowercase(java.util.Locale.ROOT)
        if (name != null && !(name.endsWith(".csv") || name.endsWith(".tsv") ||
                    name.endsWith(".txt") || name.endsWith(".csv.gz")))
            throw IOException("Selecciona el CSV o CSV.GZ original de eSir.")
        fun parse(charset: Charset): WorkspaceStore.CsvUpdate {
            operation.start(apply, expected, size)
            return resolver.openInputStream(uri).use { raw ->
                val sourceFile = raw ?: throw java.io.FileNotFoundException()
                operation.attach(sourceFile)
                var consumed = 0L
                val counted = object : java.io.FilterInputStream(sourceFile) {
                    override fun read(): Int {
                        operation.check()
                        val value = sourceFile.read()
                        if (value != -1) operation.bytes(++consumed)
                        return value
                    }
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        operation.check()
                        val count = sourceFile.read(buffer, offset, length)
                        if (count > 0) { consumed += count; operation.bytes(consumed) }
                        return count
                    }
                }
                try {
                    val input = BufferedInputStream(counted, 32768)
                    input.mark(4)
                    val gzip = input.read() == 0x1f && input.read() == 0x8b
                    input.reset()
                    if (name?.endsWith(".csv.gz") == true && !gzip)
                        throw IOException("El archivo .csv.gz no contiene datos GZIP.")
                    val source = if (gzip) GZIPInputStream(input, 32768) else input
                    InputStreamReader(source, charset.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)).use { reader ->
                        store.updateCsvCatalog(reader, field, apply, prefixes, operation)
                    }
                } finally { operation.detach() }
            }
        }
        return try { parse(StandardCharsets.UTF_8) }
        catch (_: CharacterCodingException) { operation.check(); parse(Charset.forName("windows-1252")) }
    }
    fun previewCsv(uri: Uri) {
        if (busy) return
        val selection = selectedCsvPrefixes
        if (selection.isEmpty()) {
            message = "Marca al menos una zona: 28, 45 u 82."
            return
        }
        val field = identifier
        csvCandidate = null; csvCandidatePrefixes = null; csvPreview = null
        csvTask(false, 0, { csvFromUri(uri, false, selection, it, field, 0) }) { summary ->
            csvCandidate = uri
            csvCandidatePrefixes = selection
            csvPreview = summary
            message = "Vista previa calculada para zonas: ${selection.joinToString(", ")}. Confirma para actualizar."
        }
    }
    fun applyCsv() {
        val uri = csvCandidate ?: return
        val selection = csvCandidatePrefixes ?: return
        val total = csvPreview?.rows?.toLong() ?: return
        val field = identifier
        csvTask(true, total, { operation ->
            val result = csvFromUri(uri, true, selection, operation, field, total)
            Pair(result, snapshot())
        }) { (summary, newSnapshot) ->
            show(newSnapshot)
            csvCandidate = null; csvCandidatePrefixes = null; csvPreview = null
            activeId = null; record = emptyMap(); note = ""
            message = "Actualización terminada: ${summary.added} nuevos, ${summary.changed} modificados, " +
                "${summary.unchanged} sin cambios y ${summary.retained} históricos conservados. Notas y diario intactos."
        }
    }
    fun discardCsv() {
        csvCandidate = null; csvCandidatePrefixes = null; csvPreview = null
        message = "Importación cancelada. Datos sin cambios."
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
    override fun onCleared() {
        csvOperation?.cancel()
        clearPassphrase()
        Thread({ store.close() }, "public-store-close").start()
        super.onCleared()
    }
}
