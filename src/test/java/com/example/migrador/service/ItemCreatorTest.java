package com.example.migrador.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.example.migrador.connector.ConnectorSession;
import com.example.migrador.domain.ItemKind;
import com.example.migrador.domain.ItemMapping;
import com.example.migrador.domain.ServerConfig;
import com.example.migrador.domain.StandardItem;
import com.example.migrador.repository.ItemMappingRepository;

/*
 * Casos preliminares de creación, actualización, vínculos y errores (U08–U11).
 * Un método = un caso. Repositorio y sesión de destino son mocks.
 */
@ExtendWith(MockitoExtension.class)
class ItemCreatorTest {

    @Mock
    ItemMappingRepository mappings;
    @Mock
    ConnectorSession destino;
    @Captor
    ArgumentCaptor<ItemMapping> mappingCaptor;

    ItemCreator creator;
    ServerConfig origen;
    ServerConfig target;
    StandardItem item;

    @BeforeEach
    void setUp() {
        creator = new ItemCreator(mappings);
        origen = servidor(1L, "CLICKUP");
        target = servidor(2L, "JIRA");
        item = new StandardItem("CU-1", "Título", "Descripción", List.of("backend"), ItemKind.STORY);
    }

    /*
     * CP-U08-01 - No existe relación en ItemMapping
     * Esperada: se solicita la creación en destino.
     * Obtenida: se solicita la creación en destino.
     */
    @Test
    void sinEquivalenciaPideCrear() {
        // Given
        sinEquivalencia("CU-1");
        given(destino.create(item, "PROJ")).willReturn("MIG-1");
        given(mappings.save(any())).willAnswer(returnsFirstArg());

        // When
        MigrationResult obtenida = creator.sync(origen, target, List.of(item), destino, "PROJ");

        // Then
        assertEquals(1, obtenida.created());
        verify(destino).create(item, "PROJ");
    }

    /*
     * CP-U08-02 - Ya existe relación
     * Esperada: no se solicita una nueva creación.
     * Obtenida: no se solicita una nueva creación.
     */
    @Test
    void yaHayEquivalenciaNoCrea() {
        // Given
        given(mappings.findBySourceConfigIdAndTargetConfigIdAndSourceItemId(1L, 2L, "CU-1"))
                .willReturn(Optional.of(new ItemMapping(1L, 2L, "CU-1", "MIG-9")));

        // When
        MigrationResult obtenida = creator.sync(origen, target, List.of(item), destino, "PROJ");

        // Then
        assertEquals(0, obtenida.created());
        verify(destino, never()).create(any(), any());
    }

    /*
     * CP-U08-03 - No existe relación y el elemento es inválido
     * Esperada: no se solicita su creación.
     * Obtenida: sí se solicita su creación.
     */
    @Test
    void itemInvalidoNoPideCrear() {
        // Given
        StandardItem invalido = new StandardItem(null, "", null, List.of(), ItemKind.STORY);
        sinEquivalencia(null);

        // When
        MigrationResult obtenida = creator.sync(origen, target, List.of(invalido), destino, "PROJ");

        // Then
        assertEquals(0, obtenida.created());
        verify(destino, never()).create(any(), any());
    }

    /*
     * CP-U08-04 - Fallo al guardar ItemMapping tras crear el ítem en la API
     * Esperada: no se registra la correspondencia como éxito.
     * Obtenida: create ya devolvió el id, el guardado falla y created queda en 0.
     */
    @Test
    void guardarVinculoFallaDespuesDeCrearNoCuentaExito() {
        // Given
        sinEquivalencia("CU-1");
        given(destino.create(item, "PROJ")).willReturn("MIG-1");
        given(mappings.save(any())).willThrow(new IllegalStateException("fallo al guardar ItemMapping"));

        // When
        MigrationResult obtenida = creator.sync(origen, target, List.of(item), destino, "PROJ");

        // Then
        assertEquals(0, obtenida.created());
        assertEquals(1, obtenida.itemErrors());
        InOrder orden = inOrder(destino, mappings);
        orden.verify(destino).create(item, "PROJ");
        orden.verify(mappings).save(mappingCaptor.capture());
        assertEquals("MIG-1", mappingCaptor.getValue().getTargetItemId());
        assertEquals("CU-1", mappingCaptor.getValue().getSourceItemId());
    }

    /*
     * CP-U08-05 - La API devuelve un fallo de creación
     * Esperada: no se registra una correspondencia como si la creación hubiera tenido éxito.
     * Obtenida: no se registra una correspondencia como si la creación hubiera tenido éxito.
     */
    @Test
    void apiFallaAlCrearNoGuardaEquivalencia() {
        // Given
        sinEquivalencia("CU-1");
        given(destino.create(item, "PROJ")).willThrow(new IllegalStateException("500"));

        // When
        MigrationResult obtenida = creator.sync(origen, target, List.of(item), destino, "PROJ");

        // Then
        assertEquals(0, obtenida.created());
        assertEquals(1, obtenida.itemErrors());
        verify(mappings, never()).save(any());
    }

    /*
     * CP-U09-01 - Existe relación y el origen está modificado
     * Esperada: se solicita la actualización del elemento destino asociado.
     * Obtenida: se solicita la actualización del elemento destino asociado.
     */
    @Test
    void hayEquivalenciaPideActualizar() {
        // Given
        StandardItem cambiado = new StandardItem("CU-1", "Título nuevo", "Desc nueva", List.of("urgente"), ItemKind.STORY);
        given(mappings.findBySourceConfigIdAndTargetConfigIdAndSourceItemId(1L, 2L, "CU-1"))
                .willReturn(Optional.of(new ItemMapping(1L, 2L, "CU-1", "MIG-9")));

        // When
        MigrationResult obtenida = creator.sync(origen, target, List.of(cambiado), destino, "PROJ");

        // Then
        assertEquals(1, obtenida.updated());
        verify(destino).update("MIG-9", cambiado, "PROJ");
    }

    /*
     * CP-U09-02 - El mismo elemento se sincroniza nuevamente
     * Esperada: no se crea un elemento duplicado.
     * Obtenida: no se crea un elemento duplicado.
     */
    @Test
    void segundaSincronizacionNoCreaDuplicado() {
        // Given
        given(mappings.findBySourceConfigIdAndTargetConfigIdAndSourceItemId(1L, 2L, "CU-1"))
                .willReturn(Optional.of(new ItemMapping(1L, 2L, "CU-1", "MIG-9")));

        // When
        creator.sync(origen, target, List.of(item), destino, "PROJ");
        MigrationResult obtenida = creator.sync(origen, target, List.of(item), destino, "PROJ");

        // Then
        assertEquals(0, obtenida.created());
        assertEquals(1, obtenida.updated());
        verify(destino, never()).create(any(), any());
        verify(destino, times(2)).update("MIG-9", item, "PROJ");
    }

    /*
     * CP-U09-03 - El destino posee cambios y llega información del origen
     * Esperada: se actualiza el destino con los valores del origen, sin comparar fechas.
     * Obtenida: se actualiza el destino con los valores del origen, sin comparar fechas.
     */
    @Test
    void sinCompararFechasSobrescribeConElOrigen() {
        // Given
        StandardItem origenMasViejo = new StandardItem("CU-1", "Valor del origen", "Desc", List.of("backend"), ItemKind.STORY);
        given(mappings.findBySourceConfigIdAndTargetConfigIdAndSourceItemId(1L, 2L, "CU-1"))
                .willReturn(Optional.of(new ItemMapping(1L, 2L, "CU-1", "MIG-9")));

        // When
        creator.sync(origen, target, List.of(origenMasViejo), destino, "PROJ");

        // Then
        verify(destino).update("MIG-9", origenMasViejo, "PROJ");
    }

    /*
     * CP-U09-04 - La API rechaza la actualización
     * Esperada: se registra el error y no se reporta la actualización como exitosa.
     * Obtenida: se registra el error y no se reporta la actualización como exitosa.
     */
    @Test
    void apiRechazaLaActualizacionNoCuentaExito() {
        // Given
        given(mappings.findBySourceConfigIdAndTargetConfigIdAndSourceItemId(1L, 2L, "CU-1"))
                .willReturn(Optional.of(new ItemMapping(1L, 2L, "CU-1", "MIG-9")));
        willThrow(new IllegalStateException("403")).given(destino).update("MIG-9", item, "PROJ");

        // When
        MigrationResult obtenida = creator.sync(origen, target, List.of(item), destino, "PROJ");

        // Then
        assertEquals(0, obtenida.updated());
        assertEquals(1, obtenida.itemErrors());
    }

    /*
     * CP-U09-05 - Descripción o etiquetas modificadas
     * Esperada: se envían los nuevos valores conforme a las reglas de mapeo.
     * Obtenida: se envían los nuevos valores conforme a las reglas de mapeo.
     */
    @Test
    void camposModificadosSeEnvianAlDestino() {
        // Given
        StandardItem cambiado = new StandardItem("CU-1", "Título nuevo", "Desc nueva", List.of("urgente"), ItemKind.BUG);
        given(mappings.findBySourceConfigIdAndTargetConfigIdAndSourceItemId(1L, 2L, "CU-1"))
                .willReturn(Optional.of(new ItemMapping(1L, 2L, "CU-1", "MIG-9")));

        // When
        creator.sync(origen, target, List.of(cambiado), destino, "PROJ");

        // Then
        verify(destino).update("MIG-9", cambiado, "PROJ");
    }

    /*
     * CP-U10-01 - Elemento creado exitosamente
     * Esperada: se registra la correspondencia entre origen y destino.
     * Obtenida: se registra la correspondencia entre origen y destino.
     */
    @Test
    void creacionExitosaGuardaLaEquivalencia() {
        // Given
        sinEquivalencia("CU-1");
        given(destino.create(item, "PROJ")).willReturn("MIG-1");
        given(mappings.save(any())).willAnswer(returnsFirstArg());

        // When
        creator.sync(origen, target, List.of(item), destino, "PROJ");

        // Then
        verify(mappings).save(mappingCaptor.capture());
        assertEquals("CU-1", mappingCaptor.getValue().getSourceItemId());
        assertEquals("MIG-1", mappingCaptor.getValue().getTargetItemId());
    }

    /*
     * CP-U10-02 - Existe una correspondencia previa
     * Esperada: se recupera el identificador de destino correcto.
     * Obtenida: se recupera el identificador de destino correcto.
     */
    @Test
    void hayEquivalenciaRecuperaElIdDestino() {
        // Given
        given(mappings.findBySourceConfigIdAndTargetConfigIdAndSourceItemId(1L, 2L, "CU-1"))
                .willReturn(Optional.of(new ItemMapping(1L, 2L, "CU-1", "MIG-9")));

        // When
        creator.sync(origen, target, List.of(item), destino, "PROJ");

        // Then
        verify(destino).update("MIG-9", item, "PROJ");
    }

    /*
     * CP-U10-03 - No existe correspondencia previa
     * Esperada: la consulta indica ausencia de relación.
     * Obtenida: la consulta indica ausencia de relación y se crea.
     */
    @Test
    void sinEquivalenciaNoHayIdDestino() {
        // Given
        sinEquivalencia("CU-1");
        given(destino.create(item, "PROJ")).willReturn("MIG-3");
        given(mappings.save(any())).willAnswer(returnsFirstArg());

        // When
        creator.sync(origen, target, List.of(item), destino, "PROJ");

        // Then
        verify(destino).create(item, "PROJ");
        verify(destino, never()).update(any(), any(), any());
    }

    /*
     * CP-U10-04 - Consulta repetida de la correspondencia
     * Esperada: se obtiene la misma relación sin generar duplicados.
     * Obtenida: se obtiene la misma relación sin generar duplicados.
     */
    @Test
    void consultaRepetidaNoDuplicaLaEquivalencia() {
        // Given
        given(mappings.findBySourceConfigIdAndTargetConfigIdAndSourceItemId(1L, 2L, "CU-1"))
                .willReturn(Optional.of(new ItemMapping(1L, 2L, "CU-1", "MIG-9")));

        // When
        creator.sync(origen, target, List.of(item), destino, "PROJ");
        creator.sync(origen, target, List.of(item), destino, "PROJ");

        // Then
        verify(mappings, never()).save(any());
        verify(destino, times(2)).update("MIG-9", item, "PROJ");
    }

    /*
     * CP-U11-01 - Un elemento falla durante su creación
     * Esperada: se registra el fallo y se continúa con el siguiente elemento.
     * Obtenida: se registra el fallo y se continúa con el siguiente elemento.
     */
    @Test
    void fallaUnItemContinuaConElSiguiente() {
        // Given
        StandardItem siguiente = new StandardItem("CU-2", "Otro", "d", List.of(), ItemKind.STORY);
        sinEquivalencia("CU-1");
        sinEquivalencia("CU-2");
        given(destino.create(item, "PROJ")).willThrow(new IllegalStateException("fallo"));
        given(destino.create(siguiente, "PROJ")).willReturn("MIG-2");
        given(mappings.save(any())).willAnswer(returnsFirstArg());

        // When
        MigrationResult obtenida = creator.sync(origen, target, List.of(item, siguiente), destino, "PROJ");

        // Then
        assertEquals(1, obtenida.created());
        assertEquals(1, obtenida.itemErrors());
        verify(destino).create(siguiente, "PROJ");
    }

    /*
     * CP-U11-02 - Un elemento falla durante su actualización
     * Esperada: se registra el fallo y no se marca la operación como exitosa.
     * Obtenida: se registra el fallo y no se marca la operación como exitosa.
     */
    @Test
    void fallaLaActualizacionNoEsExito() {
        // Given
        given(mappings.findBySourceConfigIdAndTargetConfigIdAndSourceItemId(1L, 2L, "CU-1"))
                .willReturn(Optional.of(new ItemMapping(1L, 2L, "CU-1", "MIG-9")));
        willThrow(new IllegalStateException("fallo")).given(destino).update("MIG-9", item, "PROJ");

        // When
        MigrationResult obtenida = creator.sync(origen, target, List.of(item), destino, "PROJ");

        // Then
        assertEquals(0, obtenida.updated());
        assertEquals(1, obtenida.itemErrors());
        assertTrue(obtenida.message().contains("Items con error: 1"));
    }

    /*
     * CP-U11-03 - Un lote contiene elementos válidos e inválidos
     * Esperada: los válidos continúan y los inválidos se registran como fallidos.
     * Obtenida: los válidos continúan y los inválidos se registran como fallidos.
     */
    @Test
    void loteMixtoProcesaElValidoYRegistraElFallo() {
        // Given
        StandardItem malo = new StandardItem("BAD", "Malo", "x", List.of(), ItemKind.STORY);
        sinEquivalencia("BAD");
        sinEquivalencia("CU-1");
        given(destino.create(malo, "PROJ")).willThrow(new IllegalStateException("invalido"));
        given(destino.create(item, "PROJ")).willReturn("MIG-1");
        given(mappings.save(any())).willAnswer(returnsFirstArg());

        // When
        MigrationResult obtenida = creator.sync(origen, target, List.of(malo, item), destino, "PROJ");

        // Then
        assertEquals(1, obtenida.created());
        assertEquals(1, obtenida.itemErrors());
        assertFalse(obtenida.failed());
    }

    /*
     * CP-U11-04 - Excepción inesperada al procesar un elemento
     * Esperada: la excepción no confirma una migración exitosa.
     * Obtenida: la excepción no confirma una migración exitosa.
     */
    @Test
    void excepcionInesperadaNoConfirmaExito() {
        // Given
        sinEquivalencia("CU-1");
        given(destino.create(item, "PROJ")).willThrow(new RuntimeException("inesperada"));

        // When
        MigrationResult obtenida = creator.sync(origen, target, List.of(item), destino, "PROJ");

        // Then
        assertEquals(1, obtenida.itemErrors());
        assertEquals(0, obtenida.created());
        assertTrue(obtenida.message().contains("Items con error: 1"));
    }

    /*
     * CP-U10-05 - Existe la relación en sentido inverso
     * Esperada: se actualiza el ítem original y no se crea otro.
     * Obtenida: se actualiza el ítem original y no se crea otro.
     */
    @Test
    void syncEquivalenciaInversaActualizaElIdOriginal() {
        // Given
        given(mappings.findBySourceConfigIdAndTargetConfigIdAndSourceItemId(1L, 2L, "CU-1"))
                .willReturn(Optional.empty());
        given(mappings.findBySourceConfigIdAndTargetConfigIdAndTargetItemId(2L, 1L, "CU-1"))
                .willReturn(Optional.of(new ItemMapping(2L, 1L, "MIG-4", "CU-1")));
        given(mappings.save(any())).willAnswer(returnsFirstArg());

        // When
        MigrationResult obtenida = creator.sync(origen, target, List.of(item), destino, "PROJ");

        // Then
        assertEquals(1, obtenida.updated());
        assertEquals(0, obtenida.created());
        verify(destino).update("MIG-4", item, "PROJ");
        verify(destino, never()).create(any(), any());
    }

    private void sinEquivalencia(String externalId) {
        given(mappings.findBySourceConfigIdAndTargetConfigIdAndSourceItemId(1L, 2L, externalId))
                .willReturn(Optional.empty());
        given(mappings.findBySourceConfigIdAndTargetConfigIdAndTargetItemId(2L, 1L, externalId))
                .willReturn(Optional.empty());
    }

    private static ServerConfig servidor(long id, String type) {
        ServerConfig config = new ServerConfig();
        config.setId(id);
        config.setName("s" + id);
        config.setType(type);
        config.setUrl("http://localhost");
        config.setUsername("qa@example.com");
        config.setApiToken("token-local");
        return config;
    }
}
