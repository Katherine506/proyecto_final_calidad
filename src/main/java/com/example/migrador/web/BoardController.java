package com.example.migrador.web;

import com.example.migrador.connector.ConnectorRegistry;
import com.example.migrador.domain.BoardOption;
import com.example.migrador.domain.ServerConfig;
import com.example.migrador.repository.ServerConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Endpoint AJAX usado por la pantalla de migración para listar los boards de una configuración. */
@RestController
public class BoardController {

    private static final Logger log = LoggerFactory.getLogger(BoardController.class);

    private final ServerConfigRepository configs;
    private final ConnectorRegistry registry;

    public BoardController(ServerConfigRepository configs, ConnectorRegistry registry) {
        this.configs = configs;
        this.registry = registry;
    }

    @GetMapping("/api/configs/{id}/boards")
    public ResponseEntity<?> boards(@PathVariable Long id) {
        try {
            ServerConfig config = configs.findById(id).orElseThrow();
            List<BoardOption> boards = registry.get(config.getType()).connect(config).listBoards();
            return ResponseEntity.ok(boards);
        } catch (Exception e) {
            log.error("No se pudieron cargar los tableros.");
            return ResponseEntity.status(500).body(Map.of("error", "No se pudieron cargar los tableros."));
        }
    }
}
