package com.proyecto.supply_core.cartera.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.security.context.Roles;

/**
 * Aviso de crédito bloqueado por mora. Va en su propia transacción: la venta que lo causa se rechaza (y se deshace),
 * pero el aviso a los encargados queda.
 */
@Service
public class AvisoCreditoService {

    private final NotificacionService notificacionService;

    /**
     * @param notificacionService avisos
     */
    public AvisoCreditoService(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    /**
     * @param mensaje texto del aviso
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void avisarBloqueo(String mensaje) {
        notificacionService.notificarRol(Roles.SUPERVISOR_VENTAS, EventoNotificacion.CREDITO_BLOQUEADO, mensaje);
        notificacionService.notificarRol(Roles.ADMIN_SISTEMA, EventoNotificacion.CREDITO_BLOQUEADO, mensaje);
    }
}
