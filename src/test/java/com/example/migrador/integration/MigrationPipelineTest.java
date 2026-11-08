package com.example.migrador.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.migrador.domain.ItemMapping;
import com.example.migrador.domain.ServerConfig;
import com.example.migrador.repository.ItemMappingRepository;
import com.example.migrador.repository.ServerConfigRepository;
import com.example.migrador.service.MigrationResult;
import com.example.migrador.service.MigrationService;
import com.example.migrador.support.HttpJson;

import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/*
 * MigrationPipelineTest — integración extractor + ADF + sincronización.
 *
 * Las APIs se sustituyen por MockWebServer. Las equivalencias se guardan en H2.
 * Las reglas de crear/actualizar ya están en ItemCreatorTest; aquí se ve el flujo junto.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MigrationPipelineTest {

    @Autowired
    MigrationService migrationService;
    @Autowired
    ServerConfigRepository configs;
    @Autowired
    ItemMappingRepository mappings;

    private final JsonMapper json = JsonMapper.builder().build();
    private MockWebServer clickUp;
    private MockWebServer jira;

    @BeforeEach
    void setUp() throws Exception {
        clickUp = new MockWebServer();
        jira = new MockWebServer();
        clickUp.start();
        jira.start();
        mappings.deleteAll();
        configs.deleteAll();
    }

    @AfterEach
    void tearDown() throws Exception {
        clickUp.shutdown();
        jira.shutdown();
    }

    /*
     * CP-U03-06 - ClickUp se transforma a StandardItem y luego a Jira
     * Esperada: los campos del alcance mantienen sus valores o equivalencias.
     * Obtenida: el issue lleva ADF, tipo Bug, y queda la equivalencia.
     */
    @Test
    void migrateClickUpHaciaJiraCreaIssueYEquivalencia() throws Exception {
        // Given
        ServerConfig origen = guardar("ClickUp", "CLICKUP", clickUp.url("/").toString());
        ServerConfig destino = guardar("Jira", "JIRA", jira.url("/").toString());
        clickUp.enqueue(HttpJson.json(200, """
                {"tasks":[{"id":"cu-1","name":"Pingüino","text_content":"Hola\\nMundo",
                  "tags":[{"name":"bug"},{"name":"urgente"}]}]}
                """));
        clickUp.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));
        jira.enqueue(HttpJson.json(201, "{\"key\":\"MIG-1\"}"));

        // When
        MigrationResult resultado = migrationService.migrate(
                origen.getId(), destino.getId(), List.of("LIST1"), "MIG");

        // Then
        assertFalse(resultado.failed());
        assertEquals(1, resultado.created());
        assertEquals(1, mappings.count());
        assertEquals("cu-1", mappings.findAll().get(0).getSourceItemId());
        assertEquals("MIG-1", mappings.findAll().get(0).getTargetItemId());
        clickUp.takeRequest();
        clickUp.takeRequest();
        JsonNode body = json.readTree(jira.takeRequest().getBody().readUtf8());
        assertEquals("Bug", body.at("/fields/issuetype/name").asText());
        assertEquals("Hola", body.at("/fields/description/content/0/content/0/text").asText());
        assertEquals("Mundo", body.at("/fields/description/content/1/content/0/text").asText());
        assertEquals("urgente", body.at("/fields/labels/0").asText());
    }

    /*
     * CP-U09-06 - Segunda pasada del mismo elemento
     * Esperada: se actualiza y no se crea un duplicado.
     * Obtenida: se actualiza y no se crea un duplicado.
     */
    @Test
    void migrateSegundaPasadaActualizaSinDuplicar() throws Exception {
        // Given
        ServerConfig origen = guardar("ClickUp", "CLICKUP", clickUp.url("/").toString());
        ServerConfig destino = guardar("Jira", "JIRA", jira.url("/").toString());
        mappings.save(new ItemMapping(origen.getId(), destino.getId(), "cu-1", "MIG-1"));
        clickUp.enqueue(HttpJson.json(200, """
                {"tasks":[{"id":"cu-1","name":"Editado","text_content":"nuevo","tags":[]}]}
                """));
        clickUp.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));
        jira.enqueue(HttpJson.json(204, ""));

        // When
        MigrationResult resultado = migrationService.migrate(
                origen.getId(), destino.getId(), List.of("LIST1"), "MIG");

        // Then
        assertEquals(0, resultado.created());
        assertEquals(1, resultado.updated());
        assertEquals(1, mappings.count());
        RecordedRequest update = jira.takeRequest();
        assertEquals("PUT", update.getMethod());
        assertTrue(update.getPath().contains("/rest/api/3/issue/MIG-1"));
        assertEquals(1, jira.getRequestCount());
    }

    /*
     * CP-U04-13 - Lista anidada dentro de una tabla, de Jira a ClickUp
     * Esperada: el texto de la lista llega en orden al destino.
     * Obtenida: el texto de la lista llega en orden al destino.
     */
    @Test
    void migrateListaDentroDeTablaLlegaEnOrdenAClickUp() throws Exception {
        // Given
        ServerConfig origen = guardar("Jira", "JIRA", jira.url("/").toString());
        ServerConfig destino = guardar("ClickUp", "CLICKUP", clickUp.url("/").toString());
        jira.enqueue(HttpJson.json(200, """
                {"issues":[{"key":"MIG-7","fields":{
                  "summary":"Tabla","issuetype":{"name":"Story"},"labels":["backend"],
                  "description":{"type":"doc","version":1,"content":[
                    {"type":"paragraph","content":[{"type":"text","text":"antes"}]},
                    {"type":"table","content":[{"type":"tableRow","content":[{"type":"tableCell","content":[
                      {"type":"bulletList","content":[
                        {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"nido-a"}]}]},
                        {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"nido-b"}]}]}
                      ]}
                    ]}]}]},
                    {"type":"paragraph","content":[{"type":"text","text":"despues"}]}
                  ]}
                }}]}
                """));
        clickUp.enqueue(HttpJson.json(201, "{\"id\":\"cu-9\"}"));

        // When
        MigrationResult resultado = migrationService.migrate(
                origen.getId(), destino.getId(), List.of("MIG"), "LIST9");

        // Then
        assertEquals(1, resultado.created());
        String descripcion = json.readTree(clickUp.takeRequest().getBody().readUtf8()).get("description").asText();
        assertTrue(descripcion.indexOf("antes") < descripcion.indexOf("nido-a"));
        assertTrue(descripcion.indexOf("nido-a") < descripcion.indexOf("nido-b"));
        assertTrue(descripcion.indexOf("nido-b") < descripcion.indexOf("despues"));
        assertEquals("MIG-7", mappings.findAll().get(0).getSourceItemId());
        assertEquals("cu-9", mappings.findAll().get(0).getTargetItemId());
    }

    /*
     * CP-U11-05 - Un alta falla y el siguiente ítem es válido
     * Esperada: el válido se guarda y el fallido no.
     * Obtenida: el válido se guarda y el fallido no.
     */
    @Test
    void migrateUnAltaFallaElSiguienteSeGuarda() {
        // Given
        ServerConfig origen = guardar("ClickUp", "CLICKUP", clickUp.url("/").toString());
        ServerConfig destino = guardar("Jira", "JIRA", jira.url("/").toString());
        clickUp.enqueue(HttpJson.json(200, """
                {"tasks":[
                  {"id":"malo","name":"Malo","text_content":"x","tags":[]},
                  {"id":"bueno","name":"Bueno","text_content":"y","tags":[]}
                ]}
                """));
        clickUp.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));
        jira.enqueue(HttpJson.json(500, "{\"error\":\"caido\"}"));
        jira.enqueue(HttpJson.json(201, "{\"key\":\"MIG-2\"}"));

        // When
        MigrationResult resultado = migrationService.migrate(
                origen.getId(), destino.getId(), List.of("LIST1"), "MIG");

        // Then
        assertEquals(1, resultado.created());
        assertEquals(1, resultado.itemErrors());
        assertEquals(1, mappings.count());
        assertEquals("bueno", mappings.findAll().get(0).getSourceItemId());
    }

    private ServerConfig guardar(String name, String type, String url) {
        ServerConfig config = new ServerConfig();
        config.setName(name);
        config.setType(type);
        config.setUrl(url);
        config.setApiToken("token-local");
        config.setUsername("qa@example.com");
        return configs.save(config);
    }
}
