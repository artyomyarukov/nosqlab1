package com.yarukov.nosql.controller;

import com.yarukov.nosql.dto.LoginRequest;
import com.yarukov.nosql.dto.LoginResponse;
import com.yarukov.nosql.dto.SessionStatusResponse;
import com.yarukov.nosql.model.entity.User;
import com.yarukov.nosql.model.riak.UserSession;
import com.yarukov.nosql.repository.jpa.UserRepository;
import com.yarukov.nosql.service.RiakService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final RiakService riakService;

    // Вход оператора: создание временной сессии в Riak с TTL на 15 минут
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElse(null);

        if (user == null || !"OPERATOR".equalsIgnoreCase(user.getRole())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Оператор с таким логином не найден"));
        }

        String token = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(15)); // TTL 15 минут

        UserSession session = UserSession.builder()
                .token(token)
                .username(user.getUsername())
                .operatorName(user.getFullName())
                .createdAt(now)
                .expiresAt(expiresAt)
                .build();

        riakService.saveSession(session);

        return ResponseEntity.ok(LoginResponse.builder()
                .token(token)
                .operatorName(user.getFullName())
                .expiresAt(expiresAt)
                .build());
    }

    // Проверка статуса сессии (проверяет TTL в Riak)
    @GetMapping("/session")
    public ResponseEntity<?> checkSession(@RequestHeader(value = "X-Session-Token", required = false) String token) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("active", false, "message", "Токен не предоставлен"));
        }

        return riakService.getSession(token)
                .map(session -> ResponseEntity.ok(SessionStatusResponse.builder()
                        .active(true)
                        .username(session.getUsername())
                        .operatorName(session.getOperatorName())
                        .build()))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(SessionStatusResponse.builder().active(false).build()));
    }

    // Выход: удаление ключа сессии из Riak
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "X-Session-Token", required = false) String token) {
        if (token != null && !token.isBlank()) {
            riakService.deleteSession(token);
        }
        return ResponseEntity.ok(Map.of("message", "Сессия успешно завершена"));
    }
}