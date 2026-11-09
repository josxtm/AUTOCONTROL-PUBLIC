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
import androidx.compose.ui.Alignment
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


    // Approved concept B: equipment | diagnostic table | MangoSSH.
    // SSH transport, host fingerprint confirmation and command policy are unchanged.
    fun scanLan() {
        if (scanRunning || ipAt(base, 14) == null) return
        val localBase = base
        scanRunning = true
        scope.launch {
            try {
                results = withContext(Dispatchers.IO) {
                    targets.associate { (name, offset) ->
                        val ip = ipAt(localBase, offset)
                        name to if (ip == null) "IP no válida" else try {
                            if (InetAddress.getByName(ip).isReachable(900)) "$ip · responde"
                            else "$ip · sin respuesta"
                        } catch (_: Exception) { "$ip · error de diagnóstico" }
                    }
                }
            } finally { scanRunning = false }
        }
    }
    val green = androidx.compose.ui.graphics.Color(0xFF287A47)
    val red = androidx.compose.ui.graphics.Color(0xFFB23439)
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("SNIFFER LAN", style = MaterialTheme.typography.titleMedium)
            Text("Diagnóstico técnico + MangoSSH", style = MaterialTheme.typography.bodySmall, color = secondary)
            Spacer(Modifier.weight(1f))
            Text(if (transport.connected()) "SSH conectado" else "SSH desconectado",
                style = MaterialTheme.typography.labelMedium,
                color = if (transport.connected()) green else secondary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(base, { base = it.take(15); results = emptyMap() },
                modifier = Modifier.weight(1f), label = { Text("IP LAN base del CT") }, singleLine = true)
            Button(onClick = ::scanLan, enabled = !scanRunning && ipAt(base, 14) != null) {
                Text(if (scanRunning) "Escaneando…" else "Escaneo LAN")
            }
            Text("Comprobados: " + results.size + "/7", style = MaterialTheme.typography.labelSmall)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            if (maxWidth >= 900.dp) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(Modifier.weight(.23f).fillMaxHeight()) {
                        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Equipos del CT", style = MaterialTheme.typography.titleSmall)
                            targets.forEach { (name, offset) ->
                                HorizontalDivider()
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text(name, style = MaterialTheme.typography.labelLarge)
                                        Text("LAN + " + offset, style = MaterialTheme.typography.labelSmall, color = secondary)
                                    }
                                    val state = results[name]
                                    Text(if (state == null) "○" else if (state.endsWith("· responde")) "●" else "!",
                                        color = if (state?.endsWith("· responde") == true) green else if (state == null) secondary else red)
                                }
                            }
                        }
                    }
                    Column(Modifier.weight(.32f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Card(Modifier.fillMaxWidth().weight(.67f)) {
                            Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Diagnóstico de comunicación", style = MaterialTheme.typography.titleSmall)
                                Row(Modifier.fillMaxWidth()) {
                                    Text("Equipo", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                                    Text("IP", Modifier.weight(1.3f), style = MaterialTheme.typography.labelSmall)
                                    Text("Estado", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                                }
                                HorizontalDivider()
                                targets.forEach { (name, offset) ->
                                    val result = results[name]
                                    val good = result?.endsWith("· responde") == true
                                    val state = if (result == null) "Pendiente" else if (good) "Responde"
                                        else if (result.endsWith("· sin respuesta")) "Sin respuesta" else "Error"
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                        Text(ipAt(base, offset) ?: "—", Modifier.weight(1.3f), style = MaterialTheme.typography.bodySmall)
                                        Text(state, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                                            color = if (good) green else if (result == null) secondary else red)
                                    }
                                }
                                Text("Sin respuesta ICMP no implica desconexión confirmada.",
                                    style = MaterialTheme.typography.labelSmall, color = secondary)
                            }
                        }
                        Card(Modifier.fillMaxWidth().weight(.33f)) {
                            Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Registro de eventos", style = MaterialTheme.typography.titleSmall)
                                Text(if (scanRunning) "Escaneo en curso…" else
                                    if (results.isEmpty()) "Todavía no hay resultados de escaneo."
                                    else "Resultados: " + results.values.count { it.endsWith("· responde") } + " respuestas.",
                                    style = MaterialTheme.typography.bodySmall)
                                Text("SSH: " + status, style = MaterialTheme.typography.bodySmall)
                                Text("Sin telemandos automáticos.", style = MaterialTheme.typography.labelSmall, color = secondary)
                            }
                        }
                    }
                    Card(Modifier.weight(.45f).fillMaxHeight()) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("MangoSSH — Terminal integrado", style = MaterialTheme.typography.titleSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedTextField(host, { host = it.take(253) }, Modifier.weight(1.5f),
                                    label = { Text("Host SSH") }, singleLine = true)
                                OutlinedTextField(port, { port = it.take(5) }, Modifier.weight(.6f),
                                    label = { Text("Puerto") }, singleLine = true)
                                OutlinedTextField(user, { user = it.take(128) }, Modifier.weight(1f),
                                    label = { Text("Usuario") }, singleLine = true)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(password, { password = it.take(256) }, Modifier.weight(1f),
                                    label = { Text("Contraseña temporal") }, singleLine = true,
                                    visualTransformation = PasswordVisualTransformation())
                                Button(onClick = ::connect, enabled = !active && !transport.connected()) { Text("Conectar") }
                                OutlinedButton(onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        transport.disconnect()
                                        withContext(Dispatchers.Main) { shell = false; status = "Desconectado" }
                                    }
                                }) { Text("Salir") }
                            }
                            Text(status, style = MaterialTheme.typography.labelSmall, color = secondary)
                            Surface(Modifier.fillMaxWidth().weight(1f), color = androidx.compose.ui.graphics.Color(0xFF20252A)) {
                                Column(Modifier.verticalScroll(rememberScrollState()).padding(9.dp)) {
                                    Text(output.ifBlank { "Conecta y verifica la huella SSH para empezar." },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = androidx.compose.ui.graphics.Color(0xFFF1F4F5))
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                OutlinedTextField(command, { command = it.take(2048) }, Modifier.weight(1f),
                                    label = { Text("Comando") }, singleLine = true)
                                Button(onClick = {
                                    val cmd = command
                                    command = ""
                                    history.add(cmd)
                                    if (history.size > 20) history.removeAt(0)
                                    scope.launch {
                                        try { withContext(Dispatchers.IO) { transport.send(cmd) } }
                                        catch (_: Exception) { status = "Error al enviar comando" }
                                    }
                                }, enabled = shell && command.isNotBlank()) { Text("Enviar") }
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                listOf("whoami", "pwd", "date", "uname -a", "df -h").forEach { cmd ->
                                    OutlinedButton(onClick = { diagnostic(cmd) }, enabled = transport.connected(),
                                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 3.dp)) {
                                        Text(cmd, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = { askShell = true }, enabled = transport.connected() && !shell) {
                                    Text("Activar terminal")
                                }
                                TextButton(onClick = { output = ""; history.clear() }) { Text("Limpiar") }
                                Text("Historial: " + history.size, style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 12.dp))
                            }
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Modo compacto: gira la tablet para mostrar tres paneles.", color = secondary)
                    targets.forEach { (name, offset) ->
                        Text(name + "  ·  " + (ipAt(base, offset) ?: "—") + "  ·  " + (results[name] ?: "Pendiente"))
                    }
                    OutlinedTextField(host, { host = it.take(253) }, label = { Text("Host SSH") })
                    OutlinedTextField(port, { port = it.take(5) }, label = { Text("Puerto") })
                    OutlinedTextField(user, { user = it.take(128) }, label = { Text("Usuario") })
                    OutlinedTextField(password, { password = it.take(256) }, label = { Text("Contraseña temporal") },
                        visualTransformation = PasswordVisualTransformation())
                    Button(onClick = ::connect, enabled = !active && !transport.connected()) { Text("Conectar") }
                    OutlinedButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            transport.disconnect()
                            withContext(Dispatchers.Main) { shell = false; status = "Desconectado" }
                        }
                    }) { Text("Desconectar") }
                    Text(status)
                    Button(onClick = { askShell = true }, enabled = transport.connected() && !shell) { Text("Activar terminal") }
                    Text(output.ifBlank { "Sin salida de terminal" })
                    OutlinedTextField(command, { command = it.take(2048) }, label = { Text("Comando") })
                    Button(onClick = {
                        val cmd = command
                        command = ""
                        history.add(cmd)
                        if (history.size > 20) history.removeAt(0)
                        scope.launch {
                            try { withContext(Dispatchers.IO) { transport.send(cmd) } }
                            catch (_: Exception) { status = "Error al enviar comando" }
                        }
                    }, enabled = shell && command.isNotBlank()) { Text("Enviar") }
                    listOf("whoami", "pwd", "date", "uname -a", "df -h").forEach { cmd ->
                        OutlinedButton(onClick = { diagnostic(cmd) }, enabled = transport.connected()) { Text(cmd) }
                    }
                    Text("Historial: " + history.joinToString(" · "))
                    OutlinedButton(onClick = { output = ""; history.clear() }) { Text("Limpiar") }
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
