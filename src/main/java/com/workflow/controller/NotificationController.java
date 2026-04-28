package com.workflow.controller;

import com.workflow.domain.model.UserDeviceToken;
import com.workflow.repository.UserDeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class NotificationController {

    private final UserDeviceTokenRepository tokenRepository;

    @PostMapping("/register-token")
    public ResponseEntity<?> registerToken(@RequestBody Map<String, String> request) {
        String usuarioId = request.get("usuarioId");
        String token = request.get("token");
        String platform = request.getOrDefault("platform", "web");

        if (token == null || token.isEmpty()) {
            return ResponseEntity.badRequest().body("Token is required");
        }

        tokenRepository.findByToken(token).ifPresentOrElse(
            existing -> {
                existing.setUsuarioId(usuarioId);
                existing.setUpdatedAt(LocalDateTime.now());
                tokenRepository.save(existing);
            },
            () -> {
                UserDeviceToken newToken = UserDeviceToken.builder()
                        .usuarioId(usuarioId)
                        .token(token)
                        .platform(platform)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                tokenRepository.save(newToken);
            }
        );

        return ResponseEntity.ok(Map.of("message", "Token registered successfully"));
    }

    @DeleteMapping("/unregister-token")
    public ResponseEntity<?> unregisterToken(@RequestParam String token) {
        tokenRepository.deleteByToken(token);
        return ResponseEntity.ok(Map.of("message", "Token unregistered successfully"));
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(@RequestParam String usuarioId) {
        if (usuarioId == null || usuarioId.isBlank()) {
            return ResponseEntity.badRequest().body("usuarioId is required");
        }

        boolean enabled = tokenRepository.existsByUsuarioId(usuarioId);
        return ResponseEntity.ok(Map.of("enabled", enabled));
    }
}
