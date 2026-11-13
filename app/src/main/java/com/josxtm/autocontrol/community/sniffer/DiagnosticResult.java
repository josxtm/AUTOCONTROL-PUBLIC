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
            case SSH_ERROR: return "Error SSH / comando remoto";
            case LOCAL_ERROR: return "Error local";
            case INVALID_TARGET: return "Destino no válido";
            case NOT_CONFIGURED: return "No configurado";
            default: return "Cancelado";
        }
    }
    static DiagnosticStatus exitStatus(boolean ping, int code, boolean stderr) {
        if (code == 0) return DiagnosticStatus.VALID_RESPONSE;
        if (ping && code == 1 && !stderr) return DiagnosticStatus.NO_RESPONSE;
        return DiagnosticStatus.SSH_ERROR;
    }
}
