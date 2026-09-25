package com.yarukov.nosql.service;

import com.basho.riak.client.api.RiakClient;
import com.basho.riak.client.api.commands.datatypes.CounterUpdate;
import com.basho.riak.client.api.commands.datatypes.FetchCounter;
import com.basho.riak.client.api.commands.datatypes.UpdateCounter;
import com.basho.riak.client.api.commands.kv.DeleteValue;
import com.basho.riak.client.api.commands.kv.FetchValue;
import com.basho.riak.client.api.commands.kv.StoreValue;
import com.basho.riak.client.core.query.Location;
import com.basho.riak.client.core.query.Namespace;
import com.basho.riak.client.core.query.RiakObject;
import com.basho.riak.client.core.util.BinaryValue;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yarukov.nosql.model.riak.ActionEvent;
import com.yarukov.nosql.model.riak.CachedUserProfile;
import com.yarukov.nosql.model.riak.UserSession;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiakService {

    private final RiakClient riakClient;
    private ObjectMapper objectMapper;

    // Bucket-types and bucket
    private static final Namespace SESSIONS_NS = new Namespace("default", "operator_sessions");
    private static final Namespace PROFILES_NS = new Namespace("default", "cached_profiles");
    private static final Namespace HISTORY_NS = new Namespace("default", "action_history");
    private static final Namespace COUNTERS_NS = new Namespace("counters", "page_visits");

    @PostConstruct
    public void init() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }



    public void saveSession(UserSession session) {
        try {
            String json = objectMapper.writeValueAsString(session);
            Location location = new Location(SESSIONS_NS, session.getToken());
            RiakObject riakObject = new RiakObject()
                    .setContentType("application/json")
                    .setValue(BinaryValue.create(json));

            StoreValue store = new StoreValue.Builder(riakObject).withLocation(location).build();
            riakClient.execute(store);
            log.info("Сессия оператора {} успешно сохранена в Riak (токен: {})", session.getUsername(), session.getToken());
        } catch (Exception e) {
            log.error("Ошибка при сохранении сессии в Riak", e);
        }
    }

    public Optional<UserSession> getSession(String token) {
        try {
            Location location = new Location(SESSIONS_NS, token);
            FetchValue fetch = new FetchValue.Builder(location).build();
            FetchValue.Response response = riakClient.execute(fetch);
            if (response.isNotFound()) {
                return Optional.empty();
            }
            RiakObject obj = response.getValue(RiakObject.class);
            UserSession session = objectMapper.readValue(obj.getValue().getValue(), UserSession.class);
            if (session.isExpired()) {
                log.warn("Сессия с токеном {} истекла (TTL просрочен).", token);
                deleteSession(token);
                return Optional.empty();
            }
            return Optional.of(session);
        } catch (Exception e) {
            log.error("Ошибка при чтении сессии из Riak", e);
            return Optional.empty();
        }
    }

    public void deleteSession(String token) {
        try {
            Location location = new Location(SESSIONS_NS, token);
            DeleteValue delete = new DeleteValue.Builder(location).build();
            riakClient.execute(delete);
            log.info("Сессия с токеном {} удалена из Riak", token);
        } catch (Exception e) {
            log.error("Ошибка при удалении сессии из Riak", e);
        }
    }


    public void cacheUserProfile(CachedUserProfile profile) {
        try {
            String json = objectMapper.writeValueAsString(profile);
            Location location = new Location(PROFILES_NS, String.valueOf(profile.getId()));
            RiakObject riakObject = new RiakObject()
                    .setContentType("application/json")
                    .setValue(BinaryValue.create(json));

            StoreValue store = new StoreValue.Builder(riakObject).withLocation(location).build();
            riakClient.execute(store);
            log.info("Профиль пользователя id={} успешно закэширован в Riak KV", profile.getId());
        } catch (Exception e) {
            log.error("Ошибка при кэшировании профиля в Riak", e);
        }
    }

    public Optional<CachedUserProfile> getCachedUserProfile(Long userId) {
        try {
            Location location = new Location(PROFILES_NS, String.valueOf(userId));
            FetchValue fetch = new FetchValue.Builder(location).build();
            FetchValue.Response response = riakClient.execute(fetch);

            if (response.isNotFound()) {
                return Optional.empty();
            }

            RiakObject obj = response.getValue(RiakObject.class);
            CachedUserProfile profile = objectMapper.readValue(obj.getValue().getValue(), CachedUserProfile.class);
            return Optional.of(profile);
        } catch (Exception e) {
            log.error("Ошибка при чтении кэша профиля из Riak", e);
            return Optional.empty();
        }
    }

    public void evictUserProfile(Long userId) {
        try {
            Location location = new Location(PROFILES_NS, String.valueOf(userId));
            DeleteValue delete = new DeleteValue.Builder(location).build();
            riakClient.execute(delete);
            log.info("Кэш профиля пользователя id={} удален из Riak", userId);
        } catch (Exception e) {
            log.error("Ошибка при инвалидации кэша в Riak", e);
        }
    }



    public void incrementVisitCount(Long userId) {
        try {
            Location location = new Location(COUNTERS_NS, "user_visit_" + userId);
            CounterUpdate cu = new CounterUpdate(1);
            UpdateCounter uc = new UpdateCounter.Builder(location, cu).build();
            riakClient.execute(uc);
            log.info("Атомарный счётчик просмотров для пользователя id={} увеличен на 1", userId);
        } catch (Exception e) {
            log.error("Ошибка при инкременте CRDT счетчика в Riak", e);
        }
    }

    public long getVisitCount(Long userId) {
        try {
            Location location = new Location(COUNTERS_NS, "user_visit_" + userId);
            FetchCounter fc = new FetchCounter.Builder(location).build();
            FetchCounter.Response response = riakClient.execute(fc);
            return response.getDatatype() != null ? response.getDatatype().view() : 0L;
        } catch (Exception e) {
            log.warn("Не удалось прочитать счётчик из Riak, возвращаем 0: {}", e.getMessage());
            return 0L;
        }
    }



    public void addActionToHistory(Long userId, ActionEvent event) {
        try {
            List<ActionEvent> history = getActionHistory(userId);
            history.add(0, event);

            String json = objectMapper.writeValueAsString(history);
            Location location = new Location(HISTORY_NS, String.valueOf(userId));
            RiakObject riakObject = new RiakObject()
                    .setContentType("application/json")
                    .setValue(BinaryValue.create(json));

            StoreValue store = new StoreValue.Builder(riakObject).withLocation(location).build();
            riakClient.execute(store);
            log.info("Действие '{}' записано в историю пользователя id={}", event.getAction(), userId);
        } catch (Exception e) {
            log.error("Ошибка при сохранении истории действий в Riak", e);
        }
    }

    public List<ActionEvent> getActionHistory(Long userId) {
        try {
            Location location = new Location(HISTORY_NS, String.valueOf(userId));
            FetchValue fetch = new FetchValue.Builder(location).build();
            FetchValue.Response response = riakClient.execute(fetch);
            if (response.isNotFound()) {
                return new ArrayList<>();
            }
            RiakObject obj = response.getValue(RiakObject.class);
            return objectMapper.readValue(obj.getValue().getValue(), new TypeReference<List<ActionEvent>>() {});
        } catch (Exception e) {
            log.error("Ошибка при чтении истории действий из Riak", e);
            return new ArrayList<>();
        }
    }
}