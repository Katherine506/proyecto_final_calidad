package com.example.migrador.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.BDDMockito.given;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import com.example.migrador.domain.User;
import com.example.migrador.repository.UserRepository;

/*
 * AuthControllerTest — la contraseña no vuelve en el mensaje ni queda en la sesión.
 * El controller se prueba directo, sin levantar Spring.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String PASSWORD = "clave-local-seg";

    @Mock
    UserRepository users;

    /*
     * CP-U14-01 - Usuario se autentica con credenciales válidas
     * Esperada: la autenticación no deja la contraseña en la sesión.
     * Obtenida: la autenticación no deja la contraseña en la sesión.
     */
    @Test
    void loginCredencialesValidasNoGuardaLaContrasenaEnLaSesion() {
        // Given
        User user = usuario();
        given(users.findByUsername("jeffry")).willReturn(Optional.of(user));
        MockHttpServletRequest request = new MockHttpServletRequest();

        // When
        String vista = new AuthController(users).login("jeffry", PASSWORD, request, new ExtendedModelMap());

        // Then
        assertEquals("redirect:/", vista);
        assertEquals("jeffry", request.getSession().getAttribute("username"));
        assertNull(request.getSession().getAttribute("password"));
    }

    /*
     * CP-U14-03 - Usuario se autentica con credenciales inválidas
     * Esperada: se rechaza el acceso sin revelar la contraseña.
     * Obtenida: se rechaza el acceso sin revelar la contraseña.
     */
    @Test
    void contrasenaIncorrectaNoLaRevela() {
        // Given
        given(users.findByUsername("jeffry")).willReturn(Optional.of(usuario()));
        Model model = new ExtendedModelMap();

        // When
        String vista = new AuthController(users).login("jeffry", "otra", new MockHttpServletRequest(), model);

        // Then
        assertEquals("login", vista);
        assertEquals("Usuario o contrasena incorrectos", model.getAttribute("error"));
        assertFalse(String.valueOf(model.getAttribute("error")).contains(PASSWORD));
    }

    private static User usuario() {
        User user = new User();
        user.setUsername("jeffry");
        user.setPassword(PASSWORD);
        return user;
    }
}
