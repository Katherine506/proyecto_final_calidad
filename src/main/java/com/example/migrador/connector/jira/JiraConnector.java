package com.example.migrador.connector.jira;

import com.example.migrador.connector.ConnectorSession;
import com.example.migrador.connector.ServerConnector;
import com.example.migrador.domain.ServerConfig;
import org.springframework.stereotype.Component;

/** Conector para Jira Cloud (REST API v3). Un "board" aquí es un proyecto de Jira. */
@Component
public class JiraConnector implements ServerConnector {

    public static final String TYPE = "JIRA";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String displayName() {
        return "Jira (API v3)";
    }

    @Override
    public String usernameLabel() {
        return "Usuario (email)";
    }

    @Override
    public String tokenLabel() {
        return "API Token";
    }

    @Override
    public String defaultUrl() {
        return "https://su-dominio.atlassian.net";
    }

    @Override
    public ConnectorSession connect(ServerConfig config) {
        return new JiraSession(config);
    }

    @Override
    public String itemUrl(ServerConfig config, String externalId) {
        String base = config.getUrl() == null ? "" : config.getUrl().trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/browse/" + externalId;
    }
}
