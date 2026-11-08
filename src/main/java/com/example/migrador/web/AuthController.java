package com.example.migrador.web;

import com.example.migrador.domain.User;
import com.example.migrador.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Login y registro lo más simple posible: sin Spring Security, comparación directa de
 * username/password contra la BD y una bandera en la sesión HTTP.
 * IMPORTANTE: el password se guarda y se compara en texto plano (ver {@link User}).
 */
@Controller
public class AuthController {

    private final UserRepository users;

    public AuthController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/login")
    public String loginForm() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password,
                        HttpServletRequest request, Model model) {
        return users.findByUsername(username)
                .filter(u -> u.getPassword().equals(password))
                .map(u -> {
                    request.getSession(true).setAttribute("username", u.getUsername());
                    return "redirect:/";
                })
                .orElseGet(() -> {
                    model.addAttribute("error", "Usuario o contraseña incorrectos");
                    return "login";
                });
    }

    @GetMapping("/register")
    public String registerForm() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username, @RequestParam String password,
                           HttpServletRequest request, Model model) {
        if (users.findByUsername(username).isPresent()) {
            model.addAttribute("error", "Ese usuario ya existe");
            return "register";
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        users.save(user);
        request.getSession(true).setAttribute("username", user.getUsername());
        return "redirect:/";
    }

    @PostMapping("/logout")
    public String logout(HttpServletRequest request) {
        request.getSession().invalidate();
        return "redirect:/login";
    }
}
