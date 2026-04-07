package com.workflow.service.impl;

import com.workflow.domain.enums.EstadoWorkflow;
import com.workflow.domain.enums.RolUsuario;
import com.workflow.domain.model.SolicitudWorkflow;
import com.workflow.dto.request.CambiarEstadoRequest;
import com.workflow.dto.request.CrearSolicitudRequest;
import com.workflow.dto.request.ReasignarDepartamentoRequest;
import com.workflow.dto.response.SolicitudResponse;
import com.workflow.exception.InvalidStateTransitionException;
import com.workflow.exception.ResourceNotFoundException;
import com.workflow.exception.UnauthorizedActionException;
import com.workflow.mapper.SolicitudMapper;
import com.workflow.repository.SolicitudWorkflowRepository;
import com.workflow.service.CodigoSeguimientoGenerator;
import com.workflow.service.WorkflowService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementación del servicio de workflow departamental.
 *
 * Contiene toda la lógica de negocio incluyendo:
 * - Validación de permisos por rol
 * - Validación de transiciones de estado (máquina de estados)
 * - Validación de pertenencia a departamento para revisores
 * - Registro atómico del historial de eventos
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private final SolicitudWorkflowRepository repository;
    private final SolicitudMapper mapper;
    private final CodigoSeguimientoGenerator codigoGenerator;

    /**
     * Inicializa el generador de códigos con el último secuencial de la BD.
     */
    @PostConstruct
    void inicializarGenerador() {
        String prefijo = String.format("WF-%d-", Year.now().getValue());
        repository.findAll(Sort.by(Sort.Direction.DESC, "codigoSeguimiento"))
                .stream()
                .filter(s -> s.getCodigoSeguimiento() != null && s.getCodigoSeguimiento().startsWith(prefijo))
                .findFirst()
                .ifPresent(ultima -> {
                    try {
                        String[] partes = ultima.getCodigoSeguimiento().split("-");
                        long ultimoNumero = Long.parseLong(partes[2]);
                        codigoGenerator.inicializarContador(ultimoNumero);
                        log.info("Generador de códigos inicializado en: {}", ultimoNumero);
                    } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                        log.warn("No se pudo parsear el último código de seguimiento, iniciando desde 0");
                    }
                });
    }

    // ═══════════════════════════════════════════════════════════════
    //  CREAR SOLICITUD
    // ═══════════════════════════════════════════════════════════════

    @Override
    public SolicitudResponse crearSolicitud(CrearSolicitudRequest request) {
        // Validar que solo SOLICITANTE o ADMINISTRADOR pueden crear
        if (!request.getRolUsuario().puedeCrearSolicitud()) {
            throw new UnauthorizedActionException(
                    request.getRolUsuario().name(),
                    "crear solicitudes de workflow"
            );
        }

        String codigo = codigoGenerator.generarCodigo();
        SolicitudWorkflow solicitud = mapper.toEntity(request, codigo);

        // Registrar evento de creación en el historial
        solicitud.registrarTransicion(
                null,
                EstadoWorkflow.PENDIENTE,
                request.getUsuarioCreador(),
                request.getRolUsuario().name(),
                "Solicitud creada"
        );

        SolicitudWorkflow guardada = repository.save(solicitud);
        log.info("Solicitud creada: {} por usuario: {}", codigo, request.getUsuarioCreador());

        return mapper.toResponse(guardada);
    }

    // ═══════════════════════════════════════════════════════════════
    //  CONSULTAS
    // ═══════════════════════════════════════════════════════════════

    @Override
    public SolicitudResponse obtenerPorId(String id) {
        SolicitudWorkflow solicitud = buscarSolicitudPorId(id);
        return mapper.toResponse(solicitud);
    }

    @Override
    public SolicitudResponse obtenerPorCodigoSeguimiento(String codigoSeguimiento) {
        SolicitudWorkflow solicitud = repository.findByCodigoSeguimiento(codigoSeguimiento)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Solicitud", "codigoSeguimiento", codigoSeguimiento
                ));
        return mapper.toResponse(solicitud);
    }

    @Override
    public List<SolicitudResponse> listarPorDepartamento(String departamento) {
        List<SolicitudWorkflow> solicitudes =
                repository.findByDepartamentoActualIgnoreCaseOrderByFechaCreacionDesc(departamento);
        return mapper.toResponseList(solicitudes);
    }

    @Override
    public List<SolicitudResponse> listarPorDepartamentoYEstado(String departamento, EstadoWorkflow estado) {
        List<SolicitudWorkflow> solicitudes =
                repository.findByDepartamentoActualIgnoreCaseAndEstado(departamento, estado);
        return mapper.toResponseList(solicitudes);
    }

    @Override
    public List<SolicitudResponse> listarPorUsuarioCreador(String usuario) {
        List<SolicitudWorkflow> solicitudes =
                repository.findByUsuarioCreadorOrderByFechaCreacionDesc(usuario);
        return mapper.toResponseList(solicitudes);
    }

    @Override
    public List<SolicitudResponse> listarTodas() {
        List<SolicitudWorkflow> solicitudes =
                repository.findAll(Sort.by(Sort.Direction.DESC, "fechaCreacion"));
        return mapper.toResponseList(solicitudes);
    }

    @Override
    public List<SolicitudResponse> buscarPorTitulo(String titulo) {
        List<SolicitudWorkflow> solicitudes = repository.buscarPorTitulo(titulo);
        return mapper.toResponseList(solicitudes);
    }

    // ═══════════════════════════════════════════════════════════════
    //  CAMBIO DE ESTADO (Lógica principal del workflow)
    // ═══════════════════════════════════════════════════════════════

    @Override
    public SolicitudResponse cambiarEstado(String id, CambiarEstadoRequest request) {
        SolicitudWorkflow solicitud = buscarSolicitudPorId(id);
        RolUsuario rol = request.getRolUsuario();
        EstadoWorkflow estadoActual = solicitud.getEstado();
        EstadoWorkflow nuevoEstado = request.getNuevoEstado();

        // ADMINISTRADOR puede forzar cualquier transición
        if (rol.puedeForzarCambioEstado()) {
            log.info("ADMIN forzando transición: {} -> {} en solicitud {}",
                    estadoActual, nuevoEstado, id);
        } else if (rol == RolUsuario.REVISOR) {
            // Validar que el REVISOR pertenece al departamento de la solicitud
            validarDepartamentoRevisor(solicitud, request.getDepartamentoUsuario());

            // Validar la transición de estado según la máquina de estados
            if (!estadoActual.puedeTransicionarA(nuevoEstado)) {
                throw new InvalidStateTransitionException(
                        estadoActual.name(), nuevoEstado.name()
                );
            }
        } else {
            // SOLICITANTE no puede cambiar estado
            throw new UnauthorizedActionException(
                    rol.name(),
                    "cambiar el estado de solicitudes"
            );
        }

        // Registrar la transición atómicamente
        solicitud.registrarTransicion(
                estadoActual,
                nuevoEstado,
                request.getUsuarioResponsable(),
                rol.name(),
                request.getComentario()
        );

        SolicitudWorkflow actualizada = repository.save(solicitud);
        log.info("Estado cambiado: {} -> {} en solicitud {} por {}",
                estadoActual, nuevoEstado, id, request.getUsuarioResponsable());

        return mapper.toResponse(actualizada);
    }

    // ═══════════════════════════════════════════════════════════════
    //  REASIGNAR DEPARTAMENTO (Solo ADMINISTRADOR)
    // ═══════════════════════════════════════════════════════════════

    @Override
    public SolicitudResponse reasignarDepartamento(String id, ReasignarDepartamentoRequest request) {
        if (!request.getRolUsuario().puedeAdministrar()) {
            throw new UnauthorizedActionException(
                    request.getRolUsuario().name(),
                    "reasignar departamentos"
            );
        }

        SolicitudWorkflow solicitud = buscarSolicitudPorId(id);
        String departamentoAnterior = solicitud.getDepartamentoActual();

        solicitud.setDepartamentoActual(request.getNuevoDepartamento());

        // Registrar la reasignación en el historial
        solicitud.registrarTransicion(
                solicitud.getEstado(),
                solicitud.getEstado(), // El estado no cambia, solo el departamento
                request.getUsuarioResponsable(),
                request.getRolUsuario().name(),
                String.format("Reasignado de '%s' a '%s'. %s",
                        departamentoAnterior,
                        request.getNuevoDepartamento(),
                        request.getComentario() != null ? request.getComentario() : "")
        );

        SolicitudWorkflow actualizada = repository.save(solicitud);
        log.info("Solicitud {} reasignada de '{}' a '{}' por {}",
                id, departamentoAnterior, request.getNuevoDepartamento(),
                request.getUsuarioResponsable());

        return mapper.toResponse(actualizada);
    }

    // ═══════════════════════════════════════════════════════════════
    //  ASIGNAR USUARIO
    // ═══════════════════════════════════════════════════════════════

    @Override
    public SolicitudResponse asignarUsuario(String id, String usuarioAsignado,
                                             String usuarioResponsable, RolUsuario rol) {
        if (!rol.puedeRevisar()) {
            throw new UnauthorizedActionException(rol.name(), "asignar usuarios a solicitudes");
        }

        SolicitudWorkflow solicitud = buscarSolicitudPorId(id);
        solicitud.setUsuarioAsignado(usuarioAsignado);

        SolicitudWorkflow actualizada = repository.save(solicitud);
        log.info("Usuario '{}' asignado a solicitud {} por {}", usuarioAsignado, id, usuarioResponsable);

        return mapper.toResponse(actualizada);
    }

    // ═══════════════════════════════════════════════════════════════
    //  ESTADÍSTICAS / KPIs
    // ═══════════════════════════════════════════════════════════════

    @Override
    public Map<String, Object> obtenerEstadisticas() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalSolicitudes", repository.count());

        Map<String, Long> porEstado = new HashMap<>();
        for (EstadoWorkflow estado : EstadoWorkflow.values()) {
            porEstado.put(estado.name(), repository.countByEstado(estado));
        }
        stats.put("porEstado", porEstado);

        return stats;
    }

    // ═══════════════════════════════════════════════════════════════
    //  MÉTODOS PRIVADOS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Busca una solicitud por ID o lanza ResourceNotFoundException.
     */
    private SolicitudWorkflow buscarSolicitudPorId(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud", "id", id));
    }

    /**
     * Valida que el revisor pertenezca al departamento de la solicitud.
     */
    private void validarDepartamentoRevisor(SolicitudWorkflow solicitud, String departamentoUsuario) {
        if (!solicitud.getDepartamentoActual().equalsIgnoreCase(departamentoUsuario)) {
            throw new UnauthorizedActionException(
                    String.format("El revisor del departamento '%s' no puede gestionar solicitudes del departamento '%s'",
                            departamentoUsuario, solicitud.getDepartamentoActual())
            );
        }
    }
}
