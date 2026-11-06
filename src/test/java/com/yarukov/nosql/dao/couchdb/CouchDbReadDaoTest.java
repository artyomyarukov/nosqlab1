package com.yarukov.nosql.dao.couchdb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CouchDbReadDaoTest {

    private final AtomicInteger backupCalls = new AtomicInteger();
    private final WebClient backup = WebClient.builder().exchangeFunction(request -> {
        backupCalls.incrementAndGet();
        return Mono.just(ClientResponse.create(HttpStatus.OK).body("резервный узел").build());
    }).build();

    @Test
    void readsFromPrimaryWhenAvailable() {
        WebClient primary = WebClient.builder().exchangeFunction(request ->
                Mono.just(ClientResponse.create(HttpStatus.OK).body("первый узел").build())).build();

        String result = read(new CouchDbReadDao(primary, backup));

        assertEquals("первый узел", result);
        assertEquals(0, backupCalls.get());
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 504})
    void readsFromBackupOnServerError(int status) {
        WebClient primary = WebClient.builder().exchangeFunction(request ->
                Mono.just(ClientResponse.create(HttpStatus.valueOf(status)).build())).build();

        assertEquals("резервный узел", read(new CouchDbReadDao(primary, backup)));
        assertEquals(1, backupCalls.get());
    }

    @Test
    void readsFromBackupOnConnectionFailure() {
        WebClient primary = WebClient.builder().exchangeFunction(request -> Mono.error(
                new WebClientRequestException(new IOException("Нет соединения"),
                        HttpMethod.GET, URI.create("http://localhost/tickets"), new HttpHeaders()))).build();

        assertEquals("резервный узел", read(new CouchDbReadDao(primary, backup)));
        assertEquals(1, backupCalls.get());
    }

    @Test
    void readsFromBackupOnTimeout() {
        WebClient primary = WebClient.builder().exchangeFunction(request -> Mono.never()).build();

        assertEquals("резервный узел", read(new CouchDbReadDao(primary, backup)));
        assertEquals(1, backupCalls.get());
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 409})
    void doesNotHideClientErrorsWithBackup(int status) {
        WebClient primary = WebClient.builder().exchangeFunction(request ->
                Mono.just(ClientResponse.create(HttpStatus.valueOf(status)).build())).build();

        WebClientResponseException error = assertThrows(WebClientResponseException.class,
                () -> read(new CouchDbReadDao(primary, backup)));

        assertEquals(status, error.getStatusCode().value());
        assertEquals(0, backupCalls.get());
    }

    @Test
    void retriesMangoPostOnBackup() {
        WebClient primary = WebClient.builder().exchangeFunction(request ->
                Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build())).build();
        CouchDbReadDao dao = new CouchDbReadDao(primary, backup);

        String result = dao.read(client -> client.post().uri("/tickets/_find")
                .bodyValue(Map.of("selector", Map.of("status", "NEW")))
                .retrieve().bodyToMono(String.class));

        assertEquals("резервный узел", result);
        assertEquals(1, backupCalls.get());
    }

    @Test
    void propagatesFailureWhenBothNodesAreUnavailable() {
        AtomicInteger calls = new AtomicInteger();
        WebClient unavailable = WebClient.builder().exchangeFunction(request -> {
            calls.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build());
        }).build();

        assertThrows(WebClientResponseException.ServiceUnavailable.class,
                () -> read(new CouchDbReadDao(unavailable, unavailable)));
        assertEquals(2, calls.get());
    }

    private String read(CouchDbReadDao dao) {
        return dao.read(client -> client.get().uri("/tickets").retrieve().bodyToMono(String.class));
    }
}
