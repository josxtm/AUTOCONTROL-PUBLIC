# GUI industrial y catálogo CT — rama de corrección

Base auditada: `0f32cd1`, rama `feat/industrial-gui-shell-correction`.
La referencia es la captura aportada por el usuario, no una interfaz anterior.

## Causas identificadas

- El importador anterior solo aceptaba un array de objetos escalares y un campo identificador configurable, inicialmente `id`. No aceptaba el contenedor versionado de exportación.
- El límite de 100 campos impedía conservar el registro extendido de 380 columnas. Ahora se admiten hasta 512, conservando los demás límites de lectura.
- La selección SNIFFER omitía `IP LAN [EDIT]` y el nombre. Ahora utiliza alias explícitos compartidos con almacenamiento.
- `DELETE records` eliminaba las notas por cascada. La sustitución ahora prepara una tabla temporal y actualiza registros existentes sin borrarlos. Una actualización que excluya centros con notas se rechaza.
- La GUI anterior usaba caracteres como iconos y dos terminales visuales separadas. La nueva composición agrupa escaneo, terminal y comandos; usa geometría vectorial local.

## Contrato de importación

Se conserva el formato genérico anterior: lista de objetos escalares con identificador explícito.
Se reconocen además las matrículas mediante `MATRÍCULA`, `MATRICULA`, `MATRICULA [BDI]`, `CÓDIGO BDI` o `CODIGO BDI`. Nombre mediante `NOMBRE` / `NOMBRE [BDI]`; LAN mediante `IP LAN`, `IP_LAN` o `IP LAN [EDIT]`.
Los alias simultáneos deben coincidir. No se infieren campos por posición en formatos desconocidos.
Las claves y valores originales se conservan; el índice de búsqueda usa matrícula normalizada en mayúsculas y sin espacios. Prefijos CT admitidos: 28 y 45.

Formato extendido conocido: `format=TELECONTROL_CT_V1`, `version=1`, `fieldNames`, `records` indexado por matrícula. Los metadatos deben preceder a `records`. Cada fila contiene 17 valores resumen antes de los campos declarados; las posiciones 15 y 16 son numéricas. Se verifican matrícula de la clave, resumen, columna declarada y nombre. Los valores resumen se conservan con claves internas `__summary_0` a `__summary_16`.

La prueba sintética tiene 363 nombres de campo y 380 valores por fila. No contiene ningún centro ni dirección operacional. El adjunto privado de sesiones anteriores no pudo recuperarse en esta sesión: esta ejecución no constituye una prueba con ese archivo real.

Un duplicado canónico, alias ambiguo, estructura incompatible o UTF-8 inválido revierte la operación completa. Las matrículas fuera del formato CT se contabilizan como rechazadas; se exige al menos un registro válido. El informe de éxito solo se muestra después del commit SQLite e incluye archivo, incorporados, rechazados, motivo y fecha. No se envían datos a servidores.

## SSH y diagnóstico

`SshTransport`, `EndpointPolicy`, `CommandHistoryPolicy` y `LanTargets` se conservan sin cambios. La sesión pertenece al panel, fuera del contenido de las cuatro pestañas. Cambiar de CT cancela el escaneo, retira sus resultados y cierra la sesión para evitar mezclar centros. Salir del módulo cierra la sesión.

La huella continúa verificándose por el transporte existente: aceptación explícita inicial, rechazo de cambio y almacenamiento local. La terminal interactiva requiere confirmación administrativa. Los comandos rápidos adicionales usan esa terminal autorizada; no amplían la lista del canal de diagnóstico.

Los pings muestran solo tiempos medidos. Sin respuesta ICMP no demuestra desconexión. La disponibilidad de Ethernet/Wi-Fi procede de Android y tampoco demuestra alcance al CT. El permiso normal `ACCESS_NETWORK_STATE` se añade exclusivamente para esa indicación. El registro exportado contiene eventos operativos sin contraseña, comandos, salida de terminal, matrícula, host o usuario.

## Verificación visual y CI

El workflow conserva las pruebas anteriores, Lint, control de privacidad y comprobación de firma. Configura un emulador horizontal de 1920 × 1200 a 240 dpi. `CatalogSnifferTest` importa una fila sintética, reabre SQLite, busca en Compose y verifica las siete IP calculadas, además de navegar por las pestañas. Su captura procede de `UiAutomation.takeScreenshot()`.
Solo la ventana del test con datos sintéticos permite captura; la aplicación conserva `FLAG_SECURE`.
La captura se publica en el artefacto de validación bajo `android-screenshots/tablet-sniffer.png` cuando termina satisfactoriamente.

El aspecto se evaluará sobre esa captura real. No se declara identidad visual ni funcionamiento SSH físico a partir de una compilación. No se han realizado conexiones a equipos reales ni escrituras sobre equipos.

La firma debug mantiene la política existente de PR #6; puede variar entre ejecutores. No se garantiza actualización sobre una APK firmada con otra clave.
