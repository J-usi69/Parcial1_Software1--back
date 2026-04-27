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

        String baseContext = String.format(
                "Eres el IA_CORE del sistema Workflow Departamental. " +
                "El usuario actual es %s con rol %s en el departamento %s. " +
                "Responde de forma profesional, conversacional, directa y rápida. " +
                "IMPORTANTE: NUNCA uses Markdown, ni asteriscos, ni negritas. Usa texto plano. Usa tus Tools para buscar datos en tiempo real. ",
                request.getUsuarioId(), rolUsuario, departamentoUsuario != null ? departamentoUsuario : "Global");

        String roleInstructions = "";
        if (rolUsuario == RolUsuario.ADMINISTRADOR) {
            roleInstructions = "Como el usuario es ADMINISTRADOR: Háblale de tú a tú como líder. Sugiere optimizaciones, identifica cuellos de botella globales y reasigna personal si es necesario.";
        } else if (rolUsuario == RolUsuario.REVISOR) {
            roleInstructions = "Como el usuario es REVISOR: Enfoca tus respuestas SOLO en su departamento. No le des sugerencias globales ni consejos dirigidos a los administradores. Limítate a informarle sobre el estado de las tareas de su área.";
        } else {
            roleInstructions = "Como el usuario es SOLICITANTE: No incluyas sugerencias de optimización del sistema, cuellos de botella o reasignaciones (esas son tareas administrativas). Limítate a darle información general y ayudarle con dudas básicas del sistema.";
        }

        String contexto = baseContext + roleInstructions;

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
