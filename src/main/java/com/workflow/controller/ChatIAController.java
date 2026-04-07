package com.workflow.controller;

import com.workflow.dto.request.ChatIARequest;
import com.workflow.dto.response.ApiResponse;
import com.workflow.dto.response.ChatIAResponse;
import com.workflow.service.ChatIAService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/chat-ia")
@RequiredArgsConstructor
@Tag(name = "Asistente IA", description = "Endpoints interactivos para comunicarse con el bot conversacional de Workflow")
public class ChatIAController {

    private final ChatIAService chatIAService;

    /**
     * Enviar mensaje al asistente de IA
     */
    @PostMapping("/preguntar")
    @Operation(summary = "Enviar consulta al Asistente IA", description = "Recibe un mensaje en texto plano del usuario y retorna una respuesta generada mediante IA.")
    public ResponseEntity<ApiResponse<ChatIAResponse>> enviarMensajeUsuario(
            @Valid @RequestBody ChatIARequest request) {

        log.info("POST /api/v1/chat-ia/preguntar - Usuario: {}", request.getUsuarioId());
        ChatIAResponse response = chatIAService.procesarMensaje(request);

        return ResponseEntity.ok(
                ApiResponse.ok("Respuesta IA generada con éxito", response)
        );
    }
}
