package com.example.migrador.support;

import okhttp3.mockwebserver.MockResponse;

/** Respuestas JSON de prueba para MockWebServer. */
public final class HttpJson {

    private HttpJson() {
    }

    public static MockResponse json(int status, String body) {
        return new MockResponse()
                .setResponseCode(status)
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
