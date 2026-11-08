package com.example.migrador.connector;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConnectorRegistry {

    private final List<ServerConnector> connectors;

    public ConnectorRegistry(List<ServerConnector> connectors) {
        this.connectors = connectors;
    }

    public List<ServerConnector> all() {
        return connectors;
    }

    public ServerConnector get(String type) {
        return connectors.stream()
                .filter(c -> c.type().equalsIgnoreCase(type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("tipo no soportado"));
    }
}
