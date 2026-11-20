package com.yarukov.nosql.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(CouchDbProperties.class)
public class CouchDbConfig {

    private final CouchDbProperties properties;

    @Bean
    public WebClient couchDbPrimary() {
        return createWebClient(properties.primary());
    }

    @Bean
    public List<WebClient> couchDbReplicas() {
        return properties.replicas().values().stream().map(this::createWebClient).toList();
    }

    private WebClient createWebClient(CouchDbProperties.Node node) {
        return WebClient.builder()
                .baseUrl(node.url().toString())
                .defaultHeaders(headers -> headers.setBasicAuth(properties.username(), properties.password(),
                        StandardCharsets.UTF_8))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @PostConstruct
    public void initDatabases() {
        WebClient primary = createWebClient(properties.primary());
        for (String database : List.of("users", "tickets")) {
            primary.put().uri("/{database}", database).retrieve().toBodilessEntity()
                    .onErrorResume(WebClientResponseException.class, error ->
                            error.getStatusCode().value() == 412 ? Mono.empty() : Mono.error(error))
                    .block();
        }
    }
}
