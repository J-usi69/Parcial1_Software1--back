package com.workflow.config;

import com.workflow.domain.enums.EstadoWorkflow;
import com.workflow.domain.enums.Prioridad;
import com.workflow.domain.enums.RolUsuario;
import com.workflow.domain.model.SolicitudWorkflow;
import com.workflow.domain.model.Usuario;
import com.workflow.repository.SolicitudWorkflowRepository;
import com.workflow.repository.UsuarioRepository;
import com.workflow.service.CodigoSeguimientoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final SolicitudWorkflowRepository solicitudRepository;
    private final CodigoSeguimientoGenerator codigoGenerator;

    @Override
    public void run(String... args) throws Exception {
        log.info("INICIANDO FRESH SEED: Limpiando base de datos...");
        
        // Limpieza total para asegurar un estado fresco
        solicitudRepository.deleteAll();
        usuarioRepository.deleteAll();

        seedUsuarios();
        seedSolicitudes();

        log.info("FRESH SEED FINALIZADO. Entorno listo con datos de prueba.");
    }

    private void seedUsuarios() {
        List<Usuario> usuariosPorDefecto = List.of(
            // --- ADMINISTRADORES ---
            buildDefaultUser("admin", "admin", "Súper Administrador", RolUsuario.ADMINISTRADOR, "Sistemas"),
            buildDefaultUser("root", "root", "Root System User", RolUsuario.ADMINISTRADOR, "Sistemas"),

            // --- RECURSOS HUMANOS ---
            buildDefaultUser("revisor", "revisor", "Jefe RRHH Principal", RolUsuario.REVISOR, "Recursos Humanos"),
            buildDefaultUser("rrhh", "rrhh", "Analista de Talento", RolUsuario.REVISOR, "Recursos Humanos"),
            buildDefaultUser("claudia.rrhh", "claudia.rrhh", "Claudia Méndez", RolUsuario.REVISOR, "Recursos Humanos"),

            // --- SISTEMAS (TI) ---
            buildDefaultUser("sistemas", "sistemas", "Lead DevOps Revisor", RolUsuario.REVISOR, "Sistemas"),
            buildDefaultUser("ti", "ti", "Soporte Técnico Revisor", RolUsuario.REVISOR, "Sistemas"),
            buildDefaultUser("maria.revisora", "maria.revisora", "Maria TI Expert", RolUsuario.REVISOR, "Sistemas"),
            buildDefaultUser("dev", "dev", "Developer Senior", RolUsuario.SOLICITANTE, "Sistemas"),

            // --- VENTAS ---
            buildDefaultUser("ventas", "ventas", "Director Comercial", RolUsuario.REVISOR, "Ventas"),
            buildDefaultUser("comercial", "comercial", "Ejecutivo de Cuentas", RolUsuario.REVISOR, "Ventas"),
            buildDefaultUser("pedro.ventas", "pedro.ventas", "Pedro Senior Sales", RolUsuario.SOLICITANTE, "Ventas"),
            
            // --- SOLICITANTES GENERALES ---
            buildDefaultUser("solicitante", "solicitante", "Usuario Estándar Juan", RolUsuario.SOLICITANTE, "Ventas"),
            buildDefaultUser("ana", "ana", "Ana Martínez (Ventas)", RolUsuario.SOLICITANTE, "Ventas"),
            buildDefaultUser("carlos", "carlos", "Carlos Ruiz (RRHH)", RolUsuario.SOLICITANTE, "Recursos Humanos")
        );

        for (Usuario user : usuariosPorDefecto) {
            usuarioRepository.save(user);
            log.info("Usuario creado: {} [{}]", user.getUsername(), user.getRol());
        }
    }

    private void seedSolicitudes() {
        if (solicitudRepository.count() > 0) {
            log.info("Ya existen solicitudes, saltando seeding de flujo.");
            return;
        }

        // 1. Solicitud PENDIENTE en Sistemas
        crearYGuardarSolicitud(
            "Acceso a Servidor Producción",
            "Se requiere acceso SSH al servidor de base de datos para realizar tareas de mantenimiento programadas.",
            Prioridad.ALTA, "Sistemas", "solicitante"
        );

        // 2. Solicitud EN REVISIÓN en RRHH
        crearYGuardarSolicitud(
            "Aprobación de Vacaciones Q4",
            "Solicitud de vacaciones para el equipo de ventas correspondiente al último trimestre del año.",
            Prioridad.MEDIA, "Recursos Humanos", "solicitante"
        );

        // 3. Solicitud URGENTE en Sistemas (Vencida/Próxima a vencer)
        crearYGuardarSolicitud(
            "CRÍTICO: Caída de VPN Regional",
            "La conexión VPN con la sucursal Norte ha fallado. Los usuarios no pueden acceder al ERP.",
            Prioridad.URGENTE, "Sistemas", "pedro.ventas"
        );

        // 4. Solicitud en Ventas
        crearYGuardarSolicitud(
            "Revisión de Contrato Cliente VIP",
            "Necesitamos validar los términos y condiciones del nuevo contrato con la empresa ACME Corp.",
            Prioridad.ALTA, "Ventas", "solicitante"
        );

        log.info("Se han creado 4 solicitudes iniciales de prueba.");
    }

    private void crearYGuardarSolicitud(String titulo, String desc, Prioridad prio, String depto, String creador) {
        String codigo = codigoGenerator.generarCodigo();
        SolicitudWorkflow s = SolicitudWorkflow.builder()
            .codigoSeguimiento(codigo)
            .titulo(titulo)
            .descripcion(desc)
            .prioridad(prio)
            .estado(EstadoWorkflow.PENDIENTE)
            .departamentoActual(depto)
            .usuarioCreador(creador)
            .fechaCreacion(LocalDateTime.now())
            .fechaLimiteAtencion(LocalDateTime.now().plusHours(24))
            .build();
        
        s.registrarTransicion(null, EstadoWorkflow.PENDIENTE, creador, "SOLICITANTE", "Creado automáticamente por sistema");
        solicitudRepository.save(s);
    }

    private Usuario buildDefaultUser(String username, String password, String nombre, RolUsuario rol, String depto) {
        return Usuario.builder()
            .username(username)
            .password(password)
            .nombreCompleto(nombre)
            .rol(rol)
            .departamento(depto)
            .fechaCreacion(LocalDateTime.now())
            .build();
    }
}
