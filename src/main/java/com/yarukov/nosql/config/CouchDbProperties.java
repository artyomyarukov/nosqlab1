package com.yarukov.nosql.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@ConfigurationProperties("couchdb")
public record CouchDbProperties(Node primary, Map<String, Node> replicas, String username, String password) {

    public CouchDbProperties {
        replicas = replicas == null ? Map.of() : replicas;
    }

    public record Node(URI url, URI replicationUrl) {}

    public String databaseUrl(Node node, String database) {
        return UriComponentsBuilder.fromUri(node.replicationUrl())
                .userInfo("{username}:{password}")
                .pathSegment(database)
                .encode()
                .buildAndExpand(username, password)
                .toUriString();
    }
}
