package com.proyecto.supply_core.notificacion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.notificacion.entity.ConfigNotificacion;
import com.proyecto.supply_core.notificacion.enums.CanalNotificacion;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;

/** Acceso a la tabla {@code config_notificacion}. */
public interface ConfigNotificacionRepository extends JpaRepository<ConfigNotificacion, Long> {

    /**
     * Configuración de un evento en un canal.
     *
     * @param evento evento
     * @param canal  canal
     * @return la fila, si existe
     */
    Optional<ConfigNotificacion> findByEventoAndCanal(EventoNotificacion evento, CanalNotificacion canal);

    /**
     * Toda la configuración, ordenada para mostrarla.
     *
     * @return filas por evento y canal
     */
    List<ConfigNotificacion> findAllByOrderByEventoAscCanalAsc();
}
