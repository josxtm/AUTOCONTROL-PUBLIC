# Fase 4 — Auditoría de migraciones y protección de datos

**Repositorio:** `josxtm/AUTOCONTROL-PUBLIC`
**Rama base auditada:** `origin/feat/sniffer-mangossh-full-integration` (`a4fff705`)
**Rama documental:** `docs/goose-fase4-migraciones-backup`
**Alcance:** investigación de lectura y plan técnico. No se modifican fuentes, esquemas, GUI ni archivos existentes; no se usan datos reales de CT.

## 1. Resumen ejecutivo

La edición pública tiene una base local pequeña y coherente, pero su esquema SQLite aún no dispone de migraciones: `WorkspaceStore.SCHEMA` vale 1 y `onUpgrade` lanza una excepción. La restauración y la importación JSON sí usan transacciones, por lo que una entrada inválida no debe sustituir parcialmente los datos actuales, pero la sustitución correcta del catálogo elimina intencionadamente las notas asociadas a los registros anteriores.

`PortableBackup` proporciona una frontera criptográfica sólida para la copia portable: AES-256-GCM, PBKDF2-HMAC-SHA256, sal/nonce aleatorios, límite de tamaño y autenticación completa antes de llamar al restaurador. El formato actual solo acepta exactamente la versión 1 y no incluye preferencias de UI, perfil SSH, huellas SSH ni modelo OCR. Esto debe permanecer explícito al añadir BDI/PES: no se deben perder datos de trabajo, pero tampoco se deben copiar secretos o preferencias operativas sin una decisión de contrato.

La recomendación central es introducir una capa de migración versionada y una separación entre catálogo, formularios BDI/PES, plantillas, evidencias y exportaciones. Cada modificación debe ser transaccional, comprobable con fixtures sintéticos y reversible a nivel de copia previa; no se debe usar “borrar y recrear” como estrategia de actualización.

## 2. Inventario del almacenamiento actual

### 2.1 Esquema SQLite actual

`WorkspaceStore.onCreate` crea estas tablas:

| Tabla | Contenido | Riesgo al evolucionar |
|---|---|---|
| `records` | `id` primario y `payload` JSON de campos escalares | El payload no tiene esquema BDI ni tipos ricos; cambiar su semántica rompe búsquedas y copias |
| `notes` | Nota por `id`, FK a `records(id)` con `ON DELETE CASCADE` | Importar un catálogo nuevo elimina las notas de registros sustituidos |
| `journal` | Entradas independientes con `id`, `time`, `body` | Debe conservarse durante importación de catálogo; restore sí sustituye el diario |
| `documents` | Adjuntos BLOB con nombre, MIME y datos | Límite de 200 documentos/16 MiB total; debe conservar IDs y bytes |
| `meta` | `identifier`, `fields`, `visible` y otros ajustes serializados | Las claves no tienen tabla de migración ni namespace formal |

La conexión habilita foreign keys. Las operaciones públicas son sincronizadas. El JSON de registros está limitado a valores escalares, 100 campos, texto acotado, un millón de filas y entrada incremental de 64 MiB.

### 2.2 Preferencias fuera de SQLite

`WorkspaceModel` usa `SharedPreferences("public-ui")` para `theme` y `tabletTheme`. `SnifferPanel` usa preferencias separadas para perfil SSH (`host`, `port`, `user`, sin contraseña) y hosts conocidos/huellas fijadas. Estas preferencias no forman parte de `WorkspaceStore.backup`.

Esto tiene consecuencias:

- Restaurar una copia no cambia tema ni apariencia.
- Restaurar una copia no mueve el perfil SSH ni las huellas conocidas.
- El modelo OCR vive en almacenamiento privado de archivos y tampoco se incluye en la copia.
- Las preferencias no deben incluirse automáticamente en una copia BDI/PES: algunas son secretas o específicas del dispositivo.
- Si el propietario quiere preservar una preferencia, debe existir una sección versionada, con clasificación de sensibilidad y consentimiento explícito.

### 2.3 Documentos y OCR

`DocumentEngine` lee adjuntos hasta 4 MiB, permite editar únicamente UTF-8 de hasta 16.000 caracteres y trata el resto como bytes inertes. No interpreta DOCX, PDF, macros o plantillas. `LocalOcr` guarda el modelo importado bajo archivos privados, valida Tesseract y limita imagen y resultado. Las futuras evidencias BDI/PES deben referenciar documentos por ID, sin duplicar bytes ni convertir OCR en una sustitución silenciosa.

## 3. Auditoría de importación JSON

### 3.1 Flujo actual

`WorkspaceModel.importCatalog` abre un `InputStream` del selector, crea un lector UTF-8 estricto y llama a `WorkspaceStore.importCatalog`. El store:

1. Comprueba el nombre del campo identificador.
2. Inicia una transacción SQLite.
3. Borra `records` (las notas asociadas caen por FK).
4. Lee el array incrementalmente.
5. Inserta cada objeto escalar y acumula campos.
6. Rechaza identificadores nulos, vacíos, repetidos o fuera de límite mediante la clave primaria.
7. Rechaza objetos anidados, JSON inválido, datos sobrantes y límites excedidos.
8. Actualiza `meta.fields`, `meta.identifier` y `meta.visible`.
9. Solo confirma cuando termina el documento y no hay error.

Una excepción antes de `setTransactionSuccessful()` revierte borrado, inserciones y metadatos. Las pruebas Android existentes verifican duplicados, campos inválidos, JSON truncado, trailing input, límites, fallo de lectura y catálogo grande sintético.

### 3.2 Riesgo funcional importante

Una importación válida es una **sustitución**, no una fusión: elimina notas de los registros anteriores y conserva el diario. También conserva documentos porque no los borra. Esto debe estar visible en UI y documentación antes de que BDI/PES dependa del catálogo.

No hay detección de “mismo registro con campos cambiados” porque el contrato actual usa un identificador exacto configurable. No se deben añadir heurísticas de fusión por nombre, matrícula o similitud. Para BDI/PES se recomienda que el caso tenga una referencia local al registro CT y una instantánea explícita solo si el usuario confirma crearla.

### 3.3 Prevención futura de pérdida y duplicados

Para futuras importaciones enriquecidas:

- Validar todo el archivo en una fase de staging antes de tocar tablas activas.
- Definir una política explícita: reemplazo, importación como nuevo conjunto o actualización por ID; no mezclar las tres.
- Detectar duplicados por clave canónica dentro del archivo y por conflicto con la base.
- Mostrar un resumen de incorporados, rechazados, modificados y conservados.
- Exigir confirmación si se van a eliminar notas, casos BDI o relaciones.
- Mantener una copia portable previa opcional antes de una sustitución destructiva.
- No normalizar silenciosamente valores que puedan contener ceros iniciales.
- Mantener los campos desconocidos si el contrato lo permite; nunca descartarlos sin informe.
- En caso de error, conservar exactamente el snapshot anterior.

## 4. Auditoría de copias `PortableBackup`

### 4.1 Formato actual

El formato binario empieza por `ACPB1`, seguido de sal de 16 bytes y nonce de 12 bytes. La clave deriva de una contraseña de 12–256 caracteres con PBKDF2-HMAC-SHA256 a 210.000 iteraciones y 256 bits. AES-GCM usa etiqueta de 128 bits y autentica magic, sal y nonce como AAD.

El contenido autenticado es JSON UTF-8 producido por `WorkspaceStore.backup`, con:

- versión exacta del esquema;
- identificador y campos visibles;
- registros;
- notas;
- diario;
- documentos por chunks Base64.

La exportación limita el contenido plano a 64 MiB. La restauración escribe primero a un temporal privado, verifica la etiqueta al alcanzar EOF y solo después pasa el lector a `WorkspaceStore.restore`. Limpia la contraseña y borra el temporal al terminar.

### 4.2 Compatibilidad actual

La compatibilidad es estricta: `WorkspaceStore.restore` exige `format = autocontrol-community` y `version = 1`; cualquier otra versión falla. No existe un lector de versiones antiguas ni una migración de backup. Esto es correcto para no interpretar un formato desconocido, pero debe convertirse en una política explícita antes de publicar esquema 2.

La copia no incluye:

- `SharedPreferences` de tema/apariencia.
- Perfil SSH ni huellas conocidas.
- Modelo OCR.
- Estado efímero de Compose.
- Contraseñas.

La documentación debe mantener esta lista y mostrarla en cualquier UI futura de backup.

### 4.3 Restauración fallida y rollback

Hay dos barreras distintas:

1. `PortableBackup.restore` evita llamar al destino si falla contraseña, magic, truncamiento, límite o autenticación GCM.
2. `WorkspaceStore.restore` realiza sustitución dentro de una transacción; una versión inválida, referencia inconsistente, documento duplicado o fallo de validación debe hacer rollback de registros, notas, diario, metadatos y documentos.

Las pruebas existentes cubren contraseña errónea, manipulación, truncamiento, magic incorrecto, snapshot inválido, versión desconocida y preservación de adjuntos/notas. Deben ampliarse para verificar también fallo de disco/stream, datos con relaciones BDI rotas y limpieza del temporal tras excepción.

No se debe prometer recuperación automática del archivo de destino creado por el proveedor SAF si una exportación se interrumpe: puede quedar parcial. La UI debe indicar que se elimine el archivo parcial y se repita la operación.

## 5. Plan de migraciones SQLite

### 5.1 Principios

- Cada versión de esquema tiene una constante y un script `onUpgrade` determinista.
- Las migraciones se ejecutan en una transacción de SQLite.
- Nunca se borra una tabla como fallback ante una migración desconocida.
- Se conserva un inventario de columnas/tablas antes y después.
- La migración es idempotente en el sentido de que Android no vuelve a aplicarla a la misma versión.
- Se valida la integridad referencial, conteos, hashes/longitudes de documentos y metadatos después de migrar.
- Si falla, la apertura de la base falla de forma segura; no se crea una base vacía con el mismo nombre.
- Antes de un salto mayor se recomienda exportar una copia portable, sin convertirla en requisito silencioso.

### 5.2 Versiones sugeridas

**Esquema 1 — actual:** catálogo escalar, notas, diario, documentos y meta.

**Esquema 2 — preparación BDI/PES:** añadir tablas vacías y metadatos versionados, sin modificar el significado de tablas existentes:

- `bdi_case`
- `bdi_revision`
- `bdi_field_value`
- `bdi_equipment`
- `bdi_evidence`
- `template_package`
- `template_mapping`
- `export_job`

Las tablas deben tener IDs locales, `schema_id`, `schema_version`, timestamps, estados y claves foráneas. Esta migración no debe rellenar campos BDI desde `records`.

**Esquema 3 — relaciones y procedencia:** añadir índices, `source`, estado de revisión, referencias a documentos y restricciones necesarias, con backfill nulo/seguro solamente donde esté definido.

**Versiones posteriores:** solo tras disponer de contratos autorizados. Cada nuevo formulario debe usar tablas o payloads versionados por contrato, no reutilizar `meta` sin namespace.

### 5.3 Migración de `meta` y campos personalizados

`meta.fields` y `meta.visible` son JSON de nombres de campo. Una migración debe:

- conservar el orden actual cuando tenga significado visual;
- validar que visibles siguen siendo subconjunto de campos;
- no cambiar la clave identificadora ni los valores del catálogo;
- preservar campos desconocidos en payloads;
- separar futuras claves BDI con prefijos o tablas propias;
- registrar una versión de metadatos sin incluir valores privados en logs.

No se debe convertir automáticamente `records.payload` en un modelo BDI ni interpretar nombres como “BDI”, “PES” o “IP” sin un contrato proporcionado.

## 6. Estrategia de almacenamiento para BDI/PES

### 6.1 Separación de datos

Los formularios BDI/PES deben vivir en tablas de dominio independientes del catálogo CT, porque:

- un CT puede cambiar de catálogo sin que desaparezca un dossier confirmado;
- una nota de catálogo no es una revisión BDI;
- los formularios necesitan estados, versiones, procedencia y relaciones;
- restaurar un catálogo no debe borrar silenciosamente trabajo de campo.

Un dossier debe guardar `ct_record_id` como referencia débil y, si se requiere estabilidad, una instantánea explícita de los campos contextuales que el técnico confirme. La instantánea debe indicar fecha, fuente y revisión; no actualizarse automáticamente al importar JSON.

### 6.2 Estados y confirmación

Propuesta de estados: `DRAFT`, `IN_REVIEW`, `CONFIRMED`, `EXPORTED`, `ARCHIVED`. Un campo debe distinguir `MANUAL`, `CT_CONTEXT`, `OCR_REVIEWED` e `IMPORTED_TEMPLATE`, además de `reviewed` y error de validación.

- Guardar borrador nunca marca confirmado.
- Confirmar exige validar obligatorios y pedir confirmación explícita.
- Editar confirmado crea revisión nueva o reabre con una acción visible.
- Exportar usa una revisión concreta y registra el hash de plantilla.
- Archivar no elimina bytes sin una confirmación de borrado.

### 6.3 Evidencias y documentos

La tabla `documents` puede seguir almacenando bytes inertes, pero BDI/PES debe guardar una relación `evidence -> document_id`, tipo de evidencia, procedencia y revisión. No se deben duplicar adjuntos al crear un dossier. El borrado de una evidencia debe comprobar referencias antes de eliminar el documento.

Las plantillas deben conservarse como originales inmutables. Un XLSX/DOCX derivado debe tener un nuevo `document_id` y un `export_job`, sin sobrescribir la plantilla.

## 7. Compatibilidad de backup futuro

### 7.1 Evolución de formato

Para esquema 2, mantener `ACPB1` solo si el contenedor criptográfico sigue siendo idéntico y versionar el JSON interno de forma explícita. Alternativamente, definir un nuevo magic (`ACPB2`) si cambian framing, KDF o cifrado. Nunca intentar adivinar por longitud.

El lector futuro debe:

1. autenticar el contenedor completo;
2. aceptar únicamente versiones conocidas;
3. migrar el JSON autenticado a un modelo temporal;
4. validar todas las relaciones y límites;
5. aplicar la restauración a SQLite dentro de una transacción;
6. dejar los datos actuales intactos si cualquier etapa falla.

Para compatibilidad hacia atrás, conservar lectores de versión 1 y migradores puros `v1 -> v2`. Para compatibilidad hacia delante, rechazar versiones mayores con un mensaje claro y sin mutación.

### 7.2 Qué incluir de BDI/PES

Incluir, sujeto a política aprobada:

- casos, revisiones, campos, estados y procedencias;
- equipos y mediciones introducidos manualmente;
- relaciones a documentos y hashes de plantillas;
- evidencias que el usuario haya decidido incluir;
- exportaciones derivadas solo si se necesitan para recuperación.

No incluir automáticamente:

- contraseñas, huellas SSH o perfiles de conexión;
- modelo OCR, que se puede reimportar;
- preferencias puramente visuales;
- archivos temporales, cachés, sesiones Compose o historial SSH efímero.

## 8. Pruebas automatizadas para Codex

### 8.1 Migración

1. Crear una base sintética en esquema 1 con catálogo, nota, diario, documento, campos visibles y metadatos.
2. Cerrar y abrir con la versión migradora; verificar tablas, conteos, bytes, IDs y valores exactos.
3. Ejecutar migración con valores Unicode, ceros iniciales, campos desconocidos y documento binario.
4. Inyectar una base corrupta o una migración que falle y verificar que no se crea una base vacía ni se borra la original.
5. Verificar que `onUpgrade(1,2)` no altera el significado de `records`, `notes`, `journal` o `documents`.
6. Crear un esquema 2 con un dossier BDI sintético, actualizar a esquema 3 y verificar relaciones y estados.

### 8.2 Importación JSON

1. Importación válida conserva registros según contrato.
2. Duplicado exacto y duplicado canónico se rechazan según la política definida.
3. JSON truncado, trailing input, objeto anidado, campo desconocido no permitido y exceso de límites dejan snapshot idéntico.
4. Falla de lectura a mitad de stream deja registros, notas, diario y documentos sin cambio.
5. Reemplazo válido demuestra explícitamente qué ocurre con notas y qué ocurre con diario/documentos.
6. Catálogo nuevo no elimina dossiers BDI/PES ni documentos referenciados.
7. Importaciones consecutivas no producen IDs duplicados ni pérdida de campos personalizados.

### 8.3 Backup y restauración

1. Round trip v1 de base vacía y base completa.
2. Round trip con nota Unicode, 25+ entradas de diario, documento binario de tamaño límite y campos personalizados.
3. Contraseña errónea, magic alterado, nonce/sal alterados, etiqueta alterada y truncamiento no llaman al restaurador.
4. Snapshot JSON con versión desconocida, campos obligatorios ausentes, IDs duplicados, FK rota o chunks inválidos conserva la base actual.
5. Fallo durante escritura/lectura de stream limpia el temporal privado.
6. Versión 1 se migra a la representación BDI/PES futura sin perder tablas antiguas.
7. Versión futura mayor se rechaza sin intentar restaurar.
8. Repetir restauración es determinista y no duplica documentos, notas, diario o casos.
9. Se comprueba que preferencias UI, SSH y modelo OCR permanecen fuera de la copia salvo contrato explícito.

### 8.4 Documentos y OCR

1. El documento original permanece byte a byte después de crear evidencia o exportación.
2. Borrar una relación no borra un documento todavía referenciado.
3. Adjuntos grandes y binarios no pasan por edición UTF-8 accidentalmente.
4. OCR sin modelo, modelo inválido, imagen inválida y resultado largo fallan sin guardar valores.
5. Texto OCR solo se convierte en valor tras revisión explícita.

### 8.5 Pruebas instrumentadas y de proceso

- Reapertura de la app conserva datos tras migración.
- Muerte/reinicio durante operaciones deja base válida o rollback completo.
- Rotación y cambio de tamaño no cambian el borrador persistido.
- Selector SAF cancelado no borra ni sustituye datos.
- `FLAG_SECURE`, backup automático desactivado y ausencia de recursos privados se mantienen.
- Los fixtures se generan con identificadores `SYN-*`, textos sintéticos y redes de documentación reservadas.

## 9. Plan de implementación por etapas

### Etapa 0 — Contrato y snapshot

Documentar el esquema actual, límites, tablas, preferencias excluidas y formato `ACPB1`. Añadir pruebas de inventario antes de tocar `SCHEMA`.

### Etapa 1 — Infraestructura de migración

Reemplazar el `onUpgrade` que falla por un dispatcher versionado, manteniendo una migración 1→2 pequeña y transaccional. Probar instalación limpia, upgrade y fallo controlado. No añadir formularios todavía.

### Etapa 2 — Backup versionado

Separar el modelo de backup v1 del modelo interno, crear lector/migrador por versión y añadir pruebas de compatibilidad. Mantener autenticación completa antes de mutación y rechazo de versiones desconocidas.

### Etapa 3 — Tablas BDI/PES vacías y repositorios

Añadir solo tablas y repositorios tipados con contratos sintéticos. No rellenar campos desde catálogos ni importar plantillas reales. Probar borradores, revisiones, FK y borrado seguro.

### Etapa 4 — Integración documental/OCR

Relacionar evidencias con documentos existentes, conservar bytes originales y crear flujo OCR revisable. No añadir exportadores XLSX/DOCX hasta disponer de plantillas autorizadas y evaluación de licencia.

### Etapa 5 — Importación segura y UI futura

Definir política de reemplazo/fusión, avisos de impacto, previsualización y confirmación. Integrar la UI BDI/PES sin modificar los módulos existentes. Validar tablet/móvil y persistencia del estado.

### Etapa 6 — Endurecimiento y aceptación

Ejecutar migración, backup, privacidad, límites, accesibilidad, restauración interrumpida y regresión completa. Revisión humana de contratos y aprobación explícita antes de cualquier APK.

## 10. Riesgos y decisiones pendientes

| Riesgo | Mitigación |
|---|---|
| Cambiar `SCHEMA` sin migrador | No publicar esquema nuevo hasta tener fixtures 1→2 y rollback |
| Restaurar versión futura como si fuera actual | Rechazo estricto por versión y magic |
| Pérdida de notas durante importación | Aviso, snapshot previo y política explícita de reemplazo |
| Dossier BDI ligado a un catálogo mutable | Referencia + instantánea confirmada, sin actualización automática |
| BLOB duplicados | Relaciones por ID y comprobación de referencias |
| Plantilla modificada | Hash/versionado y copia original inmutable |
| Secretos en backup o preferencias | Clasificación de datos y exclusión por defecto |
| Corrupción durante restore | Autenticación previa, staging y transacción SQLite |
| Formularios que inventan campos | Schemas y manifiestos entregados por el propietario |
| Dependencias de exportación incompatibles | Evaluación offline, licencia y pruebas de preservación |
| Logs/capturas con datos | Fixtures sintéticos, `FLAG_SECURE` y privacy guard |

Decisiones que deben resolver el propietario/Codex antes de implementar: política de fusión JSON, retención de notas ante reemplazo, inclusión de evidencias en backup, identificación del técnico, firma o no firma, límites BDI, versionado de plantillas y soporte de XLSX/DOCX.

## 11. Criterios de aceptación

- La versión existente de la app abre y conserva todos sus datos después de una migración probada.
- Una restauración incorrecta nunca reemplaza la base actual.
- Una copia v1 sigue siendo restaurable después de añadir BDI/PES.
- Una copia futura no se interpreta por fuerza en una versión antigua.
- Importar JSON no genera duplicados ni mutaciones parciales y comunica claramente qué se reemplaza.
- Notas, diario, documentos, campos visibles y campos personalizados tienen pruebas de preservación.
- Los dossiers BDI/PES viven fuera del catálogo y sobreviven a sustituciones de JSON.
- Las preferencias y secretos no se exportan sin contrato explícito.
- Los bytes de plantillas/evidencias permanecen intactos y las exportaciones son derivadas.
- OCR requiere revisión humana y no cambia datos automáticamente.
- Todas las pruebas usan datos sintéticos; no hay conexiones reales ni documentos empresariales.
- La entrega de implementación futura no debe tocar `main` hasta contar con aprobación y regresión completa.

## 12. Veredicto

**AUDITORÍA COMPLETADA — MIGRACIONES Y BACKUP PLANIFICADOS, SIN IMPLEMENTACIÓN.**

La aplicación actual protege importaciones y restauraciones mediante transacciones, límites y autenticación, pero aún necesita una estrategia formal de migración antes de añadir BDI/PES. Codex debe comenzar por pruebas de inventario y un migrador 1→2, mantener lectores de backup versionados y separar los dossiers estructurados del catálogo JSON. Cualquier implementación posterior debe preservar datos existentes, rechazar formatos desconocidos de forma segura y evitar que la UI o el OCR conviertan valores no revisados en información confirmada.
