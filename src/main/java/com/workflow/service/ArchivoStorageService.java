package com.workflow.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.storage.BlobInfo;
import com.workflow.domain.model.ArchivoAdjunto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
 * Servicio híbrido de almacenamiento de archivos.
 * Soporta Google Cloud Storage (GCS) y almacén en disco local como respaldo (fallback).
 * Autodetecta credenciales locales y de Google Cloud Run mediante Application Default Credentials (ADC).
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

    @Value("${app.gcp.bucket-name:}")
    private String bucketName;

    @Value("${app.firebase.credentials-path:${FIREBASE_CREDENTIALS_PATH:}}")
    private String credentialsPath;

    @Value("${app.firebase.project-id:${FIREBASE_PROJECT_ID:}}")
    private String firebaseProjectId;

    private Path uploadsPath;
    private Storage storageClient;
    private boolean useGcs = false;

    @PostConstruct
    void inicializar() {
        // 1. Inicializar almacenamiento local (siempre listo como backup)
        uploadsPath = Paths.get(uploadsDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadsPath);
            log.info("Directorio de uploads local inicializado en: {}", uploadsPath);
        } catch (IOException e) {
            log.error("No se pudo crear el directorio de uploads local: {}", uploadsPath, e);
        }

        // 2. Intentar inicializar Google Cloud Storage
        try {
            GoogleCredentials credentials;
            if (credentialsPath != null && !credentialsPath.isBlank()) {
                try (InputStream stream = new FileInputStream(credentialsPath)) {
                    credentials = GoogleCredentials.fromStream(stream);
                }
            } else {
                credentials = GoogleCredentials.getApplicationDefault();
            }

            StorageOptions.Builder optionsBuilder = StorageOptions.newBuilder()
                    .setCredentials(credentials);
            if (firebaseProjectId != null && !firebaseProjectId.isBlank()) {
                optionsBuilder.setProjectId(firebaseProjectId);
            }
            
            storageClient = optionsBuilder.build().getService();
            
            if (bucketName != null && !bucketName.isBlank()) {
                Bucket bucket = storageClient.get(bucketName);
                if (bucket != null) {
                    useGcs = true;
                    log.info("¡Google Cloud Storage Inicializado! Usando bucket: {}", bucketName);
                } else {
                    log.warn("El bucket {} no fue encontrado o es inaccesible. Se usará almacenamiento local.", bucketName);
                }
            }
        } catch (Exception e) {
            log.warn("No se pudo conectar a Google Cloud Storage ({}). Se usará almacenamiento local como backup.", e.getMessage());
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

        if (useGcs) {
            try {
                String gcsBlobPath = "uploads/" + nombreAlmacenado;
                BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, gcsBlobPath)
                        .setContentType(archivo.getContentType())
                        .build();

                storageClient.create(blobInfo, archivo.getBytes());
                log.info("Archivo almacenado en Google Cloud Storage: {} -> {} por {}", archivo.getOriginalFilename(), gcsBlobPath, usuario);

                return ArchivoAdjunto.builder()
                        .id(id)
                        .nombreOriginal(archivo.getOriginalFilename())
                        .nombreAlmacenado("gcs:" + gcsBlobPath)
                        .tipoContenido(archivo.getContentType())
                        .tamanoBytes(archivo.getSize())
                        .subidoPor(usuario)
                        .fechaSubida(LocalDateTime.now())
                        .build();
            } catch (Exception e) {
                log.error("Error al subir archivo a GCS. Intentando almacenamiento local de respaldo.", e);
            }
        }

        // Fallback: Almacenamiento local
        try {
            Path destino = uploadsPath.resolve(nombreAlmacenado).normalize();
            if (!destino.startsWith(uploadsPath)) {
                throw new IllegalArgumentException("Ruta de archivo inválida");
            }

            Files.copy(archivo.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);
            log.info("Archivo almacenado en disco local (fallback): {} -> {} por {}", archivo.getOriginalFilename(), nombreAlmacenado, usuario);

            return ArchivoAdjunto.builder()
                    .id(id)
                    .nombreOriginal(archivo.getOriginalFilename())
                    .nombreAlmacenado("local:" + nombreAlmacenado)
                    .tipoContenido(archivo.getContentType())
                    .tamanoBytes(archivo.getSize())
                    .subidoPor(usuario)
                    .fechaSubida(LocalDateTime.now())
                    .build();

        } catch (IOException e) {
            throw new RuntimeException("Error al almacenar archivo en local: " + archivo.getOriginalFilename(), e);
        }
    }

    /**
     * Carga un archivo como Resource para descarga.
     */
    public Resource cargarArchivo(String nombreAlmacenado) {
        String realName = nombreAlmacenado;
        boolean isGcs = false;

        if (nombreAlmacenado.startsWith("gcs:")) {
            realName = nombreAlmacenado.substring(4);
            isGcs = true;
        } else if (nombreAlmacenado.startsWith("local:")) {
            realName = nombreAlmacenado.substring(6);
            isGcs = false;
        } else {
            // Retrocompatibilidad con archivos antiguos sin prefijo
            isGcs = useGcs;
        }

        if (isGcs && storageClient != null) {
            try {
                Blob blob = storageClient.get(bucketName, realName);
                if (blob != null && blob.exists()) {
                    byte[] content = blob.getContent();
                    final String finalRealName = realName;
                    return new ByteArrayResource(content) {
                        @Override
                        public String getFilename() {
                            if (finalRealName.contains("/")) {
                                return finalRealName.substring(finalRealName.lastIndexOf("/") + 1);
                            }
                            return finalRealName;
                        }
                    };
                }
            } catch (Exception e) {
                log.error("Error al descargar desde Google Cloud Storage ({}). Reintentando en local.", e.getMessage());
            }
        }

        // Cargar desde disco local
        try {
            Path filePath = uploadsPath.resolve(realName).normalize();
            if (!filePath.startsWith(uploadsPath)) {
                throw new IllegalArgumentException("Ruta de archivo inválida");
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new RuntimeException("Archivo no encontrado en local: " + realName);

        } catch (MalformedURLException e) {
            throw new RuntimeException("Error al acceder al archivo: " + realName, e);
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
