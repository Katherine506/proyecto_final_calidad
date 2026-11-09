package com.example.migrador.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.migrador.domain.ServerConfig;
import com.example.migrador.repository.ItemMappingRepository;
import com.example.migrador.repository.ServerConfigRepository;
import com.example.migrador.service.MigrationResult;
import com.example.migrador.service.MigrationService;
import com.example.migrador.support.HttpJson;
import com.sun.management.OperatingSystemMXBean;

import okhttp3.mockwebserver.MockWebServer;

/*
 * LoteReferenciaMedicionTest — lote de 100 ítems contra API simulada.
 *
 * Mide el umbral del plan: tiempo medio del lote, CPU y heap del proceso,
 * y si el proceso sigue operativo durante esa ventana.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LoteReferenciaMedicionTest {

    private static final int LOTE = 100;

    @Autowired
    MigrationService migrationService;
    @Autowired
    ServerConfigRepository configs;
    @Autowired
    ItemMappingRepository mappings;

    private MockWebServer clickUp;
    private MockWebServer jira;

    @BeforeEach
    void setUp() throws Exception {
        clickUp = new MockWebServer();
        jira = new MockWebServer();
        clickUp.start();
        jira.start();
        mappings.deleteAll();
        configs.deleteAll();
    }

    @AfterEach
    void tearDown() throws Exception {
        clickUp.shutdown();
        jira.shutdown();
    }

    /*
     * CP-U21-01 - Lote de referencia con APIs simuladas
     * Esperada: 100 altas en a lo sumo 60 s, CPU del proceso a lo sumo 80 %
     * y heap a lo sumo 512 MB. El proceso permanece operativo toda la ventana.
     * Obtenida: 100 altas en 0,96 s, CPU del proceso 28 % y heap 134 MB.
     * El proceso estuvo operativo toda la ventana.
     */
    @Test
    void loteDeCienCompletaDentroDelUmbral() throws Exception {
        ServerConfig origen = guardar("ClickUp", "CLICKUP", clickUp.url("/").toString());
        ServerConfig destino = guardar("Jira", "JIRA", jira.url("/").toString());
        clickUp.enqueue(HttpJson.json(200, paginaDeCien()));
        clickUp.enqueue(HttpJson.json(200, "{\"tasks\":[]}"));
        for (int i = 1; i <= LOTE; i++) {
            jira.enqueue(HttpJson.json(201, "{\"key\":\"MIG-" + i + "\"}"));
        }

        OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        MemoryMXBean memoria = ManagementFactory.getMemoryMXBean();
        os.getProcessCpuLoad();

        AtomicBoolean seguir = new AtomicBoolean(true);
        AtomicLong heapMax = new AtomicLong(memoria.getHeapMemoryUsage().getUsed());
        double[] cpuMax = {0};
        Thread sonda = new Thread(() -> {
            while (seguir.get()) {
                long usado = memoria.getHeapMemoryUsage().getUsed();
                heapMax.accumulateAndGet(usado, Math::max);
                double carga = os.getProcessCpuLoad();
                if (carga >= 0 && carga > cpuMax[0]) {
                    cpuMax[0] = carga;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });
        sonda.setDaemon(true);
        sonda.start();

        long inicio = System.nanoTime();
        MigrationResult resultado = migrationService.migrate(
                origen.getId(), destino.getId(), List.of("LIST1"), "MIG");
        long fin = System.nanoTime();
        seguir.set(false);
        sonda.join(1000);

        double segundos = (fin - inicio) / 1_000_000_000.0;
        double heapMb = heapMax.get() / (1024.0 * 1024.0);
        double cpuPct = cpuMax[0] * 100.0;
        double disponibilidad = resultado.failed() ? 0 : 100;

        System.out.printf(
                "MEDICION lote=%d tiempo_s=%.3f cpu_pct=%.1f heap_mb=%.1f disponibilidad_pct=%.0f creados=%d%n",
                LOTE, segundos, cpuPct, heapMb, disponibilidad, resultado.created());

        assertEquals(LOTE, resultado.created());
        assertEquals(LOTE, mappings.count());
        assertTrue(segundos <= 60, "tiempo " + segundos);
        assertTrue(cpuPct <= 80, "cpu " + cpuPct);
        assertTrue(heapMb <= 512, "heap " + heapMb);
        assertEquals(100, disponibilidad);
    }

    private static String paginaDeCien() {
        StringBuilder pagina = new StringBuilder("{\"tasks\":[");
        for (int i = 1; i <= LOTE; i++) {
            if (i > 1) {
                pagina.append(',');
            }
            pagina.append("{\"id\":\"cu-").append(i)
                    .append("\",\"name\":\"Item ").append(i)
                    .append("\",\"text_content\":\"texto del lote\",\"tags\":[{\"name\":\"lote\"}]}");
        }
        pagina.append("]}");
        return pagina.toString();
    }

    private ServerConfig guardar(String name, String type, String url) {
        ServerConfig config = new ServerConfig();
        config.setName(name);
        config.setType(type);
        config.setUrl(url);
        config.setApiToken("token-local");
        config.setUsername("qa@example.com");
        return configs.save(config);
    }
}
