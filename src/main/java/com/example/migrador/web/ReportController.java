package com.example.migrador.web;

import com.example.migrador.connector.ConnectorRegistry;
import com.example.migrador.domain.ItemMapping;
import com.example.migrador.domain.ServerConfig;
import com.example.migrador.repository.ItemMappingRepository;
import com.example.migrador.repository.ServerConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reporte de migraciones: muestra la tabla de equivalencias guardada (origen <-> destino)
 * para auditar qué se migró, y arma el link directo a cada item en su servidor.
 */
@Controller
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    private final ItemMappingRepository mappings;
    private final ServerConfigRepository configs;
    private final ConnectorRegistry registry;

    public ReportController(ItemMappingRepository mappings, ServerConfigRepository configs,
                            ConnectorRegistry registry) {
        this.mappings = mappings;
        this.configs = configs;
        this.registry = registry;
    }

    @GetMapping("/reports")
    public String report(Model model) {
        Map<Long, ServerConfig> configById = configs.findAll().stream()
                .collect(Collectors.toMap(ServerConfig::getId, Function.identity()));

        List<ReportRow> rows = new ArrayList<>();
        for (ItemMapping m : mappings.findAllByOrderByIdDesc()) {
            try {
                rows.add(toRow(m, configById));
            } catch (Exception e) {
                log.error("hubo un error");
            }
        }
        model.addAttribute("rows", rows);
        return "reports";
    }

    private ReportRow toRow(ItemMapping m, Map<Long, ServerConfig> configById) {
        ServerConfig source = configById.get(m.getSourceConfigId());
        ServerConfig target = configById.get(m.getTargetConfigId());
        return new ReportRow(
                m.getId(),
                source == null ? "(servidor eliminado)" : source.getName(),
                source == null ? "?" : source.getType(),
                m.getSourceItemId(),
                itemUrl(source, m.getSourceItemId()),
                target == null ? "(servidor eliminado)" : target.getName(),
                target == null ? "?" : target.getType(),
                m.getTargetItemId(),
                itemUrl(target, m.getTargetItemId()));
    }

    private String itemUrl(ServerConfig config, String itemId) {
        if (config == null || itemId == null) {
            return null;
        }
        try {
            return registry.get(config.getType()).itemUrl(config, itemId);
        } catch (Exception e) {
            log.error("hubo un error");
            return null;
        }
    }
}
