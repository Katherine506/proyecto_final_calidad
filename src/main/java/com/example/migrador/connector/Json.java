package com.example.migrador.connector;

import org.springframework.core.ParameterizedTypeReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Utilidades para navegar respuestas JSON deserializadas como Map/List. */
public final class Json {

    public static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };
    public static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {
    };

    private Json() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Collections.emptyMap();
    }

    public static List<Object> list(Object o) {
        return o instanceof List<?> l ? new ArrayList<>(l) : Collections.emptyList();
    }

    public static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
