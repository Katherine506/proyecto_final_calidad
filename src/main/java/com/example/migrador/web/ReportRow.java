package com.example.migrador.web;

/**
 * Fila del reporte de migraciones: la correlación entre un item de origen y el item
 * equivalente creado/actualizado en el destino, con el link para abrir cada uno.
 * Las URL quedan en null cuando no se pudieron construir (configuración borrada u otro error).
 */
public record ReportRow(Long mappingId,
                        String sourceConfigName, String sourceType, String sourceItemId, String sourceUrl,
                        String targetConfigName, String targetType, String targetItemId, String targetUrl) {

    public boolean bothLinksAvailable() {
        return sourceUrl != null && targetUrl != null;
    }
}
