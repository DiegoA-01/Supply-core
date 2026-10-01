package com.proyecto.supply_core.inventario.validation;

import java.util.Locale;

import com.proyecto.supply_core.inventario.repository.ProductoRepository;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Implementa {@link CodigoProductoUnico}. Spring lo crea e inyecta el repositorio. */
public class CodigoProductoUnicoValidator implements ConstraintValidator<CodigoProductoUnico, String> {

    private final ProductoRepository productoRepository;

    /**
     * @param productoRepository acceso a productos
     */
    public CodigoProductoUnicoValidator(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    /**
     * Comprueba que ningún producto tenga ya el código.
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
        return !productoRepository.existsByCodigoIgnoreCase(codigo.trim().toUpperCase(Locale.ROOT));
    }
}
