package com.yarukov.nosql.config;

import com.basho.riak.client.api.RiakClient;
import com.basho.riak.client.core.RiakCluster;
import com.basho.riak.client.core.RiakNode;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class RiakConfig {

    @Value("${riak.nodes}")
    private String riakNodesConfig;

    private RiakCluster cluster;

    @Bean
    public RiakClient riakClient() {
        List<RiakNode> nodes = new ArrayList<>();
        String[] nodeAddresses = riakNodesConfig.split(",");
        for (String address : nodeAddresses) {
            String[] parts = address.trim().split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);

            RiakNode node = new RiakNode.Builder()
                    .withRemoteAddress(host)
                    .withRemotePort(port)
                    .withMinConnections(2)
                    .withMaxConnections(10)
                    .build();
            nodes.add(node);
        }


        this.cluster = new RiakCluster.Builder(nodes).build();
        this.cluster.start();

        return new RiakClient(this.cluster);
    }

    @PreDestroy
    public void stopRiakCluster() {
        if (this.cluster != null) {
            this.cluster.shutdown();
        }
    }
}