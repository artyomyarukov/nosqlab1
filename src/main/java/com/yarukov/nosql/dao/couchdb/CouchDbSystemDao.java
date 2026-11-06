package com.yarukov.nosql.dao.couchdb;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Repository
public class CouchDbSystemDao {

    private final WebClient couchClient1;

    public CouchDbSystemDao(@Qualifier("couchDbClient1") WebClient couchClient1) {
        this.couchClient1 = couchClient1;
    }

    @PostConstruct
    public void setupDatabase() {
        createMangoIndex();
        createMapReduceView();
        createValidationTrigger();
        startContinuousReplication();
    }

    private void createMangoIndex() {
        try {
            String indexJson = """
                    {
                      "index": { "fields": ["tags", "createdAt"] },
                      "name": "tags_createdAt_index",
                      "type": "json"
                    }
                    """;
            couchClient1.post().uri("/tickets/_index").bodyValue(indexJson).retrieve().toBodilessEntity().block();
            log.info("Система: Mango-индекс (tags, createdAt) готов");
        } catch (Exception e) {
            log.debug("Система: Mango-индекс уже существует");
        }
    }

    private void createMapReduceView() {
        try {
            String designDoc = """
                    {
                      "_id": "_design/analytics",
                      "views": {
                        "avg_changes_by_status": {
                          "map": "function (doc) { if (doc.status && doc.changeCount) { emit(doc.status, doc.changeCount); } }",
                          "reduce": "_stats"
                        }
                      }
                    }
                    """;
            couchClient1.put().uri("/tickets/_design/analytics").
                    bodyValue(designDoc).retrieve().toBodilessEntity().block();
            log.info("Система: MapReduce View для аналитики готово");
        } catch (Exception e) {
            log.debug("Система: MapReduce View уже существует");
        }
    }


    private void createValidationTrigger() {
        try {
            String validationDoc = """
                    {
                      "_id": "_design/validation",
                      "validate_doc_update": "function(newDoc, oldDoc, userCtx) { if (!newDoc.status) throw({forbidden: 'Статус тикета обязателен!'}); }"
                    }
                    """;
            couchClient1.put().uri("/tickets/_design/validation").bodyValue(validationDoc).retrieve().toBodilessEntity().block();
            log.info("Система: Триггер validate_doc_update установлен");
        } catch (Exception e) {
            log.debug("Система: Триггер валидации уже существует");
        }
    }

    private void startContinuousReplication() {
        try {
            couchClient1.put().uri("/_replicator").retrieve().toBodilessEntity()
                    .onErrorResume(WebClientResponseException.class, e ->
                            e.getStatusCode().value() == 412 ? Mono.empty() : Mono.error(e))
                    .block();
        } catch (Exception e) {
            log.error("Не удалось подготовить базу заданий репликации CouchDB", e);
            return;
        }

        for (String database : List.of("users", "tickets")) {
            configureReplication(database);
        }
    }

    private void configureReplication(String database) {
        String replicationId = database + "_replication";
        String source = "http://admin:password@couchdb1:5984/" + database;
        String target = "http://admin:password@couchdb2:5984/" + database;
        String path = "/_replicator/" + replicationId;

        try {
            JsonNode existing = couchClient1.get().uri(path).retrieve().bodyToMono(JsonNode.class)
                    .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty())
                    .block();

            if (existing != null
                    && source.equals(existing.path("source").asText())
                    && target.equals(existing.path("target").asText())
                    && existing.path("continuous").asBoolean()
                    && existing.path("create_target").asBoolean()) {
                log.info("Непрерывная репликация базы '{}' уже настроена", database);
                return;
            }

            Map<String, Object> replication = new HashMap<>(Map.of(
                    "source", source,
                    "target", target,
                    "create_target", true,
                    "continuous", true));
            if (existing != null) {
                replication.put("_rev", existing.path("_rev").asText());
            }

            couchClient1.put().uri(path).bodyValue(replication).retrieve().toBodilessEntity().block();
            log.info("Непрерывная репликация базы '{}' из couchdb1 в couchdb2 настроена", database);
        } catch (Exception e) {
            log.error("Не удалось настроить репликацию базы '{}'", database, e);
        }
    }
}
