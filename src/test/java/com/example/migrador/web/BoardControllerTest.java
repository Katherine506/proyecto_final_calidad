package com.example.migrador.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.example.migrador.connector.ConnectorRegistry;
import com.example.migrador.connector.ConnectorSession;
import com.example.migrador.connector.ServerConnector;
import com.example.migrador.domain.BoardOption;
import com.example.migrador.domain.ServerConfig;
import com.example.migrador.repository.ServerConfigRepository;

import static org.mockito.BDDMockito.given;

/*
 * U18. El controller se construye a mano. El repositorio y el conector son mocks.
 */
@ExtendWith(MockitoExtension.class)
class BoardControllerTest {

    @Mock
    ServerConfigRepository configs;
    @Mock
    ConnectorRegistry registry;
    @Mock
    ServerConnector conector;
    @Mock
    ConnectorSession sesion;

    BoardController controller;

    @BeforeEach
    void setUp() {
        controller = new BoardController(configs, registry);
    }

    /*
     * CP-U18-01 - La configuración existe y el conector lista tableros
     * Esperada: la respuesta es 200 y trae los tableros.
     * Obtenida: la respuesta es 200 y trae los tableros.
     */
    @Test
    void configExistenteDevuelveLosTableros() {
        // Given
        ServerConfig config = servidor(4L, "CLICKUP");
        List<BoardOption> tableros = List.of(new BoardOption("LIST1", "Espacio / Lista"));
        given(configs.findById(4L)).willReturn(Optional.of(config));
        given(registry.get("CLICKUP")).willReturn(conector);
        given(conector.connect(config)).willReturn(sesion);
        given(sesion.listBoards()).willReturn(tableros);

        // When
        ResponseEntity<?> respuesta = controller.boards(4L);

        // Then
        assertEquals(200, respuesta.getStatusCode().value());
        assertEquals(tableros, respuesta.getBody());
        verify(conector).connect(config);
    }

    /*
     * CP-U18-02 - No existe la configuración
     * Esperada: responde 500 y no abre un conector.
     * Obtenida: responde 500 y no abre un conector.
     */
    @Test
    void configInexistenteRespondeErrorYNoAbreConector() {
        // Given
        given(configs.findById(4L)).willReturn(Optional.empty());

        // When
        ResponseEntity<?> respuesta = controller.boards(4L);

        // Then
        assertEquals(500, respuesta.getStatusCode().value());
        assertEquals(Map.of("error", "No se pudieron cargar los tableros."), respuesta.getBody());
        verify(registry, never()).get(org.mockito.ArgumentMatchers.any());
    }

    /*
     * CP-U18-03 - El conector falla al listar y el error trae un token
     * Esperada: responde 500 con el mensaje fijo y no devuelve el token.
     * Obtenida: responde 500 con el mensaje fijo y no devuelve el token.
     */
    @Test
    void falloAlListarRespondeErrorSinElToken() {
        // Given
        ServerConfig config = servidor(4L, "JIRA");
        given(configs.findById(4L)).willReturn(Optional.of(config));
        given(registry.get("JIRA")).willReturn(conector);
        given(conector.connect(config)).willReturn(sesion);
        given(sesion.listBoards()).willThrow(new IllegalStateException("Authorization pk_test_NO_LOG_55aa"));

        // When
        ResponseEntity<?> respuesta = controller.boards(4L);

        // Then
        assertEquals(500, respuesta.getStatusCode().value());
        assertEquals("No se pudieron cargar los tableros.", ((Map<?, ?>) respuesta.getBody()).get("error"));
        assertFalse(respuesta.getBody().toString().contains("pk_test_NO_LOG_55aa"));
    }

    private static ServerConfig servidor(Long id, String tipo) {
        ServerConfig config = new ServerConfig();
        config.setId(id);
        config.setName("Origen");
        config.setType(tipo);
        config.setUrl("https://example.test");
        config.setApiToken("token-local");
        return config;
    }
}
