package com.workflow.service;

import com.workflow.domain.model.ArchivoAdjunto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Servicio de almacenamiento de archivos adjuntos en el sistema de archivos local.
 * Almacena archivos en la carpeta configurada y devuelve metadatos sin exponer rutas internas.
 */
@Slf4j
@Service
public class ArchivoStorageService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final List<String> TIPOS_PERMITIDOS = List.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/jpg",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain"
    );

    @Value("${app.uploads.dir:./uploads}")
    private String uploadsDir;

    private Path uploadsPath;

    @PostConstruct
    void inicializar() {
        uploadsPath = Paths.get(uploadsDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadsPath);
            log.info("Directorio de uploads inicializado: {}", uploadsPath);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo crear el directorio de uploads: " + uploadsPath, e);
        }
    }

    /**
     * Almacena múltiples archivos y retorna lista de metadatos.
     */
    public List<ArchivoAdjunto> almacenarArchivos(MultipartFile[] archivos, String usuario) {
        List<ArchivoAdjunto> adjuntos = new ArrayList<>();

        if (archivos == null || archivos.length == 0) {
            return adjuntos;
        }

        for (MultipartFile archivo : archivos) {
            if (archivo.isEmpty()) continue;
            adjuntos.add(almacenarArchivo(archivo, usuario));
        }

        return adjuntos;
    }

    /**
     * Almacena un único archivo y retorna sus metadatos.
     */
    public ArchivoAdjunto almacenarArchivo(MultipartFile archivo, String usuario) {
        validarArchivo(archivo);

        String id = UUID.randomUUID().toString();
        String extension = extraerExtension(archivo.getOriginalFilename());
        String nombreAlmacenado = id + extension;

        try {
            Path destino = uploadsPath.resolve(nombreAlmacenado).normalize();
            // Prevenir path traversal
            if (!destino.startsWith(uploadsPath)) {
                throw new IllegalArgumentException("Ruta de archivo inválida");
            }

            Files.copy(archivo.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);
            log.info("Archivo almacenado: {} -> {} por {}", archivo.getOriginalFilename(), nombreAlmacenado, usuario);

            return ArchivoAdjunto.builder()
                    .id(id)
                    .nombreOriginal(archivo.getOriginalFilename())
                    .nombreAlmacenado(nombreAlmacenado)
                    .tipoContenido(archivo.getContentType())
                    .tamanoBytes(archivo.getSize())
                    .subidoPor(usuario)
                    .fechaSubida(LocalDateTime.now())
                    .build();

        } catch (IOException e) {
            throw new RuntimeException("Error al almacenar archivo: " + archivo.getOriginalFilename(), e);
        }
    }

    /**
     * Carga un archivo como Resource para descarga.
     */
    public Resource cargarArchivo(String nombreAlmacenado) {
        try {
            Path filePath = uploadsPath.resolve(nombreAlmacenado).normalize();
            if (!filePath.startsWith(uploadsPath)) {
                throw new IllegalArgumentException("Ruta de archivo inválida");
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new RuntimeException("Archivo no encontrado: " + nombreAlmacenado);

        } catch (MalformedURLException e) {
            throw new RuntimeException("Error al acceder al archivo: " + nombreAlmacenado, e);
        }
    }

    private void validarArchivo(MultipartFile archivo) {
        if (archivo.isEmpty()) {
            throw new IllegalArgumentException("El archivo está vacío");
        }

        if (archivo.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    String.format("El archivo excede el limite de %d MB", MAX_FILE_SIZE / (1024 * 1024))
            );
        }

        String contentType = archivo.getContentType();
        if (contentType == null || !TIPOS_PERMITIDOS.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Tipo de archivo no permitido: " + contentType + ". Permitidos: PDF, imágenes, Word, Excel, texto."
            );
        }
    }

    private String extraerExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}
