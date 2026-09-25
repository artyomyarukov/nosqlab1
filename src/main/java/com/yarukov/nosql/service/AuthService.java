package com.yarukov.nosql.service;

import com.yarukov.nosql.dto.LoginRequest;
import com.yarukov.nosql.dto.LoginResponse;
import com.yarukov.nosql.dto.SessionStatusResponse;
import com.yarukov.nosql.model.entity.User;
import com.yarukov.nosql.model.riak.UserSession;
import com.yarukov.nosql.repository.jpa.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RiakService riakService;
    private static final Duration SESSION_TTL = Duration.ofMinutes(15);

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
        if (!"OPERATOR".equalsIgnoreCase(user.getRole())) {
            throw new RuntimeException("Доступ разрешен только операторам");
        }

        String token = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(SESSION_TTL);

        UserSession session = UserSession.builder()
                .token(token)
                .username(user.getUsername())
                .operatorName(user.getFullName())
                .createdAt(now)
                .expiresAt(expiresAt)
                .build();

        riakService.saveSession(session);

        return LoginResponse.builder()
                .token(token)
                .operatorName(user.getFullName())
                .expiresAt(expiresAt)
                .build();
    }

    public SessionStatusResponse validateSession(String token) {
        if (token == null || token.isBlank()) {
            return SessionStatusResponse.builder()
                    .active(false)
                    .message("Токен не передан")
                    .build();
        }

        Optional<UserSession> sessionOpt = riakService.getSession(token);

        if (sessionOpt.isPresent()) {
            UserSession session = sessionOpt.get();
            return SessionStatusResponse.builder()
                    .active(true)
                    .username(session.getUsername())
                    .operatorName(session.getOperatorName())
                    .message("Сессия активна")
                    .build();
        } else {
            return SessionStatusResponse.builder()
                    .active(false)
                    .message("Сессия истекла или не найдена")
                    .build();
        }
    }

    public void logout(String token) {
        if (token != null) {
            riakService.deleteSession(token);
        }
    }

    public UserSession getActiveSessionOrThrow(String token) {
        return riakService.getSession(token)
                .orElseThrow(() -> new RuntimeException("Неавторизован: сессия истекла или не существует"));
    }
}