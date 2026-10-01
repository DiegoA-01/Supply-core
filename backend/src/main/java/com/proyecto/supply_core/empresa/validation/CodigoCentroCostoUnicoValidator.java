package com.proyecto.supply_core.empresa.validation;

import java.util.Locale;

import com.proyecto.supply_core.empresa.repository.CentroCostoRepository;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Implementa {@link CodigoCentroCostoUnico}. Spring lo crea e inyecta el repositorio. */
public class CodigoCentroCostoUnicoValidator implements ConstraintValidator<CodigoCentroCostoUnico, String> {

    private final CentroCostoRepository centroCostoRepository;

    /**
     * @param centroCostoRepository acceso a centros de costo
     */
    public CodigoCentroCostoUnicoValidator(CentroCostoRepository centroCostoRepository) {
        this.centroCostoRepository = centroCostoRepository;
    }

    /**
     * Comprueba que ningún centro de costo tenga ya el código.
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
        return !centroCostoRepository.existsByCodigoIgnoreCase(codigo.trim().toUpperCase(Locale.ROOT));
    }
}
