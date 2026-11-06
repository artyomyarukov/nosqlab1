package com.yarukov.nosql.dao.couchdb;

import com.yarukov.nosql.model.couchdb.TicketDoc;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
public class TicketCrudDao {

    private final WebClient couchClient;
    private ObjectMapper objectMapper;
    private static final String DB = "/tickets";

    public TicketCrudDao(@Qualifier("couchDbClient1") WebClient couchClient) {
        this.couchClient = couchClient;
    }
    @PostConstruct
    public void init() {
        this.objectMapper = new ObjectMapper();
    }


    public TicketDoc save(TicketDoc ticket) {
        try {
            JsonNode response = couchClient.put()
                    .uri(DB + "/" + ticket.getId())
                    .bodyValue(ticket)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response != null && response.has("rev")) {
                ticket.setRev(response.get("rev").asText());
            }
            log.info("Тикет id={} сохранен в CouchDB (rev={})", ticket.getId(), ticket.getRev());
            return ticket;
        } catch (Exception e) {
            log.error("Ошибка сохранения тикета", e);
            throw new RuntimeException("Не удалось сохранить тикет в CouchDB", e);
        }
    }

    public Optional<TicketDoc> findById(String id) {
        try {
            TicketDoc doc = couchClient.get()
                    .uri(DB + "/" + id)
                    .retrieve()
                    .bodyToMono(TicketDoc.class)
                    .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty())
                    .block();
            return Optional.ofNullable(doc);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось прочитать документ из CouchDB", e);
        }
    }

    public List<TicketDoc> findAll() {
        List<TicketDoc> tickets = new ArrayList<>();
        try {
            JsonNode root = couchClient.get()
                    .uri(DB + "/_all_docs?include_docs=true")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (root != null && root.has("rows")) {
                for (JsonNode row : root.get("rows")) {
                    JsonNode docNode = row.get("doc");
                    if (docNode != null && !docNode.get("_id").asText().startsWith("_design/")) {
                        tickets.add(objectMapper.treeToValue(docNode, TicketDoc.class));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Ошибка получения всех тикетов", e);
            throw new RuntimeException("Не удалось получить обращения из CouchDB", e);
        }
        return tickets;
    }
}