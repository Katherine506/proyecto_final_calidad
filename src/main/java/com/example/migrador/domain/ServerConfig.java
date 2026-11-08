package com.example.migrador.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Configuración de un servidor (origen o destino). La misma tabla sirve para ambos roles.
 * No guarda un board/proyecto fijo: los boards a sincronizar se eligen en la pantalla de
 * migración, consultándolos en vivo al servidor con {@link com.example.migrador.connector.ConnectorSession#listBoards()}.
 */
@Entity
@Table(name = "server_config")
public class ServerConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nombre descriptivo para mostrar en el combo. */
    @Column(nullable = false)
    private String name;

    /** Tipo de servidor: determina el conector (CLICKUP, JIRA, ...). */
    @Column(nullable = false)
    private String type;

    @Column(nullable = false, length = 500)
    private String url;

    /** Jira: email del usuario. ClickUp: no se usa (puede quedar vacío). */
    @Column
    private String username;

    @Column(name = "api_token", nullable = false, length = 1000)
    private String apiToken;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getApiToken() { return apiToken; }
    public void setApiToken(String apiToken) { this.apiToken = apiToken; }
}
