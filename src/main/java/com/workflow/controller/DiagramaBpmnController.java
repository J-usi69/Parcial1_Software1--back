package com.workflow.controller;

import com.workflow.domain.model.DiagramaBpmn;
import com.workflow.dto.request.ColaboracionBpmnRequest;
import com.workflow.dto.request.GuardarDiagramaBpmnRequest;
import com.workflow.dto.response.ApiResponse;
import com.workflow.dto.response.DiagramaBpmnResponse;
import com.workflow.service.DiagramaBpmnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Controller REST + SSE para el diagrama BPMN colaborativo.
 *
 * Endpoints:
 *   GET  /api/v1/bpmn/diagrama   → Carga el diagrama actual desde MongoDB
 *   PUT  /api/v1/bpmn/diagrama   → Guarda cambios y notifica via SSE
 *   GET  /api/v1/bpmn/eventos    → Stream SSE de eventos en tiempo real
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/bpmn")
@RequiredArgsConstructor
@Tag(name = "BPMN Colaborativo", description = "Persistencia y colaboracion en tiempo real del diagrama BPMN")
public class DiagramaBpmnController {

    private final DiagramaBpmnService diagramaService;

    /** Lista thread-safe de clientes SSE conectados */
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    // ─── REST Endpoints ──────────────────────────────────────────────────────

    @GetMapping("/diagrama")
    @Operation(summary = "Obtener diagrama BPMN", description = "Carga el XML del diagrama BPMN colaborativo mas reciente")
    public ResponseEntity<ApiResponse<DiagramaBpmnResponse>> obtenerDiagrama() {
        return diagramaService.obtenerDiagrama()
                .map(d -> ResponseEntity.ok(ApiResponse.ok("Diagrama BPMN cargado", toResponse(d))))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.ok("No existe diagrama guardado aun", null)));
    }

    @PutMapping("/diagrama")
    @Operation(summary = "Guardar diagrama BPMN", description = "Persiste el XML del diagrama y notifica a los demas usuarios via SSE")
    public ResponseEntity<ApiResponse<DiagramaBpmnResponse>> guardarDiagrama(
            @Valid @RequestBody GuardarDiagramaBpmnRequest request,
            @RequestHeader(value = "X-Usuario", required = false, defaultValue = "anonimo") String usuario,
            @RequestHeader(value = "X-Departamento", required = false, defaultValue = "") String departamento
    ) {
        DiagramaBpmn guardado = diagramaService.guardarDiagrama(
                request.getXml(),
                usuario,
                departamento,
                request.getComentario()
        );

        // Notificar a todos los clientes SSE conectados
        emitirEvento("DIAGRAM_UPDATED", Map.of(
                "editadoPor", guardado.getEditadoPor(),
                "departamento", guardado.getDepartamentoEditor() != null ? guardado.getDepartamentoEditor() : "",
                "version", guardado.getVersion(),
                "timestamp", LocalDateTime.now().toString()
        ));

        return ResponseEntity.ok(ApiResponse.ok(
                "Diagrama BPMN guardado (v" + guardado.getVersion() + ")",
                toResponse(guardado)
        ));
    }

    @GetMapping("/version")
    @Operation(summary = "Obtener version actual", description = "Devuelve solo la version numerica del diagrama (para polling ligero)")
    public ResponseEntity<ApiResponse<Long>> obtenerVersion() {
        long version = diagramaService.obtenerVersionActual();
        return ResponseEntity.ok(ApiResponse.ok("Version actual", version));
    }

    @PostMapping("/colaboracion")
    @Operation(summary = "Emitir evento colaborativo", description = "Recibe eventos (cursores, movimientos) y los transmite por SSE sin guardar en BD")
    public ResponseEntity<ApiResponse<String>> emitirEventoColaborativo(
            @RequestBody ColaboracionBpmnRequest request,
            @RequestHeader(value = "X-Usuario", required = false, defaultValue = "anonimo") String usuario,
            @RequestHeader(value = "X-Departamento", required = false, defaultValue = "") String departamento,
            @RequestHeader(value = "X-Rol", required = false, defaultValue = "") String rol
    ) {
        emitirEvento("COLABORACION", Map.of(
                "usuario", usuario,
                "departamento", departamento,
                "rol", rol,
                "evento", request
        ));
        return ResponseEntity.ok(ApiResponse.ok("Evento emitido", null));
    }

    // ─── SSE Stream ──────────────────────────────────────────────────────────

    @GetMapping(value = "/eventos", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream SSE de eventos", description = "Conexion persistente para recibir notificaciones de cambios en el diagrama en tiempo real")
    public SseEmitter suscribirEventos(
            @RequestHeader(value = "X-Usuario", required = false, defaultValue = "anonimo") String usuario
    ) {
        // Timeout de 30 minutos (el frontend se reconecta automaticamente)
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        emitters.add(emitter);
        log.info("[SSE] Cliente conectado: {} (total: {})", usuario, emitters.size());

        // Limpiar al desconectarse
        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.info("[SSE] Cliente desconectado: {} (total: {})", usuario, emitters.size());
        });
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            log.info("[SSE] Timeout cliente: {} (total: {})", usuario, emitters.size());
        });
        emitter.onError(e -> {
            emitters.remove(emitter);
            log.debug("[SSE] Error cliente: {}", usuario);
        });

        // Enviar evento de bienvenida para confirmar conexion
        try {
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data(Map.of(
                            "mensaje", "Conectado al stream de colaboracion BPMN",
                            "version", diagramaService.obtenerVersionActual(),
                            "clientesOnline", emitters.size()
                    ))
            );
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void emitirEvento(String nombre, Object data) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(nombre)
                        .data(data)
                );
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }

        log.debug("[SSE] Evento '{}' emitido a {} clientes", nombre, emitters.size());
    }

    private DiagramaBpmnResponse toResponse(DiagramaBpmn d) {
        return DiagramaBpmnResponse.builder()
                .xml(d.getXml())
                .editadoPor(d.getEditadoPor())
                .departamentoEditor(d.getDepartamentoEditor())
                .comentario(d.getComentario())
                .version(d.getVersion())
                .fechaCreacion(d.getFechaCreacion())
                .fechaActualizacion(d.getFechaActualizacion())
                .build();
    }
}
