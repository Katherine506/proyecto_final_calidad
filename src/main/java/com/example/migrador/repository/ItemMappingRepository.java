package com.example.migrador.repository;

import com.example.migrador.domain.ItemMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemMappingRepository extends JpaRepository<ItemMapping, Long> {

    Optional<ItemMapping> findBySourceConfigIdAndTargetConfigIdAndSourceItemId(
            Long sourceConfigId, Long targetConfigId, String sourceItemId);

    /**
     * Busca la relación "al revés": un item que, en una migración anterior en sentido contrario
     * (target -> source), ya se creó a partir del item que ahora estamos por migrar.
     * Sirve para no duplicarlo cuando se sincroniza en ambos sentidos entre los mismos servidores.
     */
    Optional<ItemMapping> findBySourceConfigIdAndTargetConfigIdAndTargetItemId(
            Long sourceConfigId, Long targetConfigId, String targetItemId);

    void deleteBySourceConfigIdOrTargetConfigId(Long sourceConfigId, Long targetConfigId);

    List<ItemMapping> findAllByOrderByIdDesc();
}
