package com.example.migrador.connector.clickup;

import com.example.migrador.connector.ConnectorSession;
import com.example.migrador.connector.ServerConnector;
import com.example.migrador.domain.ServerConfig;
import org.springframework.stereotype.Component;

/** Conector para ClickUp (API v2). Un "board" aquí es una lista de ClickUp. */
@Component
public class ClickUpConnector implements ServerConnector {

    public static final String TYPE = "CLICKUP";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String displayName() {
        return "ClickUp";
    }

    @Override
    public String usernameLabel() {
        return "(no se usa en ClickUp)";
    }

    @Override
    public boolean usernameRequired() {
        return false;
    }

    @Override
    public String tokenLabel() {
        return "API Token";
    }

    @Override
    public String defaultUrl() {
        return "https://api.clickup.com/api/v2";
    }

    @Override
    public ConnectorSession connect(ServerConfig config) {
        return new ClickUpSession(config);
    }

    @Override
    public String itemUrl(ServerConfig config, String externalId) {
        // La URL de la app web de ClickUp es fija, distinta de la URL de la API; no depende
        // del workspace/lista y redirige correctamente a la tarea.
        return "https://app.clickup.com/t/" + externalId;
    }
}
