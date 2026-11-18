package com.proyecto.supply_core.inventario.validation;

import com.proyecto.supply_core.inventario.repository.ProductoRepository;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Implementa {@link ProductoActivo}. Spring lo crea e inyecta el repositorio. */
public class ProductoActivoValidator implements ConstraintValidator<ProductoActivo, Long> {

    private final ProductoRepository repository;

    /**
     * @param repository acceso a productos
     */
    public ProductoActivoValidator(ProductoRepository repository) {
        this.repository = repository;
    }

    /**
     * Comprueba que el producto exista y esté activo.
     *
     * @param id      id recibido
     * @param context contexto de validación
     * @return {@code true} si es {@code null} o un producto activo; los ids no positivos los reporta {@code @Positive}
     */
    @Override
    public boolean isValid(Long id, ConstraintValidatorContext context) {
        if (id == null || id <= 0) {
            return true;
        }
        return repository.existsByIdAndActivoTrue(id);
    }
}
