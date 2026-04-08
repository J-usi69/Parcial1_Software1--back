package com.workflow.service.impl;

import com.workflow.domain.enums.EstadoWorkflow;
import com.workflow.domain.enums.Prioridad;
import com.workflow.domain.enums.RolUsuario;
import com.workflow.dto.request.CambiarEstadoRequest;
import com.workflow.dto.request.ChatIARequest;
import com.workflow.dto.request.ReasignarDepartamentoRequest;
import com.workflow.dto.response.SolicitudResponse;
import com.workflow.dto.response.ChatIAResponse;
import com.workflow.exception.ResourceNotFoundException;
import com.workflow.service.ChatIAService;
import com.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Map;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatIAServiceImpl implements ChatIAService {

    private static final Pattern CODIGO_SEGUIMIENTO_PATTERN =
            Pattern.compile("WF-\\d{4}-\\d{1,6}", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMENTARIO_PATTERN =
            Pattern.compile("(?i)comentario\\s*:\\s*(.+)$");
    private static final long ACCION_TTL_MINUTOS = 10;

    private final WorkflowService workflowService;
    private final Map<String, PendingAction> accionesPendientes = new ConcurrentHashMap<>();

    private enum TipoAccionPendiente {
        CAMBIAR_ESTADO,
        REASIGNAR_DEPARTAMENTO,
        ASIGNAR_USUARIO
    }

    private static final class PendingAction {
        private final TipoAccionPendiente tipo;
        private final String solicitudId;
        private final String codigoSeguimiento;
        private final EstadoWorkflow nuevoEstado;
        private final String nuevoDepartamento;
        private final String usuarioAsignado;
        private final String comentario;
        private final RolUsuario rolEsperado;
        private final String departamentoEsperado;
        private final LocalDateTime creadaEn;
        private final String resumen;

        private PendingAction(
                TipoAccionPendiente tipo,
                String solicitudId,
                String codigoSeguimiento,
                EstadoWorkflow nuevoEstado,
                String nuevoDepartamento,
                String usuarioAsignado,
                String comentario,
                RolUsuario rolEsperado,
                String departamentoEsperado,
                LocalDateTime creadaEn,
                String resumen
        ) {
            this.tipo = tipo;
            this.solicitudId = solicitudId;
            this.codigoSeguimiento = codigoSeguimiento;
            this.nuevoEstado = nuevoEstado;
            this.nuevoDepartamento = nuevoDepartamento;
            this.usuarioAsignado = usuarioAsignado;
            this.comentario = comentario;
            this.rolEsperado = rolEsperado;
            this.departamentoEsperado = departamentoEsperado;
            this.creadaEn = creadaEn;
            this.resumen = resumen;
        }
    }

    @Override
    public ChatIAResponse procesarMensaje(ChatIARequest request, RolUsuario rolUsuario, String departamentoUsuario) {
        log.info("Procesando mensaje de Chat IA para usuario: {} rol: {}", request.getUsuarioId(), rolUsuario);

        if (rolUsuario == null) {
            return construirRespuesta(
                    "No puedo procesar la consulta porque el contexto de rol no esta disponible.",
                    "CONTEXTO_INVALIDO"
            );
        }

        String usuario = request.getUsuarioId();
        String mensajeOriginal = request.getMensaje() != null ? request.getMensaje().trim() : "";
        String mensajeNormalizado = normalizarTexto(mensajeOriginal);

        if (mensajeNormalizado.isBlank()) {
            return construirRespuesta(
                    "Escribe una consulta, por ejemplo: 'resumen', 'mis solicitudes', 'cola' o 'estado de WF-2026-001'.",
                    "MENSAJE_VACIO"
            );
        }

        Optional<String> codigoDetectado = extraerCodigoSeguimiento(mensajeOriginal);

        Optional<ChatIAResponse> confirmacion = manejarConfirmacionOCancelacion(
                usuario,
                rolUsuario,
                departamentoUsuario,
                mensajeNormalizado
        );
        if (confirmacion.isPresent()) {
            return confirmacion.get();
        }

        Optional<ChatIAResponse> accionOperativa = prepararAccionOperativa(
                usuario,
                rolUsuario,
                departamentoUsuario,
                mensajeOriginal,
                mensajeNormalizado,
                codigoDetectado
        );
        if (accionOperativa.isPresent()) {
            return accionOperativa.get();
        }

        if (contieneAlguno(mensajeNormalizado, "ayuda", "help", "comandos", "que puedes", "que pod")) {
            return responderAyudaPorRol(rolUsuario);
        }

        if (codigoDetectado.isPresent()
                && contieneAlguno(mensajeNormalizado, "estado", "detalle", "seguimiento", "solicitud", "wf-")) {
            return responderDetallePorCodigo(codigoDetectado.get(), usuario, rolUsuario, departamentoUsuario);
        }

        if (rolUsuario == RolUsuario.SOLICITANTE) {
            return procesarComoSolicitante(usuario, mensajeNormalizado);
        }

        if (rolUsuario == RolUsuario.REVISOR) {
            return procesarComoRevisor(usuario, mensajeNormalizado, departamentoUsuario, codigoDetectado);
        }

        return procesarComoAdministrador(mensajeNormalizado);
    }

    private Optional<ChatIAResponse> manejarConfirmacionOCancelacion(
            String usuario,
            RolUsuario rolUsuario,
            String departamentoUsuario,
            String mensajeNormalizado
    ) {
        boolean confirmar = esComandoConfirmacion(mensajeNormalizado);
        boolean cancelar = esComandoCancelacion(mensajeNormalizado);

        if (!confirmar && !cancelar) {
            return Optional.empty();
        }

        PendingAction pendiente = obtenerAccionPendienteVigente(usuario);
        if (pendiente == null) {
            return Optional.of(construirRespuesta(
                    "No hay acciones pendientes para confirmar. Pide una accion operativa primero.",
                    "SIN_ACCION_PENDIENTE"
            ));
        }

        if (cancelar) {
            accionesPendientes.remove(usuario);
            return Optional.of(construirRespuesta(
                    "Accion cancelada: " + pendiente.resumen,
                    "ACCION_CANCELADA"
            ));
        }

        if (pendiente.rolEsperado != rolUsuario) {
            accionesPendientes.remove(usuario);
            return Optional.of(construirRespuesta(
                    "Tu rol actual no coincide con el rol de la accion pendiente. Se descarto por seguridad.",
                    "CONTEXTO_CAMBIADO"
            ));
        }

        if (rolUsuario == RolUsuario.REVISOR
                && pendiente.departamentoEsperado != null
                && (departamentoUsuario == null || !pendiente.departamentoEsperado.equalsIgnoreCase(departamentoUsuario))) {
            accionesPendientes.remove(usuario);
            return Optional.of(construirRespuesta(
                    "Tu departamento actual no coincide con la accion pendiente. Se descarto por seguridad.",
                    "CONTEXTO_CAMBIADO"
            ));
        }

        try {
            SolicitudResponse ejecutada = ejecutarAccionPendiente(pendiente, usuario, rolUsuario, departamentoUsuario);
            accionesPendientes.remove(usuario);

            return Optional.of(construirRespuesta(
                    "Accion ejecutada con exito: " + pendiente.resumen + "\nResultado: " + lineaSolicitud(ejecutada),
                    "ACCION_EJECUTADA"
            ));
        } catch (RuntimeException ex) {
            accionesPendientes.remove(usuario);
            log.warn("No se pudo ejecutar accion pendiente para usuario {}: {}", usuario, ex.getMessage());

            return Optional.of(construirRespuesta(
                    "No pude ejecutar la accion pendiente: " + ex.getMessage(),
                    "ACCION_ERROR_EJECUCION"
            ));
        }
    }

    private Optional<ChatIAResponse> prepararAccionOperativa(
            String usuario,
            RolUsuario rolUsuario,
            String departamentoUsuario,
            String mensajeOriginal,
            String mensajeNormalizado,
            Optional<String> codigoDetectado
    ) {
        Optional<ChatIAResponse> reasignacion = prepararAccionReasignacion(
                usuario,
                rolUsuario,
                departamentoUsuario,
                mensajeOriginal,
                mensajeNormalizado,
                codigoDetectado
        );
        if (reasignacion.isPresent()) {
            return reasignacion;
        }

        Optional<ChatIAResponse> cambioEstado = prepararAccionCambioEstado(
                usuario,
                rolUsuario,
                departamentoUsuario,
                mensajeOriginal,
                mensajeNormalizado,
                codigoDetectado
        );
        if (cambioEstado.isPresent()) {
            return cambioEstado;
        }

        return prepararAccionAsignacion(
                usuario,
                rolUsuario,
                departamentoUsuario,
                mensajeOriginal,
                mensajeNormalizado,
                codigoDetectado
        );
    }

    private Optional<ChatIAResponse> prepararAccionCambioEstado(
            String usuario,
            RolUsuario rolUsuario,
            String departamentoUsuario,
            String mensajeOriginal,
            String mensajeNormalizado,
            Optional<String> codigoDetectado
    ) {
        boolean trigger = contieneAlguno(
                mensajeNormalizado,
                "aprobar",
                "rechazar",
                "cambiar estado",
                "pasar a",
                "mover a",
                "poner en"
        );
        if (!trigger) {
            return Optional.empty();
        }

        if (!rolUsuario.puedeRevisar()) {
            return Optional.of(construirRespuesta(
                    "No tienes permisos para cambiar estados desde chat.",
                    "ACCION_NO_PERMITIDA"
            ));
        }

        if (codigoDetectado.isEmpty()) {
            return Optional.of(construirRespuesta(
                    "Para cambiar estado debes indicar el codigo, ejemplo: 'aprobar WF-2026-001'.",
                    "ACCION_FORMATO_INVALIDO"
            ));
        }

        EstadoWorkflow nuevoEstado = null;
        if (contieneAlguno(mensajeNormalizado, "aprobar", "aprobado")) {
            nuevoEstado = EstadoWorkflow.APROBADO;
        } else if (contieneAlguno(mensajeNormalizado, "rechazar", "rechazado")) {
            nuevoEstado = EstadoWorkflow.RECHAZADO;
        } else if (contieneAlguno(mensajeNormalizado, "en revision", "revision")) {
            nuevoEstado = EstadoWorkflow.EN_REVISION;
        } else if (contieneAlguno(mensajeNormalizado, "pendiente")) {
            nuevoEstado = EstadoWorkflow.PENDIENTE;
        }

        if (nuevoEstado == null) {
            return Optional.of(construirRespuesta(
                    "No pude detectar el estado destino. Usa APROBADO, RECHAZADO, EN REVISION o PENDIENTE.",
                    "ACCION_FORMATO_INVALIDO"
            ));
        }

        String codigo = codigoDetectado.get();
        SolicitudResponse solicitud;
        try {
            solicitud = workflowService.obtenerPorCodigoSeguimiento(codigo);
        } catch (ResourceNotFoundException ex) {
            return Optional.of(construirRespuesta(
                    "No encontre la solicitud " + codigo + ". Verifica el codigo de seguimiento.",
                    "SOLICITUD_NO_ENCONTRADA"
            ));
        }

        if (!puedeVerSolicitud(solicitud, usuario, rolUsuario, departamentoUsuario)) {
            return Optional.of(construirRespuesta(
                    "No tienes permisos para operar la solicitud " + codigo + ".",
                    "ACCION_SIN_PERMISO"
            ));
        }

        String comentario = extraerComentario(mensajeOriginal);
        String resumen = "Cambiar estado de " + codigo + " a " + nuevoEstado.name();

        PendingAction pendiente = new PendingAction(
                TipoAccionPendiente.CAMBIAR_ESTADO,
                solicitud.getId(),
                codigo,
                nuevoEstado,
                null,
                null,
                comentario,
                rolUsuario,
                departamentoUsuario,
                LocalDateTime.now(),
                resumen
        );

        return Optional.of(registrarAccionPendiente(usuario, pendiente, "ACCION_PENDIENTE_CAMBIO_ESTADO"));
    }

    private Optional<ChatIAResponse> prepararAccionReasignacion(
            String usuario,
            RolUsuario rolUsuario,
            String departamentoUsuario,
            String mensajeOriginal,
            String mensajeNormalizado,
            Optional<String> codigoDetectado
    ) {
        boolean trigger = contieneAlguno(mensajeNormalizado, "reasign", "transfer", "redirigir");
        if (!trigger) {
            return Optional.empty();
        }

        if (rolUsuario != RolUsuario.REVISOR && rolUsuario != RolUsuario.ADMINISTRADOR) {
            return Optional.of(construirRespuesta(
                    "No tienes permisos para reasignar departamentos desde chat.",
                    "ACCION_NO_PERMITIDA"
            ));
        }

        if (codigoDetectado.isEmpty()) {
            return Optional.of(construirRespuesta(
                    "Para reasignar debes indicar codigo, ejemplo: 'reasignar WF-2026-001 a Sistemas'.",
                    "ACCION_FORMATO_INVALIDO"
            ));
        }

        List<String> catalogo = workflowService.obtenerCatalogoDepartamentos();
        String destino = detectarDepartamentoDestino(mensajeNormalizado, catalogo);
        if (destino == null) {
            return Optional.of(construirRespuesta(
                    "No detecte departamento destino valido. Usa uno de: " + String.join(", ", catalogo),
                    "ACCION_FORMATO_INVALIDO"
            ));
        }

        String codigo = codigoDetectado.get();
        SolicitudResponse solicitud;
        try {
            solicitud = workflowService.obtenerPorCodigoSeguimiento(codigo);
        } catch (ResourceNotFoundException ex) {
            return Optional.of(construirRespuesta(
                    "No encontre la solicitud " + codigo + ". Verifica el codigo de seguimiento.",
                    "SOLICITUD_NO_ENCONTRADA"
            ));
        }

        if (!puedeVerSolicitud(solicitud, usuario, rolUsuario, departamentoUsuario)) {
            return Optional.of(construirRespuesta(
                    "No tienes permisos para reasignar " + codigo + " en tu alcance actual.",
                    "ACCION_SIN_PERMISO"
            ));
        }

        if (solicitud.getDepartamentoActual() != null
                && solicitud.getDepartamentoActual().equalsIgnoreCase(destino)) {
            return Optional.of(construirRespuesta(
                    "La solicitud " + codigo + " ya esta en el departamento " + destino + ".",
                    "ACCION_SIN_CAMBIO"
            ));
        }

        String comentario = extraerComentario(mensajeOriginal);
        String resumen = "Reasignar " + codigo + " de " + valor(solicitud.getDepartamentoActual()) + " a " + destino;

        PendingAction pendiente = new PendingAction(
                TipoAccionPendiente.REASIGNAR_DEPARTAMENTO,
                solicitud.getId(),
                codigo,
                null,
                destino,
                null,
                comentario,
                rolUsuario,
                departamentoUsuario,
                LocalDateTime.now(),
                resumen
        );

        return Optional.of(registrarAccionPendiente(usuario, pendiente, "ACCION_PENDIENTE_REASIGNACION"));
    }

    private Optional<ChatIAResponse> prepararAccionAsignacion(
            String usuario,
            RolUsuario rolUsuario,
            String departamentoUsuario,
            String mensajeOriginal,
            String mensajeNormalizado,
            Optional<String> codigoDetectado
    ) {
        boolean trigger = contieneAlguno(mensajeNormalizado, "asignar")
                && !contieneAlguno(mensajeNormalizado, "reasign");
        if (!trigger) {
            return Optional.empty();
        }

        if (!rolUsuario.puedeRevisar()) {
            return Optional.of(construirRespuesta(
                    "No tienes permisos para asignar usuarios desde chat.",
                    "ACCION_NO_PERMITIDA"
            ));
        }

        if (codigoDetectado.isEmpty()) {
            return Optional.of(construirRespuesta(
                    "Para asignar usuario debes indicar codigo, ejemplo: 'asignar WF-2026-001 a maria.revisora'.",
                    "ACCION_FORMATO_INVALIDO"
            ));
        }

        String codigo = codigoDetectado.get();

        Optional<String> usuarioAsignado = extraerUsuarioAsignado(mensajeOriginal, codigo);
        if (usuarioAsignado.isEmpty()) {
            return Optional.of(construirRespuesta(
                    "No pude detectar el usuario a asignar. Formato sugerido: 'asignar WF-2026-001 a usuario.destino'.",
                    "ACCION_FORMATO_INVALIDO"
            ));
        }

        SolicitudResponse solicitud;
        try {
            solicitud = workflowService.obtenerPorCodigoSeguimiento(codigo);
        } catch (ResourceNotFoundException ex) {
            return Optional.of(construirRespuesta(
                    "No encontre la solicitud " + codigo + ". Verifica el codigo de seguimiento.",
                    "SOLICITUD_NO_ENCONTRADA"
            ));
        }

        if (!puedeVerSolicitud(solicitud, usuario, rolUsuario, departamentoUsuario)) {
            return Optional.of(construirRespuesta(
                    "No tienes permisos para asignar responsables en " + codigo + ".",
                    "ACCION_SIN_PERMISO"
            ));
        }

        String resumen = "Asignar " + codigo + " al usuario " + usuarioAsignado.get();

        PendingAction pendiente = new PendingAction(
                TipoAccionPendiente.ASIGNAR_USUARIO,
                solicitud.getId(),
                codigo,
                null,
                null,
                usuarioAsignado.get(),
                null,
                rolUsuario,
                departamentoUsuario,
                LocalDateTime.now(),
                resumen
        );

        return Optional.of(registrarAccionPendiente(usuario, pendiente, "ACCION_PENDIENTE_ASIGNACION"));
    }

    private SolicitudResponse ejecutarAccionPendiente(
            PendingAction pendiente,
            String usuario,
            RolUsuario rolUsuario,
            String departamentoUsuario
    ) {
        return switch (pendiente.tipo) {
            case CAMBIAR_ESTADO -> {
                CambiarEstadoRequest request = CambiarEstadoRequest.builder()
                        .nuevoEstado(pendiente.nuevoEstado)
                        .comentario(pendiente.comentario != null ? pendiente.comentario : "Accion ejecutada desde asistente IA")
                        .build();
                yield workflowService.cambiarEstado(
                        pendiente.solicitudId,
                        request,
                        usuario,
                        rolUsuario,
                        departamentoUsuario
                );
            }
            case REASIGNAR_DEPARTAMENTO -> {
                ReasignarDepartamentoRequest request = ReasignarDepartamentoRequest.builder()
                        .nuevoDepartamento(pendiente.nuevoDepartamento)
                        .comentario(pendiente.comentario != null ? pendiente.comentario : "Reasignacion ejecutada desde asistente IA")
                        .build();
                yield workflowService.reasignarDepartamento(
                        pendiente.solicitudId,
                        request,
                        usuario,
                        rolUsuario,
                        departamentoUsuario
                );
            }
            case ASIGNAR_USUARIO -> workflowService.asignarUsuario(
                    pendiente.solicitudId,
                    pendiente.usuarioAsignado,
                    usuario,
                    rolUsuario
            );
        };
    }

    private PendingAction obtenerAccionPendienteVigente(String usuario) {
        PendingAction pendiente = accionesPendientes.get(usuario);
        if (pendiente == null) {
            return null;
        }

        if (pendiente.creadaEn.plusMinutes(ACCION_TTL_MINUTOS).isBefore(LocalDateTime.now())) {
            accionesPendientes.remove(usuario);
            return null;
        }

        return pendiente;
    }

    private ChatIAResponse registrarAccionPendiente(String usuario, PendingAction pendiente, String intencion) {
        boolean reemplazada = obtenerAccionPendienteVigente(usuario) != null;
        accionesPendientes.put(usuario, pendiente);

        String prefijo = reemplazada
                ? "Se reemplazo la accion pendiente anterior.\n"
                : "Accion preparada.\n";

        String respuesta = prefijo + pendiente.resumen
                + "\nEscribe 'confirmar' para ejecutar o 'cancelar' para descartar.";

        return construirRespuesta(respuesta, intencion);
    }

    private boolean esComandoConfirmacion(String mensajeNormalizado) {
        return mensajeNormalizado.equals("confirmar")
                || mensajeNormalizado.equals("confirmar accion")
                || mensajeNormalizado.equals("ok confirmar")
                || mensajeNormalizado.equals("si confirmar");
    }

    private boolean esComandoCancelacion(String mensajeNormalizado) {
        return mensajeNormalizado.equals("cancelar")
                || mensajeNormalizado.equals("cancelar accion")
                || mensajeNormalizado.equals("descartar accion");
    }

    private String detectarDepartamentoDestino(String mensajeNormalizado, List<String> catalogo) {
        for (String departamento : catalogo) {
            if (mensajeNormalizado.contains(normalizarTexto(departamento))) {
                return departamento;
            }
        }
        return null;
    }

    private Optional<String> extraerUsuarioAsignado(String mensajeOriginal, String codigoSeguimiento) {
        String patronDirecto = "(?i)asignar(?:\\s+usuario)?\\s+"
                + Pattern.quote(codigoSeguimiento)
                + "\\s+a\\s+([a-zA-Z0-9._-]+)";
        Matcher directo = Pattern.compile(patronDirecto).matcher(mensajeOriginal);
        if (directo.find() && directo.group(1) != null && !directo.group(1).isBlank()) {
            return Optional.of(directo.group(1).trim());
        }

        String patronFallback = "(?i)"
                + Pattern.quote(codigoSeguimiento)
                + "\\s+a\\s+([a-zA-Z0-9._-]+)";
        Matcher fallback = Pattern.compile(patronFallback).matcher(mensajeOriginal);
        if (!fallback.find() || fallback.group(1) == null || fallback.group(1).isBlank()) {
            return Optional.empty();
        }

        return Optional.of(fallback.group(1).trim());
    }

    private String extraerComentario(String mensajeOriginal) {
        Matcher matcher = COMENTARIO_PATTERN.matcher(mensajeOriginal);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1).trim();
    }

    private ChatIAResponse procesarComoSolicitante(String usuario, String mensajeNormalizado) {
        if (contieneAlguno(mensajeNormalizado, "resumen", "estad", "cuantas", "mis solicitudes", "mis casos")) {
            return responderResumenSolicitante(usuario);
        }

        if (contieneAlguno(mensajeNormalizado, "recientes", "ultimas", "ultimos")) {
            return responderRecientesContextuales(usuario, RolUsuario.SOLICITANTE, null);
        }

        EstadoWorkflow estadoDetectado = detectarEstado(mensajeNormalizado);
        if (estadoDetectado != null) {
            return responderListadoPorEstado(usuario, RolUsuario.SOLICITANTE, null, estadoDetectado);
        }

        return responderResumenSolicitante(usuario);
    }

    private ChatIAResponse procesarComoRevisor(
            String usuario,
            String mensajeNormalizado,
            String departamentoUsuario,
            Optional<String> codigoDetectado
    ) {
        if (departamentoUsuario == null || departamentoUsuario.isBlank()) {
            return construirRespuesta(
                    "No puedo generar recomendaciones porque falta el departamento del revisor en el contexto.",
                    "CONTEXTO_INCOMPLETO_REVISOR"
            );
        }

        if (contieneAlguno(mensajeNormalizado, "reasign", "recomendacion", "sugerencia") && codigoDetectado.isPresent()) {
            return responderRecomendacionReasignacion(codigoDetectado.get(), usuario, RolUsuario.REVISOR, departamentoUsuario);
        }

        if (contieneAlguno(mensajeNormalizado, "cola", "prioridad", "siguiente", "atender", "pendientes")) {
            return responderColaRevisor(departamentoUsuario);
        }

        if (contieneAlguno(mensajeNormalizado, "resumen", "estad", "departamento")) {
            return responderResumenRevisor(departamentoUsuario);
        }

        EstadoWorkflow estadoDetectado = detectarEstado(mensajeNormalizado);
        if (estadoDetectado != null) {
            return responderListadoPorEstado(usuario, RolUsuario.REVISOR, departamentoUsuario, estadoDetectado);
        }

        return responderColaRevisor(departamentoUsuario);
    }

    private ChatIAResponse procesarComoAdministrador(String mensajeNormalizado) {
        if (contieneAlguno(mensajeNormalizado, "cuello", "atasco", "bottleneck")) {
            return responderCuelloBotellaAdmin();
        }

        if (contieneAlguno(mensajeNormalizado, "backlog", "pendientes", "prioridad", "siguiente")) {
            return responderBacklogAdmin();
        }

        if (contieneAlguno(mensajeNormalizado, "resumen", "kpi", "estad", "global", "dashboard")) {
            return responderResumenAdmin();
        }

        EstadoWorkflow estadoDetectado = detectarEstado(mensajeNormalizado);
        if (estadoDetectado != null) {
            return responderListadoPorEstado(null, RolUsuario.ADMINISTRADOR, null, estadoDetectado);
        }

        return responderResumenAdmin();
    }

    private ChatIAResponse responderAyudaPorRol(RolUsuario rolUsuario) {
        String respuesta = switch (rolUsuario) {
            case SOLICITANTE -> "Comandos: 'resumen', 'mis solicitudes recientes', 'pendientes', 'estado de WF-2026-001'.";
            case REVISOR -> "Comandos: 'cola', 'siguiente a atender', 'recomendacion WF-2026-001', 'aprobar WF-2026-001', 'reasignar WF-2026-001 a Sistemas'.";
            case ADMINISTRADOR -> "Comandos: 'resumen global', 'cuello de botella', 'backlog prioritario', 'aprobar WF-2026-001', 'asignar WF-2026-001 a maria.revisora'.";
        };

        return construirRespuesta(respuesta, "AYUDA");
    }

    private ChatIAResponse responderResumenSolicitante(String usuario) {
        List<SolicitudResponse> solicitudes = workflowService.listarPorUsuarioCreador(usuario);
        if (solicitudes.isEmpty()) {
            return construirRespuesta(
                    "No tienes solicitudes registradas por ahora.",
                    "RESUMEN_SOLICITANTE_VACIO"
            );
        }

        Map<EstadoWorkflow, Long> conteo = contarPorEstado(solicitudes);
        List<SolicitudResponse> recientes = solicitudes.stream().limit(3).toList();

        StringBuilder respuesta = new StringBuilder();
        respuesta.append("Resumen de tus solicitudes: ").append(solicitudes.size()).append(" en total. ");
        respuesta.append(resumenConteos(conteo));
        respuesta.append("\nUltimas solicitudes:");
        for (SolicitudResponse solicitud : recientes) {
            respuesta.append("\n").append(lineaSolicitud(solicitud));
        }

        return construirRespuesta(respuesta.toString(), "RESUMEN_SOLICITANTE");
    }

    private ChatIAResponse responderResumenRevisor(String departamentoUsuario) {
        List<SolicitudResponse> solicitudes = workflowService.listarPorDepartamento(departamentoUsuario);
        Map<EstadoWorkflow, Long> conteo = contarPorEstado(solicitudes);

        String respuesta = "Resumen de " + departamentoUsuario + ": " + solicitudes.size() + " solicitudes visibles. "
                + resumenConteos(conteo);

        return construirRespuesta(respuesta, "RESUMEN_REVISOR");
    }

    private ChatIAResponse responderResumenAdmin() {
        List<SolicitudResponse> solicitudes = workflowService.listarTodas();
        Map<EstadoWorkflow, Long> conteo = contarPorEstado(solicitudes);

        String respuesta = "Resumen global: " + solicitudes.size() + " solicitudes en sistema. "
                + resumenConteos(conteo);

        return construirRespuesta(respuesta, "RESUMEN_ADMIN");
    }

    private ChatIAResponse responderCuelloBotellaAdmin() {
        List<SolicitudResponse> solicitudes = workflowService.listarTodas();
        Map<EstadoWorkflow, Long> conteo = contarPorEstado(solicitudes);

        EstadoWorkflow mayorEstado = EstadoWorkflow.PENDIENTE;
        long mayorCantidad = -1;
        for (Map.Entry<EstadoWorkflow, Long> entry : conteo.entrySet()) {
            if (entry.getValue() > mayorCantidad) {
                mayorEstado = entry.getKey();
                mayorCantidad = entry.getValue();
            }
        }

        String respuesta = "Cuello de botella actual: " + mayorEstado.name() + " con " + mayorCantidad
                + " solicitudes. " + resumenConteos(conteo);

        return construirRespuesta(respuesta, "CUELLO_BOTELLA_ADMIN");
    }

    private ChatIAResponse responderBacklogAdmin() {
        List<SolicitudResponse> solicitudes = workflowService.listarTodas();

        List<SolicitudResponse> candidatas = solicitudes.stream()
                .filter(s -> s.getEstado() == EstadoWorkflow.PENDIENTE || s.getEstado() == EstadoWorkflow.EN_REVISION)
                .sorted(Comparator
                        .comparingInt((SolicitudResponse s) -> prioridadPeso(s.getPrioridad()))
                        .thenComparing(s -> s.getFechaCreacion() != null ? s.getFechaCreacion() : LocalDateTime.MAX))
                .limit(5)
                .toList();

        if (candidatas.isEmpty()) {
            return construirRespuesta(
                    "No hay backlog operativo en PENDIENTE o EN_REVISION.",
                    "BACKLOG_VACIO"
            );
        }

        StringBuilder respuesta = new StringBuilder("Top backlog sugerido por prioridad:");
        for (SolicitudResponse solicitud : candidatas) {
            respuesta.append("\n").append(lineaSolicitud(solicitud));
        }

        return construirRespuesta(respuesta.toString(), "BACKLOG_ADMIN");
    }

    private ChatIAResponse responderColaRevisor(String departamentoUsuario) {
        List<SolicitudResponse> solicitudes = workflowService.listarPorDepartamento(departamentoUsuario);

        List<SolicitudResponse> cola = solicitudes.stream()
                .filter(s -> s.getEstado() == EstadoWorkflow.PENDIENTE || s.getEstado() == EstadoWorkflow.EN_REVISION)
                .sorted(Comparator
                        .comparingInt((SolicitudResponse s) -> prioridadPeso(s.getPrioridad()))
                        .thenComparing(s -> s.getFechaCreacion() != null ? s.getFechaCreacion() : LocalDateTime.MAX))
                .toList();

        if (cola.isEmpty()) {
            return construirRespuesta(
                    "No tienes casos operativos pendientes en " + departamentoUsuario + ".",
                    "COLA_REVISOR_VACIA"
            );
        }

        StringBuilder respuesta = new StringBuilder();
        SolicitudResponse siguiente = cola.get(0);
        respuesta.append("Siguiente caso sugerido: ").append(lineaSolicitud(siguiente));
        respuesta.append("\nCola operativa (top 5):");

        cola.stream().limit(5).forEach(s -> respuesta.append("\n").append(lineaSolicitud(s)));

        return construirRespuesta(respuesta.toString(), "COLA_REVISOR");
    }

    private ChatIAResponse responderRecientesContextuales(String usuario, RolUsuario rolUsuario, String departamentoUsuario) {
        List<SolicitudResponse> solicitudes = obtenerSolicitudesContextuales(usuario, rolUsuario, departamentoUsuario);

        if (solicitudes.isEmpty()) {
            return construirRespuesta("No hay solicitudes para mostrar en tu contexto actual.", "SIN_DATOS_CONTEXTUALES");
        }

        List<SolicitudResponse> recientes = solicitudes.stream().limit(5).toList();

        StringBuilder respuesta = new StringBuilder("Solicitudes recientes:");
        for (SolicitudResponse solicitud : recientes) {
            respuesta.append("\n").append(lineaSolicitud(solicitud));
        }

        return construirRespuesta(respuesta.toString(), "LISTADO_RECIENTES");
    }

    private ChatIAResponse responderListadoPorEstado(
            String usuario,
            RolUsuario rolUsuario,
            String departamentoUsuario,
            EstadoWorkflow estado
    ) {
        List<SolicitudResponse> solicitudes = obtenerSolicitudesContextuales(usuario, rolUsuario, departamentoUsuario);

        List<SolicitudResponse> filtradas = solicitudes.stream()
                .filter(s -> s.getEstado() == estado)
                .limit(5)
                .toList();

        if (filtradas.isEmpty()) {
            return construirRespuesta(
                    "No hay solicitudes en estado " + estado.name() + " dentro de tu alcance.",
                    "LISTADO_ESTADO_VACIO"
            );
        }

        StringBuilder respuesta = new StringBuilder("Solicitudes en ").append(estado.name()).append(" (top 5):");
        for (SolicitudResponse solicitud : filtradas) {
            respuesta.append("\n").append(lineaSolicitud(solicitud));
        }

        return construirRespuesta(respuesta.toString(), "LISTADO_POR_ESTADO");
    }

    private ChatIAResponse responderDetallePorCodigo(
            String codigoSeguimiento,
            String usuario,
            RolUsuario rolUsuario,
            String departamentoUsuario
    ) {
        SolicitudResponse solicitud;
        try {
            solicitud = workflowService.obtenerPorCodigoSeguimiento(codigoSeguimiento);
        } catch (ResourceNotFoundException ex) {
            return construirRespuesta(
                    "No encontre una solicitud con codigo " + codigoSeguimiento + ".",
                    "CODIGO_NO_ENCONTRADO"
            );
        }

        if (!puedeVerSolicitud(solicitud, usuario, rolUsuario, departamentoUsuario)) {
            return construirRespuesta(
                    "No tienes permisos para consultar " + codigoSeguimiento + " en tu contexto actual.",
                    "SIN_PERMISO_CODIGO"
            );
        }

        String respuesta = "Detalle de " + codigoSeguimiento
                + ": estado=" + valor(solicitud.getEstado())
                + ", prioridad=" + valor(solicitud.getPrioridad())
                + ", departamento=" + valor(solicitud.getDepartamentoActual())
                + ", creador=" + valor(solicitud.getUsuarioCreador())
                + ", asignado=" + valor(solicitud.getUsuarioAsignado())
                + ", eventos=" + solicitud.getTotalEventos() + ".";

        return construirRespuesta(respuesta, "DETALLE_POR_CODIGO");
    }

    private ChatIAResponse responderRecomendacionReasignacion(
            String codigoSeguimiento,
            String usuario,
            RolUsuario rolUsuario,
            String departamentoUsuario
    ) {
        SolicitudResponse solicitud;
        try {
            solicitud = workflowService.obtenerPorCodigoSeguimiento(codigoSeguimiento);
        } catch (ResourceNotFoundException ex) {
            return construirRespuesta(
                    "No encontre una solicitud con codigo " + codigoSeguimiento + ".",
                    "CODIGO_NO_ENCONTRADO"
            );
        }

        if (!puedeVerSolicitud(solicitud, usuario, rolUsuario, departamentoUsuario)) {
            return construirRespuesta(
                    "No puedes consultar recomendacion para " + codigoSeguimiento + " porque esta fuera de tu alcance.",
                    "SIN_PERMISO_RECOMENDACION"
            );
        }

        Map<String, Object> recomendacion = workflowService.obtenerRecomendacionReasignacion(solicitud.getId());
        String departamentoActual = String.valueOf(recomendacion.getOrDefault("departamentoActual", "N/A"));
        String departamentoSugerido = String.valueOf(recomendacion.getOrDefault("departamentoSugerido", "N/A"));

        return construirRespuesta(
                "Recomendacion para " + codigoSeguimiento
                        + ": actual=" + departamentoActual
                        + ", sugerido=" + departamentoSugerido
                        + ".",
                "RECOMENDACION_REASIGNACION"
        );
    }

    private List<SolicitudResponse> obtenerSolicitudesContextuales(
            String usuario,
            RolUsuario rolUsuario,
            String departamentoUsuario
    ) {
        return switch (rolUsuario) {
            case SOLICITANTE -> workflowService.listarPorUsuarioCreador(usuario);
            case REVISOR -> workflowService.listarPorDepartamento(departamentoUsuario);
            case ADMINISTRADOR -> workflowService.listarTodas();
        };
    }

    private boolean puedeVerSolicitud(
            SolicitudResponse solicitud,
            String usuario,
            RolUsuario rolUsuario,
            String departamentoUsuario
    ) {
        if (rolUsuario == RolUsuario.ADMINISTRADOR) {
            return true;
        }

        if (rolUsuario == RolUsuario.SOLICITANTE) {
            return solicitud.getUsuarioCreador() != null
                    && solicitud.getUsuarioCreador().equalsIgnoreCase(usuario);
        }

        return solicitud.getDepartamentoActual() != null
                && departamentoUsuario != null
                && solicitud.getDepartamentoActual().equalsIgnoreCase(departamentoUsuario);
    }

    private Map<EstadoWorkflow, Long> contarPorEstado(List<SolicitudResponse> solicitudes) {
        Map<EstadoWorkflow, Long> conteo = new EnumMap<>(EstadoWorkflow.class);
        for (EstadoWorkflow estado : EstadoWorkflow.values()) {
            conteo.put(estado, 0L);
        }

        for (SolicitudResponse solicitud : solicitudes) {
            EstadoWorkflow estado = solicitud.getEstado();
            if (estado == null) {
                continue;
            }
            conteo.put(estado, conteo.getOrDefault(estado, 0L) + 1);
        }

        return conteo;
    }

    private String resumenConteos(Map<EstadoWorkflow, Long> conteo) {
        return "PENDIENTE=" + conteo.getOrDefault(EstadoWorkflow.PENDIENTE, 0L)
                + ", EN_REVISION=" + conteo.getOrDefault(EstadoWorkflow.EN_REVISION, 0L)
                + ", APROBADO=" + conteo.getOrDefault(EstadoWorkflow.APROBADO, 0L)
                + ", RECHAZADO=" + conteo.getOrDefault(EstadoWorkflow.RECHAZADO, 0L) + ".";
    }

    private String lineaSolicitud(SolicitudResponse solicitud) {
        return "- " + valor(solicitud.getCodigoSeguimiento())
                + " | " + valor(solicitud.getEstado())
                + " | " + valor(solicitud.getPrioridad())
                + " | " + truncar(valor(solicitud.getTitulo()), 52);
    }

    private String truncar(String texto, int maxLen) {
        if (texto == null) {
            return "N/A";
        }
        if (texto.length() <= maxLen) {
            return texto;
        }
        return texto.substring(0, maxLen - 3) + "...";
    }

    private EstadoWorkflow detectarEstado(String mensajeNormalizado) {
        if (contieneAlguno(mensajeNormalizado, "pendiente", "pendientes")) {
            return EstadoWorkflow.PENDIENTE;
        }
        if (contieneAlguno(mensajeNormalizado, "revision", "en revision")) {
            return EstadoWorkflow.EN_REVISION;
        }
        if (contieneAlguno(mensajeNormalizado, "aprobado", "aprobados", "aprobar")) {
            return EstadoWorkflow.APROBADO;
        }
        if (contieneAlguno(mensajeNormalizado, "rechazado", "rechazados", "rechazar")) {
            return EstadoWorkflow.RECHAZADO;
        }
        return null;
    }

    private int prioridadPeso(Prioridad prioridad) {
        if (prioridad == null) {
            return 99;
        }

        return switch (prioridad) {
            case URGENTE -> 0;
            case ALTA -> 1;
            case MEDIA -> 2;
            case BAJA -> 3;
        };
    }

    private Optional<String> extraerCodigoSeguimiento(String texto) {
        Matcher matcher = CODIGO_SEGUIMIENTO_PATTERN.matcher(texto);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group().toUpperCase(Locale.ROOT));
    }

    private String normalizarTexto(String texto) {
        String base = texto == null ? "" : texto;
        String sinAcentos = Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(Locale.ROOT).trim();
    }

    private boolean contieneAlguno(String texto, String... patrones) {
        for (String patron : patrones) {
            if (texto.contains(patron)) {
                return true;
            }
        }
        return false;
    }

    private String valor(Object value) {
        if (value == null) {
            return "N/A";
        }
        String asString = String.valueOf(value).trim();
        return asString.isEmpty() ? "N/A" : asString;
    }

    private ChatIAResponse construirRespuesta(String respuesta, String intencion) {
        return ChatIAResponse.builder()
                .respuesta(respuesta)
                .fecha(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .intencionDetectada(intencion)
                .build();
    }
}
