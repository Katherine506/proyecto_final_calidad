package com.example.migrador.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Equivalencia entre un item del origen y el item creado en el destino.
 * Permite decidir entre crear o actualizar en migraciones posteriores.
 */
@Entity
@Table(name = "item_mapping", uniqueConstraints = @UniqueConstraint(
        columnNames = {"source_config_id", "target_config_id", "source_item_id"}))
public class ItemMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_config_id", nullable = false)
    private Long sourceConfigId;

    @Column(name = "target_config_id", nullable = false)
    private Long targetConfigId;

    @Column(name = "source_item_id", nullable = false)
    private String sourceItemId;

    @Column(name = "target_item_id", nullable = false)
    private String targetItemId;

    protected ItemMapping() {
    }

    public ItemMapping(Long sourceConfigId, Long targetConfigId, String sourceItemId, String targetItemId) {
        this.sourceConfigId = sourceConfigId;
        this.targetConfigId = targetConfigId;
        this.sourceItemId = sourceItemId;
        this.targetItemId = targetItemId;
    }

    public Long getId() { return id; }
    public Long getSourceConfigId() { return sourceConfigId; }
    public Long getTargetConfigId() { return targetConfigId; }
    public String getSourceItemId() { return sourceItemId; }
    public String getTargetItemId() { return targetItemId; }
    public void setTargetItemId(String targetItemId) { this.targetItemId = targetItemId; }
}
