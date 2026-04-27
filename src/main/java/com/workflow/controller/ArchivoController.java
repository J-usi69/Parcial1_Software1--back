package com.workflow.controller;

import com.workflow.domain.model.ArchivoAdjunto;
import com.workflow.dto.response.ApiResponse;
import com.workflow.dto.response.ArchivoAdjuntoResponse;
import com.workflow.service.ArchivoStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller for file upload and download operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/archivos")
@RequiredArgsConstructor
@Tag(name = "Archivos", description = "Endpoints para subir y descargar archivos adjuntos de solicitudes.")
public class ArchivoController {

    private final ArchivoStorageService storageService;

    /**
     * Sube uno o más archivos y retorna metadatos.
     * Los archivos se almacenan temporalmente; el frontend usa los IDs retornados
     * para vincularlos a la solicitud al momento de crearla.
     */
    @PostMapping(value = "/subir", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Subir archivos", description = "Sube uno o más archivos (max 10MB cada uno). Tipos: PDF, imágenes, Word, Excel, texto.")
    public ResponseEntity<ApiResponse<List<ArchivoAdjuntoResponse>>> subirArchivos(
            @RequestParam("archivos") MultipartFile[] archivos,
            @RequestHeader(value = "X-Usuario", required = false) String usuario) {

        String uploader = (usuario != null && !usuario.isBlank()) ? usuario : "anonimo";
        log.info("POST /api/v1/archivos/subir - {} archivos por {}", archivos.length, uploader);

        List<ArchivoAdjunto> almacenados = storageService.almacenarArchivos(archivos, uploader);

        List<ArchivoAdjuntoResponse> respuesta = almacenados.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                ApiResponse.ok("Archivos subidos exitosamente", respuesta)
        );
    }

    /**
     * Descarga un archivo por su ID (nombre almacenado).
     */
    @GetMapping("/{archivoId}")
    @Operation(summary = "Descargar archivo", description = "Descarga un archivo adjunto por su ID.")
    public ResponseEntity<Resource> descargarArchivo(@PathVariable String archivoId) {
        log.info("GET /api/v1/archivos/{}", archivoId);

        // Search for file with any extension
        Resource resource = storageService.cargarArchivo(archivoId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    private ArchivoAdjuntoResponse toResponse(ArchivoAdjunto archivo) {
        return ArchivoAdjuntoResponse.builder()
                .id(archivo.getId())
                .nombreOriginal(archivo.getNombreOriginal())
                .tipoContenido(archivo.getTipoContenido())
                .tamanoBytes(archivo.getTamanoBytes())
                .subidoPor(archivo.getSubidoPor())
                .fechaSubida(archivo.getFechaSubida())
                .build();
    }
}
