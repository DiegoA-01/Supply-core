package com.proyecto.supply_core.empresa.dto;

import java.util.List;

import com.proyecto.supply_core.plataforma.enums.EstadoSuscripcion;

/**
 * Lo que la empresa del usuario puede usar hoy, para que el frontend muestre solo los módulos contratados y avise si la
 * suscripción está en mora.
 *
 * @param estado  estado de la suscripción
 * @param plan    código del plan
 * @param modulos códigos de los módulos activos, ordenados
 */
public record ModulosEmpresaResponse(EstadoSuscripcion estado, String plan, List<String> modulos) {
}
