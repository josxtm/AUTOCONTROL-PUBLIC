# COMPILACIONES — AutoControL

La CI pública existente genera APK de la versión simplificada anterior. **No representa todavía la GUI privada aprobada.**

A partir de esta rama, la tarea de compilación se conserva, pero `tools/gui_parity_gate.py` impide la publicación del artefacto final hasta encontrar los componentes estructurales esperados.

El resultado de la nueva CI puede ser **fallo esperado de paridad**; esto no equivale a un fallo del compilador Android. No afirmar que una compilación exitosa, por sí sola, demuestra equivalencia funcional ni visual.

Quedan pendientes: saneamiento de fuentes, restablecimiento de GUI, pruebas de pantalla con datos sintéticos, firma permanente y validación en hardware.
