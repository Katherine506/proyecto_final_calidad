package com.example.migrador.connector.clickup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
 * Casos preliminares del conector ClickUp (U01, U02, U03, U05, U06).
 * Un método = un caso. La API se sustituye con MockWebServer.
 */
class ClickUpSessionTest {

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
     * CP-U01-01 - Respuesta JSON válida con nombre, descripción y etiquetas
     * Esperada: los elementos se extraen con los valores correspondientes.
     * Obtenida: los elementos se extraen con los valores correspondientes.
     */
    @Test
    void jsonValidoExtraeNombreDescripcionYEtiquetas() {
        // Given
        server.enqueue(HttpJson.json(200, """
                {"tasks":[{"id":"cu-1","name":"Cerrar incidencia","text_content":"Detalle",
                  "tags":[{"name":"backend"},{"name":"urgente"}]}]}
                """));
        server.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));

        // When
        StandardItem obtenida = session().extractAll(List.of("LIST1")).get(0);

        // Then
        assertEquals("cu-1", obtenida.externalId());
        assertEquals("Cerrar incidencia", obtenida.title());
        assertEquals("Detalle", obtenida.description());
        assertEquals(List.of("backend", "urgente"), obtenida.labels());
    }

    /*
     * CP-U01-02 - Respuesta JSON con descripción vacía
     * Esperada: el elemento se procesa sin excepción.
     * Obtenida: el elemento se procesa sin excepción y la descripción queda vacía.
     */
    @Test
    void descripcionVaciaNoLanzaExcepcion() {
        // Given
        server.enqueue(HttpJson.json(200, """
                {"tasks":[{"id":"cu-2","name":"Sin cuerpo","text_content":"","description":"","tags":[]}]}
                """));
        server.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));

        // When
        List<StandardItem> obtenida = session().extractAll(List.of("LIST1"));

        // Then
        assertEquals(1, obtenida.size());
        assertEquals("", obtenida.get(0).description());
    }

    /*
     * CP-U01-03 - Respuesta sin un identificador obligatorio
     * Esperada: el elemento se rechaza.
     * Obtenida: se extrae con externalId null.
     */
    @Test
    void sinIdRechazaElElemento() {
        // Given
        server.enqueue(HttpJson.json(200, """
                {"tasks":[{"name":"Huérfano","text_content":"x","tags":[]}]}
                """));
        server.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));

        // When
        List<StandardItem> obtenida = session().extractAll(List.of("LIST1"));

        // Then
        assertEquals(List.of(), obtenida);
    }

    /*
     * CP-U01-04 - Respuesta con una lista de elementos vacía
     * Esperada: se obtiene una colección vacía.
     * Obtenida: se obtiene una colección vacía.
     */
    @Test
    void listaVaciaNoGeneraItems() {
        // Given
        server.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));

        // When
        List<StandardItem> obtenida = session().extractAll(List.of("LIST1"));

        // Then
        assertEquals(List.of(), obtenida);
    }

    /*
     * CP-U01-05 - Respuesta con propiedades adicionales no utilizadas
     * Esperada: se extraen los campos definidos sin alterar sus valores.
     * Obtenida: se extraen los campos definidos sin alterar sus valores.
     */
    @Test
    void propiedadesExtraNoAlteranLosCampos() {
        // Given
        server.enqueue(HttpJson.json(200, """
                {"tasks":[{"id":"cu-5","name":"Con extras","text_content":"cuerpo",
                  "priority":{"priority":"high"},"assignees":[{"id":9}],"tags":[{"name":"backend"}]}]}
                """));
        server.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));

        // When
        StandardItem obtenida = session().extractAll(List.of("LIST1")).get(0);

        // Then
        assertEquals("Con extras", obtenida.title());
        assertEquals("cuerpo", obtenida.description());
        assertEquals(List.of("backend"), obtenida.labels());
    }

    /*
     * CP-U01-06 - Elemento con tildes, símbolos y caracteres Unicode
     * Esperada: los caracteres se conservan.
     * Obtenida: los caracteres se conservan.
     */
    @Test
    void unicodeConservaLosCaracteres() {
        // Given
        server.enqueue(HttpJson.json(200, """
                {"tasks":[{"id":"cu-6","name":"Pingüino — €","text_content":"año","tags":[{"name":"diseño"}]}]}
                """));
        server.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));

        // When
        StandardItem obtenida = session().extractAll(List.of("LIST1")).get(0);

        // Then
        assertEquals("Pingüino — €", obtenida.title());
        assertEquals("año", obtenida.description());
        assertEquals(List.of("diseño"), obtenida.labels());
    }

    /*
     * CP-U01-07 - Respuesta con varias páginas de resultados
     * Esperada: se recuperan los elementos de todas las páginas, sin omisiones.
     * Obtenida: se recuperan los elementos de todas las páginas, sin omisiones.
     */
    @Test
    void variasPaginasRecuperaTodas() throws Exception {
        // Given
        server.enqueue(HttpJson.json(200, """
                {"tasks":[{"id":"p0","name":"Primera","text_content":"a","tags":[]}]}
                """));
        server.enqueue(HttpJson.json(200, """
                {"tasks":[{"id":"p1","name":"Segunda","text_content":"b","tags":[]}]}
                """));
        server.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));

        // When
        List<StandardItem> obtenida = session().extractAll(List.of("LIST1"));

        // Then
        assertEquals(List.of("p0", "p1"), obtenida.stream().map(StandardItem::externalId).toList());
        assertTrue(server.takeRequest().getPath().contains("page=0"));
        assertTrue(server.takeRequest().getPath().contains("page=1"));
        assertTrue(server.takeRequest().getPath().contains("page=2"));
    }

    /*
     * CP-U02-01 - Elemento con identificador, nombre y datos válidos
     * Esperada: el elemento es aceptado para continuar el procesamiento.
     * Obtenida: el elemento es aceptado para continuar el procesamiento.
     */
    @Test
    void itemValidoSeAcepta() {
        // Given
        server.enqueue(HttpJson.json(200, """
                {"tasks":[{"id":"cu-ok","name":"Valido","text_content":"cuerpo","tags":[{"name":"backend"}]}]}
                """));
        server.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));

        // When
        StandardItem obtenida = session().extractAll(List.of("LIST1")).get(0);

        // Then
        assertEquals("cu-ok", obtenida.externalId());
        assertEquals("Valido", obtenida.title());
    }

    /*
     * CP-U02-02 - Elemento sin identificador
     * Esperada: el elemento es rechazado como inválido.
     * Obtenida: el elemento sigue en la lista, con id null.
     */
    @Test
    void sinIdSeRechaza() {
        // Given
        server.enqueue(HttpJson.json(200, """
                {"tasks":[{"name":"Sin id","text_content":"x","tags":[]}]}
                """));
        server.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));

        // When
        List<StandardItem> obtenida = session().extractAll(List.of("LIST1"));

        // Then
        assertEquals(List.of(), obtenida);
    }

    /*
     * CP-U02-03 - Elemento con nombre vacío
     * Esperada: se impide su creación.
     * Obtenida: el elemento se extrae con nombre vacío.
     */
    @Test
    void nombreVacioSeRechaza() {
        // Given
        server.enqueue(HttpJson.json(200, """
                {"tasks":[{"id":"cu-n","name":"","text_content":"x","tags":[]}]}
                """));
        server.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));

        // When
        List<StandardItem> obtenida = session().extractAll(List.of("LIST1"));

        // Then
        assertEquals(List.of(), obtenida);
    }

    /*
     * CP-U02-04 - Elemento con descripción vacía permitida
     * Esperada: el elemento continúa el procesamiento.
     * Obtenida: el elemento continúa el procesamiento.
     */
    @Test
    void descripcionVaciaPermitidaContinua() {
        // Given
        server.enqueue(HttpJson.json(200, """
                {"tasks":[{"id":"cu-d","name":"Con nombre","text_content":"","description":"","tags":[{"name":"backend"}]}]}
                """));
        server.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));

        // When
        StandardItem obtenida = session().extractAll(List.of("LIST1")).get(0);

        // Then
        assertEquals("", obtenida.description());
        assertEquals("Con nombre", obtenida.title());
        assertEquals(List.of("backend"), obtenida.labels());
    }

    /*
     * CP-U02-05 - Elemento con etiquetas vacías
     * Esperada: se permite el procesamiento con una colección vacía de etiquetas.
     * Obtenida: se permite el procesamiento con una colección vacía de etiquetas.
     */
    @Test
    void etiquetasVaciasUsaColeccionVacia() {
        // Given
        server.enqueue(HttpJson.json(200, """
                {"tasks":[{"id":"cu-e","name":"Sin tags","text_content":"x","tags":[]}]}
                """));
        server.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));

        // When
        StandardItem obtenida = session().extractAll(List.of("LIST1")).get(0);

        // Then
        assertEquals(List.of(), obtenida.labels());
    }

    /*
     * CP-U02-06 - Entrada nula
     * Esperada: validación fallida o excepción controlada.
     * Obtenida: lista vacía, sin excepción.
     */
    @Test
    void entradaNulaFallaLaValidacion() {
        // Given
        List<String> boards = null;

        // When
        IllegalArgumentException obtenida = assertThrows(IllegalArgumentException.class,
                () -> session().extractAll(boards));

        // Then
        assertNotNull(obtenida);
    }

    /*
     * CP-U03-01 - Elemento ClickUp con nombre, descripción y etiquetas
     * Esperada: se genera un StandardItem con los valores correspondientes.
     * Obtenida: se genera un StandardItem con los valores correspondientes.
     */
    @Test
    void clickUpGeneraStandardItem() {
        // Given
        server.enqueue(HttpJson.json(200, """
                {"tasks":[{"id":"cu-1","name":"Nombre","text_content":"Desc","tags":[{"name":"backend"}]}]}
                """));
        server.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));

        // When
        StandardItem obtenida = session().extractAll(List.of("LIST1")).get(0);

        // Then
        assertEquals(new StandardItem("cu-1", "Nombre", "Desc", List.of("backend"), ItemKind.STORY), obtenida);
    }

    /*
     * CP-U03-03 - Elemento con descripción vacía
     * Esperada: la ausencia de contenido no modifica el nombre ni las etiquetas.
     * Obtenida: la ausencia de contenido no modifica el nombre ni las etiquetas.
     */
    @Test
    void descripcionVaciaNoTocaNombreNiEtiquetas() {
        // Given
        server.enqueue(HttpJson.json(200, """
                {"tasks":[{"id":"cu-3","name":"Nombre","text_content":"","tags":[{"name":"backend"}]}]}
                """));
        server.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));

        // When
        StandardItem obtenida = session().extractAll(List.of("LIST1")).get(0);

        // Then
        assertEquals("", obtenida.description());
        assertEquals("Nombre", obtenida.title());
        assertEquals(List.of("backend"), obtenida.labels());
    }

    /*
     * CP-U03-04 - Elemento con múltiples etiquetas
     * Esperada: todas las etiquetas admitidas se conservan.
     * Obtenida: todas las etiquetas admitidas se conservan.
     */
    @Test
    void variasEtiquetasSeConservan() {
        // Given
        server.enqueue(HttpJson.json(200, """
                {"tasks":[{"id":"cu-4","name":"N","text_content":"d",
                  "tags":[{"name":"backend"},{"name":"urgente"},{"name":"diseño"}]}]}
                """));
        server.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));

        // When
        StandardItem obtenida = session().extractAll(List.of("LIST1")).get(0);

        // Then
        assertEquals(List.of("backend", "urgente", "diseño"), obtenida.labels());
    }

    /*
     * CP-U03-05 - Elemento con caracteres especiales en el nombre
     * Esperada: el nombre se conserva sin alteraciones.
     * Obtenida: el nombre se conserva sin alteraciones.
     */
    @Test
    void nombreConSimbolosSeConserva() {
        // Given
        server.enqueue(HttpJson.json(200, """
                {"tasks":[{"id":"cu-5","name":"Pingüino — €","text_content":"d","tags":[]}]}
                """));
        server.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));

        // When
        StandardItem obtenida = session().extractAll(List.of("LIST1")).get(0);

        // Then
        assertEquals("Pingüino — €", obtenida.title());
    }

    /*
     * CP-U05-02 - Elemento identificado como story
     * Esperada: se asigna el tipo Story y la etiqueta story no se copia.
     * Obtenida: se asigna el tipo Story y la etiqueta story no se copia.
     */
    @Test
    void etiquetaStoryQuedaStoryYNoSeCopia() {
        // Given
        server.enqueue(HttpJson.json(200, """
                {"tasks":[{"id":"cu-s","name":"Historia","text_content":"d","tags":[{"name":"story"}]}]}
                """));
        server.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));

        // When
        StandardItem obtenida = session().extractAll(List.of("LIST1")).get(0);

        // Then
        assertEquals(ItemKind.STORY, obtenida.kind());
        assertEquals(List.of(), obtenida.labels());
    }

    /*
     * CP-U05-03 - Elemento identificado como task
     * Esperada: se asigna el tipo Task.
     * Obtenida: queda STORY y la etiqueta task se conserva.
     */
    @Test
    void etiquetaTaskQuedaComoTask() {
        // Given
        server.enqueue(HttpJson.json(200, """
                {"tasks":[{"id":"cu-t","name":"Trabajo","text_content":"d","tags":[{"name":"task"}]}]}
                """));
        server.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));

        // When
        StandardItem obtenida = session().extractAll(List.of("LIST1")).get(0);

        // Then
        assertEquals("TASK", obtenida.kind().name());
    }

    /*
     * CP-U05-04 - Elemento sin etiqueta de tipo reconocida
     * Esperada: se asigna el tipo Story.
     * Obtenida: se asigna el tipo Story.
     */
    @Test
    void sinEtiquetaDeTipoQuedaStory() {
        // Given
        server.enqueue(HttpJson.json(200, """
                {"tasks":[{"id":"cu-s","name":"N","text_content":"d","tags":[{"name":"backend"}]}]}
                """));
        server.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));

        // When
        StandardItem obtenida = session().extractAll(List.of("LIST1")).get(0);

        // Then
        assertEquals(ItemKind.STORY, obtenida.kind());
    }

    /*
     * CP-U05-05 - Etiqueta de tipo en mayúsculas
     * Esperada: se reconoce el tipo sin asignar uno incorrecto.
     * Obtenida: BUG se reconoce como bug y no se copia como etiqueta.
     */
    @Test
    void bugEnMayusculasSeReconoce() {
        // Given
        server.enqueue(HttpJson.json(200, """
                {"tasks":[{"id":"cu-b","name":"Fallo","text_content":"d","tags":[{"name":"BUG"}]}]}
                """));
        server.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));

        // When
        StandardItem obtenida = session().extractAll(List.of("LIST1")).get(0);

        // Then
        assertEquals(ItemKind.BUG, obtenida.kind());
        assertEquals(List.of(), obtenida.labels());
    }

    /*
     * CP-U06-03 - Etiqueta de tipo bug y etiqueta personalizada backend
     * Esperada: se asigna el tipo Bug y se conserva la etiqueta personalizada.
     * Obtenida: se asigna el tipo Bug y se conserva la etiqueta personalizada.
     */
    @Test
    void bugYEtiquetaPropiaSeparaTipoYEtiqueta() {
        // Given
        server.enqueue(HttpJson.json(200, """
                {"tasks":[{"id":"cu-b","name":"Fallo","text_content":"d",
                  "tags":[{"name":"bug"},{"name":"backend"}]}]}
                """));
        server.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));

        // When
        StandardItem obtenida = session().extractAll(List.of("LIST1")).get(0);

        // Then
        assertEquals(ItemKind.BUG, obtenida.kind());
        assertEquals(List.of("backend"), obtenida.labels());
    }

    /*
     * CP-U06-01 - Elemento con etiquetas backend y urgente
     * Esperada: ambas etiquetas se mantienen en el destino.
     * Obtenida: ambas etiquetas se mantienen en el destino.
     */
    @Test
    void dosEtiquetasSeMantienenAlCrear() throws Exception {
        // Given
        server.enqueue(HttpJson.json(201, "{\"id\":\"new-1\"}"));
        StandardItem item = new StandardItem("src", "Nombre", "desc", List.of("backend", "urgente"), ItemKind.STORY);

        // When
        session().create(item, "LIST9");

        // Then
        JsonNode body = json.readTree(server.takeRequest().getBody().readUtf8());
        assertEquals(List.of("backend", "urgente"), textos(body.get("tags")));
    }

    /*
     * CP-U06-02 - Elemento sin etiquetas
     * Esperada: se genera una colección vacía sin excepción.
     * Obtenida: se genera una colección vacía sin excepción.
     */
    @Test
    void sinEtiquetasEnviaListaVacia() throws Exception {
        // Given
        server.enqueue(HttpJson.json(201, "{\"id\":\"new-2\"}"));
        StandardItem item = new StandardItem("src", "Nombre", "desc", null, ItemKind.STORY);

        // When
        session().create(item, "LIST9");

        // Then
        JsonNode body = json.readTree(server.takeRequest().getBody().readUtf8());
        assertEquals(0, body.get("tags").size());
    }

    /*
     * CP-U06-04 - Etiqueta con caracteres especiales
     * Esperada: el valor se conserva sin modificaciones.
     * Obtenida: el valor se conserva sin modificaciones.
     */
    @Test
    void etiquetaConEnieSeConservaAlCrear() throws Exception {
        // Given
        server.enqueue(HttpJson.json(201, "{\"id\":\"new-3\"}"));
        StandardItem item = new StandardItem("src", "Nombre", "desc", List.of("año"), ItemKind.STORY);

        // When
        session().create(item, "LIST9");

        // Then
        JsonNode body = json.readTree(server.takeRequest().getBody().readUtf8());
        assertEquals("año", body.get("tags").get(0).asText());
    }

    /*
     * CP-U06-05 - Elemento de tipo Task migrado hacia ClickUp
     * Esperada: se incorpora la etiqueta de tipo task.
     * Obtenida: un Story no agrega etiqueta de tipo.
     */
    @Test
    void taskIncorporaEtiquetaDeTipo() throws Exception {
        // Given
        server.enqueue(HttpJson.json(201, "{\"id\":\"new-4\"}"));
        StandardItem item = new StandardItem("src", "Nombre", "desc", List.of(), ItemKind.STORY);

        // When
        session().create(item, "LIST9");

        // Then
        JsonNode body = json.readTree(server.takeRequest().getBody().readUtf8());
        assertEquals(List.of("task"), textos(body.get("tags")));
    }

    /*
     * CP-U06-06 - Elemento con etiquetas repetidas
     * Esperada: se aplica la política de duplicados sin perder el resto.
     * Obtenida: al crear, las repetidas quedan en una sola.
     */
    @Test
    void etiquetasRepetidasSeDeduplicanAlCrear() throws Exception {
        // Given
        server.enqueue(HttpJson.json(201, "{\"id\":\"new-5\"}"));
        StandardItem item = new StandardItem("src", "Nombre", "desc",
                List.of("backend", "urgente", "backend"), ItemKind.STORY);

        // When
        session().create(item, "LIST9");

        // Then
        JsonNode body = json.readTree(server.takeRequest().getBody().readUtf8());
        assertEquals(List.of("backend", "urgente"), textos(body.get("tags")));
    }

    /*
     * CP-U05-06 - Elemento convertido de Jira a ClickUp
     * Esperada: se reconstruye la etiqueta que representa el tipo.
     * Obtenida: un Story no agrega la etiqueta story.
     */
    @Test
    void storyReconstruyeLaEtiquetaStory() throws Exception {
        // Given
        server.enqueue(HttpJson.json(200, ""));
        server.enqueue(HttpJson.json(200, ""));
        StandardItem story = new StandardItem("src", "Nombre", "desc", List.of(), ItemKind.STORY);

        // When
        session().update("cu-9", story, "LIST9");

        // Then
        RecordedRequest put = server.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(put);
        assertEquals("PUT", put.getMethod());
        RecordedRequest etiqueta = server.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(etiqueta);
        assertTrue(etiqueta.getPath().contains("/tag/story"));
    }

    /*
     * CP-U01-08 - Listado de tableros con equipo, carpeta y lista suelta
     * Esperada: una opción por lista, con el espacio y la carpeta en el nombre.
     * Obtenida: una opción por lista, con el espacio y la carpeta en el nombre.
     */
    @Test
    void listBoardsRecorreEquipoCarpetaYListaSuelta() {
        // Given
        server.enqueue(HttpJson.json(200, "{\"teams\":[{\"id\":\"t1\"}]}"));
        server.enqueue(HttpJson.json(200, "{\"spaces\":[{\"id\":\"s1\",\"name\":\"Espacio\"}]}"));
        server.enqueue(HttpJson.json(200, """
                {"folders":[{"name":"Carpeta","lists":[{"id":"l1","name":"Lista"}]}]}
                """));
        server.enqueue(HttpJson.json(200, "{\"lists\":[{\"id\":\"l2\",\"name\":\"Suelta\"}]}"));

        // When
        List<BoardOption> obtenida = session().listBoards();

        // Then
        assertEquals(List.of(
                new BoardOption("l1", "Espacio / Carpeta / Lista"),
                new BoardOption("l2", "Espacio / Suelta")), obtenida);
    }

    /*
     * CP-U01-09 - El listado de equipos responde null
     * Esperada: no se agregan tableros.
     * Obtenida: no se agregan tableros.
     */
    @Test
    void listBoardsSinEquiposNoAgregaTableros() {
        // Given
        server.enqueue(HttpJson.json(200, "null"));

        // When
        List<BoardOption> obtenida = session().listBoards();

        // Then
        assertEquals(List.of(), obtenida);
    }

    /*
     * CP-U01-10 - El listado de espacios responde null
     * Esperada: no se agregan tableros.
     * Obtenida: no se agregan tableros.
     */
    @Test
    void listBoardsSinEspaciosNoAgregaTableros() {
        // Given
        server.enqueue(HttpJson.json(200, "{\"teams\":[{\"id\":\"t1\"}]}"));
        server.enqueue(HttpJson.json(200, "null"));

        // When
        List<BoardOption> obtenida = session().listBoards();

        // Then
        assertEquals(List.of(), obtenida);
    }

    /*
     * CP-U01-11 - El listado de carpetas responde null
     * Esperada: se conserva la lista suelta del espacio.
     * Obtenida: se conserva la lista suelta del espacio.
     */
    @Test
    void listBoardsSinCarpetasConservaLaListaSuelta() {
        // Given
        server.enqueue(HttpJson.json(200, "{\"teams\":[{\"id\":\"t1\"}]}"));
        server.enqueue(HttpJson.json(200, "{\"spaces\":[{\"id\":\"s1\",\"name\":\"Espacio\"}]}"));
        server.enqueue(HttpJson.json(200, "null"));
        server.enqueue(HttpJson.json(200, "{\"lists\":[{\"id\":\"l2\",\"name\":\"Suelta\"}]}"));

        // When
        List<BoardOption> obtenida = session().listBoards();

        // Then
        assertEquals(List.of(new BoardOption("l2", "Espacio / Suelta")), obtenida);
    }

    /*
     * CP-U01-12 - El listado de listas sueltas responde null
     * Esperada: se conserva la lista que está dentro de la carpeta.
     * Obtenida: se conserva la lista que está dentro de la carpeta.
     */
    @Test
    void listBoardsSinListasSueltasConservaLaDeLaCarpeta() {
        // Given
        server.enqueue(HttpJson.json(200, "{\"teams\":[{\"id\":\"t1\"}]}"));
        server.enqueue(HttpJson.json(200, "{\"spaces\":[{\"id\":\"s1\",\"name\":\"Espacio\"}]}"));
        server.enqueue(HttpJson.json(200, """
                {"folders":[{"name":"Carpeta","lists":[{"id":"l1","name":"Lista"}]}]}
                """));
        server.enqueue(HttpJson.json(200, "null"));

        // When
        List<BoardOption> obtenida = session().listBoards();

        // Then
        assertEquals(List.of(new BoardOption("l1", "Espacio / Carpeta / Lista")), obtenida);
    }

    /*
     * CP-U01-13 - La página de tareas responde null
     * Esperada: no se extraen elementos.
     * Obtenida: no se extraen elementos.
     */
    @Test
    void extractAllConCuerpoNuloNoExtraeItems() {
        // Given
        server.enqueue(HttpJson.json(200, "null"));

        // When
        List<StandardItem> obtenida = session().extractAll(List.of("LIST1"));

        // Then
        assertEquals(List.of(), obtenida);
    }

    /*
     * CP-U03-07 - Etiqueta sin nombre y descripción solo en el campo description
     * Esperada: se ignora la etiqueta vacía y se usa description.
     * Obtenida: se ignora la etiqueta vacía y se usa description.
     */
    @Test
    void etiquetaVaciaSeIgnoraYLaDescripcionSaleDelOtroCampo() {
        // Given
        server.enqueue(HttpJson.json(200, """
                {"tasks":[{"id":"cu-d","name":"N","description":"desde description",
                  "tags":[{"name":null},{"name":"  "},{"name":"backend"}]}]}
                """));
        server.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));

        // When
        StandardItem obtenida = session().extractAll(List.of("LIST1")).get(0);

        // Then
        assertEquals("desde description", obtenida.description());
        assertEquals(List.of("backend"), obtenida.labels());
    }

    /*
     * CP-U01-14 - Alta con descripción null y cuerpo de respuesta null
     * Esperada: la descripción sale vacía y no se inventa un id.
     * Obtenida: la descripción sale vacía y no se inventa un id.
     */
    @Test
    void createConDescripcionNullYCuerpoNullNoInventaId() throws Exception {
        // Given
        server.enqueue(HttpJson.json(201, "null"));
        StandardItem item = new StandardItem("src", "Nombre", null, List.of("backend"), ItemKind.STORY);

        // When
        String obtenida = session().create(item, "LIST9");

        // Then
        assertNull(obtenida);
        JsonNode body = json.readTree(server.takeRequest().getBody().readUtf8());
        assertEquals("", body.get("description").asText());
    }

    /*
     * CP-U01-15 - Actualización con descripción null
     * Esperada: la descripción sale vacía.
     * Obtenida: la descripción sale vacía.
     */
    @Test
    void updateConDescripcionNullEnviaDescripcionVacia() throws Exception {
        // Given
        server.enqueue(HttpJson.json(200, ""));
        StandardItem item = new StandardItem("src", "Nombre", null, List.of(), ItemKind.STORY);

        // When
        session().update("cu-1", item, "LIST9");

        // Then
        JsonNode body = json.readTree(server.takeRequest().getBody().readUtf8());
        assertEquals("", body.get("description").asText());
        assertEquals(1, server.getRequestCount());
    }

    private ClickUpSession session() {
        ServerConfig config = new ServerConfig();
        config.setName("ClickUp");
        config.setType(ClickUpConnector.TYPE);
        config.setUrl(server.url("/").toString());
        config.setApiToken("token-clickup-test");
        return new ClickUpSession(config);
    }

    private static List<String> textos(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }
}
