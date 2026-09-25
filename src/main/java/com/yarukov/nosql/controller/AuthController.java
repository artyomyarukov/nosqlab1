package com.yarukov.nosql.controller;

import com.yarukov.nosql.dto.LoginRequest;
import com.yarukov.nosql.dto.LoginResponse;
import com.yarukov.nosql.dto.SessionStatusResponse;
import com.yarukov.nosql.model.entity.User;
import com.yarukov.nosql.model.riak.UserSession;
import com.yarukov.nosql.repository.jpa.UserRepository;
import com.yarukov.nosql.service.AuthService;
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



    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/session")
    public ResponseEntity<SessionStatusResponse> checkSession(
            @RequestHeader(value = "X-Session-Token", required = false) String token) {
        SessionStatusResponse status = authService.validateSession(token);
        if (!status.isActive()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(status);
        }
        return ResponseEntity.ok(status);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader(value = "X-Session-Token", required = false) String token) {
        authService.logout(token);
        return ResponseEntity.ok(Map.of("message", "Сессия успешно завершена"));
    }
}