# OCR local y redistribución

Se usa Tesseract4Android **4.9.0 estándar** desde su distribución JitPack oficial, sin modificar su código. Su manifiesto no solicita permisos. La versión se fija en Gradle; no se incorporan modelos traineddata ni imágenes.

Revisión de los textos publicados en la etiqueta 4.9.0:

- Wrapper Java/JNI y Tesseract OCR: Apache-2.0. Avisos de autor de Adaptech s.r.o./Robert Pösel, Google Inc. y Robert Theis conservados.
- Leptonica: licencia BSD de redistribución con conservación de avisos.
- libpng: licencia libpng, conservación del aviso y ausencia de atribución falsa.
- libjpeg: licencia IJG para distribución ejecutable con reconocimiento. **This software is based in part on the work of the Independent JPEG Group.**

Los textos de licencia y avisos completos aplicables se empaquetan en el recurso `third_party_notices` y se muestran desde Documentos > Licencias. Los originales están en [la etiqueta pública del proveedor](https://github.com/adaptech-cz/Tesseract4Android/tree/4.9.0), archivos LICENSE y licencias de sus carpetas Tesseract, Leptonica, libpng y README de libjpeg. No se empaquetan bibliotecas de entrenamiento ni herramientas de compilación.

El usuario debe aportar un modelo traineddata compatible y disponer de licencia para usarlo. El modelo se valida con el motor antes de reemplazar el anterior, se conserva en almacenamiento privado y no se incluye en las copias portables. La app no descarga modelos ni usa OCR remoto. La imagen se reduce a 4 millones de píxeles y lado máximo 2048 para contener memoria; el resultado debe revisarse antes de guardarlo. La precisión depende del modelo y la imagen.

Kotlin, AndroidX/Compose y coroutines se consumen como dependencias públicas estándar con sus licencias Apache-2.0; los avisos de las dependencias se mantienen en su distribución. No se reutilizan materiales de aplicaciones privadas.
