package com.proyecto.supply_core.catalogo.validation;

import com.proyecto.supply_core.catalogo.repository.CategoriaProductoRepository;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Implementa {@link NombreCategoriaUnico}. Spring lo crea e inyecta el repositorio. */
public class NombreCategoriaUnicoValidator implements ConstraintValidator<NombreCategoriaUnico, String> {

    private final CategoriaProductoRepository categoriaRepository;

    /**
     * @param categoriaRepository acceso a categorías
     */
    public NombreCategoriaUnicoValidator(CategoriaProductoRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    /**
     * Comprueba que ninguna categoría tenga ya el nombre.
     *
     * @param nombre  valor recibido
     * @param context contexto de validación
     * @return {@code true} si está libre o vacío (el vacío lo reporta {@code @NotBlank})
     */
    @Override
    public boolean isValid(String nombre, ConstraintValidatorContext context) {
        if (nombre == null || nombre.isBlank()) {
            return true;
        }
        return !categoriaRepository.existsByNombreIgnoreCase(nombre.trim());
    }
}
