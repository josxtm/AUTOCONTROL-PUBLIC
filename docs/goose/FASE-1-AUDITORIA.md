# AUTOCONTROL-PUBLIC — Fase 1
## Informe de auditoría y estabilización técnica

**Repositorio:** `josxtm/AUTOCONTROL-PUBLIC`  
**Rama de referencia:** `feat/sniffer-mangossh-full-integration`  
**Rama del informe:** `docs/goose-fase1-auditoria`  
**HEAD auditado:** `a4fff705072df9274687e8d25a316c9cb80f13f3`  
**Fecha de auditoría:** 2026-11-13  
**Modo:** solo lectura; no se inspeccionaron datos privados ni se ejecutaron diagnósticos sobre equipos reales.

> Este documento contiene únicamente análisis técnico y recomendaciones. No contiene catálogos, credenciales, IP operativas, claves, procedimientos privados ni datos reales de CT.

---

## 1. Alcance y estado

Se revisaron:

- Estado Git, ramas, HEAD e historial relevante.
- Arquitectura Kotlin/Compose y módulos Android.
- `SshTransport.java`, `SnifferPanel.kt` y las políticas de endpoints.
- Gestión de contraseñas y huellas SSH.
- Resultados de diagnósticos LAN y MangoSSH.
- Los diez temas existentes y la aplicación del modo claro/oscuro.
- Historial de la GUI industrial y referencias BDI/PES.
- Manifiesto Android, `README.md`, `MIGRATION.md`, `SECURITY.md` y documentación SNIFFER.
- Estructura de pruebas y criterios necesarios para una implementación posterior.

No se modificó código durante la auditoría, no se realizaron conexiones de red, no se accedió a datos privados, no se ejecutaron acciones sobre equipos y no se hizo merge a `main`.

Estado de referencia observado:

```text
Rama de referencia:
feat/sniffer-mangossh-full-integration

HEAD:
a4fff705072df9274687e8d25a316c9cb80f13f3

Mensaje:
fix(androidTest): recognize versioned AutoControL tablet app title
```

La rama de documentación se creó separada de la rama de código. Este commit debe contener únicamente documentación de Goose.

---

## 2. Resumen ejecutivo

La rama auditada contiene una aplicación Android pública con:

- Kotlin y Jetpack Compose.
- Catálogo local genérico para registros CT.
- Diagnóstico SNIFFER LAN.
- Transporte SSH integrado de tipo MangoSSH.
- Almacenamiento SQLite, documentos, OCR local y copias cifradas.
- Diez temas visuales persistentes.
- Pruebas unitarias, instrumentadas, de privacidad e inspección de APK.

Los principales riesgos técnicos de Fase 1 son:

1. **Bloqueo indefinido de lectura SSH:** `ChannelExec` tiene timeout de conexión, pero no timeout total de comando ni cancelación vinculada al cierre del canal.
2. **Resultados no tipados:** SNIFFER utiliza mapas de cadenas y puede confundir ausencia de respuesta, timeout y error.
3. **Ausencia de respuesta ICMP:** no debe clasificarse automáticamente como equipo desconectado.
4. **Contraseña SSH:** JSch recibe la contraseña como `String`, por lo que solo puede minimizarse su permanencia, no garantizarse el borrado de la copia interna.
5. **Pinning:** la política TOFU es adecuada como base, pero el almacenamiento se indexa por host y debería considerar también el puerto.
6. **Temas:** se calcula el modo sistema/claro/oscuro en `MainActivity`, pero la variable calculada no se usa; el esquema aplicado depende únicamente de `tabletTheme`.
7. **Documentación contradictoria:** `README.md` y `MIGRATION.md` describen una aplicación sin Internet ni diagnósticos de infraestructura, mientras que el manifiesto y el código incluyen SNIFFER LAN/MangoSSH y `android.permission.INTERNET`.
8. **BDI/PES:** no existe un motor BDI/PES funcional en HEAD. Solo hay referencias históricas de interfaz y una pantalla documental que indicaba que no existía motor oficial.

La Fase 1 debe ser de estabilización, no de reconstrucción visual. Debe conservar MangoSSH, SNIFFER LAN, los diez temas, los iconos V10 y la GUI existente.

---

## 3. Bloqueos potenciales de lectura SSH

### 3.1 Código actual

Archivo:

```text
app/src/main/java/com/josxtm/autocontrol/community/sniffer/SshTransport.java
```

La lectura de un comando remoto sigue este patrón:

```java
while ((count = stdout.read(buffer)) >= 0) {
    ...
}
```

El canal utiliza un timeout de conexión:

```java
channel.connect(10000);
```

Pero no se observa un timeout total de ejecución o lectura.

### 3.2 Riesgo

Después de conectar, el canal puede quedar bloqueado si:

- El equipo remoto acepta el canal pero no termina el comando.
- El firmware no soporta las opciones enviadas.
- El proceso remoto queda abierto.
- Se produce salida parcial sin EOF.
- La coroutine Kotlin se cancela mientras el `InputStream` Java continúa bloqueado.
- La UI desaparece y el canal permanece vivo.

La shell interactiva tiene un bloqueo de lectura intencionado, pero también requiere un cierre explícito al desconectar, destruir el panel o perder la sesión.

### 3.3 Solución propuesta

Añadir una API interna con timeout total y cancelación, manteniendo wrappers compatibles si la GUI actual los necesita:

```java
DiagnosticResult diagnostic(
    String command,
    long timeoutMillis,
    CancellationToken cancellation
) throws Exception;
```

Flujo requerido:

1. Validar el comando mediante `EndpointPolicy`.
2. Obtener la sesión activa.
3. Abrir `ChannelExec`.
4. Registrar el canal como operación activa.
5. Conectar con timeout de conexión.
6. Leer hasta EOF, límite de salida, timeout total o cancelación.
7. Ante timeout o cancelación, desconectar activamente el canal.
8. Cerrar streams y canal en `finally`.
9. Retirar el canal del registro de operaciones.
10. Devolver un resultado tipado, no una cadena ambigua.

La cancelación no debe depender únicamente de `Thread.interrupt()`, porque un `InputStream` de JSch puede no despertar de forma fiable. La señal de cancelación debe estar vinculada a `channel.disconnect()`.

Estrategias posibles:

- Lectura con sondeo y deadline monotónico.
- Hilo/future supervisor que cierre el canal al vencer el deadline.

La implementación elegida debe impedir hilos huérfanos y garantizar cierre en todos los caminos.

### 3.4 Cierre seguro

El transporte debe cerrar silenciosamente:

- `stdout`.
- `stderr`, si existe.
- Stream de entrada del canal.
- `ChannelExec` o `ChannelShell`.

`disconnect()` debe ser idempotente y no ocultar un recurso abierto por haber ocurrido una excepción durante la limpieza.

El timeout de un comando no debería destruir automáticamente toda la sesión SSH, salvo que JSch deje la sesión en estado no reutilizable.

---

## 4. Resultados tipados de SNIFFER LAN y MangoSSH

### 4.1 Problema actual

`SnifferPanel.kt` mantiene:

```kotlin
Map<String, String>
```

Y presenta textos como:

```text
responde
sin respuesta
error de diagnóstico
No configurado
IP no válida
```

El estado real se pierde al convertirlo a texto.

### 4.2 Modelo propuesto

Crear un modelo en el paquete `sniffer`, preferiblemente Java para que sea consumible por `SshTransport.java`:

```java
public enum DiagnosticStatus {
    VALID_RESPONSE,
    NO_RESPONSE,
    TIMEOUT,
    SSH_ERROR,
    LOCAL_ERROR,
    INVALID_TARGET,
    NOT_CONFIGURED,
    CANCELLED
}
```

```java
public enum DiagnosticSource {
    DIRECT_ANDROID,
    MANGO_SSH
}
```

```java
public final class DiagnosticResult {
    private final String targetName;
    private final String address;
    private final DiagnosticSource source;
    private final DiagnosticStatus status;
    private final long elapsedMillis;
    private final String detail;
    private final Integer exitCode;
}
```

El modelo no debe incluir contraseñas, credenciales ni salida completa por defecto.

### 4.3 Semántica

| Situación | Estado |
|---|---|
| Host responde y el resultado se interpreta correctamente | `VALID_RESPONSE` |
| Terminó la espera sin respuesta observable | `NO_RESPONSE` |
| Venció el timeout total | `TIMEOUT` |
| Falló sesión, autenticación o canal SSH | `SSH_ERROR` |
| Falló la comprobación local | `LOCAL_ERROR` |
| IP o CIDR inválidos | `INVALID_TARGET` |
| Equipo excluido por el usuario | `NOT_CONFIGURED` |
| Usuario canceló la operación | `CANCELLED` |

La UI debe mostrar “sin respuesta observada” y no “desconectado” cuando `isReachable()` devuelve `false`.

### 4.4 Diagnóstico directo

Mapeo recomendado:

- `isReachable() == true` → `VALID_RESPONSE`.
- `isReachable() == false` → `NO_RESPONSE`.
- Expiración de espera → `TIMEOUT`.
- Error local de resolución o IO → `LOCAL_ERROR`.

No debe convertirse toda excepción en ausencia de respuesta.

### 4.5 Diagnóstico MangoSSH

Mapeo recomendado:

- Comando terminado y respuesta interpretable → `VALID_RESPONSE`.
- Comando terminado sin evidencia de respuesta → `NO_RESPONSE`.
- Deadline excedido → `TIMEOUT`.
- Sesión, autenticación o canal fallido → `SSH_ERROR`.
- Cancelación explícita → `CANCELLED`.

La clasificación no debe depender exclusivamente de frases concretas de `ping`, porque BusyBox, firmware y localización pueden cambiar los textos.

---

## 5. Contraseñas SSH y huellas

### 5.1 Contraseña

La UI convierte temporalmente el valor a `char[]` y limpia el campo Compose. Esta dirección es correcta.

Después, `SshTransport` realiza:

```java
active.setPassword(new String(password));
```

La API de JSch requiere una cadena para `Session.setPassword`. Por ello no puede garantizarse el borrado de la copia interna administrada por la JVM.

La corrección debe:

1. Mantener el estado de contraseña solo mientras sea necesario.
2. Convertir a `char[]` justo antes de conectar.
3. No duplicar el array en capas adicionales.
4. Crear el `String` de JSch únicamente en el punto de llamada.
5. Limpiar el array inmediatamente después de entregarlo a JSch.
6. Limpiarlo también en errores y cancelaciones.
7. No escribirlo en preferencias, logs, historial, excepciones o portapapeles.
8. Documentar la limitación inherente de `setPassword(String)`.

No debe afirmarse que la contraseña se elimina completamente de memoria.

### 5.2 Cancelación durante conexión

La cancelación de la coroutine Kotlin no garantiza por sí sola la cancelación de `Session.connect()`.

El transporte debería conservar la referencia de la sesión en conexión o utilizar un token compartido, de forma que la UI pueda cerrar activamente la operación sin dejar una conexión bloqueada.

### 5.3 Huellas

La implementación actual:

- Usa `StrictHostKeyChecking=yes`.
- Calcula una huella SHA-256.
- Pide aprobación explícita la primera vez.
- Acepta una huella previamente conocida.
- Rechaza cambios posteriores.

Esto debe conservarse.

Mejora recomendada: indexar el pin por host y puerto, no solo por host:

```text
pin.<sha256(host + ":" + port)>
```

Debe mantenerse el rechazo de una clave cambiada y no introducirse ningún fallback a `StrictHostKeyChecking=no`.

Una futura interfaz de mantenimiento podría permitir borrar o restablecer pines, pero no es necesario rediseñarla en Fase 1.

---

## 6. Diez temas y modo claro/oscuro

Archivo:

```text
app/src/main/java/com/josxtm/autocontrol/community/AutoThemes.kt
```

Temas existentes:

1. T1 Claro Profesional.
2. T2 Azul Corporativo.
3. T3 Verde Técnico.
4. T4 Gris Industrial.
5. T5 Arena / Trabajo de Campo.
6. T6 Noche Suave.
7. T7 Rojo Operativo.
8. T8 Púrpura Técnico.
9. T9 Turquesa Moderno.
10. T10 Inspiración macOS.

No se debe eliminar ninguno ni modificar los iconos V10 o la GUI aprobada.

En `MainActivity.kt` se calcula:

```kotlin
val dark = model.theme == 2 ||
    (model.theme == 0 && isSystemInDarkTheme())
```

Pero la variable no se utiliza. El esquema se crea únicamente con:

```kotlin
MaterialTheme(colorScheme = autoColorScheme(model.tabletTheme))
```

Consecuencias:

- El modo sistema no se aplica.
- El modo claro/oscuro genérico no se aplica.
- `cycleTheme()` existe pero no está conectado a una acción visible.
- La paleta T6 sí define `dark`, pero el comportamiento global no está modelado claramente.

Corrección recomendada:

```text
themeMode:
  SYSTEM
  LIGHT
  DARK

tabletTheme:
  0..9
```

La selección debe usar el modo global sin eliminar ninguna paleta:

```kotlin
val useDark = when (model.theme) {
    0 -> isSystemInDarkTheme()
    1 -> false
    2 -> true
    else -> isSystemInDarkTheme()
}
```

Después debe pasarse `useDark` a la función que construye el esquema. La corrección debe afectar a la selección del esquema, no rediseñar la pantalla.

---

## 7. Historial de GUI industrial y BDI/PES

Commits históricos relevantes:

```text
b43cd96 feat(ui): restore tablet industrial app shell,
        persistent header, compact rail and CT dual-pane workbench

c4b314a feat(sniffer-ui): apply approved diagnostic B
        three-pane layout on validated MangoSSH integration

541ec6b feat(gui): compact tablet rail, Sniffer screen
        and ten theme selector

aafebe4 feat(gui): extend industrial right-side controls
        to Inicio and PES/BDI tablet screens

1e5ae31 testability(gui): stable Compose identifiers
        for editable fields and action controls
```

En el commit histórico `aafebe4` aparecieron componentes como:

```text
app/src/main/java/com/josxtm/autocontrol/community/IndustrialCtPanel.kt
app/src/main/java/com/josxtm/autocontrol/community/IndustrialModuleTools.kt
app/src/main/java/com/josxtm/autocontrol/community/IndustrialWidgets.kt
app/src/main/java/com/josxtm/autocontrol/community/sniffer/LanStatus.kt
```

La interfaz histórica incluía cabecera persistente, rail lateral, iconos V10, Inicio, Centros CT, SNIFFER LAN, herramientas laterales y una pantalla “PES / BDI”.

El HEAD actual no contiene esos archivos. No se recomienda recuperarlos en Fase 1 porque la misión es estabilización técnica y las restricciones indican conservar la GUI actual y no modificar BDI/PES.

BDI/PES no existe como motor funcional en HEAD. La pantalla histórica indicaba que los documentos y procedimientos PES eran los importados por el técnico y que no existía motor BDI oficial. Por tanto, no deben interpretarse las referencias históricas como una implementación disponible.

---

## 8. Contradicciones entre documentación y permisos

### 8.1 AndroidManifest

`app/src/main/AndroidManifest.xml` declara:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

Además documenta que el permiso se usa para SNIFFER y conexiones SSH iniciadas explícitamente por el usuario.

### 8.2 README

`README.md` afirma que no hay permiso de Internet ni diagnósticos de infraestructura. Esto contradice el código actual, que incluye:

- SSH desde Android a MangoSSH.
- Diagnóstico directo LAN.
- Ping remoto desde MangoSSH.
- Terminal SSH interactiva.

### 8.3 MIGRATION

`MIGRATION.md` afirma que no hay redes, diagnósticos operativos ni servicios remotos. Esta afirmación tampoco coincide con la rama auditada.

`tools/inspect_apk.py`, en cambio, ya admite explícitamente `android.permission.INTERNET`, por lo que la validación de APK refleja mejor el estado real que la documentación.

### 8.4 Corrección documental

Debe actualizarse `README.md`, `MIGRATION.md`, `SECURITY.md` y `docs/SNIFFER-MANGOSSH-INTEGRATION.md` para indicar:

- `INTERNET` existe exclusivamente para SNIFFER LAN/MangoSSH.
- No hay telemetría.
- No hay conexiones automáticas.
- La conexión comienza por acción explícita del usuario.
- Host, puerto y usuario pueden guardarse opcionalmente; la contraseña no.
- Las huellas SSH se almacenan localmente para pinning.
- La terminal interactiva puede ejecutar comandos arbitrarios en equipos autorizados.
- Los diagnósticos predefinidos están restringidos.
- Una ausencia de respuesta ICMP no confirma desconexión.
- La compatibilidad del ping remoto depende del firmware Mango.
- CI no prueba conexiones SSH reales.

Debe seguir utilizándose información sintética y no incluir datos operativos.

---

## 9. Guion de implementación para Codex

### Archivos previstos

```text
app/src/main/java/com/josxtm/autocontrol/community/sniffer/SshTransport.java
app/src/main/java/com/josxtm/autocontrol/community/sniffer/SnifferPanel.kt
app/src/main/java/com/josxtm/autocontrol/community/sniffer/DiagnosticResult.java
app/src/main/java/com/josxtm/autocontrol/community/sniffer/DiagnosticStatus.java
app/src/main/java/com/josxtm/autocontrol/community/sniffer/DiagnosticSource.java
app/src/main/java/com/josxtm/autocontrol/community/MainActivity.kt
app/src/main/java/com/josxtm/autocontrol/community/AutoThemes.kt
app/src/main/java/com/josxtm/autocontrol/community/WorkspaceModel.kt
README.md
MIGRATION.md
SECURITY.md
docs/SNIFFER-MANGOSSH-INTEGRATION.md
app/src/test/java/com/josxtm/autocontrol/community/sniffer/...
app/src/androidTest/java/com/josxtm/autocontrol/community/...
```

### Orden de implementación

1. Crear estados y resultados tipados.
2. Añadir timeout total al transporte.
3. Añadir cancelación vinculada a `disconnect()`.
4. Garantizar cierre de canales y streams.
5. Revisar el ciclo de vida de la contraseña.
6. Indexar el pin SSH por host y puerto.
7. Adaptar `SnifferPanel` sin rediseñar la GUI.
8. Corregir la aplicación de `themeMode` manteniendo los diez temas.
9. Actualizar documentación y privacy guard si fuera necesario.
10. Añadir y ejecutar pruebas.

No recuperar en esta fase componentes históricos de GUI ni modificar BDI/PES.

---

## 10. Pruebas automatizadas requeridas

### Resultados

Crear pruebas sintéticas para:

- `VALID_RESPONSE`.
- `NO_RESPONSE`.
- `TIMEOUT`.
- `SSH_ERROR`.
- `NOT_CONFIGURED`.
- `INVALID_TARGET`.
- `CANCELLED`.
- Origen directo y MangoSSH.

### Transporte

Usar dobles de prueba o una abstracción de canal para comprobar:

- Stream que no termina.
- Salida parcial.
- Timeout total.
- Cancelación durante la lectura.
- Cierre del canal tras timeout.
- Cierre en excepciones.
- Ausencia de hilos huérfanos.

No conectar equipos reales.

### Huellas

Comprobar con bytes o claves sintéticas:

- Primera aceptación.
- Primera denegación.
- Reutilización de huella.
- Rechazo de cambio.
- Diferenciación por puerto.
- Error de persistencia.

### Contraseñas

Usar únicamente valores sintéticos, por ejemplo:

```text
synthetic-password-123
```

Verificar:

- No se guarda en preferencias.
- No aparece en historial.
- No aparece en logs o excepciones.
- El array se limpia en éxito, error y cancelación.
- La documentación no afirma borrado total del `String` interno de JSch.

### LAN

Comprobar:

- Estados tipados.
- CIDR `/24`, `/27` y `/30`.
- Broadcast rechazado.
- Offset fuera de rango.
- Equipo no configurado.
- Cancelación.
- Diferencia entre timeout y no respuesta.

### Temas

Comprobar que:

- Los diez índices siguen disponibles.
- La selección persiste.
- Claro, oscuro y sistema producen esquemas correctos.
- SNIFFER sigue accesible.
- No se eliminan iconos ni referencias visuales aprobadas.

### Privacidad y documentación

Ejecutar:

```sh
python3 tools/privacy_guard.py .
python3 -m unittest discover -s tests -v
```

No introducir IP operativas, credenciales, claves, catálogos o archivos privados.

---

## 11. Criterios de aceptación

### SSH

- Ningún `ChannelExec` puede leer indefinidamente.
- Un comando que excede el límite devuelve `TIMEOUT`.
- La cancelación cierra el canal.
- El canal se cierra en excepciones.
- La shell interactiva puede cerrarse sin dejar la sesión bloqueada.

### Diagnóstico

- La UI diferencia timeout, error SSH, no respuesta y no configurado.
- `false` de `isReachable()` no se presenta como “desconectado”.
- Las direcciones inválidas no llegan al transporte remoto.
- El estado no se deduce de texto libre.

### Contraseñas

- Nunca se guardan en `SharedPreferences`.
- No aparecen en historial, logs, excepciones o portapapeles.
- El array temporal se limpia siempre.
- Se documenta la limitación de `Session.setPassword(String)`.

### Huellas

- La primera aceptación es explícita.
- Una huella cambiada se rechaza.
- El pin distingue host y puerto.
- No existe fallback a `StrictHostKeyChecking=no`.

### Temas y GUI

- Los diez temas continúan disponibles.
- La selección continúa persistiendo.
- El modo sistema/claro/oscuro se aplica realmente.
- No se rediseñan pantallas.
- MangoSSH permanece integrado en SNIFFER LAN.
- No se modifica BDI/PES.

### Documentación

- README, MIGRATION, SECURITY y la documentación de SNIFFER describen el permiso `INTERNET`.
- Se declara la ausencia de telemetría.
- Se explican las limitaciones de ICMP, JSch y firmware Mango.
- No se incluyen datos confidenciales.

### Verificación

- Las pruebas unitarias pasan.
- El privacy guard pasa.
- Lint pasa si hay Android SDK local.
- Si no hay SDK, se informa y no se afirma que la APK está compilada.
- No se realizan conexiones a equipos reales.

---

## 12. Riesgos

| Riesgo | Mitigación |
|---|---|
| El stream de JSch no responde a interrupción | Desconectar explícitamente el canal desde timeout/cancelación |
| `setPassword(String)` deja una copia administrada | Minimizar alcance y documentar la limitación |
| Un timeout deja la sesión inconsistente | Comprobar estado y permitir reconexión limpia |
| El texto de `ping` varía por firmware | Separar estado de transporte y clasificación de salida |
| Cambios de API rompen la UI | Mantener wrappers compatibles durante la transición |
| Corregir temas altera colores aprobados | Mantener paletas y cambiar solo aplicación del modo |
| Documentación activa el privacy guard | Utilizar únicamente datos sintéticos |
| Recuperar GUI histórica amplía el alcance | No restaurar componentes visuales en Fase 1 |

---

## 13. Conclusión

La prioridad de Fase 1 es estabilizar la comunicación SSH y hacer que los diagnósticos expresen estados reales sin inferencias peligrosas. El cambio más importante es incorporar timeout total y cancelación efectiva, con cierre seguro de `ChannelExec` y streams.

En paralelo, deben introducirse resultados tipados, revisarse las limitaciones de credenciales JSch y corregirse la aplicación del modo claro/oscuro sin eliminar ninguno de los diez temas.

La documentación debe alinearse con el comportamiento real: esta rama sí contiene SNIFFER LAN, MangoSSH, diagnósticos y permiso `INTERNET`, aunque no tenga telemetría ni conexiones automáticas.

BDI/PES no debe modificarse en esta fase. La GUI industrial histórica queda identificada como referencia de commits anteriores, pero no se debe recuperar ni reconstruir dentro de esta entrega de estabilización.
