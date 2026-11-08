package com.example.migrador.repository;

import com.example.migrador.domain.ServerConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServerConfigRepository extends JpaRepository<ServerConfig, Long> {

    List<ServerConfig> findAllByOrderByNameAsc();
}
