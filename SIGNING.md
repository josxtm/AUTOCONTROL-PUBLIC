# AutoControL — APK que se actualiza sin desinstalar

## Regla obligatoria de Android

Android solo permite instalar una APK sobre otra sin borrar los datos cuando coinciden el **applicationId**, la **identidad del certificado de firma** y el `versionCode` de la actualización es superior (o compatible conforme a la política de instalación).

Para esta edición pública:
- Paquete permanente: `com.josxtm.autocontrol.community`.
- Canal instalable/actualizable: `AUTOCONTROL-STABLE-UPDATE-APK`.
- Las claves privadas y contraseñas jamás se incorporan al repositorio ni al APK.
- GitHub Actions genera una versión con `versionCode` creciente y verifica la huella SHA-256 del certificado de firma antes de publicar la APK.
- Los datos de la aplicación se conservan en las actualizaciones de este mismo canal (sin cambiar ni borrar el almacenamiento interno).

## Preparación inicial — una sola vez (obligatoria)

**Importante:** las antiguas previews `app-debug.apk` se firmaban con certificados de depuración efímeros. **No se pueden actualizar directamente mediante la nueva firma estable.** Para cambiar de canal será necesario exportar antes una copia cifrada `.acpb`, desinstalar esa preview, instalar la primera APK estable y restaurar la copia. Después de esa migración, las siguientes APK estables sí se instalarán encima de la anterior sin desinstalar.

El propietario debe crear y conservar una clave **exclusiva para esta aplicación** en un equipo confiable con JDK instalado:

```sh
keytool -genkeypair -keystore autocontrol-release.jks -alias autocontrol -keyalg RSA -keysize 3072 -validity 10000
```

El comando solicitará las contraseñas y la información del certificado. Guardar una **copia privada y cifrada** de la clave y sus contraseñas; perderlas impide continuar actualizando instalaciones firmadas por ella.

Calcular la huella pública del certificado (el formato del dato de GitHub debe contener 64 caracteres hexadecimales, sin `:`):

```sh
keytool -list -v -keystore autocontrol-release.jks -alias autocontrol
```

Configurar en GitHub, dentro del repositorio `josxtm/AUTOCONTROL-PUBLIC`:

**Settings > Environments > New environment**: crear `autocontrol-signing`. Recomendada aprobación de despliegue cuando lo permita el plan, y restringir a la rama `main`.

En ese entorno registrar estos **Secrets**, sin comillas alrededor de sus valores:

- `AUTOCONTROL_KEYSTORE_BASE64`: contenido Base64 del archivo JKS completo (sin saltos de línea).
- `AUTOCONTROL_KEYSTORE_PASSWORD`: contraseña del almacén.
- `AUTOCONTROL_KEY_ALIAS`: alias, por ejemplo `autocontrol`.
- `AUTOCONTROL_KEY_PASSWORD`: contraseña de la clave privada.

Y esta **Variable de entorno**:

- `AUTOCONTROL_CERT_SHA256`: huella SHA-256 pública del certificado en hexadecimal sin separadores.

Para obtener Base64:

```sh
base64 < autocontrol-release.jks | tr -d '\\n'
```

**No pegar el Base64 de la clave, contraseñas ni datos secretos en chats, issues, PR, logs o documentos públicos.** Introducirlos directamente en la interfaz de secretos de GitHub.

## Publicación de nuevas APK

Después de fusionar los cambios comprobados a `main`:

1. GitHub > Actions > **Stable signed AutoControL APK** > **Run workflow** (rama `main`).
2. Confirmar que el trabajo completo ha terminado en verde.
3. Descargar el artefacto `AUTOCONTROL-STABLE-UPDATE-APK`, descomprimirlo e instalar `app-release.apk`.
4. La siguiente ejecución del workflow usa la **misma clave** y un código de versión mayor. Android actualizará la app anterior **conservando su contenido**.

La firma solo se obtiene del entorno `autocontrol-signing`. El workflow se ejecuta manualmente y se niega a publicar si faltan secretos o no coincide la huella. La publicación del artefacto está detrás de la comprobación de identidad y del análisis de privacidad.

## Prueba de actualización y recuperación

En la primera migración, exportar una copia `.acpb` y comprobar que se dispone de su contraseña. Antes de instalar la segunda versión estable, guardar una copia adicional. Instalar la segunda APK **sin desinstalar** la primera, verificar que la misma ficha CT, notas, diario y documentos siguen accesibles y probar las funciones SSH y OCR. Mantener además una copia de respaldo fuera del dispositivo.

**No confundir** el artefacto de la CI anterior `AUTOCONTROL-PUBLIC-PREVIEW-NO-PRIVATE-UPDATE` con el nuevo canal estable: el primero es para pruebas, no garantiza actualizaciones y puede tener otra firma.
