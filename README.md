# AutoControL Público

Aplicación Android local independiente. Paquete `com.josxtm.autocontrol.community`, versión visible `1.0-comp.X`, `versionCode 2`. No sustituye ni accede a las aplicaciones privadas.

## Módulos

- `app`: interfaz **Kotlin y Jetpack Compose**, diez temas persistentes, navegación de registros, diario, documentos y copias. Ancho adaptable y contenido desplazable en móvil/tablet.
- `storage`: catálogo CSV mensual con actualización incremental en SQLite e índice por matrícula, fichas con selección de campos, notas individuales, diario paginado y documentos privados. Copias portables cifradas y restauración transaccional.
- `documents`: adjuntos inertes aportados por el usuario y edición de texto UTF-8. No ejecuta macros, scripts ni plantillas. Los demás formatos se conservan para exportar su archivo original; no se ofrece edición de DOCX/PDF ni interpretación de documentos oficiales.
- `ocr`: Tesseract4Android 4.9.0 estándar, sin modelos incluidos. Importa explícitamente un modelo traineddata compatible y reconoce imágenes seleccionadas por el usuario **sin red**. El texto reconocido se revisa en el editor antes de guardarlo.

El permiso `INTERNET` se utiliza para SNIFFER LAN y MangoSSH, iniciados explícitamente por el usuario. No hay telemetría ni conexiones automáticas. No se incorporan plantillas, catálogos ni documentos de centros. El diagnóstico directo y el ping remoto expresan resultados tipados; ausencia de respuesta ICMP no confirma desconexión. La terminal interactiva permite comandos arbitrarios solo después de confirmar el acceso a un equipo autorizado. Los diagnósticos predefinidos están restringidos. Capturas y copias automáticas del sistema están deshabilitadas. El selector y el teclado tienen políticas propias: elige proveedores locales autorizados.

## Catálogo GRID MT de eSir: CSV sin conversiones

La pantalla CT permite seleccionar directamente el archivo original CSV/TSV exportado por el técnico, sin convertirlo previamente a JSON. Se admiten separadores punto y coma, coma o tabulador, cabecera con matrícula, campos entre comillas y texto UTF-8 (con/sin BOM) o Windows-1252. La importación no necesita Internet y nunca descarga datos por su cuenta.

**Flujo:** seleccionar archivo → obtener vista previa de altas, modificaciones, sin cambios y CT históricos → confirmar o cancelar. Antes de confirmar, SQLite revierte la simulación completa; al confirmar, todos los cambios se aplican en una única transacción. Un archivo truncado, con matrículas duplicadas o con cabecera inválida no modifica el estado existente.

**Conservación:** los registros con la misma matrícula se actualizan; los nuevos se añaden; los CT que no aparecen en el nuevo CSV permanecen disponibles como registros históricos. Ni las notas asociadas, ni el diario, ni los documentos se borran al actualizar el catálogo. Los campos locales que no figuran en el CSV también se conservan. Se protege la compatibilidad con el identificador de los registros que ya existen; si cambia el identificador y hay datos antiguos, la actualización se rechaza en vez de asociar incorrectamente notas.

**Limitaciones iniciales:** el identificador se busca preferentemente por una cabecera equivalente a `MATRÍCULA`; si no existe, se exige un campo identificador configurado. Las búsquedas continúan siendo exactas. Si la exportación real usa una estructura distinta, será necesario ajustar el mapeo tras revisar una muestra anonimizada. Por ahora se admiten archivos `.csv`, `.tsv` y `.txt`; los libros `.xls`/`.xlsx` no se interpretan directamente hasta validar la estructura real del fichero. Los históricos se conservan y siguen apareciendo en búsquedas exactas, sin marcarse aún como inactivos.

La base SQLite conserva internamente el payload de registros y el formato de copias cifradas anteriores. **Esto no exige archivos JSON al técnico** ni modifica el formato `.acpb`.

Límites: hasta 64 Mi caracteres de CSV; un millón de registros; 100 campos; cabeceras de 128 caracteres; identificadores de 256; celdas de 16000 y registros de 262144. Adjuntos de hasta 4 MiB cada uno y 16 MiB en total.

## Documentos y OCR

Importar copia el archivo al almacenamiento privado. Nuevo texto abre un editor vacío. Guardar conserva su contenido UTF-8; exportar utiliza siempre la última versión guardada, con el selector del sistema. Eliminar requiere confirmación. No hay plantillas ni mapeos incluidos.

OCR requiere importar un modelo con licencia adecuada (hasta 64 MiB). Se valida con el motor antes de sustituir el anterior y se muestra su SHA-256. La aplicación no descarga modelos. La imagen se reduce hasta 4 millones de píxeles y lado máximo de 2048, y se libera tras el reconocimiento. El modelo no se incluye en copias portables: puede reimportarse. Lee [THIRD_PARTY.md](THIRD_PARTY.md); los avisos de licencia de Tesseract, Leptonica, libpng e IJG también están disponibles en la aplicación.

## Copia y recuperación

La copia `.acpb` contiene catálogo, campos visibles, notas, diario y adjuntos. Cifrado AES-256-GCM, sal aleatoria de 16 bytes, nonce de 12 bytes, autenticación de 128 bits y clave derivada con PBKDF2-HMAC-SHA256 (210000 iteraciones). Contraseña de 12 a 256 caracteres: no se almacena ni se puede recuperar. Cada exportación genera sal y nonce nuevos. No utiliza certificados ni claves de otras apps.

Restaurar pide confirmación, comprueba la etiqueta de autenticación completa en un archivo temporal privado y solo entonces valida/aplica el contenido en una transacción SQLite. Contraseña incorrecta, alteración, truncamiento o esquema inválido dejan intactos los datos existentes. El temporal se elimina al finalizar. El límite del contenido descifrado es 64 MiB. Una exportación fallida puede dejar un archivo parcial: elimínalo y repite tras corregir el problema. Guarda la contraseña separada de la copia. Tema y modelo OCR no se incluyen. No restaura copias privadas ni de otras aplicaciones.

## Validación y distribución

Con JDK 17 completo, SDK 35 y Gradle 8.9:

```sh
python3 tools/privacy_guard.py .
python3 -m unittest discover -s tests -v
gradle :storage:testDebugUnitTest :documents:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
python3 tools/inspect_apk.py app/build/outputs/apk/debug/app-debug.apk "$ANDROID_HOME/build-tools/35.0.0"
gradle :app:connectedDebugAndroidTest
```

Las pruebas OCR requieren aportar por separado el modelo de prueba verificado por el workflow en `/data/local/tmp/synthetic-ocr-model.traineddata`; nunca se empaqueta. Todos los registros, documentos e imágenes de prueba se generan sintéticamente. CI ejecuta las pruebas Android en API 35, incluidas recuperación cifrada, integridad, SQLite real, interfaz Compose y OCR sobre una imagen generada. Solo publica la APK si todos los pasos pasan. El artefacto incluye huella del certificado y hash SHA-256 de la APK. Las versiones fijadas de AndroidX tienen avisos Lint de actualización disponibles; no se desactiva ningún control de privacidad ni de análisis.

Las APK debug de CI son **previews públicas con firma efímera**; no actualizan aplicaciones privadas ni garantizan continuidad de firma entre ejecuciones. [SIGNING.md](SIGNING.md) define un futuro canal permanente público independiente. [MIGRATION.md](MIGRATION.md) detalla las limitaciones.

## Estabilización SNIFFER / SSH

Los comandos de diagnóstico tienen un deadline monotónico de 5 segundos por defecto, cancelación que cierra el canal y salida limitada a 64 KiB. Los estados distinguen respuesta, ausencia de respuesta, timeout, error SSH/local, destino inválido, no configurado y cancelación. La sesión se conserva tras un timeout si sigue utilizable. El cierre de un canal bloqueado dispone de un último recurso que aborta el socket.

Host, puerto y usuario se guardan únicamente al pulsar «Guardar perfil». La contraseña no se guarda. Los pines SHA-256 se almacenan localmente por host y puerto, con compatibilidad de los pines anteriores y rechazo de cambios. La UI conserva sus diez temas, iconos y distribución. El ajuste del modo global claro/oscuro mencionado en la auditoría queda pendiente fuera de esta estabilización, para no alterar las paletas aprobadas.

Consulta [SNIFFER](docs/SNIFFER-MANGOSSH-INTEGRATION.md), [seguridad](SECURITY.md) y [trazabilidad de la auditoría](docs/SSH-STABILITY-VALIDATION.md). Las pruebas de integración usan un servidor sintético que escucha exclusivamente en loopback; no validan el firmware Mango ni una red CT.
