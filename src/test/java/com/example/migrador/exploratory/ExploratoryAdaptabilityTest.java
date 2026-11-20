package com.example.migrador.exploratory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.migrador.connector.ConnectorRegistry;
import com.example.migrador.connector.ServerConnector;
import com.example.migrador.connector.clickup.ClickUpConnector;
import com.example.migrador.connector.jira.JiraConnector;

class ExploratoryAdaptabilityTest {

    @Test
    void registroAceptaUnConectorAdicionalSinCambiarLaLogicaCentral() {
        ServerConnector adicional = mock(ServerConnector.class);
        when(adicional.type()).thenReturn("TRELLO");
        ConnectorRegistry registry = new ConnectorRegistry(
                List.of(new ClickUpConnector(), new JiraConnector(), adicional));

        assertEquals(3, registry.all().size());
        assertSame(adicional, registry.get("TRELLO"));
    }
}
