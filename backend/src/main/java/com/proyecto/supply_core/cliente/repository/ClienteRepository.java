package com.proyecto.supply_core.cliente.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.cliente.entity.Cliente;
import com.proyecto.supply_core.cliente.enums.TipoDocumento;

/** Clientes de la empresa ({@code @TenantId}). */
public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    /**
     * @param tipo   tipo de documento
     * @param numero número normalizado
     * @return el cliente con ese documento en la empresa, si existe
     */
    Optional<Cliente> findByTipoDocumentoAndNumeroDocumento(TipoDocumento tipo, String numero);

    /**
     * @return el consumidor final de la empresa, si ya existe
     */
    Optional<Cliente> findFirstByEsConsumidorFinalTrue();

    /**
     * Busca por número de documento (empieza por) o nombre (contiene). El consumidor final va primero.
     *
     * @param numero   número sin puntos ni espacios + {@code "%"} (comodines escapados con {@code !})
     * @param nombre   {@code "%"} + texto en minúsculas + {@code "%"} (comodines escapados con {@code !})
     * @param activo   filtro por estado ({@code null} = todos)
     * @param pageable página
     * @return página de clientes
     */
    @Query("""
            select c from Cliente c left join fetch c.listaPrecio
            where (:activo is null or c.activo = :activo)
              and (c.numeroDocumento like :numero escape '!' or lower(c.nombre) like :nombre escape '!')
            order by c.esConsumidorFinal desc, c.nombre
            """)
    Page<Cliente> buscar(@Param("numero") String numero, @Param("nombre") String nombre,
            @Param("activo") Boolean activo, Pageable pageable);

    /**
     * @param activo filtro por estado ({@code null} = todos)
     * @return todos los clientes, el consumidor final primero y luego por nombre (exportación)
     */
    @Query("""
            select c from Cliente c left join fetch c.listaPrecio
            where (:activo is null or c.activo = :activo)
            order by c.esConsumidorFinal desc, c.nombre
            """)
    List<Cliente> todos(@Param("activo") Boolean activo);
}
