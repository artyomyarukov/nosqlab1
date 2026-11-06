package com.yarukov.nosql.dao.couchdb;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CouchDbSystemDaoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, JsonNode> replications = new ConcurrentHashMap<>();
    private final AtomicInteger writes = new AtomicInteger();
    private HttpServer server;
    private CouchDbSystemDao dao;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            int status = 200;
            String body = "{}";
            if (path.equals("/_replicator") && method.equals("PUT")) {
                status = 412;
            } else if (path.startsWith("/_replicator/")) {
                if (method.equals("GET")) {
                    JsonNode document = replications.get(path);
                    status = document == null ? 404 : 200;
                    body = document == null ? "{}" : document.toString();
                } else if (method.equals("PUT")) {
                    JsonNode document = objectMapper.readTree(exchange.getRequestBody().readAllBytes());
                    JsonNode previous = replications.get(path);
                    if (previous != null && !previous.path("_rev").equals(document.path("_rev"))) {
                        status = 409;
                    } else {
                        replications.put(path, document);
                        writes.incrementAndGet();
                        status = 201;
                    }
                }
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        dao = new CouchDbSystemDao(WebClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build());
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void configuresBothDatabasesWithoutDuplicateWritesOnRestart() {
        dao.setupDatabase();
        dao.setupDatabase();

        assertEquals(2, replications.size());
        assertEquals(2, writes.get());
        for (String database : new String[]{"users", "tickets"}) {
            JsonNode document = replications.get("/_replicator/" + database + "_replication");
            assertNotNull(document);
            assertEquals("http://admin:password@couchdb1:5984/" + database, document.path("source").asText());
            assertEquals("http://admin:password@couchdb2:5984/" + database, document.path("target").asText());
            assertTrue(document.path("continuous").asBoolean());
            assertTrue(document.path("create_target").asBoolean());
        }
    }

    @Test
    void updatesChangedReplicationUsingExistingRevision() {
        replications.put("/_replicator/users_replication", objectMapper.readTree("""
                {"_rev":"1-existing", "source":"old-source", "target":"old-target", "continuous":false}
                """));

        dao.setupDatabase();

        assertEquals(2, writes.get());
        JsonNode document = replications.get("/_replicator/users_replication");
        assertEquals("1-existing", document.path("_rev").asText());
        assertTrue(document.path("continuous").asBoolean());
        assertEquals("http://admin:password@couchdb2:5984/users", document.path("target").asText());
    }
}
