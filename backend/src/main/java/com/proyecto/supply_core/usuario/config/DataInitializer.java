package com.proyecto.supply_core.usuario.config;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.common.util.EmpresaUnica;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.RolRepository;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

/**
 * Crea el administrador del sistema la primera vez que arranca la app.
 * La contraseña viene de {@code ADMIN_PASSWORD} (nunca del código ni de una migración SQL).
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final int LARGO_MINIMO = 8;
    private static final int LARGO_MAXIMO = 72; // límite de BCrypt

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    /**
     * @param usuarioRepository acceso a usuarios
     * @param rolRepository     acceso a roles
     * @param passwordEncoder   hash BCrypt
     * @param adminEmail        {@code app.admin.email}
     * @param adminPassword     {@code app.admin.password} (vacío = no crear)
     */
    public DataInitializer(UsuarioRepository usuarioRepository, RolRepository rolRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.email}") String adminEmail,
            @Value("${app.admin.password}") String adminPassword) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail.trim().toLowerCase(Locale.ROOT);
        this.adminPassword = adminPassword;
    }

    /**
     * Si no existe ningún ADMIN_SISTEMA y hay contraseña configurada válida, lo crea.
     *
     * @param args argumentos de arranque (no se usan)
     */
    @Override
    @Transactional
    public void run(String... args) {
        if (usuarioRepository.existsByRoles_Codigo(Roles.ADMIN_SISTEMA)) {
            return;
        }
        if (adminPassword == null || adminPassword.length() < LARGO_MINIMO || adminPassword.length() > LARGO_MAXIMO) {
            log.warn("No hay administrador y ADMIN_PASSWORD no está definida o no tiene entre {} y {} caracteres. "
                    + "Defínela y reinicia para crear el admin inicial.", LARGO_MINIMO, LARGO_MAXIMO);
            return;
        }
        if (usuarioRepository.existsByCorreoIgnoreCase(adminEmail)) {
            log.warn("No se creó el admin: el correo {} ya pertenece a otro usuario", adminEmail);
            return;
        }
        Usuario admin = new Usuario();
        admin.setEmpresaId(EmpresaUnica.ID);
        admin.setNombreCompleto("Administrador del sistema");
        admin.setCorreo(adminEmail);
        admin.setHashPassword(passwordEncoder.encode(adminPassword));
        admin.getRoles().add(rolRepository.findByCodigo(Roles.ADMIN_SISTEMA)
                .orElseThrow(() -> new IllegalStateException("Falta el rol ADMIN_SISTEMA (V2)")));
        usuarioRepository.save(admin);
        log.info("Administrador inicial creado: {}", adminEmail);
    }
}
