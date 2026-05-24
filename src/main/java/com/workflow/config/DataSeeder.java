package com.workflow.config;

import com.workflow.domain.enums.EstadoWorkflow;
import com.workflow.domain.enums.Prioridad;
import com.workflow.domain.enums.RolUsuario;
import com.workflow.domain.model.Departamento;
import com.workflow.domain.model.SolicitudWorkflow;
import com.workflow.domain.model.Usuario;
import com.workflow.domain.model.Documento;
import com.workflow.domain.model.WorkflowDefinition;
import com.workflow.repository.DepartamentoRepository;
import com.workflow.repository.SolicitudWorkflowRepository;
import com.workflow.repository.UsuarioRepository;
import com.workflow.repository.DocumentoRepository;
import com.workflow.repository.WorkflowDefinitionRepository;
import com.workflow.service.CodigoSeguimientoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final SolicitudWorkflowRepository solicitudRepository;
    private final DepartamentoRepository departamentoRepository;
    private final DocumentoRepository documentoRepository;
    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final CodigoSeguimientoGenerator codigoGenerator;

    private static final String PROCUREMENT_XML = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<bpmn:definitions xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
            "xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" " +
            "xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\" " +
            "xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\" " +
            "xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\" " +
            "xmlns:wf=\"http://workflow.com/schema\" id=\"Definitions_Procurement\" " +
            "targetNamespace=\"http://bpmn.io/schema/bpmn\">\n" +
            "  <bpmn:process id=\"procurement-workflow\" name=\"Proceso de Compras\" isExecutable=\"true\">\n" +
            "    <bpmn:startEvent id=\"StartEvent_1\" name=\"Inicio\">\n" +
            "      <bpmn:outgoing>Flow_1</bpmn:outgoing>\n" +
            "    </bpmn:startEvent>\n" +
            "    <bpmn:userTask id=\"Activity_Pendiente\" name=\"Bandeja de Entrada / Pendientes\" wf:departamento=\"Sistemas\">\n" +
            "      <bpmn:incoming>Flow_1</bpmn:incoming>\n" +
            "      <bpmn:outgoing>Flow_2</bpmn:outgoing>\n" +
            "    </bpmn:userTask>\n" +
            "    <bpmn:userTask id=\"Activity_Sys1\" name=\"Analizar Requerimientos\" wf:departamento=\"Sistemas\" " +
            "wf:form='[{\"name\":\"monto\",\"label\":\"Presupuesto Est. (USD)\",\"type\":\"number\",\"required\":true},{\"name\":\"motivo\",\"label\":\"Justificación Técnica\",\"type\":\"text\",\"required\":false}]'>\n" +
            "      <bpmn:incoming>Flow_2</bpmn:incoming>\n" +
            "      <bpmn:outgoing>Flow_3</bpmn:outgoing>\n" +
            "    </bpmn:userTask>\n" +
            "    <bpmn:userTask id=\"Activity_Venta1\" name=\"Aprobación Presupuesto\" wf:departamento=\"Ventas\">\n" +
            "      <bpmn:incoming>Flow_3</bpmn:incoming>\n" +
            "      <bpmn:outgoing>Flow_4</bpmn:outgoing>\n" +
            "    </bpmn:userTask>\n" +
            "    <bpmn:userTask id=\"Activity_RRHH1\" name=\"Firma de Contrato\" wf:departamento=\"Recursos Humanos\">\n" +
            "      <bpmn:incoming>Flow_4</bpmn:incoming>\n" +
            "      <bpmn:outgoing>Flow_5</bpmn:outgoing>\n" +
            "    </bpmn:userTask>\n" +
            "    <bpmn:endEvent id=\"EndEvent_1\" name=\"Fin\">\n" +
            "      <bpmn:incoming>Flow_5</bpmn:incoming>\n" +
            "    </bpmn:endEvent>\n" +
            "    <bpmn:sequenceFlow id=\"Flow_1\" sourceRef=\"StartEvent_1\" targetRef=\"Activity_Pendiente\" />\n" +
            "    <bpmn:sequenceFlow id=\"Flow_2\" sourceRef=\"Activity_Pendiente\" targetRef=\"Activity_Sys1\" />\n" +
            "    <bpmn:sequenceFlow id=\"Flow_3\" sourceRef=\"Activity_Sys1\" targetRef=\"Activity_Venta1\" />\n" +
            "    <bpmn:sequenceFlow id=\"Flow_4\" sourceRef=\"Activity_Venta1\" targetRef=\"Activity_RRHH1\" />\n" +
            "    <bpmn:sequenceFlow id=\"Flow_5\" sourceRef=\"Activity_RRHH1\" targetRef=\"EndEvent_1\" />\n" +
            "  </bpmn:process>\n" +
            "  <bpmndi:BPMNDiagram id=\"BPMNDiagram_1\">\n" +
            "    <bpmndi:BPMNPlane id=\"BPMNPlane_1\" bpmnElement=\"procurement-workflow\">\n" +
            "      <bpmndi:BPMNShape id=\"_BPMNShape_StartEvent_2\" bpmnElement=\"StartEvent_1\">\n" +
            "        <dc:Bounds x=\"152\" y=\"102\" width=\"36\" height=\"36\" />\n" +
            "      </bpmndi:BPMNShape>\n" +
            "      <bpmndi:BPMNShape id=\"Activity_Pendiente_di\" bpmnElement=\"Activity_Pendiente\">\n" +
            "        <dc:Bounds x=\"240\" y=\"80\" width=\"100\" height=\"80\" />\n" +
            "      </bpmndi:BPMNShape>\n" +
            "      <bpmndi:BPMNShape id=\"Activity_Sys1_di\" bpmnElement=\"Activity_Sys1\">\n" +
            "        <dc:Bounds x=\"400\" y=\"80\" width=\"100\" height=\"80\" />\n" +
            "      </bpmndi:BPMNShape>\n" +
            "      <bpmndi:BPMNShape id=\"Activity_Venta1_di\" bpmnElement=\"Activity_Venta1\">\n" +
            "        <dc:Bounds x=\"560\" y=\"80\" width=\"100\" height=\"80\" />\n" +
            "      </bpmndi:BPMNShape>\n" +
            "      <bpmndi:BPMNShape id=\"Activity_RRHH1_di\" bpmnElement=\"Activity_RRHH1\">\n" +
            "        <dc:Bounds x=\"720\" y=\"80\" width=\"100\" height=\"80\" />\n" +
            "      </bpmndi:BPMNShape>\n" +
            "      <bpmndi:BPMNShape id=\"EndEvent_1_di\" bpmnElement=\"EndEvent_1\">\n" +
            "        <dc:Bounds x=\"882\" y=\"102\" width=\"36\" height=\"36\" />\n" +
            "      </bpmndi:BPMNShape>\n" +
            "      <bpmndi:BPMNEdge id=\"Flow_1_di\" bpmnElement=\"Flow_1\">\n" +
            "        <di:waypoint x=\"188\" y=\"120\" />\n" +
            "        <di:waypoint x=\"240\" y=\"120\" />\n" +
            "      </bpmndi:BPMNEdge>\n" +
            "      <bpmndi:BPMNEdge id=\"Flow_2_di\" bpmnElement=\"Flow_2\">\n" +
            "        <di:waypoint x=\"340\" y=\"120\" />\n" +
            "        <di:waypoint x=\"400\" y=\"120\" />\n" +
            "      </bpmndi:BPMNEdge>\n" +
            "      <bpmndi:BPMNEdge id=\"Flow_3_di\" bpmnElement=\"Flow_3\">\n" +
            "        <di:waypoint x=\"500\" y=\"120\" />\n" +
            "        <di:waypoint x=\"560\" y=\"120\" />\n" +
            "      </bpmndi:BPMNEdge>\n" +
            "      <bpmndi:BPMNEdge id=\"Flow_4_di\" bpmnElement=\"Flow_4\">\n" +
            "        <di:waypoint x=\"660\" y=\"120\" />\n" +
            "        <di:waypoint x=\"720\" y=\"120\" />\n" +
            "      </bpmndi:BPMNEdge>\n" +
            "      <bpmndi:BPMNEdge id=\"Flow_5_di\" bpmnElement=\"Flow_5\">\n" +
            "        <di:waypoint x=\"820\" y=\"120\" />\n" +
            "        <di:waypoint x=\"882\" y=\"120\" />\n" +
            "      </bpmndi:BPMNEdge>\n" +
            "    </bpmndi:BPMNPlane>\n" +
            "  </bpmndi:BPMNDiagram>\n" +
            "</bpmn:definitions>";

    @Override
    public void run(String... args) throws Exception {
        if (usuarioRepository.count() > 0) {
            log.info("CONEXIÓN A BASE DE DATOS EXITOSA: Sembrado omitido.");
            return;
        }

        log.info("INICIANDO FRESH SEED: Limpiando base de datos...");
        solicitudRepository.deleteAll();
        usuarioRepository.deleteAll();
        departamentoRepository.deleteAll();
        documentoRepository.deleteAll();
        workflowDefinitionRepository.deleteAll();

        seedDepartamentos();
        seedUsuarios();
        seedWorkflowDefinitions();
        seedSolicitudes();

        log.info("FRESH SEED FINALIZADO. Entorno listo.");
    }

    private void seedDepartamentos() {
        departamentoRepository.saveAll(List.of(
                buildDepto("Sistemas", "Departamento de tecnología"),
                buildDepto("Recursos Humanos", "Gestión de personal"),
                buildDepto("Ventas", "Área comercial")));
    }

    private void seedUsuarios() {
        usuarioRepository.saveAll(List.of(
                buildDefaultUser("admin", "admin", "Súper Administrador", RolUsuario.ADMINISTRADOR, "Sistemas"),
                buildDefaultUser("revisor", "revisor", "Jefe RRHH", RolUsuario.REVISOR, "Recursos Humanos"),
                buildDefaultUser("ti", "ti", "Soporte Técnico", RolUsuario.REVISOR, "Sistemas"),
                buildDefaultUser("ventas", "ventas", "Director Comercial", RolUsuario.REVISOR, "Ventas"),
                buildDefaultUser("solicitante", "solicitante", "Juan Solicitante", RolUsuario.SOLICITANTE, "Sistemas")));
    }

    private void seedSolicitudes() {
        String flowId = "procurement-workflow";

        // 1. Sistemas - Vinculado a Activity_Sys1
        SolicitudWorkflow s1 = crearSolicitudCompleta(
                "Compra de Servidores Cloud",
                "Se requiere ampliar capacidad de cómputo para fin de año.",
                Prioridad.URGENTE, "Sistemas", "solicitante", EstadoWorkflow.EN_REVISION, "ti",
                flowId, "Activity_Sys1", "Analizar Requerimientos");

        // 2. Ventas - Vinculado a Activity_Venta1
        SolicitudWorkflow s2 = crearSolicitudCompleta(
                "Renovación Licencias CRM",
                "Pago anual del software de gestión comercial.",
                Prioridad.ALTA, "Ventas", "solicitante", EstadoWorkflow.EN_REVISION, "ventas",
                flowId, "Activity_Venta1", "Aprobación Presupuesto");

        // 3. RRHH - Vinculado a Activity_RRHH1
        SolicitudWorkflow s3 = crearSolicitudCompleta(
                "Seguro Médico Colectivo",
                "Renovación de la póliza de salud para empleados.",
                Prioridad.MEDIA, "Recursos Humanos", "solicitante", EstadoWorkflow.EN_REVISION, "revisor",
                flowId, "Activity_RRHH1", "Firma de Contrato");

        // 4. Pendiente sin asignar
        crearSolicitudCompleta(
                "Mantenimiento AC Servidores",
                "Reparación del aire acondicionado central.",
                Prioridad.BAJA, "Sistemas", "solicitante", EstadoWorkflow.PENDIENTE, null,
                null, null, null);

        // Documentos
        seedDocumentoColaborativo(s1, "Presupuesto_Servidores", "Costos Cloud", "Detalle de instancias AWS.", "solicitante");
        seedDocumentoColaborativo(s3, "Contrato_Seguro", "Términos Póliza", "Coberturas médicas 2026.", "revisor");

        // Sincronizar el XML con los códigos de los tickets creados
        actualizarXmlConSolicitudes(flowId, s1, s2, s3);
    }

    private void actualizarXmlConSolicitudes(String key, SolicitudWorkflow sys, SolicitudWorkflow vta, SolicitudWorkflow rrhh) {
        workflowDefinitionRepository.findByKey(key).ifPresent(def -> {
            String xml = def.getXml();
            xml = xml.replace("id=\"Activity_Sys1\" name=\"Analizar Requerimientos\"", "id=\"Activity_Sys1\" name=\"Analizar Requerimientos\" wf:solicitudes=\"" + sys.getCodigoSeguimiento() + "\"");
            xml = xml.replace("id=\"Activity_Venta1\" name=\"Aprobación Presupuesto\"", "id=\"Activity_Venta1\" name=\"Aprobación Presupuesto\" wf:solicitudes=\"" + vta.getCodigoSeguimiento() + "\"");
            xml = xml.replace("id=\"Activity_RRHH1\" name=\"Firma de Contrato\"", "id=\"Activity_RRHH1\" name=\"Firma de Contrato\" wf:solicitudes=\"" + rrhh.getCodigoSeguimiento() + "\"");
            def.setXml(xml);
            workflowDefinitionRepository.save(def);
        });
    }

    private SolicitudWorkflow crearSolicitudCompleta(String titulo, String desc, Prioridad prio, String depto, String creador, 
                                                   EstadoWorkflow estadoFinal, String revisor, String flowId, String tareaId, String tareaNombre) {
        String codigo = codigoGenerator.generarCodigo();
        SolicitudWorkflow s = SolicitudWorkflow.builder()
                .codigoSeguimiento(codigo).titulo(titulo).descripcion(desc).prioridad(prio).estado(EstadoWorkflow.PENDIENTE)
                .departamentoActual(depto).usuarioCreador(creador).workflowDefinitionId(flowId).tareaActualId(tareaId).tareaActualNombre(tareaNombre)
                .fechaCreacion(LocalDateTime.now()).fechaLimiteAtencion(LocalDateTime.now().plusHours(48)).build();

        s.registrarTransicion(null, EstadoWorkflow.PENDIENTE, creador, "SOLICITANTE", "Solicitud registrada.");
        if (estadoFinal != EstadoWorkflow.PENDIENTE) {
            s.setUsuarioAsignado(revisor);
            s.registrarTransicion(EstadoWorkflow.PENDIENTE, EstadoWorkflow.EN_REVISION, revisor, "REVISOR", "Iniciada revisión en etapa " + tareaNombre);
        }
        return solicitudRepository.save(s);
    }

    private Departamento buildDepto(String nombre, String desc) {
        return Departamento.builder().nombre(nombre).descripcion(desc).creadoPor("system").activo(true).fechaCreacion(LocalDateTime.now()).build();
    }

    private Usuario buildDefaultUser(String username, String password, String nombre, RolUsuario rol, String depto) {
        String avatarUrl = "https://ui-avatars.com/api/?name=" + nombre.replace(" ", "+") + "&background=random&color=fff&size=128";
        return Usuario.builder().username(username).password(password).nombreCompleto(nombre).rol(rol).departamento(depto).avatarUrl(avatarUrl).fechaCreacion(LocalDateTime.now()).build();
    }

    private void seedDocumentoColaborativo(SolicitudWorkflow solicitud, String nombre, String desc, String contenido, String creador) {
        Documento documento = Documento.builder()
                .solicitudId(solicitud.getId()).nombre(nombre).descripcion(desc).tipo("COLLABORATIVE").versionActual(1).creadoPor(creador)
                .fechaCreacion(LocalDateTime.now()).fechaActualizacion(LocalDateTime.now()).contenidoColaborativo(contenido).build();
        documentoRepository.save(documento);
    }

    private void seedWorkflowDefinitions() {
        workflowDefinitionRepository.save(WorkflowDefinition.builder()
                .key("procurement-workflow").name("Proceso de Compras").description("Gestión de adquisiciones").xml(PROCUREMENT_XML)
                .editadoPor("admin").departamentoEditor("Sistemas").version(1).build());
    }
}
