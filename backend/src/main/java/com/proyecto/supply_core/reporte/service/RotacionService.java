package com.proyecto.supply_core.reporte.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.inventario.enums.TipoMovimiento;
import com.proyecto.supply_core.reporte.dto.ClaseRotacion;
import com.proyecto.supply_core.reporte.dto.InventarioFila;
import com.proyecto.supply_core.reporte.dto.RotacionItem;
import com.proyecto.supply_core.reporte.dto.RotacionMovimiento;
import com.proyecto.supply_core.reporte.dto.RotacionResponse;
import com.proyecto.supply_core.reporte.dto.RotacionResumen;
import com.proyecto.supply_core.reporte.dto.RotacionUltima;
import com.proyecto.supply_core.reporte.repository.ReporteRepository;

/**
 * Rotación de productos (UC-15), solo lectura: qué sale más, qué sale menos, cuántos días alcanza el stock y qué lleva
 * tiempo sin moverse. La base de datos suma los movimientos; Java calcula ritmos, días y clasificación.
 * <p>Qué cuenta como "salida": el consumo interno y los despachos, menos los despachos anulados (que devuelven la
 * mercancía). No cuentan los ajustes (una pérdida no es demanda) ni los traslados (el producto sigue en la empresa).
 * Qué cuenta como "compra": las entradas por recepción, menos las devoluciones al proveedor.</p>
 */
@Service
@Transactional(readOnly = true)
public class RotacionService {

    /** Período por defecto: los últimos 90 días. */
    public static final int DIAS_POR_DEFECTO = 90;
    /** Período máximo que se puede consultar. */
    public static final int DIAS_MAXIMOS = 366;
    /** ALTA: el stock alcanza este número de días o menos al ritmo del período. */
    public static final int DIAS_ROTACION_ALTA = 30;
    /** BAJA: el stock alcanza más de este número de días. */
    public static final int DIAS_ROTACION_BAJA = 90;
    /** "Por agotarse": el stock alcanza este número de días o menos. */
    public static final int DIAS_POR_AGOTARSE = 15;

    static final Set<TipoMovimiento> TIPOS_DEL_PERIODO = EnumSet.of(TipoMovimiento.SALIDA_CONSUMO,
            TipoMovimiento.SALIDA_VENTA, TipoMovimiento.ANULACION_DESPACHO, TipoMovimiento.ENTRADA_COMPRA,
            TipoMovimiento.DEVOLUCION_PROVEEDOR, TipoMovimiento.ANULACION_VENTA, TipoMovimiento.DEVOLUCION_CLIENTE);
    static final Set<TipoMovimiento> TIPOS_DE_SALIDA = EnumSet.of(TipoMovimiento.SALIDA_CONSUMO,
            TipoMovimiento.SALIDA_VENTA);

    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(4);

    private final ReporteRepository reporteRepository;
    private final Clock reloj;

    /**
     * @param reporteRepository consultas agregadas
     */
    @Autowired
    public RotacionService(ReporteRepository reporteRepository) {
        this(reporteRepository, Clock.systemDefaultZone());
    }

    /**
     * @param reporteRepository consultas agregadas
     * @param reloj             reloj para saber qué día es hoy (se cambia en las pruebas)
     */
    RotacionService(ReporteRepository reporteRepository, Clock reloj) {
        this.reporteRepository = reporteRepository;
        this.reloj = reloj;
    }

    /**
     * Rotación de los productos activos de toda la empresa en un período.
     *
     * @param desde       primer día; por defecto, {@value #DIAS_POR_DEFECTO} días antes de {@code hasta}
     * @param hasta       último día (incluido); por defecto, hoy
     * @param categoriaId solo productos de esta categoría, opcional
     * @return productos de más a menos salidas, con resumen
     * @throws ParametroInvalidoException si el rango está invertido o pasa de {@value #DIAS_MAXIMOS} días
     */
    public RotacionResponse rotacion(LocalDate desde, LocalDate hasta, Long categoriaId) {
        return rotacion(desde, hasta, categoriaId, null);
    }

    /**
     * Rotación de los productos activos en un período; con sede, el stock y las salidas son solo los de las bodegas
     * de esa sede.
     *
     * @param desde       primer día; por defecto, {@value #DIAS_POR_DEFECTO} días antes de {@code hasta}
     * @param hasta       último día (incluido); por defecto, hoy
     * @param categoriaId solo productos de esta categoría, opcional
     * @param sedeId      solo las bodegas de esta sede, opcional
     * @return productos de más a menos salidas, con resumen
     * @throws ParametroInvalidoException si el rango está invertido o pasa de {@value #DIAS_MAXIMOS} días
     * @throws com.proyecto.supply_core.common.exception.NoEncontradoException si la sede no es de la empresa
     */
    public RotacionResponse rotacion(LocalDate desde, LocalDate hasta, Long categoriaId, Long sedeId) {
        ReporteService.validarSede(reporteRepository, sedeId);
        LocalDate hoy = LocalDate.now(reloj);
        LocalDate fin = hasta != null ? hasta : hoy;
        LocalDate inicio = desde != null ? desde : fin.minusDays(DIAS_POR_DEFECTO - 1L);
        if (inicio.isAfter(fin)) {
            throw new ParametroInvalidoException("'desde' no puede ser posterior a 'hasta'");
        }
        int dias = (int) ChronoUnit.DAYS.between(inicio, fin) + 1;
        if (dias > DIAS_MAXIMOS) {
            throw new ParametroInvalidoException("El período máximo es de " + DIAS_MAXIMOS + " días");
        }

        Map<Long, Map<TipoMovimiento, BigDecimal>> movido = new HashMap<>();
        for (RotacionMovimiento m : reporteRepository.movimientosPorProducto(TIPOS_DEL_PERIODO,
                inicio.atStartOfDay(), fin.plusDays(1).atStartOfDay(), sedeId)) {
            movido.computeIfAbsent(m.productoId(), k -> new EnumMap<>(TipoMovimiento.class)).put(m.tipo(), m.cantidad());
        }
        Map<Long, LocalDateTime> ultimas = reporteRepository.ultimaSalida(TIPOS_DE_SALIDA, sedeId).stream()
                .collect(Collectors.toMap(RotacionUltima::productoId, RotacionUltima::ultima));

        List<RotacionItem> items = reporteRepository.inventario(categoriaId, sedeId).stream()
                .map(f -> item(f, movido.getOrDefault(f.productoId(), Map.of()), ultimas.get(f.productoId()), dias, hoy))
                .sorted(Comparator.comparing(RotacionItem::salidas).reversed().thenComparing(RotacionItem::nombre))
                .toList();
        return new RotacionResponse(inicio, fin, dias, resumen(items), items);
    }

    private static RotacionItem item(InventarioFila f, Map<TipoMovimiento, BigDecimal> mov, LocalDateTime ultima,
            int dias, LocalDate hoy) {
        BigDecimal stock = f.stock();
        BigDecimal salidas = noNegativo(cant(mov, TipoMovimiento.SALIDA_CONSUMO).add(cant(mov, TipoMovimiento.SALIDA_VENTA))
                .subtract(cant(mov, TipoMovimiento.ANULACION_DESPACHO)).subtract(cant(mov, TipoMovimiento.ANULACION_VENTA))
                .subtract(cant(mov, TipoMovimiento.DEVOLUCION_CLIENTE)));
        BigDecimal comprado = noNegativo(cant(mov, TipoMovimiento.ENTRADA_COMPRA)
                .subtract(cant(mov, TipoMovimiento.DEVOLUCION_PROVEEDOR)));
        boolean huboSalidas = salidas.signum() > 0;
        BigDecimal consumoDiario = salidas.divide(BigDecimal.valueOf(dias), 4, RoundingMode.HALF_UP);
        // stock × días ÷ salidas: evita dividir por un consumo diario redondeado a cero
        BigDecimal cobertura = !huboSalidas ? null
                : stock.signum() <= 0 ? BigDecimal.ZERO.setScale(1)
                        : stock.multiply(BigDecimal.valueOf(dias)).divide(salidas, 1, RoundingMode.HALF_UP);
        Long diasSinSalida = ultima == null ? null : Math.max(0, ChronoUnit.DAYS.between(ultima.toLocalDate(), hoy));
        BigDecimal valor = f.costoReferencia() == null ? null
                : stock.multiply(f.costoReferencia()).setScale(2, RoundingMode.HALF_UP);
        boolean porAgotarse = huboSalidas && stock.signum() > 0
                && cobertura.compareTo(BigDecimal.valueOf(DIAS_POR_AGOTARSE)) <= 0;
        LocalDate agotamiento = huboSalidas && stock.signum() > 0 ? hoy.plusDays(cobertura.longValue()) : null;
        return new RotacionItem(f.productoId(), f.codigo(), f.nombre(), f.categoria(), f.unidad(), stock,
                f.stockMinimo(), salidas, comprado, consumoDiario, cobertura, agotamiento, ultima, diasSinSalida, valor,
                clase(stock, huboSalidas, cobertura), porAgotarse);
    }

    private static ClaseRotacion clase(BigDecimal stock, boolean huboSalidas, BigDecimal cobertura) {
        if (!huboSalidas) {
            return stock.signum() > 0 ? ClaseRotacion.SIN_MOVIMIENTO : ClaseRotacion.SIN_ACTIVIDAD;
        }
        if (stock.signum() <= 0) {
            return ClaseRotacion.AGOTADO;
        }
        if (cobertura.compareTo(BigDecimal.valueOf(DIAS_ROTACION_ALTA)) <= 0) {
            return ClaseRotacion.ALTA;
        }
        return cobertura.compareTo(BigDecimal.valueOf(DIAS_ROTACION_BAJA)) > 0 ? ClaseRotacion.BAJA : ClaseRotacion.MEDIA;
    }

    private static RotacionResumen resumen(List<RotacionItem> items) {
        BigDecimal valorTotal = items.stream().map(RotacionItem::valorStock).filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal valorParado = items.stream().filter(i -> i.clase() == ClaseRotacion.SIN_MOVIMIENTO)
                .map(RotacionItem::valorStock).filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        return new RotacionResumen(items.size(), items.stream().filter(i -> i.salidas().signum() > 0).count(),
                items.stream().filter(i -> i.clase() == ClaseRotacion.SIN_MOVIMIENTO).count(),
                items.stream().filter(i -> i.clase() == ClaseRotacion.AGOTADO).count(),
                items.stream().filter(RotacionItem::porAgotarse).count(), valorTotal, valorParado,
                DIAS_ROTACION_ALTA, DIAS_ROTACION_BAJA, DIAS_POR_AGOTARSE);
    }

    private static BigDecimal cant(Map<TipoMovimiento, BigDecimal> mov, TipoMovimiento tipo) {
        BigDecimal v = mov.get(tipo);
        return v == null ? CERO : v;
    }

    private static BigDecimal noNegativo(BigDecimal v) {
        return v.signum() < 0 ? CERO : v.setScale(4, RoundingMode.HALF_UP);
    }
}
