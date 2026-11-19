package com.proyecto.supply_core.reporte.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.reporte.dto.EstadoCuentaResponse;
import com.proyecto.supply_core.reporte.dto.GastoItem;
import com.proyecto.supply_core.reporte.dto.GastoResponse;
import com.proyecto.supply_core.reporte.dto.InventarioResumen;
import com.proyecto.supply_core.reporte.dto.ProveedorRanking;
import com.proyecto.supply_core.reporte.service.ReporteService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.constraints.Positive;

/**
 * Reportes de gestión (UC-15), solo lectura. Los ven compras, aprobadores, administrativo y auditoría; el inventario
 * lo ve además el almacenista.
 */
@RequiereModulo(Modulos.REPORTES)
@RestController
@RequestMapping("/api/reportes")
@RequiereRol({ Roles.AUDITOR, Roles.ADMIN_COMPRAS, Roles.COMPRADOR, Roles.APROBADOR, Roles.ADMINISTRATIVO })
public class ReporteController {

    private final ReporteService reporteService;

    /**
     * @param reporteService consultas de reporte
     */
    public ReporteController(ReporteService reporteService) {
        this.reporteService = reporteService;
    }

    /**
     * Gasto en órdenes vigentes por proveedor, categoría y mes.
     *
     * @param desde       emisión desde (yyyy-MM-dd), opcional
     * @param hasta       emisión hasta (yyyy-MM-dd), opcional
     * @param categoriaId categoría, opcional
     * @param proveedorId proveedor, opcional
     * @param sedeId      solo órdenes que llegan a bodegas de esta sede, opcional
     * @return gasto del período (ceros y listas vacías si no hay datos)
     */
    @GetMapping("/gasto")
    public GastoResponse gasto(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long categoriaId,
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long proveedorId,
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long sedeId) {
        return reporteService.gasto(desde, hasta, categoriaId, proveedorId, sedeId);
    }

    /**
     * Stock total, valor y alerta de mínimo por producto activo.
     *
     * @param categoriaId    categoría, opcional
     * @param soloBajoMinimo {@code true} deja solo los productos bajo el mínimo
     * @return inventario de la empresa
     */
    @GetMapping("/inventario")
    @RequiereRol({ Roles.AUDITOR, Roles.ADMIN_COMPRAS, Roles.COMPRADOR, Roles.APROBADOR, Roles.ADMINISTRATIVO,
            Roles.ALMACENISTA })
    public InventarioResumen inventario(
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long categoriaId,
            @RequestParam(defaultValue = "false") boolean soloBajoMinimo) {
        return reporteService.inventario(categoriaId, soloBajoMinimo);
    }

    /**
     * Ranking de proveedores con órdenes en el período.
     *
     * @param desde       emisión desde (yyyy-MM-dd), opcional
     * @param hasta       emisión hasta (yyyy-MM-dd), opcional
     * @param categoriaId categoría, opcional
     * @param sedeId      solo órdenes que llegan a bodegas de esta sede, opcional
     * @return ranking por score (lista vacía si no hay órdenes)
     */
    @GetMapping("/proveedores")
    public List<ProveedorRanking> proveedores(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long categoriaId,
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long sedeId) {
        return reporteService.proveedores(desde, hasta, categoriaId, sedeId);
    }

    /**
     * Gasto del período repartido por la sede a la que llegan las órdenes (para comparar sedes).
     *
     * @param desde emisión desde (yyyy-MM-dd), opcional
     * @param hasta emisión hasta (yyyy-MM-dd), opcional
     * @return una fila por sede, de mayor a menor gasto
     */
    @GetMapping("/gasto/sedes")
    public List<GastoItem> gastoPorSede(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return reporteService.gastoPorSede(desde, hasta);
    }

    /**
     * Estado de cuenta de un proveedor: ordenado, facturado, pagado y saldo por orden.
     *
     * @param proveedorId proveedor de la empresa
     * @param desde       emisión desde (yyyy-MM-dd), opcional
     * @param hasta       emisión hasta (yyyy-MM-dd), opcional
     * @return estado de cuenta
     */
    @GetMapping("/proveedores/{proveedorId}/estado-cuenta")
    public EstadoCuentaResponse estadoCuenta(@PathVariable @Positive(message = "debe ser un id válido") Long proveedorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return reporteService.estadoCuenta(proveedorId, desde, hasta);
    }
}
