package com.example.migrador.exploratory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import com.example.migrador.connector.ConnectorRegistry;
import com.example.migrador.domain.ItemMapping;
import com.example.migrador.domain.ServerConfig;
import com.example.migrador.domain.User;
import com.example.migrador.repository.ItemMappingRepository;
import com.example.migrador.repository.ServerConfigRepository;
import com.example.migrador.repository.UserRepository;
import com.example.migrador.support.HttpJson;

import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:exploratory-security;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@AutoConfigureMockMvc
class ExploratoryWebSecurityTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository users;
    @Autowired
    ServerConfigRepository configs;
    @Autowired
    ItemMappingRepository mappings;
    @Autowired
    ConnectorRegistry registry;

    private MockWebServer credentialSink;

    @BeforeEach
    void setUp() throws Exception {
        credentialSink = new MockWebServer();
        credentialSink.start();
        mappings.deleteAll();
        configs.deleteAll();
    }

    @AfterEach
    void tearDown() throws Exception {
        credentialSink.shutdown();
    }

    @Test
    void credencialesAdministrativasPredeterminadasInicianSesion() throws Exception {
        mvc.perform(post("/login")
                        .param("username", "admin")
                        .param("password", "admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        assertTrue(users.findByUsername("admin").isPresent());
    }

    @Test
    void rutaProtegidaRedirigeAlLoginSinSesion() throws Exception {
        mvc.perform(get("/servers"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void paginaLoginNoIncluyeCabecerasDeProteccionDelNavegador() throws Exception {
        var response = mvc.perform(get("/login")).andReturn().getResponse();

        assertNull(response.getHeader("Content-Security-Policy"));
        assertNull(response.getHeader("X-Frame-Options"));
        assertNull(response.getHeader("X-Content-Type-Options"));
    }

    @Test
    void registroPersisteLaContrasenaSinTransformarla() throws Exception {
        String passwordFicticia = "exploratory-local-password-2026";

        mvc.perform(post("/register")
                        .param("username", "exploratory-user")
                        .param("password", passwordFicticia))
                .andExpect(status().is3xxRedirection());

        User persisted = users.findByUsername("exploratory-user").orElseThrow();
        assertEquals(passwordFicticia, persisted.getPassword());
    }

    @Test
    void usuarioAnonimoPuedeRegistrarseYEliminarLaConfiguracionDeOtroUsuario() throws Exception {
        ServerConfig privateConfig = new ServerConfig();
        privateConfig.setName("other-users-private-config");
        privateConfig.setType("JIRA");
        privateConfig.setUrl("https://jira.invalid");
        privateConfig.setUsername("owner@example.test");
        privateConfig.setApiToken("fictional-owner-token");
        ServerConfig savedConfig = configs.save(privateConfig);

        var registration = mvc.perform(post("/register")
                        .param("username", "exploratory-attacker")
                        .param("password", "fictional-password"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession newAccountSession =
                (MockHttpSession) registration.getRequest().getSession(false);
        assertNotNull(newAccountSession);
        assertEquals("exploratory-attacker", newAccountSession.getAttribute("username"));

        mvc.perform(get("/servers").session(newAccountSession))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.containsString("other-users-private-config")));

        mvc.perform(post("/servers/{id}/delete", savedConfig.getId()).session(newAccountSession))
                .andExpect(status().is3xxRedirection());

        assertTrue(configs.findById(savedConfig.getId()).isEmpty());
    }

    @Test
    void inicioDeSesionNoRotaElIdentificadorDeUnaSesionPreAutenticada() throws Exception {
        User user = new User();
        user.setUsername("session-fixation-user");
        user.setPassword("fictional-password");
        users.save(user);
        MockHttpSession session = new MockHttpSession();
        String originalSessionId = session.getId();

        var login = mvc.perform(post("/login")
                        .session(session)
                        .param("username", "session-fixation-user")
                        .param("password", "fictional-password"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        var authenticatedSession = login.getRequest().getSession(false);
        assertNotNull(authenticatedSession);
        assertEquals(originalSessionId, authenticatedSession.getId());
        assertEquals("session-fixation-user", session.getAttribute("username"));
    }

    @Test
    void reporteRenderizaUrlJavascriptProvenienteDeConfiguracionPersistida() throws Exception {
        ServerConfig source = new ServerConfig();
        source.setName("exploratory-link");
        source.setType("JIRA");
        source.setUrl("javascript:alert(1)");
        source.setUsername("qa@example.test");
        source.setApiToken("fictional-token");
        source = configs.save(source);
        ServerConfig target = new ServerConfig();
        target.setName("exploratory-target");
        target.setType("JIRA");
        target.setUrl("https://jira.invalid");
        target.setUsername("qa@example.test");
        target.setApiToken("fictional-token");
        target = configs.save(target);
        mappings.save(new ItemMapping(source.getId(), target.getId(), "SRC-1", "TGT-1"));

        String html = mvc.perform(get("/reports").sessionAttr("username", "admin"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(html.contains("href=\"javascript:alert(1)/browse/SRC-1\""));
    }

    @Test
    void postDeCambioAceptaOrigenNoConfiableConSesionAutenticada() throws Exception {
        mvc.perform(post("/servers")
                        .header("Origin", "https://attacker.invalid")
                        .sessionAttr("username", "admin")
                        .param("name", "exploratory-csrf")
                        .param("type", "JIRA")
                        .param("url", "http://127.0.0.1:1")
                        .param("username", "qa@example.test")
                        .param("apiToken", "exploratory-token-not-real"))
                .andExpect(status().is3xxRedirection());

        assertTrue(configs.findAll().stream()
                .anyMatch(config -> "exploratory-csrf".equals(config.getName())));
    }

    @Test
    void conectorJiraEnviaCredencialFicticiaAUrlConfiguradaPorFormulario() throws Exception {
        credentialSink.enqueue(HttpJson.json(200, "{\"values\":[],\"isLast\":true}"));
        String sinkUrl = credentialSink.url("/").toString();

        mvc.perform(post("/servers")
                        .sessionAttr("username", "admin")
                        .param("name", "exploratory-local-jira")
                        .param("type", "JIRA")
                        .param("url", sinkUrl)
                        .param("username", "qa@example.test")
                        .param("apiToken", "exploratory-token-not-real"))
                .andExpect(status().is3xxRedirection());

        ServerConfig saved = configs.findAll().stream()
                .filter(config -> "exploratory-local-jira".equals(config.getName()))
                .findFirst()
                .orElseThrow();
        registry.get("JIRA").connect(saved).listBoards();

        RecordedRequest request = credentialSink.takeRequest();
        String expectedAuth = "Basic " + Base64.getEncoder().encodeToString(
                "qa@example.test:exploratory-token-not-real".getBytes(StandardCharsets.UTF_8));
        assertNotNull(request);
        assertNotNull(request.getPath());
        assertTrue(request.getPath().startsWith("/rest/api/3/project/search"));
        assertEquals(expectedAuth, request.getHeader("Authorization"));
    }
}
