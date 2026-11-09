package com.josxtm.autocontrol.community.sniffer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

private fun ipAt(base: String, offset: Int): String? {
    val parts = base.trim().split(".")
    if (parts.size != 4) return null
    val bytes = parts.map { it.toIntOrNull()?.takeIf { x -> x in 0..255 } ?: return null }
    val n = bytes[3] + offset
    if (n > 255) return null // Never silently cross a subnet without its mask
    return bytes.take(3).joinToString(".") + "." + n
}

@Composable
fun SnifferPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
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
    val history = remember { mutableStateListOf<String>() }
    DisposableEffect(transport) { onDispose { transport.disconnect() } }

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
                    OutlinedButton(onClick = { output = ""; history.clear() }) { Text("Limpiar salida") }
                    Text("Historial de sesión", style = MaterialTheme.typography.titleSmall)
                    history.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        } else {
            OutlinedTextField(base, { base = it.take(15) }, label = { Text("IP LAN base del CT · introducir manualmente") }, singleLine = true)
            if (tab == 0 || tab == 1) Button(onClick = {
                val ip = base
                scanRunning = true
                scope.launch {
                    val newResults = withContext(Dispatchers.IO) {
                        targets.associate { (name, offset) ->
                            val address = ipAt(ip, offset)
                            name to if (address == null) "IP no válida" else try {
                                if (InetAddress.getByName(address).isReachable(900)) "$address · responde"
                                else "$address · sin respuesta"
                            } catch (_: Exception) { "$address · error de diagnóstico" }
                        }
                    }
                    results = newResults; scanRunning = false
                }
            }, enabled = !scanRunning && ipAt(base, 14) != null) { Text(if (scanRunning) "Escaneando…" else "Iniciar comprobación") }
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
