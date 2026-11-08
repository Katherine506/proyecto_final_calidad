package com.example.migrador.connector;

import com.example.migrador.domain.ServerConfig;

/**
 * Punto de extensión: para soportar un nuevo tipo de servidor basta con crear
 * un bean que implemente esta interfaz (se registra solo en {@link ConnectorRegistry}).
 */
public interface ServerConnector {

    /** Código del tipo de servidor, se guarda en BD (ej. "CLICKUP"). */
    String type();

    /** Nombre para mostrar en pantalla. */
    String displayName();

    /** Etiqueta del campo "username" para este tipo de servidor. */
    String usernameLabel();

    /** Si este tipo de servidor realmente necesita el campo username. */
    default boolean usernameRequired() {
        return true;
    }

    /** Etiqueta del campo "token". */
    String tokenLabel();

    /** URL por defecto sugerida (puede ser vacía). */
    String defaultUrl();

    /** Abre una sesión (carga del conector) contra el servidor configurado. */
    ConnectorSession connect(ServerConfig config);

    /** URL para abrir directamente en el navegador el item con ese id en este servidor. */
    String itemUrl(ServerConfig config, String externalId);
}
