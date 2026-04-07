package com.workflow.domain.enums;

/**
 * Estados del ciclo de vida de una solicitud de workflow.
 * Transiciones válidas:
 *   PENDIENTE -> EN_REVISION (Revisor)
 *   EN_REVISION -> APROBADO | RECHAZADO (Revisor)
 *   Cualquier estado -> Cualquier estado (Administrador, forzado)
 */
public enum EstadoWorkflow {
    PENDIENTE,
    EN_REVISION,
    APROBADO,
    RECHAZADO;

    /**
     * Valida si la transición de estado es permitida para un Revisor.
     * Los Administradores pueden forzar cualquier transición.
     */
    public boolean puedeTransicionarA(EstadoWorkflow nuevoEstado) {
        return switch (this) {
            case PENDIENTE -> nuevoEstado == EN_REVISION;
            case EN_REVISION -> nuevoEstado == APROBADO || nuevoEstado == RECHAZADO;
            case APROBADO, RECHAZADO -> false; // Estados terminales para revisores
        };
    }

    public boolean esEstadoTerminal() {
        return this == APROBADO || this == RECHAZADO;
    }
}
