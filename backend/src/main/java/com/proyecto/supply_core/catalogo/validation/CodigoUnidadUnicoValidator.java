package com.proyecto.supply_core.catalogo.validation;

import java.util.Locale;

import com.proyecto.supply_core.catalogo.repository.UnidadMedidaRepository;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Implementa {@link CodigoUnidadUnico}. Spring lo crea e inyecta el repositorio. */
public class CodigoUnidadUnicoValidator implements ConstraintValidator<CodigoUnidadUnico, String> {

    private final UnidadMedidaRepository unidadRepository;

    /**
     * @param unidadRepository acceso a unidades de medida
     */
    public CodigoUnidadUnicoValidator(UnidadMedidaRepository unidadRepository) {
        this.unidadRepository = unidadRepository;
    }

    /**
     * Comprueba que ninguna unidad tenga ya el código.
     *
     * @param codigo  valor recibido
     * @param context contexto de validación
     * @return {@code true} si está libre o vacío (el vacío lo reporta {@code @NotBlank})
     */
    @Override
    public boolean isValid(String codigo, ConstraintValidatorContext context) {
        if (codigo == null || codigo.isBlank()) {
            return true;
        }
        return !unidadRepository.existsByCodigoIgnoreCase(codigo.trim().toUpperCase(Locale.ROOT));
    }
}
