package com.josxtm.autocontrol.community.sniffer

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.josxtm.autocontrol.community.*
import kotlinx.coroutines.*
import java.net.InetAddress
import java.util.Locale

private val targets=listOf("Router" to 1,"CC T1" to 2,"CC T2" to 3,"SABT T2" to 9,"SABT T1" to 11,"FFAA1" to 13,"Remota" to 14)
private fun ipAt(base:String,offset:Int):String?=try{LanTargets.at(base.trim().substringBefore('/'),base.substringAfter('/',"24").toInt(),offset)}catch(_:Exception){null}
private data class ScanResult(val state:String,val millis:Long?=null)
private data class Event(val time:String,val type:String,val description:String)

@Composable fun SnifferPanel(modifier:Modifier=Modifier,selectedCt:String?=null,selectedLan:String?=null,selectedName:String?=null,query:String="",onQuery:(String)->Unit={},onSelect:()->Unit={}){
    val context=LocalContext.current;val clipboard=LocalClipboardManager.current;val scope=rememberCoroutineScope()
    val networkStatus=rememberLanStatus()
    val profile=remember{context.getSharedPreferences("sniffer-ssh-profile",0)}
    val pins=remember{context.getSharedPreferences("sniffer-known-hosts",0)}
    val transport=remember{SshTransport(pins)}
    var tab by remember{mutableIntStateOf(0)};var base by remember{mutableStateOf("")}
    var host by remember{mutableStateOf(profile.getString("host","").orEmpty())};var port by remember{mutableStateOf(profile.getString("port","22").orEmpty())};var user by remember{mutableStateOf(profile.getString("user","").orEmpty())}
    var password by remember{mutableStateOf("")};var command by remember{mutableStateOf("")};var output by remember{mutableStateOf("")}
    var connected by remember{mutableStateOf(false)};var shell by remember{mutableStateOf(false)};var connecting by remember{mutableStateOf(false)}
    var connectionDialog by remember{mutableStateOf(false)};var adminDialog by remember{mutableStateOf(false)}
    var approval by remember{mutableStateOf<Pair<String,CompletableDeferred<Boolean>>?>(null)}
    var fingerprint by remember{mutableStateOf("")};var status by remember{mutableStateOf("Desconectado")};var connectedAt by remember{mutableLongStateOf(0)};var seconds by remember{mutableLongStateOf(0)}
    var expanded by remember{mutableStateOf(false)};var historyOpen by remember{mutableStateOf(false)}
    val history=remember{mutableStateListOf<String>()};val events=remember{mutableStateListOf<Event>()};var eventFilter by remember{mutableStateOf("Todos")}
    var results by remember{mutableStateOf<Map<String,ScanResult>>(emptyMap())};val absent=remember{mutableStateMapOf<String,Boolean>()}
    var scanning by remember{mutableStateOf(false)};var scanJob by remember{mutableStateOf<Job?>(null)};var epoch by remember{mutableIntStateOf(0)}
    fun log(type:String,text:String){events.add(0,Event(java.text.SimpleDateFormat("HH:mm:ss",Locale.ROOT).format(java.util.Date()),type,text));if(events.size>150)events.removeAt(events.lastIndex)}
    fun append(text:String){output=(output+text).takeLast(40000)}
    fun disconnect(){scope.launch{withContext(Dispatchers.IO){transport.disconnect()};connected=false;shell=false;status="Desconectado";log("SSH","Sesión desconectada")}}
    LaunchedEffect(transport){while(isActive){connected=transport.connected();shell=transport.shellConnected();seconds=if(connected&&connectedAt>0)(android.os.SystemClock.elapsedRealtime()-connectedAt)/1000 else 0;delay(1000)}}
    DisposableEffect(transport){onDispose{approval?.second?.complete(false);scanJob?.cancel();Thread{transport.disconnect()}.start()}}
    LaunchedEffect(selectedCt,selectedLan){
        epoch++;scanJob?.cancel();scanning=false;results=emptyMap();absent.clear();base=selectedLan.orEmpty();
        approval?.second?.complete(false);approval=null
        if(connected||connecting){withContext(Dispatchers.IO){transport.disconnect()};connected=false;shell=false;status="CT cambiado: sesión cerrada"}
        host=profile.getString("host","").orEmpty();fingerprint=""
        log("CT",if(selectedCt==null)"Sin CT seleccionado"else"CT seleccionado; resultados anteriores retirados")
    }
    fun connect(){
        val p=port.toIntOrNull();if(p==null||!EndpointPolicy.valid(host.trim(),p,user.trim())){status="Revisa host, puerto y usuario";return}
        val requestedEpoch=epoch;val endpoint=host.trim();val login=user.trim();val secret=password.toCharArray();password="";connecting=true;connectionDialog=false
        scope.launch{try{
            withContext(Dispatchers.IO){transport.connect(endpoint,p,login,secret){server,algorithm,fp->runBlocking{
                val response=CompletableDeferred<Boolean>();withContext(Dispatchers.Main){approval=("Servidor: $server · $algorithm\n$fp\nVerifica la huella fuera de banda.") to response}
                val accepted=withTimeoutOrNull(60000){response.await()}?:false;withContext(Dispatchers.Main){approval=null};accepted
            }}}
            if(requestedEpoch!=epoch){withContext(Dispatchers.IO){transport.disconnect()};return@launch}
            connected=transport.connected();connectedAt=android.os.SystemClock.elapsedRealtime();status="Conectado · identidad verificada"
            val pinHost=if(p==22)endpoint else "[$endpoint]:$p"
            val digest=java.security.MessageDigest.getInstance("SHA-256").digest(pinHost.toByteArray(Charsets.UTF_8))
            fingerprint=pins.getString("pin.SHA256:"+android.util.Base64.encodeToString(digest,android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING),"").orEmpty()
            profile.edit().putString("host",endpoint).putString("port",p.toString()).putString("user",login).apply();log("SSH","Conexión establecida; huella verificada")
        }catch(_:Exception){status="Conexión rechazada o no disponible";log("Error",status)}finally{secret.fill('\u0000');connecting=false}}
    }
    fun execute(cmd:String){
        if(!transport.connected()){status="Conecta primero al equipo";return}
        scope.launch{try{withContext(Dispatchers.IO){
            if(EndpointPolicy.diagnostic(cmd)){val text=transport.diagnostic(cmd);withContext(Dispatchers.Main){append("\n> $cmd\n$text\n")}}
            else if(transport.shellConnected())transport.send(cmd) else {withContext(Dispatchers.Main){status="Requiere terminal autorizada";adminDialog=true};return@withContext}
        }}catch(_:Exception){status="Comando no disponible en este equipo";log("Error",status)}}
    }
    fun scan(fromMango:Boolean){
        val generation=++epoch;val requested=base;scanJob?.cancel();results=emptyMap();scanning=true;log("Escaneo",if(fromMango)"Pings desde Mango iniciados"else"Comprobación LAN iniciada")
        scanJob=scope.launch{try{for((name,offset)in targets){ensureActive();val address=ipAt(requested,offset)
            val result=if(absent[name]==true)ScanResult("No instalado")else if(address==null)ScanResult("Error")else withContext(Dispatchers.IO){try{
                if(fromMango){val text=transport.diagnostic("ping -c 1 -W 2 $address");val match=Regex("time[=<]([0-9.]+)\\s*ms").find(text);ScanResult(if(match!=null)"Responde"else"Sin respuesta",match?.groupValues?.get(1)?.toDoubleOrNull()?.toLong())}
                else {val start=android.os.SystemClock.elapsedRealtime();val ok=InetAddress.getByName(address).isReachable(900);ScanResult(if(ok)"Responde"else"Sin respuesta",if(ok)android.os.SystemClock.elapsedRealtime()-start else null)}
            }catch(_:Exception){ScanResult("Error")}}
            ensureActive();if(generation==epoch){results=results+(name to result);log("Ping","$name: ${result.state}")}
        };if(generation==epoch)log("Escaneo","Comprobación finalizada")
        }catch(e:CancellationException){if(generation==epoch)log("Escaneo","Comprobación cancelada");throw e}finally{if(generation==epoch)scanning=false}}
    }
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")){uri->if(uri!=null){val snapshot=events.toList();scope.launch{try{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri,"w")!!.bufferedWriter().use{w->snapshot.forEach{w.write("${it.time}\t${it.type}\t${it.description}\n")}}};status="Eventos exportados"}catch(_:Exception){status="No se pudo exportar"}}}}
    val green=Color(0xFF287B2D);val red=Color(0xFFAD202C)
    @Composable fun ScanPane(mod:Modifier){IndustrialPanel(if(tab==1)"Pings desde Mango"else"Escaneo de red (LAN)","search",mod){
        Row(Modifier.padding(6.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){IndustrialButton("Iniciar escaneo","play",!scanning&&ipAt(base,14)!=null&&(tab!=1||connected),true){scan(tab==1)};IndustrialButton("Cancelar","cancel",scanning){scanJob?.cancel()}}
        Text("${results.size}/${targets.size} equipos comprobados",Modifier.padding(6.dp),fontSize=12.sp)
        LinearProgressIndicator(progress={results.size/7f},modifier=Modifier.fillMaxWidth().padding(horizontal=6.dp),color=green)
        Row(Modifier.padding(6.dp)){Text("Equipo",Modifier.weight(1f),fontSize=12.sp);Text("IP",Modifier.weight(1.4f),fontSize=12.sp);Text("ms / estado",Modifier.weight(1f),fontSize=12.sp)}
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())){targets.forEach{(name,offset)->
            HorizontalDivider();val r=results[name];val state=if(absent[name]==true)"No instalado"else r?.state?:"Pendiente"
            Row(Modifier.fillMaxWidth().heightIn(min=38.dp).padding(horizontal=5.dp),verticalAlignment=Alignment.CenterVertically){
                Text(name,Modifier.weight(1f),fontSize=12.sp);Text(ipAt(base,offset)?:"LAN +$offset",Modifier.weight(1.4f),fontSize=11.sp)
                Column(Modifier.weight(1f)){Text(r?.millis?.let{"$it ms"}?:"—",fontSize=11.sp);Text(state,fontSize=10.sp,color=if(state=="Responde")green else if(state=="Sin respuesta"||state=="Error")red else MaterialTheme.colorScheme.onSurfaceVariant)}
                if(tab==2)Checkbox(absent[name]==true,{absent[name]=it;results=results-name},enabled=!scanning)
            }
        }}
        Text("Responde · Sin respuesta · No instalado · Pendiente",Modifier.padding(5.dp),fontSize=10.sp)
        Text("Sin respuesta ICMP no confirma desconexión.",Modifier.padding(5.dp),fontSize=10.sp)
    }}
    @Composable fun TerminalPane(mod:Modifier){IndustrialPanel("MangoSSH — Terminal","terminal",mod,actions={
        IconButton(onClick={expanded=!expanded},modifier=Modifier.size(30.dp)){TechIcon("expand",color=Color.White,description=if(expanded)"Restaurar terminal"else"Ampliar terminal")}
        IconButton(onClick={output=""},modifier=Modifier.size(30.dp)){TechIcon("clear",color=Color.White,description="Limpiar terminal")}
    }){
        Row(Modifier.fillMaxWidth().padding(5.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(if(connected)"Conectado: $host ($user)"else"Desconectado",fontSize=12.sp,color=if(connected)green else MaterialTheme.colorScheme.onSurface);Text("%02d:%02d:%02d".format(seconds/3600,seconds/60%60,seconds%60),fontSize=12.sp)}
        Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF0C0E10)).padding(9.dp)){Text(output.ifBlank{"MangoSSH\nConecta un equipo y verifica su huella.\nNo hay datos de terminal simulados."},Modifier.verticalScroll(rememberScrollState()),fontFamily=FontFamily.Monospace,fontSize=13.sp,color=Color(0xFFE8EEE9))}
        Row(Modifier.fillMaxWidth().padding(top=4.dp),verticalAlignment=Alignment.CenterVertically){OutlinedTextField(command,{command=it.take(2048)},Modifier.weight(1f),placeholder={Text("Escribe un comando…",fontSize=12.sp)},singleLine=true);IndustrialButton("Enviar","play",shell&&command.isNotBlank()){
            val cmd=command;command="";if(CommandHistoryPolicy.rememberable(cmd)){history.add(cmd);if(history.size>20)history.removeAt(0)};scope.launch{try{withContext(Dispatchers.IO){transport.send(cmd)}}catch(_:Exception){status="Error de envío"}}
        }}
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){IndustrialButton("Historial"){historyOpen=!historyOpen};listOf("whoami","uname -a","df -h").forEach{cmd->IndustrialButton(cmd,enabled=connected){execute(cmd)}};IndustrialButton("Copiar",enabled=output.isNotEmpty()){clipboard.setText(AnnotatedString(output))}}
        if(historyOpen)Column(Modifier.heightIn(max=90.dp).verticalScroll(rememberScrollState())){history.forEach{cmd->TextButton(onClick={command=cmd}){Text(cmd.take(100))}}}
    }}
    @Composable fun QuickPane(mod:Modifier){IndustrialPanel("Comandos rápidos","terminal",mod){Column(Modifier.verticalScroll(rememberScrollState()).padding(4.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
        listOf("Información del sistema" to listOf("Resumen del sistema" to "uname -a","Estado de servicios" to "ps","Uso de recursos" to "df -h"),
            "Diagnóstico de red" to listOf("Ping al router" to (ipAt(base,1)?.let{"ping -c 1 -W 2 $it"}?:""),"Tabla de rutas" to "if command -v ip >/dev/null 2>&1; then ip route; else route -n; fi","Interfaces de red" to "if command -v ip >/dev/null 2>&1; then ip addr; else ifconfig; fi"),
            "Herramientas" to listOf("Ver logs del sistema" to "if command -v logread >/dev/null 2>&1; then logread | tail -n 60; else dmesg | tail -n 60; fi","Ver logs de conexión" to "","Comando personalizado" to ""))
            .forEach{(section,commands)->var open by remember{mutableStateOf(true)};IndustrialButton(section,primary=true){open=!open};if(open)commands.forEach{(title,cmd)->IndustrialButton(title,"document",enabled=connected||title=="Ver logs de conexión"){
                when(title){"Ver logs de conexión"->{eventFilter="SSH"};"Comando personalizado"->{tab=3;adminDialog=!shell};else->if(cmd.isEmpty())status="Selecciona una LAN válida"else execute(cmd)}
            }}}
        Text("Servicios: lista de procesos. Las herramientas ausentes muestran su salida de error. Acciones de lectura.",fontSize=10.sp)
    }}}
    Column(modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(5.dp)){
        Row(Modifier.fillMaxWidth().heightIn(min=45.dp).background(MaterialTheme.colorScheme.surfaceVariant).padding(5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
            OutlinedTextField(query,onQuery,Modifier.weight(1f).height(48.dp),label={Text("Matrícula CT",fontSize=11.sp)},singleLine=true,enabled=!connecting)
            IndustrialButton("Buscar","search",!connecting){onSelect()}
            Column(Modifier.weight(1f)){Text(selectedCt?:"Sin CT",fontSize=12.sp);Text(selectedName?:"Nombre pendiente",fontSize=11.sp,maxLines=1)}
            OutlinedTextField(base,{epoch++;scanJob?.cancel();scanning=false;base=it;results=emptyMap()},Modifier.weight(1f).height(48.dp),label={Text("IP LAN",fontSize=11.sp)},singleLine=true)
            var selectHost by remember{mutableStateOf(false)}
            Box{IndustrialButton("SSH: ${targets.firstOrNull{ipAt(base,it.second)==host}?.first?:"Seleccionar"}","network",!connected&&!connecting){selectHost=true};DropdownMenu(selectHost,{selectHost=false}){targets.forEach{(name,offset)->DropdownMenuItem(text={Text("$name · ${ipAt(base,offset)?:"Sin IP"}")},onClick={ipAt(base,offset)?.let{host=it};selectHost=false})}}}
            Text(networkStatus,fontSize=11.sp)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf("Escaneo","Pings","Equipos","MangoSSH").forEachIndexed{i,label->IndustrialButton(label,if(i==3)"terminal"else"network",primary=tab==i){tab=i}}}
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()){
            if(maxWidth>=850.dp)Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(7.dp)){
                if(!expanded)ScanPane(Modifier.weight(.30f).fillMaxHeight())
                TerminalPane(Modifier.weight(if(expanded)1f else .47f).fillMaxHeight())
                if(!expanded)QuickPane(Modifier.weight(.23f).fillMaxHeight())
            }else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)){
                if(tab!=3)ScanPane(Modifier.fillMaxWidth().height(410.dp));TerminalPane(Modifier.fillMaxWidth().height(500.dp));QuickPane(Modifier.fillMaxWidth())
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){
            @Composable fun StatusStrip(label:String,mod:Modifier,actions:@Composable RowScope.()->Unit){
                Surface(mod.height(58.dp),shape=androidx.compose.foundation.shape.RoundedCornerShape(5.dp),
                    color=MaterialTheme.colorScheme.surfaceVariant,
                    border=BorderStroke(1.dp,Color(0xFF92979B))){
                    Row(Modifier.fillMaxSize().padding(horizontal=6.dp),
                        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){
                        TechIcon("lock",Modifier.size(18.dp))
                        Text(label,Modifier.weight(1f),fontSize=11.sp,maxLines=2)
                        actions()
                    }
                }
            }
            StatusStrip("SSH · ${if(connected)"Conectado"else if(connecting)"Conectando"else"Desconectado"}",Modifier.weight(1.2f)){
                IndustrialButton("Conectar","play",!connected&&!connecting){connectionDialog=true}
                IndustrialButton("Desconectar","cancel",connected){disconnect()}
            }
            StatusStrip("Huella SSH (SHA256)",Modifier.weight(1f)){
                Text(if(connected)fingerprint.ifBlank{"Verificada"}else"Sin verificar",
                    fontSize=9.sp,maxLines=2,modifier=Modifier.weight(1f))
            }
            StatusStrip("Acceso administrador",Modifier.weight(.8f)){
                IndustrialButton(if(shell)"Autorizado"else"Confirmar",enabled=connected&&!shell){adminDialog=true}
            }
        }
        IndustrialPanel("Registro de eventos","document",Modifier.fillMaxWidth().height(116.dp),actions={
            TextButton(onClick={eventFilter=if(eventFilter=="Todos")"Error"else"Todos"}){Text("Filtro: $eventFilter",color=Color.White,fontSize=11.sp)}
            IconButton(onClick={events.clear()},modifier=Modifier.size(30.dp)){TechIcon("clear",color=Color.White,description="Limpiar eventos")}
            TextButton(onClick={exporter.launch("AutoControL-eventos.txt")}){Text("Exportar",color=Color.White,fontSize=11.sp)}
        }){Column(Modifier.verticalScroll(rememberScrollState())){events.filter{eventFilter=="Todos"||it.type==eventFilter}.forEach{e->Row(Modifier.fillMaxWidth().padding(2.dp)){Text(e.time,Modifier.width(70.dp),fontSize=11.sp);TechIcon(if(e.type=="Error")"cancel"else"status",Modifier.size(15.dp));Text("${e.type} · ${e.description}",Modifier.padding(start=8.dp),fontSize=11.sp)}}}}
    }
    if(connectionDialog)AlertDialog(onDismissRequest={connectionDialog=false;password=""},title={Text("Conexión MangoSSH")},text={Column{OutlinedTextField(host,{host=it},label={Text("Host SSH")});OutlinedTextField(port,{port=it},label={Text("Puerto")});OutlinedTextField(user,{user=it},label={Text("Usuario")});OutlinedTextField(password,{password=it},label={Text("Contraseña temporal")},visualTransformation=PasswordVisualTransformation())}},confirmButton={TextButton(onClick={connect()}){Text("Conectar")}},dismissButton={TextButton(onClick={connectionDialog=false;password=""}){Text("Cancelar")}})
    approval?.let{(prompt,response)->AlertDialog(onDismissRequest={response.complete(false);approval=null},title={Text("Identidad SSH")},text={Text(prompt)},confirmButton={TextButton(onClick={response.complete(true);approval=null}){Text("Huella verificada")}},dismissButton={TextButton(onClick={response.complete(false);approval=null}){Text("Rechazar")}})}
    if(adminDialog)AlertDialog(onDismissRequest={adminDialog=false},title={Text("Confirmar acceso administrativo")},text={Text("La terminal interactiva permite ejecutar comandos en el equipo remoto. Úsala exclusivamente en equipos autorizados.")},confirmButton={TextButton(onClick={adminDialog=false;scope.launch{try{withContext(Dispatchers.IO){transport.openShell{chunk->scope.launch{append(chunk)}}};shell=transport.shellConnected();log("SSH","Terminal autorizada y abierta")}catch(_:Exception){status="No se pudo abrir la terminal"}}}){Text("Autorizar terminal")}},dismissButton={TextButton(onClick={adminDialog=false}){Text("Cancelar")}})
}
