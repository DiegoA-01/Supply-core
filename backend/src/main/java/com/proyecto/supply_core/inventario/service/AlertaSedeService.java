package com.proyecto.supply_core.inventario.service;

import java.math.BigDecimal;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.proyecto.supply_core.empresa.entity.Sede;
import com.proyecto.supply_core.inventario.entity.Bodega;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.entity.ProductoSede;
import com.proyecto.supply_core.inventario.repository.ProductoSedeRepository;
import com.proyecto.supply_core.inventario.repository.StockProductoBodegaRepository;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.security.context.Roles;

/**
 * Aviso inmediato cuando una sede se queda corta de un producto, aunque el total de la empresa alcance.
 * <ul>
 *   <li>Si la sede tiene mínimo propio: avisa al bajar de él.</li>
 *   <li>Si no lo tiene: avisa cuando la sede se queda en cero.</li>
 * </ul>
 * Se avisa una sola vez, al cruzar el umbral, al comprador (para comprar) y al almacenista (para trasladar). El
 * mensaje dice cuánto hay en las otras sedes. Lo llama {@link MovimientoInventarioService} en la misma transacción.
 */
@Service
public class AlertaSedeService {

    private final StockProductoBodegaRepository stockRepository;
    private final ProductoSedeRepository productoSedeRepository;
    private final NotificacionService notificacionService;

    /**
     * @param stockRepository        stock por sede
     * @param productoSedeRepository mínimos propios de cada sede
     * @param notificacionService    avisos
     */
    public AlertaSedeService(StockProductoBodegaRepository stockRepository,
            ProductoSedeRepository productoSedeRepository, NotificacionService notificacionService) {
        this.stockRepository = stockRepository;
        this.productoSedeRepository = productoSedeRepository;
        this.notificacionService = notificacionService;
    }

    /**
     * Revisa la sede de la bodega de la que salió mercancía.
     *
     * @param producto producto movido
     * @param origen   bodega de la que salió ({@code null} si el movimiento no saca de ninguna)
     * @param destino  bodega a la que entró ({@code null} si no entra a ninguna)
     * @param cantidad cantidad que salió
     * @return {@code true} si se envió un aviso
     */
    public boolean revisar(Producto producto, Bodega origen, Bodega destino, BigDecimal cantidad) {
        if (origen == null || origen.getSede() == null) {
            return false;
        }
        Sede sede = origen.getSede();
        if (destino != null && destino.getSede() != null && sede.getId().equals(destino.getSede().getId())) {
            return false; // traslado dentro de la misma sede: el total de la sede no cambia
        }
        BigDecimal ahora = stockRepository.totalDeProductoEnSede(producto.getId(), sede.getId());
        BigDecimal antes = ahora.add(cantidad);
        BigDecimal minimo = productoSedeRepository.deProductoEnSede(producto.getId(), sede.getId())
                .map(ProductoSede::getStockMinimo).orElse(BigDecimal.ZERO);
        boolean cruzo = minimo.signum() > 0
                ? ahora.compareTo(minimo) < 0 && antes.compareTo(minimo) >= 0
                : ahora.signum() == 0 && antes.signum() > 0;
        if (!cruzo) {
            return false;
        }
        String otras = stockRepository.stockPorSede(producto.getId()).stream()
                .filter(s -> !s.sedeId().equals(sede.getId()) && s.cantidad().signum() > 0)
                .map(s -> s.sede() + " tiene " + s.cantidad().stripTrailingZeros().toPlainString())
                .collect(Collectors.joining(", "));
        String estado = minimo.signum() > 0
                ? "quedó bajo su mínimo: " + ahora.stripTrailingZeros().toPlainString() + " < "
                        + minimo.stripTrailingZeros().toPlainString()
                : "se quedó sin existencias";
        String mensaje = sede.getNombre() + ": " + producto.getCodigo() + " - " + producto.getNombre() + " " + estado
                + ". " + (otras.isEmpty() ? "Ninguna otra sede tiene: hay que comprar."
                        : "Para trasladar: " + otras + ".");
        notificacionService.notificarRol(Roles.COMPRADOR, EventoNotificacion.STOCK_BAJO_SEDE, mensaje);
        notificacionService.notificarRol(Roles.ALMACENISTA, EventoNotificacion.STOCK_BAJO_SEDE, mensaje);
        return true;
    }
}
