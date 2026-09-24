package com.yarukov.nosql.controller;

import com.yarukov.nosql.dto.*;
import com.yarukov.nosql.model.entity.User;
import com.yarukov.nosql.model.riak.ActionEvent;
import com.yarukov.nosql.model.riak.CachedUserProfile;
import com.yarukov.nosql.model.riak.UserSession;
import com.yarukov.nosql.repository.jpa.UserRepository;
import com.yarukov.nosql.service.RiakService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final RiakService riakService;

    // 1. Список всех пользователей (для левой колонки фронтенда)
    @GetMapping
    public List<UserListItemResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .filter(u -> !"OPERATOR".equalsIgnoreCase(u.getRole())) // Показываем клиентов и курьеров
                .map(u -> UserListItemResponse.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .fullName(u.getFullName())
                        .role(u.getRole())
                        .build())
                .collect(Collectors.toList());
    }

    // 2. Получение профиля: СЧЕТЧИК CRDT + КЭШИРОВАНИЕ
    @GetMapping("/{id}")
    public ResponseEntity<?> getUserProfile(@PathVariable Long id) {
        // Атомарно увеличиваем счетчик просмотров в Riak (CRDT PN-Counter)
        riakService.incrementVisitCount(id);
        long visitCount = riakService.getVisitCount(id);

        // Пытаемся взять данные из кэша Riak KV
        Optional<CachedUserProfile> cached = riakService.getCachedUserProfile(id);

        if (cached.isPresent()) {
            log.info(">>> CACHE HIT: Профиль пользователя id={} взят из RIAK KV", id);
            CachedUserProfile c = cached.get();
            return ResponseEntity.ok(UserProfileResponse.builder()
                    .id(c.getId())
                    .username(c.getUsername())
                    .fullName(c.getFullName())
                    .phone(c.getPhone())
                    .deliveryAddress(c.getDeliveryAddress())
                    .role(c.getRole())
                    .fromCache(true)
                    .visitCount(visitCount)
                    .build());
        }

        // Если в кэше нет (CACHE MISS) — идем в PostgreSQL
        log.info(">>> CACHE MISS: Загрузка пользователя id={} из PostgreSQL и запись в Riak KV", id);
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Пользователь не найден"));
        }

        // Сохраняем в кэш Riak KV
        CachedUserProfile profileToCache = CachedUserProfile.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .deliveryAddress(user.getDeliveryAddress())
                .role(user.getRole())
                .cachedAt(Instant.now())
                .build();
        riakService.cacheUserProfile(profileToCache);

        return ResponseEntity.ok(UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .deliveryAddress(user.getDeliveryAddress())
                .role(user.getRole())
                .fromCache(false)
                .visitCount(visitCount)
                .build());
    }

    // 3. Обновление профиля: инвалидация кэша в Riak
    @PutMapping("/{id}")
    public ResponseEntity<?> updateUserProfile(@PathVariable Long id,
                                               @RequestBody UpdateUserRequest request,
                                               @RequestHeader(value = "X-Session-Token", required = false) String token) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Пользователь не найден"));
        }

        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setDeliveryAddress(request.getDeliveryAddress());
        userRepository.save(user);

        // Инвалидируем кэш в Riak KV
        riakService.evictUserProfile(id);

        // Определяем оператора
        String operatorName = getOperatorNameFromToken(token);

        // Логируем изменение в историю Riak
        riakService.addActionToHistory(id, ActionEvent.builder()
                .timestamp(Instant.now())
                .action("PROFILE_UPDATED")
                .operator(operatorName)
                .details("Обновлены данные профиля: ФИО, телефон или адрес")
                .build());

        return ResponseEntity.ok(Map.of("message", "Профиль успешно обновлен, кэш сброшен"));
    }

    // 4. Отправка уведомления пользователю: логирование в историю Riak
    @PostMapping("/{id}/notify")
    public ResponseEntity<?> notifyUser(@PathVariable Long id,
                                        @Valid @RequestBody NotificationRequest request,
                                        @RequestHeader(value = "X-Session-Token", required = false) String token) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Пользователь не найден"));
        }

        String operatorName = getOperatorNameFromToken(token);
        Instant now = Instant.now();

        // Записываем событие отправки уведомления в историю Riak KV
        riakService.addActionToHistory(id, ActionEvent.builder()
                .timestamp(now)
                .action("NOTIFICATION_SENT [" + (request.getType() != null ? request.getType() : "INFO") + "]")
                .operator(operatorName)
                .details(request.getMessage())
                .build());

        return ResponseEntity.ok(NotificationResponse.builder()
                .status("SENT")
                .loggedAt(now)
                .build());
    }

    // 5. ОБЯЗАТЕЛЬНЫЙ СЦЕНАРИЙ: Просмотр истории действий
    @GetMapping("/{id}/history")
    public ResponseEntity<List<ActionEvent>> getUserHistory(@PathVariable Long id) {
        List<ActionEvent> history = riakService.getActionHistory(id);
        return ResponseEntity.ok(history);
    }

    // Вспомогательный метод определения оператора из Riak-сессии
    private String getOperatorNameFromToken(String token) {
        if (token != null && !token.isBlank()) {
            return riakService.getSession(token)
                    .map(UserSession::getOperatorName)
                    .orElse("Неизвестный оператор");
        }
        return "Система / Анонимный оператор";
    }
}