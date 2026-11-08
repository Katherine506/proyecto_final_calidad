package com.example.migrador.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;

import com.example.migrador.connector.ConnectorRegistry;
import com.example.migrador.connector.ServerConnector;
import com.example.migrador.domain.ItemMapping;
import com.example.migrador.domain.ServerConfig;
import com.example.migrador.repository.ItemMappingRepository;
import com.example.migrador.repository.ServerConfigRepository;

/*
 * U19. El reporte se arma sin Spring. Repositorios y conectores son mocks.
 */
@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock
    ItemMappingRepository mappings;
    @Mock
    ServerConfigRepository configs;
    @Mock
    ConnectorRegistry registry;
    @Mock
    ServerConnector conectorOrigen;
    @Mock
    ServerConnector conectorDestino;

    ReportController controller;

    @BeforeEach
    void setUp() {
        controller = new ReportController(mappings, configs, registry);
    }

    /*
     * CP-U19-01 - Origen y destino existen
     * Esperada: la fila trae los nombres, los tipos y los dos enlaces.
     * Obtenida: la fila trae los nombres, los tipos y los dos enlaces.
     */
    @Test
    void servidoresPresentesArmanLaFilaConEnlaces() {
        // Given
        ServerConfig origen = servidor(1L, "ClickUp local", "CLICKUP");
        ServerConfig destino = servidor(2L, "Jira local", "JIRA");
        ItemMapping mapping = new ItemMapping(1L, 2L, "cu-1", "MIG-1");
        given(configs.findAll()).willReturn(List.of(origen, destino));
        given(mappings.findAllByOrderByIdDesc()).willReturn(List.of(mapping));
        given(registry.get("CLICKUP")).willReturn(conectorOrigen);
        given(registry.get("JIRA")).willReturn(conectorDestino);
        given(conectorOrigen.itemUrl(origen, "cu-1")).willReturn("https://app.clickup.com/t/cu-1");
        given(conectorDestino.itemUrl(destino, "MIG-1")).willReturn("https://qa.atlassian.net/browse/MIG-1");
        ExtendedModelMap model = new ExtendedModelMap();

        // When
        String vista = controller.report(model);

        // Then
        assertEquals("reports", vista);
        ReportRow fila = filas(model).get(0);
        assertEquals("ClickUp local", fila.sourceConfigName());
        assertEquals("CLICKUP", fila.sourceType());
        assertEquals("cu-1", fila.sourceItemId());
        assertEquals("https://app.clickup.com/t/cu-1", fila.sourceUrl());
        assertEquals("Jira local", fila.targetConfigName());
        assertEquals("JIRA", fila.targetType());
        assertEquals("MIG-1", fila.targetItemId());
        assertEquals("https://qa.atlassian.net/browse/MIG-1", fila.targetUrl());
        assertTrue(fila.bothLinksAvailable());
    }

    /*
     * CP-U19-02 - El servidor de origen ya no existe
     * Esperada: el origen queda como eliminado y sin enlace; el destino sí tiene enlace.
     * Obtenida: el origen queda como eliminado y sin enlace; el destino sí tiene enlace.
     */
    @Test
    void origenEliminadoDejaLaFilaSinEseEnlace() {
        // Given
        ServerConfig destino = servidor(2L, "Jira local", "JIRA");
        given(configs.findAll()).willReturn(List.of(destino));
        given(mappings.findAllByOrderByIdDesc()).willReturn(List.of(new ItemMapping(1L, 2L, "cu-1", "MIG-1")));
        given(registry.get("JIRA")).willReturn(conectorDestino);
        given(conectorDestino.itemUrl(destino, "MIG-1")).willReturn("https://qa.atlassian.net/browse/MIG-1");
        ExtendedModelMap model = new ExtendedModelMap();

        // When
        controller.report(model);

        // Then
        ReportRow fila = filas(model).get(0);
        assertEquals("(servidor eliminado)", fila.sourceConfigName());
        assertEquals("?", fila.sourceType());
        assertNull(fila.sourceUrl());
        assertEquals("Jira local", fila.targetConfigName());
        assertEquals("https://qa.atlassian.net/browse/MIG-1", fila.targetUrl());
        assertFalse(fila.bothLinksAvailable());
    }

    /*
     * CP-U19-03 - Armar el enlace lanza una excepción
     * Esperada: la fila se conserva y ese enlace queda null.
     * Obtenida: la fila se conserva y ese enlace queda null.
     */
    @Test
    void falloAlArmarEnlaceDejaLaUrlEnNull() {
        // Given
        ServerConfig origen = servidor(1L, "ClickUp local", "CLICKUP");
        ServerConfig destino = servidor(2L, "Jira local", "JIRA");
        given(configs.findAll()).willReturn(List.of(origen, destino));
        given(mappings.findAllByOrderByIdDesc()).willReturn(List.of(new ItemMapping(1L, 2L, "cu-1", "MIG-1")));
        given(registry.get("CLICKUP")).willReturn(conectorOrigen);
        given(registry.get("JIRA")).willReturn(conectorDestino);
        given(conectorOrigen.itemUrl(origen, "cu-1")).willThrow(new IllegalStateException("pk_test_NO_LOG_55aa"));
        given(conectorDestino.itemUrl(destino, "MIG-1")).willReturn("https://qa.atlassian.net/browse/MIG-1");
        ExtendedModelMap model = new ExtendedModelMap();

        // When
        controller.report(model);

        // Then
        ReportRow fila = filas(model).get(0);
        assertNull(fila.sourceUrl());
        assertEquals("https://qa.atlassian.net/browse/MIG-1", fila.targetUrl());
        assertEquals(1, filas(model).size());
    }

    /*
     * CP-U19-04 - Armar la fila lanza una excepción
     * Esperada: esa fila no entra al reporte y las demás sí.
     * Obtenida: esa fila no entra al reporte y las demás sí.
     */
    @Test
    void filaQueFallaNoEntraYLaSiguienteSi() {
        // Given
        ServerConfig origen = org.mockito.Mockito.mock(ServerConfig.class);
        given(origen.getId()).willReturn(1L);
        given(origen.getName()).willThrow(new IllegalStateException("nombre roto"));
        ServerConfig destino = servidor(2L, "Jira local", "JIRA");
        ItemMapping rota = new ItemMapping(1L, 2L, "cu-malo", "MIG-0");
        ItemMapping buena = new ItemMapping(9L, 2L, "cu-bueno", "MIG-2");
        given(configs.findAll()).willReturn(List.of(origen, destino));
        given(mappings.findAllByOrderByIdDesc()).willReturn(List.of(rota, buena));
        given(registry.get("JIRA")).willReturn(conectorDestino);
        given(conectorDestino.itemUrl(destino, "MIG-2")).willReturn("https://qa.atlassian.net/browse/MIG-2");
        ExtendedModelMap model = new ExtendedModelMap();

        // When
        controller.report(model);

        // Then
        assertEquals(1, filas(model).size());
        assertEquals("cu-bueno", filas(model).get(0).sourceItemId());
        assertEquals("(servidor eliminado)", filas(model).get(0).sourceConfigName());
    }

    @SuppressWarnings("unchecked")
    private static List<ReportRow> filas(ExtendedModelMap model) {
        return (List<ReportRow>) model.get("rows");
    }

    private static ServerConfig servidor(Long id, String nombre, String tipo) {
        ServerConfig config = new ServerConfig();
        config.setId(id);
        config.setName(nombre);
        config.setType(tipo);
        config.setUrl("https://example.test");
        config.setApiToken("token-local");
        return config;
    }
}
