package com.example.migrador.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.migrador.connector.ConnectorRegistry;
import com.example.migrador.repository.ServerConfigRepository;
import com.example.migrador.repository.UserRepository;
import com.example.migrador.service.MigrationResult;
import com.example.migrador.service.MigrationService;

/*
 * U12. Un método = un caso. Se prueba el controller, no la regla de negocio.
 */
@WebMvcTest(MigrationController.class)
class MigrationControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    MigrationService migrationService;

    @MockitoBean
    ServerConfigRepository configs;

    @MockitoBean
    ConnectorRegistry registry;

    @MockitoBean
    UserRepository users;

    /*
     * CP-U12-01 - Origen, destino y al menos un tablero seleccionados
     * Esperada: se habilita la migración.
     * Obtenida: la migración se ejecuta.
     */
    @Test
    void origenDestinoYBoardEjecutaLaMigracion() throws Exception {
        // Given
        given(migrationService.migrate(any(), any(), any(), any()))
                .willReturn(new MigrationResult(false, 0, 0, 0));

        // When
        mvc.perform(post("/migrate").sessionAttr("username", "admin")
                        .param("sourceId", "1")
                        .param("targetId", "2")
                        .param("sourceBoardIds", "LIST1")
                        .param("targetBoardId", "PROJ"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        // Then
        verify(migrationService).migrate(1L, 2L, List.of("LIST1"), "PROJ");
    }

    /*
     * CP-U12-02 - No se selecciona origen
     * Esperada: la migración permanece deshabilitada.
     * Obtenida: la petición se rechaza y no se migra.
     */
    @Test
    void sinOrigenNoMigra() throws Exception {
        // Given
        // When
        mvc.perform(post("/migrate").sessionAttr("username", "admin")
                        .param("targetId", "2")
                        .param("sourceBoardIds", "LIST1")
                        .param("targetBoardId", "PROJ"))
                .andExpect(status().isBadRequest());

        // Then
        verify(migrationService, never()).migrate(any(), any(), any(), any());
    }

    /*
     * CP-U12-03 - No se selecciona destino
     * Esperada: la migración permanece deshabilitada.
     * Obtenida: la petición se rechaza y no se migra.
     */
    @Test
    void sinDestinoNoMigra() throws Exception {
        // Given
        // When
        mvc.perform(post("/migrate").sessionAttr("username", "admin")
                        .param("sourceId", "1")
                        .param("sourceBoardIds", "LIST1")
                        .param("targetBoardId", "PROJ"))
                .andExpect(status().isBadRequest());

        // Then
        verify(migrationService, never()).migrate(any(), any(), any(), any());
    }

    /*
     * CP-U12-04 - No se selecciona ningún tablero
     * Esperada: la migración permanece deshabilitada.
     * Obtenida: la petición se rechaza y no se migra.
     */
    @Test
    void sinBoardNoMigra() throws Exception {
        // Given
        // When
        mvc.perform(post("/migrate").sessionAttr("username", "admin")
                        .param("sourceId", "1")
                        .param("targetId", "2")
                        .param("targetBoardId", "PROJ"))
                .andExpect(status().isBadRequest());

        // Then
        verify(migrationService, never()).migrate(any(), any(), any(), any());
    }

    /*
     * CP-U12-05 - Se seleccionan varios tableros válidos
     * Esperada: los tableros seleccionados se incluyen en la solicitud.
     * Obtenida: los tableros seleccionados se incluyen en la solicitud.
     */
    @Test
    void variosBoardsSeIncluyenEnLaSolicitud() throws Exception {
        // Given
        given(migrationService.migrate(any(), any(), any(), any()))
                .willReturn(new MigrationResult(false, 0, 0, 0));

        // When
        mvc.perform(post("/migrate").sessionAttr("username", "admin")
                        .param("sourceId", "1")
                        .param("targetId", "2")
                        .param("sourceBoardIds", "LIST1")
                        .param("sourceBoardIds", "LIST2")
                        .param("targetBoardId", "PROJ"))
                .andExpect(status().is3xxRedirection());

        // Then
        verify(migrationService).migrate(eq(1L), eq(2L), eq(List.of("LIST1", "LIST2")), eq("PROJ"));
    }
}
