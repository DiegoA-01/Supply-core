package com.proyecto.supply_core.notificacion.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.notificacion.dto.CantidadResponse;
import com.proyecto.supply_core.notificacion.dto.NotificacionResponse;
import com.proyecto.supply_core.notificacion.service.NotificacionService;

import jakarta.validation.constraints.Positive;

/**
 * Bandeja de notificaciones del usuario de la sesión (internos y proveedores).
 * Cada usuario solo ve y marca las suyas; no se crean ni se borran por la API.
 */
@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {

    private final NotificacionService notificacionService;

    /**
     * @param notificacionService lógica de notificaciones
     */
    public NotificacionController(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    /**
     * Mis notificaciones, las más recientes primero.
     *
     * @param soloNoLeidas {@code true} para ver solo las pendientes
     * @param pageable     página (máximo 100) y orden
     * @return página de notificaciones
     */
    @GetMapping
    public PageResponse<NotificacionResponse> mias(@RequestParam(defaultValue = "false") boolean soloNoLeidas,
            @PageableDefault(size = 20, sort = "creadoEn", direction = Sort.Direction.DESC) Pageable pageable) {
        return notificacionService.mias(soloNoLeidas, pageable);
    }

    /**
     * Cantidad de notificaciones sin leer (para la campana del frontend).
     *
     * @return contador
     */
    @GetMapping("/no-leidas")
    public CantidadResponse noLeidas() {
        return notificacionService.noLeidas();
    }

    /**
     * Marca una notificación propia como leída.
     *
     * @param id id positivo
     * @return notificación actualizada
     */
    @PatchMapping("/{id}/leida")
    public NotificacionResponse marcarLeida(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return notificacionService.marcarLeida(id);
    }

    /**
     * Marca todas mis notificaciones como leídas.
     *
     * @return cantidad marcada
     */
    @PatchMapping("/leidas")
    public CantidadResponse marcarTodasLeidas() {
        return notificacionService.marcarTodasLeidas();
    }
}
