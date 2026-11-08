package com.example.migrador.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Usuario de la aplicación. A propósito, con fines didácticos, la contraseña se guarda
 * SIN ENCRIPTAR ni hashear, para que se pueda auditar y discutir por qué esto es inseguro.
 * No usar este patrón en un proyecto real.
 */
@Entity
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    /** Contraseña en texto plano, sin ningún hash. Ver advertencia en la clase. */
    @Column(nullable = false)
    private String password;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
