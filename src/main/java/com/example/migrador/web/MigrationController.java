package com.example.migrador.web;

import com.example.migrador.connector.ConnectorRegistry;
import com.example.migrador.repository.ServerConfigRepository;
import com.example.migrador.service.MigrationResult;
import com.example.migrador.service.MigrationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class MigrationController {

    private final ServerConfigRepository configs;
    private final ConnectorRegistry registry;
    private final MigrationService migrationService;

    public MigrationController(ServerConfigRepository configs, ConnectorRegistry registry,
                               MigrationService migrationService) {
        this.configs = configs;
        this.registry = registry;
        this.migrationService = migrationService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("types", registry.all());
        model.addAttribute("configs", configs.findAllByOrderByNameAsc());
        return "migrate";
    }

    @PostMapping("/migrate")
    public String migrate(@RequestParam Long sourceId, @RequestParam Long targetId,
                          @RequestParam List<String> sourceBoardIds, @RequestParam String targetBoardId,
                          RedirectAttributes redirect) {
        MigrationResult result = migrationService.migrate(sourceId, targetId, sourceBoardIds, targetBoardId);
        redirect.addFlashAttribute("message", result.message());
        redirect.addFlashAttribute("failed", result.failed() || result.itemErrors() > 0);
        return "redirect:/";
    }
}
