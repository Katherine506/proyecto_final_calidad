package com.example.migrador.domain;

import java.util.List;

/**
 * Formato estándar intermedio. Todos los conectores extraen a este formato
 * y crean/actualizan a partir de él.
 *
 * @param externalId  id del item en el servidor de origen
 * @param title       nombre del ticket
 * @param description descripción en texto plano
 * @param labels      etiquetas, sin la etiqueta que representa el tipo (story/bug)
 * @param kind        tipo del item
 */
public record StandardItem(String externalId, String title, String description,
                           List<String> labels, ItemKind kind) {
}
