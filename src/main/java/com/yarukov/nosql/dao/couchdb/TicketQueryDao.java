package com.yarukov.nosql.dao.couchdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yarukov.nosql.dto.TicketAnalyticsResponse;
import com.yarukov.nosql.model.couchdb.TicketDoc;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Repository
public class TicketQueryDao {

    static final String DATE_INDEX = "tickets_by_created_at";
    private static final String DB = "/tickets";

    private final CouchDbReadDao couchDbReadDao;

    public TicketQueryDao(CouchDbReadDao couchDbReadDao) {
        this.couchDbReadDao = couchDbReadDao;
    }

    public List<TicketDoc> findByTagsAndDate(String tag, String fromDate, int limit, int skip) {
        Map<String, Object> query = tagsAndDateQuery(tag, fromDate);
        query.put("limit", limit);
        query.put("skip", skip);
        return findTickets(query);
    }

    public JsonNode explainFindByTagsAndDate(String tag, String fromDate) {
        return couchDbReadDao.read(client -> client.post()
                .uri(DB + "/_explain")
                .bodyValue(tagsAndDateQuery(tag, fromDate))
                .retrieve()
                .bodyToMono(JsonNode.class));
    }

    public TicketAnalyticsResponse getAverageChangesByStatus() {
        return couchDbReadDao.read(client -> client.get()
                .uri(DB + "/_design/analytics/_view/avg_changes_by_status?group=true")
                .retrieve()
                .bodyToMono(TicketAnalyticsResponse.class));
    }

    public List<TicketDoc> findByAttachmentType(String contentType) {
        return findTickets(Map.of(
                "selector", Map.of("attachments", Map.of(
                        "$elemMatch", Map.of("contentType", contentType)))));
    }

    private Map<String, Object> tagsAndDateQuery(String tag, String fromDate) {
        return new HashMap<>(Map.of(
                "selector", Map.of(
                        "tags", Map.of("$elemMatch", Map.of("$eq", tag)),
                        "createdAt", Map.of("$gte", fromDate)),
                "use_index", List.of(DATE_INDEX, DATE_INDEX)));
    }

    private List<TicketDoc> findTickets(Map<String, Object> query) {
        try {
            MangoResult result = couchDbReadDao.read(client -> client.post()
                    .uri(DB + "/_find")
                    .bodyValue(query)
                    .retrieve()
                    .bodyToMono(MangoResult.class));
            return result == null || result.docs() == null ? List.of() : result.docs();
        } catch (Exception e) {
            log.error("Ошибка Mango-поиска тикетов", e);
            throw new RuntimeException("Не удалось выполнить поиск обращений в CouchDB", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MangoResult(List<TicketDoc> docs) {}
}
