package com.proyecto.supply_core.plataforma.service;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.plataforma.entity.UsuarioPlataforma;
import com.proyecto.supply_core.plataforma.repository.UsuarioPlataformaRepository;

/**
 * Crea al arrancar el primer usuario de la plataforma si todavía no hay ninguno y hay una contraseña configurada
 * ({@code PLATAFORMA_ADMIN_PASSWORD}). La contraseña nunca va en el código ni en las migraciones. Con el primer login la
 * persona vincula su app de autenticación; sin MFA confirmado no hay sesión.
 */
@Component
public class PlataformaInicializador implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PlataformaInicializador.class);
    private static final int LARGO_MINIMO = 12;
    private static final int LARGO_MAXIMO = 72;

    private final UsuarioPlataformaRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String correo;
    private final String password;

    /**
     * @param repository      usuarios de la plataforma
     * @param passwordEncoder hash BCrypt
     * @param correo          {@code app.plataforma.admin-correo}
     * @param password        {@code app.plataforma.admin-password} (vacío = no crear)
     */
    public PlataformaInicializador(UsuarioPlataformaRepository repository, PasswordEncoder passwordEncoder,
            @Value("${app.plataforma.admin-correo}") String correo,
            @Value("${app.plataforma.admin-password}") String password) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.correo = correo.trim().toLowerCase(Locale.ROOT);
        this.password = password;
    }

    /**
     * Crea el usuario inicial si hace falta. La tabla no tiene empresa, así que corre en modo sin empresa.
     *
     * @param args argumentos de arranque (no se usan)
     */
    @Override
    public void run(String... args) {
        EmpresaContexto.sinEmpresa(this::crearSiHaceFalta);
    }

    /** Crea el usuario de la plataforma si no hay ninguno y la contraseña configurada es válida. */
    private void crearSiHaceFalta() {
        if (repository.count() > 0) {
            return;
        }
        if (password == null || password.length() < LARGO_MINIMO || password.length() > LARGO_MAXIMO) {
            log.warn("No hay usuario de plataforma y PLATAFORMA_ADMIN_PASSWORD no está definida o no tiene entre {} y {} "
                    + "caracteres. Defínela y reinicia para crear al dueño.", LARGO_MINIMO, LARGO_MAXIMO);
            return;
        }
        UsuarioPlataforma u = new UsuarioPlataforma();
        u.setCorreo(correo);
        u.setNombre("Dueño de la plataforma");
        u.setHashPassword(passwordEncoder.encode(password));
        repository.save(u);
        log.info("Usuario de plataforma creado: {} (vincula su app de autenticación en el primer login)", correo);
    }
}
