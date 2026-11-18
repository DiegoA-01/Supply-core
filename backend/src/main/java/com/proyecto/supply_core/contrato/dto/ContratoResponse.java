package com.proyecto.supply_core.contrato.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.proyecto.supply_core.contrato.entity.Contrato;
import com.proyecto.supply_core.contrato.enums.EstadoContrato;

/**
 * Contrato con sus precios pactados y lo ya consumido de cada tope.
 *
 * @param id                   id
 * @param numero               número del contrato
 * @param descripcion          descripción
 * @param proveedorId          proveedor
 * @param proveedorRazonSocial razón social
 * @param vigenteDesde         primer día de vigencia
 * @param vigenteHasta         último día de vigencia
 * @param estado               ACTIVO o ANULADO
 * @param vigente              {@code true} si hoy se puede usar (ACTIVO y dentro de las fechas)
 * @param motivoAnulacion      motivo, si está anulado
 * @param creadoEn             fecha de creación
 * @param precios              precios pactados
 */
public record ContratoResponse(Long id, String numero, String descripcion, Long proveedorId,
        String proveedorRazonSocial, LocalDate vigenteDesde, LocalDate vigenteHasta, EstadoContrato estado,
        boolean vigente, String motivoAnulacion, LocalDateTime creadoEn, List<Precio> precios) {

    /**
     * Convierte la entidad (proveedor, precios y productos cargados).
     *
     * @param c         contrato
     * @param consumido cantidad ya pedida por id de precio pactado (puede faltar un precio sin pedidos)
     * @param hoy       fecha con la que se calcula si está vigente
     * @return DTO
     */
    public static ContratoResponse of(Contrato c, Map<Long, BigDecimal> consumido, LocalDate hoy) {
        return new ContratoResponse(c.getId(), c.getNumero(), c.getDescripcion(), c.getProveedor().getId(),
                c.getProveedor().getRazonSocial(), c.getVigenteDesde(), c.getVigenteHasta(), c.getEstado(),
                c.esVigente(hoy), c.getMotivoAnulacion(), c.getCreadoEn(), c.getPrecios().stream().map(p -> {
                    BigDecimal usado = consumido.getOrDefault(p.getId(), BigDecimal.ZERO);
                    BigDecimal disponible = p.getCantidadMaxima() == null ? null : p.getCantidadMaxima().subtract(usado);
                    return new Precio(p.getId(), p.getProducto().getId(), p.getProducto().getCodigo(),
                            p.getProducto().getNombre(), p.getProducto().getUnidadMedida().getCodigo(),
                            p.getPrecioUnitario(), p.getImpuestoPct(), p.getCantidadMaxima(), usado, disponible);
                }).toList());
    }

    /**
     * Precio pactado de un producto.
     *
     * @param id              id del precio pactado
     * @param productoId      producto
     * @param productoCodigo  código
     * @param productoNombre  nombre
     * @param unidad          unidad del producto (el precio y el tope van en esta unidad)
     * @param precioUnitario  precio sin impuesto
     * @param impuestoPct     impuesto %
     * @param cantidadMaxima  tope ({@code null} = sin tope)
     * @param consumido       cantidad ya pedida en órdenes vivas
     * @param disponible      tope menos consumido ({@code null} si no hay tope)
     */
    public record Precio(Long id, Long productoId, String productoCodigo, String productoNombre, String unidad,
            BigDecimal precioUnitario, BigDecimal impuestoPct, BigDecimal cantidadMaxima, BigDecimal consumido,
            BigDecimal disponible) {
    }
}
