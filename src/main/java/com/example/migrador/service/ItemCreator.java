package com.example.migrador.service;

import com.example.migrador.connector.ConnectorSession;
import com.example.migrador.domain.ItemMapping;
import com.example.migrador.domain.ServerConfig;
import com.example.migrador.domain.StandardItem;
import com.example.migrador.repository.ItemMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Creador de items: recibe los items en formato estándar y, usando el conector del destino,
 * los crea o los actualiza según la tabla de equivalencias.
 */
@Service
public class ItemCreator {

    private static final Logger log = LoggerFactory.getLogger(ItemCreator.class);

    private final ItemMappingRepository mappings;

    public ItemCreator(ItemMappingRepository mappings) {
        this.mappings = mappings;
    }

    public MigrationResult sync(ServerConfig source, ServerConfig target, List<StandardItem> items,
                                ConnectorSession targetSession, String targetBoardId) {
        int created = 0;
        int updated = 0;
        int errors = 0;
        for (StandardItem item : items) {
            try {
                if (updateIfRelated(source, target, item, targetSession, targetBoardId)) {
                    updated++;
                } else {
                    String targetId = targetSession.create(item, targetBoardId);
                    mappings.save(new ItemMapping(source.getId(), target.getId(), item.externalId(), targetId));
                    created++;
                }
            } catch (Exception e) {
                log.error("No se pudo crear o actualizar un item.");
                errors++;
            }
        }
        return new MigrationResult(false, created, updated, errors);
    }

    /**
     * Si ya existe una relación guardada para este item, actualiza el item del destino y
     * devuelve true. Revisa tanto la relación directa (source -> target, de una migración
     * anterior en este mismo sentido) como la inversa (target -> source, por si este item
     * se migró antes en sentido contrario), para no crear un duplicado en ningún caso.
     */
    private boolean updateIfRelated(ServerConfig source, ServerConfig target, StandardItem item,
                                    ConnectorSession targetSession, String targetBoardId) {
        Optional<ItemMapping> direct = mappings.findBySourceConfigIdAndTargetConfigIdAndSourceItemId(
                source.getId(), target.getId(), item.externalId());
        if (direct.isPresent()) {
            targetSession.update(direct.get().getTargetItemId(), item, targetBoardId);
            return true;
        }

        Optional<ItemMapping> reverse = mappings.findBySourceConfigIdAndTargetConfigIdAndTargetItemId(
                target.getId(), source.getId(), item.externalId());
        if (reverse.isPresent()) {
            String targetItemId = reverse.get().getSourceItemId();
            targetSession.update(targetItemId, item, targetBoardId);
            // Se guarda también la relación directa, para que la próxima sincronización
            // en este sentido la encuentre de una vez sin tener que revisar la inversa.
            mappings.save(new ItemMapping(source.getId(), target.getId(), item.externalId(), targetItemId));
            return true;
        }

        return false;
    }
}
