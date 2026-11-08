# PARIDAD_GUI — AutoControL

**Resultado de auditoría: BLOQUEANTE — GUI pública no equivalente a la versión privada aprobada.**

## Referencias técnicas privadas de lectura exclusiva

- Repositorio `josxtm/AutoControL`.
- Ramas/commits de referencia: `feat/document-engine-foundation @ 3114b8ec85cb3e5f863929865d5675db231c14e5; feat/sniffer-ui-ssh-simulator @ a6a48bd1b2ae21b23e7b3cc4a9f0878eaee2f3ed`.
- No se transfieren historiales, datos, fotos, claves, recursos restringidos ni documentación operativa.

## Qué debe restaurarse

Interfaz Compose original modular con navegación CT, SNIFFER, PES/BDI y Configuración, componentes originales, iconos y temas en móvil/tablet.

## Diferencias observadas

La interfaz pública usa un área de trabajo con pestañas genéricas y no reproduce la estructura ni iconografía original. Las ramas privadas documental y SNIFFER son divergentes; deben compararse y reconciliarse sin divulgar procedimientos o datos restringidos.

## Aceptación obligatoria

- [ ] Diseño, ubicación, iconos, temas, geometría y navegación idénticos a los aprobados.
- [ ] Las funciones antiguas siguen funcionando sin pérdida de datos.
- [ ] Todas las dependencias y recursos tienen revisión de seguridad y licencias.
- [ ] Comparación visual real con datos sintéticos en pantalla móvil y tablet.
- [ ] Firma y actualizaciones seguras comprobadas; APK generada tras pruebas.
- [ ] Aprobación explícita del propietario antes de integrar `main`.

El control `tools/gui_parity_gate.py` bloquea un conjunto mínimo de fuentes que deben existir. **No es una prueba pixel-perfect** ni sustituye revisar las pantallas. Su ejecución se ubica después de compilar y antes de subir la APK para impedir nuevas previews con interfaz incorrecta.

[Trabajo pendiente](https://github.com/josxtm/AUTOCONTROL-PUBLIC/issues/4) · [PR de trabajo](https://github.com/josxtm/AUTOCONTROL-PUBLIC/pull/3).
