package com.example.migrador.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import com.example.migrador.connector.ConnectorRegistry;
import com.example.migrador.connector.ConnectorSession;
import com.example.migrador.connector.ServerConnector;
import com.example.migrador.domain.ItemKind;
import com.example.migrador.domain.ServerConfig;
import com.example.migrador.domain.StandardItem;
import com.example.migrador.repository.ServerConfigRepository;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/*
 * MigrationServiceTest — pruebas UNITARIAS del orquestador (sin Spring, sin APIs).
 *
 * Colaboradores simulados: repositorio de configuraciones, registro de conectores, ItemCreator
 * Convención de nombres:   metodo_condicion_resultadoEsperado
 */
@ExtendWith(MockitoExtension.class)
class MigrationServiceTest {

    private static final String TOKEN = "pk_test_NO_LOG_55aa";

    @Mock
    ServerConfigRepository configs;
    @Mock
    ConnectorRegistry registry;
    @Mock
    ItemCreator itemCreator;
    @Mock
    ServerConnector conectorOrigen;
    @Mock
    ServerConnector conectorDestino;
    @Mock
    ConnectorSession sesionOrigen;
    @Mock
    ConnectorSession sesionDestino;

    MigrationService service;

    @BeforeEach
    void setUp() {
        service = new MigrationService(configs, registry, itemCreator);
    }

    /*
     * CP-U13-01 - Origen y destino disponibles
     * Esperada: el resultado es el que devuelve el creador de ítems.
     * Obtenida: el resultado es el que devuelve el creador de ítems.
     */
    @Test
    void migrateConectoresDisponiblesDelegaEnItemCreator() {
        // Given
        ServerConfig origen = servidor(1L, "CLICKUP");
        ServerConfig destino = servidor(2L, "JIRA");
        StandardItem item = new StandardItem("CU-1", "T", "D", List.of(), ItemKind.STORY);
        given(configs.findById(1L)).willReturn(Optional.of(origen));
        given(configs.findById(2L)).willReturn(Optional.of(destino));
        given(registry.get("CLICKUP")).willReturn(conectorOrigen);
        given(registry.get("JIRA")).willReturn(conectorDestino);
        given(conectorOrigen.connect(origen)).willReturn(sesionOrigen);
        given(conectorDestino.connect(destino)).willReturn(sesionDestino);
        given(sesionOrigen.extractAll(List.of("LIST"))).willReturn(List.of(item));
        given(itemCreator.sync(origen, destino, List.of(item), sesionDestino, "PROJ"))
                .willReturn(new MigrationResult(false, 1, 0, 0));

        // When
        MigrationResult resultado = service.migrate(1L, 2L, List.of("LIST"), "PROJ");

        // Then
        assertEquals(1, resultado.created());
        assertEquals("Migracion terminada: 1 creados, 0 actualizados", resultado.message());
    }

    /*
     * CP-U11-06 - Falla la extracción porque el origen no existe
     * Esperada: no se confirma una migración exitosa.
     * Obtenida: el mensaje dice que no se pudo completar la migración.
     */
    @Test
    void migrateOrigenInexistenteRetornaFallo() {
        // Given
        given(configs.findById(1L)).willReturn(Optional.empty());

        // When
        MigrationResult resultado = service.migrate(1L, 2L, List.of("LIST"), "PROJ");

        // Then
        assertTrue(resultado.failed());
        assertEquals("No se pudo completar la migracion.", resultado.message());
    }

    /*
     * CP-U14-02 - La excepción interna contiene un API token
     * Esperada: el log no imprime el token.
     * Obtenida: el log explica el fallo y no imprime el token.
     */
    @Test
    void migrateExcepcionConTokenNoLoEscribeEnElLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(MigrationService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            given(configs.findById(9L)).willThrow(new IllegalStateException("Authorization " + TOKEN));

            MigrationResult resultado = service.migrate(9L, 2L, List.of("LIST"), "PROJ");

            assertTrue(resultado.failed());
            assertEquals("No se pudo completar la migracion.", resultado.message());
            assertEquals(1, appender.list.size());
            ILoggingEvent evento = appender.list.get(0);
            assertEquals("No se pudo completar la migracion.", evento.getFormattedMessage());
            assertNull(evento.getThrowableProxy());
            assertFalse(evento.getFormattedMessage().contains(TOKEN));
        } finally {
            logger.detachAppender(appender);
        }
    }

    private static ServerConfig servidor(long id, String type) {
        ServerConfig config = new ServerConfig();
        config.setId(id);
        config.setName("s" + id);
        config.setType(type);
        config.setUrl("http://localhost");
        config.setApiToken("token-local");
        return config;
    }
}
