package com.example.migrador.exploratory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.migrador.domain.ServerConfig;
import com.example.migrador.repository.ItemMappingRepository;
import com.example.migrador.repository.ServerConfigRepository;
import com.example.migrador.service.MigrationResult;
import com.example.migrador.service.MigrationService;
import com.example.migrador.support.HttpJson;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ExploratoryApiResilienceTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired
    MigrationService migrationService;
    @Autowired
    ServerConfigRepository configs;
    @Autowired
    ItemMappingRepository mappings;

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

    @Test
    void migrateFalloHttpDuranteExtraccionAbortaAntesDeContactarDestino() {
        ServerConfig origen = guardar("ClickUp", "CLICKUP", clickUp.url("/").toString());
        ServerConfig destino = guardar("Jira", "JIRA", jira.url("/").toString());
        clickUp.enqueue(HttpJson.json(503, "{\"error\":\"servicio no disponible\"}"));

        MigrationResult resultado = migrationService.migrate(
                origen.getId(), destino.getId(), List.of("LIST1"), "MIG");

        assertTrue(resultado.failed());
        assertEquals(0, resultado.created());
        assertEquals(0, resultado.updated());
        assertEquals(0, resultado.itemErrors());
        assertEquals(0, mappings.count());
        assertEquals(1, clickUp.getRequestCount());
        assertEquals(0, jira.getRequestCount());
    }

    @Test
    void migrateRespuestaJsonInvalidaAbortaSinContactarDestino() {
        ServerConfig origen = guardar("ClickUp", "CLICKUP", clickUp.url("/").toString());
        ServerConfig destino = guardar("Jira", "JIRA", jira.url("/").toString());
        clickUp.enqueue(HttpJson.json(200, "{respuesta incompleta"));

        MigrationResult resultado = migrationService.migrate(
                origen.getId(), destino.getId(), List.of("LIST1"), "MIG");

        assertTrue(resultado.failed());
        assertEquals(0, mappings.count());
        assertEquals(1, clickUp.getRequestCount());
        assertEquals(0, jira.getRequestCount());
    }

    @Test
    void migrateRateLimitDuranteExtraccionAbortaSinCrearEnDestino() {
        ServerConfig origen = guardar("ClickUp", "CLICKUP", clickUp.url("/").toString());
        ServerConfig destino = guardar("Jira", "JIRA", jira.url("/").toString());
        clickUp.enqueue(new MockResponse()
                .setResponseCode(429)
                .addHeader("Retry-After", "0")
                .addHeader("Content-Type", "application/json")
                .setBody("{\"error\":\"rate limited\"}"));

        MigrationResult resultado = migrationService.migrate(
                origen.getId(), destino.getId(), List.of("LIST1"), "MIG");

        assertTrue(resultado.failed());
        assertEquals(1, clickUp.getRequestCount());
        assertEquals(0, jira.getRequestCount());
        assertEquals(0, mappings.count());
    }

    @Test
    void migrateRespuestaDeErrorConHttp200SeReportaComoMigracionVaciaExitosa() {
        ServerConfig origen = guardar("ClickUp", "CLICKUP", clickUp.url("/").toString());
        ServerConfig destino = guardar("Jira", "JIRA", jira.url("/").toString());
        clickUp.enqueue(HttpJson.json(200, "{\"error\":\"upstream temporarily unavailable\"}"));

        MigrationResult resultado = migrationService.migrate(
                origen.getId(), destino.getId(), List.of("LIST1"), "MIG");

        assertFalse(resultado.failed());
        assertEquals(0, resultado.created());
        assertEquals(0, resultado.updated());
        assertEquals(0, resultado.itemErrors());
        assertEquals(0, jira.getRequestCount());
        assertTrue(resultado.message().contains("0 creados"));
    }

    @Test
    void altaRemotaExitosaSinIdentificadorDeRespuestaDejaUnItemHuerfano() {
        ServerConfig origen = guardar("ClickUp", "CLICKUP", clickUp.url("/").toString());
        ServerConfig destino = guardar("Jira", "JIRA", jira.url("/").toString());
        AtomicInteger altasRemotas = new AtomicInteger();
        clickUp.enqueue(tarea());
        clickUp.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));
        jira.setDispatcher(new Dispatcher() {
            @Override
            @NotNull
            public MockResponse dispatch(@NotNull RecordedRequest request) {
                if ("POST".equals(request.getMethod()) && "/rest/api/3/issue".equals(request.getPath())) {
                    altasRemotas.incrementAndGet();
                    return HttpJson.json(201, "{\"id\":\"10001\"}");
                }
                return HttpJson.json(404, "{\"error\":\"not found\"}");
            }
        });

        MigrationResult resultado = migrationService.migrate(
                origen.getId(), destino.getId(), List.of("LIST1"), "MIG");

        assertEquals(1, altasRemotas.get());
        assertEquals(0, resultado.created());
        assertEquals(1, resultado.itemErrors());
        assertEquals(0, mappings.count());
    }

    @Test
    void migrateFalloEnPaginaPosteriorDescartaExtraccionParcial() {
        ServerConfig origen = guardar("ClickUp", "CLICKUP", clickUp.url("/").toString());
        ServerConfig destino = guardar("Jira", "JIRA", jira.url("/").toString());
        clickUp.enqueue(HttpJson.json(200, """
                {"tasks":[{"id":"cu-parcial","name":"No debe migrarse",
                  "text_content":"extraído antes del fallo","tags":[]}]}
                """));
        clickUp.enqueue(HttpJson.json(503, "{\"error\":\"servicio no disponible\"}"));

        MigrationResult resultado = migrationService.migrate(
                origen.getId(), destino.getId(), List.of("LIST1"), "MIG");

        assertTrue(resultado.failed());
        assertEquals(0, mappings.count());
        assertEquals(2, clickUp.getRequestCount());
        assertEquals(0, jira.getRequestCount());
    }

    @Test
    void migrateDesconexionAlCrearCuentaErrorDeItemSinGuardarEquivalencia() {
        ServerConfig origen = guardar("ClickUp", "CLICKUP", clickUp.url("/").toString());
        ServerConfig destino = guardar("Jira", "JIRA", jira.url("/").toString());
        clickUp.enqueue(HttpJson.json(200, """
                {"tasks":[{"id":"cu-1","name":"Tarea","text_content":"detalle","tags":[]}]}
                """));
        clickUp.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));
        jira.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        MigrationResult resultado = migrationService.migrate(
                origen.getId(), destino.getId(), List.of("LIST1"), "MIG");

        assertFalse(resultado.failed());
        assertEquals(0, resultado.created());
        assertEquals(0, resultado.updated());
        assertEquals(1, resultado.itemErrors());
        assertEquals(0, mappings.count());
        assertEquals(1, jira.getRequestCount());
    }

    @Test
    void reintentoTrasAltaAplicadaPeroRespuestaPerdidaPuedeDuplicarElItem() {
        ServerConfig origen = guardar("ClickUp", "CLICKUP", clickUp.url("/").toString());
        ServerConfig destino = guardar("Jira", "JIRA", jira.url("/").toString());
        AtomicInteger altasRemotasSimuladas = new AtomicInteger();
        jira.setDispatcher(new Dispatcher() {
            @Override
            @NotNull
            public MockResponse dispatch(@NotNull RecordedRequest request) {
                if ("POST".equals(request.getMethod()) && "/rest/api/3/issue".equals(request.getPath())) {
                    if (altasRemotasSimuladas.incrementAndGet() == 1) {
                        return new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST);
                    }
                    return HttpJson.json(201, "{\"key\":\"MIG-2\"}");
                }
                return HttpJson.json(404, "{\"error\":\"not found\"}");
            }
        });

        clickUp.enqueue(tarea());
        clickUp.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));
        MigrationResult primerIntento = migrationService.migrate(
                origen.getId(), destino.getId(), List.of("LIST1"), "MIG");
        long mappingsTrasPrimerIntento = mappings.count();

        clickUp.enqueue(tarea());
        clickUp.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));
        MigrationResult reintento = migrationService.migrate(
                origen.getId(), destino.getId(), List.of("LIST1"), "MIG");

        assertEquals(1, primerIntento.itemErrors());
        assertEquals(0, mappingsTrasPrimerIntento);
        assertEquals(1, reintento.created());
        assertEquals(1, mappings.count());
        assertEquals(2, altasRemotasSimuladas.get());
    }

    @Test
    void migrateTextoLargoUnicodeYCaracteresDeEscapeSinTruncar() throws Exception {
        ServerConfig origen = guardar("ClickUp", "CLICKUP", clickUp.url("/").toString());
        ServerConfig destino = guardar("Jira", "JIRA", jira.url("/").toString());
        String title = "Título-ñ-🙂".repeat(250);
        String description = "línea \"citada\" \\ símbolos 🙂 ".repeat(400);
        clickUp.enqueue(HttpJson.json(200, json.writeValueAsString(Map.of(
                "tasks", List.of(Map.of(
                        "id", "cu-boundary",
                        "name", title,
                        "text_content", description,
                        "tags", List.of()))))));
        clickUp.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));
        jira.enqueue(HttpJson.json(201, "{\"key\":\"MIG-BOUNDARY\"}"));

        MigrationResult resultado = migrationService.migrate(
                origen.getId(), destino.getId(), List.of("LIST1"), "MIG");

        assertEquals(1, resultado.created());
        RecordedRequest create = jira.takeRequest();
        JsonNode body = json.readTree(create.getBody().readUtf8());
        assertEquals(title, body.at("/fields/summary").asText());
        assertEquals(description, body.at("/fields/description/content/0/content/0/text").asText());
    }

    @Test
    void migrateIdentificadorDuplicadoEnOrigenReutilizaLaEquivalencia() throws Exception {
        ServerConfig origen = guardar("ClickUp", "CLICKUP", clickUp.url("/").toString());
        ServerConfig destino = guardar("Jira", "JIRA", jira.url("/").toString());
        clickUp.enqueue(HttpJson.json(200, """
                {"tasks":[
                  {"id":"cu-duplicate","name":"Primera","text_content":"uno","tags":[]},
                  {"id":"cu-duplicate","name":"Segunda","text_content":"dos","tags":[]}
                ]}
                """));
        clickUp.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));
        jira.enqueue(HttpJson.json(201, "{\"key\":\"MIG-DUP\"}"));
        jira.enqueue(HttpJson.json(204, ""));

        MigrationResult resultado = migrationService.migrate(
                origen.getId(), destino.getId(), List.of("LIST1"), "MIG");

        assertEquals(1, resultado.created());
        assertEquals(1, resultado.updated());
        assertEquals(1, mappings.count());
        assertEquals("POST", jira.takeRequest().getMethod());
        assertEquals("PUT", jira.takeRequest().getMethod());
    }

    @Test
    void migrarAlMismoServidorCreaUnaCopiaEnElOrigen() {
        List<String> copiasCreadas = new java.util.ArrayList<>();
        AtomicInteger siguienteId = new AtomicInteger(1);
        clickUp.setDispatcher(new Dispatcher() {
            @Override
            @NotNull
            public MockResponse dispatch(@NotNull RecordedRequest request) {
                String path = request.getPath();
                if ("GET".equals(request.getMethod()) && "/list/LIST1/task?page=0&include_closed=true".equals(path)) {
                    StringBuilder tasks = new StringBuilder(
                            "{\"tasks\":[{\"id\":\"cu-original\",\"name\":\"Original\","
                                    + "\"text_content\":\"detalle\",\"tags\":[]}");
                    for (String id : copiasCreadas) {
                        tasks.append(",{\"id\":\"").append(id).append("\",\"name\":\"Copia\",")
                                .append("\"text_content\":\"detalle\",\"tags\":[]}");
                    }
                    return HttpJson.json(200, tasks.append("]}").toString());
                }
                if ("GET".equals(request.getMethod())
                        && "/list/LIST1/task?page=1&include_closed=true".equals(path)) {
                    return HttpJson.json(200, "{\"tasks\":[]}");
                }
                if ("POST".equals(request.getMethod()) && "/list/LIST1/task".equals(path)) {
                    String id = "cu-copy-" + siguienteId.getAndIncrement();
                    copiasCreadas.add(id);
                    return HttpJson.json(200, "{\"id\":\"" + id + "\"}");
                }
                if ("PUT".equals(request.getMethod()) && path != null && path.startsWith("/task/")) {
                    return HttpJson.json(200, "{}");
                }
                return HttpJson.json(404, "{\"error\":\"not found\"}");
            }
        });
        ServerConfig mismoServidor = guardar("ClickUp", "CLICKUP", clickUp.url("/").toString());

        MigrationResult primera = migrationService.migrate(
                mismoServidor.getId(), mismoServidor.getId(), List.of("LIST1"), "LIST1");
        MigrationResult segunda = migrationService.migrate(
                mismoServidor.getId(), mismoServidor.getId(), List.of("LIST1"), "LIST1");

        assertEquals(1, primera.created());
        assertEquals(0, segunda.created());
        assertEquals(2, segunda.updated());
        assertEquals(1, copiasCreadas.size());
    }

    @Test
    void configuracionesDuplicadasDelMismoServidorPuedenDuplicarItemsEnElMismoTablero() throws Exception {
        String sameUrl = clickUp.url("/").toString();
        ServerConfig origen = guardar("ClickUp origen", "CLICKUP", sameUrl);
        ServerConfig destino = guardar("ClickUp destino", "CLICKUP", sameUrl);
        clickUp.enqueue(tarea());
        clickUp.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));
        clickUp.enqueue(HttpJson.json(200, "{\"id\":\"cu-copy\"}"));

        MigrationResult resultado = migrationService.migrate(
                origen.getId(), destino.getId(), List.of("LIST1"), "LIST1");

        assertNotEquals(origen.getId(), destino.getId());
        assertEquals(1, resultado.created());
        assertEquals(1, mappings.count());
        assertEquals("cu-retry", mappings.findAll().getFirst().getSourceItemId());
        assertEquals("cu-copy", mappings.findAll().getFirst().getTargetItemId());
        assertEquals("GET", clickUp.takeRequest().getMethod());
        assertEquals("GET", clickUp.takeRequest().getMethod());
        assertEquals("POST", clickUp.takeRequest().getMethod());
    }

    @Test
    void migracionesConcurrentesDelMismoLoteCreanDuplicadoEnDestino() throws Exception {
        ServerConfig origen = guardar("ClickUp", "CLICKUP", clickUp.url("/").toString());
        ServerConfig destino = guardar("Jira", "JIRA", jira.url("/").toString());
        clickUp.setDispatcher(new Dispatcher() {
            @Override
            @NotNull
            public MockResponse dispatch(@NotNull RecordedRequest request) {
                if ("/list/LIST1/task?page=0&include_closed=true".equals(request.getPath())) {
                    return tarea();
                }
                if ("/list/LIST1/task?page=1&include_closed=true".equals(request.getPath())) {
                    return HttpJson.json(200, "{\"tasks\":[]}");
                }
                return HttpJson.json(404, "{\"error\":\"not found\"}");
            }
        });

        CountDownLatch createRequests = new CountDownLatch(2);
        CountDownLatch allowCreateResponses = new CountDownLatch(1);
        AtomicInteger createdRemotely = new AtomicInteger();
        jira.setDispatcher(new Dispatcher() {
            @Override
            @NotNull
            public MockResponse dispatch(@NotNull RecordedRequest request) {
                if ("POST".equals(request.getMethod()) && "/rest/api/3/issue".equals(request.getPath())) {
                    int number = createdRemotely.incrementAndGet();
                    createRequests.countDown();
                    try {
                        if (!allowCreateResponses.await(10, TimeUnit.SECONDS)) {
                            return HttpJson.json(503, "{\"error\":\"test synchronization timed out\"}");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        return HttpJson.json(503, "{\"error\":\"test interrupted\"}");
                    }
                    return HttpJson.json(201, "{\"key\":\"MIG-" + number + "\"}");
                }
                return HttpJson.json(404, "{\"error\":\"not found\"}");
            }
        });

        ExecutorService workers = Executors.newFixedThreadPool(2);
        Future<MigrationResult> first = workers.submit(
                () -> migrationService.migrate(origen.getId(), destino.getId(), List.of("LIST1"), "MIG"));
        Future<MigrationResult> second = workers.submit(
                () -> migrationService.migrate(origen.getId(), destino.getId(), List.of("LIST1"), "MIG"));
        try {
            assertTrue(createRequests.await(10, TimeUnit.SECONDS), "ambas corridas deben llegar al POST de Jira");
            allowCreateResponses.countDown();
            MigrationResult firstResult = first.get(10, TimeUnit.SECONDS);
            MigrationResult secondResult = second.get(10, TimeUnit.SECONDS);

            assertEquals(2, createdRemotely.get());
            assertEquals(1, firstResult.created() + secondResult.created());
            assertEquals(1, firstResult.itemErrors() + secondResult.itemErrors());
            assertEquals(1, mappings.count());
        } finally {
            allowCreateResponses.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void cursorJiraRepetidoSeConsultaVeinteVecesSinDetectarQueNoAvanza() throws Exception {
        ServerConfig origen = guardar("Jira", "JIRA", jira.url("/").toString());
        ServerConfig destino = guardar("ClickUp", "CLICKUP", clickUp.url("/").toString());
        AtomicInteger pages = new AtomicInteger();
        AtomicInteger creates = new AtomicInteger();
        AtomicInteger updates = new AtomicInteger();
        jira.setDispatcher(new Dispatcher() {
            @Override
            @NotNull
            public MockResponse dispatch(@NotNull RecordedRequest request) {
                if ("POST".equals(request.getMethod())
                        && "/rest/api/3/search/jql".equals(request.getPath())) {
                    if (pages.incrementAndGet() <= 20) {
                        return HttpJson.json(200, """
                                {"issues":[{"key":"MIG-LOOP","fields":{
                                  "summary":"Repetido","issuetype":{"name":"Story"},"labels":[]}}],"nextPageToken":"same-cursor"}
                                """);
                    }
                    return HttpJson.json(200, "{\"issues\":[]}");
                }
                return HttpJson.json(404, "{\"error\":\"not found\"}");
            }
        });
        clickUp.setDispatcher(new Dispatcher() {
            @Override
            @NotNull
            public MockResponse dispatch(@NotNull RecordedRequest request) {
                if ("POST".equals(request.getMethod()) && "/list/LIST2/task".equals(request.getPath())) {
                    creates.incrementAndGet();
                    return HttpJson.json(200, "{\"id\":\"cu-created\"}");
                }
                if ("PUT".equals(request.getMethod()) && "/task/cu-created".equals(request.getPath())) {
                    updates.incrementAndGet();
                    return HttpJson.json(200, "{}");
                }
                return HttpJson.json(404, "{\"error\":\"not found\"}");
            }
        });

        MigrationResult resultado = migrationService.migrate(
                origen.getId(), destino.getId(), List.of("MIG"), "LIST2");

        assertEquals(21, pages.get());
        assertEquals(1, creates.get());
        assertEquals(19, updates.get());
        assertEquals(1, resultado.created());
        assertEquals(19, resultado.updated());
        int repeatedCursorRequests = 0;
        for (int requestNumber = 0; requestNumber < 21; requestNumber++) {
            RecordedRequest request = jira.takeRequest();
            JsonNode requestBody = json.readTree(request.getBody().readUtf8());
            if (requestBody.has("nextPageToken")) {
                assertEquals("\"same-cursor\"", requestBody.get("nextPageToken").toString());
                repeatedCursorRequests++;
            }
        }
        assertEquals(20, repeatedCursorRequests);
    }

    private static MockResponse tarea() {
        return HttpJson.json(200, """
                {"tasks":[{"id":"cu-retry","name":"Tarea","text_content":"detalle","tags":[]}]}
                """);
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
