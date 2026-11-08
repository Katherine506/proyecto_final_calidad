package com.example.migrador.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/*
 * U16. Navegación de los JSON que devuelven las APIs.
 * Convención de nombres: metodo_condicion_resultadoEsperado
 */
class JsonParsingTest {

    /*
     * CP-U16-01 - Valor null o que no es un mapa
     * Esperada: se obtiene un mapa vacío; un mapa real conserva sus campos.
     * Obtenida: se obtiene un mapa vacío; un mapa real conserva sus campos.
     */
    @Test
    void mapNullONoMapaDevuelveMapaVacio() {
        assertTrue(Json.map(null).isEmpty());
        assertTrue(Json.map("no es un mapa").isEmpty());
        assertEquals("1", Json.map(Map.of("id", "1")).get("id"));
    }

    /*
     * CP-U16-02 - Valor null o que no es una lista
     * Esperada: se obtiene una lista vacía y la original no se altera.
     * Obtenida: se obtiene una lista vacía y la original no se altera.
     */
    @Test
    void listNullONoListaDevuelveListaVacia() {
        List<String> original = new ArrayList<>(List.of("a", "b"));

        List<Object> copia = Json.list(original);
        copia.add("c");

        assertEquals(List.of(), Json.list(null));
        assertEquals(List.of(), Json.list("no es una lista"));
        assertEquals(List.of("a", "b"), original);
    }

    /*
     * CP-U16-03 - Texto null y otros valores
     * Esperada: null se conserva y el resto usa su representación de texto.
     * Obtenida: null se conserva y el resto usa su representación de texto.
     */
    @Test
    void strNullSeConservaYElRestoUsaToString() {
        assertEquals(null, Json.str(null));
        assertEquals("x", Json.str("x"));
        assertEquals("10", Json.str(10));
    }
}
