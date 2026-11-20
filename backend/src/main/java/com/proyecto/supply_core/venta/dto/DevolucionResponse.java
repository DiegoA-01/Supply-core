package com.proyecto.supply_core.venta.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.proyecto.supply_core.caja.enums.MedioPago;
import com.proyecto.supply_core.venta.enums.FormaReembolso;

/**
 * Devolución de cliente.
 *
 * @param id               id
 * @param consecutivo      consecutivo ({@code DV-000001})
 * @param ventaId          venta original
 * @param venta            consecutivo de la venta original
 * @param cliente          cliente
 * @param motivo           motivo
 * @param formaReembolso   forma de reembolso
 * @param medioReembolso   medio por el que salió el dinero ({@code null} si quedó como saldo a favor)
 * @param totalReembolso   total devuelto
 * @param usuario          quién la registró
 * @param autorizadoPor    supervisor que la autorizó
 * @param creadoEn         fecha
 * @param lineas           líneas
 */
public record DevolucionResponse(Long id, String consecutivo, Long ventaId, String venta, String cliente, String motivo,
        FormaReembolso formaReembolso, MedioPago medioReembolso, BigDecimal totalReembolso, String usuario,
        String autorizadoPor, LocalDateTime creadoEn, List<DevolucionLineaResponse> lineas) {
}
