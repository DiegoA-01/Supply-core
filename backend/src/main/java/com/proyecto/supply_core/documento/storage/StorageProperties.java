package com.proyecto.supply_core.documento.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Configuración del almacenamiento de archivos ({@code app.storage.*}). Se valida al arrancar.
 *
 * @param ruta              carpeta raíz donde se guardan los archivos ({@code STORAGE_RUTA})
 * @param tamanoMaximoBytes tamaño máximo por archivo
 */
@Validated
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        @NotBlank(message = "es obligatoria") String ruta,
        @Positive(message = "debe ser mayor que cero") long tamanoMaximoBytes) {
}
