# Guía de animaciones Material 3 · AutoControL público 0.3.0

## Alcance

Solo cambia la presentación Compose. Los módulos de datos y documentos, la importación local, las copias cifradas y el OCR conservan sus contratos. No se añaden permisos, peticiones de red, bibliotecas de terceros ni contenido de ejemplo con datos reales.

## Tokens de movimiento

| Interacción | Efecto | Duración |
| --- | --- | --- |
| Navegar a una sección posterior | deslizamiento hacia la izquierda + fundido | entrada 280 ms, salida 220 ms |
| Volver a una sección anterior | deslizamiento hacia la derecha + fundido | entrada 280 ms, salida 220 ms |
| Pulsar una acción principal | ripple Material 3 + escala 0,985 | 120 ms |
| Abrir accesos rápidos | expansión vertical + fundido | 240 ms |
| Cerrar accesos rápidos | contracción vertical + fundido | 200 ms |
| Campos y ayuda de seguridad | expandir/contraer + fundido | 180–240 ms |
| Mostrar/ocultar estado | expansión vertical + fundido | 120–180 ms |

Se usan los componentes y las transiciones declarativas estables de Jetpack Compose, con indicaciones Material 3. La escala global de animaciones del sistema es respetada por Compose; no se fuerza ninguna animación continua. El contenido sigue siendo legible si el usuario desactiva las animaciones en los ajustes de accesibilidad.

## Pruebas

- `MaterialMotionTest` navega por los cuatro módulos y comprueba los paneles.
- `ComposeSmokeTest` mantiene el flujo de creación de documentos y diario.
- Ejecutar lint, pruebas y empaquetado según `README.md` y el workflow CI.
- Comprobar manualmente la respuesta táctil, el desplazamiento con teclado abierto y el comportamiento en teléfono y tablet.
- Comprobar el cambio de sección con accesibilidad / escala de animación cero, sin bloquear acciones.

## Regla de compatibilidad

Se conserva el `applicationId` público y se incrementa `versionCode` a 3. La actualización sin perder datos **requiere la misma identidad de firma** entre la APK instalada y la nueva; las previews debug efímeras no garantizan esto. No desinstalar la versión que contenga datos sin exportar antes una copia de seguridad.
