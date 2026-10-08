package com.yarukov.nosql.dao;

import com.basho.riak.client.api.RiakClient;
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

    public void add(Long userId, ActionEvent event) {
        try {
            List<ActionEvent> history = findByUserId(userId);
            history.add(0, event);

            String json = objectMapper.writeValueAsString(history);
            Location location = new Location(HISTORY_NS, String.valueOf(userId));
            RiakObject riakObject = new RiakObject()
                    .setContentType("application/json")
                    .setValue(BinaryValue.create(json));

            StoreValue store = new StoreValue.Builder(riakObject).withLocation(location).build();
            riakClient.execute(store);
            log.info("Событие '{}' сохранено в историю пользователя id={}", event.getAction(), userId);
        } catch (Exception e) {
            log.error("Ошибка сохранения истории в Riak", e);
        }
    }

    public List<ActionEvent> findByUserId(Long userId) {
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
            log.error("Ошибка чтения истории из Riak", e);
            return new ArrayList<>();
        }
    }
}