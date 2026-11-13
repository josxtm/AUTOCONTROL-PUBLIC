# Fase 2 — edición pública independiente

Implementación a partir de requisitos públicos e issue #1. No se han consultado repositorios privados ni copiado sus datos, código, firmas o historial. Se mantiene el identificador público. Los módulos genéricos de catálogo/JSON se adaptan del código público revisado de TELECONTROL-PUBLIC; no se incorporan sus commits ni se mezclan historiales.

## Resultado funcional

Interfaz Kotlin/Compose; módulos Android separados para almacenamiento, motor documental y OCR. Importación JSON incremental, índice por identificador configurable, fichas, campos visibles y notas. Diario independiente paginado. Adjuntos locales importados/exportados mediante SAF, edición UTF-8 sin plantillas, OCR local con modelo aportado por el usuario y licencias verificadas. Copias cifradas con restauración autenticada antes de iniciar la transacción de sustitución.

No hay procedimientos empresariales, documentos oficiales, mapeos ni destinos operativos incorporados. Sí existe diagnóstico LAN y SSH explícito contra el destino introducido por el usuario, mediante el permiso INTERNET; no hay telemetría ni conexiones automáticas. El almacenamiento es privado de esta app, con copia automática y capturas desactivadas. Las copias portables se cifran; SQLite utiliza la protección del almacenamiento del sistema, sin afirmar cifrado propio de base de datos.

## Pruebas

Filtro de privacidad y sus pruebas; pruebas JVM de identificadores, edición/inercia de documentos y cifrado (contraseña errónea, manipulación, truncamiento, aleatoriedad y borrado del array de contraseña); Android Lint; APK y pruebas instrumentadas. En Android real del emulador se prueban SQLite, 20000 registros sintéticos incrementales, errores y reversión, persistencia, notas/diario/campos, documentos grandes y recuperación cifrada. Prueba de navegación/edición/guardado Compose. OCR usa una imagen creada con Canvas y un modelo público Apache-2.0 descargado y comprobado por SHA-256 solo en el entorno CI, fuera de la APK.

La inspección de APK comprueba paquete, versionCode, certificado, permiso interno de receptor AndroidX e INTERNET para SNIFFER/SSH, recursos XML, cadenas DEX, ausencia de assets/raw y tipos de archivos empaquetados. Las bibliotecas nativas permitidas son las públicas revisadas de OCR y AndroidX graphics-path; no hay modelos ni datos de usuario empaquetados. Se conserva la detección original del filtro y se amplía a enlaces, bundles de datos y permisos de red. La carpeta generada `.kotlin` se excluye igual que `.gradle` y `build`; no se versiona.

## Límites pendientes

Edición de documentos limitada a texto UTF-8; adjuntos complejos se conservan sin interpretación ni macros. No hay plantillas ni equivalencia prometida con formatos internos. OCR requiere modelo compatible; revisar siempre su resultado. No se incluyen modelo ni tema en la copia portable. La app no ofrece fusión de catálogos ni cancelación de transacciones largas. Los límites se detallan en README. Los borradores sobreviven a rotaciones mediante ViewModel, pero no a muerte del proceso. Puede quedar un temporal privado tras una interrupción forzada del proceso; no se almacena en rutas compartidas.

Pendientes: validación física con proveedores SAF, TalkBack, tablet/rotación, memoria de dispositivos modestos y OCR en idiomas elegidos por el usuario; pruebas de actualización del futuro canal estable y firma permanente según SIGNING. La versión inicial de esquema público es 1; no existía base de trabajo en la fase anterior. Se prueba reapertura y recuperación dentro de esta edición. Cualquier cambio futuro de esquema requiere una migración explícita con pruebas, sin borrar datos como fallback.

## Estabilización SSH (Fase 1)

No cambia el esquema SQLite, paquete, firma ni documentos. Los pines nuevos usan una clave versionada derivada del host normalizado y puerto. La clave antigua se busca con la representación exacta usada por JSch: host para el puerto estándar, host entre corchetes y puerto para los demás. Un pin antiguo coincidente se migra sin volver a solicitar confianza; cualquier discrepancia se rechaza. Si no existe una identidad compatible, se exige nueva comprobación explícita fuera de banda. No se reutiliza un pin del puerto estándar para otro puerto.

El perfil sigue siendo opcional y sin contraseña. Los resultados y el historial SSH siguen siendo volátiles. La cancelación de diagnósticos no requiere borrar datos ni cerrar toda la app. La terminal continúa integrada; BDI/PES y los diez temas no se modifican. Las limitaciones de firma debug efímera y pruebas pendientes en tablet permanecen vigentes.
