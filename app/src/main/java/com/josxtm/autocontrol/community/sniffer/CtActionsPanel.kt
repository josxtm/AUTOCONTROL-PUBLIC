package com.josxtm.autocontrol.community.sniffer

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Compact functional panels using the app's existing Material theme. */
@Composable internal fun CtActionsPanel(state: CtActionState, host: String, connected: Boolean,
    connection: @Composable () -> Unit) {
    val context = LocalContext.current
    var confirmation by remember { mutableStateOf("") }
    var pendingProfile by remember { mutableStateOf<Uri?>(null) }
    var webError by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { pendingProfile = it }
    val plan = state.plan()
    val enabled = !state.busy
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(state.message, style = MaterialTheme.typography.bodySmall)
        if (state.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = state::cancel) { Text("Cancelar operación") }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(.29f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("CT seleccionado", style = MaterialTheme.typography.titleMedium)
                Text(state.ct.ifBlank { "Selecciona una matrícula en CT" })
                state.record.entries.firstOrNull { LocalAddressProfiles.key(it.key) == "NOMBRE" }?.value?.let { Text(it) }
                OutlinedTextField(state.transformers, { v -> state.edit { state.transformers = v.take(3) } }, label = { Text("Transformadores confirmados") }, enabled = enabled, singleLine = true)
                OutlinedTextField(state.remotes, { v -> state.edit { state.remotes = v.take(3) } }, label = { Text("Remotas confirmadas") }, enabled = enabled, singleLine = true)
                Text(when (state.kind) {
                    AddressPlan.Kind.NORMAL -> "Normal /28"
                    AddressPlan.Kind.EXTENDED -> "Extendido /26 · confirmar perfil local"
                    else -> "Inventario incompleto: perfil sin confirmar"
                })
                OutlinedTextField(state.base, { v -> state.edit { state.base = v.take(18) } }, label = { Text("IP LAN base del CT") }, enabled = enabled, singleLine = true)
                Text("Máscara: ${plan?.mask() ?: "sin validar"}")
                Text("Perfil: ${plan?.profile?.revision ?: "pendiente de importar y validar"}", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = enabled && !state.leaseActive) { Text("Importar perfil privado") }
                Text("Las posiciones sin servicio no se consideran libres. Los perfiles no se incluyen en APK ni copias del catálogo.", style = MaterialTheme.typography.bodySmall)
            }
            Column(Modifier.weight(.33f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Mango · WAN temporal", style = MaterialTheme.typography.titleMedium)
                connection()
                Text(if (connected) "SSH autenticado" else "Sin conexión SSH")
                Button(onClick = state::inspect, enabled = enabled && connected) { Text("Comprobar WAN") }
                state.snapshot?.let { snapshot ->
                    Text("WAN: ${snapshot.device} · enlace ${if (snapshot.carrier) "activo" else "sin confirmar"}")
                    Text("IPv4: ${snapshot.wanAddresses.joinToString().ifBlank { "ninguna" }}", style = MaterialTheme.typography.bodySmall)
                    Text("LAN de gestión: ${snapshot.lanAddresses.joinToString()}", style = MaterialTheme.typography.bodySmall)
                    Text("Rutas WAN: ${snapshot.routes.ifBlank { "ninguna" }}", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(state.maintenance, state::maintenance, label = { Text("IP de mantenimiento autorizada") },
                    placeholder = { Text(plan?.candidate().orEmpty()) }, enabled = enabled && !state.leaseActive, singleLine = true)
                Button(onClick = { state.prepare(host.trim()) }, enabled = enabled && connected && plan != null && !state.leaseActive) { Text("Preparar WAN de mantenimiento") }
                state.proposal?.let { proposal ->
                    Text("Aplicar ${proposal.ip}/${proposal.plan.profile.prefix} a ${proposal.snapshot.device}; sin gateway ni cambios UCI.")
                    Row { Checkbox(state.authorized, { state.authorized = it }, enabled = enabled); Text("Uso temporal autorizado") }
                    Row { Checkbox(state.physicallyAbsent, { state.physicallyAbsent = it }, enabled = enabled); Text("He verificado físicamente que ningún otro equipo utiliza esta IP") }
                    Button(onClick = { confirmation = "apply" }, enabled = enabled && state.authorized && state.physicallyAbsent && proposal.probe != WanManager.Probe.CONFLICT) { Text("Aplicar WAN") }
                }
                if (state.leaseActive) Text("WAN temporal de ${state.leaseCt}. Caducidad remota de seguridad: 10 min; comprobar WAN si cambia el enlace.")
                OutlinedButton(onClick = { confirmation = "restore" }, enabled = enabled && connected) { Text("Restaurar WAN anterior") }
                Text("Solo se añade una IPv4 temporal; se conservan las direcciones previas, LAN/Wi-Fi y gateways. No responder al ping no prueba una IP libre.", style = MaterialTheme.typography.bodySmall)
            }
            Column(Modifier.weight(.38f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Equipos", style = MaterialTheme.typography.titleMedium)
                Row { Checkbox(state.direct, { state.direct = it }, enabled = enabled); Text("Comprobar desde Android") }
                Button(onClick = { state.ping(expectedCt = plan?.ct) }, enabled = enabled && plan != null && (state.direct || connected) && (!state.leaseActive || state.leaseCt == state.ct)) { Text("Comprobar todos") }
                if (plan == null) Text("No se calculan direcciones sin perfil, inventario y base alineada válidos.")
                plan?.profile?.services?.forEach { service ->
                    val presence = plan.presence(service)
                    val result = state.results[service.id]
                    val color = when (result?.status) {
                        DiagnosticStatus.VALID_RESPONSE -> Color(0xFF1C7839)
                        DiagnosticStatus.COMMAND_ERROR, DiagnosticStatus.LOCAL_ERROR -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    HorizontalDivider()
                    Text("${service.label} · ${plan.address(service)}", style = MaterialTheme.typography.titleSmall)
                    Text(result?.let { "${it.label()} · total ${it.elapsedMillis} ms" } ?: when (presence) {
                        AddressPlan.Presence.ABSENT -> "No instalado"
                        AddressPlan.Presence.UNKNOWN -> "Instalación sin confirmar"
                        else -> "Instalado · sin comprobar"
                    }, color = color, style = MaterialTheme.typography.bodySmall)
                    Row {
                        listOf(AddressPlan.Presence.INSTALLED to "Sí", AddressPlan.Presence.ABSENT to "No", AddressPlan.Presence.UNKNOWN to "?").forEach { (value, label) ->
                            TextButton(onClick = { state.presence(service.id, value) }, enabled = enabled) { Text(if (presence == value) "● $label" else label) }
                        }
                    }
                    val webSpec = state.webOverrides[service.id] ?: (service.scheme to if (service.scheme.isBlank()) "" else service.port.toString())
                    val webService = state.webService(service)
                    Row {
                        listOf("http", "https").forEach { scheme ->
                            TextButton(onClick = { state.configureWeb(service, scheme, webSpec.second) }, enabled = enabled) { Text(if (webSpec.first == scheme) "● ${scheme.uppercase()}" else scheme.uppercase()) }
                        }
                    }
                    OutlinedTextField(webSpec.second, { state.configureWeb(service, webSpec.first, it) }, label = { Text("Puerto web confirmado") }, enabled = enabled, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = { state.ping(service.id, plan.ct) }, enabled = enabled && presence == AddressPlan.Presence.INSTALLED && (state.direct || connected) && (!state.leaseActive || state.leaseCt == state.ct)) { Text("Ping") }
                        OutlinedButton(onClick = {
                            runCatching { val live = requireNotNull(state.plan())
                                require(live.ct == plan.ct && live.base == plan.base && live.profile.revision == plan.profile.revision)
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(live.web(requireNotNull(state.webService(service))).toASCIIString()))) }
                                .onFailure { webError = "No se pudo abrir el navegador para este destino validado." }
                        }, enabled = enabled && presence == AddressPlan.Presence.INSTALLED && webService != null && (!state.leaseActive || state.leaseCt == state.ct)) { Text("Abrir web") }
                    }
                }
                if (webError.isNotEmpty()) Text(webError)
            }
        }
    }
    pendingProfile?.let { uri ->
        AlertDialog(onDismissRequest = { pendingProfile = null }, title = { Text("Perfil privado autorizado") },
            text = { Text("Confirma que esta configuración procede de documentación autorizada y que has revisado sus asignaciones, reservas y protocolos web. Se guarda únicamente en esta aplicación.") },
            confirmButton = { TextButton(onClick = { pendingProfile = null; state.importProfiles(uri) }) { Text("Importar perfil revisado") } },
            dismissButton = { TextButton(onClick = { pendingProfile = null }) { Text("Cancelar") } })
    }
    if (confirmation.isNotEmpty()) AlertDialog(onDismissRequest = { confirmation = "" }, title = { Text(if (confirmation == "apply") "Confirmar WAN temporal" else "Restaurar WAN") },
        text = { Text(if (confirmation == "apply") "Se volverán a comprobar interfaz, gestión y ocupación. Se añadirá únicamente la IP mostrada a la WAN del Mango. Confirma autorización y ausencia de otro equipo con esa IP." else "Se retirará únicamente el alias creado por esta sesión. No se tocarán las direcciones ni los gateways anteriores.") },
        confirmButton = { TextButton(onClick = { val action = confirmation; confirmation = ""; if (action == "apply") state.apply(host.trim()) else state.restore() }) { Text("Confirmar") } },
        dismissButton = { TextButton(onClick = { confirmation = "" }) { Text("Cancelar") } })
}
