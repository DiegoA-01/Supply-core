package com.proyecto.supply_core.exportacion.generador;

import static java.util.Map.entry;

import java.util.Locale;
import java.util.Map;

import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;
import com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud;
import com.proyecto.supply_core.inventario.enums.TipoMovimiento;

/**
 * Textos legibles para estados y tipos en los archivos exportados ({@code RECIBIDA_TOTAL} → "Recibida total").
 * <p>Solo cambia cómo se muestran: la API y la base siguen usando los nombres de los enums.</p>
 */
public final class Etiquetas {

    private static final Map<Enum<?>, String> TEXTOS = Map.ofEntries(
            entry(EstadoSolicitud.BORRADOR, "Borrador"),
            entry(EstadoSolicitud.PENDIENTE_APROBACION, "Pendiente de aprobación"),
            entry(EstadoSolicitud.APROBADA, "Aprobada"),
            entry(EstadoSolicitud.DEVUELTA, "Devuelta"),
            entry(EstadoSolicitud.RECHAZADA, "Rechazada"),
            entry(EstadoSolicitud.EN_RFQ, "En cotización (RFQ)"),
            entry(EstadoSolicitud.ADJUDICADA, "Adjudicada"),
            entry(EstadoSolicitud.CANCELADA, "Cancelada"),
            entry(EstadoSolicitud.CERRADA, "Cerrada"),
            entry(TipoMovimiento.ENTRADA_COMPRA, "Entrada por compra"),
            entry(TipoMovimiento.SALIDA_CONSUMO, "Salida por consumo"),
            entry(TipoMovimiento.AJUSTE_POSITIVO, "Ajuste positivo"),
            entry(TipoMovimiento.AJUSTE_NEGATIVO, "Ajuste negativo"),
            entry(TipoMovimiento.DEVOLUCION_PROVEEDOR, "Devolución al proveedor"),
            entry(TipoMovimiento.TRASLADO, "Traslado"),
            entry(TipoMovimiento.SALIDA_VENTA, "Salida por venta"),
            entry(TipoMovimiento.ANULACION_DESPACHO, "Anulación de despacho"),
            entry(TipoMovimiento.ANULACION_VENTA, "Anulación de venta"),
            entry(TipoMovimiento.DEVOLUCION_CLIENTE, "Devolución de cliente"),
            entry(EstadoOrden.EMITIDA, "Emitida"),
            entry(EstadoOrden.ACEPTADA_PROVEEDOR, "Aceptada por el proveedor"),
            entry(EstadoOrden.RECHAZADA_PROVEEDOR, "Rechazada por el proveedor"),
            entry(EstadoOrden.EN_PREPARACION, "En preparación"),
            entry(EstadoOrden.DESPACHADA, "Despachada"),
            entry(EstadoOrden.RECIBIDA_PARCIAL, "Recibida parcial"),
            entry(EstadoOrden.RECIBIDA_TOTAL, "Recibida total"),
            entry(EstadoOrden.CERRADA, "Cerrada"),
            entry(EstadoOrden.CANCELADA, "Cancelada"),
            entry(EstadoOrden.REEMPLAZADA, "Reemplazada"));

    private static final Map<String, String> REFERENCIAS = Map.of(
            "RECEPCION", "Recepción",
            "DESPACHO", "Despacho");

    /**
     * Clase de utilidades: no se instancia.
     */
    private Etiquetas() {
    }

    /**
     * Texto de un estado o tipo; uno sin texto definido sale con la primera letra en mayúscula y espacios.
     *
     * @param valor estado o tipo ({@code null} = vacío)
     * @return texto para mostrar
     */
    public static String de(Enum<?> valor) {
        if (valor == null) {
            return null;
        }
        String texto = TEXTOS.get(valor);
        return texto != null ? texto : generico(valor.name());
    }

    /**
     * Referencia de un movimiento de inventario, p. ej. {@code RECEPCION} y 5 → "Recepción #5".
     *
     * @param tipo tipo de documento de origen ({@code null} = sin referencia)
     * @param id   id del documento
     * @return texto para mostrar o {@code null}
     */
    public static String referencia(String tipo, Long id) {
        if (tipo == null) {
            return null;
        }
        return REFERENCIAS.getOrDefault(tipo, generico(tipo)) + " #" + id;
    }

    /**
     * {@code EN_PREPARACION} → "En preparacion".
     *
     * @param nombre nombre en mayúsculas con guiones bajos
     * @return texto con mayúscula inicial
     */
    private static String generico(String nombre) {
        String texto = nombre.replace('_', ' ').toLowerCase(Locale.ROOT);
        return texto.isEmpty() ? texto : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }
}
