package com.example.migrador.connector.jira;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/*
 * U04. Un método por caso preliminar.
 * El Then exige la salida esperada del plan.
 */
class AdfUtilTest {

    /*
     * CP-U04-01 - Descripción con texto simple
     * Esperada: el contenido textual se conserva.
     * Obtenida: el contenido textual se conserva.
     */
    @Test
    void textoSimpleConservaElContenido() {
        // Given
        String entrada = "Migrar el tablero";

        // When
        String obtenida = AdfUtil.toText(AdfUtil.fromText(entrada));

        // Then
        assertEquals("Migrar el tablero", obtenida);
    }

    /*
     * CP-U04-02 - Descripción con texto en negrita
     * Esperada: el contenido y el formato en negrita se mantienen.
     * Obtenida: solo queda el texto; la marca strong se pierde.
     */
    @Test
    void negritaConservaElTextoYLaMarca() {
        // Given
        Map<String, Object> documento = doc(List.of(
                nodo("paragraph", List.of(texto("importante", List.of(Map.of("type", "strong")))))));

        // When
        String obtenida = AdfUtil.toText(documento);

        // Then
        assertTrue(obtenida.contains("importante"));
        assertTrue(obtenida.contains("**") || obtenida.toLowerCase().contains("strong"));
    }

    /*
     * CP-U04-03 - Descripción con lista de elementos
     * Esperada: se conserva el contenido y la estructura de lista.
     * Obtenida: se conserva el texto y el orden; no hay viñetas.
     */
    @Test
    void listaConservaElTextoYLaEstructura() {
        // Given
        Map<String, Object> documento = doc(List.of(nodo("bulletList", List.of(
                nodo("listItem", List.of(nodo("paragraph", List.of(texto("uno", null))))),
                nodo("listItem", List.of(nodo("paragraph", List.of(texto("dos", null)))))))));

        // When
        String obtenida = AdfUtil.toText(documento);

        // Then
        assertTrue(obtenida.contains("uno") && obtenida.contains("dos"));
        assertTrue(obtenida.contains("- ") || obtenida.contains("* ") || obtenida.contains("1."));
    }

    /*
     * CP-U04-04 - Descripción con hipervínculo
     * Esperada: se mantienen el texto visible y la URL.
     * Obtenida: solo se mantiene el texto visible.
     */
    @Test
    void enlaceConservaElTextoYLaUrl() {
        // Given
        Map<String, Object> enlace = texto("sitio", List.of(
                Map.of("type", "link", "attrs", Map.of("href", "https://ejemplo.test/a"))));
        Map<String, Object> documento = doc(List.of(nodo("paragraph", List.of(enlace))));

        // When
        String obtenida = AdfUtil.toText(documento);

        // Then
        assertTrue(obtenida.contains("sitio"));
        assertTrue(obtenida.contains("https://ejemplo.test/a"));
    }

    /*
     * CP-U04-05 - Descripción con tildes y caracteres especiales
     * Esperada: los caracteres originales permanecen intactos.
     * Obtenida: los caracteres originales permanecen intactos.
     */
    @Test
    void unicodeConservaLosCaracteres() {
        // Given
        String entrada = "Pingüino — € / año";

        // When
        String obtenida = AdfUtil.toText(AdfUtil.fromText(entrada));

        // Then
        assertEquals(entrada, obtenida);
    }

    /*
     * CP-U04-06 - Documento ADF vacío válido
     * Esperada: se obtiene una representación vacía válida, sin excepción.
     * Obtenida: se obtiene texto vacío, sin excepción.
     */
    @Test
    void adfVacioDevuelveTextoVacio() {
        // Given
        Map<String, Object> documento = doc(List.of());

        // When
        String obtenida = AdfUtil.toText(documento);

        // Then
        assertEquals("", obtenida);
    }

    /*
     * CP-U04-08 - Texto de entrada en blanco
     * Esperada: representación vacía válida, sin excepción.
     * Obtenida: fromText devuelve null.
     */
    @Test
    void fromTextTextoEnBlancoDevuelveRepresentacionVacia() {
        // Given
        String entrada = "   ";

        // When
        Map<String, Object> obtenida = AdfUtil.fromText(entrada);

        // Then
        assertNotNull(obtenida);
        assertEquals("doc", obtenida.get("type"));
    }

    /*
     * CP-U04-09 - El texto de entrada es null al armar el ADF
     * Esperada: no hay documento.
     * Obtenida: fromText devuelve null.
     */
    @Test
    void fromTextNullNoArmaDocumento() {
        // Given
        String entrada = null;

        // When
        Map<String, Object> obtenida = AdfUtil.fromText(entrada);

        // Then
        assertNull(obtenida);
    }

    /*
     * CP-U04-07 - Documento ADF con estructura inválida
     * Esperada: se identifica el error y no hay una transformación silenciosa.
     * Obtenida: se obtiene texto vacío, sin excepción.
     */
    @Test
    void adfInvalidoIdentificaElError() {
        // Given
        Object entrada = "esto no es un documento";

        // When
        IllegalArgumentException obtenida = assertThrows(IllegalArgumentException.class,
                () -> AdfUtil.toText(entrada));

        // Then
        assertNotNull(obtenida);
    }

    /*
     * CP-U04-10 - Lista anidada dentro de una tabla
     * Esperada: se conserva el contenido y la estructura anidada.
     * Obtenida: se conserva el texto de los ítems, en orden.
     */
    @Test
    void listaDentroDeTablaConservaTextoYEstructura() {
        // Given
        Map<String, Object> lista = nodo("bulletList", List.of(
                nodo("listItem", List.of(nodo("paragraph", List.of(texto("nido-a", null))))),
                nodo("listItem", List.of(nodo("paragraph", List.of(texto("nido-b", null)))))));
        Map<String, Object> tabla = nodo("table", List.of(
                nodo("tableRow", List.of(nodo("tableCell", List.of(lista))))));
        Map<String, Object> documento = doc(List.of(
                nodo("paragraph", List.of(texto("antes", null))),
                tabla,
                nodo("paragraph", List.of(texto("despues", null)))));

        // When
        String obtenida = AdfUtil.toText(documento);

        // Then
        int antes = obtenida.indexOf("antes");
        int a = obtenida.indexOf("nido-a");
        int b = obtenida.indexOf("nido-b");
        int despues = obtenida.indexOf("despues");
        assertTrue(antes >= 0 && antes < a && a < b && b < despues);
        assertTrue(obtenida.contains("- nido-a") || obtenida.contains("* nido-a"));
    }

    /*
     * CP-U04-11 - Encabezado, código, cita y salto de línea
     * Esperada: el texto de esos nodos se conserva.
     * Obtenida: el texto de esos nodos se conserva.
     */
    @Test
    void toTextEncabezadoCodigoCitaYSaltoConservaElTexto() {
        // Given
        Map<String, Object> documento = doc(List.of(
                nodo("heading", List.of(texto("titulo", null))),
                nodo("codeBlock", List.of(texto("codigo", null))),
                nodo("blockquote", List.of(nodo("paragraph", List.of(texto("cita", null))))),
                nodo("paragraph", List.of(texto("linea", null), nodo("hardBreak", null), texto("sigue", null))),
                nodo("rule", null)));

        // When
        String obtenida = AdfUtil.toText(documento);

        // Then
        assertTrue(obtenida.contains("titulo"));
        assertTrue(obtenida.contains("codigo"));
        assertTrue(obtenida.contains("cita"));
        assertTrue(obtenida.contains("linea"));
        assertTrue(obtenida.contains("sigue"));
    }

    /*
     * CP-U04-12 - Texto con saltos de línea de Windows
     * Esperada: un párrafo por línea.
     * Obtenida: un párrafo por línea.
     */
    @Test
    void fromTextSaltosWindowsUnParrafoPorLinea() {
        // Given
        String entrada = "a\r\nb\n";

        // When
        Map<String, Object> obtenida = AdfUtil.fromText(entrada);

        // Then
        @SuppressWarnings("unchecked")
        List<Object> parrafos = (List<Object>) obtenida.get("content");
        assertEquals("doc", obtenida.get("type"));
        assertEquals(3, parrafos.size());
    }

    private static Map<String, Object> doc(List<Object> content) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("type", "doc");
        doc.put("version", 1);
        doc.put("content", content);
        return doc;
    }

    private static Map<String, Object> nodo(String type, List<Object> content) {
        Map<String, Object> nodo = new LinkedHashMap<>();
        nodo.put("type", type);
        if (content != null) {
            nodo.put("content", new ArrayList<>(content));
        }
        return nodo;
    }

    private static Map<String, Object> texto(String value, List<Map<String, Object>> marks) {
        Map<String, Object> nodo = new LinkedHashMap<>();
        nodo.put("type", "text");
        nodo.put("text", value);
        if (marks != null) {
            nodo.put("marks", marks);
        }
        return nodo;
    }
}
