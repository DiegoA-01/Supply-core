package com.proyecto.supply_core.empresa.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.proyecto.supply_core.empresa.entity.Empresa;

/**
 * Empresa tal como la ve la API.
 *
 * @param id                 id
 * @param razonSocial        nombre legal
 * @param nit                NIT
 * @param direccionPrincipal dirección principal
 * @param monedaBase         moneda (ISO 4217)
 * @param topeAnticipoPct    tope de los anticipos (% del total de la orden)
 * @param toleranciaFacturaPct   tolerancia del cruce de la factura en %
 * @param toleranciaFacturaMonto tolerancia del cruce de la factura en monto fijo
 * @param slaAprobacionHoras horas para decidir una solicitud pendiente antes de escalarla (0 = sin SLA)
 * @param creadoEn           fecha de creación
 * @param codigoPublico      código con el que entran los usuarios y los proveedores de la empresa
 * @param plazoEntregaDiasDefecto días hábiles que suele tardar un pedido en llegar
 * @param diasSeguridadDefecto    días hábiles de colchón por retrasos o picos de consumo
 * @param diasCoberturaDefecto    días de calendario que debe cubrir un pedido al llegar
 * @param preciosDelegadosCompras el jefe de compras también puede fijar precios de venta
 */
public record EmpresaResponse(Long id, String razonSocial, String nit, String direccionPrincipal,
        String monedaBase, BigDecimal topeAnticipoPct, BigDecimal toleranciaFacturaPct,
        BigDecimal toleranciaFacturaMonto, int slaAprobacionHoras, LocalDateTime creadoEn,
        String codigoPublico, int plazoEntregaDiasDefecto, int diasSeguridadDefecto, int diasCoberturaDefecto,
        boolean preciosDelegadosCompras) {

    /**
     * Convierte la entidad en DTO.
     *
     * @param e empresa
     * @return DTO
     */
    public static EmpresaResponse of(Empresa e) {
        return new EmpresaResponse(e.getId(), e.getRazonSocial(), e.getNit(), e.getDireccionPrincipal(),
                e.getMonedaBase(), e.getTopeAnticipoPct(), e.getToleranciaFacturaPct(), e.getToleranciaFacturaMonto(),
                e.getSlaAprobacionHoras(), e.getCreadoEn(), e.getCodigoPublico(), e.getPlazoEntregaDiasDefecto(),
                e.getDiasSeguridadDefecto(), e.getDiasCoberturaDefecto(), e.isPreciosDelegadosCompras());
    }
}
