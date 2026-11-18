package com.josxtm.autocontrol.community.sniffer

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import org.json.JSONObject

/** Session state survives tab changes; a CT change invalidates every CT-dependent action/result. */
internal class CtActionState(private val context: Context, val transport: SshTransport, private val scope: CoroutineScope,
    private val profileStore: AddressProfileStore = AddressProfileStore(context)) {
    private var installedProfile = runCatching { profileStore.installed() }.getOrNull()
    var profiles by mutableStateOf(installedProfile?.profiles); private set
    var confirmationSaved by mutableStateOf(false); private set
    private var originalBase = ""
    private var inputSource = ""
    val transformersAutomatic get() = profiles?.let { LocalAddressProfiles.count(record, it.transformerFields) }?.let { it > 0 } == true
    val remotesAutomatic get() = profiles?.let { LocalAddressProfiles.count(record, it.remoteFields) } != null
    var ct by mutableStateOf(""); private set
    var record by mutableStateOf<Map<String, String?>>(emptyMap()); private set
    var transformers by mutableStateOf("")
    var remotes by mutableStateOf("")
    var base by mutableStateOf("")
    var maintenance by mutableStateOf("")
    var inventory by mutableStateOf<Map<String, AddressPlan.Presence>>(emptyMap()); private set
    var results by mutableStateOf<Map<String, DiagnosticResult>>(emptyMap()); private set
    var proposal by mutableStateOf<WanManager.Prepared?>(null); private set
    var snapshot by mutableStateOf<WanManager.Snapshot?>(null); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf(if (profiles == null) "Instala el perfil privado una vez desde Configuración." else "Perfil privado disponible. Selecciona un CT del Grid MT."); private set
    var webOverrides by mutableStateOf<Map<String, Pair<String, String>>>(emptyMap()); private set
    fun configureWeb(service: AddressPlan.Service, scheme: String, port: String) {
        if (!busy) { webOverrides = webOverrides + (service.id to (scheme to port.take(5))); confirmationSaved = false }
    }
    fun webService(service: AddressPlan.Service): AddressPlan.Service? = runCatching {
        val spec = webOverrides[service.id] ?: (service.scheme to service.port.toString())
        require(spec.first == "http" || spec.first == "https")
        AddressPlan.Service(service.id, service.label, service.offset, service.inventory, service.index, spec.first, requireNotNull(spec.second.toIntOrNull()))
    }.getOrNull()
    fun sourceDirect(value: Boolean) { if (!busy) { direct = value; epoch++; results = emptyMap() } }
    var direct by mutableStateOf(false); private set
    var authorized by mutableStateOf(false)
    var physicallyAbsent by mutableStateOf(false)
    var leaseActive by mutableStateOf(false); private set
    var leaseCt by mutableStateOf(""); private set
    val wan = WanManager(transport)
    private var epoch = 0
    private var token: CancellationToken? = null
    private var job: Job? = null
    val kind get() = AddressPlan.kind(transformers.toIntOrNull(), remotes.toIntOrNull())
    fun plan(): AddressPlan? = runCatching {
        val profiles = requireNotNull(profiles)
        AddressPlan(ct, base, if (kind == AddressPlan.Kind.NORMAL) profiles.normal else profiles.extended,
            transformers.toIntOrNull(), remotes.toIntOrNull(), inventory)
    }.getOrNull()
    fun changeCt(id: String?, values: Map<String, String?>, lan: String?) {
        if (ct == id.orEmpty() && record == values && originalBase == lan.orEmpty() && base.isNotEmpty()) return
        cancel(); epoch++; ct = id.orEmpty(); record = values.toMap(); originalBase = lan.orEmpty(); base = originalBase
        transformers = profiles?.let { LocalAddressProfiles.count(record, it.transformerFields) }?.toString().orEmpty()
        remotes = profiles?.let { LocalAddressProfiles.count(record, it.remoteFields) }?.toString().orEmpty()
        inventory = emptyMap(); webOverrides = emptyMap(); confirmationSaved = false
        inputSource = profiles?.let { AddressProfileStore.source(it, record, lan) }.orEmpty()
        if (ct.isNotEmpty() && installedProfile != null) {
            profileStore.confirmation(ct, requireNotNull(installedProfile).digest, inputSource)?.let { saved ->
                if (!transformersAutomatic) transformers = saved.getString("transformers")
                if (!remotesAutomatic) remotes = saved.getString("remotes")
                base = saved.getString("base")
                val storedPresence = saved.getJSONObject("presence")
                inventory = storedPresence.keys().asSequence().associateWith { AddressPlan.Presence.valueOf(storedPresence.getString(it)) }
                val storedWeb = saved.getJSONObject("web")
                webOverrides = storedWeb.keys().asSequence().associateWith { key ->
                    val value = storedWeb.getJSONObject(key); value.getString("scheme") to value.getInt("port").toString()
                }
                confirmationSaved = true
            }
        }
        invalidate()
        message = when {
            leaseActive -> "WAN temporal de $leaseCt. Restaúrala antes de diagnosticar otro CT."
            profiles == null -> "Instala el perfil privado una vez en Configuración."
            kind == AddressPlan.Kind.UNKNOWN -> "Falta inventario fiable. Confirma solo los datos necesarios y guárdalos para este CT."
            confirmationSaved -> "Perfil seleccionado automáticamente con datos Grid y confirmación local guardada."
            else -> "Datos del Grid cargados. Perfil seleccionado por topología, independiente del fabricante."
        }
    }
    fun saveConfirmation() {
        val plan = plan() ?: return
        val profile = installedProfile ?: return
        val chosenCt = ct
        val presence = JSONObject(); inventory.forEach { (key, value) -> presence.put(key, value.name) }
        val web = JSONObject()
        for ((key, spec) in webOverrides) {
            val port = spec.second.toIntOrNull()
            if (spec.first !in listOf("http", "https") || port == null || port !in 1..65535) {
                message = "Confirma protocolo y puerto web válidos antes de guardar."; return
            }
            web.put(key, JSONObject().put("scheme", spec.first).put("port", port))
        }
        val value = JSONObject().put("schema", 1).put("profile", profile.digest).put("source", inputSource)
            .put("transformers", transformers).put("remotes", remotes).put("base", plan.base)
            .put("presence", presence).put("web", web)
        task({ profileStore.saveConfirmation(chosenCt, value) }) {
            confirmationSaved = true; message = "Confirmación guardada para este CT. No se volverá a pedir mientras su inventario/base y el perfil sigan vigentes."
        }
    }
    fun invalidate() { epoch++; proposal = null; results = emptyMap(); maintenance = ""; authorized = false; physicallyAbsent = false }
    fun edit(action: () -> Unit) { if (!busy && !leaseActive) { action(); confirmationSaved = false; invalidate() } }
    fun presence(id: String, value: AddressPlan.Presence) { if (!busy && !leaseActive) { inventory = inventory + (id to value); confirmationSaved = false; invalidate() } }
    fun maintenance(value: String) { if (!busy) { maintenance = value.take(15); proposal = null; authorized = false; physicallyAbsent = false } }
    fun cancel() { token?.cancel() }
    private fun <T> task(work: (CancellationToken) -> T, done: (T) -> Unit = {}) {
        if (busy) return
        val generation = epoch
        val cancellation = CancellationToken(); token = cancellation; busy = true
        job = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { work(cancellation) }
                if (generation == epoch && !cancellation.isCancelled) done(result)
            } catch (_: CancellationException) { if (generation == epoch) message = "Operación cancelada." }
            catch (error: Exception) {
                if (generation == epoch) message = when (error) {
                    is WanManager.Failure -> error.message ?: "Error SSH"
                    is IllegalArgumentException -> error.message?.takeIf { it.length <= 160 } ?: "Parámetros sin validar"
                    else -> "Operación no completada. Revisa conexión, permisos y parámetros locales."
                }
            } finally {
                leaseActive = wan.hasLease(); leaseCt = wan.leaseCt()
                if (generation == epoch && cancellation.isCancelled) message = "Operación cancelada. Comprueba el estado WAN antes de continuar."
                if (token === cancellation) { busy = false; token = null }
            }
        }
    }
    fun importProfiles(uri: Uri) = task({
        val text = context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input)
            val bytes = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) { val n = input.read(buffer); if (n < 0) break; require(bytes.size() + n <= 131072); bytes.write(buffer, 0, n) }
            bytes.toString("UTF-8")
        }
        profileStore.install(text)
    }) { loaded ->
        installedProfile = loaded; profiles = loaded.profiles
        val selected = ct; ct = ""
        changeCt(selected, record, originalBase)
    }
    fun inspect() = task({ wan.inspect(it) }) { snapshot = it; message = "WAN comprobada; configuración permanente intacta." }
    fun prepare(host: String) {
        val plan = plan() ?: return
        val ip = maintenance.ifBlank { plan.candidate() }
        maintenance = ip; proposal = null; authorized = false; physicallyAbsent = false
        task({ wan.prepare(plan, ip, host, it) }) {
            proposal = it; snapshot = it.snapshot
            message = when (it.probe) {
                WanManager.Probe.CONFLICT -> "Conflicto conocido: IP ocupada. Aplicación bloqueada."
                WanManager.Probe.CLEAR_ARP -> "Sin duplicado observado por ARP. No demuestra que la IP esté libre: verifica físicamente y autoriza."
                else -> "Ocupación no concluyente. Debes verificar físicamente la ausencia de otro equipo y autorizar el uso."
            }
        }
    }
    fun apply(host: String) {
        val prepared = proposal ?: return
        if (prepared.plan.ct != ct || plan()?.base != prepared.plan.base) { proposal = null; return }
        val allow = authorized; val absent = physicallyAbsent
        task({ wan.apply(prepared, host, allow, absent, it) }) { proposal = null; message = "WAN temporal aplicada. Restauración automática de seguridad en 10 minutos; restaura antes de salir." }
    }
    fun restore() = task({ wan.restore(it) }) { proposal = null; snapshot = null; results = emptyMap(); message = "Alias temporal retirado; direcciones y rutas anteriores conservadas." }
    fun ping(only: String? = null, expectedCt: String? = ct) {
        if (expectedCt != ct) return
        val plan = plan() ?: return
        if (leaseActive && leaseCt != ct) { message = "Restaura la WAN de la matrícula anterior."; return }
        val source = if (direct) DiagnosticSource.DIRECT_ANDROID else DiagnosticSource.MANGO_SSH
        val generation = ++epoch
        results = if (only == null) emptyMap() else results - only
        val services = plan.profile.services.filter { only == null || it.id == only }
        task({ cancellation ->
            for ((index, service) in services.withIndex()) {
                if (cancellation.isCancelled) break
                scope.launch(Dispatchers.Main) { if (generation == epoch) message = "Comprobando ${service.label} · ${index + 1}/${services.size}" }
                val address = plan.address(service)
                val status = plan.presence(service)
                val result = when (status) {
                    AddressPlan.Presence.ABSENT -> DiagnosticResult(service.label, address, source, DiagnosticStatus.NOT_CONFIGURED, 0, null)
                    AddressPlan.Presence.UNKNOWN -> DiagnosticResult(service.label, address, source, DiagnosticStatus.UNKNOWN, 0, null)
                    else -> if (source == DiagnosticSource.DIRECT_ANDROID) LocalDiagnostic.check(service.label, address, cancellation)
                        else transport.diagnostic("ping -c 1 -W 2 $address", 5000, cancellation, null).forTarget(service.label, address)
                }
                scope.launch(Dispatchers.Main) { if (generation == epoch) results = results + (service.id to result) }
            }
            if (cancellation.isCancelled) scope.launch(Dispatchers.Main) {
                if (generation == epoch) results = results + services.filter { it.id !in results }.associate { service ->
                    service.id to DiagnosticResult(service.label, plan.address(service), source, DiagnosticStatus.CANCELLED, 0, null)
                }
            }
        }) { message = "Diagnóstico finalizado. Sin respuesta ICMP no significa desconectado." }
    }
    fun disconnect() = task({
        if (transport.connected()) { wan.inspect(it); if (wan.hasLease()) wan.restore(it) }
        transport.disconnect()
    }) { snapshot = null; results = emptyMap(); message = "Mango desconectado; WAN temporal restaurada." }
    fun dispose() {
        cancel()
        // Retain transport until bounded recovery finishes; remote lease remains the crash fallback.
        CoroutineScope(Dispatchers.IO).launch { runCatching { wan.restore(CancellationToken()) }; transport.disconnect() }
    }
}
