package com.proyecto.supply_core.reporte.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.proyecto.supply_core.reporte.dto.VentaReporte;
import com.proyecto.supply_core.reporte.enums.AgrupacionVenta;

/**
 * Suma las líneas vendidas y devueltas de un periodo en el reporte de ventas netas (spec 009). Es cálculo puro: no lee
 * la base, así que se prueba sin ella. Todo el dinero va con 2 decimales y redondeo comercial.
 */
public final class AgregadorVentas {

    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal CERO_UNIDADES = BigDecimal.ZERO.setScale(4);

    /** Texto de las ventas que no vienen de un pedido de vendedor. */
    public static final String VENTA_DIRECTA = "Venta directa";
    /** Clave del dinero devuelto a la deuda del cliente o a su saldo a favor (no sale de la caja). */
    public static final String A_CUENTA = "A_CUENTA";

    private AgregadorVentas() {
    }

    /**
     * Una línea vendida, o la parte devuelta de una línea, con los datos de la venta original.
     *
     * @param ventaId     venta
     * @param fecha       día que cuenta (de la venta o de la devolución)
     * @param cajaId      caja
     * @param caja        nombre de la caja
     * @param cajeroId    cajero
     * @param cajero      nombre del cajero
     * @param vendedorId  vendedor del pedido ({@code null} = venta directa)
     * @param vendedor    nombre del vendedor
     * @param clienteId   cliente
     * @param cliente     nombre del cliente
     * @param productoId  producto
     * @param producto    código y nombre del producto
     * @param categoriaId categoría
     * @param categoria   nombre de la categoría
     * @param unidades    unidades en la unidad del producto
     * @param total       valor con IVA
     * @param base        valor sin IVA
     * @param costo       costo de esas unidades ({@code null} si la línea no tenía costo)
     * @param devolucion  {@code true} si es una devolución (resta)
     */
    public record Linea(long ventaId, LocalDate fecha, Long cajaId, String caja, Long cajeroId, String cajero,
            Long vendedorId, String vendedor, Long clienteId, String cliente, Long productoId, String producto,
            Long categoriaId, String categoria, BigDecimal unidades, BigDecimal total, BigDecimal base, BigDecimal costo,
            boolean devolucion) {
    }

    /**
     * Dinero que entró por un medio de pago, o que salió por ese medio en una devolución.
     *
     * @param ventaId    venta
     * @param fecha      día que cuenta
     * @param medio      código del medio ({@link #A_CUENTA} para lo devuelto a la deuda o al saldo a favor)
     * @param nombre     texto del medio
     * @param monto      valor (positivo)
     * @param devolucion {@code true} si es una devolución (resta)
     */
    public record Cobro(long ventaId, LocalDate fecha, String medio, String nombre, BigDecimal monto, boolean devolucion) {
    }

    /** Acumulado de un renglón. */
    private static final class Acumulado {
        private final String clave;
        private final String nombre;
        private final Set<Long> ventas = new HashSet<>();
        private BigDecimal unidades = CERO_UNIDADES;
        private BigDecimal bruto = CERO;
        private BigDecimal devoluciones = CERO;
        private BigDecimal baseNeta = CERO;
        private BigDecimal baseConCosto = CERO;
        private BigDecimal costo = CERO;

        /**
         * @param clave  clave del renglón
         * @param nombre texto del renglón
         */
        private Acumulado(String clave, String nombre) {
            this.clave = clave;
            this.nombre = nombre;
        }

        /** @param l línea que se suma (o resta si es devolución) */
        private void sumar(Linea l) {
            int signo = l.devolucion() ? -1 : 1;
            if (l.devolucion()) {
                devoluciones = devoluciones.add(dinero(l.total()));
            } else {
                ventas.add(l.ventaId());
                bruto = bruto.add(dinero(l.total()));
            }
            unidades = unidades.add(l.unidades().multiply(BigDecimal.valueOf(signo))).setScale(4, RoundingMode.HALF_UP);
            BigDecimal base = dinero(l.base()).multiply(BigDecimal.valueOf(signo));
            baseNeta = baseNeta.add(base);
            if (l.costo() != null) {
                baseConCosto = baseConCosto.add(base);
                costo = costo.add(dinero(l.costo()).multiply(BigDecimal.valueOf(signo)));
            }
        }

        /** @return el renglón terminado */
        private VentaReporte.Fila fila() {
            BigDecimal margen = baseConCosto.subtract(costo);
            return new VentaReporte.Fila(clave, nombre, ventas.size(), unidades, bruto, devoluciones,
                    bruto.subtract(devoluciones), baseNeta, costo, margen, porcentaje(margen, baseConCosto),
                    baseNeta.subtract(baseConCosto));
        }
    }

    /**
     * Arma el reporte.
     *
     * @param desde             primer día
     * @param hasta             último día
     * @param agrupar           agrupación pedida
     * @param lineas            líneas vendidas y devueltas del periodo
     * @param cobros            cobros por medio y reembolsos del periodo (solo se usan al agrupar por medio)
     * @param redondeo          redondeo cobrado en las ventas del periodo menos el devuelto en sus devoluciones
     * @param soloPropio        si el reporte es solo de quien lo pide
     * @return reporte con renglones y totales
     */
    public static VentaReporte armar(LocalDate desde, LocalDate hasta, AgrupacionVenta agrupar, List<Linea> lineas,
            List<Cobro> cobros, BigDecimal redondeo, boolean soloPropio) {
        Acumulado total = new Acumulado("TOTAL", "Total");
        lineas.forEach(total::sumar);
        VentaReporte.Fila t = total.fila();
        BigDecimal ajuste = dinero(redondeo == null ? BigDecimal.ZERO : redondeo);
        BigDecimal neto = t.neto().add(ajuste);
        long ventas = t.ventas();
        VentaReporte.Totales totales = new VentaReporte.Totales(ventas, t.bruto(), t.devoluciones(), ajuste, neto,
                t.baseNeta(), t.costo(), t.margen(), t.margenPct(), t.baseSinCosto(),
                ventas == 0 ? null : neto.divide(BigDecimal.valueOf(ventas), 2, RoundingMode.HALF_UP));

        List<VentaReporte.Fila> filas = agrupar == AgrupacionVenta.MEDIO_PAGO ? porMedio(cobros) : porDimension(agrupar, lineas);
        return new VentaReporte(desde, hasta, agrupar, filas, totales, soloPropio);
    }

    /**
     * @param agrupar agrupación (no medio de pago)
     * @param lineas  líneas
     * @return renglones ordenados
     */
    private static List<VentaReporte.Fila> porDimension(AgrupacionVenta agrupar, List<Linea> lineas) {
        Map<String, Acumulado> grupos = new LinkedHashMap<>();
        for (Linea l : lineas) {
            String[] cn = claveYNombre(agrupar, l);
            grupos.computeIfAbsent(cn[0], k -> new Acumulado(cn[0], cn[1])).sumar(l);
        }
        List<VentaReporte.Fila> filas = new ArrayList<>(grupos.values().stream().map(Acumulado::fila).toList());
        filas.sort(agrupar.esTiempo() ? Comparator.comparing(VentaReporte.Fila::clave)
                : Comparator.comparing(VentaReporte.Fila::neto).reversed().thenComparing(VentaReporte.Fila::nombre));
        return filas;
    }

    /**
     * @param cobros cobros y reembolsos
     * @return un renglón por medio, del que más dinero dejó al que menos
     */
    private static List<VentaReporte.Fila> porMedio(List<Cobro> cobros) {
        Map<String, Object[]> grupos = new LinkedHashMap<>();
        for (Cobro c : cobros) {
            Object[] g = grupos.computeIfAbsent(c.medio(), k -> new Object[] { c.nombre(), new HashSet<Long>(), CERO, CERO });
            @SuppressWarnings("unchecked")
            Set<Long> ventas = (Set<Long>) g[1];
            if (c.devolucion()) {
                g[3] = ((BigDecimal) g[3]).add(dinero(c.monto()));
            } else {
                ventas.add(c.ventaId());
                g[2] = ((BigDecimal) g[2]).add(dinero(c.monto()));
            }
        }
        List<VentaReporte.Fila> filas = new ArrayList<>();
        grupos.forEach((medio, g) -> {
            BigDecimal bruto = (BigDecimal) g[2];
            BigDecimal devuelto = (BigDecimal) g[3];
            filas.add(new VentaReporte.Fila(medio, (String) g[0], ((Set<?>) g[1]).size(), null, bruto, devuelto,
                    bruto.subtract(devuelto), null, null, null, null, null));
        });
        filas.sort(Comparator.comparing(VentaReporte.Fila::neto).reversed().thenComparing(VentaReporte.Fila::clave));
        return filas;
    }

    /**
     * @param agrupar agrupación
     * @param l       línea
     * @return clave y nombre del renglón al que va la línea
     */
    static String[] claveYNombre(AgrupacionVenta agrupar, Linea l) {
        return switch (agrupar) {
            case DIA -> new String[] { l.fecha().toString(), l.fecha().toString() };
            case SEMANA -> {
                LocalDate lunes = l.fecha().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new String[] { lunes.toString(), "Semana del " + lunes };
            }
            case MES -> {
                LocalDate primero = l.fecha().withDayOfMonth(1);
                yield new String[] { primero.toString(), primero.toString().substring(0, 7) };
            }
            case PRODUCTO -> new String[] { String.valueOf(l.productoId()), l.producto() };
            case CATEGORIA -> new String[] { String.valueOf(l.categoriaId()), l.categoria() };
            case CLIENTE -> new String[] { String.valueOf(l.clienteId()), l.cliente() };
            case VENDEDOR -> l.vendedorId() == null ? new String[] { "0", VENTA_DIRECTA }
                    : new String[] { String.valueOf(l.vendedorId()), l.vendedor() };
            case CAJERO -> new String[] { String.valueOf(l.cajeroId()), l.cajero() };
            case CAJA -> new String[] { String.valueOf(l.cajaId()), l.caja() };
            case MEDIO_PAGO -> throw new IllegalArgumentException("El medio de pago se agrupa con los cobros");
        };
    }

    /**
     * @param valor  parte
     * @param total  todo
     * @return parte en % del todo con 2 decimales ({@code null} si el todo es 0)
     */
    static BigDecimal porcentaje(BigDecimal valor, BigDecimal total) {
        if (total == null || total.signum() == 0) {
            return null;
        }
        return valor.multiply(CIEN).divide(total, 2, RoundingMode.HALF_UP);
    }

    /**
     * @param valor valor
     * @return con 2 decimales
     */
    static BigDecimal dinero(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}
