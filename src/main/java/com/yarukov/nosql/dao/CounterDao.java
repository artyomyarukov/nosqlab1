package com.yarukov.nosql.dao;

import com.basho.riak.client.api.RiakClient;
import com.basho.riak.client.api.commands.datatypes.CounterUpdate;
import com.basho.riak.client.api.commands.datatypes.FetchCounter;
import com.basho.riak.client.api.commands.datatypes.UpdateCounter;
import com.basho.riak.client.core.query.Location;
import com.basho.riak.client.core.query.Namespace;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class CounterDao {

    private final RiakClient riakClient;
    private static final Namespace COUNTERS_NS = new Namespace("counters", "page_visits");

    public void increment(Long userId) {
        try {
            Location location = new Location(COUNTERS_NS, "user_visit_" + userId);
            CounterUpdate cu = new CounterUpdate(1);
            UpdateCounter uc = new UpdateCounter.Builder(location, cu).build();
            riakClient.execute(uc);
            log.info("CRDT счётчик для пользователя id={} увеличен на 1", userId);
        } catch (Exception e) {
            log.error("Ошибка инкремента счётчика в Riak", e);
        }
    }

    public long getCount(Long userId) {
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
}