package com.example.migrador.domain;

/** Un board/proyecto seleccionable del servidor (para Jira: un proyecto; para ClickUp: una lista). */
public record BoardOption(String id, String name) {
}
