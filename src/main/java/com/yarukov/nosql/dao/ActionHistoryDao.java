package com.yarukov.nosql.dao;

import com.basho.riak.client.api.RiakClient;
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
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ActionHistoryDao {

    private final RiakClient riakClient;
    private ObjectMapper objectMapper;
    private static final Namespace HISTORY_NS = new Namespace("default", "action_history");

    @PostConstruct
    public void init() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public void save(Long userId, List<ActionEvent> history) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(history);;
            Location location = new Location(HISTORY_NS, String.valueOf(userId));
            RiakObject riakObject = new RiakObject()
                    .setContentType("application/json")
                    .setValue(BinaryValue.create(bytes));

            StoreValue store = new StoreValue.Builder(riakObject).withLocation(location).build();
            riakClient.execute(store);
            log.info("Кэш истории действий пользователя id={} сохранен в Riak KV", userId);
        } catch (Exception e) {
            log.error("Ошибка сохранения кэша истории в Riak", e);
        }
    }

    public Optional<List<ActionEvent>> findByUserId(Long userId) {
        try {
            Location location = new Location(HISTORY_NS, String.valueOf(userId));
            FetchValue fetch = new FetchValue.Builder(location).build();
            FetchValue.Response response = riakClient.execute(fetch);

            if (response.isNotFound()) {
                return Optional.empty();
            }

            RiakObject obj = response.getValue(RiakObject.class);
            List<ActionEvent> list = objectMapper.readValue(obj.getValue().getValue(), new TypeReference<>() {
            });
            return Optional.of(list);
        } catch (Exception e) {
            log.error("Ошибка чтения кэша истории из Riak", e);
            return Optional.empty();
        }
    }

    public void deleteByUserId(Long userId) {
        try {
            Location location = new Location(HISTORY_NS, String.valueOf(userId));
            DeleteValue delete = new DeleteValue.Builder(location).build();
            riakClient.execute(delete);
            log.info("Кэш истории действий id={} сброшен в Riak", userId);
        } catch (Exception e) {
            log.error("Ошибка инвалидации кэша истории в Riak", e);
        }
    }
}
