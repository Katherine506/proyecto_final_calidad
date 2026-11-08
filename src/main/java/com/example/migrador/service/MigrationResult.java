package com.example.migrador.service;

/**
 * Resultado de una migración.
 *
 * @param failed true si falló toda la operación (conexión/extracción)
 */
public record MigrationResult(boolean failed, int created, int updated, int itemErrors) {

    public static MigrationResult failure() {
        return new MigrationResult(true, 0, 0, 0);
    }

    public String message() {
        if (failed) {
            return "No se pudo completar la migracion.";
        }
        String msg = "Migracion terminada: " + created + " creados, " + updated + " actualizados";
        if (itemErrors > 0) {
            msg += ". Items con error: " + itemErrors + ". Esos items no se crearon ni se actualizaron.";
        }
        return msg;
    }
}
