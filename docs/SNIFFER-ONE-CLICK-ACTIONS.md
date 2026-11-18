# SNIFFER por matrícula — issue #18

## Flujo

Configurar el perfil privado autorizado una vez en Configuración → actualizar el Grid MT mensual → seleccionar CT en el catálogo → SNIFFER → confirmar únicamente el inventario que falte → conectar Mango con huella verificada → comprobar/preparar WAN → confirmar autorización y ausencia física de otro equipo → aplicar → comprobar equipos → abrir web con protocolo/puerto confirmados → restaurar WAN.

El panel industrial y las diez paletas existentes se conservan. Los botones de diagnóstico funcionan con SSH exec sin activar la terminal. La consola sigue en «MangoSSH avanzado», con confirmación de privilegios, perfil sin contraseña, historial de sesión, copia y seguimiento de salida salvo desplazamiento voluntario hacia atrás.

## Perfiles privados

No se incorpora al código, APK, pruebas ni artefactos de Actions ninguna tabla operativa ni PDF del propietario. Las asignaciones proceden de un JSON local importado con SAF, de hasta 128 Ki caracteres, que se guarda en SharedPreferences privados de esta app. Se conserva al reiniciar y al actualizar el Grid. Configuración muestra las versiones NORMAL/EXTENDIDO y la huella de la configuración. No se incluye en el backup de catálogo: Configuración ofrece un respaldo privado independiente cifrado con contraseña, que incluye perfiles y confirmaciones por CT y permite recuperarlos tras reinstalar. Guardar también la copia del catálogo para recuperar notas, diario y documentos. Se requiere confirmar que ha sido contrastado con documentación autorizada. El agente no ha podido consultar el PDF privado mediante el conector disponible; no se certifica una revisión independiente de sus tablas.

Contrato versión 1 (ejemplo exclusivamente sintético):

```json
{
  "schema": 1,
  "transformerFields": ["COUNT_A"],
  "remoteFields": ["COUNT_B"],
  "normal": {
    "revision": "synthetic-v1", "prefix": 28,
    "maintenanceCandidates": [6], "reserved": [7],
    "services": [
      {"id": "synthetic-a", "label": "Synthetic A", "offset": 4,
       "inventory": "manual", "index": 0, "scheme": "https", "port": 8443}
    ]
  },
  "extended": {
    "revision": "synthetic-v1", "prefix": 26,
    "maintenanceCandidates": [6], "reserved": [7],
    "services": [
      {"id": "synthetic-b", "label": "Synthetic B", "offset": 27,
       "inventory": "manual", "index": 0, "scheme": "", "port": 80}
    ]
  }
}
```

`inventory` acepta `manual`, `transformers` o `remotes`; `index` vincula el servicio al inventario que el perfil local considera autoritativo. Con `manual` la presencia comienza desconocida. Con un inventario por conteo se distinguen instalados y ausentes según el índice; el técnico puede corregirlo por CT antes de actuar. **Un conteo solo debe vincularse a servicios cuya presencia realmente pueda deducirse de él.** Los equipos desconocidos/ausentes no ofrecen ping o web operativos.

Los alias de conteos se normalizan sin calificadores de origen. Ambos conteos confirmados <=2 seleccionan normal; cualquiera >2 selecciona extendido; inventario incompleto o inválido bloquea el cálculo si no basta para probar extendido. El conteo de remotas debe representar las remotas aplicables al inventario autorizado: no asumir que un campo genérico describe todas ellas. Los mismos perfiles se aplican a todos los fabricantes; no se decide por fabricante ni por prefijo de matrícula. Los datos fiables del Grid se leen automáticamente y sus campos de conteo no se editan. Si faltan datos, se confirman únicamente los necesarios y se guardan por CT.

La base IPv4 debe estar alineada con el /28 o /26 correspondiente; un CIDR explícito incompatible se rechaza. Se rechazan red, broadcast, desbordamientos y subredes que se solapen con LAN/Wi-Fi o el acceso SSH de gestión. No hay /24 implícito ni tabla fija de equipos en producción. Las posiciones no descritas no adquieren servicio ni se consideran libres.

Los candidatos de mantenimiento son propuestas, no permisos. Un servicio instalado o desconocido en la dirección candidata la bloquea; solo puede reutilizarse un servicio marcado explícitamente ausente, fuera de las reservas no reutilizables, con autorización y comprobación física posteriores. Un hueco sin servicio también exige autorización y verificación física. **No se presupone que un desplazamiento habitual esté disponible.**

## WAN temporal y recuperación

- Solo actúa sobre el Mango SSH autenticado y autorizado. Usa `/lib/functions/network.sh` para descubrir WAN y LAN reales, sin nombres físicos incorporados.
- Lee enlace, IPv4, rutas y sesión temporal anterior. Requiere enlace WAN, LAN identificable e IPv4 de gestión validable. Un hostname SSH puede usarse para conectar, pero no habilita cambios WAN sin una IPv4 de gestión verificable.
- Busca vecinos conocidos, respuesta ping y duplicados con `arping -D` cuando existe. Un positivo bloquea. Ausencia de respuesta/no disponibilidad ARP sigue siendo incertidumbre, nunca prueba de IP libre: la confirmación incluye verificación física del técnico.
- Repite la validación al aplicar. Comprueba otra vez interfaces y direcciones LAN, evita apropiarse de una IP ya configurada en el Mango y verifica que la ruta calculada sale por la WAN.
- Añade únicamente un alias IPv4 temporal mediante `ip addr add`. No vacía direcciones, no cambia DHCP, no escribe UCI, no reinicia red, no modifica firewall, gateways, LAN o Wi-Fi, y no ejecuta ninguna configuración sobre equipos CT.
- Tras aplicar verifica marcador de sesión y presencia del alias. Restaurar elimina solamente ese alias; las direcciones/rutas anteriores permanecen. Si netifd cambia por causas externas, no se intenta reproducir a ciegas una configuración antigua.
- Una exclusión mutua remota con espera acotada y un marcador de cancelación evitan una aplicación tardía posterior a restaurar. Los marcadores se almacenan en `/tmp`, con permisos restringidos y nonce por sesión. El watchdog remoto de 10 minutos comprueba ese nonce: nunca puede retirar el alias de una sesión posterior.
- Cancelación/error intenta restaurar con un token nuevo. Si falla, conserva el estado de recuperación. Al reconectar «Comprobar WAN» descubre el marcador y permite restaurarlo. Al salir se intenta restaurar antes de desconectar; el watchdog cubre caída de app/conexión. Una terminación forzosa del proceso remoto puede dejar la exclusión/marcador incompleto: requiere revisión manual autorizada; no se fuerza un borrado sin conocer el dueño.
- Se exige `nohup`; si falta una utilidad requerida se bloquea y se muestra comando no disponible. La compatibilidad concreta de BusyBox/netifd/arping debe verificarse físicamente.

Con una WAN temporal activa se bloquean los cambios de base, conteos e inventario: hay que restaurar antes de redefinir los parámetros. Mientras SSH está conectado o existe una sesión WAN pendiente, el destino SSH no se puede editar. Si se pierde la conexión con WAN temporal, la contraseña y «Conectar» siguen disponibles para recuperar el mismo Mango; una prueba Compose reproduce ese caso sin sockets. Al cambiar de CT se cancela el trabajo, se invalidan propuestas/resultados y autorizaciones, y se cargan exclusivamente las confirmaciones persistentes de la nueva matrícula. La WAN de otra matrícula bloquea nuevas acciones hasta restaurarla. Las tareas publican resultados solo para su generación. Las operaciones de recuperación terminan en el worker; la UI no espera monitores de red.

## Estados SSH y acceso web

`id` sustituye `whoami` en los botones. Los canales exec se abren sin PTY y tienen deadline/cierre efectivos. Se permite una breve llegada tardía del exit status; su ausencia se informa como desconocida. Se distinguen error de canal/conexión SSH, comando inexistente (127), comando terminado con error, timeout, cancelación y ausencia de respuesta ICMP. No se interpreta stderr como fallo de conexión ni se desactiva pinning.

Cada resultado identifica si procede de Mango SSH o Android; cambiar el origen limpia los resultados previos. Solo una respuesta ping válida pinta éxito. No-respuesta, desconocido y no instalado tienen etiquetas propias. Los protocolos HTTP/HTTPS y el puerto deben venir del perfil revisado o confirmarse en la GUI. Se construye una URI numérica sin usuario, contraseña, query o comandos. Al pulsar se valida de nuevo la matrícula/base/revisión, evitando abrir un destino de un CT anterior.

## Pruebas y límites

- Motor puro compartido JVM/Android: perfiles normal/extendido, conteos, offsets sintéticos, alineación, máscara/rango, conflictos en candidato, reservas, ocupación desconocida, confirmación, conservación de gestión, rollback, pérdida de conexión, recuperación de lease y parámetros maliciosos.
- Scripts WAN reales ejecutados en JVM con `ip`, red, ping, ARP y watchdog ficticios en un directorio temporal: aplicar/restaurar, restaurar antes/durante aplicación, y watchdog antiguo frente a sesión nueva. No se invocan herramientas de red del host ni equipos físicos.
- JSch real contra servidor SSH loopback de pruebas: exec con shell abierta/cerrada, `id`, exit 127/error/ping sin respuesta, timeout, cancelación y huella cambiada. El servidor es sintético; no sustituye Dropbear/BusyBox real.
- Instrumentadas: importación privada de perfil sintético, lectura de conteos, transición CT normal→extendido, ausencia de datos, base no alineada, web validada y borrado de estado por matrícula. Se ejecutan también las regresiones completas del importador de 70.000 filas, almacenamiento, documentos, OCR y Compose.

Comando de validación: `gradle --no-daemon :storage:testDebugUnitTest :documents:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`, seguido por `:app:connectedDebugAndroidTest` en CI. El filtro de privacidad y la inspección de APK siguen activos.

Pendientes físicos: contraste de la configuración privada con el procedimiento, detección WAN/LAN y opciones BusyBox del Mango, interfaz real, ARP en el enlace, aplicación y restauración, renovación netifd, pérdida de Wi-Fi, forwarding/NAT existente hacia CT y navegador, y legibilidad en tablet horizontal. No se modifican forwarding/firewall ni dispositivos CT para hacer pasar estas pruebas. El canal debug conserva los límites de firma de SIGNING.md; hacer copia cifrada verificada antes de una desinstalación.

Dependencia: rama basada en el commit validado del PR #17. El PR de esta fase se compara contra esa rama para aislar los cambios de SNIFFER. No se fusiona main ni se altera el importador, su base, notas, diario o documentos.

## Persistencia y mantenimiento (fase de simplificación IP)

- Se reutilizan el fichero privado de preferencias y la clave `profiles` de comp. 255. No hay migración destructiva ni importación mensual de JSON. Un perfil instalado se lee automáticamente en cada sesión. Solo se importa de nuevo para instalarlo o cambiar su versión.
- El botón de importación está en la pestaña Configuración existente; SNIFFER muestra la versión y permite guardar la confirmación del CT. Sin perfil ofrece acceso directo a Configuración. No se añaden pantallas ni tablas operativas al APK.
- Las confirmaciones incluyen base, conteos faltantes, presencia de equipos y protocolo/puerto web. Se vinculan a la huella SHA-256 del perfil y de los campos de inventario/base del Grid. Un cambio de nombre o fabricante no las invalida; un cambio de inventario/base o perfil exige nueva revisión. Importar el mismo perfil es idempotente. Cambiar su contenido invalida las confirmaciones anteriores.
- No se guardan autorizaciones de WAN, afirmaciones de ausencia física, contraseñas SSH, propuestas ni sesiones WAN en las confirmaciones o el respaldo privado. El candidato habitual +10 no tiene excepción: reservas, equipos instalados/desconocidos y conflictos observados lo bloquean.
- El respaldo `.acip` usa el contenedor existente ACPB1: PBKDF2-HMAC-SHA256, 210.000 iteraciones, sal y nonce aleatorios, AES-256-GCM. La autenticación y validación integral del contenido terminan antes de sustituir las preferencias en una sola edición. Una copia incorrecta, truncada o con contraseña equivocada conserva el perfil anterior. La contraseña no se almacena. Su pérdida impide recuperar la copia.
- Una actualización con la misma identidad y firma conserva los datos privados por el mecanismo de Android. Una desinstalación los elimina: exportar y verificar ambos respaldos antes. No se garantiza conservarlos entre APK con firmas diferentes; consultar SIGNING.md sobre el canal debug.

Pruebas instrumentadas nuevas: compatibilidad con preferencias antiguas; reapertura del almacén; actualización mensual Grid sin pérdida de perfil, notas o diario y exclusión de credenciales; persistencia por CT; invalidación por cambio de inventario/base/perfil; fabricantes distintos; conteo cero no fiable; respaldo cifrado/restauración, contraseña errónea, corrupción, truncamiento y carga ajena. CI ejecuta además `tools/profile_upgrade_probe.sh`: siembra datos sintéticos en un proceso, lo detiene, realiza `adb install -r` del mismo APK con la misma firma y verifica desde otro proceso. Es una sustitución real compatible del paquete en emulador, no una prueba de migración de firma ni una actualización física desde otra APK.

La regresión completa mantiene importador de 70.000 filas, pruebas WAN y conflictos, SSH real de laboratorio, interfaz sin terminal, almacenamiento, documentos y privacidad. Los resultados verificables de cada compilación se publican en PR #19 y en los artefactos de Actions. Quedan pendientes exclusivamente la validación física del Mango/tablet y el contraste autorizado del perfil privado; no se certifican aquí tablas que no se han podido consultar.
