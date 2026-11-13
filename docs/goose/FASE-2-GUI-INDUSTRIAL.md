# Fase 2 — Auditoría de recuperación de la GUI industrial

**Fecha:** 2026-11-13
**Base auditada:** `origin/feat/sniffer-mangossh-full-integration` (`a4fff705072df9274687e8d25a316c9cb80f13f3`)
**Rama del informe:** `docs/goose-fase2-gui-industrial`
**Alcance:** auditoría documental previa a cualquier corrección de interfaz. No se modifica código, no se genera APK y no se incorporan datos de CT.

## 1. Trazabilidad y límites

- Se ejecutó `git fetch origin` antes de crear la rama.
- La rama se creó directamente desde la referencia remota indicada; no se tomó como base `main` ni la rama de Fase 1.
- La auditoría es de lectura sobre la base seleccionada y sobre referencias históricas públicas del propio repositorio. Las referencias privadas se citan únicamente como antecedentes de paridad ya documentados; no se copiaron recursos, historial, procedimientos ni datos restringidos.
- Este documento es el único cambio previsto en Fase 2. La implementación de correcciones queda expresamente para Codex.

## 2. Diagnóstico ejecutivo

La base contiene una GUI Compose funcional y adaptativa, pero todavía no constituye una recuperación completa de la GUI industrial histórica. Conserva el esqueleto de navegación, el selector de diez temas y un SNIFFER LAN operativo con transporte SSH, pero la pantalla se presenta principalmente como pestañas y formularios Material 3. La referencia industrial histórica añade una cabecera persistente, rail lateral compacto, paneles con jerarquía instrumental, iconografía V10 y una composición tablet de tres zonas.

El punto de recuperación recomendado es el **concepto B — Diagnóstico Técnico** de `design/sniffer-concepts`: equipos a la izquierda, comparación de pings en el centro y terminal MangoSSH a la derecha. La propuesta B es una referencia visual histórica, no una autorización para implementar en esta fase.

## 3. Los diez temas aprobados

`AutoThemes.kt` conserva exactamente diez paletas y `MainActivity.kt` permite seleccionarlas desde Configuración. El índice 3 (T4) es el tono industrial utilizado por las maquetas históricas; T6 es el único tema explícitamente oscuro.

| ID | Nombre | Acento | Fondo / superficie | Observación de auditoría |
|---|---|---|---|---|
| T1 | Claro Profesional | `#487B96` | `#F4F7FA` / blanco | Base clara y sobria |
| T2 | Azul Corporativo | `#366C9C` | `#E7F0F8` / blanco | Contraste corporativo |
| T3 | Verde Técnico | `#47795A` | `#F1F6EC` / blanco | Lectura técnica |
| T4 | Gris Industrial | `#A92E35` | `#E5E7E9` / `#F8F8F7` | Referencia de las maquetas V10 |
| T5 | Arena / Trabajo de Campo | `#9A7343` | `#F8F2E8` / `#FFFCF7` | Uso de campo |
| T6 | Noche Suave | `#90B9C9` | `#202B38` / `#2A3949` | Tema oscuro |
| T7 | Rojo Operativo | `#B44B4D` | `#FFF0EF` / blanco | Estado operativo |
| T8 | Púrpura Técnico | `#8069AB` | `#F3F0FA` / blanco | Variante técnica |
| T9 | Turquesa Moderno | `#348C94` | `#ECF8F8` / blanco | Variante moderna |
| T10 | Inspiración macOS | `#688CB8` | `#ECF1FA` / `#F8FAFE` | Variante de inspiración macOS |

**Resultado:** el inventario de temas está presente. La deuda de recuperación no es la cantidad de paletas, sino aplicar coherentemente el tema elegido a la geometría industrial, estados, rail, paneles e iconos. El texto actual de Configuración identifica además la “iconografía V10”, aunque el recurso de aplicación público sigue siendo `res/drawable/ic_public.xml` y la navegación usa glifos de texto en la evolución histórica.

## 4. Iconos V10 y lenguaje visual

La evidencia pública histórica está en el commit `b43cd96` y en las maquetas `design/sniffer-concepts/version-a.svg`, `version-b.svg` y `version-c.svg` de la referencia de conceptos. El rail industrial usa una iconografía compacta para:

- CT / Centros CT: símbolo de ficha o cuadrícula.
- Diario: registro o líneas.
- Documentos: biblioteca/archivo.
- Copias: respaldo.
- SNIFFER: enlace/onda de red.
- Configuración: engranaje.

En `b43cd96` esos identificadores aparecen como glifos (`▣`, `▤`, `▥`, `▧`, `⌁`, `⚙`) dentro de un rail de 162 dp, con cabecera oscura, estado seleccionado por color de acento y etiquetas secundarias. Esto permite recuperar el **lenguaje** V10 sin afirmar que esos glifos sean el paquete final de recursos. La base actual usa una navegación más simple y no contiene una familia de drawables V10 dedicada.

**Criterio para Codex:** no inventar iconos nuevos ni copiar recursos privados. Primero debe definirse el inventario público de iconos, sus estados (normal, seleccionado, deshabilitado, advertencia) y su contraste en los diez temas; después se podrá implementar y validar visualmente.

## 5. Componentes históricos identificados

La recuperación histórica se puede descomponer en estos componentes públicos:

1. **Cabecera de instrumento persistente:** marca AutoControL, distintivo TABLET, módulo actual y nombre de compilación; queda fuera de contenedores desplazables.
2. **Rail lateral compacto:** módulos CT, Diario, Documentos, Copias, SNIFFER y Configuración, con selección visible y estado “sin telemandos”.
3. **Cabecera de contexto CT:** CT seleccionado, IP base y estado de conexión/prueba; en maquetas se representa únicamente con datos sintéticos.
4. **Espacio de trabajo de CT:** catálogo, búsqueda exacta, campos visibles y nota del registro, con panel de herramientas lateral en la evolución industrial.
5. **Matriz de equipos del CT:** siete roles con offsets relativos documentados públicamente: Router (+1), CC T1 (+2), CC T2 (+3), SABT T2 (+9), SABT T1 (+11), FFAA1 (+13) y Remota (+14).
6. **Panel de diagnóstico/ping:** resultados comparables, cancelación, estados “sin respuesta” y exclusión “no instalado”.
7. **Terminal MangoSSH:** consola oscura, entrada de comandos, comandos rápidos, historial volátil y control de activación explícita.
8. **Panel de conexión y confianza:** host, puerto, usuario, conexión/desconexión y confirmación fuera de banda de huella SSH.
9. **Banda de eventos:** salida de escaneo, pings y avisos, sin convertir una ausencia ICMP en prueba de desconexión.
10. **Paneles adaptativos:** composición A/B/C para tablet horizontal; B fue la recomendación provisional y es la candidata para recuperar la jerarquía técnica.

La base actual mantiene los elementos funcionales 5–9 dentro de `SnifferPanel.kt`, pero la composición es una pantalla de pestañas (“Escaneo”, “Pings”, “Equipos”, “MangoSSH”), no la composición industrial simultánea de los conceptos.

## 6. Integración SNIFFER LAN / MangoSSH auditada

### 6.1 Entrada de contexto

`WorkspaceModel.kt` expone de forma limitada la IP LAN del registro local seleccionado. `MainActivity.kt` mantiene SNIFFER fuera de la `LazyColumn`, evitando que una sesión abierta se destruya por virtualización al desplazarse. Si el catálogo no aporta IP LAN, el operador puede introducir una base manual.

### 6.2 Equipos y direccionamiento

`LanTargets.java` mantiene una tabla pública de siete nombres y offsets, valida IPv4, CIDR, prefijos y rango de hosts. Las direcciones se calculan en tiempo de ejecución; no se embeben IP operativas en la aplicación. `SnifferPanel.kt` permite marcar equipos como “No instalado” y excluirlos del diagnóstico.

### 6.3 Diagnóstico local y remoto

- El diagnóstico local usa comprobación de alcanzabilidad desde Android.
- “Ping desde Mango” envía únicamente comandos que pasan `EndpointPolicy.diagnostic` y utiliza la sesión SSH existente.
- Hay cancelación de escaneo, salida acotada y mensajes que distinguen “sin respuesta” de “no configurado”.
- La salida es indicativa: no autoriza maniobras ni prueba por sí sola una avería o desconexión.

### 6.4 Transporte y confianza SSH

`SshTransport.java` usa JSch, autenticación temporal, `StrictHostKeyChecking=yes`, huella SHA-256 fijada y rechazo de cambio de clave. La primera aceptación requiere confirmación explícita mostrada por la interfaz; la contraseña se limpia después de conectar y el perfil guarda solo host, puerto y usuario.

La terminal interactiva requiere una segunda confirmación. Los comandos rápidos están limitados por política; el historial es de sesión y filtra indicios de secretos. La salida de terminal elimina secuencias ANSI básicas, por lo que la deuda conocida es la ausencia de emulación ANSI completa.

### 6.5 Límites de la integración

La compilación y las pruebas públicas no verifican un Mango físico ni una red real. Deben conservarse como pendientes de validación autorizada: compatibilidad de comandos del firmware, rutas de red, respuesta ICMP, comportamiento en tablet de baja altura y visualización de terminal. No se deben añadir credenciales, hosts, matrículas, catálogos ni capturas reales.

## 7. Brechas de recuperación para la siguiente implementación

| Prioridad | Brecha | Criterio de cierre posterior |
|---|---|---|
| Alta | Sustituir la sensación de pestañas por shell industrial persistente | Cabecera + rail + contexto + workspace sin perder el estado SSH |
| Alta | Recuperar composición tablet de SNIFFER | Equipos, pings y MangoSSH visibles según concepto aprobado |
| Alta | Inventariar iconos V10 reales y estados | Recursos públicos, consistentes y contrastados en T1–T10 |
| Media | Unificar estados y eventos | Estados legibles, no truncados y con semántica accesible |
| Media | Validar adaptación móvil/tablet | Sin clipping, scroll anidado incorrecto ni destrucción de sesión |
| Media | Comparación visual sintética | Capturas de prueba sin datos CT y revisión humana de geometría |
| Baja | Mejorar representación de terminal | Documentar limitación ANSI o incorporar solución revisada |

## 8. Privacidad y control de cambios

La auditoría no contiene datos privados de CT. Los únicos valores de red que aparecen como ejemplos son documentación sintética de rango reservado (`192.0.2.0/24`) en las maquetas históricas; no representan equipos conectados. No se incluyen IP reales, matrículas, credenciales, huellas operativas, fotos, catálogos ni documentos.

La entrega de esta fase debe quedar limitada a este archivo Markdown. No se modifican `main`, la rama de Fase 1 ni fuentes de Android; tampoco se genera APK. La rama se publicará de forma exclusiva en GitHub tras un commit documental.

## 9. Veredicto

**AUDITORÍA COMPLETADA — IMPLEMENTACIÓN PENDIENTE.**

La base funcional permite continuar, pero la recuperación industrial requiere que Codex implemente la shell histórica, la composición B (si el propietario mantiene la recomendación), el inventario V10 y la verificación visual sintética sin cruzar datos privados. Este informe no autoriza por sí mismo cambios de código ni integración en `main`.
