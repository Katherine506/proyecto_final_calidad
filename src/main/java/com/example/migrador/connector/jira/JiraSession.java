package com.example.migrador.connector.jira;

import com.example.migrador.connector.ConnectorSession;
import com.example.migrador.connector.Json;
import com.example.migrador.domain.BoardOption;
import com.example.migrador.domain.ItemKind;
import com.example.migrador.domain.ServerConfig;
import com.example.migrador.domain.StandardItem;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sesión contra Jira Cloud REST API v3. Autenticación Basic (email + API token).
 * Cada "board" devuelto por {@link #listBoards()} es un proyecto de Jira (su "key").
 */
class JiraSession implements ConnectorSession {

    private static final int PAGE_SIZE = 100;

    private final RestClient client;

    JiraSession(ServerConfig config) {
        String base = config.getUrl().trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.client = RestClient.builder()
                .baseUrl(base)
                .defaultHeaders(h -> {
                    h.setBasicAuth(config.getUsername(), config.getApiToken());
                    h.setContentType(MediaType.APPLICATION_JSON);
                    h.setAccept(List.of(MediaType.APPLICATION_JSON));
                })
                .build();
    }

    @Override
    public List<BoardOption> listBoards() {
        List<BoardOption> boards = new ArrayList<>();
        int startAt = 0;
        while (true) {
            int currentStartAt = startAt;
            Map<String, Object> response = client.get()
                    .uri(b -> b.path("/rest/api/3/project/search")
                            .queryParam("maxResults", PAGE_SIZE)
                            .queryParam("startAt", currentStartAt)
                            .build())
                    .retrieve()
                    .body(Json.MAP);
            if (response == null) {
                break;
            }
            for (Object o : Json.list(response.get("values"))) {
                Map<String, Object> project = Json.map(o);
                String key = Json.str(project.get("key"));
                String name = Json.str(project.get("name"));
                boards.add(new BoardOption(key, name + " (" + key + ")"));
            }
            boolean last = Boolean.TRUE.equals(response.get("isLast"));
            if (last) {
                break;
            }
            startAt += PAGE_SIZE;
        }
        return boards;
    }

    @Override
    public List<StandardItem> extractAll(List<String> boardIds) {
        if (boardIds == null || boardIds.isEmpty()) {
            return List.of();
        }
        List<StandardItem> items = new ArrayList<>();
        String keys = boardIds.stream()
                .map(k -> "\"" + k.replace("\"", "") + "\"")
                .collect(Collectors.joining(","));
        String jql = "project in (" + keys + ") ORDER BY created ASC";
        String nextToken = null;
        do {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("jql", jql);
            body.put("fields", List.of("summary", "description", "labels", "issuetype"));
            body.put("maxResults", PAGE_SIZE);
            if (nextToken != null) {
                body.put("nextPageToken", nextToken);
            }
            Map<String, Object> response = client.post()
                    .uri("/rest/api/3/search/jql")
                    .body(body)
                    .retrieve()
                    .body(Json.MAP);
            if (response == null) {
                break;
            }
            for (Object o : Json.list(response.get("issues"))) {
                items.add(toStandard(Json.map(o)));
            }
            nextToken = Json.str(response.get("nextPageToken"));
        } while (nextToken != null && !nextToken.isBlank());
        return items;
    }

    @Override
    public String create(StandardItem item, String targetBoardId) {
        Map<String, Object> fields = fields(item);
        fields.put("project", Map.of("key", targetBoardId));
        fields.put("issuetype", Map.of("name", item.kind() == ItemKind.BUG ? "Bug" : "Story"));

        Map<String, Object> created = client.post()
                .uri("/rest/api/3/issue")
                .body(Map.of("fields", fields))
                .retrieve()
                .body(Json.MAP);
        return created == null ? null : Json.str(created.get("key"));
    }

    @Override
    public void update(String externalId, StandardItem item, String targetBoardId) {
        // No se cambia el tipo de issue en actualizaciones: Jira no lo permite en un PUT simple.
        client.put()
                .uri("/rest/api/3/issue/{key}", externalId)
                .body(Map.of("fields", fields(item)))
                .retrieve()
                .toBodilessEntity();
    }

    private Map<String, Object> fields(StandardItem item) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("summary", item.title());
        fields.put("description", AdfUtil.fromText(item.description()));
        List<String> labels = new ArrayList<>();
        if (item.labels() != null) {
            for (String l : item.labels()) {
                // Jira no admite espacios en las etiquetas.
                labels.add(l.trim().replaceAll("\\s+", "_"));
            }
        }
        fields.put("labels", labels);
        return fields;
    }

    private StandardItem toStandard(Map<String, Object> issue) {
        Map<String, Object> f = Json.map(issue.get("fields"));
        String issueType = Json.str(Json.map(f.get("issuetype")).get("name"));
        List<String> labels = new ArrayList<>();
        for (Object l : Json.list(f.get("labels"))) {
            labels.add(Json.str(l));
        }
        return new StandardItem(
                Json.str(issue.get("key")),
                Json.str(f.get("summary")),
                AdfUtil.toText(f.get("description")),
                labels,
                "bug".equalsIgnoreCase(issueType) ? ItemKind.BUG : ItemKind.STORY);
    }
}
