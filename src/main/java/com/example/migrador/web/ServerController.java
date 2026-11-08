package com.example.migrador.web;

import com.example.migrador.connector.ConnectorRegistry;
import com.example.migrador.domain.ServerConfig;
import com.example.migrador.repository.ItemMappingRepository;
import com.example.migrador.repository.ServerConfigRepository;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class ServerController {

    private final ServerConfigRepository configs;
    private final ItemMappingRepository mappings;
    private final ConnectorRegistry registry;

    public ServerController(ServerConfigRepository configs, ItemMappingRepository mappings,
                            ConnectorRegistry registry) {
        this.configs = configs;
        this.mappings = mappings;
        this.registry = registry;
    }

    @GetMapping("/servers")
    public String list(Model model) {
        model.addAttribute("configs", configs.findAllByOrderByNameAsc());
        return "servers";
    }

    @GetMapping("/servers/new")
    public String create(Model model) {
        model.addAttribute("server", new ServerConfig());
        model.addAttribute("types", registry.all());
        return "server-form";
    }

    @GetMapping("/servers/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("server", configs.findById(id).orElseThrow());
        model.addAttribute("types", registry.all());
        return "server-form";
    }

    @PostMapping("/servers")
    public String save(@ModelAttribute("server") ServerConfig form) {
        if (form.getId() != null) {
            ServerConfig existing = configs.findById(form.getId()).orElseThrow();
            // Si el token llega vacío al editar, se conserva el guardado.
            if (form.getApiToken() == null || form.getApiToken().isBlank()) {
                form.setApiToken(existing.getApiToken());
            }
        }
        configs.save(form);
        return "redirect:/servers";
    }

    @PostMapping("/servers/{id}/delete")
    @Transactional
    public String delete(@PathVariable Long id) {
        mappings.deleteBySourceConfigIdOrTargetConfigId(id, id);
        configs.deleteById(id);
        return "redirect:/servers";
    }
}
