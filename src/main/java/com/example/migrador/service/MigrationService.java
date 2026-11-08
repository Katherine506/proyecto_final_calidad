package com.example.migrador.service;

import com.example.migrador.connector.ConnectorRegistry;
import com.example.migrador.connector.ConnectorSession;
import com.example.migrador.domain.ServerConfig;
import com.example.migrador.domain.StandardItem;
import com.example.migrador.repository.ServerConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orquesta la migración: carga el conector del origen, extrae todos los items de los boards
 * elegidos al formato estándar y los pasa al creador de items, que usa el conector del destino.
 * No se valida que origen y destino sean iguales.
 */
@Service
public class MigrationService {

    private static final Logger log = LoggerFactory.getLogger(MigrationService.class);

    private final ServerConfigRepository configs;
    private final ConnectorRegistry registry;
    private final ItemCreator itemCreator;

    public MigrationService(ServerConfigRepository configs, ConnectorRegistry registry, ItemCreator itemCreator) {
        this.configs = configs;
        this.registry = registry;
        this.itemCreator = itemCreator;
    }

    public MigrationResult migrate(Long sourceId, Long targetId, List<String> sourceBoardIds, String targetBoardId) {
        try {
            ServerConfig source = configs.findById(sourceId).orElseThrow();
            ServerConfig target = configs.findById(targetId).orElseThrow();

            ConnectorSession sourceSession = registry.get(source.getType()).connect(source);
            List<StandardItem> items = sourceSession.extractAll(sourceBoardIds);

            ConnectorSession targetSession = registry.get(target.getType()).connect(target);
            return itemCreator.sync(source, target, items, targetSession, targetBoardId);
        } catch (Exception e) {
            log.error("hubo un error");
            return MigrationResult.failure();
        }
    }
}
