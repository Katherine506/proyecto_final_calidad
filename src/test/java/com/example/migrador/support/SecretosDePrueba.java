package com.example.migrador.support;

import java.util.List;

/** Valores de prueba que no pueden aparecer en consola ni en bitácoras. */
public final class SecretosDePrueba {

    public static final String TOKEN = "pk_test_NO_LOG_55aa";
    public static final String PASSWORD = "clave-local-seg";

    public static final List<String> PROHIBIDOS = List.of(
            TOKEN,
            PASSWORD,
            "token-clickup-test",
            "token-jira-test",
            "token-local",
            "clave-local");

    private SecretosDePrueba() {
    }

    public static String primerSecreto(String texto) {
        for (String secreto : PROHIBIDOS) {
            if (texto.contains(secreto)) {
                return secreto;
            }
        }
        return null;
    }
}
