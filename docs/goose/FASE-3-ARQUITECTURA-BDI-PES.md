# Fase 3 — Arquitectura propuesta para BDI/PES

**Repositorio:** `josxtm/AUTOCONTROL-PUBLIC`
**Referencia auditada:** `origin/feat/sniffer-mangossh-full-integration` (`a4fff705`)
**Rama documental:** `docs/goose-fase3-bdi-pes`
**Modalidad:** investigación y documentación; no se desarrolla código.

## 1. Propósito y límites

Este documento prepara una arquitectura para un futuro módulo BDI/PES Android, manteniendo intacta la aplicación pública actual. La propuesta es local-first: los datos se guardan exclusivamente en el dispositivo, los formularios son editables y toda exportación exige revisión y confirmación del técnico.

No se definen campos oficiales, referencias de celdas, procedimientos, maniobras ni nombres de equipos reales porque no se han aportado los contratos autorizados. Las listas de campos de este informe son **categorías arquitectónicas**, no un formulario operativo. Antes de implementar cada documento se necesita que el propietario entregue su plantilla y especificación autorizada.

Quedan fuera del alcance:

- Automatización de SCADA, DPF, telemandos, firmware o maniobras.
- Conexiones de red para completar BDI/PES.
- Inferir datos desde un CT, un catálogo o una plantilla no proporcionada.
- Incluir plantillas empresariales, documentos confidenciales o datos operativos.
- Cambiar la GUI actual, los diez temas, SNIFFER/MangoSSH o el almacenamiento existente durante esta fase.

## 2. Estado de la base de referencia

### 2.1 Módulos existentes

| Módulo | Capacidades reutilizables | Implicación para BDI/PES |
|---|---|---|
| `:storage` | `WorkspaceStore`, SQLite local, registros JSON escalares, notas, diario, documentos BLOB, metadatos y copias lógicas | Añadir entidades estructuradas mediante una migración explícita; no abusar de `records.payload` como esquema BDI definitivo |
| `:documents` | `DocumentEngine`: adjuntos inertes, límite 4 MiB, edición UTF-8 hasta 16.000 caracteres | Base para documentos aportados; no interpreta DOCX/PDF ni macros |
| `:ocr` | `LocalOcr`: modelo importado por el usuario, validación Tesseract, SHA-256, procesamiento sin red, imagen acotada | Captura asistida; nunca escritura automática sin revisión del técnico |
| `:app` | `WorkspaceModel`, Kotlin/Compose, selectores SAF, estado de trabajo y acciones en `Dispatchers.IO` | Nuevo ViewModel/repositorio de formularios, separado de `SnifferPanel` |
| Backup | `PortableBackup`: AES-256-GCM, PBKDF2-HMAC-SHA256, restauración autenticada antes de mutar SQLite | Versionar el contenido BDI/PES y conservar compatibilidad/rollback |
| SNIFFER | SSH fijado, diagnóstico limitado, datos LAN solo del registro seleccionado | No se usa para rellenar BDI/PES ni se amplían sus comandos |

### 2.2 Restricciones actuales que condicionan el diseño

- `WorkspaceStore` está en esquema 1 y `onUpgrade` rechaza migraciones. Un módulo BDI/PES necesita una migración versionada y pruebas de instalación/upgrade antes de cambiar el número de esquema.
- El JSON actual admite objetos planos con valores escalares y límites de tamaño; no es un formato suficiente para formularios anidados, estados de revisión y múltiples versiones de plantilla.
- `DocumentEngine` conserva bytes y solo edita texto UTF-8. No hay lector/escritor XLSX o DOCX.
- `WorkspaceModel` mezcla estado de UI con operaciones de almacenamiento. El nuevo módulo debe introducir interfaces de dominio para no aumentar el acoplamiento.
- El selector Android ya permite importar/exportar archivos por SAF. Las futuras exportaciones deben usarlo, sin permisos de almacenamiento amplios ni rutas externas directas.
- `LocalOcr.recognize` devuelve texto y exige revisión posterior. No devuelve confianza por campo ni coordenadas, por lo que la extracción estructurada debe ser explícita y reversible.
- La aplicación declara trabajo local y no incorpora catálogo, documentos ni modelos OCR. Esa política debe continuar.

## 3. Arquitectura propuesta

### 3.1 Capas

1. **UI Compose industrial existente:** incorpora en el futuro un módulo BDI/PES sin alterar cabecera, rail, temas ni SNIFFER. La UI solo edita un borrador y muestra validaciones.
2. **ViewModel de módulo:** `BdiPesViewModel` (nombre propuesto), estado de pantalla, selección de CT, borrador, revisión, errores y progreso de exportación. No contiene SQL ni manipulación de DOCX/XLSX.
3. **Casos de uso:** crear borrador, cargar borrador, actualizar campo, adjuntar OCR, validar, confirmar, importar plantilla, previsualizar y exportar.
4. **Dominio tipado:** modelos Kotlin inmutables, identificadores locales, valores con estado de procedencia y validadores por versión de formulario.
5. **Repositorios:** interfaces para formularios, plantillas, adjuntos, auditoría local y exportaciones. Implementación SQLite/archivos privados en `:storage` y `:documents`.
6. **Adaptadores:** OCR local, SAF, generador de XLSX/DOCX y serialización de backup. Los adaptadores no acceden a red.

### 3.2 Separación de conceptos

- **Registro CT existente:** fuente de selección y contexto, leído desde `WorkspaceStore`; no se debe copiar todo el catálogo automáticamente al formulario.
- **Dossier BDI/PES:** conjunto de borradores y documentos del técnico, con versión y estado propios.
- **Plantilla:** archivo local aportado por el usuario más un manifiesto de mapeo autorizado. El archivo por sí solo no permite adivinar campos.
- **Evidencia:** foto/imagen o adjunto inerte y resultado OCR; se conserva la relación con el campo, pero el valor confirmado siempre lo introduce o valida el técnico.
- **Exportación:** producto derivado, no fuente de verdad. El dossier estructurado se conserva aunque se borre un archivo exportado.

## 4. Modelo de datos propuesto

Los siguientes nombres son contratos internos provisionales, no nombres oficiales de columnas empresariales.

### 4.1 Identidad y ciclo de vida

```text
BdiPesCase
  localId: UUID
  ctRecordId: String?          // referencia local al registro seleccionado
  caseType: CaseType            // CENTROS, TELECONTROL, ROUTER, BT_SABT, PES
  schemaId: String              // contrato aprobado, nunca inferido
  schemaVersion: Int
  status: DraftStatus           // DRAFT, IN_REVIEW, CONFIRMED, EXPORTED
  createdAt / updatedAt: Long
  confirmedAt: Long?
  confirmedByLabel: String?     // etiqueta introducida por el técnico, si procede
  revision: Int
```

Un caso confirmado no se edita silenciosamente: se crea una revisión nueva o se solicita explícitamente reabrirlo. Deben conservarse los borradores incompletos sin confundirlos con datos confirmados.

### 4.2 Valores de campo

```text
FormFieldValue
  caseId: UUID
  fieldKey: String              // clave del contrato aprobado
  value: String?
  valueType: TEXT, INTEGER, DECIMAL, BOOLEAN, DATE, ENUM, SERIALIZED
  source: MANUAL, CT_CONTEXT, OCR_REVIEWED, IMPORTED_TEMPLATE
  evidenceId: UUID?
  validation: VALID, WARNING, ERROR, UNREVIEWED
  reviewed: Boolean
  note: String?
```

Se recomienda conservar el valor como texto canónico junto con su tipo esperado: así no se pierden ceros iniciales en números de serie y se puede mostrar exactamente lo escrito. Los validadores deben rechazar o advertir según el contrato, nunca corregir silenciosamente.

### 4.3 Equipos y relaciones

Para Telecontrol, Router y BT/SABT se requiere un modelo de equipos relacionado con el caso, pero no se deben inventar subtipos ni campos de fabricante:

```text
EquipmentDraft
  localId: UUID
  caseId: UUID
  roleKey: String               // catálogo autorizado pendiente
  displayLabel: String
  fields: Map<String, FormFieldValue>
  source: MANUAL | CT_CONTEXT | OCR_REVIEWED
  reviewState: UNREVIEWED | REVIEWED
```

Las relaciones deben ser locales y explícitas (`caseId`, `parentEquipmentId?`). No se debe derivar topología, dirección o comando a partir de una posición de fila.

### 4.4 Plantillas y exportaciones

```text
TemplatePackage
  localId: UUID
  kind: XLSX | DOCX
  displayName: String
  originalDocumentId: String
  templateVersion: String
  manifestVersion: Int
  sha256: String
  authorized: Boolean

ExportJob
  localId: UUID
  caseId: UUID
  templateId: UUID
  outputType: XLSX | DOCX
  status: PREVIEW | CONFIRMED | FAILED
  createdAt: Long
  outputDocumentId: String?
  errorSummary: String?
```

El manifiesto debe especificar, mediante un formato que entregue el propietario, cada `fieldKey`, hoja/sección, celda o marcador, formato, obligatoriedad y reglas de repetición. El motor no puede inventar referencias de celdas, nombres de hojas ni marcadores.

## 5. Submódulos BDI/PES

### 5.1 BDI de Centros

**Objetivo futuro:** un formulario estructurado asociado a un CT seleccionado localmente.

Flujo propuesto:

1. Seleccionar un registro CT existente y mostrar solo un resumen de contexto.
2. Crear un dossier BDI con el `ctRecordId`, el esquema aprobado y una revisión vacía.
3. Presentar secciones definidas por contrato: identidad, localización descriptiva, equipos y observaciones solo si esas secciones están presentes en la especificación entregada.
4. Validar campo por campo y mostrar faltantes, formatos inválidos y advertencias.
5. Permitir revisión completa, confirmación explícita y exportación posterior.

No se debe copiar automáticamente toda la ficha del CT, ni convertir una clave JSON en campo oficial sin un mapeo aprobado.

**Información que falta para implementarlo:** plantilla oficial, lista/versiones de campos, tipos, obligatoriedad, catálogos de valores, reglas de normalización, identidad del técnico y política de firma/confirmación.

### 5.2 BDI Telecontrol

Debe modelar datos introducidos por el técnico para la remota, FAS, baterías y equipos asociados como subformularios separados, con relaciones explícitas y evidencias opcionales. La arquitectura soporta:

- Una remota por dossier o varias, si el contrato lo permite.
- Equipos asociados con identificador local y rol autorizado.
- Mediciones manuales con unidad y fecha/hora introducidas por el técnico.
- Campos de batería y FAS como valores revisables, sin inferir estado de telecontrol.

No debe abrir sesiones SSH, ejecutar comandos, tocar firmware, consultar SCADA ni reutilizar resultados de SNIFFER como si fueran mediciones BDI confirmadas.

**Faltan:** modelo oficial de remota/FAS/baterías, unidades y precisión, reglas de rango, cardinalidades, fotografías permitidas, campos obligatorios y formato de salida.

### 5.3 Router de comunicaciones

Propuesta de datos: un formulario de registro de equipos de comunicación con campos proporcionados por el contrato, una colección de SIM/operadores y mediciones manuales. La SIM debe tratarse como dato confidencial: mostrarla enmascarada cuando sea posible, no escribirla en logs ni en historial SSH y protegerla dentro de las copias cifradas locales.

La app debe distinguir:

- Identidad introducida del equipo.
- Datos de operador/SIM introducidos por el técnico.
- Medición manual con unidad, instante y nota.
- Estado de revisión, sin probar conectividad automáticamente.

**Faltan:** nombres oficiales de campos, política de almacenamiento de identificadores de SIM, reglas de enmascarado, unidades y plantilla autorizada. No se debe añadir un escáner de red ni una prueba contra operadores.

### 5.4 Telegestión BT y SABT

La arquitectura permite una jerarquía dossier → transformador → concentrador → equipos asociados, pero sus roles, cardinalidades y campos deben provenir del contrato. Cada nivel debe tener su propio `localId` y estado de revisión para que una edición parcial no se confunda con una inspección completada.

Las mediciones se guardan como valores manuales con unidad, fecha/hora, autor/etiqueta y evidencia opcional. No se deben derivar capacidades, alarmas, direcciones ni estados desde offsets LAN de SNIFFER. La presencia de un equipo en el catálogo CT solo puede sugerir contexto visual, nunca rellenar un BDI confirmado.

**Faltan:** esquema BT/SABT, catálogo de equipos, relación transformador/concentrador, mediciones admitidas, unidades, tolerancias y plantilla de salida.

### 5.5 Documentos PES

PES debe ser un flujo de cumplimentación asistida, no un procesador de procedimientos. El usuario aporta localmente una plantilla autorizada; la app:

1. Importa y conserva el archivo original como adjunto inerte.
2. Valida un manifiesto de mapeo separado y versionado.
3. Crea un dossier tipado y un borrador de valores.
4. Muestra una previsualización de campos que se rellenarán.
5. Exige revisión y confirmación del técnico.
6. Genera una copia derivada sin alterar el original.

No se deben ejecutar macros, scripts, fórmulas desconocidas, controles ActiveX, campos externos ni enlaces remotos. Un DOCX/PDF no aportado con manifiesto no puede cumplimentarse automáticamente.

**Faltan:** plantillas PES reales autorizadas, formato (XLSX/DOCX/PDF), mapa de campos/marcadores, reglas de repetición, encabezados/pies, firmas, anexos y criterios de aceptación visual.

## 6. OCR para placas y números de serie

`LocalOcr` ya ofrece una base adecuada de procesamiento sin red:

- El propietario importa explícitamente un modelo `traineddata`.
- Se valida el modelo con Tesseract antes de instalarlo.
- Se informa SHA-256.
- La imagen se limita a 4 millones de píxeles y 2048 px de lado.
- El texto queda en memoria para que el técnico lo revise.

### Flujo BDI/PES propuesto

1. El técnico selecciona una imagen local mediante SAF.
2. OCR produce texto provisional y conserva una referencia al adjunto/evidencia.
3. La UI muestra el texto completo y permite seleccionar/copiar un fragmento a un campo.
4. El técnico confirma manualmente el valor; el campo queda `OCR_REVIEWED`, nunca `OCR_CONFIRMED` automáticamente.
5. Se almacenan valor, fuente, evidencia y revisión en el dossier local.

La primera versión no debe intentar detectar placas, identificar números de serie por heurística ni convertir texto OCR en campos sin acción explícita. Si más adelante se añade extracción, deberá ser un parser versionado por plantilla y mostrar cada candidato antes de guardarlo.

## 7. Almacenamiento local, recuperación y privacidad

### 7.1 Persistencia

Se recomienda ampliar `:storage` con tablas separadas y migraciones transaccionales:

- `bdi_cases`
- `bdi_revisions`
- `bdi_field_values`
- `bdi_equipment`
- `bdi_evidence`
- `template_packages`
- `template_mappings`
- `export_jobs`

Los nombres son propuestas internas. Las claves de contrato y plantillas deben versionarse, no depender de la posición de una columna. Las operaciones de guardar borrador, confirmar y migrar deben ser atómicas.

Los adjuntos grandes pueden seguir usando la tabla `documents` y su límite actual mientras se conserva una relación por ID. Si se necesita una política distinta, debe documentarse y probarse antes de cambiar límites.

### 7.2 Copias cifradas

`PortableBackup` autentica y descifra a un temporal antes de mutar SQLite. La futura sección BDI/PES debe incluir:

- versión de esquema y versión de contrato de cada dossier;
- valores, estados de revisión y notas;
- relación con documentos/evidencias;
- identificadores de plantilla y hash, no una plantilla empresarial implícita;
- exportaciones solo si se decide explícitamente incluirlas.

La restauración debe rechazar claves desconocidas, duplicados, estados inválidos, referencias rotas y tamaños excesivos antes de modificar datos. El modelo OCR no necesita estar incluido; sus evidencias sí solo si el usuario las incorporó a la copia y la política lo permite.

### 7.3 Privacidad

- Sin permiso de Internet para el flujo BDI/PES.
- Sin telemetría ni logs de valores de formulario.
- Sin datos en nombres de excepciones, tests o capturas.
- Contraseñas, SIM y secretos no deben entrar en historial de comandos.
- `FLAG_SECURE` debe mantenerse para evitar capturas accidentales.
- Exportar a un proveedor externo mediante SAF requiere confirmación y aviso de que el archivo abandona el almacenamiento privado.
- El borrado debe exigir confirmación y limpiar referencias de adjuntos/exportaciones no necesarias.

## 8. Plantillas XLSX y DOCX: estudio de dependencias

La base no contiene dependencias ni motores XLSX/DOCX; `DocumentEngine` solo trata bytes y texto UTF-8. La implementación futura debe separar dos decisiones:

1. **Formato autorizado:** el propietario debe confirmar si cada documento es XLSX, DOCX, PDF rellenable u otro formato.
2. **Estrategia de preservación:** debe conservarse la plantilla original y generar una copia, manteniendo estilos, hojas, marcadores, fórmulas permitidas, imágenes y metadatos en la medida que la biblioteca escogida lo garantice.

Posibles familias de librerías JVM/Android (pendientes de evaluación de tamaño, licencia y compatibilidad) son Apache POI para OOXML o una biblioteca especializada de DOCX/XLSX. No se debe añadir ninguna dependencia hasta disponer de plantillas sintéticas y una matriz de casos. La evaluación debe cubrir:

- ejecución completamente offline;
- consumo de memoria con plantillas grandes;
- preservación de estilos, fórmulas y relaciones OOXML;
- eliminación o bloqueo de macros, enlaces externos y contenido activo;
- licencias compatibles con el proyecto;
- escritura en `OutputStream` SAF sin crear copias no controladas.

### Manifiesto de mapeo requerido

Para cada plantilla, el propietario debe proporcionar un manifiesto autorizado con, como mínimo:

- identificador y versión de plantilla;
- hash del archivo base;
- tipo XLSX/DOCX;
- `fieldKey` del formulario;
- hoja y celda, o marcador y sección, exactos;
- tipo/formato y longitud;
- obligatoriedad y regla de validación;
- repetición de filas/secciones;
- tratamiento de campos vacíos;
- anexos, firmas y restricciones de edición.

La app debe rechazar un hash o versión no coincidente. No se deben adivinar celdas por texto visible, coordenadas o similitud de nombres.

## 9. Integración futura en la GUI industrial

La GUI existente y la recuperación industrial de Fase 2 deben permanecer sin cambios durante esta planificación. Cuando Codex implemente el módulo:

- Añadirá una entrada BDI/PES al rail existente sin mover CT, SNIFFER, MangoSSH, Diario, Documentos, Copias o Configuración.
- Reutilizará el patrón de paneles industriales, títulos compactos e iconos V10 ya aprobados.
- En tablet mostrará navegación, selección de dossier y panel técnico; en móvil usará una columna con el mismo flujo.
- Mantendrá SNIFFER fuera de la virtualización que pueda destruir sesiones.
- Separará estado de formulario, validación y exportación del estado de `WorkspaceModel` existente.
- Usará diálogos explícitos para confirmar importación de plantilla, OCR aplicado, confirmación de formulario, exportación y abandono de borradores.
- Mostrará claramente “borrador”, “requiere revisión”, “confirmado” y “exportado”.

La primera pantalla BDI/PES debe poder abrirse vacía con datos sintéticos y sin exigir que exista un catálogo CT real.

## 10. Dependencias y contratos que faltan

Antes de desarrollar cualquier formulario se debe recibir, por cada familia documental:

1. Plantilla pública o autorizada, con versión y hash.
2. Diccionario de campos: clave, etiqueta, tipo, unidad, obligatoriedad y longitud.
3. Reglas de validación y mensajes aprobados.
4. Cardinalidades y relaciones entre CT, equipos y mediciones.
5. Catálogo de valores permitido, si lo hay.
6. Política de revisión, firma o identificación del técnico.
7. Mapa de exportación XLSX/DOCX, incluyendo celdas/marcadores.
8. Ejemplos sintéticos que cubran vacío, válido, inválido, repetido y máximo.
9. Política de retención, borrado, backup y exportación de evidencias.
10. Licencias autorizadas de plantillas, fuentes y librerías.

Mientras falte cualquiera de estos elementos, Codex debe implementar únicamente infraestructura genérica y estados vacíos, no formularios oficiales.

## 11. Plan de desarrollo para Codex

### Fase A — Contratos y migración

- Definir `schemaId`, versiones, estados y modelos internos.
- Diseñar migración SQLite reversible y pruebas de upgrade.
- Crear repositorios sin conectarlos todavía a una pantalla oficial.
- Añadir validadores puros y tests con ejemplos sintéticos.

### Fase B — Dossier y formularios genéricos

- Implementar borrador/revisión/confirmación.
- Añadir secciones dinámicas solo desde un schema aprobado empaquetado o importado.
- Asociar CT por ID local, sin duplicar automáticamente todos sus campos.
- Añadir equipos y mediciones manuales con unidades explícitas.

### Fase C — Evidencias y OCR

- Relacionar imagen, texto OCR, campo candidato y revisión humana.
- Impedir guardado automático desde OCR.
- Probar límites, rotación, imágenes inválidas y ausencia de modelo.

### Fase D — Plantillas y exportación

- Importar plantilla y manifiesto por SAF.
- Verificar hash/versión y mostrar previsualización de mapeos.
- Evaluar biblioteca OOXML offline y licencia.
- Exportar una copia derivada después de confirmación; conservar original.

### Fase E — Integración GUI

- Añadir módulo al rail industrial sin cambiar pantallas existentes.
- Probar tablet/móvil, rotación, teclado, scroll y estados largos.
- Confirmar que SNIFFER/MangoSSH sigue funcionando durante navegación.

### Fase F — Endurecimiento

- Privacidad, límites, backup/restore, borrado, accesibilidad y pruebas de regresión.
- Revisión de dependencias y licencia.
- Aprobación del propietario de plantillas y capturas sintéticas antes de cualquier APK.

## 12. Pruebas y criterios de aceptación

### Dominio y almacenamiento

- Un caso se crea, edita, guarda, reabre y confirma sin perder valores ni ceros iniciales.
- Un borrador incompleto nunca aparece como confirmado.
- Una revisión conserva la anterior y permite comparar cambios.
- Campos duplicados, tipos inválidos, valores fuera de rango y referencias rotas se rechazan sin mutación parcial.
- La migración desde el esquema actual conserva catálogo, notas, diario, documentos, OCR y preferencias.
- Backup/restore autenticado conserva los dossiers permitidos y rechaza alteraciones/truncamientos sin tocar datos existentes.

### CT, equipos y mediciones

- Seleccionar un CT solo establece contexto; no inventa ni sobrescribe valores BDI.
- Equipos, SIM y mediciones manuales se guardan con procedencia y revisión.
- No se realizan pings, SSH, SCADA, telemandos ni cambios remotos desde el módulo.
- Los ejemplos de pruebas son sintéticos y no contienen IP, matrícula o documento operativo.

### OCR

- Sin modelo, el flujo se detiene con un mensaje claro.
- El texto OCR se puede revisar y editar antes de asociarlo a un campo.
- Ningún carácter OCR se confirma automáticamente.
- La imagen y el texto respetan límites, almacenamiento privado y política de borrado.

### Plantillas y exportación

- Una plantilla sin manifiesto o con hash incorrecto no se puede rellenar.
- El archivo original permanece intacto.
- La previsualización muestra todos los campos que se escribirían.
- La exportación funciona sin red y produce una copia derivada en el destino SAF.
- Se bloquean macros, enlaces externos y contenido activo según la biblioteca evaluada.
- XLSX/DOCX conserva la estructura autorizada en una batería de comparación sintética; si no puede garantizarse, la exportación se rechaza o se limita a un formato explícitamente aprobado.

### Interfaz

- Las pantallas existentes no cambian al instalar la infraestructura.
- El módulo se integra en la GUI industrial sin perder CT, SNIFFER, MangoSSH, OCR, documentos o copias.
- Tablet apaisada de 13,4 pulgadas muestra selección, formulario, validación y acciones sin clipping.
- Móvil permite el mismo flujo en columna y no pierde borradores al abrir el teclado.
- Todos los estados tienen semántica accesible y no dependen solo de color.

### Privacidad

- El escáner de privacidad no encuentra datos operativos, credenciales ni documentos reales.
- No hay conexiones de red ni telemetría.
- No se incluyen plantillas empresariales ni ejemplos que parezcan reales.
- No se genera APK como parte de esta planificación.

## 13. Veredicto

**FASE 3 PLANIFICADA — MOTOR BDI/PES NO IMPLEMENTADO.**

La base dispone de almacenamiento local, adjuntos inertes, OCR offline, Compose y copias cifradas suficientes para construir una plataforma de dossiers revisables. No dispone todavía de modelos BDI/PES oficiales, plantillas ni mapeos de exportación; esos insumos son el bloqueo correcto antes de crear formularios. La implementación futura debe empezar por contratos versionados y migración segura, continuar con borradores y revisión humana, y dejar XLSX/DOCX para después de recibir plantillas autorizadas. No se debe convertir esta arquitectura en automatización de SCADA, DPF, telemandos, firmware o maniobras.
