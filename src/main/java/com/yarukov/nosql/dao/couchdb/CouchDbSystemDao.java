package com.yarukov.nosql.dao.couchdb;

import com.yarukov.nosql.config.CouchDbProperties;
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

    private final WebClient primary;
    private final CouchDbProperties properties;

    public CouchDbSystemDao(@Qualifier("couchDbPrimary") WebClient primary, CouchDbProperties properties) {
        this.primary = primary;
        this.properties = properties;
    }

    @PostConstruct
    public void setupDatabase() {
        createMangoIndex("tickets", TicketQueryDao.DATE_INDEX, "createdAt", TicketQueryDao.DATE_INDEX);
        createMangoIndex("users", "username_index", "username", null);
        createMapReduceView();
        createValidationTrigger();
        startContinuousReplication();
    }

    private void createMangoIndex(String database, String name, String field, String designDoc) {
        Map<String, Object> index = new HashMap<>(Map.of(
                "index", Map.of("fields", List.of(field)),
                "name", name,
                "type", "json"));
        if (designDoc != null) {
            index.put("ddoc", designDoc);
        }
        try {
            primary.post().uri("/{database}/_index", database).bodyValue(index).retrieve().toBodilessEntity().block();
            log.info("Mango-индекс '{}' в базе '{}' готов", name, database);
        } catch (Exception e) {
            log.warn("Не удалось подготовить Mango-индекс '{}' в базе '{}'", name, database, e);
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
            primary.put().uri("/tickets/_design/analytics").
                    bodyValue(designDoc).retrieve().toBodilessEntity().block();
            log.info("MapReduce View для аналитики готово");
        } catch (Exception e) {
            log.debug("MapReduce View уже существует");
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
            primary.put().uri("/tickets/_design/validation").bodyValue(validationDoc).retrieve().toBodilessEntity().block();
            log.info("Система: Триггер validate_doc_update установлен");
        } catch (Exception e) {
            log.debug("Триггер валидации уже существует");
        }
    }

    private void startContinuousReplication() {
        try {
            primary.put().uri("/_replicator").retrieve().toBodilessEntity()
                    .onErrorResume(WebClientResponseException.class, e ->
                            e.getStatusCode().value() == 412 ? Mono.empty() : Mono.error(e))
                    .block();
        } catch (Exception e) {
            log.error("Не удалось подготовить базу заданий репликации CouchDB", e);
            return;
        }

        for (String database : List.of("users", "tickets")) {
            properties.replicas().forEach((jobSuffix, replica) -> configureReplication(database, jobSuffix, replica));
        }
    }

    private void configureReplication(String database, String jobSuffix, CouchDbProperties.Node replica) {
        String replicationId = database + "_" + jobSuffix;
        String source = properties.databaseUrl(properties.primary(), database);
        String target = properties.databaseUrl(replica, database);
        String path = "/_replicator/" + replicationId;

        try {
            JsonNode existing = primary.get().uri(path).retrieve().bodyToMono(JsonNode.class)
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

            primary.put().uri(path).bodyValue(replication).retrieve().toBodilessEntity().block();
            log.info("Непрерывная репликация базы '{}' на реплику {} настроена", database, jobSuffix);
        } catch (Exception e) {
            log.error("Не удалось настроить репликацию базы '{}' на реплику {}", database, jobSuffix, e);
        }
    }
}
