package com.josxtm.autocontrol.community.sniffer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import java.net.InetAddress

private val targets = listOf("Router" to 1, "CC T1" to 2, "CC T2" to 3, "SABT T2" to 9, "SABT T1" to 11, "FFAA1" to 13, "Remota" to 14)

private fun ipAt(base: String, offset: Int): String? =
    try {
        val address = base.trim().substringBefore("/")
        val prefix = base.trim().substringAfter("/", "24").toInt()
        LanTargets.at(address, prefix, offset)
    } catch (_: IllegalArgumentException) { null }

@Composable
fun SnifferPanel(modifier: Modifier = Modifier, selectedCt: String? = null, selectedLan: String? = null) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val profile = remember { context.getSharedPreferences("sniffer-ssh-profile", 0) }
    val scope = rememberCoroutineScope()
    val transport = remember { SshTransport(context.getSharedPreferences("sniffer-known-hosts", 0)) }
    var tab by remember { mutableIntStateOf(0) }
    var base by remember { mutableStateOf("") }
    var host by remember { mutableStateOf(profile.getString("host", "").orEmpty()) }
    var port by remember { mutableStateOf(profile.getString("port", "22").orEmpty()) }
    var user by remember { mutableStateOf(profile.getString("user", "").orEmpty()) }
    var password by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Sin conexión SSH") }
    var output by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(false) }
    var shell by remember { mutableStateOf(false) }
    var askShell by remember { mutableStateOf(false) }
    var approval by remember { mutableStateOf<Pair<String, CompletableDeferred<Boolean>>?>(null) }
    var results by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var scanRunning by remember { mutableStateOf(false) }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var historyVisible by remember { mutableStateOf(false) }
    var event by remember { mutableStateOf("Sin diagnósticos") }
    val history = remember { mutableStateListOf<String>() }
    val notConfigured = remember { mutableStateMapOf<String, Boolean>() }
    DisposableEffect(transport) {
        onDispose { scanJob?.cancel(); transport.disconnect() }
    }
    LaunchedEffect(selectedCt, selectedLan) {
        scanJob?.cancel()
        scanRunning = false
        results = emptyMap()
        notConfigured.clear()
        base = selectedLan?.trim().orEmpty()
        event = if (selectedCt == null) "Selecciona un CT en Registros, o introduce la red manualmente."
            else if (selectedLan.isNullOrBlank()) "El CT seleccionado no contiene IP LAN: introduce una base manual."
            else "Red cargada del CT seleccionado. Comprueba su máscara antes del escaneo."
    }


    fun append(text: String) { output = (output + text).takeLast(20000) }
    fun connect() {
        if (active) return
        val p = port.toIntOrNull()
        if (p == null || !EndpointPolicy.valid(host.trim(), p, user.trim())) {
            status = "Host, puerto o usuario incorrectos"; return
        }
        val secret = password.toCharArray()
        password = ""
        active = true
        status = "Conectando…"
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    transport.connect(host.trim(), p, user.trim(), secret) { endpoint, algorithm, fingerprint ->
                        runBlocking {
                            val answer = CompletableDeferred<Boolean>()
                            withContext(Dispatchers.Main) {
                                approval = ("Servidor: $endpoint · $algorithm\nHuella: $fingerprint\nVerifica fuera de banda antes de aceptar.") to answer
                            }
                            val accepted = withTimeoutOrNull(60000) { answer.await() } ?: false
                            withContext(Dispatchers.Main) { approval = null }
                            accepted
                        }
                    }
                }
                status = "SSH conectado · huella verificada"
            } catch (e: Exception) {
                status = "Conexión fallida: " + e.javaClass.simpleName
            } finally { secret.fill('\u0000'); active = false }
        }
    }
    fun diagnostic(cmd: String) {
        scope.launch {
            try { val result = withContext(Dispatchers.IO) { transport.diagnostic(cmd) }; append("\n> $cmd\n$result\n") }
            catch (_: Exception) { status = "Diagnóstico no disponible" }
        }
    }
    fun openShell() {
        askShell = false
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    transport.openShell { chunk -> scope.launch { append(chunk) } }
                }
                shell = true
                status = "Terminal interactiva activa"
            } catch (_: Exception) { status = "No se pudo abrir la terminal" }
        }
    }

    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    @Composable
    fun ScanControls() {
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val requestedBase = base
                results = emptyMap()
                scanRunning = true
                scanJob = scope.launch {
                    try {
                        for ((name, offset) in targets) {
                            ensureActive()
                            val address = ipAt(requestedBase, offset)
                            if (notConfigured[name] == true) {
                                results = results + (name to "No configurado")
                                continue
                            }
                            val result = withContext(Dispatchers.IO) {
                                if (address == null) "IP no válida" else try {
                                    if (InetAddress.getByName(address).isReachable(900)) "$address · responde"
                                    else "$address · sin respuesta"
                                } catch (_: Exception) { "$address · error de diagnóstico" }
                            }
                            ensureActive()
                            results = results + (name to result)
                        }
                        event = "Escaneo completo: " + results.size + " equipos comprobados."
                    } catch (_: CancellationException) {
                        event = "Escaneo cancelado."
                    } finally { scanRunning = false }
                }
            }, enabled = !scanRunning && ipAt(base, 14) != null) { Text(if (scanRunning) "Escaneando…" else "Iniciar comprobación") }
            OutlinedButton(onClick = {
                val requestedBase = base
                results = emptyMap()
                scanRunning = true
                scanJob = scope.launch {
                    try {
                        for ((name, offset) in targets) {
                            ensureActive()
                            val address = ipAt(requestedBase, offset)
                            if (notConfigured[name] == true) {
                                results = results + (name to "No configurado")
                                continue
                            }
                            val result = if (address == null) "IP no válida" else try {
                                val response = withContext(Dispatchers.IO) {
                                    transport.diagnostic("ping -c 1 -W 2 $address")
                                }
                                val reachable = response.contains("0% packet loss") ||
                                    response.contains("1 packets received") ||
                                    response.contains("1 received")
                                if (reachable) "$address · responde"
                                else "$address · sin respuesta"
                            } catch (_: CancellationException) {
                                throw CancellationException()
                            } catch (_: Exception) { "$address · error de diagnóstico" }
                            ensureActive()
                            results = results + (name to result)
                        }
                        event = "Diagnóstico desde Mango finalizado: " + results.size + " equipos."
                    } catch (_: CancellationException) {
                        event = "Diagnóstico desde Mango cancelado."
                    } finally { scanRunning = false }
                }
            }, enabled = !scanRunning && transport.connected() && ipAt(base, 14) != null) {
                Text("Ping desde Mango")
            }
            OutlinedButton(onClick = { scanJob?.cancel() }, enabled = scanRunning) { Text("Cancelar") }
        }
    }


    @Composable
    fun MangoPane(paneModifier: Modifier) {
        Card(paneModifier) {
            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("MangoSSH · Terminal", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(host, { host = it.take(253) }, Modifier.weight(1.5f),
                        label = { Text("Host SSH") }, singleLine = true)
                    OutlinedTextField(port, { port = it.take(5) }, Modifier.weight(.65f),
                        label = { Text("Puerto") }, singleLine = true)
                    OutlinedTextField(user, { user = it.take(128) }, Modifier.weight(1f),
                        label = { Text("Usuario") }, singleLine = true)
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(password, { password = it.take(256) }, Modifier.weight(1f),
                        label = { Text("Clave temporal") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation())
                    Button(onClick = ::connect, enabled = !active && !transport.connected()) { Text("Conectar") }
                    OutlinedButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            transport.disconnect()
                            withContext(Dispatchers.Main) { shell = false; status = "Desconectado" }
                        }
                    }) { Text("Salir") }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(status, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = muted)
                    TextButton(onClick = {
                        val p = port.toIntOrNull()
                        if (p != null && EndpointPolicy.valid(host.trim(), p, user.trim())) {
                            profile.edit().putString("host", host.trim()).putString("port", port.trim())
                                .putString("user", user.trim()).apply()
                            status = "Perfil guardado (sin contraseña)."
                        } else status = "Perfil SSH incorrecto"
                    }) { Text("Guardar perfil") }
                }
                Surface(Modifier.fillMaxWidth().weight(1f), color = androidx.compose.ui.graphics.Color(0xFF20252A),
                    shape = MaterialTheme.shapes.small) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(9.dp)) {
                        Text(output.ifBlank { "Conecta al Mango y verifica su huella SSH." },
                            color = androidx.compose.ui.graphics.Color(0xFFE9F2ED),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    OutlinedTextField(command, { command = it.take(2048) }, Modifier.weight(1f),
                        label = { Text("Comando SSH") }, singleLine = true)
                    Button(onClick = {
                        val cmd = command
                        command = ""
                        if (CommandHistoryPolicy.rememberable(cmd)) history.add(cmd)
                        if (history.size > 20) history.removeAt(0)
                        scope.launch {
                            try { withContext(Dispatchers.IO) { transport.send(cmd) } }
                            catch (_: Exception) { status = "Error al enviar comando" }
                        }
                    }, enabled = shell && command.isNotBlank()) { Text("Enviar") }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("whoami", "pwd", "date", "uname -a", "df -h").forEach { cmd ->
                        OutlinedButton(onClick = { diagnostic(cmd) }, enabled = transport.connected(),
                            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 4.dp)) {
                            Text(cmd, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { askShell = true }, enabled = transport.connected() && !shell) { Text("Activar terminal") }
                    TextButton(onClick = { clipboard.setText(AnnotatedString(output)) }, enabled = output.isNotEmpty()) { Text("Copiar") }
                    TextButton(onClick = { output = ""; history.clear() }) { Text("Limpiar") }
                    TextButton(onClick = { historyVisible = !historyVisible }) {
                        Text(if (historyVisible) "Ocultar historial" else "Historial (" + history.size + ")")
                    }
                }
                if (historyVisible) Column(Modifier.heightIn(max = 100.dp).verticalScroll(rememberScrollState())) {
                    history.forEach { previous ->
                        TextButton(onClick = { command = previous }) { Text(previous.take(120)) }
                    }
                }
            }
        }
    }


    // Definitive GUI B: devices (left), diagnostics (center), integrated MangoSSH (right).
    // Retain the tested SSH transport, manual host key approval and scan implementations.
    val green = androidx.compose.ui.graphics.Color(0xFF247746)
    val red = androidx.compose.ui.graphics.Color(0xFFAB343A)
    val goodCount = results.values.count { it.endsWith("· responde") }
    Column(modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Text("SNIFFER LAN", style = MaterialTheme.typography.titleMedium)
            Text("Diagnóstico técnico · MangoSSH integrado", style = MaterialTheme.typography.bodySmall, color = muted)
            Spacer(Modifier.weight(1f))
            Text(if (transport.connected()) "● SSH conectado" else "○ SSH desconectado",
                style = MaterialTheme.typography.labelMedium,
                color = if (transport.connected()) green else muted)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(base, { base = it.take(18); results = emptyMap() },
                modifier = Modifier.weight(1f), label = { Text("Base LAN CT (IPv4/CIDR)") }, singleLine = true)
            Text((selectedCt ?: "CT manual").take(22), style = MaterialTheme.typography.labelSmall)
            Text("OK " + goodCount + "/" + targets.size, style = MaterialTheme.typography.labelMedium, color = green)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 800.dp
            if (wide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Card(Modifier.weight(.24f).fillMaxHeight()) {
                        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Equipos del CT", style = MaterialTheme.typography.titleSmall)
                            Text("Marca solo los no instalados", style = MaterialTheme.typography.labelSmall, color = muted)
                            HorizontalDivider()
                            targets.forEach { (name, offset) ->
                                val noInstall = notConfigured[name] == true
                                val response = results[name]
                                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text(name, style = MaterialTheme.typography.labelMedium)
                                        Text("LAN +" + offset + " · " + (ipAt(base, offset) ?: "—"),
                                            style = MaterialTheme.typography.labelSmall, color = muted)
                                    }
                                    Checkbox(checked = noInstall, onCheckedChange = { notConfigured[name] = it; results = results - name })
                                }
                                if (response != null) Text(response.substringAfter(" · ", response),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (response.endsWith("· responde")) green else red)
                                HorizontalDivider()
                            }
                        }
                    }
                    Column(Modifier.weight(.34f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Card(Modifier.fillMaxWidth().weight(.68f)) {
                            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("Diagnóstico LAN", style = MaterialTheme.typography.titleSmall)
                                Text(event.take(150), style = MaterialTheme.typography.labelSmall, color = muted)
                                HorizontalDivider()
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("Equipo", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                                    Text("IP", Modifier.weight(1.3f), style = MaterialTheme.typography.labelSmall)
                                    Text("Estado", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                                }
                                targets.forEach { (name, offset) ->
                                    val result = results[name]
                                    val state = when {
                                        notConfigured[name] == true -> "No instalado"
                                        result == null -> "Pendiente"
                                        result.endsWith("· responde") -> "Responde"
                                        result.endsWith("· sin respuesta") -> "Sin respuesta"
                                        else -> "Error"
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                        Text(ipAt(base, offset) ?: "—", Modifier.weight(1.3f), style = MaterialTheme.typography.bodySmall)
                                        Text(state, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                                            color = if (state == "Responde") green else if (state == "Sin respuesta") red else muted)
                                    }
                                }
                                Spacer(Modifier.height(5.dp))
                                Text("Sin respuesta ICMP ≠ equipo desconectado.",
                                    color = muted, style = MaterialTheme.typography.labelSmall)
                                ScanControls()
                            }
                        }
                        Card(Modifier.fillMaxWidth().weight(.32f)) {
                            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Registro de eventos", style = MaterialTheme.typography.titleSmall)
                                Text(event, style = MaterialTheme.typography.bodySmall)
                                Text("SSH: " + status, style = MaterialTheme.typography.bodySmall)
                                Text("Diagnóstico de lectura · sin telemandos", style = MaterialTheme.typography.labelSmall, color = muted)
                            }
                        }
                    }
                    MangoPane(Modifier.weight(.42f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Modo compacto", style = MaterialTheme.typography.titleSmall)
                    ScanControls()
                    targets.forEach { (name, offset) ->
                        val result = results[name] ?: "Pendiente"
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(name + " · " + (ipAt(base, offset) ?: "—") + " · " + result, Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall)
                            Checkbox(checked = notConfigured[name] == true,
                                onCheckedChange = { notConfigured[name] = it; results = results - name })
                        }
                    }
                    MangoPane(Modifier.fillMaxWidth().height(610.dp))
                }
            }
        }
    }
    approval?.let { (prompt, deferred) ->
        AlertDialog(onDismissRequest = { if (!deferred.isCompleted) deferred.complete(false); approval = null },
            title = { Text("Identidad del servidor SSH") }, text = { Text(prompt) },
            confirmButton = { TextButton(onClick = { if (!deferred.isCompleted) deferred.complete(true); approval = null }) { Text("Huella verificada") } },
            dismissButton = { TextButton(onClick = { if (!deferred.isCompleted) deferred.complete(false); approval = null }) { Text("Rechazar") } })
    }
    if (askShell) AlertDialog(onDismissRequest = { askShell = false },
        title = { Text("Terminal con privilegios remotos") },
        text = { Text("Los comandos pueden modificar el dispositivo remoto. Activa la terminal solo en equipos autorizados.") },
        confirmButton = { TextButton(onClick = ::openShell) { Text("Activar") } },
        dismissButton = { TextButton(onClick = { askShell = false }) { Text("Cancelar") } })
}
