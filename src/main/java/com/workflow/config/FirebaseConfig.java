package com.workflow.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.io.IOException;

@Configuration
public class FirebaseConfig {

    @PostConstruct
    public void initialize() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .setProjectId("parcialsoftware-383c1")
                    .build();
                FirebaseApp.initializeApp(options);
                System.out.println("Firebase inicializado correctamente para el proyecto parcialsoftware-383c1");
            }
        } catch (Exception e) {
            // Esto evitará que la app falle al arrancar si Firebase no responde
            System.err.println("CRÍTICO: No se pudo iniciar Firebase, pero la app seguirá arrancando: " + e.getMessage());
        }
    }

}
