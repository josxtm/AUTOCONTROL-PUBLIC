package com.josxtm.autocontrol.community.sniffer;

import java.util.Objects;

/** Metadata only. Command output and credentials never belong in a diagnostic record. */
public final class DiagnosticResult {
    public final String targetName, address;
    public final DiagnosticSource source;
    public final DiagnosticStatus status;
    public final long elapsedMillis;
    public final Integer exitCode;
    public DiagnosticResult(String name, String address, DiagnosticSource source,
                            DiagnosticStatus status, long elapsedMillis, Integer exitCode) {
        this.targetName = Objects.requireNonNull(name);
        this.address = address;
        this.source = Objects.requireNonNull(source);
        this.status = Objects.requireNonNull(status);
        this.elapsedMillis = Math.max(0, elapsedMillis);
        this.exitCode = exitCode;
    }
    public DiagnosticResult forTarget(String name, String address) {
        return new DiagnosticResult(name, address, source, status, elapsedMillis, exitCode);
    }
    public String label() {
        switch (status) {
            case VALID_RESPONSE: return "Respuesta válida";
            case NO_RESPONSE: return "Sin respuesta observada";
            case TIMEOUT: return "Tiempo total agotado";
            case SSH_ERROR: return "Error de conexión o canal SSH";
            case COMMAND_NOT_FOUND: return "Comando no disponible en el Mango";
            case COMMAND_ERROR: return "El comando terminó con error";
            case UNKNOWN_EXIT_STATUS: return "Finalización SSH sin código de salida";
            case UNKNOWN: return "Sin comprobar";
            case OUTPUT_LIMIT: return "Salida de comando truncada por límite";
            case LOCAL_ERROR: return "Error local";
            case INVALID_TARGET: return "Destino no válido";
            case NOT_CONFIGURED: return "No instalado";
            default: return "Cancelado";
        }
    }
    static DiagnosticStatus exitStatus(boolean ping, int code, boolean stderr) {
        if (code == 0) return DiagnosticStatus.VALID_RESPONSE;
        if (code < 0) return DiagnosticStatus.UNKNOWN_EXIT_STATUS;
        if (code == 127) return DiagnosticStatus.COMMAND_NOT_FOUND;
        if (ping && code == 1) return DiagnosticStatus.NO_RESPONSE;
        return DiagnosticStatus.COMMAND_ERROR;
    }
}
