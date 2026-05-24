package com.workflow.controller;

import com.workflow.dto.response.ApiResponse;
import com.workflow.dto.response.PrediccionResponse;
import com.workflow.service.MotorPredictivoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/ia/prediccion")
@RequiredArgsConstructor
@Tag(name = "Motor Predictivo IA (TensorFlow)", description = "Análisis proactivo de procesos, predicción de retrasos y detección de anomalías.")
public class MotorPredictivoController {

    private final MotorPredictivoService predictivoService;

    @GetMapping("/solicitud/{id}")
    @Operation(summary = "Análisis predictivo de solicitud", description = "Ejecuta el motor TensorFlow para predecir el éxito y riesgo de una solicitud específica.")
    public ResponseEntity<ApiResponse<PrediccionResponse>> analizarSolicitud(@PathVariable String id) {
        log.info("GET /api/v1/ia/prediccion/solicitud/{} - Ejecutando motor IA", id);
        PrediccionResponse analisis = predictivoService.analizarSolicitud(id);
        return ResponseEntity.ok(ApiResponse.ok("Análisis predictivo completado con motor TensorFlow", analisis));
    }

    @GetMapping("/anomalias")
    @Operation(summary = "Detección de anomalías globales", description = "Escanea el sistema en busca de cuellos de botella o procesos estancados.")
    public ResponseEntity<ApiResponse<List<String>>> detectarAnomalias() {
        log.info("GET /api/v1/ia/prediccion/anomalias - Escaneo de salud del sistema");
        List<String> anomalias = predictivoService.detectarAnomaliasGlobales();
        return ResponseEntity.ok(ApiResponse.ok("Escaneo de anomalías completado", anomalias));
    }
}
