# Fase 1 — estabilización SSH y SNIFFER

Base: `a4fff705072df9274687e8d25a316c9cb80f13f3`, `feat/sniffer-mangossh-full-integration`.
Rama: `fix/autocontrol-ssh-diagnostics-stability`.
Referencia: [auditoría Goose](https://github.com/josxtm/AUTOCONTROL-PUBLIC/blob/docs/goose-fase1-auditoria/docs/goose/FASE-1-AUDITORIA.md).

## Cambios contrastados con la auditoría

| Hallazgo | Corrección / evidencia |
|---|---|
| Lectura SSH sin deadline | CommandRunner: plazo monotónico, cancelación activa, cierre de pipe y canal; pruebas de salida parcial, canal pendiente, lectura detenida y excepción. |
| synchronized bloquea desconexión | Monitor solo de referencias; socket propiedad del adaptador; desconexión concurrente con conexión/diagnóstico; prueba real con JSch. |
| Cadenas ambiguas | Modelos tipados; pruebas para todos los estados y ambos orígenes, sin inferencia por texto de ping. |
| Credenciales String | Verificación del JAR y fuentes oficiales 2.28.8: existe setPassword(byte[]). Tests de codificación UTF-8 y limpieza en éxito/error/cancelación. |
| Pines sin puerto explícito | Namespace v2 por endpoint, compatibilidad exacta con las claves antiguas de JSch, rechazo de cambios y fallos de persistencia. |
| Documentación sin INTERNET | README, MIGRATION, SECURITY y documento SNIFFER corregidos. |
| Temas / GUI | Archivos de temas, MainActivity, recursos y BDI/PES intactos. No se incorpora el rediseño de otra rama. El cambio de modo global se difiere para respetar la restricción visual de esta fase. |

## Comprobaciones locales

- 29 pruebas JUnit de SNIFFER/SSH superadas, repetidas tras las correcciones finales: políticas existentes, resultados tipados, deadline, cancelación, recursos, contraseñas y pines.
- Cuatro pruebas de integración con JSch 2.28.8 y un servidor Apache MINA SSHD 2.14.0 efímero, ligado exclusivamente a loopback. Claves generadas en memoria; ningún equipo CT.
- El transporte permite reutilizar la sesión tras el timeout del comando. Cancelación y desconexión liberan el canal real; el lector de shell termina. Un pin modificado se rechaza antes de pedir nueva aceptación.
- La compilación Android local está bloqueada por la política de acceso a JitPack, necesaria para el OCR preexistente. Esto no se presenta como un fallo del motor SSH ni se elimina OCR para eludirlo.

- 9 pruebas adicionales de almacenamiento/documentos y 10 pruebas Python de privacidad superadas localmente: 48 comprobaciones locales en total, además del filtro estático.
- [Actions de a4addde](https://github.com/josxtm/AUTOCONTROL-PUBLIC/actions/runs/43344895936): todos los pasos en verde, incluidos Kotlin/JUnit, Android Lint, APK, inspección de firma y pruebas instrumentadas Compose/SQLite/OCR. Se comprobó el estado de cada paso en la página pública del trabajo.

Las correcciones posteriores deben verificarse también en la ejecución correspondiente al commit final. La confirmación de CI no equivale a validación física de la APK en tablet.

## Revisión de seguridad

No cambia StrictHostKeyChecking ni se amplía el allowlist de diagnóstico. No hay hosts, credenciales o claves operativas en el código. No se toca SQLite, documentos, esquema o almacenes privados. Los pines se migran con alcance exacto y sin borrados masivos. La cancelación termina los recursos, no solo la coroutine. Un cierre atascado puede abortar toda la sesión como último recurso, situación que debe indicarse mediante el estado real.

## Pendiente en tablet

Comprobar pérdida de cable/Wi-Fi, suspensión/reanudación, timeout con firmware real, primeras huellas y huellas cambiadas, varios puertos, terminal interactiva y cancelación durante conexión. Verificar ruta y compatibilidad de opciones de ping. Las APK debug mantienen firma efímera; no se garantiza actualización sobre otra firma. No se declara validación de campo ni compatibilidad de todos los equipos.
