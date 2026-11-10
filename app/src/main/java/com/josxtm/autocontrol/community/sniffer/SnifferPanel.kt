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
    } catch (_: IllegalArgumentException) { null } catch (_: NumberFormatException) { null }

@Composable
fun SnifferPanel(modifier: Modifier = Modifier, selectedCt: String? = null, selectedLan: String? = null) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val transport = remember { SshTransport(context.getSharedPreferences("sniffer-known-hosts", 0)) }
    var tab by remember { mutableIntStateOf(0) }
    var base by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var user by remember { mutableStateOf("") }
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
    DisposableEffect(transport) {
        onDispose { scanJob?.cancel(); transport.disconnect() }
    }
    LaunchedEffect(selectedCt, selectedLan) {
        scanJob?.cancel()
        scanRunning = false
        results = emptyMap()
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

    Column(modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("SNIFFER LAN · MangoSSH integrado", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Escaneo", "Pings", "Equipos", "MangoSSH").forEachIndexed { i, label ->
                if (tab == i) Button(onClick = { tab = i }) { Text(label) }
                else OutlinedButton(onClick = { tab = i }) { Text(label) }
            }
        }
        if (tab == 3) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(.26f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(host, { host = it.take(253) }, label = { Text("Host SSH") }, singleLine = true)
                    OutlinedTextField(port, { port = it.take(5) }, label = { Text("Puerto") }, singleLine = true)
                    OutlinedTextField(user, { user = it.take(128) }, label = { Text("Usuario") }, singleLine = true)
                    OutlinedTextField(password, { password = it.take(256) }, label = { Text("Contraseña temporal") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    Button(onClick = ::connect, enabled = !active && !transport.connected()) { Text("Conectar") }
                    OutlinedButton(onClick = { scope.launch(Dispatchers.IO) { transport.disconnect(); withContext(Dispatchers.Main) { shell = false; status = "Desconectado" } } }) { Text("Desconectar") }
                    Text(status)
                    Text("Comprueba siempre la huella del servidor.", style = MaterialTheme.typography.bodySmall)
                }
                Column(Modifier.weight(.51f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Consola SSH", style = MaterialTheme.typography.titleMedium)
                    Surface(Modifier.weight(1f).fillMaxWidth(), color = androidx.compose.ui.graphics.Color(0xFF20252A)) {
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(10.dp)) {
                            Text(output.ifBlank { "Sin salida" }, color = androidx.compose.ui.graphics.Color(0xFFF1F1F1), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(command, { command = it.take(2048) }, modifier = Modifier.weight(1f), label = { Text("Comando") }, singleLine = true)
                        Button(onClick = {
                            val cmd = command; command = ""; history.add(cmd)
                            if (history.size > 20) history.removeAt(0)
                            scope.launch { try { withContext(Dispatchers.IO) { transport.send(cmd) } } catch (_: Exception) { status = "Error al enviar comando" } }
                        }, enabled = shell && command.isNotBlank()) { Text("Enviar") }
                    }
                }
                Column(Modifier.weight(.23f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Comandos rápidos")
                    listOf("whoami", "pwd", "date", "uname -a", "df -h").forEach { cmd ->
                        OutlinedButton(onClick = { diagnostic(cmd) }, enabled = transport.connected(), modifier = Modifier.fillMaxWidth()) { Text(cmd) }
                    }
                    OutlinedButton(onClick = { askShell = true }, enabled = transport.connected() && !shell) { Text("Activar terminal") }
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(output)) }, enabled = output.isNotEmpty()) { Text("Copiar salida") }
                    OutlinedButton(onClick = { output = ""; history.clear() }) { Text("Limpiar salida") }
                    OutlinedButton(onClick = { historyVisible = !historyVisible }) { Text(if (historyVisible) "Ocultar historial" else "Ver historial") }

                    Text("Historial de sesión", style = MaterialTheme.typography.titleSmall)
                    if (historyVisible) history.forEach { previous ->
                        OutlinedButton(onClick = { command = previous }, modifier = Modifier.fillMaxWidth()) {
                            Text(previous.take(48), style = MaterialTheme.typography.bodySmall)
                        }
                    }

                }
            }
        } else {
            Text(event, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(base, { base = it.take(18); results = emptyMap() }, label = { Text("IP LAN base del CT (IPv4 o CIDR)") }, singleLine = true)
            if (tab == 0 || tab == 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val requestedBase = base
                    results = emptyMap()
                    scanRunning = true
                    scanJob = scope.launch {
                        try {
                            for ((name, offset) in targets) {
                                ensureActive()
                                val address = ipAt(requestedBase, offset)
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
            LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(targets) { (name, offset) ->
                    val computed = ipAt(base, offset) ?: "—"
                    ListItem(headlineContent = { Text(name) },
                        supportingContent = { Text(results[name] ?: computed) })
                }
            }
            Text("La falta de respuesta ICMP no confirma desconexión. No se realizan acciones de telemando.", style = MaterialTheme.typography.bodySmall)
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
