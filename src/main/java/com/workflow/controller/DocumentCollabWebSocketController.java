package com.workflow.controller;

import com.workflow.dto.request.DocumentCollabMessage;
import com.workflow.service.DocumentoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Controlador de WebSockets para colaboración en tiempo real sobre documentos en línea.
 * Implementa:
 * 1. Persistencia diferida (Debounced Auto-Save) para eliminar lag.
 * 2. Seguimiento de presencia y colaboradores activos por documento (JOIN, LEAVE, HEARTBEAT).
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class DocumentCollabWebSocketController {

    private final DocumentoService documentoService;
    private final SimpMessagingTemplate messagingTemplate;

    // Mapa en memoria para almacenar las tareas de guardado pendiente (Debounce)
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingSaves = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    // Registro de presencia a nivel de documento
    // Key: docId, Value: Map de username -> último latido (timestamp)
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Long>> docActiveUsers = new ConcurrentHashMap<>();

    @MessageMapping("/document/{docId}/collab")
    @SendTo("/topic/document/{docId}/collab")
    public DocumentCollabMessage handleCollabMessage(
            @DestinationVariable String docId,
            DocumentCollabMessage message
    ) {
        log.debug("[WebSocket] Mensaje recibido para doc {}: Tipo: {}, Autor: {}", docId, message.getType(), message.getAuthor());
        if (message.getTimestamp() == null) {
            message.setTimestamp(System.currentTimeMillis());
        }

        String author = message.getAuthor();
        String type = message.getType();

        // 1. Gestionar Presencia Activa del Colaborador
        if (author != null && !author.isBlank()) {
            if ("LEAVE".equalsIgnoreCase(type)) {
                removeActiveUser(docId, author);
            } else {
                // Si es JOIN, EDIT, CURSOR o COMMENT, actualizamos o registramos su presencia
                registerActiveUser(docId, author);
            }
        }

        // 2. Gestionar Auto-guardado Diferido (Debounced Save)
        if ("EDIT".equalsIgnoreCase(type) && message.getContent() != null) {
            debounceSaveToDatabase(docId, message.getContent(), author);
        }

        return message;
    }

    /**
     * Registra a un usuario como visualizador/editor activo de un documento específico.
     */
    private void registerActiveUser(String docId, String username) {
        ConcurrentHashMap<String, Long> users = docActiveUsers.computeIfAbsent(docId, k -> new ConcurrentHashMap<>());
        boolean isNew = !users.containsKey(username);
        users.put(username, System.currentTimeMillis());
        
        if (isNew) {
            log.info("[Doc Collab] Usuario '{}' entró al documento {}", username, docId);
            broadcastActiveUsers(docId);
        }
    }

    /**
     * Remueve de forma explícita a un usuario de la lista de colaboradores activos.
     */
    private void removeActiveUser(String docId, String username) {
        ConcurrentHashMap<String, Long> users = docActiveUsers.get(docId);
        if (users != null) {
            if (users.remove(username) != null) {
                log.info("[Doc Collab] Usuario '{}' salió del documento {}", username, docId);
                broadcastActiveUsers(docId);
            }
        }
    }

    /**
     * Transmite la lista consolidada de colaboradores activos en el documento por WebSockets.
     */
    private void broadcastActiveUsers(String docId) {
        ConcurrentHashMap<String, Long> users = docActiveUsers.get(docId);
        List<String> activeList = users != null ? new ArrayList<>(users.keySet()) : List.of();
        messagingTemplate.convertAndSend("/topic/document/" + docId + "/active-users", activeList);
    }

    /**
     * Tarea programada cada 5 segundos para limpiar usuarios cuya sesión expiró
     * (por ejemplo, cerraron la pestaña del navegador sin enviar un mensaje 'LEAVE').
     */
    @Scheduled(fixedDelay = 5000)
    public void cleanExpiredDocumentUsers() {
        long now = System.currentTimeMillis();
        docActiveUsers.forEach((docId, users) -> {
            // Un usuario expira si no ha enviado latidos o mensajes en los últimos 15 segundos
            boolean removedAny = users.entrySet().removeIf(entry -> (now - entry.getValue()) > 15000);
            if (removedAny) {
                log.debug("[Doc Collab] Limpiados usuarios inactivos en doc {}. Retransmitiendo...", docId);
                broadcastActiveUsers(docId);
            }
        });
    }

    /**
     * Guarda de forma diferida el contenido del documento en MongoDB.
     */
    private void debounceSaveToDatabase(String docId, String content, String author) {
        ScheduledFuture<?> scheduledTask = pendingSaves.remove(docId);
        if (scheduledTask != null && !scheduledTask.isDone()) {
            scheduledTask.cancel(false);
        }

        ScheduledFuture<?> newTask = scheduler.schedule(() -> {
            try {
                log.info("[Auto-Save] Guardando estado consolidado para documento {} en MongoDB (Disparado por {})", docId, author);
                documentoService.actualizarContenidoColaborativo(docId, content, author);
                
                messagingTemplate.convertAndSend("/topic/document/" + docId + "/status", 
                    Map.of("status", "SAVED", "timestamp", System.currentTimeMillis()));
                
            } catch (Exception e) {
                log.error("[Auto-Save] Fallo crítico al guardar documento colaborativo {}", docId, e);
            } finally {
                pendingSaves.remove(docId);
            }
        }, 2500, TimeUnit.MILLISECONDS);

        pendingSaves.put(docId, newTask);
    }
}
