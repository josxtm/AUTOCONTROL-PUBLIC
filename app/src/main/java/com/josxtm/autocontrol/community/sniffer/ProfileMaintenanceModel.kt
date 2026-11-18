package com.josxtm.autocontrol.community.sniffer

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.josxtm.autocontrol.community.storage.PortableBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class ProfileMaintenanceModel(application: Application) : AndroidViewModel(application) {
    private val store = AddressProfileStore(application)
    var installed by mutableStateOf<AddressProfileStore.Installed?>(null); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf("Comprobando perfil privado…"); private set
    var password by mutableStateOf("")
    private var pending: CharArray? = null
    init { refresh() }
    private fun task(work: () -> AddressProfileStore.Installed?, success: String) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try { installed = withContext(Dispatchers.IO) { work() }; message = success }
            catch (_: Exception) { message = "No se pudo completar. Revisa el archivo, la contraseña, el espacio y los permisos. Una copia inválida no sustituye la configuración instalada." }
            finally { busy = false }
        }
    }
    fun refresh() = task({ store.installed() }, "Los perfiles se reutilizan automáticamente; la actualización mensual solo necesita el Grid MT.")
    fun importProfile(uri: Uri) = task({
        val text = getApplication<Application>().contentResolver.openInputStream(uri).use { stream ->
            requireNotNull(stream)
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) { val n = stream.read(buffer); if (n < 0) break; require(output.size() + n <= 131072); output.write(buffer, 0, n) }
            Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(output.toByteArray())).toString()
        }
        store.install(text)
    }, "Perfil privado instalado. Se conservará al reiniciar, al actualizar el Grid y en actualizaciones compatibles de la app.")
    fun preparePassword(): Boolean {
        clearPassword()
        if (password.length !in 12..256) { message = "Contraseña del respaldo: entre 12 y 256 caracteres."; return false }
        pending = password.toCharArray(); password = ""; return true
    }
    fun clearPassword() { pending?.fill('\u0000'); pending = null }
    fun selectorUnavailable() { clearPassword(); message = "No se pudo abrir el selector de archivos." }
    fun backup(uri: Uri, restore: Boolean) {
        if (busy) { clearPassword(); message = "Espera a que termine la operación actual."; return }
        val pass = pending ?: run { message = "Introduce de nuevo la contraseña del respaldo."; return }
        pending = null
        task({
            try {
                val app = getApplication<Application>()
                if (restore) app.contentResolver.openInputStream(uri).use { input ->
                    PortableBackup.restore(requireNotNull(input), pass, app.cacheDir) { store.readBackup(it) }
                } else app.contentResolver.openOutputStream(uri, "wt").use { output ->
                    PortableBackup.export(requireNotNull(output), pass) { store.writeBackup(it) }
                }
                store.installed()
            } finally { pass.fill('\u0000') }
        }, if (restore) "Respaldo privado autenticado y restaurado. Catálogo, notas y documentos sin cambios." else "Respaldo privado cifrado guardado. Incluye perfiles y confirmaciones por CT.")
    }
    override fun onCleared() { clearPassword(); password = ""; super.onCleared() }
}
