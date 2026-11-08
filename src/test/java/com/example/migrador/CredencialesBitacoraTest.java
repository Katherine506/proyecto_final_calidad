package com.example.migrador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;

import com.example.migrador.connector.ConnectorRegistry;
import com.example.migrador.connector.ConnectorSession;
import com.example.migrador.domain.ItemKind;
import com.example.migrador.domain.ServerConfig;
import com.example.migrador.domain.StandardItem;
import com.example.migrador.domain.User;
import com.example.migrador.repository.ItemMappingRepository;
import com.example.migrador.repository.ServerConfigRepository;
import com.example.migrador.repository.UserRepository;
import com.example.migrador.service.ItemCreator;
import com.example.migrador.service.MigrationService;
import com.example.migrador.support.SecretosDePrueba;
import com.example.migrador.web.AuthController;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.read.ListAppender;

/*
 * U14. La contraseña y el API token no pueden salir por consola ni por archivo de bitácora.
 */
@ExtendWith(MockitoExtension.class)
class CredencialesBitacoraTest {

    private static final Path ARCHIVO = Path.of("target", "bitacora-cp-seg-03.log");

    @Mock
    UserRepository users;
    @Mock
    ServerConfigRepository configs;
    @Mock
    ConnectorRegistry registry;
    @Mock
    ItemMappingRepository mappings;
    @Mock
    ConnectorSession destino;

    /*
     * CP-U14-04 - Login, migración y alta con contraseña y API token
     * Esperada: ni la consola ni la bitácora contienen la contraseña ni el token.
     * Obtenida: ni la consola ni la bitácora contienen la contraseña ni el token.
     */
    @Test
    void contrasenaYTokenNoSalenEnConsolaNiEnBitacora() throws Exception {
        // Given
        ByteArrayOutputStream consola = new ByteArrayOutputStream();
        PrintStream salidaPrevia = System.out;
        PrintStream errorPrevio = System.err;
        System.setOut(new PrintStream(new Copia(salidaPrevia, consola), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new Copia(errorPrevio, consola), true, StandardCharsets.UTF_8));

        LoggerContext contexto = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger raiz = contexto.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> memoria = new ListAppender<>();
        memoria.start();
        FileAppender<ILoggingEvent> archivo = appenderDeArchivo(contexto);
        raiz.addAppender(memoria);
        raiz.addAppender(archivo);

        try {
            // When
            User usuario = new User();
            usuario.setUsername("jeffry");
            usuario.setPassword(SecretosDePrueba.PASSWORD);
            given(users.findByUsername("jeffry")).willReturn(Optional.of(usuario));
            String vista = new AuthController(users).login(
                    "jeffry", SecretosDePrueba.PASSWORD, new MockHttpServletRequest(), new ExtendedModelMap());

            given(configs.findById(9L)).willThrow(
                    new IllegalStateException("Authorization " + SecretosDePrueba.TOKEN));
            var migracion = new MigrationService(configs, registry, new ItemCreator(mappings))
                    .migrate(9L, 2L, List.of("LIST"), "PROJ");

            given(mappings.findBySourceConfigIdAndTargetConfigIdAndSourceItemId(any(), any(), any()))
                    .willReturn(Optional.empty());
            given(mappings.findBySourceConfigIdAndTargetConfigIdAndTargetItemId(any(), any(), any()))
                    .willReturn(Optional.empty());
            given(destino.create(any(), any())).willThrow(
                    new IllegalStateException("token " + SecretosDePrueba.TOKEN));
            ServerConfig origen = servidor(1L);
            ServerConfig target = servidor(2L);
            StandardItem item = new StandardItem("CU-1", "T", "D", List.of(), ItemKind.STORY);
            var alta = new ItemCreator(mappings).sync(origen, target, List.of(item), destino, "PROJ");

            // Then
            assertEquals("redirect:/", vista);
            assertTrue(migracion.failed());
            assertEquals(1, alta.itemErrors());
            String salida = consola.toString(StandardCharsets.UTF_8)
                    + texto(memoria)
                    + Files.readString(ARCHIVO);
            assertFalse(salida.contains(SecretosDePrueba.PASSWORD));
            assertFalse(salida.contains(SecretosDePrueba.TOKEN));
            assertNull(SecretosDePrueba.primerSecreto(salida));
        } finally {
            raiz.detachAppender(memoria);
            raiz.detachAppender(archivo);
            archivo.stop();
            System.setOut(salidaPrevia);
            System.setErr(errorPrevio);
        }
    }

    private static FileAppender<ILoggingEvent> appenderDeArchivo(LoggerContext contexto) {
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(contexto);
        encoder.setPattern("%msg %ex%n");
        encoder.start();
        FileAppender<ILoggingEvent> archivo = new FileAppender<>();
        archivo.setContext(contexto);
        archivo.setName("bitacora-cp-seg-03");
        archivo.setFile(ARCHIVO.toString());
        archivo.setEncoder(encoder);
        archivo.start();
        return archivo;
    }

    private static String texto(ListAppender<ILoggingEvent> memoria) {
        StringBuilder texto = new StringBuilder();
        for (ILoggingEvent evento : memoria.list) {
            texto.append('\n').append(evento.getFormattedMessage());
            IThrowableProxy fallo = evento.getThrowableProxy();
            while (fallo != null) {
                texto.append(' ').append(fallo.getMessage());
                fallo = fallo.getCause();
            }
        }
        return texto.toString();
    }

    private static ServerConfig servidor(long id) {
        ServerConfig config = new ServerConfig();
        config.setId(id);
        config.setApiToken(SecretosDePrueba.TOKEN);
        return config;
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
