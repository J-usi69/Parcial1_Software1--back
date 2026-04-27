package com.workflow.config;

import com.workflow.domain.enums.EstadoWorkflow;
import com.workflow.domain.enums.Prioridad;
import com.workflow.domain.enums.RolUsuario;
import com.workflow.domain.model.Departamento;
import com.workflow.domain.model.SolicitudWorkflow;
import com.workflow.domain.model.Usuario;
import com.workflow.repository.DepartamentoRepository;
import com.workflow.repository.SolicitudWorkflowRepository;
import com.workflow.repository.UsuarioRepository;
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
        private final CodigoSeguimientoGenerator codigoGenerator;

        @Override
        public void run(String... args) throws Exception {
                log.info("INICIANDO FRESH SEED: Limpiando base de datos...");

                // 1. Limpieza total para asegurar un estado fresco
                solicitudRepository.deleteAll();
                usuarioRepository.deleteAll();
                departamentoRepository.deleteAll();

                // 2. Población en orden de dependencia
                seedDepartamentos();
                seedUsuarios();
                seedSolicitudes();

                log.info("FRESH SEED FINALIZADO. Entorno listo con datos de prueba.");
        }

        private void seedDepartamentos() {
                log.info("Sembrando departamentos iniciales...");
                List<Departamento> iniciales = List.of(
                                buildDepto("Sistemas", "Departamento central de tecnología y soporte"),
                                buildDepto("Recursos Humanos", "Gestión de talento humano y personal"),
                                buildDepto("Ventas", "Área comercial y atención al cliente"));
                departamentoRepository.saveAll(iniciales);
        }

        private void seedUsuarios() {
                List<Usuario> usuariosPorDefecto = List.of(
                                // --- ADMINISTRADORES ---
                                buildDefaultUser("admin", "admin", "Súper Administrador", RolUsuario.ADMINISTRADOR,
                                                "Sistemas"),
                                buildDefaultUser("root", "root", "Root System User", RolUsuario.ADMINISTRADOR,
                                                "Sistemas"),

                                // --- RECURSOS HUMANOS ---
                                buildDefaultUser("revisor", "revisor", "Jefe RRHH Principal", RolUsuario.REVISOR,
                                                "Recursos Humanos"),
                                buildDefaultUser("rrhh", "rrhh", "Analista de Talento", RolUsuario.REVISOR,
                                                "Recursos Humanos"),
                                buildDefaultUser("claudia.rrhh", "claudia.rrhh", "Claudia Méndez", RolUsuario.REVISOR,
                                                "Recursos Humanos"),

                                // --- SISTEMAS (TI) ---
                                buildDefaultUser("sistemas", "sistemas", "Lead DevOps Revisor", RolUsuario.REVISOR,
                                                "Sistemas"),
                                buildDefaultUser("ti", "ti", "Soporte Técnico Revisor", RolUsuario.REVISOR, "Sistemas"),
                                buildDefaultUser("maria.revisora", "maria.revisora", "Maria TI Expert",
                                                RolUsuario.REVISOR, "Sistemas"),
                                buildDefaultUser("dev", "dev", "Developer Senior", RolUsuario.SOLICITANTE, "Sistemas"),

                                // --- VENTAS ---
                                buildDefaultUser("ventas", "ventas", "Director Comercial", RolUsuario.REVISOR,
                                                "Ventas"),
                                buildDefaultUser("comercial", "comercial", "Ejecutivo de Cuentas", RolUsuario.REVISOR,
                                                "Ventas"),
                                buildDefaultUser("pedro.ventas", "pedro.ventas", "Pedro Senior Sales",
                                                RolUsuario.SOLICITANTE,
                                                "Ventas"),

                                // --- SOLICITANTES GENERALES ---
                                buildDefaultUser("solicitante", "solicitante", "Usuario Estándar Juan",
                                                RolUsuario.SOLICITANTE,
                                                "Ventas"),
                                buildDefaultUser("ana", "ana", "Ana Martínez", RolUsuario.SOLICITANTE, "Ventas"),
                                buildDefaultUser("carlos", "carlos", "Carlos Ruiz", RolUsuario.SOLICITANTE,
                                                "Recursos Humanos"));

                for (Usuario user : usuariosPorDefecto) {
                        usuarioRepository.save(user);
                        log.info("Usuario creado: {} [{}]", user.getUsername(), user.getRol());
                }
        }

        private void seedSolicitudes() {
                // --- SISTEMAS ---
                crearSolicitudCompleta(
                        "Acceso a Servidor Producción",
                        "Se requiere acceso SSH al servidor de base de datos para realizar tareas de mantenimiento programadas.",
                        Prioridad.ALTA, "Sistemas", "dev", EstadoWorkflow.APROBADO, "maria.revisora");

                crearSolicitudCompleta(
                        "CRÍTICO: Caída de VPN Regional",
                        "La conexión VPN con la sucursal Norte ha fallado. Los usuarios no pueden acceder al ERP.",
                        Prioridad.URGENTE, "Sistemas", "pedro.ventas", EstadoWorkflow.EN_REVISION, "ti");

                crearSolicitudCompleta(
                        "Renovación de Licencias IDE",
                        "Solicitud para renovar las licencias de IntelliJ IDEA para el equipo de backend.",
                        Prioridad.MEDIA, "Sistemas", "dev", EstadoWorkflow.PENDIENTE, null);

                crearSolicitudCompleta(
                        "Actualización de Firewall perimetral",
                        "Es necesario aplicar el último parche de seguridad al firewall principal antes del fin de semana.",
                        Prioridad.ALTA, "Sistemas", "admin", EstadoWorkflow.APROBADO, "sistemas");

                crearSolicitudCompleta(
                        "Asignación de Equipo Mac Pro",
                        "Solicito cambio de equipo a Mac Pro para mejorar tiempos de compilación en iOS.",
                        Prioridad.BAJA, "Sistemas", "dev", EstadoWorkflow.RECHAZADO, "sistemas");

                // --- RECURSOS HUMANOS ---
                crearSolicitudCompleta(
                        "Aprobación de Vacaciones Q4",
                        "Solicitud de vacaciones para el equipo de ventas correspondiente al último trimestre del año.",
                        Prioridad.MEDIA, "Recursos Humanos", "solicitante", EstadoWorkflow.EN_REVISION, "rrhh");

                crearSolicitudCompleta(
                        "Publicación de Oferta: Dev Senior",
                        "Requerimos publicar la vacante para desarrollador Senior en LinkedIn y portales locales.",
                        Prioridad.ALTA, "Recursos Humanos", "admin", EstadoWorkflow.APROBADO, "claudia.rrhh");

                crearSolicitudCompleta(
                        "Incapacidad Médica por 3 días",
                        "Adjunto certificado médico por incapacidad del 10 al 12 del presente mes.",
                        Prioridad.MEDIA, "Recursos Humanos", "carlos", EstadoWorkflow.APROBADO, "rrhh");

                crearSolicitudCompleta(
                        "Solicitud de Adelanto de Sueldo",
                        "Solicito adelanto del 30% de mi quincena por motivos personales de urgencia.",
                        Prioridad.ALTA, "Recursos Humanos", "ana", EstadoWorkflow.RECHAZADO, "revisor");

                crearSolicitudCompleta(
                        "Onboarding Nuevo Ingreso - Ventas",
                        "Preparación de bienvenida y kit inicial para el nuevo ejecutivo comercial que entra el lunes.",
                        Prioridad.MEDIA, "Recursos Humanos", "revisor", EstadoWorkflow.PENDIENTE, null);

                // --- VENTAS ---
                crearSolicitudCompleta(
                        "Revisión de Contrato Cliente VIP",
                        "Necesitamos validar los términos y condiciones del nuevo contrato con la empresa ACME Corp.",
                        Prioridad.ALTA, "Ventas", "pedro.ventas", EstadoWorkflow.EN_REVISION, "comercial");

                crearSolicitudCompleta(
                        "Descuento Extraordinario 25%",
                        "El cliente amenaza con irse a la competencia. Pido autorización para aplicar 25% de descuento.",
                        Prioridad.URGENTE, "Ventas", "ana", EstadoWorkflow.APROBADO, "ventas");

                crearSolicitudCompleta(
                        "Presupuesto Evento Corporativo",
                        "Aprobación de fondos para stand en la feria internacional de tecnología.",
                        Prioridad.MEDIA, "Ventas", "solicitante", EstadoWorkflow.RECHAZADO, "ventas");

                crearSolicitudCompleta(
                        "Reembolso de Viáticos (Viaje Sur)",
                        "Solicitud de reembolso por $450 debido a viáticos en visita a clientes de zona sur.",
                        Prioridad.MEDIA, "Ventas", "pedro.ventas", EstadoWorkflow.EN_REVISION, "comercial");

                crearSolicitudCompleta(
                        "Apertura de Crédito Cliente Nuevo",
                        "Análisis crediticio para otorgar línea de crédito a la empresa XYZ.",
                        Prioridad.ALTA, "Ventas", "ana", EstadoWorkflow.PENDIENTE, null);

                log.info("Se han creado 15 solicitudes de prueba con distintos estados y departamentos.");
        }

        private void crearSolicitudCompleta(String titulo, String desc, Prioridad prio, String depto, String creador, EstadoWorkflow estadoFinal, String revisor) {
                String codigo = codigoGenerator.generarCodigo();
                
                LocalDateTime fechaCreacion = LocalDateTime.now().minusDays((long) (Math.random() * 10)); // Distribuir fechas
                
                SolicitudWorkflow s = SolicitudWorkflow.builder()
                                .codigoSeguimiento(codigo)
                                .titulo(titulo)
                                .descripcion(desc)
                                .prioridad(prio)
                                .estado(EstadoWorkflow.PENDIENTE)
                                .departamentoActual(depto)
                                .usuarioCreador(creador)
                                .fechaCreacion(fechaCreacion)
                                .fechaLimiteAtencion(fechaCreacion.plusHours(prio == Prioridad.URGENTE ? 4 : (prio == Prioridad.ALTA ? 24 : 72)))
                                .build();

                // 1. Siempre inicia en PENDIENTE
                s.registrarTransicion(null, EstadoWorkflow.PENDIENTE, creador, "SOLICITANTE",
                                "Solicitud registrada en el sistema.");
                                
                // 2. Transición a EN_REVISION si aplica
                if (estadoFinal != EstadoWorkflow.PENDIENTE) {
                        s.setUsuarioAsignado(revisor);
                        s.registrarTransicion(EstadoWorkflow.PENDIENTE, EstadoWorkflow.EN_REVISION, revisor, "REVISOR", 
                                        "Tomado para análisis y revisión departamental.");
                                        
                        // 3. Transición final a APROBADO o RECHAZADO si aplica
                        if (estadoFinal == EstadoWorkflow.APROBADO) {
                                s.registrarTransicion(EstadoWorkflow.EN_REVISION, EstadoWorkflow.APROBADO, revisor, "REVISOR", 
                                                "Validación completada. Se aprueba la solicitud según políticas.");
                        } else if (estadoFinal == EstadoWorkflow.RECHAZADO) {
                                s.registrarTransicion(EstadoWorkflow.EN_REVISION, EstadoWorkflow.RECHAZADO, revisor, "REVISOR", 
                                                "Solicitud rechazada por falta de justificación o incumplimiento de reglas.");
                        }
                }
                
                solicitudRepository.save(s);
        }

        private Departamento buildDepto(String nombre, String desc) {
                return Departamento.builder()
                                .nombre(nombre)
                                .descripcion(desc)
                                .creadoPor("system")
                                .activo(true)
                                .fechaCreacion(LocalDateTime.now())
                                .build();
        }

        private Usuario buildDefaultUser(String username, String password, String nombre, RolUsuario rol,
                        String depto) {
                String encoded = nombre.trim().replace(" ", "+");
                String avatarUrl = "https://ui-avatars.com/api/?name=" + encoded
                                + "&background=random&color=fff&bold=true&size=128";

                return Usuario.builder()
                                .username(username)
                                .password(password)
                                .nombreCompleto(nombre)
                                .rol(rol)
                                .departamento(depto)
                                .avatarUrl(avatarUrl)
                                .fechaCreacion(LocalDateTime.now())
                                .build();
        }
}
