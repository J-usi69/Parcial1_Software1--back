package com.workflow.service.impl;

import com.workflow.domain.enums.RolUsuario;
import com.workflow.dto.request.ChatIARequest;
import com.workflow.dto.response.ChatIAResponse;
import com.workflow.service.ChatIAService;
import org.springframework.ai.chat.client.ChatClient;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ChatIAServiceImpl implements ChatIAService {

    // ==== SPRING AI INTEGRATION ====
    private final ChatClient chatClient;

    public ChatIAServiceImpl(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public ChatIAResponse procesarMensaje(ChatIARequest request, RolUsuario rolUsuario, String departamentoUsuario) {
        log.info("Procesando mensaje con Spring AI para usuario: {}", request.getUsuarioId());

        String contexto = String.format(
                "Eres el IA_CORE del sistema Workflow Departamental. " +
                        "El usuario actual es %s con rol %s en el departamento %s. " +
                        "Responde de forma profesional, conversacional, directa y rápida. " +
                        "IMPORTANTE: NUNCA uses Markdown, ni asteriscos, ni negritas. Usa solo texto plano y saltos de línea normales. " +
                        "Estás autorizado para proveer sugerencias, consejos de optimización y reasignación de personal de manera proactiva basados en los datos. No digas que no puedes. Usa tus Tools para leer datos si es necesario.",
                request.getUsuarioId(), rolUsuario, departamentoUsuario != null ? departamentoUsuario : "Global");

        try {
            // == MAGIA DE SPRING AI (Llamando a Gemini Pura) ==
            String respuestaIA = this.chatClient.prompt()
                    .system(contexto)
                    .user(request.getMensaje())
                    .functions("analizarColaDepartamentoTool", "reasignarTicketTool", "cambiarEstadoTicketTool", "analizarSistemaGlobalTool")
                    .call()
                    .content();

            return ChatIAResponse.builder()
                    .respuesta(respuestaIA)
                    .intencionDetectada("LLM_PROCESADO")
                    .fecha(java.time.LocalDateTime.now().toString())
                    .build();
        } catch (Exception e) {
            log.error("Error contactando a Gemini AI: ", e);
            return ChatIAResponse.builder()
                    .respuesta("Houston, tuvimos un problema conectando con el satélite Gemini: " + e.getMessage())
                    .intencionDetectada("ERROR_LLM")
                    .fecha(java.time.LocalDateTime.now().toString())
                    .build();
        }
    }
}
