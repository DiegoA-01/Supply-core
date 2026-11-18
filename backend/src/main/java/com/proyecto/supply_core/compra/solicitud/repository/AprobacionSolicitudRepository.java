package com.proyecto.supply_core.compra.solicitud.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.compra.solicitud.entity.AprobacionSolicitud;

/** Acceso a la tabla {@code aprobacion_solicitud}. */
public interface AprobacionSolicitudRepository extends JpaRepository<AprobacionSolicitud, Long> {

    /**
     * Historial de decisiones de una solicitud, de la más antigua a la más reciente.
     *
     * @param solicitudCompraId solicitud
     * @return decisiones
     */
    List<AprobacionSolicitud> findBySolicitudCompraIdOrderByDecididoEnAscIdAsc(Long solicitudCompraId);
}
