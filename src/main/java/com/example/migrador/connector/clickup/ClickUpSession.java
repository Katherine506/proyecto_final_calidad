package com.example.migrador.connector.clickup;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sesión contra la API REST de ClickUp (v2). Autenticación con el token directo en el
 * header Authorization (sin prefijo "Bearer"). Cada "board" es una lista de ClickUp
 * (las tareas viven dentro de listas, dentro de carpetas o directamente de un espacio).
 */
class ClickUpSession implements ConnectorSession {

    private static final String BUG_TAG = "bug";

    private final RestClient client;

    ClickUpSession(ServerConfig config) {
        String base = config.getUrl() == null || config.getUrl().isBlank()
                ? "https://api.clickup.com/api/v2" : config.getUrl().trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.client = RestClient.builder()
                .baseUrl(base)
                .defaultHeaders(h -> {
                    h.set("Authorization", config.getApiToken());
                    h.setContentType(MediaType.APPLICATION_JSON);
                    h.setAccept(List.of(MediaType.APPLICATION_JSON));
                })
                .build();
    }

    @Override
    public List<BoardOption> listBoards() {
        List<BoardOption> boards = new ArrayList<>();
        Map<String, Object> teamsResponse = client.get().uri("/team").retrieve().body(Json.MAP);
        for (Object teamObj : Json.list(teamsResponse == null ? null : teamsResponse.get("teams"))) {
            String teamId = Json.str(Json.map(teamObj).get("id"));
            Map<String, Object> spacesResponse = client.get()
                    .uri(b -> b.path("/team/{id}/space").queryParam("archived", "false").build(teamId))
                    .retrieve().body(Json.MAP);
            for (Object spaceObj : Json.list(spacesResponse == null ? null : spacesResponse.get("spaces"))) {
                Map<String, Object> space = Json.map(spaceObj);
                String spaceId = Json.str(space.get("id"));
                String spaceName = Json.str(space.get("name"));

                Map<String, Object> foldersResponse = client.get()
                        .uri(b -> b.path("/space/{id}/folder").queryParam("archived", "false").build(spaceId))
                        .retrieve().body(Json.MAP);
                for (Object folderObj : Json.list(foldersResponse == null ? null : foldersResponse.get("folders"))) {
                    Map<String, Object> folder = Json.map(folderObj);
                    String folderName = Json.str(folder.get("name"));
                    for (Object listObj : Json.list(folder.get("lists"))) {
                        Map<String, Object> list = Json.map(listObj);
                        boards.add(new BoardOption(Json.str(list.get("id")),
                                spaceName + " / " + folderName + " / " + Json.str(list.get("name"))));
                    }
                }

                Map<String, Object> listsResponse = client.get()
                        .uri(b -> b.path("/space/{id}/list").queryParam("archived", "false").build(spaceId))
                        .retrieve().body(Json.MAP);
                for (Object listObj : Json.list(listsResponse == null ? null : listsResponse.get("lists"))) {
                    Map<String, Object> list = Json.map(listObj);
                    boards.add(new BoardOption(Json.str(list.get("id")),
                            spaceName + " / " + Json.str(list.get("name"))));
                }
            }
        }
        return boards;
    }

    @Override
    public List<StandardItem> extractAll(List<String> boardIds) {
        List<StandardItem> items = new ArrayList<>();
        if (boardIds == null) {
            return items;
        }
        for (String listId : boardIds) {
            int page = 0;
            while (true) {
                int currentPage = page;
                Map<String, Object> response = client.get()
                        .uri(b -> b.path("/list/{id}/task")
                                .queryParam("page", currentPage)
                                .queryParam("include_closed", "true")
                                .build(listId))
                        .retrieve()
                        .body(Json.MAP);
                List<Object> tasks = Json.list(response == null ? null : response.get("tasks"));
                if (tasks.isEmpty()) {
                    break;
                }
                for (Object t : tasks) {
                    items.add(toStandard(Json.map(t)));
                }
                page++;
            }
        }
        return items;
    }

    @Override
    public String create(StandardItem item, String targetBoardId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", item.title());
        body.put("description", item.description() == null ? "" : item.description());
        body.put("tags", tagNames(item));

        Map<String, Object> created = client.post()
                .uri("/list/{id}/task", targetBoardId)
                .body(body)
                .retrieve()
                .body(Json.MAP);
        return created == null ? null : Json.str(created.get("id"));
    }

    @Override
    public void update(String externalId, StandardItem item, String targetBoardId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", item.title());
        body.put("description", item.description() == null ? "" : item.description());

        client.put()
                .uri("/task/{id}", externalId)
                .body(body)
                .retrieve()
                .toBodilessEntity();

        // ClickUp maneja las etiquetas con un endpoint aparte; se agregan las que falten.
        for (String tag : tagNames(item)) {
            client.post()
                    .uri("/task/{id}/tag/{tag}", externalId, tag)
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    /** Etiquetas de la tarea destino: las del item más "bug" cuando es un bug. Story = tarea normal. */
    private List<String> tagNames(StandardItem item) {
        Set<String> names = new LinkedHashSet<>();
        if (item.kind() == ItemKind.BUG) {
            names.add(BUG_TAG);
        }
        if (item.labels() != null) {
            names.addAll(item.labels());
        }
        return new ArrayList<>(names);
    }

    /** Label "bug" => BUG; label "story" o ninguna => STORY. Esas dos labels no se copian como etiqueta normal. */
    private StandardItem toStandard(Map<String, Object> task) {
        boolean bug = false;
        List<String> labels = new ArrayList<>();
        for (Object o : Json.list(task.get("tags"))) {
            String name = Json.str(Json.map(o).get("name"));
            if (name == null || name.isBlank()) {
                continue;
            }
            String trimmed = name.trim();
            if (trimmed.equalsIgnoreCase("bug")) {
                bug = true;
            } else if (!trimmed.equalsIgnoreCase("story")) {
                labels.add(trimmed);
            }
        }
        String desc = Json.str(task.get("text_content"));
        if (desc == null || desc.isBlank()) {
            desc = Json.str(task.get("description"));
        }
        return new StandardItem(
                Json.str(task.get("id")),
                Json.str(task.get("name")),
                desc == null ? "" : desc,
                labels,
                bug ? ItemKind.BUG : ItemKind.STORY);
    }
}
