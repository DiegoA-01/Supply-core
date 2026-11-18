package com.proyecto.supply_core.informe.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.proyecto.supply_core.reporte.dto.GastoResponse;

/**
 * Foto del periodo que arma Java para el informe de compras: qué se pidió, qué se cotizó, qué se adjudicó y cuánto
 * se gastó. Todos los montos los suma Java; la IA no interviene. Las listas llevan como máximo
 * {@link #MAXIMO_FILAS} filas cada una ({@code recortado} avisa si quedó algo por fuera); el resumen cuenta todo.
 *
 * @param desde          primer día del periodo
 * @param hasta          último día del periodo
 * @param generadoEn     momento en que se armó
 * @param resumen        cifras del periodo
 * @param solicitudes    solicitudes enviadas a aprobación
 * @param rfqs           RFQ creadas
 * @param cotizaciones   cotizaciones recibidas de los proveedores
 * @param adjudicaciones adjudicaciones decididas
 * @param gasto          gasto de las órdenes vigentes del periodo (el mismo reporte de gasto)
 * @param recortado      {@code true} si alguna lista superó el máximo de filas
 */
public record InformeContenido(LocalDate desde, LocalDate hasta, LocalDateTime generadoEn, Resumen resumen,
        List<SolicitudFila> solicitudes, List<RfqFila> rfqs, List<CotizacionFila> cotizaciones,
        List<AdjudicacionFila> adjudicaciones, GastoResponse gasto, boolean recortado) {

    /** Filas máximas por lista. */
    public static final int MAXIMO_FILAS = 200;

    /**
     * Cifras del periodo.
     *
     * @param solicitudes                         solicitudes enviadas
     * @param montoSolicitado                     suma de sus montos estimados (las que tienen monto)
     * @param solicitudesPorEstado                cuántas hay en cada estado
     * @param rfqs                                RFQ creadas
     * @param cotizacionesRecibidas               cotizaciones enviadas por proveedores
     * @param adjudicaciones                      adjudicaciones decididas
     * @param adjudicacionesFueraDeRecomendacion  las que no siguieron la recomendación (con justificación)
     * @param montoAdjudicado                     suma de lo adjudicado
     */
    public record Resumen(long solicitudes, BigDecimal montoSolicitado, Map<String, Long> solicitudesPorEstado,
            long rfqs, long cotizacionesRecibidas, long adjudicaciones, long adjudicacionesFueraDeRecomendacion,
            BigDecimal montoAdjudicado) {
    }

    /**
     * Solicitud del periodo.
     *
     * @param consecutivo     número de la solicitud
     * @param solicitante     quién la pidió
     * @param centroCosto     centro de costo
     * @param estado          estado actual
     * @param montoEstimado   monto estimado ({@code null} si no tiene)
     * @param montoIncompleto si algún producto no tenía costo de referencia
     * @param motivo          justificación del solicitante
     * @param enviadoEn       fecha de envío a aprobación
     */
    public record SolicitudFila(String consecutivo, String solicitante, String centroCosto, String estado,
            BigDecimal montoEstimado, boolean montoIncompleto, String motivo, LocalDateTime enviadoEn) {
    }

    /**
     * RFQ del periodo.
     *
     * @param consecutivo  número de la RFQ
     * @param solicitud    solicitud de la que salió
     * @param estado       estado actual
     * @param invitados    proveedores invitados
     * @param cotizaciones cotizaciones recibidas
     * @param fechaLimite  fecha límite para cotizar
     */
    public record RfqFila(String consecutivo, String solicitud, String estado, int invitados, long cotizaciones,
            LocalDateTime fechaLimite) {
    }

    /**
     * Cotización recibida en el periodo.
     *
     * @param rfq              RFQ que responde
     * @param proveedor        proveedor
     * @param estado           estado actual
     * @param total            total calculado por Java ({@code null} si llegó solo el archivo)
     * @param plazoEntregaDias plazo ofrecido
     * @param enviadoEn        fecha de envío
     */
    public record CotizacionFila(String rfq, String proveedor, String estado, BigDecimal total,
            Integer plazoEntregaDias, LocalDateTime enviadoEn) {
    }

    /**
     * Adjudicación del periodo.
     *
     * @param rfq                RFQ adjudicada
     * @param proveedor          proveedor elegido
     * @param montoTotal         monto adjudicado
     * @param sigueRecomendacion si eligió la recomendada
     * @param justificacion      por qué se apartó de la recomendación
     * @param estado             estado de la adjudicación
     * @param decididoEn         fecha de la decisión
     */
    public record AdjudicacionFila(String rfq, String proveedor, BigDecimal montoTotal, boolean sigueRecomendacion,
            String justificacion, String estado, LocalDateTime decididoEn) {
    }
}
