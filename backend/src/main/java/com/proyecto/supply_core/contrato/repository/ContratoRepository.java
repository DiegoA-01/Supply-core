package com.proyecto.supply_core.contrato.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;
import com.proyecto.supply_core.contrato.dto.ConsumoPrecio;
import com.proyecto.supply_core.contrato.entity.Contrato;
import com.proyecto.supply_core.contrato.enums.EstadoContrato;

import jakarta.persistence.LockModeType;

/** Acceso a las tablas {@code contrato_proveedor} y {@code contrato_precio}. */
public interface ContratoRepository extends JpaRepository<Contrato, Long> {

    /**
     * Busca por id con proveedor, precios y productos (con su unidad).
     *
     * @param id contrato
     * @return contrato, si existe
     */
    @EntityGraph(attributePaths = { "proveedor", "precios", "precios.producto", "precios.producto.unidadMedida" })
    Optional<Contrato> findDetalleById(Long id);

    /**
     * Bloquea la fila hasta el fin de la transacción ({@code SELECT ... FOR UPDATE}): dos órdenes directas del mismo
     * contrato se evalúan una tras otra y no se pasan juntas del tope.
     *
     * @param id contrato
     * @return contrato bloqueado, si existe
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Contrato c where c.id = :id")
    Optional<Contrato> bloquear(@Param("id") Long id);

    /**
     * Indica si ya existe un contrato con ese número en la empresa (sin distinguir mayúsculas).
     *
     * @param numero número del contrato
     * @return {@code true} si existe
     */
    boolean existsByNumeroIgnoreCase(String numero);

    /**
     * Cuenta los contratos ACTIVOS del proveedor cuya vigencia se cruza con el rango y que ya fijan precio para
     * alguno de los productos. Un resultado mayor que cero significa solape.
     *
     * @param proveedorId proveedor
     * @param productoIds productos del contrato nuevo
     * @param desde       inicio de la vigencia
     * @param hasta       fin de la vigencia
     * @param excluirId   contrato que no se cuenta ({@code null} = contar todos)
     * @return cantidad de contratos que chocan
     */
    @Query("""
            select count(distinct c.id) from Contrato c join c.precios p
            where c.proveedor.id = :proveedorId
              and c.estado = com.proyecto.supply_core.contrato.enums.EstadoContrato.ACTIVO
              and p.producto.id in :productoIds
              and c.vigenteDesde <= :hasta and c.vigenteHasta >= :desde
              and (:excluirId is null or c.id <> :excluirId)
            """)
    long contarSolapados(@Param("proveedorId") Long proveedorId, @Param("productoIds") Collection<Long> productoIds,
            @Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta, @Param("excluirId") Long excluirId);

    /**
     * Contratos ACTIVOS y vigentes hoy que fijan precio para alguno de los productos, con todos sus precios.
     *
     * @param productoIds productos buscados
     * @param hoy         fecha de hoy
     * @return contratos vigentes, del que vence antes al que vence después
     */
    @EntityGraph(attributePaths = { "proveedor", "precios", "precios.producto", "precios.producto.unidadMedida" })
    @Query("""
            select distinct c from Contrato c join c.precios p
            where c.estado = com.proyecto.supply_core.contrato.enums.EstadoContrato.ACTIVO
              and c.vigenteDesde <= :hoy and c.vigenteHasta >= :hoy
              and p.producto.id in :productoIds
            order by c.vigenteHasta, c.id
            """)
    List<Contrato> vigentesConProductos(@Param("productoIds") Collection<Long> productoIds, @Param("hoy") LocalDate hoy);

    /**
     * Búsqueda paginada con filtros opcionales ({@code null} = no filtrar).
     *
     * @param proveedorId proveedor
     * @param estado      estado
     * @param vigenteEn   si se envía, solo los ACTIVOS vigentes ese día
     * @param pageable    página y orden
     * @return página de contratos
     */
    @EntityGraph(attributePaths = { "proveedor" })
    @Query("""
            select c from Contrato c
            where (:proveedorId is null or c.proveedor.id = :proveedorId)
              and (:estado is null or c.estado = :estado)
              and (:vigenteEn is null or (c.estado = com.proyecto.supply_core.contrato.enums.EstadoContrato.ACTIVO
                   and c.vigenteDesde <= :vigenteEn and c.vigenteHasta >= :vigenteEn))
            """)
    Page<Contrato> buscar(@Param("proveedorId") Long proveedorId, @Param("estado") EstadoContrato estado,
            @Param("vigenteEn") LocalDate vigenteEn, Pageable pageable);

    /**
     * Suma lo ya pedido contra cada precio pactado, contando solo las órdenes que siguen vivas.
     *
     * @param precioIds precios pactados
     * @param excluidos estados de orden que no cuentan (canceladas y reemplazadas)
     * @return una fila por precio que tenga pedidos
     */
    @Query("""
            select new com.proyecto.supply_core.contrato.dto.ConsumoPrecio(l.contratoPrecioId, sum(l.cantidad))
            from OrdenCompraLinea l
            where l.contratoPrecioId in :precioIds and l.orden.estado not in :excluidos
            group by l.contratoPrecioId
            """)
    List<ConsumoPrecio> consumo(@Param("precioIds") Collection<Long> precioIds,
            @Param("excluidos") Collection<EstadoOrden> excluidos);
}
