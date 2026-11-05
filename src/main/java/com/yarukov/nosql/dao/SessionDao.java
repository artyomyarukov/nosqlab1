package com.yarukov.nosql.dao;

import com.basho.riak.client.api.RiakClient;
import com.basho.riak.client.api.commands.kv.DeleteValue;
import com.basho.riak.client.api.commands.kv.FetchValue;
import com.basho.riak.client.api.commands.kv.StoreValue;
import com.basho.riak.client.core.query.Location;
import com.basho.riak.client.core.query.Namespace;
import com.basho.riak.client.core.query.RiakObject;
import com.basho.riak.client.core.util.BinaryValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yarukov.nosql.model.riak.UserSession;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import com.basho.riak.client.api.commands.kv.ListKeys;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class SessionDao {

    private final RiakClient riakClient;
    private ObjectMapper objectMapper;
    private static final Namespace SESSIONS_NS = new Namespace("default", "operator_sessions");

    @PostConstruct
    public void init() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public void save(UserSession session) {
        try {
            String json = objectMapper.writeValueAsString(session);
            Location location = new Location(SESSIONS_NS, session.getToken());
            RiakObject riakObject = new RiakObject()
                    .setContentType("application/json")
                    .setValue(BinaryValue.create(json));

            StoreValue store = new StoreValue.Builder(riakObject).withLocation(location).build();
            riakClient.execute(store);
            log.info("Сессия оператора {} сохранена в Riak (токен: {})", session.getUsername(), session.getToken());
        } catch (Exception e) {
            log.error("Ошибка сохранения сессии в Riak", e);
        }
    }

    public Optional<UserSession> findByToken(String token) {
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
                log.warn("Сессия с токеном {} истекла (TTL).", token);
                deleteByToken(token);
                return Optional.empty();
            }

            return Optional.of(session);
        } catch (Exception e) {
            log.error("Ошибка чтения сессии из Riak", e);
            return Optional.empty();
        }
    }

    public void deleteByToken(String token) {
        try {
            Location location = new Location(SESSIONS_NS, token);
            DeleteValue delete = new DeleteValue.Builder(location).build();
            riakClient.execute(delete);
            log.info("Сессия {} удалена из Riak", token);
        } catch (Exception e) {
            log.error("Ошибка удаления сессии из Riak", e);
        }
    }


    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredSessions() {
        try {
            ListKeys listKeys =
                    new ListKeys.Builder(SESSIONS_NS).build();
            ListKeys.Response response = riakClient.execute(listKeys);
            int cleaned = 0;
            for (Location location : response) {
                if (findByToken(location.getKeyAsString()).isEmpty()) {
                    cleaned++;
                }
            }
            if (cleaned > 0) {
                log.info("Фоновый шедулер: автоматически удалено {} протухших сессий из Riak KV", cleaned);
            }
        } catch (Exception e) {
            log.error("Ошибка при фоновой очистке сессий", e);
        }
    }
}