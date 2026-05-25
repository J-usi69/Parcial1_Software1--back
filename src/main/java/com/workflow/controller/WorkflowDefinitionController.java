package com.workflow.controller;

import com.workflow.domain.model.WorkflowDefinition;
import com.workflow.dto.response.ApiResponse;
import com.workflow.service.WorkflowDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/bpmn/definitions")
@RequiredArgsConstructor
@Tag(name = "BPMN Workflow Definitions", description = "Endpoints para la gestión, guardado y listado de definiciones de Workflow en formato BPMN 2.0 XML")
public class WorkflowDefinitionController {

    private final WorkflowDefinitionService definitionService;
    private final com.workflow.config.DataSeeder dataSeeder;

    @GetMapping
    @Operation(summary = "Listar todas las definiciones", description = "Recupera todas las definiciones de workflows BPMN persistidas")
    public ResponseEntity<ApiResponse<List<WorkflowDefinition>>> listarTodas() {
        log.info("GET /api/v1/bpmn/definitions - Listando workflows");
        List<WorkflowDefinition> lista = definitionService.listarTodos();
        return ResponseEntity.ok(ApiResponse.ok("Definiciones cargadas exitosamente", lista));
    }

    @GetMapping("/{key}")
    @Operation(summary = "Obtener por llave (key)", description = "Busca una definición específica de BPMN usando su identificador key")
    public ResponseEntity<ApiResponse<WorkflowDefinition>> obtenerPorKey(@PathVariable String key) {
        log.info("GET /api/v1/bpmn/definitions/{} - Buscando por key", key);
        return definitionService.obtenerPorKey(key)
                .map(def -> ResponseEntity.ok(ApiResponse.ok("Workflow cargado", def)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.ok("No existe workflow con esa key", null)));
    }

    @PostMapping
    @Operation(summary = "Guardar o actualizar definición", description = "Crea o sobreescribe una definición de workflow, autoincrementando la versión del XML")
    public ResponseEntity<ApiResponse<WorkflowDefinition>> guardarOActualizar(
            @RequestBody WorkflowDefinition definition,
            @RequestHeader(value = "X-Usuario", required = false, defaultValue = "anonimo") String usuario,
            @RequestHeader(value = "X-Departamento", required = false, defaultValue = "") String departamento
    ) {
        log.info("POST /api/v1/bpmn/definitions - Guardando workflow '{}' (key: {})", definition.getName(), definition.getKey());
        WorkflowDefinition guardado = definitionService.guardarOActualizar(definition, usuario, departamento);
        return ResponseEntity.ok(ApiResponse.ok("Workflow guardado exitosamente (v" + guardado.getVersion() + ")", guardado));
    }

    @PostMapping("/reset-seed")
    @Operation(summary = "Resetear y Seedear base de datos", description = "ELIMINA todas las definiciones y re-inyecta los procesos maestros de la lógica de negocio.")
    public ResponseEntity<ApiResponse<Void>> resetAndSeed() {
        log.warn("POST /api/v1/bpmn/definitions/reset-seed - Ejecutando limpieza y recarga completa del entorno de pruebas");
        dataSeeder.forceSeed();
        return ResponseEntity.ok(ApiResponse.ok("Entorno de pruebas y base de datos reseteados y cargados con éxito", null));
    }
}
