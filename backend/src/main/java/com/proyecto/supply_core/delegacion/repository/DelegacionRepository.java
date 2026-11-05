package com.proyecto.supply_core.delegacion.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.delegacion.entity.DelegacionAprobacion;

/** Acceso a la tabla {@code delegacion_aprobacion}. */
public interface DelegacionRepository extends JpaRepository<DelegacionAprobacion, Long> {

    /**
     * Delegaciones que dio un usuario, de la más reciente a la más antigua.
     *
     * @param deleganteId usuario que delega
     * @return delegaciones
     */
    List<DelegacionAprobacion> findByDeleganteIdOrderByIdDesc(Long deleganteId);

    /**
     * Delegaciones que recibió un usuario, de la más reciente a la más antigua.
     *
     * @param delegadoId usuario que recibe
     * @return delegaciones
     */
    List<DelegacionAprobacion> findByDelegadoIdOrderByIdDesc(Long delegadoId);

    /**
     * Roles que un usuario puede ejercer hoy por delegación: los roles de cada delegante activo.
     *
     * @param delegadoId usuario que recibe
     * @param hoy        fecha de hoy
     * @return códigos de rol, sin repetir
     */
    @Query("""
            select distinct r.codigo from DelegacionAprobacion d
              join Usuario u on u.id = d.deleganteId
              join u.roles r
            where d.delegadoId = :delegadoId
              and d.estado = com.proyecto.supply_core.delegacion.enums.EstadoDelegacion.ACTIVA
              and d.vigenteDesde <= :hoy and d.vigenteHasta >= :hoy
              and u.activo = true
            """)
    Set<String> rolesDelegados(@Param("delegadoId") Long delegadoId, @Param("hoy") LocalDate hoy);

    /**
     * Ids de las delegaciones vigentes hoy que le dan a un usuario el rol pedido, sin contar las de un delegante
     * (se usa para que nadie decida lo de quien le delegó).
     *
     * @param delegadoId       usuario que recibe
     * @param rol              código del rol que exige la política
     * @param hoy              fecha de hoy
     * @param excluirDelegante delegante cuyas delegaciones no valen ({@code null} = ninguno)
     * @return ids de delegación, la más antigua primero
     */
    @Query("""
            select d.id from DelegacionAprobacion d
              join Usuario u on u.id = d.deleganteId
              join u.roles r
            where d.delegadoId = :delegadoId and r.codigo = :rol
              and d.estado = com.proyecto.supply_core.delegacion.enums.EstadoDelegacion.ACTIVA
              and d.vigenteDesde <= :hoy and d.vigenteHasta >= :hoy
              and u.activo = true
              and (:excluirDelegante is null or d.deleganteId <> :excluirDelegante)
            order by d.id
            """)
    List<Long> vigentesQueDanElRol(@Param("delegadoId") Long delegadoId, @Param("rol") String rol,
            @Param("hoy") LocalDate hoy, @Param("excluirDelegante") Long excluirDelegante);

    /**
     * Ids de los usuarios con una delegación vigente hoy que les da el rol pedido (para avisarles del SLA).
     *
     * @param rol código del rol
     * @param hoy fecha de hoy
     * @return ids de usuario delegado, sin repetir
     */
    @Query("""
            select distinct d.delegadoId from DelegacionAprobacion d
              join Usuario u on u.id = d.deleganteId
              join u.roles r
            where r.codigo = :rol
              and d.estado = com.proyecto.supply_core.delegacion.enums.EstadoDelegacion.ACTIVA
              and d.vigenteDesde <= :hoy and d.vigenteHasta >= :hoy
              and u.activo = true
            """)
    Set<Long> delegadosDelRol(@Param("rol") String rol, @Param("hoy") LocalDate hoy);
}
