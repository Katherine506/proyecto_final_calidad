package com.example.migrador.connector;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Prueba mínima de las utilidades JSON compartidas por los conectores. */
class MappingTest {

    @Test
    void jsonStrHandlesNull() {
        assertEquals(null, Json.str(null));
        assertEquals("x", Json.str("x"));
    }

    @Test
    void jsonListAndMapAreSafe() {
        assertEquals(List.of(), Json.list(null));
        assertEquals(Map.of(), Json.map("no es un mapa"));
    }
}
