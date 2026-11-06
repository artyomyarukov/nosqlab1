package com.yarukov.nosql.dao.couchdb;

import com.yarukov.nosql.model.couchdb.TicketDoc;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Repository
public class TicketQueryDao {

    private final CouchDbReadDao couchDbReadDao;
    private ObjectMapper objectMapper;
    private static final String DB = "/tickets";

    public TicketQueryDao(CouchDbReadDao couchDbReadDao) {
        this.couchDbReadDao = couchDbReadDao;
    }

    @PostConstruct
    public void init() {
        this.objectMapper = new ObjectMapper();
    }


    public List<TicketDoc> findByTagsAndDate(String tag, String fromDate, int limit, int skip) {
        List<TicketDoc> result = new ArrayList<>();
        try {
            Map<String, Object> query = Map.of(
                    "selector", Map.of(
                            "tags", Map.of("$elemMatch", Map.of("$eq", tag)),
                            "createdAt", Map.of("$gte", fromDate)),
                    "limit", limit,
                    "skip", skip,
                    "use_index", "tags_createdAt_index");

            JsonNode root = couchDbReadDao.read(client -> client.post().uri(DB + "/_find").bodyValue(query)
                    .retrieve().bodyToMono(JsonNode.class));

            if (root != null && root.has("docs")) {
                for (JsonNode doc : root.get("docs")) {
                    result.add(objectMapper.treeToValue(doc, TicketDoc.class));
                }
            }
        } catch (Exception e) {
            log.error("Ошибка Mango-поиска тикетов", e);
            throw new RuntimeException("Не удалось выполнить поиск обращений в CouchDB", e);
        }
        return result;
    }

    public JsonNode explainFindByTagsAndDate(String tag, String fromDate) {
        Map<String, Object> query = Map.of(
                "selector", Map.of(
                        "tags", Map.of("$elemMatch", Map.of("$eq", tag)),
                        "createdAt", Map.of("$gte", fromDate)),
                "use_index", "tags_createdAt_index");

        return couchDbReadDao.read(client -> client.post().uri(DB + "/_explain").bodyValue(query)
                .retrieve().bodyToMono(JsonNode.class));
    }

    public JsonNode getAverageChangesByStatus() {
        return couchDbReadDao.read(client -> client.get()
                .uri(DB + "/_design/analytics/_view/avg_changes_by_status?group=true")
                .retrieve().bodyToMono(JsonNode.class));
    }
}