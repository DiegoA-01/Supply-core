package com.proyecto.supply_core.contrato.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import com.proyecto.supply_core.inventario.validation.ProductoActivo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear un contrato con un proveedor.
 *
 * @param proveedorId   proveedor HABILITADO
 * @param numero        número del contrato, único en la empresa
 * @param descripcion   texto libre (opcional)
 * @param vigenteDesde  primer día de vigencia
 * @param vigenteHasta  último día de vigencia (no antes de hoy ni de {@code vigenteDesde})
 * @param precios       precio pactado por producto (al menos uno, sin productos repetidos)
 */
public record ContratoRequest(
        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser un id válido")
        Long proveedorId,

        @NotBlank(message = "es obligatorio")
        @Size(max = 40, message = "máximo 40 caracteres")
        String numero,

        @Size(max = 250, message = "máximo 250 caracteres")
        String descripcion,

        @NotNull(message = "es obligatoria")
        LocalDate vigenteDesde,

        @NotNull(message = "es obligatoria")
        LocalDate vigenteHasta,

        @NotEmpty(message = "debe tener al menos un precio")
        @Size(max = 500, message = "máximo 500 precios")
        List<@NotNull(message = "no puede estar vacío") @Valid PrecioRequest> precios) {

    /**
     * Regla entre fechas: el contrato no puede terminar antes de empezar.
     *
     * @return {@code true} si las fechas faltan (otra validación las exige) o están en orden
     */
    @AssertTrue(message = "la vigencia no puede terminar antes de empezar")
    public boolean isVigenciaEnOrden() {
        return vigenteDesde == null || vigenteHasta == null || !vigenteHasta.isBefore(vigenteDesde);
    }

    /**
     * Regla entre líneas: cada producto una sola vez.
     *
     * @return {@code true} si no hay productos repetidos
     */
    @AssertTrue(message = "no puede repetir un producto")
    public boolean isProductosSinRepetir() {
        if (precios == null) {
            return true;
        }
        var ids = precios.stream().filter(Objects::nonNull).map(PrecioRequest::productoId).filter(Objects::nonNull)
                .toList();
        return ids.size() == ids.stream().distinct().count();
    }

    /**
     * Precio pactado de un producto.
     *
     * @param productoId      producto activo
     * @param precioUnitario  precio por unidad del producto, sin impuesto, mayor que cero
     * @param impuestoPct     impuesto en porcentaje, de 0 a 100
     * @param cantidadMaxima  tope de cantidad en unidades del producto durante toda la vigencia ({@code null} = sin tope)
     */
    public record PrecioRequest(
            @NotNull(message = "es obligatorio")
            @Positive(message = "debe ser un id válido")
            @ProductoActivo
            Long productoId,

            @NotNull(message = "es obligatorio")
            @Positive(message = "debe ser mayor que cero")
            @Digits(integer = 14, fraction = 4, message = "máximo 14 enteros y 4 decimales")
            BigDecimal precioUnitario,

            @NotNull(message = "es obligatorio")
            @DecimalMin(value = "0", message = "no puede ser negativo")
            @DecimalMax(value = "100", message = "no puede pasar de 100")
            @Digits(integer = 3, fraction = 2, message = "máximo 2 decimales")
            BigDecimal impuestoPct,

            @Positive(message = "debe ser mayor que cero")
            @Digits(integer = 14, fraction = 4, message = "máximo 14 enteros y 4 decimales")
            BigDecimal cantidadMaxima) {
    }
}
