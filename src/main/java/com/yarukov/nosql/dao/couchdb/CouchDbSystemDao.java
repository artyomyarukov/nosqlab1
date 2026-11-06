package com.yarukov.nosql.dao.couchdb;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.client.WebClient;

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

    public void startReplication() {
        try {
            String replicationJson = """
                {
                  "source": "http://admin:password@couchdb1:5984/tickets",
                  "target": "http://admin:password@couchdb2:5984/tickets",
                  "create_target": true,
                  "continuous": true
                }
                """;
            couchClient1.post().uri("/_replicator").bodyValue(replicationJson)
                    .retrieve().toBodilessEntity().block();
            log.info("Система: Репликация из couchdb1 в couchdb2 успешно запущена!");
        } catch (Exception e) {
            log.error("Ошибка запуска репликации", e);
        }
    }
}