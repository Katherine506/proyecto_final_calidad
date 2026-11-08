package com.example.migrador.connector;

import com.example.migrador.domain.BoardOption;
import com.example.migrador.domain.StandardItem;

import java.util.List;

/** Operaciones sobre un servidor concreto, ya autenticado. */
public interface ConnectorSession {

    /** Lista los boards disponibles (proyectos en Jira, listas en ClickUp) para elegir cuáles sincronizar. */
    List<BoardOption> listBoards();

    /** Extrae todos los items de los boards indicados y los convierte al formato estándar. */
    List<StandardItem> extractAll(List<String> boardIds);

    /** Crea el item en el board de destino indicado y devuelve su id externo. */
    String create(StandardItem item, String targetBoardId);

    /** Actualiza un item existente. */
    void update(String externalId, StandardItem item, String targetBoardId);
}
