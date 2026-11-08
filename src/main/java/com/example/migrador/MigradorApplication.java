package com.example.migrador;

import com.example.migrador.domain.User;
import com.example.migrador.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class MigradorApplication {

    public static void main(String[] args) {
        SpringApplication.run(MigradorApplication.class, args);
    }

    /** Crea un usuario admin/admin la primera vez, para poder entrar sin registrarse. */
    @Bean
    CommandLineRunner seedAdmin(UserRepository users) {
        return args -> {
            if (users.count() == 0) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword("admin");
                users.save(admin);
            }
        };
    }
}
