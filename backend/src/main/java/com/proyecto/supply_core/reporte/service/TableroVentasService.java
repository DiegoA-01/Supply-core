package com.proyecto.supply_core.reporte.service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.reporte.dto.CajaReporte;
import com.proyecto.supply_core.reporte.dto.TableroResponse;
import com.proyecto.supply_core.reporte.dto.TableroVentas;
import com.proyecto.supply_core.reporte.dto.VentaReporte;
import com.proyecto.supply_core.reporte.enums.AgrupacionVenta;

/**
 * Bloque de ventas del tablero del dueño (spec 009): reutiliza los reportes de ventas y de caja, así que sus cifras son
 * las mismas que se ven en esos reportes para el mismo periodo.
 */
@Service
public class TableroVentasService {

    private final VentaReporteService ventaReporte;
    private final CajaReporteService cajaReporte;

    /**
     * @param ventaReporte ventas netas
     * @param cajaReporte  cierres y diferencias
     */
    public TableroVentasService(VentaReporteService ventaReporte, CajaReporteService cajaReporte) {
        this.ventaReporte = ventaReporte;
        this.cajaReporte = cajaReporte;
    }

    /**
     * @return ventas de hoy y del mes en curso, de toda la empresa
     */
    @Transactional(readOnly = true)
    public TableroVentas tablero() {
        LocalDate hoy = LocalDate.now();
        LocalDate desde = hoy.withDayOfMonth(1);
        VentaReporte.Totales delDia = ventaReporte.deEmpresa(hoy, hoy, AgrupacionVenta.DIA).totales();
        VentaReporte productos = ventaReporte.deEmpresa(desde, hoy, AgrupacionVenta.PRODUCTO);
        VentaReporte medios = ventaReporte.deEmpresa(desde, hoy, AgrupacionVenta.MEDIO_PAGO);
        VentaReporte cajeros = ventaReporte.deEmpresa(desde, hoy, AgrupacionVenta.CAJERO);
        CajaReporte.Totales caja = cajaReporte.reporte(desde, hoy).totales();
        VentaReporte.Totales mes = productos.totales();
        return new TableroVentas(hoy, desde, delDia.neto(), delDia.ventas(), mes.neto(), mes.ventas(), mes.ticketPromedio(),
                mes.margen(), mes.margenPct(), productos.filas().stream().limit(TableroResponse.MAXIMO_LISTA).toList(),
                medios.filas(), cajeros.filas(), caja.diferencia(), caja.faltantes(), caja.porAprobar());
    }
}
