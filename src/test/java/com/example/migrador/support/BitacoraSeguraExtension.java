package com.example.migrador.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.read.ListAppender;

/**
 * Copia la consola y escribe {@code target/bitacora-pruebas.log} durante toda la suite.
 * Al cerrar el plan de JUnit falla si algún secreto de prueba quedó en esa salida.
 */
public class BitacoraSeguraExtension implements BeforeAllCallback {

    static final Path ARCHIVO = Path.of("target", "bitacora-pruebas.log");

    @Override
    public void beforeAll(ExtensionContext context) {
        context.getRoot().getStore(ExtensionContext.Namespace.GLOBAL)
                .getOrComputeIfAbsent(Captura.class);
    }

    static final class Captura implements AutoCloseable {
        private final PrintStream salidaOriginal;
        private final PrintStream errorOriginal;
        private final ByteArrayOutputStream consola = new ByteArrayOutputStream();
        private final ListAppender<ILoggingEvent> memoria = new ListAppender<>();
        private final FileAppender<ILoggingEvent> archivo;
        private final Logger raiz;

        private Captura() {
            salidaOriginal = System.out;
            errorOriginal = System.err;
            System.setOut(new PrintStream(new Copia(salidaOriginal, consola), true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(new Copia(errorOriginal, consola), true, StandardCharsets.UTF_8));

            LoggerContext contexto = (LoggerContext) LoggerFactory.getILoggerFactory();
            raiz = contexto.getLogger(Logger.ROOT_LOGGER_NAME);
            memoria.start();
            raiz.addAppender(memoria);

            PatternLayoutEncoder encoder = new PatternLayoutEncoder();
            encoder.setContext(contexto);
            encoder.setPattern("%msg %ex%n");
            encoder.start();
            archivo = new FileAppender<>();
            archivo.setContext(contexto);
            archivo.setName("bitacora-pruebas");
            archivo.setFile(ARCHIVO.toString());
            archivo.setEncoder(encoder);
            archivo.start();
            raiz.addAppender(archivo);
        }

        @Override
        public void close() throws IOException {
            raiz.detachAppender(memoria);
            raiz.detachAppender(archivo);
            archivo.stop();
            System.setOut(salidaOriginal);
            System.setErr(errorOriginal);

            StringBuilder texto = new StringBuilder(consola.toString(StandardCharsets.UTF_8));
            for (ILoggingEvent evento : memoria.list) {
                texto.append('\n').append(evento.getFormattedMessage());
                IThrowableProxy fallo = evento.getThrowableProxy();
                while (fallo != null) {
                    texto.append(' ').append(fallo.getMessage());
                    fallo = fallo.getCause();
                }
            }
            if (Files.exists(ARCHIVO)) {
                texto.append('\n').append(Files.readString(ARCHIVO));
            }
            String hallado = SecretosDePrueba.primerSecreto(texto.toString());
            if (hallado != null) {
                throw new AssertionError("La consola o la bitácora contienen un secreto de prueba: " + hallado);
            }
        }
    }

    private static final class Copia extends OutputStream {
        private final OutputStream visible;
        private final OutputStream copia;

        private Copia(OutputStream visible, OutputStream copia) {
            this.visible = visible;
            this.copia = copia;
        }

        @Override
        public void write(int b) throws IOException {
            visible.write(b);
            copia.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            visible.write(b, off, len);
            copia.write(b, off, len);
        }
    }
}
