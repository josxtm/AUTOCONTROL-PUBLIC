# AutoControL Público

Aplicación Android local independiente. Paquete `com.josxtm.autocontrol.community`, versión `0.3.0-public`, `versionCode 3`. No sustituye ni accede a las aplicaciones privadas.

## Módulos

- `app`: interfaz **Kotlin y Jetpack Compose**, temas sistema/claro/oscuro, navegación de registros, diario, documentos y copias. Ancho adaptable y contenido desplazable en móvil/tablet.
- `storage`: catálogo JSON incremental, índice SQLite por identificador configurable, fichas con selección de campos, notas individuales, diario paginado y documentos privados. Copias portables cifradas y restauración transaccional.
- `documents`: adjuntos inertes aportados por el usuario y edición de texto UTF-8. No ejecuta macros, scripts ni plantillas. Los demás formatos se conservan para exportar su archivo original; no se ofrece edición de DOCX/PDF ni interpretación de documentos oficiales.
- `ocr`: Tesseract4Android 4.9.0 estándar, sin modelos incluidos. Importa explícitamente un modelo traineddata compatible y reconoce imágenes seleccionadas por el usuario **sin red**. El texto reconocido se revisa en el editor antes de guardarlo.

No hay permiso de Internet, telemetría, diagnósticos de infraestructura, plantillas, catálogos ni documentos incorporados. El diagnóstico solo indica esquema local, número de documentos y disponibilidad del modelo. Capturas y copias automáticas del sistema están deshabilitadas. El selector y el teclado tienen políticas propias: elige proveedores locales autorizados.

## Interfaz Material 3 y movimiento

La edición 0.3.0 incorpora navegación horizontal animada entre las cuatro secciones, chips de navegación con estado seleccionado, microinteracciones de presión en las acciones principales, accesos rápidos desplegables y paneles expansibles para la información de registros y copias. Se utilizan componentes estándar de Compose Material 3 sin una biblioteca externa, respetando las escalas de animación configuradas en Android.

Los cambios son exclusivamente visuales: no alteran esquemas, almacenamiento, OCR, funciones de importación, exportación ni copia. Se mantiene el mismo `applicationId` público, pero las APK debug firmadas temporalmente pueden requerir desinstalar la preview pública anterior (lo que borra sus datos). Consulta [la guía de movimiento](docs/MATERIAL3_MOTION.md) y [SIGNING.md](SIGNING.md).

## Catálogos y trabajo

Importa una lista JSON de objetos con valores simples (texto, números, booleanos o `null`), sin objetos/listas anidados. Ejemplo **sintético**:

```text
[{"id":"SYN-001","label":"Synthetic example","active":true}]
```

Configura `id` como campo identificador para ese ejemplo. Búsqueda exacta y sensible a mayúsculas; conserva ceros iniciales en cadenas. Los valores se muestran y conservan como texto; `null` se muestra vacío. Rechaza campos o identificadores repetidos, identificadores nulos/vacíos, JSON inválido y contenido sobrante. Una importación correcta sustituye catálogo y notas con confirmación; conserva diario y documentos. Si falla, revierte todos los cambios.

Límites: 64 Mi de caracteres de JSON; un millón de registros; 100 campos distintos; nombres de campo de 128 caracteres; identificadores de 256; valores, notas, entradas y textos editables de 16000; 262144 caracteres acumulados por registro; tokens serializados de 65536 caracteres. Diario en páginas de 25 entradas. Adjuntos de hasta 4 MiB cada uno, 16 MiB en total y 200 documentos; los blobs SQLite se leen por fragmentos. Los textos más largos y los archivos binarios se conservan como adjuntos sin edición.

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
gradle :storage:testDebugUnitTest :documents:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
python3 tools/inspect_apk.py app/build/outputs/apk/debug/app-debug.apk "$ANDROID_HOME/build-tools/35.0.0"
gradle :app:connectedDebugAndroidTest
```

Las pruebas OCR requieren aportar por separado el modelo de prueba verificado por el workflow en `/data/local/tmp/synthetic-ocr-model.traineddata`; nunca se empaqueta. Todos los registros, documentos e imágenes de prueba se generan sintéticamente. CI ejecuta las pruebas Android en API 35, incluidas recuperación cifrada, integridad, SQLite real, interfaz Compose y OCR sobre una imagen generada. Solo publica la APK si todos los pasos pasan. El artefacto incluye huella del certificado y hash SHA-256 de la APK. Las versiones fijadas de AndroidX tienen avisos Lint de actualización disponibles; no se desactiva ningún control de privacidad ni de análisis.

Las APK debug de CI son **previews públicas con firma efímera**; no actualizan aplicaciones privadas ni garantizan continuidad de firma entre ejecuciones. [SIGNING.md](SIGNING.md) define un futuro canal permanente público independiente. [MIGRATION.md](MIGRATION.md) detalla las limitaciones.
