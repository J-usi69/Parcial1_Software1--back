package com.workflow.config;

import com.workflow.domain.enums.RolUsuario;
import com.workflow.domain.model.Usuario;
import com.workflow.repository.UsuarioRepository;
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

    @Override
    public void run(String... args) throws Exception {
        log.info("Inicializando DataSeeder de usuarios para testing (modo sincronizacion)...");

        List<Usuario> usuariosPorDefecto = List.of(
                buildDefaultUser("admin", "admin", "Administrador del Sistema", RolUsuario.ADMINISTRADOR, "Sistemas"),
                buildDefaultUser("solicitante", "solicitante", "Juan Solicitante", RolUsuario.SOLICITANTE, "Ventas"),
                buildDefaultUser("revisor", "revisor", "Mario Revisor RRHH", RolUsuario.REVISOR, "Recursos Humanos")
        );

        int creados = 0;
        int actualizados = 0;

        for (Usuario defaultUser : usuariosPorDefecto) {
            Optional<Usuario> existenteOpt = usuarioRepository.findByUsername(defaultUser.getUsername());

            if (existenteOpt.isEmpty()) {
                usuarioRepository.save(defaultUser);
                creados++;
                continue;
            }

            Usuario existente = existenteOpt.get();
            boolean requiereActualizacion = false;

            if (!defaultUser.getPassword().equals(existente.getPassword())) {
                existente.setPassword(defaultUser.getPassword());
                requiereActualizacion = true;
            }
            if (!defaultUser.getNombreCompleto().equals(existente.getNombreCompleto())) {
                existente.setNombreCompleto(defaultUser.getNombreCompleto());
                requiereActualizacion = true;
            }
            if (defaultUser.getRol() != existente.getRol()) {
                existente.setRol(defaultUser.getRol());
                requiereActualizacion = true;
            }
            if (!defaultUser.getDepartamento().equals(existente.getDepartamento())) {
                existente.setDepartamento(defaultUser.getDepartamento());
                requiereActualizacion = true;
            }
            if (existente.getFechaCreacion() == null) {
                existente.setFechaCreacion(LocalDateTime.now());
                requiereActualizacion = true;
            }

            if (requiereActualizacion) {
                usuarioRepository.save(existente);
                actualizados++;
            }
        }

        log.info(
                "DataSeeder finalizado. Creados: {}, Actualizados: {}. Credenciales de prueba: (admin/admin), (solicitante/solicitante), (revisor/revisor)",
                creados,
                actualizados
        );
    }

    private Usuario buildDefaultUser(
            String username,
            String password,
            String nombreCompleto,
            RolUsuario rol,
            String departamento
    ) {
        return Usuario.builder()
                .username(username)
                .password(password)
                .nombreCompleto(nombreCompleto)
                .rol(rol)
                .departamento(departamento)
                .fechaCreacion(LocalDateTime.now())
                .build();
    }
}
