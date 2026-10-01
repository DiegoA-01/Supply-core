package com.proyecto.supply_core.catalogo.validation;

import com.proyecto.supply_core.catalogo.repository.UnidadMedidaRepository;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Implementa {@link UnidadMedidaActiva}. Spring lo crea e inyecta el repositorio. */
public class UnidadMedidaActivaValidator implements ConstraintValidator<UnidadMedidaActiva, Long> {

    private final UnidadMedidaRepository repository;

    /**
     * @param repository acceso a los datos a verificar
     */
    public UnidadMedidaActivaValidator(UnidadMedidaRepository repository) {
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
