# SNIFFER LAN — MangoSSH integrado (fase funcional)

## Arquitectura y alcance

Esta rama parte de la integración previa en `feat/tablet-industrial-gui-mangossh`. No incorpora el rediseño de tres paneles del PR de GUI. No requiere instalar `MANGOSSH-PUBLIC` en el dispositivo: su clase de transporte SSH ya estaba incorporada en el paquete interno `sniffer`, y se conserva como motor JSch, con estabilización de cancelación, recursos y pines por endpoint.

- `SshTransport.java`: canal SSH con autenticación temporal, verificación de huella, comandos de lectura y terminal interactiva.
- `EndpointPolicy.java`: validación de host/usuario y comandos de diagnóstico de lectura, incluido ping de un paquete a una IPv4 validada.
- `LanTargets.java`: offsets de siete equipos y comprobación de prefijo IPv4.
- `SnifferPanel.kt`: controles existentes, carga de datos de CT, diagnóstico directo y desde el Mango, cancelación, salida, copia e historial de sesión.
- `WorkspaceModel.kt`: acceso de lectura a la IP LAN del registro local seleccionado, sin cargar datos del repositorio público.
- `MainActivity.kt`: SNIFFER fuera de virtualización de lista para que una sesión abierta no se cierre al desplazarse dentro de la pantalla.

## Flujo operativo

1. Importar un catálogo JSON privado por el selector del dispositivo.
2. Buscar un registro por su identificador configurado.
3. Abrir SNIFFER LAN. Si existe un campo `IP LAN`, se propone la base de red; si falta, introducirla manualmente. Se admite IPv4 base y CIDR opcional.
4. Revisar en la pestaña Equipos qué dispositivos están instalados; marcar los ausentes como `No instalado` para excluirlos del diagnóstico.
5. Realizar comprobación directa desde Android o conectar el Mango y usar `Ping desde Mango`. El segundo método ejecuta comandos de ping limitados por la política de solo lectura desde el equipo SSH remoto.
6. En MangoSSH, introducir host/puerto/usuario/contraseña temporal. Comprobar y aceptar explícitamente la huella SSH por un canal independiente. No se guarda la contraseña.
7. Ejecutar diagnósticos permitidos; activar la consola interactiva únicamente mediante confirmación expresa.
8. Revisar, copiar o limpiar la salida e historial. Guardar host/puerto/usuario localmente solo si se pulsa `Guardar perfil`.

## Criterios de seguridad

- Nunca se incluyeron IP, matrículas, catálogo privado ni credenciales operativas en código.
- Las pruebas utilizan direcciones construidas sintéticamente, sin conectar a redes externas.
- `StrictHostKeyChecking` permanece habilitado; una huella cambiada es rechazada.
- La terminal interactiva está protegida mediante diálogo, y no hay ejecución automática de comandos administrativos o de telemando.
- Una ausencia de respuesta ICMP no es prueba definitiva de equipo desconectado.
- Las IP derivadas deben ser hosts válidos dentro de la subred; no se cruzan subredes implícitamente.

## Límites verificados / pendientes

- El terminal SSH conserva el lector de texto básico de MangoSSH: **no dispone de emulación ANSI completa**.
- El ping remoto requiere que el firmware real del Mango soporte las opciones de línea de comandos indicadas y tenga ruta al equipo. Confirmar en pruebas autorizadas sobre hardware físico.
- La carga de IP depende de que el catálogo privado pueda importarse y contenga un campo denominado `IP LAN`, con base de red compatible.
- El historial y resultados son volátiles en memoria, no se exportan automáticamente ni se sincronizan.
- La clasificación de ping es indicativa; no debe usarse para realizar maniobras eléctricas.
- La compilación y pruebas de CI no prueban la conexión real a un Mango ni compatibilidad física de todas las redes.
- En el canal debug actual se mantiene la instalación manual después de desinstalar la APK anterior, con copia cifrada previa si hay datos.

**No modificar el diseño aprobado B en esta fase. No fusionar automáticamente con main.**

## Fase 1: transporte estabilizado

`DiagnosticResult`, `DiagnosticStatus` y `DiagnosticSource` separan datos de presentación. Un registro no incluye contraseña ni salida de comandos. `NO_RESPONSE` significa ausencia de respuesta observada, nunca desconexión confirmada. El ping remoto usa el estado de salida (0 respuesta; 1 sin respuesta si no hay stderr; otros/error de ejecución: error SSH/comando), sin buscar subcadenas ambiguas como porcentajes de pérdida. Confirmar esa convención y las opciones de ping en el firmware concreto.

`CommandRunner` aplica deadline monotónico, supervisión y cancelación activa, conserva hasta 64 KiB de salida y cierra recursos en éxito/error/timeout/cancelación. La lectura por sondeo de la pipe y la espera de cierre/código de salida están dentro del mismo límite. La limpieza puede añadir hasta el margen de cierre de 250 ms y planificación del sistema. No se crean lectores por comando. La terminal interactiva mantiene un lector propio durante su sesión y lo termina al cerrar.

El puente de coroutines vincula la cancelación al token Java incluso si el trabajador IO está detenido. Cancelar o cambiar la base CT retira resultados antiguos; los estados restantes se marcan cancelados cuando corresponde. Un comando cancelado no bloquea `disconnect()` por un monitor global.

JSch se fija en 2.28.8. Se verificó en el JAR y fuentes oficiales la API `setPassword(byte[])`, que evita el String adicional indicado como riesgo en la auditoría. Los pines nuevos distinguen host y puerto y migran las identidades antiguas exactas; una identidad nueva exige confirmación explícita. El usuario, host y puerto solo se persisten con «Guardar perfil».

`INTERNET` se utiliza exclusivamente para las operaciones iniciadas por el usuario; sin telemetría ni conexión automática. Los diez temas, iconos y composición existentes no cambian. La terminal continúa siendo textual, sin emulación ANSI completa. CI y las pruebas de loopback no sustituyen las comprobaciones físicas autorizadas con Mango.
