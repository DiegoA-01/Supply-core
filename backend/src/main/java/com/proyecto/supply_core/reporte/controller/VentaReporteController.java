package com.proyecto.supply_core.reporte.controller;

import java.time.LocalDate;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.reporte.dto.CajaReporte;
import com.proyecto.supply_core.reporte.dto.CuadreResponse;
import com.proyecto.supply_core.reporte.dto.TableroVentas;
import com.proyecto.supply_core.reporte.dto.VentaReporte;
import com.proyecto.supply_core.reporte.enums.AgrupacionVenta;
import com.proyecto.supply_core.reporte.service.CajaReporteService;
import com.proyecto.supply_core.reporte.service.CuadreDiarioService;
import com.proyecto.supply_core.reporte.service.TableroVentasService;
import com.proyecto.supply_core.reporte.service.VentaReporteService;
import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.security.context.Roles;

/**
 * Reportes de ventas y caja, bloque de ventas del tablero y cuadre diario (spec 009). Solo lectura, salvo volver a
 * revisar un día del cuadre.
 */
@RequiereModulo(Modulos.VENTAS)
@RestController
@RequestMapping("/api/reportes")
@UsuarioInterno
public class VentaReporteController {

    private final VentaReporteService ventaReporte;
    private final CajaReporteService cajaReporte;
    private final TableroVentasService tablero;
    private final CuadreDiarioService cuadre;

    /**
     * @param ventaReporte ventas netas
     * @param cajaReporte  caja
     * @param tablero      bloque del tablero
     * @param cuadre       cuadre diario
     */
    public VentaReporteController(VentaReporteService ventaReporte, CajaReporteService cajaReporte,
            TableroVentasService tablero, CuadreDiarioService cuadre) {
        this.ventaReporte = ventaReporte;
        this.cajaReporte = cajaReporte;
        this.tablero = tablero;
        this.cuadre = cuadre;
    }

    /**
     * Ventas netas del periodo. El cajero y el vendedor reciben solo lo suyo.
     *
     * @param desde   primer día (vacío = primer día del mes)
     * @param hasta   último día (vacío = hoy)
     * @param agrupar agrupación (vacío = por día)
     * @return reporte
     */
    @GetMapping("/ventas")
    @RequiereRol({ Roles.SUPERVISOR_VENTAS, Roles.ADMIN_SISTEMA, Roles.AUDITOR, Roles.CAJERO, Roles.VENDEDOR })
    public VentaReporte ventas(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) AgrupacionVenta agrupar) {
        return ventaReporte.reporte(desde, hasta, agrupar);
    }

    /**
     * Cierres, diferencias, retiros, gastos, anulaciones y descuentos autorizados del periodo.
     *
     * @param desde primer día (vacío = primer día del mes)
     * @param hasta último día (vacío = hoy)
     * @return reporte
     */
    @GetMapping("/caja")
    @RequiereRol({ Roles.SUPERVISOR_VENTAS, Roles.ADMIN_SISTEMA, Roles.AUDITOR })
    public CajaReporte caja(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return cajaReporte.reporte(desde, hasta);
    }

    /**
     * @return bloque de ventas del tablero del dueño (hoy y mes en curso)
     */
    @GetMapping("/tablero/ventas")
    @RequiereRol({ Roles.ADMIN_SISTEMA, Roles.AUDITOR })
    public TableroVentas tablero() {
        return tablero.tablero();
    }

    /**
     * @param soloDescuadres {@code true} para ver solo los días que no cuadran
     * @param pageable       página (por defecto el día más reciente primero)
     * @return días revisados por el cuadre
     */
    @GetMapping("/cuadre")
    @RequiereRol({ Roles.ADMIN_SISTEMA, Roles.AUDITOR })
    public PageResponse<CuadreResponse> cuadres(@RequestParam(defaultValue = "false") boolean soloDescuadres,
            @PageableDefault(size = 31, sort = "fecha", direction = Sort.Direction.DESC) Pageable pageable) {
        return cuadre.listar(soloDescuadres, pageable);
    }

    /**
     * Vuelve a revisar un día, por ejemplo después de corregir lo que no cuadraba.
     *
     * @param fecha día a revisar
     * @return resultado nuevo
     */
    @PostMapping("/cuadre/{fecha}")
    @RequiereRol(Roles.ADMIN_SISTEMA)
    public CuadreResponse revisar(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return cuadre.revisar(fecha);
    }
}
