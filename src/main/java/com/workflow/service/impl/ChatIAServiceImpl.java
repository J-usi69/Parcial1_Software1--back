package com.workflow.service.impl;

import com.workflow.dto.request.ChatIARequest;
import com.workflow.dto.response.ChatIAResponse;
import com.workflow.service.ChatIAService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatIAServiceImpl implements ChatIAService {

    // private final OpenAiClient openAiClient; // A futuro: cliente feign/webclient para la IA

    @Override
    public ChatIAResponse procesarMensaje(ChatIARequest request) {
        log.info("Procesando mensaje de Chat IA para usuario: {}", request.getUsuarioId());
        
        // Mock de comportamiento para el futuro sistema
        // Aquí iría la lógica donde la IA interpreta el "request.getMensaje()",
        // hace consultas a la BD (ej: workflowService.listarPorUsuarioCreador(request.getUsuarioId()))
        // y arma una respuesta en lenguaje natural.

        String respuestaGenerada = "¡Hola! He recibido tu mensaje: '" + request.getMensaje() + 
                                   "'. Actualmente mi módulo de IA se encuentra en desarrollo estructural. " +
                                   "¡Pronto podré ayudarte a consultar tus solicitudes de forma automática!";

        return ChatIAResponse.builder()
                .respuesta(respuestaGenerada)
                .fecha(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .intencionDetectada("SALUDO_O_PENDIENTE_IMPLEMENTACION")
                .build();
    }
}
