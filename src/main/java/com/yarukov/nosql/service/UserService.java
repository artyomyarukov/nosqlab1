package com.yarukov.nosql.service;

import com.yarukov.nosql.dto.*;
import com.yarukov.nosql.model.entity.User;
import com.yarukov.nosql.model.riak.ActionEvent;
import com.yarukov.nosql.model.riak.CachedUserProfile;
import com.yarukov.nosql.model.riak.UserSession;
import com.yarukov.nosql.repository.jpa.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RiakService riakService;
    private final AuthService authService;


    public List<UserListItemResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .filter(u -> !"OPERATOR".equalsIgnoreCase(u.getRole()))
                .map(u -> UserListItemResponse.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .fullName(u.getFullName())
                        .role(u.getRole())
                        .build())
                .collect(Collectors.toList());
    }

    public UserProfileResponse getUserProfile(Long userId) {
        riakService.incrementVisitCount(userId);
        long visitCount = riakService.getVisitCount(userId);
        Optional<CachedUserProfile> cachedOpt = riakService.getCachedUserProfile(userId);
        if (cachedOpt.isPresent()) {
            log.info("CACHE HIT: Профиль пользователя id={} получен из Riak KV", userId);
            CachedUserProfile cached = cachedOpt.get();
            return UserProfileResponse.builder()
                    .id(cached.getId())
                    .username(cached.getUsername())
                    .fullName(cached.getFullName())
                    .phone(cached.getPhone())
                    .deliveryAddress(cached.getDeliveryAddress())
                    .role(cached.getRole())
                    .fromCache(true)
                    .visitCount(visitCount)
                    .build();
        }

        log.info("CACHE MISS: Профиль пользователя id={} загружается из PostgreSQL", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь с id=" + userId + " не найден"));

        CachedUserProfile toCache = CachedUserProfile.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .deliveryAddress(user.getDeliveryAddress())
                .role(user.getRole())
                .cachedAt(Instant.now())
                .build();
        riakService.cacheUserProfile(toCache);

        return UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .deliveryAddress(user.getDeliveryAddress())
                .role(user.getRole())
                .fromCache(false)
                .visitCount(visitCount)
                .build();
    }

    public UserProfileResponse updateUserProfile(Long userId, UpdateUserRequest request, String token) {
        UserSession session = authService.getActiveSessionOrThrow(token);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getDeliveryAddress() != null) user.setDeliveryAddress(request.getDeliveryAddress());

        userRepository.save(user);

        riakService.evictUserProfile(userId);
        riakService.addActionToHistory(userId, ActionEvent.builder()
                .timestamp(Instant.now())
                .action("PROFILE_UPDATED")
                .operator(session.getOperatorName())
                .details("Обновлены контактные данные пользователя")
                .build());

        return getUserProfile(userId);
    }

    public NotificationResponse sendNotification(Long userId, NotificationRequest request, String token) {
        UserSession session = authService.getActiveSessionOrThrow(token);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        Instant now = Instant.now();
        riakService.addActionToHistory(userId, ActionEvent.builder()
                .timestamp(now)
                .action("NOTIFICATION_SENT [" + request.getType() + "]")
                .operator(session.getOperatorName())
                .details(request.getMessage())
                .build());

        log.info("Уведомление оператором '{}' отправлено пользователю '{}': {}",
                session.getOperatorName(), user.getUsername(), request.getMessage());

        return NotificationResponse.builder()
                .status("SENT")
                .loggedAt(now)
                .message("Уведомление успешно доставлено и записано в историю")
                .build();
    }

    public List<ActionEvent> getUserActionHistory(Long userId) {
        return riakService.getActionHistory(userId);
    }
}