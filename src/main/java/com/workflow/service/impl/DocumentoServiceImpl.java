package com.workflow.service.impl;

import com.workflow.domain.enums.EstadoWorkflow;
import com.workflow.domain.model.ArchivoAdjunto;
import com.workflow.domain.model.Documento;
import com.workflow.domain.model.SolicitudWorkflow;
import com.workflow.exception.ResourceNotFoundException;
import com.workflow.exception.UnauthorizedActionException;
import com.workflow.repository.DocumentoRepository;
import com.workflow.repository.SolicitudWorkflowRepository;
import com.workflow.service.ArchivoStorageService;
import com.workflow.service.DocumentoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentoServiceImpl implements DocumentoService {

    private final DocumentoRepository repository;
    private final SolicitudWorkflowRepository solicitudRepository;
    private final ArchivoStorageService storageService;

    @Override
    public List<Documento> listarPorSolicitud(String solicitudId) {
        return repository.findBySolicitudIdOrderByFechaCreacionDesc(solicitudId);
    }

    @Override
    public List<Documento> listarPorTarea(String tareaId) {
        return repository.findByTareaIdOrderByFechaCreacionDesc(tareaId);
    }

    @Override
    public Documento obtenerPorId(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Documento", "id", id));
    }

    @Override
    public Documento crearDocumentoArchivo(String solicitudId, String nombre, String descripcion, MultipartFile archivo, String usuario) {
        validarSolicitudExiste(solicitudId);

        // Almacenar el archivo físico
        ArchivoAdjunto adjunto = storageService.almacenarArchivo(archivo, usuario);

        // Crear la primera versión del documento
        Documento.VersionDocumento v1 = Documento.VersionDocumento.builder()
                .version(1)
                .nombreAlmacenado(adjunto.getNombreAlmacenado())
                .nombreOriginal(adjunto.getNombreOriginal())
                .tipoContenido(adjunto.getTipoContenido())
                .tamanoBytes(adjunto.getTamanoBytes())
                .subidoPor(usuario)
                .fechaSubida(LocalDateTime.now())
                .comentarioCambio("Carga inicial del archivo")
                .build();

        List<Documento.VersionDocumento> versiones = new ArrayList<>();
        versiones.add(v1);

        Documento documento = Documento.builder()
                .solicitudId(solicitudId)
                .nombre(nombre)
                .descripcion(descripcion)
                .tipo("FILE")
                .versionActual(1)
                .creadoPor(usuario)
                .fechaCreacion(LocalDateTime.now())
                .fechaActualizacion(LocalDateTime.now())
                .versiones(versiones)
                .build();

        Documento guardado = repository.save(documento);
        log.info("Documento de archivo '{}' creado con versión 1 para la solicitud {}", nombre, solicitudId);

        registrarEventoEnSolicitud(solicitudId, usuario, 
                String.format("Documento de archivo creado: '%s' (Versión 1)", nombre));

        return guardado;
    }

    @Override
    public Documento crearDocumentoColaborativo(String solicitudId, String nombre, String descripcion, String contenidoInicial, String usuario) {
        validarSolicitudExiste(solicitudId);

        Documento.VersionDocumento v1 = Documento.VersionDocumento.builder()
                .version(1)
                .nombreOriginal(nombre + ".txt")
                .tipoContenido("text/plain")
                .subidoPor(usuario)
                .fechaSubida(LocalDateTime.now())
                .comentarioCambio("Inicialización del documento colaborativo online")
                .contenidoColaborativoSnapshot(contenidoInicial)
                .build();

        List<Documento.VersionDocumento> versiones = new ArrayList<>();
        versiones.add(v1);

        Documento documento = Documento.builder()
                .solicitudId(solicitudId)
                .nombre(nombre)
                .descripcion(descripcion)
                .tipo("COLLABORATIVE")
                .versionActual(1)
                .creadoPor(usuario)
                .fechaCreacion(LocalDateTime.now())
                .fechaActualizacion(LocalDateTime.now())
                .contenidoColaborativo(contenidoInicial)
                .versiones(versiones)
                .build();

        Documento guardado = repository.save(documento);
        log.info("Documento colaborativo '{}' creado para la solicitud {}", nombre, solicitudId);

        registrarEventoEnSolicitud(solicitudId, usuario, 
                String.format("Documento colaborativo en línea creado: '%s'", nombre));

        return guardado;
    }

    @Override
    public Documento subirNuevaVersionArchivo(String id, MultipartFile archivo, String comentario, String usuario) {
        Documento documento = obtenerPorId(id);

        if (documento.getBloqueadoPor() != null && !documento.getBloqueadoPor().equalsIgnoreCase(usuario)) {
            throw new UnauthorizedActionException(usuario, "modificar el documento porque está bloqueado por " + documento.getBloqueadoPor());
        }

        // Guardar archivo físico
        ArchivoAdjunto adjunto = storageService.almacenarArchivo(archivo, usuario);

        int proximaVersion = documento.getVersionActual() + 1;

        Documento.VersionDocumento version = Documento.VersionDocumento.builder()
                .version(proximaVersion)
                .nombreAlmacenado(adjunto.getNombreAlmacenado())
                .nombreOriginal(adjunto.getNombreOriginal())
                .tipoContenido(adjunto.getTipoContenido())
                .tamanoBytes(adjunto.getTamanoBytes())
                .subidoPor(usuario)
                .fechaSubida(LocalDateTime.now())
                .comentarioCambio(comentario != null && !comentario.isBlank() ? comentario : "Actualización de versión a v" + proximaVersion)
                .build();

        documento.agregarVersion(version);
        documento.setVersionActual(proximaVersion);
        documento.setFechaActualizacion(LocalDateTime.now());
        // Desbloquear automáticamente al subir nueva versión si estaba bloqueado por el mismo usuario
        if (usuario.equalsIgnoreCase(documento.getBloqueadoPor())) {
            documento.setBloqueadoPor(null);
            documento.setBloqueadoAt(null);
        }

        Documento guardado = repository.save(documento);
        log.info("Documento '{}' actualizado a la versión {} por {}", documento.getNombre(), proximaVersion, usuario);

        registrarEventoEnSolicitud(documento.getSolicitudId(), usuario, 
                String.format("Nueva versión del archivo '%s' subida: v%d. Motivo: %s", 
                        documento.getNombre(), proximaVersion, comentario));

        return guardado;
    }

    @Override
    public Documento guardarSnapshotVersionColaborativo(String id, String comentario, String usuario) {
        Documento documento = obtenerPorId(id);
        if (!"COLLABORATIVE".equalsIgnoreCase(documento.getTipo())) {
            throw new IllegalArgumentException("Sólo se pueden guardar snapshots de versiones para documentos colaborativos.");
        }

        int proximaVersion = documento.getVersionActual() + 1;

        Documento.VersionDocumento version = Documento.VersionDocumento.builder()
                .version(proximaVersion)
                .nombreOriginal(documento.getNombre() + "_v" + proximaVersion + ".txt")
                .tipoContenido("text/plain")
                .subidoPor(usuario)
                .fechaSubida(LocalDateTime.now())
                .comentarioCambio(comentario != null && !comentario.isBlank() ? comentario : "Snapshot manual de versión a v" + proximaVersion)
                .contenidoColaborativoSnapshot(documento.getContenidoColaborativo())
                .build();

        documento.agregarVersion(version);
        documento.setVersionActual(proximaVersion);
        documento.setFechaActualizacion(LocalDateTime.now());

        Documento guardado = repository.save(documento);
        log.info("Snapshot guardado para documento colaborativo '{}' a la versión v{} por {}", documento.getNombre(), proximaVersion, usuario);

        registrarEventoEnSolicitud(documento.getSolicitudId(), usuario, 
                String.format("Guardada nueva versión v%d en línea del documento colaborativo '%s'", proximaVersion, documento.getNombre()));

        return guardado;
    }

    @Override
    public Documento actualizarContenidoColaborativo(String id, String nuevoContenido, String usuario) {
        Documento documento = obtenerPorId(id);
        if (!"COLLABORATIVE".equalsIgnoreCase(documento.getTipo())) {
            throw new IllegalArgumentException("No se puede actualizar contenido en línea de un documento de tipo FILE.");
        }

        if (documento.getBloqueadoPor() != null && !documento.getBloqueadoPor().equalsIgnoreCase(usuario)) {
            throw new UnauthorizedActionException(usuario, "editar el documento en línea porque está bloqueado por " + documento.getBloqueadoPor());
        }

        documento.setContenidoColaborativo(nuevoContenido);
        documento.setFechaActualizacion(LocalDateTime.now());

        return repository.save(documento);
    }

    @Override
    public Documento bloquearDocumento(String id, String usuario) {
        Documento documento = obtenerPorId(id);
        if (documento.getBloqueadoPor() != null) {
            if (documento.getBloqueadoPor().equalsIgnoreCase(usuario)) {
                return documento; // Ya lo tiene bloqueado él mismo
            }
            throw new UnauthorizedActionException(usuario, "bloquear el documento porque ya está bloqueado por " + documento.getBloqueadoPor());
        }

        documento.setBloqueadoPor(usuario);
        documento.setBloqueadoAt(LocalDateTime.now());
        log.info("Documento '{}' bloqueado temporalmente por {}", documento.getNombre(), usuario);

        return repository.save(documento);
    }

    @Override
    public Documento desbloquearDocumento(String id, String usuario) {
        Documento documento = obtenerPorId(id);
        if (documento.getBloqueadoPor() == null) {
            return documento; // No estaba bloqueado
        }

        documento.setBloqueadoPor(null);
        documento.setBloqueadoAt(null);
        log.info("Documento '{}' desbloqueado por {}", documento.getNombre(), usuario);

        return repository.save(documento);
    }

    @Override
    public void eliminarDocumento(String id, String usuario) {
        Documento documento = obtenerPorId(id);
        if (documento.getBloqueadoPor() != null && !documento.getBloqueadoPor().equalsIgnoreCase(usuario)) {
            throw new UnauthorizedActionException(usuario, "eliminar el documento ya que está bloqueado por " + documento.getBloqueadoPor());
        }

        repository.delete(documento);
        log.info("Documento '{}' eliminado por {}", documento.getNombre(), usuario);

        registrarEventoEnSolicitud(documento.getSolicitudId(), usuario, 
                String.format("Documento '%s' ha sido eliminado del sistema", documento.getNombre()));
    }

    @Override
    public Documento asociarASolicitudYTarea(String id, String solicitudId, String tareaId, String usuario) {
        Documento documento = obtenerPorId(id);
        
        // Validar que la solicitud/etapa exista si se manda solicitudId
        if (solicitudId != null) {
            validarSolicitudExiste(solicitudId);
        }

        String antiguaSolicitudId = documento.getSolicitudId();
        
        if (solicitudId != null) {
            documento.setSolicitudId(solicitudId);
            if (tareaId == null || tareaId.trim().isEmpty()) {
                documento.setTareaId(null);
            }
        }
        if (tareaId != null) {
            documento.setTareaId(tareaId);
        }
        
        documento.setFechaActualizacion(LocalDateTime.now());

        Documento guardado = repository.save(documento);
        log.info("Documento '{}' re-asociado de la solicitud {} a la {} con tarea {}", documento.getNombre(), antiguaSolicitudId, solicitudId, tareaId);

        if (solicitudId != null && !solicitudId.trim().isEmpty() && !solicitudId.equals("bpmn-central")) {
            registrarEventoEnSolicitud(solicitudId, usuario, 
                String.format("Documento '%s' ha sido vinculado a esta solicitud/etapa", documento.getNombre()));
        }

        return guardado;
    }

    @Override
    public List<Documento> buscarDocumentos(String nombre) {
        return repository.buscarPorNombre(nombre);
    }

    @Override
    public List<Documento> listarTodos() {
        return repository.findAll();
    }

    private void validarSolicitudExiste(String solicitudId) {
        if (solicitudId != null && (solicitudId.startsWith("Activity_") || 
                                    solicitudId.startsWith("Event_") || 
                                    solicitudId.startsWith("Gateway_") ||
                                    solicitudId.equals("bpmn-central") ||
                                    solicitudId.startsWith("bpmn-"))) {
            return; // Es una etapa o nodo de diseño BPMN, no requiere Solicitud física en la BD
        }
        if (!solicitudRepository.existsById(solicitudId)) {
            throw new ResourceNotFoundException("Solicitud", "id", solicitudId);
        }
    }

    private void registrarEventoEnSolicitud(String solicitudId, String usuario, String comentario) {
        solicitudRepository.findById(solicitudId).ifPresent(solicitud -> {
            solicitud.registrarTransicion(
                    solicitud.getEstado(),
                    solicitud.getEstado(),
                    usuario,
                    "USUARIO",
                    comentario
            );
            solicitudRepository.save(solicitud);
        });
    }
}
