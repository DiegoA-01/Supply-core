package com.proyecto.supply_core.compra.cotizacion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.compra.cotizacion.entity.Cotizacion;
import com.proyecto.supply_core.compra.cotizacion.enums.EstadoCotizacion;

/** Acceso a la tabla {@code cotizacion}. */
public interface CotizacionRepository extends JpaRepository<Cotizacion, Long> {

    /**
     * La cotización de un proveedor en una RFQ (hay como máximo una).
     *
     * @param rfqId       RFQ
     * @param proveedorId proveedor
     * @return cotización con líneas, si existe
     */
    @EntityGraph(attributePaths = { "rfq", "proveedor", "lineas", "lineas.rfqLinea", "lineas.producto" })
    Optional<Cotizacion> findByRfq_IdAndProveedor_Id(Long rfqId, Long proveedorId);

    /**
     * Cotizaciones de una RFQ que no están en el estado dado (normalmente BORRADOR, que es privado).
     *
     * @param rfqId  RFQ
     * @param estado estado excluido
     * @return cotizaciones con proveedor y líneas
     */
    @EntityGraph(attributePaths = { "proveedor", "lineas", "lineas.rfqLinea", "lineas.producto" })
    List<Cotizacion> findByRfq_IdAndEstadoNotOrderByEnviadoEnAsc(Long rfqId, EstadoCotizacion estado);

    /**
     * Busca por id trayendo RFQ y proveedor (reglas de acceso a documentos).
     *
     * @param id id de la cotización
     * @return cotización, si existe
     */
    @EntityGraph(attributePaths = { "rfq", "proveedor" })
    Optional<Cotizacion> findWithRfqById(Long id);
}
