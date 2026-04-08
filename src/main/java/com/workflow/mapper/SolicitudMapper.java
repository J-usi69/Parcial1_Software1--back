package com.workflow.mapper;

import com.workflow.domain.model.EventoHistorial;
import com.workflow.domain.model.SolicitudWorkflow;
import com.workflow.dto.request.CrearSolicitudRequest;
import com.workflow.dto.response.EventoHistorialResponse;
import com.workflow.dto.response.SolicitudResponse;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper manual para convertir entre entidades de dominio y DTOs.
 * Evita dependencias en frameworks de mapping (MapStruct) para mayor control.
 */
@Component
public class SolicitudMapper {

    /**
     * Convierte una entidad SolicitudWorkflow a su DTO de respuesta.
     */
    public SolicitudResponse toResponse(SolicitudWorkflow solicitud) {
        if (solicitud == null) return null;

        List<EventoHistorialResponse> historialResponse = solicitud.getHistorial() != null
                ? solicitud.getHistorial().stream()
                    .map(this::toEventoResponse)
                    .collect(Collectors.toList())
                : Collections.emptyList();

        return SolicitudResponse.builder()
                .id(solicitud.getId())
                .codigoSeguimiento(solicitud.getCodigoSeguimiento())
                .titulo(solicitud.getTitulo())
                .descripcion(solicitud.getDescripcion())
                .prioridad(solicitud.getPrioridad())
                .estado(solicitud.getEstado())
                .departamentoActual(solicitud.getDepartamentoActual())
                .usuarioCreador(solicitud.getUsuarioCreador())
                .usuarioAsignado(solicitud.getUsuarioAsignado())
                .historial(historialResponse)
                .fechaCreacion(solicitud.getFechaCreacion())
                .fechaActualizacion(solicitud.getFechaActualizacion())
                .totalEventos(historialResponse.size())
                .build();
    }

    /**
     * Convierte una lista de entidades a lista de DTOs de respuesta.
     */
    public List<SolicitudResponse> toResponseList(List<SolicitudWorkflow> solicitudes) {
        if (solicitudes == null) return Collections.emptyList();
        return solicitudes.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Convierte un EventoHistorial a su DTO de respuesta.
     */
    public EventoHistorialResponse toEventoResponse(EventoHistorial evento) {
        if (evento == null) return null;

        return EventoHistorialResponse.builder()
                .fecha(evento.getFecha())
                .estadoAnterior(evento.getEstadoAnterior())
                .estadoNuevo(evento.getEstadoNuevo())
                .usuarioResponsable(evento.getUsuarioResponsable())
                .rolUsuario(evento.getRolUsuario())
                .comentario(evento.getComentario())
                .build();
    }

    /**
     * Convierte un DTO de creación al modelo de dominio.
     */
    public SolicitudWorkflow toEntity(CrearSolicitudRequest request, String codigoSeguimiento, String usuarioCreador) {
        return SolicitudWorkflow.builder()
                .codigoSeguimiento(codigoSeguimiento)
                .titulo(request.getTitulo())
                .descripcion(request.getDescripcion())
                .prioridad(request.getPrioridad())
                .departamentoActual(request.getDepartamentoDestino())
                .usuarioCreador(usuarioCreador)
                .build();
    }
}
