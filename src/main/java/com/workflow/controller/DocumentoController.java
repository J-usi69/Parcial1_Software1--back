package com.workflow.controller;

import com.workflow.domain.model.Documento;
import com.workflow.dto.response.ApiResponse;
import com.workflow.service.DocumentoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Slf4j
@RestController
@RequestMapping("/api/v1/documentos")
@RequiredArgsConstructor
@Tag(name = "Documentos (DMS)", description = "Endpoints para la Gestión Documental Avanzada (DMS), control de versiones y colaboración.")
public class DocumentoController {

    private final DocumentoService documentoService;

    @GetMapping("/solicitud/{solicitudId}")
    @Operation(summary = "Listar por solicitud", description = "Obtiene los documentos vinculados al expediente de una solicitud.")
    public ResponseEntity<ApiResponse<List<Documento>>> listarPorSolicitud(@PathVariable String solicitudId) {
        log.info("GET /api/v1/documentos/solicitud/{}", solicitudId);
        List<Documento> docs = documentoService.listarPorSolicitud(solicitudId);
        return ResponseEntity.ok(ApiResponse.ok("Documentos de la solicitud obtenidos", docs));
    }

    @GetMapping("/tarea/{tareaId}")
    @Operation(summary = "Listar por tarea BPMN", description = "Obtiene los documentos vinculados exclusivamente a una etapa/tarea de workflow.")
    public ResponseEntity<ApiResponse<List<Documento>>> listarPorTarea(@PathVariable String tareaId) {
        log.info("GET /api/v1/documentos/tarea/{}", tareaId);
        List<Documento> docs = documentoService.listarPorTarea(tareaId);
        return ResponseEntity.ok(ApiResponse.ok("Documentos de la tarea obtenidos", docs));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener detalles del documento", description = "Obtiene metadatos y el historial completo de versiones de un documento.")
    public ResponseEntity<ApiResponse<Documento>> obtenerPorId(@PathVariable String id) {
        log.info("GET /api/v1/documentos/{}", id);
        Documento doc = documentoService.obtenerPorId(id);
        return ResponseEntity.ok(ApiResponse.ok("Detalles del documento encontrados", doc));
    }

    @PostMapping(value = "/solicitud/{solicitudId}/archivo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Crear documento cargando archivo", description = "Sube un archivo inicial y crea un registro de documento gestionado.")
    public ResponseEntity<ApiResponse<Documento>> crearDocumentoArchivo(
            @PathVariable String solicitudId,
            @RequestParam("nombre") String nombre,
            @RequestParam("descripcion") String descripcion,
            @RequestParam("archivo") MultipartFile archivo,
            @RequestHeader("X-Usuario") String usuario) {
        log.info("POST /api/v1/documentos/solicitud/{}/archivo - Archivo: '{}' por {}", solicitudId, nombre, usuario);
        Documento doc = documentoService.crearDocumentoArchivo(solicitudId, nombre, descripcion, archivo, usuario);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Documento de archivo cargado exitosamente", doc));
    }

    @PostMapping("/solicitud/{solicitudId}/colaborativo")
    @Operation(summary = "Crear documento en línea colaborativo", description = "Crea una hoja o documento de texto en blanco para edición colaborativa en tiempo real.")
    public ResponseEntity<ApiResponse<Documento>> crearDocumentoColaborativo(
            @PathVariable String solicitudId,
            @RequestParam("nombre") String nombre,
            @RequestParam("descripcion") String descripcion,
            @RequestParam(value = "contenido", defaultValue = "") String contenido,
            @RequestHeader("X-Usuario") String usuario) {
        log.info("POST /api/v1/documentos/solicitud/{}/colaborativo - Colaborativo: '{}' por {}", solicitudId, nombre, usuario);
        Documento doc = documentoService.crearDocumentoColaborativo(solicitudId, nombre, descripcion, contenido, usuario);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Documento colaborativo inicializado", doc));
    }

    @PostMapping(value = "/{id}/version", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Cargar nueva versión del archivo", description = "Agrega una nueva versión a un documento existente de tipo FILE.")
    public ResponseEntity<ApiResponse<Documento>> subirNuevaVersion(
            @PathVariable String id,
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam("comentario") String comentario,
            @RequestHeader("X-Usuario") String usuario) {
        log.info("POST /api/v1/documentos/{}/version - Nueva versión por {}", id, usuario);
        Documento doc = documentoService.subirNuevaVersionArchivo(id, archivo, comentario, usuario);
        return ResponseEntity.ok(ApiResponse.ok("Nueva versión del archivo subida exitosamente", doc));
    }

    @PostMapping("/{id}/snapshot")
    @Operation(summary = "Guardar snapshot de versión colaborativa", description = "Crea un snapshot inmutable en el historial de versiones para un documento colaborativo.")
    public ResponseEntity<ApiResponse<Documento>> guardarSnapshot(
            @PathVariable String id,
            @RequestParam("comentario") String comentario,
            @RequestHeader("X-Usuario") String usuario) {
        log.info("POST /api/v1/documentos/{}/snapshot - Snapshot por {}", id, usuario);
        Documento doc = documentoService.guardarSnapshotVersionColaborativo(id, comentario, usuario);
        return ResponseEntity.ok(ApiResponse.ok("Snapshot de versión guardado exitosamente", doc));
    }

    @PutMapping("/{id}/contenido")
    @Operation(summary = "Actualizar contenido colaborativo en línea", description = "Guarda las pulsaciones/cambios rápidos del editor en el contenido colaborativo activo.")
    public ResponseEntity<ApiResponse<Documento>> actualizarContenido(
            @PathVariable String id,
            @RequestBody String contenido,
            @RequestHeader("X-Usuario") String usuario) {
        Documento doc = documentoService.actualizarContenidoColaborativo(id, contenido, usuario);
        return ResponseEntity.ok(ApiResponse.ok("Contenido colaborativo guardado", doc));
    }

    @PatchMapping("/{id}/bloquear")
    @Operation(summary = "Bloquear documento para edición exclusiva", description = "Activa el cerrojo de edición exclusiva colaborativa para evitar colisiones.")
    public ResponseEntity<ApiResponse<Documento>> bloquearDocumento(
            @PathVariable String id,
            @RequestHeader("X-Usuario") String usuario) {
        log.info("PATCH /api/v1/documentos/{}/bloquear - Por {}", id, usuario);
        Documento doc = documentoService.bloquearDocumento(id, usuario);
        return ResponseEntity.ok(ApiResponse.ok("Documento bloqueado para tu edición", doc));
    }

    @PatchMapping("/{id}/desbloquear")
    @Operation(summary = "Desbloquear documento", description = "Libera el cerrojo de edición exclusiva del documento.")
    public ResponseEntity<ApiResponse<Documento>> desbloquearDocumento(
            @PathVariable String id,
            @RequestHeader("X-Usuario") String usuario) {
        log.info("PATCH /api/v1/documentos/{}/desbloquear - Por {}", id, usuario);
        Documento doc = documentoService.desbloquearDocumento(id, usuario);
        return ResponseEntity.ok(ApiResponse.ok("Documento liberado exitosamente", doc));
    }

    @PatchMapping("/{id}/asociar")
    @Operation(summary = "Asociar documento a una tarea/solicitud", description = "Vincula el documento al ID de la solicitud física o de la etapa BPMN.")
    public ResponseEntity<ApiResponse<Documento>> asociarDocumento(
            @PathVariable String id,
            @RequestParam(value = "solicitudId", required = false) String solicitudId,
            @RequestParam(value = "tareaId", required = false) String tareaId,
            @RequestHeader("X-Usuario") String usuario) {
        log.info("PATCH /api/v1/documentos/{}/asociar?solicitudId={}&tareaId={} - Por {}", id, solicitudId, tareaId, usuario);
        Documento doc = documentoService.asociarASolicitudYTarea(id, solicitudId, tareaId, usuario);
        return ResponseEntity.ok(ApiResponse.ok("Documento asociado correctamente", doc));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar documento del gestor", description = "Remueve un documento completamente del sistema.")
    public ResponseEntity<ApiResponse<Void>> eliminarDocumento(
            @PathVariable String id,
            @RequestHeader("X-Usuario") String usuario) {
        log.info("DELETE /api/v1/documentos/{} - Por {}", id, usuario);
        documentoService.eliminarDocumento(id, usuario);
        return ResponseEntity.ok(ApiResponse.ok("Documento eliminado exitosamente", null));
    }

    @GetMapping("/buscar")
    @Operation(summary = "Búsqueda global de documentos", description = "Busca documentos por coincidencia de nombre en todo el software.")
    public ResponseEntity<ApiResponse<List<Documento>>> buscarDocumentos(@RequestParam("query") String query) {
        log.info("GET /api/v1/documentos/buscar?query={}", query);
        List<Documento> docs = documentoService.buscarDocumentos(query);
        return ResponseEntity.ok(ApiResponse.ok("Resultados de búsqueda de documentos", docs));
    }

    @GetMapping
    @Operation(summary = "Listar todos los documentos", description = "Lista absolutamente todos los documentos en el gestor documental.")
    public ResponseEntity<ApiResponse<List<Documento>>> listarTodos() {
        log.info("GET /api/v1/documentos");
        List<Documento> docs = documentoService.listarTodos();
        return ResponseEntity.ok(ApiResponse.ok("Todos los documentos obtenidos", docs));
    }
}
