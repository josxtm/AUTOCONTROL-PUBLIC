# SNIFFER por matrícula — issue #18

## Flujo

Seleccionar CT en el catálogo → SNIFFER → importar configuración privada revisada (una vez) → confirmar inventario/base/perfil → conectar Mango con huella verificada → comprobar/preparar WAN → confirmar autorización y ausencia física de otro equipo → aplicar → comprobar equipos → abrir web con protocolo/puerto confirmados → restaurar WAN.

El panel industrial y las diez paletas existentes se conservan. Los botones de diagnóstico funcionan con SSH exec sin activar la terminal. La consola sigue en «MangoSSH avanzado», con confirmación de privilegios, perfil sin contraseña, historial de sesión, copia y seguimiento de salida salvo desplazamiento voluntario hacia atrás.

## Perfiles privados

No se incorpora al código, APK, pruebas ni artefactos de Actions ninguna tabla operativa ni PDF del propietario. Las asignaciones proceden de un JSON local importado con SAF, de hasta 128 Ki caracteres, que se guarda en SharedPreferences privados de esta app. No se incluye en el backup de catálogo; conservar el archivo original privado por separado. Se requiere confirmar que ha sido contrastado con documentación autorizada. El agente no ha podido consultar el PDF privado mediante el conector disponible; no se certifica una revisión independiente de sus tablas.

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

Los alias de conteos se normalizan sin calificadores de origen. Ambos conteos confirmados <=2 seleccionan normal; cualquiera >2 selecciona extendido; inventario incompleto o inválido bloquea el cálculo si no basta para probar extendido. El conteo de remotas debe representar las familias compatibles del procedimiento autorizado, no asumir que un campo genérico describe todas ellas. La GUI permite confirmarlo/corregirlo expresamente por CT. Nunca se decide por prefijo de matrícula.

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

Con una WAN temporal activa se bloquean los cambios de base, conteos e inventario: hay que restaurar antes de redefinir los parámetros. Mientras SSH está conectado o existe una sesión WAN pendiente, el destino SSH no se puede editar. Si se pierde la conexión con WAN temporal, la contraseña y «Conectar» siguen disponibles para recuperar el mismo Mango; una prueba Compose reproduce ese caso sin sockets. Al cambiar de CT se cancela el trabajo, se invalidan propuestas/resultados/webs y se restablecen inventarios y confirmaciones. La WAN de otra matrícula bloquea nuevas acciones hasta restaurarla. Las tareas publican resultados solo para su generación. Las operaciones de recuperación terminan en el worker; la UI no espera monitores de red.

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
