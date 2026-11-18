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
     * Administradores de una empresa, el más antiguo primero. La consola de la plataforma trabaja sin empresa en el
     * contexto, por eso va en SQL nativo con {@code empresa_id} escrito a mano; solo trae nombre y correo.
     *
     * @param empresaId empresa
     * @return usuarios con rol ADMIN_SISTEMA
     */
    @Query(value = "SELECT u.nombre_completo AS nombre, u.correo AS correo FROM usuario u "
            + "JOIN usuario_rol ur ON ur.usuario_id = u.id JOIN rol r ON r.id = ur.rol_id "
            + "WHERE u.empresa_id = :empresaId AND r.codigo = 'ADMIN_SISTEMA' ORDER BY u.id", nativeQuery = true)
    List<AdministradorVista> administradorDe(@Param("empresaId") Long empresaId);

    /**
     * Estado de sesión de un usuario: si sigue activo y su versión de sesión (una consulta liviana por clave).
     *
     * @param id id del usuario
     * @return activo y versión, si existe en la empresa del contexto
     */
    @Query("select new com.proyecto.supply_core.usuario.repository.EstadoSesion(u.activo, u.versionSesion) "
            + "from Usuario u where u.id = :id")
    Optional<EstadoSesion> estadoSesion(@Param("id") Long id);

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
     * Usuarios internos activos, sin contar al indicado: a ellos se les puede delegar una aprobación.
     *
     * @param excluidoId usuario que delega (no se ofrece a sí mismo)
     * @return usuarios por nombre
     */
    @Query("select u from Usuario u where u.activo = true and u.proveedorId is null and u.id <> :excluidoId "
            + "order by u.nombreCompleto")
    List<Usuario> candidatosDeDelegacion(@Param("excluidoId") Long excluidoId);

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
