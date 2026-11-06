package com.yarukov.nosql.dao.couchdb;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

@Slf4j
@Repository
public class CouchDbReadDao {

    private final WebClient couchClient1;
    private final WebClient couchClient2;
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    public CouchDbReadDao(@Qualifier("couchDbClient1") WebClient couchClient1,
                          @Qualifier("couchDbClient2") WebClient couchClient2) {
        this.couchClient1 = couchClient1;
        this.couchClient2 = couchClient2;
    }

    public <T> T read(Function<WebClient, Mono<T>> request) {
        return Mono.defer(() -> request.apply(couchClient1))
                .timeout(READ_TIMEOUT)
                .onErrorResume(this::isUnavailable, error -> {
                    log.warn("Первый узел CouchDB недоступен, читаем со второго узла");
                    return Mono.defer(() -> request.apply(couchClient2)).timeout(READ_TIMEOUT);
                })
                .block();
    }

    private boolean isUnavailable(Throwable error) {
        return error instanceof WebClientRequestException
                || error instanceof TimeoutException
                || error instanceof WebClientResponseException response
                && response.getStatusCode().is5xxServerError();
    }
}
