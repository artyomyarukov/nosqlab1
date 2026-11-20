package com.yarukov.nosql.dao.couchdb;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

@Slf4j
@Repository
public class CouchDbReadDao {

    public enum Source { REPLICAS, PRIMARY }

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
    private final WebClient primary;
    private final List<WebClient> replicas;
    private final AtomicInteger nextReplica = new AtomicInteger();

    public CouchDbReadDao(@Qualifier("couchDbPrimary") WebClient primary,
                          @Qualifier("couchDbReplicas") List<WebClient> replicas) {
        this.primary = primary;
        this.replicas = List.copyOf(replicas);
    }

    public <T> T read(Function<WebClient, Mono<T>> request) {
        return read(Source.REPLICAS, request);
    }

    public <T> T read(Source source, Function<WebClient, Mono<T>> request) {
        if (source == Source.REPLICAS && !replicas.isEmpty()) {
            int index = Math.floorMod(nextReplica.getAndIncrement(), replicas.size());
            for (int attempt = 0; attempt < replicas.size(); attempt++) {
                try {
                    return request.apply(replicas.get(index)).timeout(READ_TIMEOUT).block();
                } catch (RuntimeException error) {
                    if (!isUnavailable(Exceptions.unwrap(error))) {
                        throw error;
                    }
                    log.warn("Реплика CouchDB {} недоступна, пробуем следующий узел", index);
                }
                index = (index + 1) % replicas.size();
            }
        }
        return request.apply(primary).timeout(READ_TIMEOUT).block();
    }

    private boolean isUnavailable(Throwable error) {
        return error instanceof WebClientRequestException
                || error instanceof TimeoutException
                || error instanceof WebClientResponseException response
                && response.getStatusCode().is5xxServerError();
    }
}
