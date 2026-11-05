package com.proyecto.supply_core.empresa.validation;

import com.proyecto.supply_core.empresa.repository.SedeRepository;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Implementa {@link SedeActiva}. Spring lo crea e inyecta el repositorio. */
public class SedeActivaValidator implements ConstraintValidator<SedeActiva, Long> {

    private final SedeRepository repository;

    /**
     * @param repository acceso a los datos a verificar
     */
    public SedeActivaValidator(SedeRepository repository) {
        this.repository = repository;
    }

    /**
     * Comprueba que el registro exista y esté activo.
     *
     * @param id      id recibido
     * @param context contexto de validación
     * @return {@code true} si es {@code null} o un registro activo; los ids no positivos los reporta {@code @Positive}
     */
    @Override
    public boolean isValid(Long id, ConstraintValidatorContext context) {
        if (id == null || id <= 0) {
            return true;
        }
        return repository.existsByIdAndActivoTrue(id);
    }
}
