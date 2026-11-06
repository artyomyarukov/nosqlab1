package com.yarukov.nosql.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Configuration
public class CouchDbConfig {

    @Value("${couchdb.node1.url}")
    private String node1Url;

    @Value("${couchdb.node2.url}")
    private String node2Url;

    @Value("${couchdb.username}")
    private String username;

    @Value("${couchdb.password}")
    private String password;

    @Bean(name = "couchDbClient1")
    public WebClient couchDbClient1() {
        return createWebClient(node1Url);
    }

    @Bean(name = "couchDbClient2")
    public WebClient couchDbClient2() {
        return createWebClient(node2Url);
    }

    private WebClient createWebClient(String baseUrl) {
        String authHeader = "Basic " + Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes(StandardCharsets.UTF_8)
        );

        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
    }

    @PostConstruct
    public void initDatabases() {
        WebClient client = createWebClient(node1Url);
        createDatabaseIfNotExists(client, "users");
        createDatabaseIfNotExists(client, "tickets");
    }

    private void createDatabaseIfNotExists(WebClient client, String dbName) {
        try {
            client.get()
                    .uri("/" + dbName)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, response -> {
                        if (response.statusCode().value() == 404) {
                            log.info("База данных '{}' не найдена в CouchDB. Создаем...", dbName);
                            client.put().uri("/" + dbName).retrieve().toBodilessEntity().block();
                            log.info("База данных '{}' успешно создана в CouchDB!", dbName);
                        }
                        return response.createException();
                    })
                    .toBodilessEntity()
                    .block();
            log.info("База данных '{}' готова к работе в CouchDB", dbName);
        } catch (Exception e) {
            log.debug("Инициализация базы '{}' завершена", dbName);
        }
    }
}