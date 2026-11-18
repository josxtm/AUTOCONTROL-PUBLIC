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


@Composable
fun SnifferPanel(modifier: Modifier = Modifier, selectedCt: String? = null, selectedLan: String? = null, selectedRecord: Map<String, String?> = emptyMap(), suppliedTransport: SshTransport? = null) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val profile = remember { context.getSharedPreferences("sniffer-ssh-profile", 0) }
    val scope = rememberCoroutineScope()
    val transport = remember(suppliedTransport) { suppliedTransport ?: SshTransport(context.getSharedPreferences("sniffer-known-hosts", 0)) }
    var tab by remember { mutableIntStateOf(0) }
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
    var historyVisible by remember { mutableStateOf(false) }
    var event by remember { mutableStateOf("Sin diagnósticos") }
    val history = remember { mutableStateListOf<String>() }
    val actions = remember(transport) { CtActionState(context, transport, scope) }
    var connectionState by remember(transport) { mutableStateOf(transport.connected()) }
    val sessionConnected = connectionState && transport.connected()
    LaunchedEffect(transport) {
        while (true) {
            connectionState = transport.connected()
            if (!connectionState) shell = false
            kotlinx.coroutines.delay(500)
        }
    }
    DisposableEffect(transport) {
        onDispose { approval?.second?.complete(false); password=""; actions.dispose() }
    }
    LaunchedEffect(selectedCt, selectedLan, selectedRecord) { actions.changeCt(selectedCt, selectedRecord, selectedLan) }


    fun append(text: String) { output = (output + text).takeLast(20000) }
    fun connect() {
        if (active || actions.busy || transport.connected()) return
        val p = port.toIntOrNull()
        if (p == null || !EndpointPolicy.valid(host.trim(), p, user.trim())) {
            status = "Host, puerto o usuario incorrectos"; return
        }
        val endpointHost = host.trim()
        val endpointUser = user.trim()
        val secret = password.toCharArray()
        password = ""
        active = true
        status = "Conectando…"
        scope.launch {
            try {
                cancellableDiagnostic { token ->
                    transport.connect(endpointHost, p, endpointUser, secret, { endpoint, algorithm, fingerprint ->
                        runBlocking {
                            val answer = CompletableDeferred<Boolean>()
                            withContext(Dispatchers.Main) {
                                approval = ("Servidor: $endpoint · $algorithm\nHuella: $fingerprint\nVerifica fuera de banda antes de aceptar.") to answer
                            }
                            val registration=token.onCancel { answer.complete(false) }
                            try {
                                val accepted = withTimeoutOrNull(60000) { answer.await() } ?: false
                                withContext(Dispatchers.Main) { approval = null }
                                accepted
                            } finally { registration.close() }
                        }
                    }, token)
                }
                status = "SSH conectado · huella verificada"
            } catch (e: Exception) {
                status = "Conexión fallida: " + e.javaClass.simpleName
            } finally { secret.fill('\u0000'); connectionState = transport.connected(); active = false }
        }
    }
    fun diagnostic(cmd: String) {
        scope.launch {
            try {
                val text=StringBuilder()
                val result=cancellableDiagnostic { token -> transport.diagnostic(cmd,5000,token) { text.append(it) } }
                append("\n> $cmd\n$text\n[${result.label()}]\n")
            }
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
            listOf("Acciones CT", "MangoSSH avanzado").forEachIndexed { i, label ->
                if (tab == i) Button(onClick = { tab = i }) { Text(label) }
                else OutlinedButton(onClick = { tab = i }) { Text(label) }
            }
        }
        if (tab == 1) {
            Text(actions.message, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(.26f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(host, { host = it.take(253) }, label = { Text("Host SSH") }, singleLine = true, enabled = !active && !actions.busy && !actions.leaseActive && !sessionConnected)
                    OutlinedTextField(port, { port = it.take(5) }, label = { Text("Puerto") }, singleLine = true, enabled = !active && !actions.busy && !actions.leaseActive && !sessionConnected)
                    OutlinedTextField(user, { user = it.take(128) }, label = { Text("Usuario") }, singleLine = true, enabled = !active && !actions.busy && !actions.leaseActive && !sessionConnected)
                    OutlinedTextField(password, { password = it.take(256) }, label = { Text("Contraseña temporal") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    OutlinedButton(onClick = {
                        val p = port.toIntOrNull()
                        if (p != null && EndpointPolicy.valid(host.trim(), p, user.trim())) {
                            profile.edit().putString("host", host.trim())
                                .putString("port", port.trim())
                                .putString("user", user.trim()).apply()
                            status = "Perfil guardado localmente (sin contraseña)."
                        } else status = "Revisa host, puerto y usuario."
                    }) { Text("Guardar perfil") }
                    Button(onClick = ::connect, enabled = !active && !actions.busy && !sessionConnected) { Text("Conectar") }
                    OutlinedButton(onClick = { actions.disconnect(); shell = false; status = "Restaurando y desconectando…" }) { Text("Desconectar") }
                    Text(status)
                    Text("Comprueba siempre la huella del servidor.", style = MaterialTheme.typography.bodySmall)
                }
                Column(Modifier.weight(.51f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Consola SSH", style = MaterialTheme.typography.titleMedium)
                    val consoleScroll = rememberScrollState()
                    val follow = consoleScroll.maxValue - consoleScroll.value < 40
                    LaunchedEffect(output) { if (follow) consoleScroll.animateScrollTo(consoleScroll.maxValue) }
                    Surface(Modifier.weight(1f).fillMaxWidth(), color = androidx.compose.ui.graphics.Color(0xFF20252A)) {
                        Column(Modifier.verticalScroll(consoleScroll).padding(10.dp)) {
                            Text(output.ifBlank { "Sin salida" }, color = androidx.compose.ui.graphics.Color(0xFFF1F1F1), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(command, { command = it.take(2048) }, modifier = Modifier.weight(1f), label = { Text("Comando") }, singleLine = true)
                        Button(onClick = {
                            val cmd = command; command = ""; if (CommandHistoryPolicy.rememberable(cmd)) history.add(cmd)
                            if (history.size > 20) history.removeAt(0)
                            scope.launch { try { withContext(Dispatchers.IO) { transport.send(cmd) } } catch (_: Exception) { status = "Error al enviar comando" } }
                        }, enabled = shell && sessionConnected && command.isNotBlank()) { Text("Enviar") }
                    }
                }
                Column(Modifier.weight(.23f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Comandos rápidos")
                    listOf("id", "pwd", "date", "uname -a", "df -h").forEach { cmd ->
                        OutlinedButton(onClick = { diagnostic(cmd) }, enabled = sessionConnected, modifier = Modifier.fillMaxWidth()) { Text(cmd) }
                    }
                    OutlinedButton(onClick = { askShell = true }, enabled = sessionConnected && !shell) { Text("Activar terminal") }
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
            CtActionsPanel(actions, host, sessionConnected && !active) {
                val canReconnect = !active && !actions.busy && !sessionConnected
                val canEdit = canReconnect && !actions.leaseActive
                OutlinedTextField(host, { host = it.take(253) }, label = { Text("IPv4 Mango (gestión SSH)") }, enabled = canEdit, singleLine = true)
                OutlinedTextField(port, { port = it.take(5) }, label = { Text("Puerto SSH") }, enabled = canEdit, singleLine = true)
                OutlinedTextField(user, { user = it.take(128) }, label = { Text("Usuario") }, enabled = canEdit, singleLine = true)
                OutlinedTextField(password, { password = it.take(256) }, label = { Text("Contraseña de sesión") }, enabled = canReconnect, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Button(onClick = ::connect, enabled = canReconnect) { Text("Conectar Mango") }
                OutlinedButton(onClick = { actions.disconnect(); shell = false; status = "Restaurando y desconectando…" }, enabled = !actions.busy) { Text("Desconectar y restaurar") }
                OutlinedButton(onClick = {
                    val p = port.toIntOrNull()
                    if (p != null && EndpointPolicy.valid(host.trim(), p, user.trim())) {
                        profile.edit().putString("host", host.trim()).putString("port", port.trim()).putString("user", user.trim()).apply()
                        status = "Perfil guardado sin contraseña."
                    } else status = "Revisa el perfil SSH."
                }, enabled = canEdit) { Text("Guardar perfil Mango") }
                Text(if (active || sessionConnected) status else "Sin conexión SSH", style = MaterialTheme.typography.bodySmall)
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
