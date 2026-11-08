package com.example.migrador.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

/** Exige haber iniciado sesión para cualquier ruta no listada como pública en {@link WebConfig}. */
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("username") != null) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", request.getContextPath() + "/login");
        return false;
    }
}
