# Fase 2 — Auditoría y plan de recuperación de la GUI industrial

**Repositorio:** `josxtm/AUTOCONTROL-PUBLIC`
**Fecha de auditoría:** 2026-11-13
**Base funcional de referencia:** `origin/feat/sniffer-mangossh-full-integration` (`a4fff705`)
**Rama documental:** `docs/goose-fase2-gui-industrial`
**Alcance:** investigación de lectura. No se modifica código, no se conecta con equipos, no se genera APK y no se incorporan datos privados.

## 1. Trazabilidad

Se actualizó `origin` mediante `git fetch origin` y se comprobó que la rama documental desciende directamente de `origin/feat/sniffer-mangossh-full-integration`, sin modificar `main`, la rama de Fase 1 ni la rama funcional de Codex. El inventario siguiente se obtuvo leyendo las referencias remotas y sus historiales; los archivos de las ramas históricas no se copiaron a la rama documental.

La diferencia de esta entrega respecto de la base es exclusivamente este Markdown. Los ejemplos de red citados proceden de maquetas sintéticas reservadas (`192.0.2.0/24`); no son redes operativas.

## 2. Inventario de ramas y commits útiles

| Referencia | Utilidad histórica | Estado frente a la base |
|---|---|---|
| `origin/feat/tablet-industrial-gui-mangossh` | Primera integración de shell tablet, selector de temas y SNIFFER/MangoSSH. Conserva `AutoThemes.kt`, `MainActivity.kt`, `WorkspaceModel.kt`, `SnifferPanel.kt` y `SshTransport.java`. | Antecedente funcional; su integración SSH es la que debe preservarse. |
| `origin/feat/sniffer-gui-b-final` | Recupera el concepto B como layout real de tres paneles y conserva el transporte. Commits clave: `c4b314a` y `bf292e4`. | La base actual contiene su funcionalidad SSH, pero no su composición B final. |
| `origin/feat/industrial-gui-shell-correction` | Recupera cabecera persistente, rail lateral compacto, workbench CT de doble panel y correcciones de scroll. Commits: `b43cd96`, `4c43b95`, `0f32cd1`. | Sus componentes de shell no están en la base actual. |
| `origin/feat/gui-overflow-right-panel-fix` | Iteración más completa de GUI industrial: `IndustrialCtPanel.kt`, `IndustrialModuleTools.kt`, `IndustrialWidgets.kt`, `LanStatus.kt`, captura tablet, tags de prueba y herramientas para CT/PES/BDI. | No debe fusionarse automáticamente; es una fuente de recuperación visual y de pruebas. |
| `origin/feat/sniffer-mangossh-full-integration` | Referencia funcional obligatoria para esta Fase 2. Contiene SNIFFER LAN, política de endpoint, offsets, SSH, historial efímero y pruebas. | Base exclusiva de la rama documental y destino de una futura implementación de Codex. |

### Secuencia histórica relevante

- `515d057` — define las diez paletas aprobadas.
- `541ec6b` — añade rail tablet, pantalla SNIFFER y selector de temas.
- `3f31329`, `3a69860`, `0b182a3` — maquetas A, B y C en T4 Gris Industrial.
- `a44306b` — documenta ventajas y limitaciones de A/B/C; recomienda provisionalmente B.
- `b43cd96` — shell industrial: cabecera, rail, CT dual-pane y layout tablet.
- `4c43b95`, `0f32cd1` — corrigen tamaño de superficie y scroll anidado.
- `c4b314a`, `bf292e4` — materializan B en SNIFFER y corrigen foreground del terminal.
- `12afd7f`, `697f087`, `e265c0b`, `aafebe4` — herramientas laterales y acciones existentes en módulos tablet.
- `f36afe0`, `711be4e`, `ef72fe4` — compactación de terminal, cabecera, selector, eventos y prevención de clipping.
- `053da06`, `c8ee69f`, `e043302` — capturas tablet sintéticas, artefactos de validación y semántica estable.
- `f400f57` — endurece el control de privacidad de la rama de corrección.

## 3. Recuperación de GUI histórica: presente, aprobado y desaparecido

### 3.1 Presente en la base funcional

La base conserva:

- Compose, Material 3 y adaptación básica de ancho.
- Navegación por módulos: CT, Diario, Documentos, Copias, SNIFFER y Configuración.
- Los diez temas y la selección persistida en `WorkspaceModel`.
- Catálogo JSON local, búsqueda exacta, campos visibles, notas, diario, documentos, OCR y copias cifradas.
- `SnifferPanel.kt` con pestañas Escaneo/Pings/Equipos/MangoSSH.
- `LanTargets.java` con siete roles y offsets; validación IPv4/CIDR y límites de subred.
- `SshTransport.java`, `EndpointPolicy.java` y `CommandHistoryPolicy.java` con huella fijada, diagnóstico restringido, terminal explícita y filtrado de historial.
- Pruebas unitarias de offsets, política SSH, endpoint y privacidad.

### 3.2 Componentes visuales aprobados que desaparecieron de la base

Los siguientes están implementados o dibujados en ramas históricas, pero no en la referencia funcional actual:

1. Cabecera persistente de instrumento, fuera de cualquier `LazyColumn`.
2. Rail lateral compacto con módulo activo, marca TABLET y estado “sin telemandos”.
3. Cabecera contextual de CT con selección, red y estado de prueba.
4. Workbench CT de dos paneles (`IndustrialCtPanel.kt`).
5. Rail lateral operativo compartido para diario, documentos, copias, configuración e introducción PES/BDI (`IndustrialModuleTools.kt`).
6. Paneles con encabezado técnico, gradientes y botones compactos (`IndustrialWidgets.kt`).
7. Iconos vectoriales locales `TechIcon`, en lugar de glifos de fuente.
8. B de tres paneles: equipos, diagnóstico/ping y terminal MangoSSH.
9. Banda de eventos y pings integrada bajo el espacio principal.
10. Indicador informativo de Wi-Fi/Ethernet (`LanStatus.kt`), sin confundirlo con alcance al CT.
11. Pruebas de captura real de emulador tablet y tags Compose estables de la rama de overflow.

### 3.3 No debe interpretarse como aprobado o existente

- Las maquetas SVG A/B/C son diseños estáticos, no capturas Compose.
- PES/BDI no tiene motor documental, formularios ni validación oficial en el repositorio público.
- La existencia de un rail visual no implica que sus acciones sean nuevas: las futuras acciones deben delegar en `WorkspaceModel` y launchers ya existentes.
- La prueba de captura sintética no demuestra conexión SSH real ni compatibilidad con hardware Mango.

## 4. Especificación documental de tablet de 13,4 pulgadas

### Objetivo

Formato apaisado, alta densidad informativa y lectura técnica a distancia corta. La interfaz debe aprovechar el ancho sin crear una pantalla de controles fragmentada. La geometría histórica trabaja con una cabecera persistente, rail estrecho y un workspace dividido en paneles con títulos compactos.

### Estructura estable

1. **Cabecera persistente:** marca AutoControL, distintivo TABLET, módulo actual, tema/compilación y estado general. No debe desplazarse ni desaparecer al hacer scroll.
2. **Rail lateral:** navegación CT, Diario, Documentos, Copias, SNIFFER y Configuración. Cada entrada tiene icono V10, texto, estado activo y área táctil accesible.
3. **Contexto:** CT seleccionado, red local disponible como dato de catálogo, estado de diagnóstico y aviso de que el entorno es local/sin telemandos.
4. **Workspace:** paneles técnicos con títulos, separadores, estados, acciones compactas y scroll interno solo cuando sea necesario.
5. **SNIFFER:** equipos a la izquierda, diagnóstico/pings en el centro y MangoSSH a la derecha, siguiendo B como composición de referencia.
6. **Eventos:** una zona inferior o lateral para resultados, cancelación y avisos, sin desplazar la terminal de forma impredecible.

### Reglas de densidad y adaptación

- La tablet debe mostrar simultáneamente contexto, acción principal y estado; no ocultar la acción detrás de una pestaña si hay ancho suficiente.
- Los títulos y valores largos deben envolver o usar un contenedor dimensionado; nunca truncarse silenciosamente.
- No anidar `LazyColumn` y `verticalScroll` sin un límite claro.
- SNIFFER debe vivir fuera de listas virtualizadas para que la sesión no se destruya al desplazarse.
- En móvil se permite una columna ordenada: contexto → equipos → diagnóstico → conexión → consola. La semántica y las acciones deben ser las mismas.
- La consola debe reservar espacio útil, mantener salida legible y conservar copia/limpieza/historial volátil.

## 5. Temas, gráficos e iconos V10

### Temas T1–T10

`AutoThemes.kt` conserva exactamente estas paletas: T1 Claro Profesional, T2 Azul Corporativo, T3 Verde Técnico, T4 Gris Industrial, T5 Arena/Trabajo de Campo, T6 Noche Suave, T7 Rojo Operativo, T8 Púrpura Técnico, T9 Turquesa Moderno y T10 Inspiración macOS. T4 es la referencia visual de las maquetas; T6 es el único tema oscuro explícito.

No se propone cambiar colores ni nombres. La recuperación debe aplicar el tema seleccionado a superficies, paneles, estados, texto, foco, iconos y controles. En particular, la rama histórica ajusta `surfaceVariant`, `outline`, `onSurface` y `onSurfaceVariant` de T4 para soportar la estética industrial.

### Recursos encontrados y faltantes

- `app/src/main/res/drawable/ic_public.xml` existe en la base, pero es el icono público del launcher; no es el set V10 de navegación.
- En la rama de overflow existe `IndustrialWidgets.kt` con `TechIcon` vectorial local para `home`, `network/ct`, `terminal`, `search`, `settings`, `database`, `play`, `clear/cancel`, `expand`, `status`, `lock`, `menu` y `bell`.
- La misma rama usa gradientes y geometría local en encabezados y botones, dando la apariencia semitridimensional V10 sin cargar imágenes remotas.
- Las maquetas A/B/C contienen la dirección visual V10, pero son SVG de diseño y no recursos Android listos para producción.
- La base funcional no contiene ese inventario de iconos V10 ni sus estados normal/activo/deshabilitado/aviso.

**Decisión:** los iconos aprobados no se rediseñan. Codex debe recuperar, portar o sustituir únicamente mediante equivalentes públicos trazables los recursos históricos, manteniendo forma, proporción, semántica y contraste. No debe convertir la auditoría en una exploración de iconografía alternativa.

## 6. SNIFFER LAN y MangoSSH: integración sin regresión

### Contrato que se debe conservar

- `WorkspaceModel` entrega únicamente la red del registro local seleccionado; si falta, se permite entrada manual.
- `LanTargets` calcula siete hosts a partir de base/CIDR y valida rango; no embebe direcciones operativas.
- Diagnóstico Android y “Ping desde Mango” son rutas distintas, ambas cancelables.
- `SshTransport` conserva autenticación temporal, `StrictHostKeyChecking`, pin de huella SHA-256, rechazo de clave cambiada, desconexión y terminal interactiva.
- `EndpointPolicy` mantiene hosts/usuarios válidos y una lista cerrada de diagnósticos, incluido ping de un host IPv4 ya validado.
- La contraseña no se guarda; el perfil solo puede guardar host, puerto y usuario.
- `CommandHistoryPolicy` mantiene historial efímero y evita indicios de secretos.
- La sesión debe cerrarse al salir del panel y no debe mezclarse entre CT distintos.

### Superposición visual recomendada

1. Extraer la shell industrial como contenedor visual, sin reescribir el transporte.
2. Mantener el `SnifferPanel` como dueño del estado SSH, jobs, aprobación de huella y consola.
3. Reorganizar sus controles en paneles B: lista de equipos, resultados/eventos y MangoSSH.
4. Conectar botones históricos a las acciones existentes: escaneo, ping remoto, cancelar, activar terminal, copiar, limpiar, historial y desconectar.
5. Garantizar que el cambio de tamaño, rotación simulada o scroll no recree `SshTransport` ni pierda el estado.
6. Mantener mensajes explícitos: “sin respuesta” no equivale a desconectado; red disponible no equivale a CT alcanzable.

No se debe sustituir MangoSSH por otro transporte ni ampliar la lista de comandos por motivos visuales.

## 7. BDI/PES: pantalla visual frente a motor real

### Evidencia visual

La rama `feat/gui-overflow-right-panel-fix` añade una entrada de navegación `PES / BDI`, un panel de herramientas y texto de estado para documentación local. `IndustrialModuleTools.kt` reserva el módulo y permite abrir documentos importados. La cabecera histórica y los rails de acciones hacen visible el espacio donde posteriormente viviría PES/BDI.

### Lo que realmente existe

No se encontró un motor BDI/PES oficial, formularios de operación, parser de procedimientos, validación de maniobras, catálogo oficial ni exportación específica. El módulo histórico declara expresamente que el motor definitivo no está disponible y que no se simulan exportaciones ni validaciones. El almacenamiento/documentos público solo ofrece adjuntos inertes y texto editable, sin macros ni interpretación de plantillas.

### Propuesta futura, sin desarrollar ahora

- Mantener PES/BDI como módulo visual reservado y claramente marcado como “motor no disponible”.
- En una fase posterior, definir primero un contrato de datos, permisos, revisión de licencias y política de seguridad.
- Integrar únicamente documentos que el usuario importe localmente, sin inventar procedimientos ni rellenar formularios automáticamente.
- Reutilizar documentos, copias cifradas, notas y eventos existentes solo cuando el contrato esté aprobado.
- Añadir pruebas sintéticas de navegación y estados vacíos antes de cualquier motor; no añadir acciones de campo, telemando o automatización desde esta auditoría.

## 8. Archivos recuperables y archivos que faltan

### Recuperables conceptualmente desde referencias públicas

- Shell y navegación: `MainActivity.kt` de `industrial-gui-shell-correction`.
- Workbench CT: `IndustrialCtPanel.kt`.
- Herramientas por módulo: `IndustrialModuleTools.kt`.
- Gráficos V10: `IndustrialWidgets.kt`.
- Estado informativo de red: `sniffer/LanStatus.kt`.
- Composición B: `SnifferPanel.kt` de `sniffer-gui-b-final`.
- Maquetas y criterios: `design/sniffer-concepts/*.svg` y `README.md` de la rama de conceptos.
- Evidencia de importación extendida: `docs/INDUSTRIAL-REFERENCE-JSON.md`, solo como documentación técnica pública; no implica introducir datos de ejemplo operativos.

### Faltantes en la referencia funcional actual

- Shell industrial persistente y rail lateral.
- `IndustrialCtPanel`, `IndustrialModuleTools`, `IndustrialWidgets` y `LanStatus`.
- Composición B simultánea de SNIFFER.
- Recursos V10 Android con estados y semántica accesible.
- Navegación/pantalla visual PES/BDI completa como módulo, aunque el motor siga ausente.
- Suite de captura tablet y validación visual de todos los módulos en esta base concreta.

## 9. Riesgos de integración

1. **Pérdida de sesión SSH:** recrear el panel o colocarlo dentro de una lista puede desconectar el Mango.
2. **Regresión de almacenamiento:** cambiar la forma de CT o alias puede romper JSON, notas, índices, OCR o copias.
3. **Clipping y overflow:** títulos de CT, IP, huella y estados largos necesitan wrapping y límites tablet/móvil.
4. **Confusión funcional:** una pantalla PES/BDI visual puede interpretarse como motor implementado.
5. **Privacidad:** maquetas, capturas o pruebas podrían incluir accidentalmente IP, matrículas o documentos reales.
6. **Seguridad SSH:** un rediseño no puede relajar pinning, aceptación fuera de banda, limpieza de contraseña o lista de comandos.
7. **Tema:** iconos y gradientes de T4 pueden perder contraste en T6 o en cualquier otra paleta.
8. **Scroll:** paneles con scroll anidado pueden ocultar acciones o hacer imposible operar en móvil.
9. **Dependencia histórica:** copiar una rama completa mezclaría correcciones de JSON, CI o privacidad fuera del objetivo visual.
10. **Falsa validación física:** screenshots y tests sintéticos no prueban firmware, ICMP ni hardware Mango.

## 10. Orden recomendado de implementación para Codex

1. Congelar contratos: `WorkspaceModel`, almacenamiento, JSON, OCR, documentos, backup y APIs SNIFFER/MangoSSH.
2. Crear la shell visual persistente: cabecera, rail, contexto y contenedor de workspace, sin cambiar acciones.
3. Recuperar `IndustrialWidgets`/iconos V10 y verificar contraste de T1–T10; no rediseñar iconos.
4. Reintroducir el workbench CT, delegando búsqueda, notas, campos, importación y selector en operaciones existentes.
5. Reorganizar SNIFFER conforme a B, manteniendo exactamente transporte, estado, jobs, seguridad y comandos.
6. Añadir rail de herramientas para Diario, Documentos, Copias y Configuración usando launchers/modelo existentes.
7. Reservar PES/BDI como pantalla informativa sin motor ni formularios operativos.
8. Añadir pruebas semánticas, de estado vacío, rotación/tamaño, scroll y preservación de sesión.
9. Ejecutar privacidad, unit tests, lint y pruebas instrumentadas antes de cualquier APK de validación.
10. Revisión visual humana de tablet y móvil; solo después solicitar aprobación para integrar cambios funcionales.

## 11. Guion de desarrollo

Para cada paso, Codex debe trabajar en commits pequeños y reversibles:

- **Commit A — shell:** cabecera, rail y contenedores; cero cambios de modelo.
- **Commit B — gráficos:** recursos V10 públicos, descripciones accesibles y estados de contraste.
- **Commit C — CT:** panel doble, importación, búsqueda, ficha y acciones rápidas existentes.
- **Commit D — SNIFFER:** layout B; pruebas de que la sesión sobrevive scroll/cambio de panel.
- **Commit E — módulos:** herramientas laterales para diario, documentos, copias y configuración.
- **Commit F — PES/BDI:** pantalla informativa y estado “motor no disponible”, sin documentos nuevos.
- **Commit G — regresión:** screenshots sintéticos, semántica, móvil/tablet y privacidad.

Cada commit debe declarar qué se conserva y comprobar que no añade datos operativos. No se debe hacer merge a `main` como parte de este plan.

## 12. Pruebas y criterios de aceptación

### Regresión funcional

- Tests existentes de almacenamiento, JSON, OCR, documentos, copias y privacidad siguen pasando.
- `LanTargets`, `EndpointPolicy`, `CommandHistoryPolicy` y pinning SSH conservan sus pruebas.
- Importar/buscar CT, guardar nota, abrir SNIFFER, escanear, cancelar, copiar, limpiar y desconectar mantienen el comportamiento.
- Una sesión SSH no se destruye al desplazar el workspace ni por recomposición.
- PES/BDI solo muestra estado informativo y no fabrica operaciones ni documentos.

### Tablet 13,4 pulgadas

- Cabecera y rail permanecen visibles.
- CT muestra búsqueda y ficha sin clipping.
- SNIFFER muestra simultáneamente equipos, pings/eventos y MangoSSH en orientación apaisada.
- Host, puerto, usuario, estado de huella, IP/CIDR y estados largos son legibles.
- Los siete roles y sus offsets se muestran sin datos operativos.
- Screenshots usan exclusivamente registros sintéticos y rangos reservados.

### Móvil

- Navegación y acciones son accesibles en una columna.
- No hay scroll horizontal accidental ni controles fuera de pantalla.
- El teclado no oculta permanentemente acciones.
- El terminal, historial y diálogos de huella siguen siendo utilizables.

### Temas y accesibilidad

- T1–T10 se pueden seleccionar sin alterar datos.
- Iconos V10 tienen descripción semántica donde corresponda y contraste suficiente.
- T4 mantiene la estética industrial; T6 mantiene legibilidad en oscuro.
- Estados activo, error, advertencia, sin respuesta y no configurado no dependen solo del color.

### Privacidad y entrega

- El escáner de privacidad no encuentra IP, matrículas, credenciales, documentos ni catálogos reales.
- No se ejecutan conexiones a equipos reales.
- No se genera APK como parte de esta auditoría.
- La rama de entrega contiene exclusivamente `docs/goose/FASE-2-GUI-INDUSTRIAL.md` respecto de la base funcional.

## 13. Veredicto

**AUDITORÍA COMPLETADA — RECUPERACIÓN PLANIFICADA, NO IMPLEMENTADA.**

La recuperación correcta consiste en superponer la shell y el lenguaje visual industrial histórico sobre los contratos funcionales actuales, priorizando B para SNIFFER y preservando MangoSSH sin sustitución. PES/BDI debe permanecer como reserva visual hasta que exista un motor aprobado. Codex puede comenzar por la shell, los iconos V10 existentes y el workbench CT, con regresión continua de almacenamiento, privacidad, móvil y tablet.
