# Auditoría del importador GRID MT — PR #17

## Causa y corrección

En la compilación 243, `previewCsv()` ejecutaba el mismo recorrido de escritura que la actualización y luego revertía la transacción. Cada CT generaba una inserción temporal para detectar duplicados, una consulta SQLite individual y normalización/expresiones regulares para todas las columnas. La GUI solo mostraba un indicador indefinido.

La vista previa actual usa exclusivamente SELECT sobre la base existente: no abre una transacción de escritura, no crea tablas temporales y no modifica registros ni metadatos. La batería verifica tanto el hash del backup completo como `total_changes()` de SQLite, que detecta incluso escrituras posteriormente revertidas.

La selección de columnas se prepara una vez por cabecera; las normalizaciones se reutilizan. El parser recorre un buffer de caracteres de 32 KiB en lugar de llamar al PushbackReader por cada carácter. Las consultas de payloads se agrupan en lotes de 128 matrículas. Solo se mantienen un lote de payloads, la cabecera y un conjunto de IDs para detectar duplicados (acotado por el límite vigente de un millón de CT seleccionados). No se carga todo el CSV en memoria. En la carga de 19.948 CT el conjunto contiene 19.948 IDs.

La actualización reutiliza INSERT y UPDATE preparados en una única transacción. UPDATE conserva las relaciones de notas: no se usa REPLACE ni DELETE sobre registros. Los CT que faltan en el nuevo catálogo se conservan. Un error de lectura, GZIP, estructura, duplicados, SQLite, almacenamiento o cancelación antes del commit revierte la carga completa. Se conserva el error original si SQLITE_FULL ya ha deshecho la transacción y el cierre vuelve a intentar revertirla; así la GUI distingue falta de espacio de un error genérico de SQLite. Si cambia el número de CT respecto de la vista previa, la carga se rechaza; no se promete detectar cambios de contenido que mantengan el mismo número de filas.

## Progreso, cancelación y ciclo de vida

- Lectura: registros CSV leídos y CT que corresponden a los prefijos elegidos.
- Bytes: contador bajo el descompresor; porcentaje del archivo comprimido si el proveedor SAF informa de su tamaño. No es porcentaje de análisis. Los buffers pueden consumir todos los bytes antes de terminar el análisis; la GUI lo explica.
- Escritura: CT procesados / total seleccionado en la vista previa, incluyendo CT sin cambios. El 100% de CT procesados sigue pendiente de terminar la lectura y confirmar la transacción.
- Actualizaciones cada 150 ms y al iniciar/terminar cada recorrido. Si falla UTF-8 se reabre el archivo en Windows-1252 y se reinician los contadores de ese recorrido; no se acumulan lecturas de dos intentos.
- Cancelación cooperativa en Java en cada fila, buffer y operación de almacenamiento. Un ejecutor cierra el stream activo sin bloquear el hilo principal. El worker termina el rollback antes de devolver los controles. El token arbitra cancelación frente al punto de confirmación atómica: una cancelación aceptada antes de ese punto revierte todo; después, la carga termina normalmente.
- Cada trabajo posee su propio token. Los callbacks comprueban identidad en el hilo principal; un trabajo anterior no puede publicar progreso de una carga posterior. Al destruir el ViewModel se cancela el trabajo antes de cerrar SQLite.
- Los errores se traducen a mensajes controlados sin incluir nombres de documento, matrículas, contenido de celdas ni mensajes de proveedores en logs.

## Conservación de datos

| Información | Evidencia / comportamiento |
| --- | --- |
| Notas del CT y notas históricas | UPDATE sin borrado de relaciones; comprobadas tras cargar, repetir, cancelar y provocar errores |
| Diario y documentos | El importador no escribe en sus tablas; contenido comprobado y hash de backup completo |
| Campos locales/manuales adicionales | Se mantienen los campos que no pertenecen a las columnas técnicas entrantes; prueba `MANUAL=keep` |
| Columnas técnicas del catálogo | Se refrescan desde el CSV, como en la selección vigente; esta app no guarda procedencia para distinguir una edición externa manual del mismo campo técnico |
| Configuración y temas | No se escribe SharedPreferences; se conserva `visible` y los demás metadatos, salvo `fields` e `identifier` al confirmar |
| CT históricos y otros prefijos | No se elimina ningún registro; pruebas de prefijo 13 histórico y actualización independiente 45 |
| Credenciales | Se descartan antes de persistir; también se retiran claves sensibles de los payloads actualizados. Ninguna se añade al backup |
| Esquema y copias | Sin cambios de esquema ni formato cifrado; no hay migración destructiva |

## Campos conservados y omitidos

Se conserva exactamente la selección técnica anterior; no se reduce para acelerar el importador. La lista vigente de bases normalizadas contiene 100 campos:

```
MATRICULA | NOMBRE | CENTRORESPONSABLE | CODIGO | NTRAFOS | NSALIDAS | TIPOCENTRO | TIPOEDIFICIO
PROPIEDAD | BBDD | PROVINCIA | MUNICIPIO | CALLE | PORTAL | CP | X
Y | ALIMENTACION | PINSTKVA | NCLIENTES | TELEMANDOINST | TELEMANDOACT | ESTADOTG | ESTADOTC
FECHAPESTG | FECHADC | FECHAINSTALACIONTC | FECHAEXPLOTACIONTC | ARMARIOGCTTIPO | EQCOMFABRICANTE | EQCOMMODELO | EQCOMNSERIE
EQCOMOPERADOR1 | EQCOMNSIM1 | EQCOMOPERADOR2 | EQCOMNSIM2 | EQCOMTCFABRICANTE | EQCOMTCMODELO | EQCOMTCNSERIE | POTENCIAMOVISTAR
POTENCIAORANGE | EQCOMMODELOANTENA | EQCOMTGTIPOANTENAALTAGANANCIA | EQCOMTGUBICACIONANTENA | EQCOMTG | ARMARIOGCTUBICACION | FABRICANTEGCT | T1MODELOCC
T1NSERIECC | T1NSERIESBT | T1MATRICULA | TI1BTRELTRANSFORMACION | T2MODELOCC | T2NSERIECC | T2NSERIESBT | T2MATRICULA
TI2BTRELTRANSFORMACION | FABATN | FA1FABRICANTE | FA1MODELO | FA1NSERIE | FA1VERSIONFW | BAT1CAPACIDAD | BAT1FABRICANTE
BAT1MODELO | BAT1FECHA | BAT2CAPACIDAD | BAT2FABRICANTE | BAT2MODELO | BAT2FECHA | RTUN | RTU1FABRICANTE
RTU1MODELO | RTU1NSERIE | RTU1VERSIONFW | IPLAN | IPWAN | IPTC | APNTC | TECNOLOGIATC
CRITICIDADTG | CRITICIDADTC | GST | CPU | TIPORFIS | CAA | IPFA1 | IPFA2
POTENCIANOMINALT1 | POTENCIANOMINALT2 | CNCT1 | CNCT2 | IPT1 | IPT2 | ESTADOCNCT1 | ESTADOCNCT2
SBTT1 | SBTT2 | ESTADOSBTT1 | ESTADOSBTT2
```

Los calificadores de origen entre corchetes se retiran al proyectar. `IP TC [CAL]` se conserva como `IP TC (CAL)` y la otra procedencia como `IP TC`. Cuando distintas cabeceras proyectan a la misma etiqueta se conserva la primera, como antes. El límite total de campos almacenados sigue siendo 128, contando los campos locales conservados. Se rechaza una carga que lo supera; no se truncan datos silenciosamente.

Todavía no se conservan las columnas de las 364 originales cuya base no figure en esa lista, incluyendo telemetrías y campos específicos no seleccionados; tampoco columnas de contraseñas, claves, tokens, secretos o credenciales. Los nombres de las columnas no seleccionadas se mantienen solo en la cabecera del parser durante la lectura. El archivo original sigue siendo la fuente completa. No se dispone ni se publica aquí de las 364 cabeceras o valores de un archivo operativo.

## Pruebas reproducibles y rendimiento

`CsvImportScenarios` genera en el cache de pruebas un GZIP sintético de **70.000 filas y 364 columnas**, con las bases técnicas de arriba, columnas sensibles de prueba y columnas sintéticas ignoradas. Selección: 28=13.153, 45=6.748, 82=47; total=19.948. Las demás 50.052 filas se excluyen. Incluye un CT existente modificado, un campo local, notas, diario, documento, históricos dentro y fuera del ámbito y preferencias de visibilidad. Los fixtures se eliminan al terminar y no se versionan.

La misma batería se ejecuta con Robolectric/SQLite nativo en el host y con AndroidJUnit4 en Android CI. Verifica: vista previa sin escrituras; carga y búsqueda posterior; un solo prefijo; repetición sin cambios; cancelación durante lectura y después de escrituras; reinicio posterior; GZIP truncado; duplicados entre lotes; cabeceras incompatibles; fallo SQLite mediante trigger; SQLiteFull mediante límite de páginas; conservación completa y exclusión de credenciales. `CsvModelTest` verifica recuperación de controles tras cancelar/fallar y una vista previa posterior; `CsvOperationTest` verifica cierre del stream, arbitraje de commit, aislamiento y contadores/throttling.

Comando:

```sh
AUTOCONTROL_BUILD_NUMBER=244 gradle --no-daemon :storage:testDebugUnitTest :documents:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

Medición del host tras la corrección (17-11-2026, Linux x86_64, 3 CPU disponibles, JDK 17, Robolectric 4.14.1/Android 28 con SQLite nativo):

| Recorrido | Tiempo medido |
| --- | ---: |
| Vista previa inicial (19.948 CT) | 1.060 ms |
| Primera actualización | 1.458 ms |
| Actualización repetida | 1.822 ms |
| Vista previa solo 45 (6.748 CT) | 964 ms |

Los tiempos miden abrir/descomprimir/analizar/consultar y, al aplicar, guardar/confirmar. Excluyen generar el fixture y calcular el hash de auditoría. Son una ejecución concreta, no una garantía ni percentiles. El fixture mide unos 103 MB sin comprimir pero solo 414.231 bytes comprimidos porque sus celdas son homogéneas. No modela la entropía, todas las longitudes de texto ni la latencia del proveedor SAF del Grid real. El primer arranque de Robolectric/descarga del runtime tampoco se contabiliza en estos tiempos.

La finalización tras solicitar cancelación durante lectura y durante escritura fue inferior a 1 ms en esta ejecución (medida después de la solicitud hasta terminar la reversión, resolución de 1 ms). Esto no incluye la latencia de la GUI ni de un proveedor SAF físico.

GitHub Actions ejecuta además `:app:connectedDebugAndroidTest` en Android 35 x86_64; publica informes, el log `GRID_BENCHMARK` y APK tras superar toda la validación. Sus resultados son independientes de esta medición del host.

## Límites de aceptación física y distribución

No se ha usado el archivo real ni una tablet física de 13,4 pulgadas. La prueba física pendiente es seleccionar el Grid MT real mediante su proveedor SAF, verificar progreso y cancelación, confirmar, y buscar varios CT con notas previas. El host no dispone de KVM/emulador local; la ejecución Android se realiza en CI. El rendimiento en el dispositivo y proveedor concretos debe medirse allí antes de declarar cerrado el criterio de campo.

Se mantiene el nombre `AutoControL_tablet_v.1.0 - comp. X`, con compilación posterior a 243. Se conserva el canal debug existente: ver [SIGNING](../SIGNING.md). Una APK con certificado distinto no puede actualizar la instalada conservando automáticamente su espacio privado. No debe desinstalarse una instalación con datos sin exportar y comprobar primero su copia cifrada. La continuidad de la firma es un límite del canal previo, no una migración resuelta por este importador.

Ámbito: solamente AUTOCONTROL-PUBLIC y la rama existente del PR #17. Sin cambios funcionales en MangoSSH, SNIFFER, terminal, diagnósticos, temas, iconos ni PES/BDI; sin merge automático.
