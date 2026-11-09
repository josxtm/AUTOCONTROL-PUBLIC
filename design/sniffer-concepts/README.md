# SNIFFER LAN + MangoSSH — Tres propuestas de interfaz
**Solo diseño; no modifica ni ejecuta SSH, pings o telemandos.** Formato tablet horizontal, T4 Gris Industrial, iconos V10 como dirección visual. Archivos SVG editables a 1600×900, con datos ficticios (192.0.2.0/24).

- [A — Consola Central](version-a.svg): consola ancha y dominante, equipos a la izquierda, comandos a la derecha.
- [B — Diagnóstico Técnico](version-b.svg): tabla de pings en el centro, terminal a la derecha, lista de equipos a la izquierda.
- [C — Espacio Flexible](version-c.svg): consola ampliada y zona prevista para paneles plegables y ajustables.

| Concepto | Ventaja | Limitación |
|---|---|---|
| A | Lectura de consola muy cómoda | Menos espacio para comparar pings |
| B | Mejor visión conjunta durante PES | Terminal más estrecha |
| C | Prioriza personalización y multitarea | Mayor complejidad de implementación |

**Recomendación provisional:** B. Falta elección del usuario.

Los SVG son maquetas, no capturas reales de Compose. Los diseños representan selección de CT, IP base, siete equipos (Router +1, CC T1 +2, CC T2 +3, SABT T2 +9, SABT T1 +11, FFAA1 +13, Remota +14), estados, ping, terminal MangoSSH, comandos, huella SSH y eventos. Diálogos completos, acciones reales y paneles adaptativos se implementarán después de elegir un concepto. La comprobación de huella requiere consentimiento explícito antes de conectar.

**No modificar main, MangoSSH, credenciales ni catálogos privados. No generar APK definitiva sin aprobación.**
