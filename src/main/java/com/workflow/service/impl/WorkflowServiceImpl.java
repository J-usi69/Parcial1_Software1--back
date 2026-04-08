package com.workflow.service.impl;

import com.workflow.domain.enums.EstadoSla;
import com.workflow.domain.enums.EstadoWorkflow;
import com.workflow.domain.enums.Prioridad;
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
import com.workflow.repository.UsuarioRepository;
import com.workflow.service.CodigoSeguimientoGenerator;
import com.workflow.service.WorkflowService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.EnumMap;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    private static final List<String> DEPARTAMENTOS_VALIDOS = List.of(
            "Sistemas",
            "Ventas",
            "Recursos Humanos"
    );

    private static final Map<Prioridad, Long> SLA_HORAS_POR_PRIORIDAD = Map.of(
            Prioridad.URGENTE, 4L,
            Prioridad.ALTA, 8L,
            Prioridad.MEDIA, 24L,
            Prioridad.BAJA, 72L
    );

    private final SolicitudWorkflowRepository repository;
    private final UsuarioRepository usuarioRepository;
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
    public SolicitudResponse crearSolicitud(CrearSolicitudRequest request, String usuarioCreador, RolUsuario rolUsuario) {
        // Validar que solo SOLICITANTE o ADMINISTRADOR pueden crear
        if (!rolUsuario.puedeCrearSolicitud()) {
            throw new UnauthorizedActionException(
                    rolUsuario.name(),
                    "crear solicitudes de workflow"
            );
        }

        String departamentoNormalizado = normalizarDepartamento(request.getDepartamentoDestino());
        request.setDepartamentoDestino(departamentoNormalizado);

        String codigo = codigoGenerator.generarCodigo();
        SolicitudWorkflow solicitud = mapper.toEntity(request, codigo, usuarioCreador);

        Prioridad prioridadSla = solicitud.getPrioridad() != null ? solicitud.getPrioridad() : Prioridad.MEDIA;
        solicitud.setPrioridad(prioridadSla);
        solicitud.setFechaLimiteAtencion(calcularFechaLimite(prioridadSla, LocalDateTime.now()));

        // Registrar evento de creación en el historial
        solicitud.registrarTransicion(
                null,
                EstadoWorkflow.PENDIENTE,
                usuarioCreador,
                rolUsuario.name(),
                "Solicitud creada"
        );

        SolicitudWorkflow guardada = repository.save(solicitud);
        log.info("Solicitud creada: {} por usuario: {}", codigo, usuarioCreador);

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
    public SolicitudResponse cambiarEstado(String id, CambiarEstadoRequest request, String usuarioResponsable, RolUsuario rolUsuario, String departamentoUsuario) {
        SolicitudWorkflow solicitud = buscarSolicitudPorId(id);
        RolUsuario rol = rolUsuario;
        EstadoWorkflow estadoActual = solicitud.getEstado();
        EstadoWorkflow nuevoEstado = request.getNuevoEstado();

        // ADMINISTRADOR puede forzar cualquier transición
        if (rol.puedeForzarCambioEstado()) {
            log.info("ADMIN forzando transición: {} -> {} en solicitud {}",
                    estadoActual, nuevoEstado, id);
        } else if (rol == RolUsuario.REVISOR) {
            // Validar que el REVISOR pertenece al departamento de la solicitud
            validarDepartamentoRevisor(solicitud, departamentoUsuario);

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
                usuarioResponsable,
                rol.name(),
                request.getComentario()
        );

        SolicitudWorkflow actualizada = repository.save(solicitud);
        log.info("Estado cambiado: {} -> {} en solicitud {} por {}",
                estadoActual, nuevoEstado, id, usuarioResponsable);

        return mapper.toResponse(actualizada);
    }

    // ═══════════════════════════════════════════════════════════════
    //  REASIGNAR DEPARTAMENTO (Solo ADMINISTRADOR)
    // ═══════════════════════════════════════════════════════════════

    @Override
    public SolicitudResponse reasignarDepartamento(
            String id,
            ReasignarDepartamentoRequest request,
            String usuarioResponsable,
            RolUsuario rolUsuario,
            String departamentoUsuario
    ) {
        SolicitudWorkflow solicitud = buscarSolicitudPorId(id);

        if (rolUsuario.puedeAdministrar()) {
            log.info("ADMIN reasignando departamento en solicitud {}", id);
        } else if (rolUsuario == RolUsuario.REVISOR) {
            validarDepartamentoRevisor(solicitud, departamentoUsuario);
            log.info("REVISOR '{}' reasignando solicitud {}", usuarioResponsable, id);
        } else {
            throw new UnauthorizedActionException(
                    rolUsuario.name(),
                    "reasignar departamentos"
            );
        }

        String departamentoAnterior = solicitud.getDepartamentoActual();
        String departamentoNormalizado = normalizarDepartamento(request.getNuevoDepartamento());

        if (departamentoAnterior != null && departamentoAnterior.equalsIgnoreCase(departamentoNormalizado)) {
            throw new IllegalArgumentException("La solicitud ya está en el departamento indicado");
        }

        solicitud.setDepartamentoActual(departamentoNormalizado);

        // Registrar la reasignación en el historial
        solicitud.registrarTransicion(
                solicitud.getEstado(),
                solicitud.getEstado(), // El estado no cambia, solo el departamento
                usuarioResponsable,
                rolUsuario.name(),
                String.format("Reasignado de '%s' a '%s'. %s",
                        departamentoAnterior,
                departamentoNormalizado,
                        request.getComentario() != null ? request.getComentario() : "")
        );

        SolicitudWorkflow actualizada = repository.save(solicitud);
        log.info("Solicitud {} reasignada de '{}' a '{}' por {}",
            id, departamentoAnterior, departamentoNormalizado,
                usuarioResponsable);

        return mapper.toResponse(actualizada);
    }

    @Override
    public List<String> obtenerCatalogoDepartamentos() {
        return DEPARTAMENTOS_VALIDOS;
    }

    @Override
    public Map<String, Object> obtenerRecomendacionReasignacion(String id) {
        SolicitudWorkflow solicitud = buscarSolicitudPorId(id);
        String departamentoActual = solicitud.getDepartamentoActual();

        Map<String, Long> colaPendiente = new LinkedHashMap<>();
        for (String departamento : DEPARTAMENTOS_VALIDOS) {
            long cantidad = repository.countByDepartamentoActualIgnoreCaseAndEstado(
                    departamento,
                    EstadoWorkflow.PENDIENTE
            );
            colaPendiente.put(departamento, cantidad);
        }

        String departamentoSugerido = DEPARTAMENTOS_VALIDOS.stream()
                .filter(dep -> departamentoActual == null || !dep.equalsIgnoreCase(departamentoActual))
                .min(Comparator.comparingLong(dep -> colaPendiente.getOrDefault(dep, 0L)))
                .orElse(null);

        Map<String, Object> recomendacion = new LinkedHashMap<>();
        recomendacion.put("departamentoActual", departamentoActual);
        recomendacion.put("departamentoSugerido", departamentoSugerido);
        recomendacion.put("colaPendiente", colaPendiente);
        recomendacion.put("departamentosDisponibles", DEPARTAMENTOS_VALIDOS);

        return recomendacion;
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

        if (!usuarioRepository.existsByUsername(usuarioAsignado)) {
            throw new ResourceNotFoundException("Usuario", "username", usuarioAsignado);
        }

        SolicitudWorkflow solicitud = buscarSolicitudPorId(id);
        String usuarioAnterior = solicitud.getUsuarioAsignado();
        solicitud.setUsuarioAsignado(usuarioAsignado);

        String comentario = String.format(
                "Asignación de responsable: '%s' -> '%s'",
                usuarioAnterior != null ? usuarioAnterior : "sin asignar",
                usuarioAsignado
        );

        solicitud.registrarTransicion(
                solicitud.getEstado(),
                solicitud.getEstado(),
                usuarioResponsable,
                rol.name(),
                comentario
        );

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
        List<SolicitudWorkflow> solicitudes = repository.findAll();

        stats.put("totalSolicitudes", solicitudes.size());

        Map<String, Long> porEstado = new HashMap<>();
        for (EstadoWorkflow estado : EstadoWorkflow.values()) {
            porEstado.put(estado.name(), repository.countByEstado(estado));
        }
        stats.put("porEstado", porEstado);

        Map<String, Long> porSla = new LinkedHashMap<>();
        Map<EstadoSla, Long> conteoSla = new EnumMap<>(EstadoSla.class);
        for (EstadoSla estadoSla : EstadoSla.values()) {
            conteoSla.put(estadoSla, 0L);
        }

        LocalDateTime ahora = LocalDateTime.now();
        for (SolicitudWorkflow solicitud : solicitudes) {
            EstadoSla estadoSla = calcularEstadoSla(solicitud, ahora);
            conteoSla.put(estadoSla, conteoSla.get(estadoSla) + 1);
        }

        for (EstadoSla estadoSla : EstadoSla.values()) {
            porSla.put(estadoSla.name(), conteoSla.get(estadoSla));
        }
        stats.put("porSla", porSla);

        long totalAlertasSla = solicitudes.stream()
            .filter(s -> s.getFechaPrimeraAlertaSla() != null)
            .count();
        long totalEscaladasSla = solicitudes.stream()
            .filter(s -> s.getFechaEscalamientoSla() != null)
            .count();

        stats.put("totalAlertasSla", totalAlertasSla);
        stats.put("totalEscaladasSla", totalEscaladasSla);

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

    /**
     * Normaliza y valida el departamento contra el catálogo fijo de la aplicación.
     */
    private String normalizarDepartamento(String departamento) {
        if (departamento == null || departamento.isBlank()) {
            throw new IllegalArgumentException("El departamento es obligatorio");
        }

        String valor = departamento.trim();

        return DEPARTAMENTOS_VALIDOS.stream()
                .filter(dep -> dep.equalsIgnoreCase(valor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format(
                                "Departamento '%s' no válido. Valores permitidos: %s",
                                departamento,
                                DEPARTAMENTOS_VALIDOS.stream().collect(Collectors.joining(", "))
                        )
                ));
    }

    private LocalDateTime calcularFechaLimite(Prioridad prioridad, LocalDateTime base) {
        Prioridad prioridadEfectiva = prioridad != null ? prioridad : Prioridad.MEDIA;
        Long horasSla = SLA_HORAS_POR_PRIORIDAD.getOrDefault(prioridadEfectiva, 24L);
        return base.plusHours(horasSla);
    }

    private EstadoSla calcularEstadoSla(SolicitudWorkflow solicitud, LocalDateTime referencia) {
        EstadoWorkflow estado = solicitud.getEstado();
        if (estado == EstadoWorkflow.APROBADO || estado == EstadoWorkflow.RECHAZADO) {
            return EstadoSla.CERRADO;
        }

        LocalDateTime fechaLimite = solicitud.getFechaLimiteAtencion();
        if (fechaLimite == null) {
            return EstadoSla.EN_TIEMPO;
        }

        long minutosRestantes = Duration.between(referencia, fechaLimite).toMinutes();
        if (minutosRestantes <= 0) {
            return EstadoSla.VENCIDO;
        }

        if (minutosRestantes <= 240) {
            return EstadoSla.POR_VENCER;
        }

        return EstadoSla.EN_TIEMPO;
    }
}
