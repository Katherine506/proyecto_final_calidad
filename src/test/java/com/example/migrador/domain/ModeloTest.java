package com.example.migrador.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/*
 * U17. Los objetos de dominio no se mockean: se construyen y se leen.
 */
class ModeloTest {

    /*
     * CP-U17-01 - ServerConfig y User con valores asignados
     * Esperada: los valores leídos son los asignados.
     * Obtenida: los valores leídos son los asignados.
     */
    @Test
    void serverConfigYUserConservanLosValoresAsignados() {
        ServerConfig server = new ServerConfig();
        server.setId(4L);
        server.setName("Jira QA");
        server.setType("JIRA");
        server.setUrl("https://qa.atlassian.net");
        server.setUsername("qa@example.com");
        server.setApiToken("token-local");

        assertEquals(4L, server.getId());
        assertEquals("Jira QA", server.getName());
        assertEquals("JIRA", server.getType());
        assertEquals("https://qa.atlassian.net", server.getUrl());
        assertEquals("qa@example.com", server.getUsername());
        assertEquals("token-local", server.getApiToken());

        User user = new User();
        user.setId(8L);
        user.setUsername("jeffry");
        user.setPassword("clave-local");
        assertEquals(8L, user.getId());
        assertEquals("jeffry", user.getUsername());
        assertEquals("clave-local", user.getPassword());
    }

    /*
     * CP-U17-02 - ItemMapping y StandardItem
     * Esperada: la equivalencia y el ítem conservan sus campos.
     * Obtenida: la equivalencia y el ítem conservan sus campos.
     */
    @Test
    void itemMappingYStandardItemConservanLaEquivalencia() {
        ItemMapping mapping = new ItemMapping(1L, 2L, "CU-1", "MIG-1");
        mapping.setTargetItemId("MIG-2");

        assertEquals(1L, mapping.getSourceConfigId());
        assertEquals(2L, mapping.getTargetConfigId());
        assertEquals("CU-1", mapping.getSourceItemId());
        assertEquals("MIG-2", mapping.getTargetItemId());

        StandardItem item = new StandardItem("CU-1", "Título", "Desc", List.of("backend"), ItemKind.BUG);
        assertEquals("CU-1", item.externalId());
        assertEquals("Título", item.title());
        assertEquals(ItemKind.BUG, item.kind());
        assertEquals(ItemKind.STORY, ItemKind.valueOf("STORY"));

        BoardOption board = new BoardOption("QA", "Quality (QA)");
        assertEquals("QA", board.id());
        assertEquals("Quality (QA)", board.name());
    }
}
