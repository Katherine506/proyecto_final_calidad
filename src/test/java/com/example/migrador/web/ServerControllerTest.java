package com.example.migrador.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;

import com.example.migrador.connector.ConnectorRegistry;
import com.example.migrador.connector.ServerConnector;
import com.example.migrador.domain.ServerConfig;
import com.example.migrador.repository.ItemMappingRepository;
import com.example.migrador.repository.ServerConfigRepository;

/*
 * U20. El alta, la edición y el borrado se prueban con el repositorio simulado.
 */
@ExtendWith(MockitoExtension.class)
class ServerControllerTest {

    @Mock
    ServerConfigRepository configs;
    @Mock
    ItemMappingRepository mappings;
    @Mock
    ConnectorRegistry registry;
    @Mock
    ServerConnector conector;

    ServerController controller;

    @BeforeEach
    void setUp() {
        controller = new ServerController(configs, mappings, registry);
    }

    /*
     * CP-U20-01 - Hay configuraciones guardadas
     * Esperada: la vista de servidores recibe esa lista.
     * Obtenida: la vista de servidores recibe esa lista.
     */
    @Test
    void listarEntregaLasConfiguracionesOrdenadas() {
        // Given
        List<ServerConfig> guardadas = List.of(servidor(1L, "token-local"));
        given(configs.findAllByOrderByNameAsc()).willReturn(guardadas);
        ExtendedModelMap model = new ExtendedModelMap();

        // When
        String vista = controller.list(model);

        // Then
        assertEquals("servers", vista);
        assertSame(guardadas, model.get("configs"));
    }

    /*
     * CP-U20-02 - Se abre el formulario de alta
     * Esperada: hay un servidor vacío y los tipos del registro.
     * Obtenida: hay un servidor vacío y los tipos del registro.
     */
    @Test
    void formularioNuevoTraeServidorVacioYLosTipos() {
        // Given
        List<ServerConnector> tipos = List.of(conector);
        given(registry.all()).willReturn(tipos);
        ExtendedModelMap model = new ExtendedModelMap();

        // When
        String vista = controller.create(model);

        // Then
        assertEquals("server-form", vista);
        assertEquals(null, ((ServerConfig) model.get("server")).getId());
        assertSame(tipos, model.get("types"));
    }

    /*
     * CP-U20-03 - Se abre la edición de un servidor que existe
     * Esperada: el formulario recibe ese servidor.
     * Obtenida: el formulario recibe ese servidor.
     */
    @Test
    void editarCargaElServidorGuardado() {
        // Given
        ServerConfig guardado = servidor(3L, "token-local");
        given(configs.findById(3L)).willReturn(Optional.of(guardado));
        given(registry.all()).willReturn(List.of(conector));
        ExtendedModelMap model = new ExtendedModelMap();

        // When
        String vista = controller.edit(3L, model);

        // Then
        assertEquals("server-form", vista);
        assertSame(guardado, model.get("server"));
    }

    /*
     * CP-U20-04 - El alta no trae id
     * Esperada: se guarda el formulario y no se busca uno previo.
     * Obtenida: se guarda el formulario y no se busca uno previo.
     */
    @Test
    void altaGuardaElFormularioSinBuscarOtro() {
        // Given
        ServerConfig form = servidor(null, "token-nuevo");

        // When
        String vista = controller.save(form);

        // Then
        assertEquals("redirect:/servers", vista);
        verify(configs).save(form);
        verify(configs, never()).findById(any());
    }

    /*
     * CP-U20-05 - Al editar, el token llega en blanco
     * Esperada: se conserva el token ya guardado.
     * Obtenida: se conserva el token ya guardado.
     */
    @Test
    void editarConTokenEnBlancoConservaElGuardado() {
        // Given
        ServerConfig guardado = servidor(3L, "token-local");
        ServerConfig form = servidor(3L, "   ");
        given(configs.findById(3L)).willReturn(Optional.of(guardado));

        // When
        controller.save(form);

        // Then
        ArgumentCaptor<ServerConfig> captor = ArgumentCaptor.forClass(ServerConfig.class);
        verify(configs).save(captor.capture());
        assertEquals("token-local", captor.getValue().getApiToken());
    }

    /*
     * CP-U20-06 - Al editar, el token llega con valor
     * Esperada: se guarda el token nuevo.
     * Obtenida: se guarda el token nuevo.
     */
    @Test
    void editarConTokenNuevoLoGuarda() {
        // Given
        given(configs.findById(3L)).willReturn(Optional.of(servidor(3L, "token-local")));
        ServerConfig form = servidor(3L, "token-nuevo");

        // When
        controller.save(form);

        // Then
        ArgumentCaptor<ServerConfig> captor = ArgumentCaptor.forClass(ServerConfig.class);
        verify(configs).save(captor.capture());
        assertEquals("token-nuevo", captor.getValue().getApiToken());
    }

    /*
     * CP-U20-07 - Se borra un servidor
     * Esperada: primero se borran las equivalencias y después la configuración.
     * Obtenida: primero se borran las equivalencias y después la configuración.
     */
    @Test
    void borrarQuitaEquivalenciasYDespuesLaConfiguracion() {
        // When
        String vista = controller.delete(3L);

        // Then
        assertEquals("redirect:/servers", vista);
        InOrder orden = inOrder(mappings, configs);
        orden.verify(mappings).deleteBySourceConfigIdOrTargetConfigId(3L, 3L);
        orden.verify(configs).deleteById(3L);
    }

    /*
     * CP-U20-08 - Se edita un id que no existe
     * Esperada: no se guarda nada.
     * Obtenida: no se guarda nada.
     */
    @Test
    void editarInexistenteNoGuarda() {
        // Given
        given(configs.findById(3L)).willReturn(Optional.empty());

        // When / Then
        assertThrows(NoSuchElementException.class, () -> controller.save(servidor(3L, "token-nuevo")));
        verify(configs, never()).save(any());
    }

    private static ServerConfig servidor(Long id, String token) {
        ServerConfig config = new ServerConfig();
        config.setId(id);
        config.setName("Servidor");
        config.setType("CLICKUP");
        config.setUrl("https://example.test");
        config.setApiToken(token);
        return config;
    }
}
