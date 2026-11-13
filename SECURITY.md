# Seguridad de la edición pública

## Política obligatoria

Este proyecto es una edición independiente de software Android. Nunca se incluirán archivos con instalaciones reales, credenciales, claves, firmas, datos de usuarios, procedimientos internos, documentación de terceros no redistribuible ni parámetros operativos específicos.

Se prohíbe reutilizar el historial Git del repositorio privado, incluso tras eliminar archivos. Las pull requests, los artefactos y los commits antiguos pueden conservar información.

## Revisiones previas a cualquier PR

1. Ejecutar `python3 tools/privacy_guard.py .`.
2. Inspeccionar manualmente cada archivo nuevo, cambio de código, recurso binario y documentación.
3. Ejecutar pruebas y compilar APK en un entorno sin secretos.
4. Examinar recursos empaquetados, logs y artefactos.
5. Verificar `applicationId`, `versionCode` y huella de firma de la APK antes de decir que actualiza otra aplicación.

**No copiar claves privadas al repositorio público.** Nunca asignar credenciales de firma privadas a un workflow ejecutado sobre una pull request externa.

El código genérico puede ser público; los complementos que requieran conocimientos internos deben permanecer en proyectos privados.

## SNIFFER LAN / MangoSSH

- `android.permission.INTERNET` permite las conexiones solicitadas explícitamente desde SNIFFER. No hay telemetría, conexiones automáticas ni destinos incorporados.
- `StrictHostKeyChecking=yes` se mantiene. El primer pin SHA-256 requiere confirmación fuera de banda; cambios de huella y fallos de persistencia se rechazan. La identidad está ligada a host y puerto. La migración de pines antiguos nunca amplía su alcance a otro puerto.
- Los comandos de diagnóstico están limitados por `EndpointPolicy`. La terminal interactiva admite comandos arbitrarios tras confirmación administrativa; no es un entorno de solo lectura ni una barrera frente a comandos que el técnico escriba manualmente. No se automatizan operaciones de control o configuración.
- Ausencia de respuesta ICMP no demuestra desconexión. Los códigos de salida remotos y los errores de transporte se separan de textos localizados. Un código de error o stderr no se muestra como respuesta válida.
- Credenciales: JSch **2.28.8** ofrece `Session.setPassword(byte[])`. El transporte codifica UTF-8 sin crear un `String` adicional y borra sus arrays de caracteres/bytes, también en fallo o cancelación. JSch copia internamente esos bytes y su `Session.connect` los limpia en `finally`. No se garantiza borrado completo: Compose/teclado, bibliotecas y JVM pueden mantener otras copias. No se guardan contraseñas en preferencias, logs, historial ni portapapeles.
- La salida de terminal permanece en memoria acotada y puede contener datos sensibles del servidor. «Copiar salida» es una acción explícita del usuario. El filtro de historial evita patrones comunes de secretos, sin garantizar que detecte todos los posibles valores sensibles. No se exporta ni sincroniza automáticamente.
- Los comandos de diagnóstico tienen timeout total; la cancelación cierra pipes/canal. El monitor de estado nunca abarca operaciones de red. Desconectar cierra primero el socket para despertar escrituras y lecturas bloqueadas. Si el cierre de canal se atasca, un supervisor independiente aborta su socket después de 250 ms; esa situación obliga a reconectar.
- CI prueba JSch contra un servidor sintético en loopback, sin equipos reales ni claves estáticas. El servidor es una dependencia exclusiva de pruebas, no incluida en la APK. Las pruebas físicas de firmware, Ethernet/Wi-Fi, pérdida de enlace y suspensión Android siguen pendientes.
