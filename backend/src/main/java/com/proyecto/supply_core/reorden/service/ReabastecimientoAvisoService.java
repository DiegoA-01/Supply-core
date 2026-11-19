package com.proyecto.supply_core.reorden.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.reorden.dto.ReabastecimientoItem;
import com.proyecto.supply_core.reorden.dto.ReabastecimientoResponse;
import com.proyecto.supply_core.reorden.enums.EstadoReabastecimiento;
import com.proyecto.supply_core.security.context.Roles;

/**
 * Resumen diario del plan de reabastecimiento: si hay productos urgentes o que hay que pedir ya, avisa al COMPRADOR
 * y al ADMIN_COMPRAS (bandeja y correo, según la configuración de avisos de la empresa). Si no hay nada que pedir,
 * no envía nada. El aviso inmediato al cruzar el mínimo sigue funcionando aparte.
 */
@Service
public class ReabastecimientoAvisoService {

    /** Cuántos productos se nombran en el mensaje; el resto se resume con "y N más". */
    static final int MAXIMO_EN_MENSAJE = 10;

    private final ReabastecimientoService reabastecimientoService;
    private final NotificacionService notificacionService;

    /**
     * @param reabastecimientoService plan de reabastecimiento
     * @param notificacionService     envío de avisos
     */
    public ReabastecimientoAvisoService(ReabastecimientoService reabastecimientoService,
            NotificacionService notificacionService) {
        this.reabastecimientoService = reabastecimientoService;
        this.notificacionService = notificacionService;
    }

    /**
     * Calcula el plan de la empresa actual y avisa si hay algo que pedir hoy.
     *
     * @return productos incluidos en el aviso (0 si no se envió nada)
     */
    @Transactional
    public int avisar() {
        Map<String, ReabastecimientoResponse> porSede = reabastecimientoService.planesPorSede();
        if (!porSede.isEmpty()) {
            return avisarPorSede(porSede);
        }
        ReabastecimientoResponse plan = reabastecimientoService.plan();
        List<ReabastecimientoItem> pedir = plan.items().stream()
                .filter(i -> i.estado() == EstadoReabastecimiento.URGENTE
                        || i.estado() == EstadoReabastecimiento.PEDIR_YA)
                .toList();
        if (pedir.isEmpty()) {
            return 0;
        }
        String mensaje = mensaje(plan, pedir);
        notificacionService.notificarRol(Roles.COMPRADOR, EventoNotificacion.REABASTECIMIENTO_DIARIO, mensaje);
        notificacionService.notificarRol(Roles.ADMIN_COMPRAS, EventoNotificacion.REABASTECIMIENTO_DIARIO, mensaje);
        return pedir.size();
    }

    /**
     * Resumen de una empresa con varias sedes: un solo aviso que dice, sede por sede, qué hay que pedir y qué se puede
     * trasladar desde otra sede antes de comprar.
     *
     * @param porSede plan con traslados de cada sede
     * @return productos incluidos en el aviso (0 si no se envió nada)
     */
    private int avisarPorSede(Map<String, ReabastecimientoResponse> porSede) {
        List<String> partes = new ArrayList<>();
        int total = 0;
        for (Map.Entry<String, ReabastecimientoResponse> e : porSede.entrySet()) {
            List<ReabastecimientoItem> pedir = e.getValue().items().stream()
                    .filter(i -> i.estado() == EstadoReabastecimiento.URGENTE
                            || i.estado() == EstadoReabastecimiento.PEDIR_YA)
                    .toList();
            if (!pedir.isEmpty()) {
                total += pedir.size();
                partes.add(e.getKey() + ": " + lineaDeSede(pedir));
            }
        }
        if (total == 0) {
            return 0;
        }
        String mensaje = "Hoy hay que reponer " + total + " producto(s) por sede para no quedarse sin stock mientras "
                + "llega el pedido. " + String.join(" | ", partes) + ". Revisa el plan de reabastecimiento por sede.";
        notificacionService.notificarRol(Roles.COMPRADOR, EventoNotificacion.REABASTECIMIENTO_DIARIO, mensaje);
        notificacionService.notificarRol(Roles.ADMIN_COMPRAS, EventoNotificacion.REABASTECIMIENTO_DIARIO, mensaje);
        notificacionService.notificarRol(Roles.ALMACENISTA, EventoNotificacion.REABASTECIMIENTO_DIARIO, mensaje);
        return total;
    }

    /**
     * Productos de una sede para el resumen, con traslado y compra cuando aplica.
     *
     * @param pedir productos urgentes o por pedir hoy de la sede
     * @return texto de la sede
     */
    static String lineaDeSede(List<ReabastecimientoItem> pedir) {
        String lista = pedir.stream().limit(MAXIMO_EN_MENSAJE).map(i -> {
            String unidad = " " + i.unidad();
            String accion = i.cantidadTraslado().signum() > 0
                    ? "trasladar " + numero(i.cantidadTraslado()) + unidad + " desde " + i.trasladoDesdeSede()
                            + (i.cantidadComprar().signum() > 0 ? " y comprar " + numero(i.cantidadComprar()) + unidad : "")
                    : "comprar " + numero(i.cantidadComprar()) + unidad;
            return (i.estado() == EstadoReabastecimiento.URGENTE ? "URGENTE " : "") + i.codigo() + " (" + accion + ")";
        }).collect(Collectors.joining("; "));
        return lista + (pedir.size() > MAXIMO_EN_MENSAJE ? " y " + (pedir.size() - MAXIMO_EN_MENSAJE) + " más" : "");
    }

    /**
     * @param valor cantidad
     * @return la cantidad sin ceros a la derecha
     */
    private static String numero(java.math.BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString();
    }

    /**
     * Texto del aviso.
     *
     * @param plan  plan completo (para los conteos)
     * @param pedir productos urgentes o por pedir ya, de más a menos urgente
     * @return mensaje para la bandeja y el correo
     */
    static String mensaje(ReabastecimientoResponse plan, List<ReabastecimientoItem> pedir) {
        String lista = pedir.stream().limit(MAXIMO_EN_MENSAJE)
                .map(i -> (i.estado() == EstadoReabastecimiento.URGENTE ? "URGENTE " : "") + i.codigo() + " - "
                        + i.nombre() + " (pedir " + i.cantidadSugerida().stripTrailingZeros().toPlainString() + " "
                        + i.unidad() + ")")
                .collect(Collectors.joining("; "));
        String resto = pedir.size() > MAXIMO_EN_MENSAJE ? " y " + (pedir.size() - MAXIMO_EN_MENSAJE) + " más" : "";
        return "Hoy hay que pedir " + pedir.size() + " producto(s) para no quedarse sin stock mientras llega el "
                + "pedido (" + plan.urgentes() + " urgente(s)): " + lista + resto
                + ". Revisa el plan de reabastecimiento para crear la solicitud.";
    }
}
