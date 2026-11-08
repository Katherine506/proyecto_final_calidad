package com.example.migrador.connector.jira;

import com.example.migrador.connector.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Conversión entre texto plano y Atlassian Document Format (ADF) usado por Jira API v3. */
final class AdfUtil {

    private AdfUtil() {
    }

    /** Construye un documento ADF simple: un párrafo por línea. Devuelve null si el texto está vacío. */
    static Map<String, Object> fromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        List<Object> paragraphs = new ArrayList<>();
        for (String line : text.replace("\r\n", "\n").split("\n", -1)) {
            Map<String, Object> paragraph = new LinkedHashMap<>();
            paragraph.put("type", "paragraph");
            if (!line.isEmpty()) {
                paragraph.put("content", List.of(Map.of("type", "text", "text", line)));
            }
            paragraphs.add(paragraph);
        }
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("type", "doc");
        doc.put("version", 1);
        doc.put("content", paragraphs);
        return doc;
    }

    /** Extrae texto plano de un documento ADF. */
    static String toText(Object adf) {
        if (adf == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        walk(adf, sb);
        return sb.toString().strip();
    }

    private static void walk(Object node, StringBuilder sb) {
        Map<String, Object> map = Json.map(node);
        String type = Json.str(map.get("type"));
        if ("text".equals(type)) {
            sb.append(Json.str(map.get("text")));
            return;
        }
        if ("hardBreak".equals(type)) {
            sb.append('\n');
            return;
        }
        for (Object child : Json.list(map.get("content"))) {
            walk(child, sb);
        }
        if (type != null && !"doc".equals(type) && isBlock(type)) {
            sb.append('\n');
        }
    }

    private static boolean isBlock(String type) {
        return switch (type) {
            case "paragraph", "heading", "codeBlock", "blockquote", "listItem", "rule" -> true;
            default -> false;
        };
    }
}
