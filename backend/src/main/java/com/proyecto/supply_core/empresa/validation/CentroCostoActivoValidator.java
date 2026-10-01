package com.proyecto.supply_core.empresa.validation;

import com.proyecto.supply_core.empresa.repository.CentroCostoRepository;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Implementa {@link CentroCostoActivo}. Spring lo crea e inyecta el repositorio. */
public class CentroCostoActivoValidator implements ConstraintValidator<CentroCostoActivo, Long> {

    private final CentroCostoRepository centroCostoRepository;

    /**
     * @param centroCostoRepository acceso a centros de costo
     */
    public CentroCostoActivoValidator(CentroCostoRepository centroCostoRepository) {
        this.centroCostoRepository = centroCostoRepository;
    }

    /**
     * Comprueba que el centro exista y esté activo.
     *
     * @param id      id recibido
     * @param context contexto de validación
     * @return {@code true} si es {@code null} o un centro activo; los ids no positivos los reporta {@code @Positive}
     */
    @Override
    public boolean isValid(Long id, ConstraintValidatorContext context) {
        if (id == null || id <= 0) {
            return true;
        }
        return centroCostoRepository.existsByIdAndActivoTrue(id);
    }
}
