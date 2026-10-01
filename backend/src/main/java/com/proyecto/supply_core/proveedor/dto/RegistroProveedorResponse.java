package com.proyecto.supply_core.proveedor.dto;

import com.proyecto.supply_core.proveedor.enums.EstadoProveedor;

/**
 * Resultado del autorregistro.
 *
 * @param proveedorId id del proveedor creado
 * @param estado      siempre {@code PENDIENTE}
 * @param mensaje     texto para mostrar al proveedor
 */
public record RegistroProveedorResponse(Long proveedorId, EstadoProveedor estado, String mensaje) {
}
