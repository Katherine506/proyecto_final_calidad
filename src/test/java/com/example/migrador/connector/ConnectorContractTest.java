package com.example.migrador.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.migrador.connector.clickup.ClickUpConnector;
import com.example.migrador.connector.jira.JiraConnector;
import com.example.migrador.domain.ServerConfig;

/*
 * ConnectorContractTest — metadatos de los conectores y el registro.
 * connect() solo arma la sesión; no llama a la red.
 */
class ConnectorContractTest {

    /*
     * CP-U01-16 - Tipo, etiquetas y URL del ítem en ClickUp
     * Esperada: el tipo es CLICKUP, el usuario no se usa y la URL abre la tarea.
     * Obtenida: el tipo es CLICKUP, el usuario no se usa y la URL abre la tarea.
     */
    @Test
    void clickUpMetadatosYUrlDelItem() {
        ClickUpConnector clickUp = new ClickUpConnector();

        assertEquals("CLICKUP", clickUp.type());
        assertEquals("ClickUp", clickUp.displayName());
        assertEquals("(no se usa en ClickUp)", clickUp.usernameLabel());
        assertEquals("API Token", clickUp.tokenLabel());
        assertFalse(clickUp.usernameRequired());
        assertEquals("https://api.clickup.com/api/v2", clickUp.defaultUrl());
        assertEquals("https://app.clickup.com/t/cu-1", clickUp.itemUrl(new ServerConfig(), "cu-1"));
        assertNotNull(clickUp.connect(config("https://example.test/", "CLICKUP")));
        assertNotNull(clickUp.connect(config(null, "CLICKUP")));
        assertNotNull(clickUp.connect(config("  ", "CLICKUP")));
    }

    /*
     * CP-U07-17 - Tipo, etiquetas y URL del ítem en Jira
     * Esperada: el tipo es JIRA, hay URL por defecto y la URL abre el browse.
     * Obtenida: el tipo es JIRA, hay URL por defecto y la URL abre el browse.
     */
    @Test
    void jiraMetadatosYUrlDelItem() {
        JiraConnector jira = new JiraConnector();
        ServerConfig config = config("https://qa.atlassian.net/", "JIRA");

        assertEquals("JIRA", jira.type());
        assertEquals("Jira (API v3)", jira.displayName());
        assertEquals("Usuario (email)", jira.usernameLabel());
        assertEquals("API Token", jira.tokenLabel());
        assertEquals("https://su-dominio.atlassian.net", jira.defaultUrl());
        assertTrue(jira.usernameRequired());
        assertEquals("https://qa.atlassian.net/browse/MIG-1", jira.itemUrl(config, "MIG-1"));
        config.setUrl(null);
        assertEquals("/browse/MIG-1", jira.itemUrl(config, "MIG-1"));
        config.setUrl("https://example.test");
        assertNotNull(jira.connect(config));
    }

    /*
     * CP-U15-01 - Tipo en otro case y tipo desconocido
     * Esperada: encuentra el conector e ignora mayúsculas; uno desconocido se rechaza.
     * Obtenida: encuentra el conector e ignora mayúsculas; uno desconocido se rechaza.
     */
    @Test
    void registroTipoConOtroCaseLoEncuentraYRechazaUnoDesconocido() {
        ConnectorRegistry registry = new ConnectorRegistry(List.of(new ClickUpConnector(), new JiraConnector()));

        assertEquals(2, registry.all().size());
        assertEquals("CLICKUP", registry.get("clickup").type());
        assertEquals("JIRA", registry.get("JIRA").type());
        assertEquals("tipo no soportado",
                assertThrows(IllegalArgumentException.class, () -> registry.get("TRELLO")).getMessage());
    }

    private static ServerConfig config(String url, String type) {
        ServerConfig config = new ServerConfig();
        config.setUrl(url);
        config.setType(type);
        config.setUsername("qa@example.com");
        config.setApiToken("token-local");
        return config;
    }
}
