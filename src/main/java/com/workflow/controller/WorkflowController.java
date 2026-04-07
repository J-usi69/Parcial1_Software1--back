package com.workflow.controller;

import com.workflow.domain.enums.EstadoWorkflow;
import com.workflow.dto.request.CambiarEstadoRequest;
import com.workflow.dto.request.CrearSolicitudRequest;
import com.workflow.dto.request.ReasignarDepartamentoRequest;
import com.workflow.dto.response.ApiResponse;
import com.workflow.dto.response.SolicitudResponse;
import com.workflow.service.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controlador REST para el sistema de workflow departamental.
 *
 * Endpoints:
 * POST   /api/v1/workflows                            → Crear solicitud
 * GET    /api/v1/workflows                             → Listar todas (Admin)
 * GET    /api/v1/workflows/{id}                        → Obtener por ID
 * GET    /api/v1/workflows/seguimiento/{codigo}        → Obtener por código
 * GET    /api/v1/workflows/departamento/{nombre}       → Listar por departamento
 * GET    /api/v1/workflows/departamento/{nombre}/estado/{estado} → Filtrar por depto y estado
 * GET    /api/v1/workflows/usuario/{usuario}           → Listar por usuario creador
 * GET    /api/v1/workflows/buscar                      → Buscar por título
 * PATCH  /api/v1/workflows/{id}/estado                 → Cambiar estado
 * PATCH  /api/v1/workflows/{id}/departamento           → Reasignar departamento
 * GET    /api/v1/workflows/estadisticas                → KPIs
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
@Tag(name = "Workflow Departamental", description = "Endpoints para la gestión de solicitudes, asignaciones de departamentos y estados de Workflow.")
public class WorkflowController {

    private final WorkflowService workflowService;

    // ═══════════════════════════════════════════════════════════════
    //  CREAR
    // ═══════════════════════════════════════════════════════════════

    /**
     * Crea una nueva solicitud de workflow.
     * Actor: SOLICITANTE
     */
    @PostMapping
    @Operation(summary = "Crear nueva solicitud", description = "Permite a un SOLICITANTE iniciar un nuevo trámite/solicitud en un departamento específico.")
    public ResponseEntity<ApiResponse<SolicitudResponse>> crearSolicitud(
            @Valid @RequestBody CrearSolicitudRequest request) {

        log.info("POST /api/v1/workflows - Creando solicitud: '{}'", request.getTitulo());
        SolicitudResponse response = workflowService.crearSolicitud(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Solicitud creada exitosamente", response));
    }

    // ═══════════════════════════════════════════════════════════════
    //  CONSULTAS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Lista todas las solicitudes.
     * Actor: ADMINISTRADOR
     */
    @GetMapping
    @Operation(summary = "Listar todas las solicitudes", description = "Obtiene absolutamente todas las solicitudes. Ideal para vista global de Administradores.")
    public ResponseEntity<ApiResponse<List<SolicitudResponse>>> listarTodas() {
        log.info("GET /api/v1/workflows - Listando todas las solicitudes");
        List<SolicitudResponse> solicitudes = workflowService.listarTodas();

        return ResponseEntity.ok(
                ApiResponse.ok("Solicitudes obtenidas exitosamente", solicitudes)
        );
    }

    /**
     * Obtiene una solicitud por su ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Obtener por ID interno", description = "Recuperar los detalles de una solicitud específica basándose en su ID de MongoDB.")
    public ResponseEntity<ApiResponse<SolicitudResponse>> obtenerPorId(@PathVariable String id) {
        log.info("GET /api/v1/workflows/{} - Obteniendo solicitud", id);
        SolicitudResponse response = workflowService.obtenerPorId(id);

        return ResponseEntity.ok(ApiResponse.ok("Solicitud encontrada", response));
    }

    /**
     * Obtiene una solicitud por su código de seguimiento.
     */
    @GetMapping("/seguimiento/{codigo}")
    @Operation(summary = "Buscar solicitud por código", description = "Busca una solicitud usando su código de seguimiento generado aleatoriamente (ej. WF-12345).")
    public ResponseEntity<ApiResponse<SolicitudResponse>> obtenerPorCodigo(
            @PathVariable String codigo) {
        log.info("GET /api/v1/workflows/seguimiento/{} - Buscando por código", codigo);
        SolicitudResponse response = workflowService.obtenerPorCodigoSeguimiento(codigo);

        return ResponseEntity.ok(ApiResponse.ok("Solicitud encontrada", response));
    }

    /**
     * Lista solicitudes por departamento.
     * Actor: REVISOR (Jefe de Departamento)
     */
    @GetMapping("/departamento/{nombre}")
    @Operation(summary = "Listar por departamento", description = "Devuelve todas las solicitudes que actualmente residen en el flujo de un departamento.")
    public ResponseEntity<ApiResponse<List<SolicitudResponse>>> listarPorDepartamento(
            @PathVariable String nombre) {
        log.info("GET /api/v1/workflows/departamento/{} - Listando por departamento", nombre);
        List<SolicitudResponse> solicitudes = workflowService.listarPorDepartamento(nombre);

        return ResponseEntity.ok(
                ApiResponse.ok("Solicitudes del departamento obtenidas", solicitudes)
        );
    }

    /**
     * Lista solicitudes por departamento y estado.
     */
    @GetMapping("/departamento/{nombre}/estado/{estado}")
    @Operation(summary = "Filtrar por departamento y estado", description = "Combinación para encontrar, por ejemplo, solicitudes 'PENDIENTES' en el depto de 'Sistemas'.")
    public ResponseEntity<ApiResponse<List<SolicitudResponse>>> listarPorDepartamentoYEstado(
            @PathVariable String nombre,
            @PathVariable EstadoWorkflow estado) {

        log.info("GET /api/v1/workflows/departamento/{}/estado/{} - Filtrando", nombre, estado);
        List<SolicitudResponse> solicitudes =
                workflowService.listarPorDepartamentoYEstado(nombre, estado);

        return ResponseEntity.ok(
                ApiResponse.ok("Solicitudes filtradas por departamento y estado", solicitudes)
        );
    }

    /**
     * Lista solicitudes creadas por un usuario específico.
     */
    @GetMapping("/usuario/{usuario}")
    @Operation(summary = "Mis solicitudes", description = "Busca todas las solicitudes iniciadas por un identificador de usuario creador.")
    public ResponseEntity<ApiResponse<List<SolicitudResponse>>> listarPorUsuario(
            @PathVariable String usuario) {
        log.info("GET /api/v1/workflows/usuario/{} - Listando por usuario creador", usuario);
        List<SolicitudResponse> solicitudes = workflowService.listarPorUsuarioCreador(usuario);

        return ResponseEntity.ok(
                ApiResponse.ok("Solicitudes del usuario obtenidas", solicitudes)
        );
    }

    /**
     * Busca solicitudes por título (búsqueda parcial).
     */
    @GetMapping("/buscar")
    @Operation(summary = "Busqueda global parcial", description = "Busca y retorna las solicitudes basándose en coincidencias parciales del Título.")
    public ResponseEntity<ApiResponse<List<SolicitudResponse>>> buscarPorTitulo(
            @RequestParam String titulo) {
        log.info("GET /api/v1/workflows/buscar?titulo={}", titulo);
        List<SolicitudResponse> solicitudes = workflowService.buscarPorTitulo(titulo);

        return ResponseEntity.ok(
                ApiResponse.ok("Resultados de búsqueda", solicitudes)
        );
    }

    // ═══════════════════════════════════════════════════════════════
    //  ACCIONES DE WORKFLOW
    // ═══════════════════════════════════════════════════════════════

    /**
     * Cambia el estado de una solicitud de workflow.
     * El Service valida la máquina de estados y los permisos del rol.
     * Actor: REVISOR (transiciones normales) | ADMINISTRADOR (forzado)
     */
    @PatchMapping("/{id}/estado")
    @Operation(summary = "Actualizar estado / Aprobar / Rechazar", description = "Principal operación del Revisor. Permite mover la solicitud dentro del flujo válido.")
    public ResponseEntity<ApiResponse<SolicitudResponse>> cambiarEstado(
            @PathVariable String id,
            @Valid @RequestBody CambiarEstadoRequest request) {

        log.info("PATCH /api/v1/workflows/{}/estado - Cambiando a: {}", id, request.getNuevoEstado());
        SolicitudResponse response = workflowService.cambiarEstado(id, request);

        return ResponseEntity.ok(
                ApiResponse.ok("Estado actualizado exitosamente", response)
        );
    }

    /**
     * Reasigna una solicitud a otro departamento.
     * Actor: ADMINISTRADOR
     */
    @PatchMapping("/{id}/departamento")
    @Operation(summary = "Reasignar de departamento", description = "El administrador puede transferir una solicitud a otro departamento registrando esto en el historial.")
    public ResponseEntity<ApiResponse<SolicitudResponse>> reasignarDepartamento(
            @PathVariable String id,
            @Valid @RequestBody ReasignarDepartamentoRequest request) {

        log.info("PATCH /api/v1/workflows/{}/departamento - Reasignando a: {}",
                id, request.getNuevoDepartamento());
        SolicitudResponse response = workflowService.reasignarDepartamento(id, request);

        return ResponseEntity.ok(
                ApiResponse.ok("Departamento reasignado exitosamente", response)
        );
    }

    /**
     * Asigna un usuario revisor a una solicitud específica.
     * Actor: ADMINISTRADOR o Jefe de Departamento
     */
    @PatchMapping("/{id}/asignar")
    @Operation(summary = "Asignar Usuario a Solicitud", description = "Asigna directamente a la persona encargada de trabajar la solicitud.")
    public ResponseEntity<ApiResponse<SolicitudResponse>> asignarUsuario(
            @PathVariable String id,
            @Valid @RequestBody com.workflow.dto.request.AsignarUsuarioRequest request) {

        log.info("PATCH /api/v1/workflows/{}/asignar - Asignando a: {}", id, request.getUsuarioAsignado());
        SolicitudResponse response = workflowService.asignarUsuario(
                id, 
                request.getUsuarioAsignado(), 
                request.getUsuarioResponsable(), 
                request.getRolUsuario()
        );

        return ResponseEntity.ok(
                ApiResponse.ok("Usuario asignado exitosamente", response)
        );
    }

    // ═══════════════════════════════════════════════════════════════
    //  ESTADÍSTICAS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Obtiene estadísticas generales del sistema de workflow.
     */
    @GetMapping("/estadisticas")
    @Operation(summary = "Resumen y KPIs", description = "Muestra estadisticas sobre cuántas solicitudes hay por cada estado. Útil para Dashboard.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> obtenerEstadisticas() {
        log.info("GET /api/v1/workflows/estadisticas - Obteniendo KPIs");
        Map<String, Object> stats = workflowService.obtenerEstadisticas();

        return ResponseEntity.ok(ApiResponse.ok("Estadísticas del workflow", stats));
    }
}
