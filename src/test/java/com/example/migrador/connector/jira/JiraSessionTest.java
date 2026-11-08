package com.example.migrador.connector.jira;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import com.example.migrador.domain.BoardOption;
import com.example.migrador.domain.ItemKind;
import com.example.migrador.domain.ServerConfig;
import com.example.migrador.domain.StandardItem;
import com.example.migrador.support.HttpJson;

import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/*
 * Casos preliminares del conector Jira (U03, U05, U07).
 * Un método = un caso. La API se sustituye con MockWebServer.
 */
class JiraSessionTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private MockWebServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    /*
     * CP-U03-02 - Elemento Jira con nombre, descripción y etiquetas
     * Esperada: se genera un StandardItem con los valores correspondientes.
     * Obtenida: se genera un StandardItem con los valores correspondientes.
     */
    @Test
    void jiraGeneraStandardItem() {
        // Given
        server.enqueue(HttpJson.json(200, issue("MIG-1", "Resumen", "Story", "Hola", "backend")));

        // When
        StandardItem obtenida = session().extractAll(List.of("MIG")).get(0);

        // Then
        assertEquals("MIG-1", obtenida.externalId());
        assertEquals("Resumen", obtenida.title());
        assertEquals("Hola", obtenida.description());
        assertEquals(List.of("backend"), obtenida.labels());
        assertEquals(ItemKind.STORY, obtenida.kind());
    }

    /*
     * CP-U03-08 - Elemento Jira con descripción ausente
     * Esperada: la ausencia de contenido no modifica el nombre ni las etiquetas.
     * Obtenida: la descripción queda vacía y el nombre y las etiquetas no cambian.
     */
    @Test
    void descripcionAusenteDejaElTextoVacio() {
        // Given
        server.enqueue(HttpJson.json(200, """
                {"issues":[{"key":"MIG-3","fields":{"summary":"Nombre","issuetype":{"name":"Story"},
                  "labels":["backend"],"description":null}}]}
                """));

        // When
        StandardItem obtenida = session().extractAll(List.of("MIG")).get(0);

        // Then
        assertEquals("", obtenida.description());
        assertEquals("Nombre", obtenida.title());
        assertEquals(List.of("backend"), obtenida.labels());
    }

    /*
     * CP-U05-01 - Elemento identificado como bug
     * Esperada: se asigna el tipo Bug en Jira.
     * Obtenida: se asigna el tipo Bug en Jira.
     */
    @Test
    void bugEnviaTipoBug() throws Exception {
        // Given
        server.enqueue(HttpJson.json(201, "{\"key\":\"MIG-8\"}"));
        StandardItem bug = new StandardItem("cu-1", "Fallo", "detalle", List.of("backend"), ItemKind.BUG);

        // When
        String obtenida = session().create(bug, "MIG");

        // Then
        assertEquals("MIG-8", obtenida);
        JsonNode body = json.readTree(server.takeRequest().getBody().readUtf8());
        assertEquals("Bug", body.at("/fields/issuetype/name").asText());
    }

    /*
     * CP-U05-08 - Elemento identificado como story
     * Esperada: se asigna el tipo Story en Jira.
     * Obtenida: se asigna el tipo Story en Jira.
     */
    @Test
    void storyEnviaTipoStory() throws Exception {
        // Given
        server.enqueue(HttpJson.json(201, "{\"key\":\"MIG-2\"}"));
        StandardItem story = new StandardItem("cu-2", "Historia", "d", List.of(), ItemKind.STORY);

        // When
        session().create(story, "MIG");

        // Then
        JsonNode body = json.readTree(server.takeRequest().getBody().readUtf8());
        assertEquals("Story", body.at("/fields/issuetype/name").asText());
    }

    /*
     * CP-U05-09 - Issue Jira de tipo Task
     * Esperada: se asigna el tipo Task.
     * Obtenida: se lee como Story.
     */
    @Test
    void issueTaskSeLeeComoTask() {
        // Given
        server.enqueue(HttpJson.json(200, """
                {"issues":[{"key":"MIG-3","fields":{"summary":"Trabajo","issuetype":{"name":"Task"},
                  "labels":[],"description":null}}]}
                """));

        // When
        StandardItem obtenida = session().extractAll(List.of("MIG")).get(0);

        // Then
        assertEquals("TASK", obtenida.kind().name());
    }

    /*
     * CP-U07-01 - Solicitud con token válido y respuesta HTTP 200
     * Esperada: la respuesta se interpreta y se obtienen los datos solicitados.
     * Obtenida: la respuesta se interpreta y se obtienen los datos solicitados.
     */
    @Test
    void http200InterpretaLaRespuesta() throws Exception {
        // Given
        server.enqueue(HttpJson.json(200, issue("MIG-1", "Resumen", "Story", "Hola", "backend")));

        // When
        List<StandardItem> obtenida = session().extractAll(List.of("MIG"));
        RecordedRequest request = server.takeRequest();

        // Then
        assertEquals(1, obtenida.size());
        assertEquals("MIG-1", obtenida.get(0).externalId());
        String esperada = "Basic " + Base64.getEncoder().encodeToString(
                "qa@example.com:token-jira-test".getBytes(StandardCharsets.UTF_8));
        assertEquals(esperada, request.getHeader("Authorization"));
    }

    /*
     * CP-U07-02 - Solicitud de creación válida con respuesta HTTP 201
     * Esperada: se obtiene el identificador del elemento creado.
     * Obtenida: se obtiene el identificador del elemento creado.
     */
    @Test
    void http201DevuelveLaClave() {
        // Given
        server.enqueue(HttpJson.json(201, "{\"id\":\"10001\",\"key\":\"MIG-8\"}"));
        StandardItem item = new StandardItem("cu-1", "Fallo", "detalle", List.of(), ItemKind.BUG);

        // When
        String obtenida = session().create(item, "MIG");

        // Then
        assertEquals("MIG-8", obtenida);
    }

    /*
     * CP-U07-03 - Solicitud de actualización válida con respuesta exitosa
     * Esperada: se confirma la actualización sin intentar crear otro elemento.
     * Obtenida: se confirma la actualización sin intentar crear otro elemento.
     */
    @Test
    void actualizacionHacePutYNoCreaOtro() throws Exception {
        // Given
        server.enqueue(HttpJson.json(204, ""));
        StandardItem item = new StandardItem("cu-1", "Nuevo", "nueva", List.of("backend"), ItemKind.STORY);

        // When
        session().update("MIG-8", item, "MIG");
        RecordedRequest request = server.takeRequest();

        // Then
        assertEquals("PUT", request.getMethod());
        assertTrue(request.getPath().contains("/rest/api/3/issue/MIG-8"));
        assertEquals(1, server.getRequestCount());
    }

    /*
     * CP-U07-04 - Respuesta HTTP 401
     * Esperada: se identifica el error de autenticación y la operación no es exitosa.
     * Obtenida: se identifica el error de autenticación y la operación no es exitosa.
     */
    @Test
    void http401NoEsExito() {
        // Given
        server.enqueue(HttpJson.json(401, "{\"error\":\"no\"}"));
        StandardItem item = new StandardItem("src", "T", "d", List.of(), ItemKind.STORY);

        // When
        RestClientResponseException obtenida = assertThrows(RestClientResponseException.class,
                () -> session().create(item, "MIG"));

        // Then
        assertEquals(401, obtenida.getStatusCode().value());
    }

    /*
     * CP-U07-05 - Respuesta HTTP 403
     * Esperada: se identifica el rechazo por permisos insuficientes.
     * Obtenida: se identifica el rechazo por permisos insuficientes.
     */
    @Test
    void http403NoEsExito() {
        // Given
        server.enqueue(HttpJson.json(403, "{\"error\":\"no\"}"));
        StandardItem item = new StandardItem("src", "T", "d", List.of(), ItemKind.STORY);

        // When
        RestClientResponseException obtenida = assertThrows(RestClientResponseException.class,
                () -> session().create(item, "MIG"));

        // Then
        assertEquals(403, obtenida.getStatusCode().value());
    }

    /*
     * CP-U07-06 - Respuesta HTTP 429
     * Esperada: se identifica el límite de solicitudes y no se confirma éxito.
     * Obtenida: se identifica el límite de solicitudes y no se confirma éxito.
     */
    @Test
    void http429NoEsExito() {
        // Given
        server.enqueue(HttpJson.json(429, "{\"error\":\"limite\"}"));
        StandardItem item = new StandardItem("src", "T", "d", List.of(), ItemKind.STORY);

        // When
        RestClientResponseException obtenida = assertThrows(RestClientResponseException.class,
                () -> session().create(item, "MIG"));

        // Then
        assertEquals(429, obtenida.getStatusCode().value());
    }

    /*
     * CP-U07-07 - Respuesta HTTP 500
     * Esperada: el fallo se controla y no se confirma éxito.
     * Obtenida: el fallo se controla y no se confirma éxito.
     */
    @Test
    void http500NoEsExito() {
        // Given
        server.enqueue(HttpJson.json(500, "{\"error\":\"caido\"}"));
        StandardItem item = new StandardItem("src", "T", "d", List.of(), ItemKind.STORY);

        // When
        RestClientResponseException obtenida = assertThrows(RestClientResponseException.class,
                () -> session().create(item, "MIG"));

        // Then
        assertEquals(500, obtenida.getStatusCode().value());
    }

    /*
     * CP-U06-08 - Etiqueta con espacios al escribir en Jira
     * Esperada: el valor se conserva sin modificaciones.
     * Obtenida: los espacios se reemplazan por _.
     */
    @Test
    void etiquetaConEspaciosSeConserva() throws Exception {
        // Given
        server.enqueue(HttpJson.json(204, ""));
        StandardItem item = new StandardItem("cu-1", "T", "d", List.of("back end"), ItemKind.STORY);

        // When
        session().update("MIG-1", item, "MIG");

        // Then
        JsonNode body = json.readTree(server.takeRequest().getBody().readUtf8());
        assertEquals("back end", body.at("/fields/labels/0").asText());
    }

    /*
     * CP-U07-08 - Tiempo de espera agotado
     * Esperada: se registra el fallo y se aplica el manejo de timeout.
     * Obtenida: el conector no configura un timeout de lectura.
     */
    @Test
    void tiempoDeEsperaAgotadoSeControla() {
        // Given
        server.enqueue(HttpJson.json(200, "{\"issues\":[]}")
                .setHeadersDelay(2, TimeUnit.SECONDS));

        // When
        ResourceAccessException obtenida = assertThrows(ResourceAccessException.class,
                () -> session().extractAll(List.of("MIG")));

        // Then
        assertNotNull(obtenida);
    }

    /*
     * CP-U07-09 - La respuesta trae nextPageToken
     * Esperada: se leen los issues de la página siguiente.
     * Obtenida: se leen los issues de la página siguiente.
     */
    @Test
    void extractAllHayOtraPaginaLeeElSiguienteToken() {
        // Given
        server.enqueue(HttpJson.json(200, """
                {"issues":[{"key":"MIG-1","fields":{"summary":"A","issuetype":{"name":"Story"},"labels":[]}}],
                 "nextPageToken":"pagina-2"}
                """));
        server.enqueue(HttpJson.json(200, """
                {"issues":[{"key":"MIG-2","fields":{"summary":"B","issuetype":{"name":"Story"},"labels":[]}}]}
                """));

        // When
        List<String> obtenida = session().extractAll(List.of("MIG")).stream()
                .map(StandardItem::externalId).toList();

        // Then
        assertEquals(List.of("MIG-1", "MIG-2"), obtenida);
    }

    /*
     * CP-U07-10 - El cuerpo de la respuesta es null
     * Esperada: no se inventa una clave.
     * Obtenida: se devuelve null.
     */
    @Test
    void createCuerpoNullDevuelveClaveNull() {
        // Given
        server.enqueue(HttpJson.json(201, "null"));

        // When
        String obtenida = session().create(
                new StandardItem("src", "T", "d", List.of(), ItemKind.STORY), "MIG");

        // Then
        assertNull(obtenida);
    }

    /*
     * CP-U07-11 - El listado de proyectos trae otra página
     * Esperada: se leen los proyectos de todas las páginas.
     * Obtenida: se leen los proyectos de todas las páginas.
     */
    @Test
    void listBoardsRecorreLasPaginasHastaLaUltima() throws Exception {
        // Given
        server.enqueue(HttpJson.json(200, """
                {"values":[{"key":"MIG","name":"Migrador"}],"isLast":false}
                """));
        server.enqueue(HttpJson.json(200, """
                {"values":[{"key":"QA","name":"Calidad"}],"isLast":true}
                """));

        // When
        List<BoardOption> obtenida = session().listBoards();

        // Then
        assertEquals(List.of(
                new BoardOption("MIG", "Migrador (MIG)"),
                new BoardOption("QA", "Calidad (QA)")), obtenida);
        assertTrue(server.takeRequest().getPath().contains("startAt=0"));
        assertTrue(server.takeRequest().getPath().contains("startAt=100"));
    }

    /*
     * CP-U07-12 - El listado de proyectos responde null
     * Esperada: no se inventan tableros.
     * Obtenida: no se inventan tableros.
     */
    @Test
    void listBoardsConCuerpoNuloNoAgregaTableros() {
        // Given
        server.enqueue(HttpJson.json(200, "null"));

        // When
        List<BoardOption> obtenida = session().listBoards();

        // Then
        assertEquals(List.of(), obtenida);
    }

    /*
     * CP-U07-13 - La lista de tableros de origen es null
     * Esperada: no se consulta la búsqueda.
     * Obtenida: no se consulta la búsqueda.
     */
    @Test
    void extractAllConTablerosNullNoConsulta() {
        // Given
        List<String> tableros = null;

        // When
        List<StandardItem> obtenida = session().extractAll(tableros);

        // Then
        assertEquals(List.of(), obtenida);
        assertEquals(0, server.getRequestCount());
    }

    /*
     * CP-U07-14 - No se selecciona ningún tablero de origen
     * Esperada: no se consulta la búsqueda.
     * Obtenida: no se consulta la búsqueda.
     */
    @Test
    void extractAllSinTablerosNoConsulta() {
        // Given
        List<String> tableros = List.of();

        // When
        List<StandardItem> obtenida = session().extractAll(tableros);

        // Then
        assertEquals(List.of(), obtenida);
        assertEquals(0, server.getRequestCount());
    }

    /*
     * CP-U07-15 - La búsqueda responde null
     * Esperada: no se extraen issues.
     * Obtenida: no se extraen issues.
     */
    @Test
    void extractAllConCuerpoNuloNoExtraeIssues() {
        // Given
        server.enqueue(HttpJson.json(200, "null"));

        // When
        List<StandardItem> obtenida = session().extractAll(List.of("MIG"));

        // Then
        assertEquals(List.of(), obtenida);
    }

    /*
     * CP-U07-16 - nextPageToken viene en blanco
     * Esperada: no se pide otra página.
     * Obtenida: no se pide otra página.
     */
    @Test
    void extractAllConTokenEnBlancoNoPideOtraPagina() {
        // Given
        server.enqueue(HttpJson.json(200, """
                {"issues":[{"key":"MIG-1","fields":{"summary":"A","issuetype":{"name":"Story"},"labels":[]}}],
                 "nextPageToken":"   "}
                """));

        // When
        List<String> obtenida = session().extractAll(List.of("MIG")).stream()
                .map(StandardItem::externalId).toList();

        // Then
        assertEquals(List.of("MIG-1"), obtenida);
        assertEquals(1, server.getRequestCount());
    }

    /*
     * CP-U05-07 - El issue es de tipo Bug
     * Esperada: el tipo intermedio es BUG.
     * Obtenida: el tipo intermedio es BUG.
     */
    @Test
    void issueBugSeLeeComoBug() {
        // Given
        server.enqueue(HttpJson.json(200, issue("MIG-9", "Fallo", "Bug", "detalle", "backend")));

        // When
        StandardItem obtenida = session().extractAll(List.of("MIG")).get(0);

        // Then
        assertEquals(ItemKind.BUG, obtenida.kind());
    }

    /*
     * CP-U06-07 - El item no trae etiquetas
     * Esperada: Jira recibe una lista vacía.
     * Obtenida: Jira recibe una lista vacía.
     */
    @Test
    void createSinEtiquetasEnviaListaVacia() throws Exception {
        // Given
        server.enqueue(HttpJson.json(201, "{\"key\":\"MIG-1\"}"));
        StandardItem item = new StandardItem("src", "T", "d", null, ItemKind.STORY);

        // When
        session().create(item, "MIG");

        // Then
        JsonNode body = json.readTree(server.takeRequest().getBody().readUtf8());
        assertEquals(0, body.at("/fields/labels").size());
    }

    private JiraSession session() {
        ServerConfig config = new ServerConfig();
        config.setName("Jira");
        config.setType(JiraConnector.TYPE);
        config.setUrl(server.url("/").toString());
        config.setUsername("qa@example.com");
        config.setApiToken("token-jira-test");
        return new JiraSession(config);
    }

    private static String issue(String key, String summary, String type, String text, String label) {
        return """
                {"issues":[{"key":"%s","fields":{
                  "summary":"%s","issuetype":{"name":"%s"},"labels":["%s"],
                  "description":{"type":"doc","version":1,"content":[
                    {"type":"paragraph","content":[{"type":"text","text":"%s"}]}]}}}]}
                """.formatted(key, summary, type, label, text);
    }
}
