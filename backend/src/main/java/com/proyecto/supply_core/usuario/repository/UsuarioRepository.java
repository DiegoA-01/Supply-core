package com.proyecto.supply_core.usuario.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.usuario.entity.Usuario;

/** Acceso a la tabla {@code usuario}. */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /**
     * Busca por correo trayendo los roles en la misma consulta (se necesitan para el JWT).
     *
     * @param correo correo a buscar
     * @return usuario con roles, si existe
     */
    @EntityGraph(attributePaths = "roles")
    Optional<Usuario> findByCorreoIgnoreCase(String correo);

    /**
     * Busca por id trayendo los roles.
     *
     * @param id id del usuario
     * @return usuario con roles, si existe
     */
    @EntityGraph(attributePaths = "roles")
    Optional<Usuario> findWithRolesById(Long id);

    /**
     * Usuarios del portal de un proveedor.
     *
     * @param proveedorId id del proveedor
     * @return sus usuarios (activos o no)
     */
    List<Usuario> findByProveedorId(Long proveedorId);

    /**
     * Indica si el correo ya está registrado.
     *
     * @param correo correo a verificar
     * @return {@code true} si existe
     */
    boolean existsByCorreoIgnoreCase(String correo);

    /**
     * Indica si el correo lo usa otro usuario distinto al indicado (para ediciones).
     *
     * @param correo correo a verificar
     * @param id     usuario que se está editando
     * @return {@code true} si otro usuario ya lo tiene
     */
    boolean existsByCorreoIgnoreCaseAndIdNot(String correo, Long id);

    /**
     * Indica si algún usuario (activo o no) tiene el rol.
     *
     * @param codigoRol código del rol
     * @return {@code true} si existe al menos uno
     */
    boolean existsByRoles_Codigo(String codigoRol);

    /**
     * Cuenta usuarios activos con el rol.
     *
     * @param codigoRol código del rol
     * @return cantidad de usuarios activos
     */
    long countByActivoTrueAndRoles_Codigo(String codigoRol);

    /**
     * Usuarios activos con el rol (destinatarios de notificaciones por rol).
     *
     * @param codigoRol código del rol
     * @return usuarios activos que lo tienen
     */
    List<Usuario> findByActivoTrueAndRoles_Codigo(String codigoRol);

    /**
     * Búsqueda paginada con filtros opcionales ({@code null} = no filtrar).
     *
     * @param texto    coincidencia parcial en nombre o correo
     * @param activo   estado del usuario
     * @param rol      código de rol
     * @param pageable página y orden
     * @return página de usuarios
     */
    @Query(value = """
            select distinct u from Usuario u left join u.roles r
            where (:texto is null or lower(u.nombreCompleto) like lower(concat('%', :texto, '%'))
                                  or lower(u.correo) like lower(concat('%', :texto, '%')))
              and (:activo is null or u.activo = :activo)
              and (:rol is null or r.codigo = :rol)
            """,
            countQuery = """
            select count(distinct u) from Usuario u left join u.roles r
            where (:texto is null or lower(u.nombreCompleto) like lower(concat('%', :texto, '%'))
                                  or lower(u.correo) like lower(concat('%', :texto, '%')))
              and (:activo is null or u.activo = :activo)
              and (:rol is null or r.codigo = :rol)
            """)
    Page<Usuario> buscar(@Param("texto") String texto, @Param("activo") Boolean activo,
            @Param("rol") String rol, Pageable pageable);
}
