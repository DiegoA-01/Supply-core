package com.proyecto.supply_core.security.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.proyecto.supply_core.security.jwt.JwtProperties;

/** Beans de seguridad que no dependen de Spring Security (solo su módulo crypto). */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityBeansConfig {

    /**
     * Hash de contraseñas con BCrypt costo 12: lento a propósito para frenar la fuerza bruta.
     * <p>BCrypt solo usa los primeros 72 bytes; por eso los DTOs limitan la contraseña a 72.</p>
     *
     * @return codificador compartido por toda la aplicación
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
