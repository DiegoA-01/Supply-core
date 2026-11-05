package com.proyecto.supply_core.notificacion.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.notificacion.entity.Notificacion;

/** Acceso a la tabla {@code notificacion}. Toda consulta va filtrada por destinatario. */
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    /**
     * Notificaciones de un usuario.
     *
     * @param usuarioId destinatario
     * @param pageable  página y orden
     * @return página de notificaciones
     */
    Page<Notificacion> findByUsuarioId(Long usuarioId, Pageable pageable);

    /**
     * Notificaciones no leídas de un usuario.
     *
     * @param usuarioId destinatario
     * @param pageable  página y orden
     * @return página de notificaciones sin leer
     */
    Page<Notificacion> findByUsuarioIdAndLeidaFalse(Long usuarioId, Pageable pageable);

    /**
     * Cuenta las no leídas (contador de la campana del frontend; usa el índice usuario+leida).
     *
     * @param usuarioId destinatario
     * @return cantidad sin leer
     */
    long countByUsuarioIdAndLeidaFalse(Long usuarioId);

    /**
     * Busca una notificación solo si pertenece al usuario.
     *
     * @param id        id de la notificación
     * @param usuarioId destinatario esperado
     * @return la notificación, si es suya
     */
    Optional<Notificacion> findByIdAndUsuarioId(Long id, Long usuarioId);

    /**
     * Marca como leídas todas las pendientes del usuario en una sola sentencia.
     *
     * @param usuarioId destinatario
     * @return cantidad marcada
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notificacion n set n.leida = true where n.usuarioId = :usuarioId and n.leida = false")
    int marcarTodasLeidas(@Param("usuarioId") Long usuarioId);
}
