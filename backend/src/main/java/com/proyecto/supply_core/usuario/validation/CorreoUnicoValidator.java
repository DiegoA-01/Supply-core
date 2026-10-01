package com.proyecto.supply_core.usuario.validation;

import java.util.Locale;

import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Implementa {@link CorreoUnico}. Spring lo crea e inyecta el repositorio por constructor. */
public class CorreoUnicoValidator implements ConstraintValidator<CorreoUnico, String> {

    private final UsuarioRepository usuarioRepository;

    /**
     * @param usuarioRepository acceso a usuarios
     */
    public CorreoUnicoValidator(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Comprueba que ningún usuario tenga ya el correo.
     *
     * @param correo  valor recibido
     * @param context contexto de validación
     * @return {@code true} si está libre o vacío (el vacío lo reporta {@code @NotBlank})
     */
    @Override
    public boolean isValid(String correo, ConstraintValidatorContext context) {
        if (correo == null || correo.isBlank()) {
            return true;
        }
        return !usuarioRepository.existsByCorreoIgnoreCase(correo.trim().toLowerCase(Locale.ROOT));
    }
}
