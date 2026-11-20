package com.proyecto.supply_core.venta.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.venta.enums.MedioPagoVenta;

/**
 * Cálculo del dinero de una venta (spec 004), sin base de datos para poder probarlo solo. La pantalla nunca calcula:
 * todo sale de aquí.
 * <ul>
 *   <li>Línea: bruto = precio de lista × cantidad; el descuento se aplica sobre el bruto; si el precio incluye IVA
 *       (lo normal en mostrador, decisión D2) la base se separa del total; si no, el IVA se suma a la base.</li>
 *   <li>Venta: el total es la suma de los totales de las líneas, así que siempre cuadra con ellas.</li>
 *   <li>Redondeo (D3): si todo se paga en efectivo, lo que se cobra se lleva al múltiplo de $50 más cercano y la
 *       diferencia queda aparte para que la caja cuadre.</li>
 *   <li>Pagos: la suma debe cubrir lo que se cobra; solo el efectivo puede pasarse y el exceso es el cambio.</li>
 * </ul>
 * Todos los valores de dinero quedan con 2 decimales, redondeo comercial (mitad hacia arriba).
 */
public final class CalculoVenta {

    /** Múltiplo al que se redondea el efectivo (decisión D3). */
    static final BigDecimal MULTIPLO_EFECTIVO = new BigDecimal("50");
    private static final BigDecimal CIEN = new BigDecimal("100");

    /** No se instancia: solo métodos estáticos. */
    private CalculoVenta() {
    }

    /**
     * Datos de una línea para calcular.
     *
     * @param precioLista      precio vigente de la lista en la unidad vendida
     * @param cantidad         cantidad vendida (> 0)
     * @param descuentoPct     descuento en porcentaje (0 a 100)
     * @param tarifaPct        tarifa de impuesto en porcentaje (0 si no tiene)
     * @param incluyeImpuesto  {@code true} si el precio de lista ya trae el impuesto
     */
    public record Linea(BigDecimal precioLista, BigDecimal cantidad, BigDecimal descuentoPct, BigDecimal tarifaPct,
            boolean incluyeImpuesto) {
    }

    /**
     * Resultado de una línea.
     *
     * @param precioUnitario precio por unidad después del descuento
     * @param bruto          precio de lista × cantidad
     * @param descuento      valor del descuento
     * @param base           base gravable
     * @param impuesto       impuesto
     * @param total          total de la línea (base + impuesto)
     */
    public record ResultadoLinea(BigDecimal precioUnitario, BigDecimal bruto, BigDecimal descuento, BigDecimal base,
            BigDecimal impuesto, BigDecimal total) {
    }

    /**
     * Totales de la venta.
     *
     * @param subtotal  suma de los brutos (precios de lista)
     * @param descuento suma de los descuentos
     * @param base      suma de las bases gravables
     * @param impuestos suma de los impuestos
     * @param total     suma de los totales de las líneas
     */
    public record Totales(BigDecimal subtotal, BigDecimal descuento, BigDecimal base, BigDecimal impuestos,
            BigDecimal total) {
    }

    /**
     * Un pago que manda la pantalla.
     *
     * @param medio medio de pago
     * @param monto en efectivo, lo que entrega el cliente; en los demás, lo que se cobra por ese medio
     */
    public record Pago(MedioPagoVenta medio, BigDecimal monto) {
    }

    /**
     * Resultado de los pagos.
     *
     * @param cambio   lo que se devuelve al cliente (solo sale del efectivo)
     * @param porMedio lo que queda en la caja por cada medio (en efectivo, recibido menos cambio)
     */
    public record ResultadoPagos(BigDecimal cambio, Map<MedioPagoVenta, BigDecimal> porMedio) {
    }

    /**
     * @param l datos de la línea
     * @return precio unitario, bruto, descuento, base, impuesto y total de la línea
     */
    public static ResultadoLinea linea(Linea l) {
        BigDecimal factor = CIEN.subtract(l.descuentoPct()).divide(CIEN);
        BigDecimal bruto = dinero(l.precioLista().multiply(l.cantidad()));
        BigDecimal neto = dinero(l.precioLista().multiply(l.cantidad()).multiply(factor));
        BigDecimal descuento = bruto.subtract(neto);
        BigDecimal unitario = dinero(l.precioLista().multiply(factor));
        BigDecimal tarifa = l.tarifaPct() == null ? BigDecimal.ZERO : l.tarifaPct();
        BigDecimal base;
        BigDecimal impuesto;
        BigDecimal total;
        if (l.incluyeImpuesto()) {
            total = neto;
            base = total.multiply(CIEN).divide(CIEN.add(tarifa), 2, RoundingMode.HALF_UP);
            impuesto = total.subtract(base);
        } else {
            base = neto;
            impuesto = dinero(base.multiply(tarifa).divide(CIEN));
            total = base.add(impuesto);
        }
        return new ResultadoLinea(unitario, bruto, descuento, base, impuesto, total);
    }

    /**
     * @param lineas líneas ya calculadas
     * @return totales de la venta (el total es la suma exacta de las líneas)
     */
    public static Totales totales(List<ResultadoLinea> lineas) {
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
        BigDecimal descuento = subtotal;
        BigDecimal base = subtotal;
        BigDecimal impuestos = subtotal;
        BigDecimal total = subtotal;
        for (ResultadoLinea r : lineas) {
            subtotal = subtotal.add(r.bruto());
            descuento = descuento.add(r.descuento());
            base = base.add(r.base());
            impuestos = impuestos.add(r.impuesto());
            total = total.add(r.total());
        }
        return new Totales(subtotal, descuento, base, impuestos, total);
    }

    /**
     * Redondeo del efectivo (decisión D3).
     *
     * @param total        total de la venta
     * @param soloEfectivo {@code true} si todos los pagos son en efectivo
     * @return ajuste que se suma al total para llegar al múltiplo de $50 más cercano (0 si hay otro medio)
     */
    public static BigDecimal redondeo(BigDecimal total, boolean soloEfectivo) {
        if (!soloEfectivo) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal cobrado = total.divide(MULTIPLO_EFECTIVO, 0, RoundingMode.HALF_UP).multiply(MULTIPLO_EFECTIVO);
        if (cobrado.signum() <= 0) {
            // Una venta de menos de $25 no se redondea a cero: se cobra exacta
            return BigDecimal.ZERO.setScale(2);
        }
        return cobrado.subtract(total).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Revisa los pagos y calcula el cambio.
     *
     * @param aPagar lo que se cobra (total más redondeo)
     * @param pagos  pagos que manda la pantalla
     * @return cambio y lo que queda en la caja por medio
     * @throws NegocioException {@code PAGO_INSUFICIENTE} si no alcanzan; {@code PAGO_EXCEDE} si lo que se pasa no
     *                          es efectivo o el efectivo sobra completo (el crédito y el saldo a favor nunca se pasan)
     */
    public static ResultadoPagos pagos(BigDecimal aPagar, List<Pago> pagos) {
        Map<MedioPagoVenta, BigDecimal> porMedio = new EnumMap<>(MedioPagoVenta.class);
        for (Pago p : pagos) {
            porMedio.merge(p.medio(), dinero(p.monto()), BigDecimal::add);
        }
        BigDecimal efectivo = porMedio.getOrDefault(MedioPagoVenta.EFECTIVO, BigDecimal.ZERO);
        BigDecimal otros = porMedio.entrySet().stream().filter(e -> e.getKey() != MedioPagoVenta.EFECTIVO)
                .map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal suma = efectivo.add(otros);
        if (suma.compareTo(aPagar) < 0) {
            throw new NegocioException("PAGO_INSUFICIENTE", "Los pagos suman " + suma.toPlainString()
                    + " y la venta es de " + aPagar.toPlainString());
        }
        if (otros.compareTo(aPagar) > 0 || (otros.compareTo(aPagar) == 0 && efectivo.signum() > 0)) {
            throw new NegocioException("PAGO_EXCEDE",
                    "Solo el efectivo puede pasarse del total: ajuste el valor de la tarjeta, la transferencia o la billetera");
        }
        BigDecimal cambio = suma.subtract(aPagar).setScale(2, RoundingMode.HALF_UP);
        if (efectivo.signum() > 0) {
            porMedio.put(MedioPagoVenta.EFECTIVO, efectivo.subtract(cambio));
        }
        return new ResultadoPagos(cambio, porMedio);
    }

    /**
     * @param valor valor
     * @return el valor con 2 decimales, mitad hacia arriba
     */
    static BigDecimal dinero(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}
