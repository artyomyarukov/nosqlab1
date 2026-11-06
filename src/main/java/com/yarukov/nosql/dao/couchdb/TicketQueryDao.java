package com.yarukov.nosql.dao.couchdb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yarukov.nosql.model.couchdb.TicketDoc;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Repository
public class TicketQueryDao {

    private final WebClient couchClient;
    private ObjectMapper objectMapper;
    private static final String DB = "/tickets";

    public TicketQueryDao(@Qualifier("couchDbClient1") WebClient couchClient) {
        this.couchClient = couchClient;
    }

    @PostConstruct
    public void init() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }


    public List<TicketDoc> findByTagsAndDate(String tag, String fromDate, int limit, int skip) {
        List<TicketDoc> result = new ArrayList<>();
        try {
            String queryJson = String.format("""
                {
                  "selector": {
                    "tags": { "$elemMatch": { "$eq": "%s" } },
                    "createdAt": { "$gte": "%s" }
                  },
                  "limit": %d,
                  "skip": %d,
                  "use_index": "tags_createdAt_index"
                }
                """, tag, fromDate, limit, skip);

            JsonNode root = couchClient.post().uri(DB + "/_find").bodyValue(queryJson)
                    .retrieve().bodyToMono(JsonNode.class).block();

            if (root != null && root.has("docs")) {
                for (JsonNode doc : root.get("docs")) {
                    result.add(objectMapper.treeToValue(doc, TicketDoc.class));
                }
            }
        } catch (Exception e) {
            log.error("Ошибка Mango-поиска тикетов", e);
        }
        return result;
    }

    public JsonNode explainFindByTagsAndDate(String tag, String fromDate) {
        String queryJson = String.format("""
            {
              "selector": {
                "tags": { "$elemMatch": { "$eq": "%s" } },
                "createdAt": { "$gte": "%s" }
              },
              "use_index": "tags_createdAt_index"
            }
            """, tag, fromDate);

        return couchClient.post().uri(DB + "/_explain").bodyValue(queryJson)
                .retrieve().bodyToMono(JsonNode.class).block();
    }

    public JsonNode getAverageChangesByStatus() {
        return couchClient.get().uri(DB + "/_design/analytics/_view/avg_changes_by_status?group=true")
                .retrieve().bodyToMono(JsonNode.class).block();
    }
}