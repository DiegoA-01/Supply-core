package com.proyecto.supply_core.proveedor.validation;

import com.proyecto.supply_core.proveedor.repository.ProveedorRepository;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Implementa {@link NitUnico}. Spring lo crea e inyecta el repositorio por constructor. */
public class NitUnicoValidator implements ConstraintValidator<NitUnico, String> {

    private final ProveedorRepository proveedorRepository;

    /**
     * @param proveedorRepository acceso a proveedores
     */
    public NitUnicoValidator(ProveedorRepository proveedorRepository) {
        this.proveedorRepository = proveedorRepository;
    }

    /**
     * Comprueba que ningún proveedor tenga ya el NIT.
     *
     * @param nit     valor recibido
     * @param context contexto de validación
     * @return {@code true} si está libre o vacío (el vacío lo reporta {@code @NotBlank})
     */
    @Override
    public boolean isValid(String nit, ConstraintValidatorContext context) {
        if (nit == null || nit.isBlank()) {
            return true;
        }
        return !proveedorRepository.existsByNitIgnoreCase(nit.trim());
    }
}
