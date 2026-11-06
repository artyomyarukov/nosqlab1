package com.yarukov.nosql.service;

import com.yarukov.nosql.dao.couchdb.UserCouchDao;
import com.yarukov.nosql.dao.riak.ActionHistoryDao;
import com.yarukov.nosql.dao.riak.CounterDao;
import com.yarukov.nosql.dao.riak.UserCacheDao;
import com.yarukov.nosql.dto.*;
import com.yarukov.nosql.model.couchdb.UserDoc;
import com.yarukov.nosql.model.entity.History;
import com.yarukov.nosql.model.riak.ActionEvent;
import com.yarukov.nosql.model.riak.CachedUserProfile;
import com.yarukov.nosql.model.riak.UserSession;
import com.yarukov.nosql.repository.jpa.HistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserCouchDao userCouchDao;
    private final AuthService authService;
    private final HistoryRepository historyRepository;

    private final UserCacheDao userCacheDao;
    private final CounterDao counterDao;
    private final ActionHistoryDao actionHistoryDao;

    public List<UserListItemResponse> getAllUsers() {
        return userCouchDao.findAll().stream()
                .filter(u -> !"OPERATOR".equalsIgnoreCase(u.getRole()))
                .map(u -> UserListItemResponse.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .fullName(u.getFullName())
                        .role(u.getRole())
                        .build())
                .collect(Collectors.toList());
    }

    public UserProfileResponse getUserProfile(String userId) {

        counterDao.increment(userId);
        long visitCount = counterDao.getCount(userId);

        Optional<CachedUserProfile> cachedOpt = userCacheDao.findById(userId);

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

        UserDoc user = userCouchDao.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь с id=" + userId + " не найден в CouchDB"));

        CachedUserProfile toCache = CachedUserProfile.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .deliveryAddress(user.getDeliveryAddress())
                .role(user.getRole())
                .cachedAt(Instant.now())
                .build();
        userCacheDao.save(toCache);

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

    @Transactional
    public UserProfileResponse updateUserProfile(String userId, UpdateUserRequest request, String token) {
        UserSession session = authService.getActiveSessionOrThrow(token);

        UserDoc user = userCouchDao.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден в CouchDB"));

        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getDeliveryAddress() != null) user.setDeliveryAddress(request.getDeliveryAddress());


        userCouchDao.save(user);

        historyRepository.save(History.builder()
                .userId(userId)
                .action("PROFILE_UPDATED")
                .operator(session.getOperatorName())
                .details("Обновлен профиль")
                .timestamp(Instant.now())
                .build());

        userCacheDao.deleteById(userId);
        actionHistoryDao.deleteByUserId(userId);

        return getUserProfile(userId);
    }

    @Transactional
    public NotificationResponse sendNotification(String userId, NotificationRequest request, String token) {
        UserSession session = authService.getActiveSessionOrThrow(token);

        UserDoc user = userCouchDao.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден в CouchDB"));

        Instant now = Instant.now();
        historyRepository.save(History.builder()
                .userId(userId)
                .action("NOTIFICATION_SENT [" + request.getType() + "]")
                .operator(session.getOperatorName())
                .details(request.getMessage())
                .timestamp(now)
                .build());

        actionHistoryDao.deleteByUserId(userId);

        log.info("Уведомление оператором '{}' отправлено пользователю '{}'", session.getOperatorName(), user.getUsername());
        return NotificationResponse.builder()
                .status("SENT")
                .loggedAt(now)
                .message("Уведомление успешно доставлено и записано в историю")
                .build();
    }

    public List<ActionEvent> getUserActionHistory(String userId) {
        Optional<List<ActionEvent>> cachedHistory = actionHistoryDao.findByUserId(userId);
        if (cachedHistory.isPresent()) {
            log.info("CACHE HIT: История пользователя id={} получена из Riak KV", userId);
            return cachedHistory.get();
        }
        log.info("CACHE MISS: Загрузка истории действий id={} из PostgreSQL", userId);
        List<ActionEvent> history = historyRepository.findByUserIdOrderByTimestampDesc(userId).stream()
                .map(h -> ActionEvent.builder()
                        .action(h.getAction())
                        .operator(h.getOperator())
                        .details(h.getDetails())
                        .timestamp(h.getTimestamp())
                        .build())
                .collect(Collectors.toList());

        actionHistoryDao.save(userId, history);

        return history;
    }
}