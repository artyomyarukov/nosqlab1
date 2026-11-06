package com.yarukov.nosql.dao.couchdb;

import com.yarukov.nosql.model.couchdb.UserDoc;
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
import java.util.Map;
import java.util.Optional;

@Slf4j
@Repository
public class UserCouchDao {

    private final WebClient couchClient;
    private ObjectMapper objectMapper;
    private static final String DB = "/users";

    public UserCouchDao(@Qualifier("couchDbClient1") WebClient couchClient) {
        this.couchClient = couchClient;
    }

    @PostConstruct
    public void init() {
        this.objectMapper = new ObjectMapper();
    }

    public UserDoc save(UserDoc user) {
        try {
            JsonNode response = couchClient.put()
                    .uri(DB + "/" + user.getId())
                    .bodyValue(user)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response != null && response.has("rev")) {
                user.setRev(response.get("rev").asText());
            }
            log.info("Пользователь id={} сохранен в CouchDB (rev={})", user.getId(), user.getRev());
            return user;
        } catch (Exception e) {
            log.error("Ошибка сохранения пользователя в CouchDB", e);
            throw new RuntimeException("Не удалось сохранить пользователя в CouchDB", e);
        }
    }


    public Optional<UserDoc> findById(String id) {
        try {
            UserDoc doc = couchClient.get()
                    .uri(DB + "/" + id)
                    .retrieve()
                    .bodyToMono(UserDoc.class)
                    .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty())
                    .block();
            return Optional.ofNullable(doc);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось прочитать документ из CouchDB", e);
        }
    }

    public List<UserDoc> findAll() {
        List<UserDoc> users = new ArrayList<>();
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
                        users.add(objectMapper.treeToValue(docNode, UserDoc.class));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Ошибка получения списка пользователей из CouchDB", e);
            throw new RuntimeException("Не удалось получить список пользователей из CouchDB", e);
        }
        return users;
    }

    public Optional<UserDoc> findByUsername(String username) {
        try {
            Map<String, Object> query = Map.of("selector", Map.of("username", username));

            JsonNode root = couchClient.post()
                    .uri(DB + "/_find")
                    .bodyValue(query)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (root != null && root.has("docs") && !root.get("docs").isEmpty()) {
                UserDoc user = objectMapper.treeToValue(root.get("docs").get(0), UserDoc.class);
                return Optional.of(user);
            }
        } catch (Exception e) {
            log.error("Ошибка поиска пользователя по username в CouchDB", e);
            throw new RuntimeException("Не удалось найти пользователя в CouchDB", e);
        }
        return Optional.empty();
    }
}