package com.proyecto.supply_core.reporte.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;
import com.proyecto.supply_core.proveedor.entity.Proveedor;
import com.proyecto.supply_core.proveedor.repository.ProveedorRepository;
import com.proyecto.supply_core.reporte.dto.EstadoCuentaOrden;
import com.proyecto.supply_core.reporte.dto.EstadoCuentaResponse;
import com.proyecto.supply_core.reporte.dto.GastoFila;
import com.proyecto.supply_core.reporte.dto.GastoItem;
import com.proyecto.supply_core.reporte.dto.GastoMes;
import com.proyecto.supply_core.reporte.dto.GastoMesFila;
import com.proyecto.supply_core.reporte.dto.GastoResponse;
import com.proyecto.supply_core.reporte.dto.InventarioFila;
import com.proyecto.supply_core.reporte.dto.InventarioItem;
import com.proyecto.supply_core.reporte.dto.InventarioResumen;
import com.proyecto.supply_core.reporte.dto.OrdenFila;
import com.proyecto.supply_core.reporte.dto.ProveedorRanking;
import com.proyecto.supply_core.reporte.dto.SumaConsecutivo;
import com.proyecto.supply_core.reporte.repository.ReporteRepository;

/**
 * Reportes de solo lectura (UC-15). La base de datos agrupa y suma; Java redondea, totaliza y arma las respuestas.
 * Sin datos, las listas salen vacías y los totales en cero: nunca es un error. No escribe nada: no audita ni notifica.
 */
@Service
@Transactional(readOnly = true)
public class ReporteService {

    /** Órdenes que no son gasto: ya no están vigentes o el proveedor las rechazó. */
    static final Set<EstadoOrden> NO_VIGENTES = EnumSet.of(EstadoOrden.REEMPLAZADA, EstadoOrden.CANCELADA,
            EstadoOrden.RECHAZADA_PROVEEDOR);

    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2);

    private final ReporteRepository reporteRepository;
    private final ProveedorRepository proveedorRepository;

    /**
     * @param reporteRepository   consultas agregadas
     * @param proveedorRepository proveedores de la empresa
     */
    public ReporteService(ReporteRepository reporteRepository, ProveedorRepository proveedorRepository) {
        this.reporteRepository = reporteRepository;
        this.proveedorRepository = proveedorRepository;
    }

    /**
     * Gasto comprometido en órdenes vigentes de toda la empresa, con desglose por proveedor, categoría y mes.
     *
     * @param desde       emisión desde (incluida), opcional
     * @param hasta       emisión hasta (incluida), opcional
     * @param categoriaId solo líneas de esta categoría, opcional
     * @param proveedorId solo órdenes de este proveedor, opcional
     * @return gasto del período
     * @throws ParametroInvalidoException si {@code desde} es posterior a {@code hasta}
     */
    public GastoResponse gasto(LocalDate desde, LocalDate hasta, Long categoriaId, Long proveedorId) {
        return gasto(desde, hasta, categoriaId, proveedorId, null);
    }

    /**
     * Gasto comprometido en órdenes vigentes, con desglose por proveedor, categoría y mes; opcionalmente solo de las
     * órdenes que llegan a bodegas de una sede.
     *
     * @param desde       emisión desde (incluida), opcional
     * @param hasta       emisión hasta (incluida), opcional
     * @param categoriaId solo líneas de esta categoría, opcional
     * @param proveedorId solo órdenes de este proveedor, opcional
     * @param sedeId      solo órdenes con bodega destino en esta sede, opcional
     * @return gasto del período
     * @throws ParametroInvalidoException si {@code desde} es posterior a {@code hasta}
     * @throws NoEncontradoException      si la sede no es de la empresa
     */
    public GastoResponse gasto(LocalDate desde, LocalDate hasta, Long categoriaId, Long proveedorId, Long sedeId) {
        validarRango(desde, hasta);
        validarSede(reporteRepository, sedeId);
        LocalDateTime inicio = inicio(desde);
        LocalDateTime fin = fin(hasta);

        List<GastoItem> porProveedor = reporteRepository
                .gastoPorProveedor(NO_VIGENTES, inicio, fin, categoriaId, proveedorId, sedeId).stream()
                .map(ReporteService::item).toList();
        List<GastoItem> porCategoria = reporteRepository
                .gastoPorCategoria(NO_VIGENTES, inicio, fin, categoriaId, proveedorId, sedeId).stream()
                .map(ReporteService::item).toList();
        List<GastoMes> porMes = reporteRepository.gastoPorMes(NO_VIGENTES, inicio, fin, categoriaId, proveedorId, sedeId)
                .stream().map(ReporteService::mes).toList();

        BigDecimal subtotal = suma(porProveedor.stream().map(GastoItem::subtotal));
        BigDecimal impuestos = suma(porProveedor.stream().map(GastoItem::impuestos));
        long ordenes = porProveedor.stream().mapToLong(GastoItem::ordenes).sum();
        BigDecimal pagado = categoriaId == null
                ? dos(reporteRepository.pagado(desde, hasta, proveedorId, sedeId))
                : null;
        return new GastoResponse(desde, hasta, ordenes, subtotal, impuestos, subtotal.add(impuestos), pagado,
                porProveedor, porCategoria, porMes);
    }

    /**
     * Gasto en órdenes vigentes repartido por la sede a la que llegan (bodega destino).
     *
     * @param desde emisión desde (incluida), opcional
     * @param hasta emisión hasta (incluida), opcional
     * @return una fila por sede, de mayor a menor gasto
     * @throws ParametroInvalidoException si {@code desde} es posterior a {@code hasta}
     */
    public List<GastoItem> gastoPorSede(LocalDate desde, LocalDate hasta) {
        validarRango(desde, hasta);
        return reporteRepository.gastoPorSede(NO_VIGENTES, inicio(desde), fin(hasta)).stream()
                .map(ReporteService::item).toList();
    }

    /**
     * Responde 404 si el filtro pide una sede que no es de la empresa de la sesión (o no existe).
     *
     * @param repo   consultas de reporte (la sede se cuenta filtrada por empresa)
     * @param sedeId sede pedida, o {@code null} para toda la empresa
     * @throws NoEncontradoException si la sede no es de la empresa
     */
    static void validarSede(ReporteRepository repo, Long sedeId) {
        if (sedeId != null && repo.contarSede(sedeId) == 0) {
            throw new NoEncontradoException("Sede no encontrada");
        }
    }

    /**
     * Productos activos con su stock total, valor y alerta de mínimo.
     *
     * @param categoriaId solo esta categoría, opcional
     * @param soloBajoMinimo {@code true} deja solo los productos con stock menor al mínimo
     * @return inventario de la empresa
     */
    public InventarioResumen inventario(Long categoriaId, boolean soloBajoMinimo) {
        List<InventarioItem> items = reporteRepository.inventario(categoriaId, null).stream()
                .map(ReporteService::item)
                .filter(i -> !soloBajoMinimo || i.bajoMinimo())
                .toList();
        BigDecimal valorTotal = suma(items.stream().map(InventarioItem::valor).filter(v -> v != null));
        return new InventarioResumen(items.size(), items.stream().filter(InventarioItem::bajoMinimo).count(),
                valorTotal, items);
    }

    /**
     * Proveedores con órdenes en el período, del mejor score al peor. Sin score van al final; entre iguales manda
     * el mayor monto ordenado.
     *
     * @param desde       emisión desde (incluida), opcional
     * @param hasta       emisión hasta (incluida), opcional
     * @param categoriaId solo líneas de esta categoría, opcional
     * @return ranking, vacío si no hubo órdenes
     * @throws ParametroInvalidoException si {@code desde} es posterior a {@code hasta}
     */
    public List<ProveedorRanking> proveedores(LocalDate desde, LocalDate hasta, Long categoriaId) {
        return proveedores(desde, hasta, categoriaId, null);
    }

    /**
     * Ranking de proveedores; con sede, solo las órdenes que llegan a bodegas de esa sede.
     *
     * @param desde       emisión desde (incluida), opcional
     * @param hasta       emisión hasta (incluida), opcional
     * @param categoriaId solo líneas de esta categoría, opcional
     * @param sedeId      solo órdenes con bodega destino en esta sede, opcional
     * @return ranking, vacío si no hubo órdenes
     * @throws ParametroInvalidoException si {@code desde} es posterior a {@code hasta}
     * @throws NoEncontradoException      si la sede no es de la empresa
     */
    public List<ProveedorRanking> proveedores(LocalDate desde, LocalDate hasta, Long categoriaId, Long sedeId) {
        validarRango(desde, hasta);
        validarSede(reporteRepository, sedeId);
        List<GastoItem> gasto = reporteRepository
                .gastoPorProveedor(NO_VIGENTES, inicio(desde), fin(hasta), categoriaId, null, sedeId).stream()
                .map(ReporteService::item).toList();
        Map<Long, BigDecimal> scores = new HashMap<>();
        for (Proveedor p : proveedorRepository.findAllById(gasto.stream().map(GastoItem::id).toList())) {
            scores.put(p.getId(), p.getScoreActual());
        }
        Comparator<GastoItem> orden = Comparator
                .comparing((GastoItem g) -> scores.get(g.id()), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(GastoItem::total, Comparator.reverseOrder());
        List<GastoItem> ordenado = gasto.stream().sorted(orden).toList();
        return java.util.stream.IntStream.range(0, ordenado.size())
                .mapToObj(i -> new ProveedorRanking(i + 1, ordenado.get(i).id(), ordenado.get(i).nombre(),
                        scores.get(ordenado.get(i).id()), ordenado.get(i).ordenes(), ordenado.get(i).total()))
                .toList();
    }

    /**
     * Estado de cuenta de un proveedor: por cada orden vigente, lo ordenado, lo facturado, lo pagado y el saldo.
     * El saldo parte de la factura vigente si existe y, si no, del total de la orden.
     *
     * @param proveedorId proveedor de la empresa
     * @param desde       emisión desde (incluida), opcional
     * @param hasta       emisión hasta (incluida), opcional
     * @return estado de cuenta, con lista vacía si no hay órdenes
     * @throws NoEncontradoException      si el proveedor no existe en la empresa
     * @throws ParametroInvalidoException si {@code desde} es posterior a {@code hasta}
     */
    public EstadoCuentaResponse estadoCuenta(Long proveedorId, LocalDate desde, LocalDate hasta) {
        validarRango(desde, hasta);
        Proveedor proveedor = proveedorRepository.findById(proveedorId)
                .orElseThrow(() -> new NoEncontradoException("Proveedor", proveedorId));
        Map<String, BigDecimal> pagado = mapa(reporteRepository.pagadoPorOrden(proveedorId));
        Map<String, BigDecimal> facturado = mapa(reporteRepository.facturadoPorOrden(proveedorId));

        List<OrdenFila> filas = reporteRepository.ordenesDelProveedor(proveedorId, NO_VIGENTES, inicio(desde),
                fin(hasta));
        List<EstadoCuentaOrden> ordenes = filas.stream().map(o -> {
            BigDecimal total = dos(o.total());
            BigDecimal factura = dos(facturado.get(o.consecutivo()));
            BigDecimal pago = dos(pagado.get(o.consecutivo()));
            BigDecimal base = factura.signum() > 0 ? factura : total;
            return new EstadoCuentaOrden(o.consecutivo(), o.id(), o.creadoEn().toLocalDate(), o.estado(), total,
                    factura, pago, base.subtract(pago));
        }).toList();

        return new EstadoCuentaResponse(proveedor.getId(), proveedor.getRazonSocial(), proveedor.getNit(), desde,
                hasta, suma(ordenes.stream().map(EstadoCuentaOrden::total)),
                suma(ordenes.stream().map(EstadoCuentaOrden::facturado)),
                suma(ordenes.stream().map(EstadoCuentaOrden::pagado)),
                suma(ordenes.stream().map(EstadoCuentaOrden::saldo)), ordenes);
    }

    private static void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ParametroInvalidoException("'desde' no puede ser posterior a 'hasta'");
        }
    }

    private static LocalDateTime inicio(LocalDate desde) {
        return desde == null ? null : desde.atStartOfDay();
    }

    /** El filtro {@code hasta} incluye todo ese día: la consulta usa el inicio del día siguiente, sin incluirlo. */
    private static LocalDateTime fin(LocalDate hasta) {
        return hasta == null ? null : hasta.plusDays(1).atStartOfDay();
    }

    private static BigDecimal dos(BigDecimal valor) {
        return valor == null ? CERO : valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal suma(java.util.stream.Stream<BigDecimal> valores) {
        return valores.reduce(CERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private static Map<String, BigDecimal> mapa(List<SumaConsecutivo> filas) {
        return filas.stream().collect(Collectors.toMap(SumaConsecutivo::consecutivo, SumaConsecutivo::valor));
    }

    private static GastoItem item(GastoFila f) {
        BigDecimal subtotal = dos(f.subtotal());
        BigDecimal impuestos = dos(f.impuestos());
        return new GastoItem(f.id(), f.nombre(), f.ordenes(), subtotal, impuestos, subtotal.add(impuestos));
    }

    private static GastoMes mes(GastoMesFila f) {
        BigDecimal subtotal = dos(f.subtotal());
        BigDecimal impuestos = dos(f.impuestos());
        return new GastoMes("%d-%02d".formatted(f.anio(), f.mes()), f.ordenes(), subtotal, impuestos,
                subtotal.add(impuestos));
    }

    private static InventarioItem item(InventarioFila f) {
        BigDecimal stock = f.stock();
        BigDecimal valor = f.costoReferencia() == null ? null : dos(stock.multiply(f.costoReferencia()));
        return new InventarioItem(f.productoId(), f.codigo(), f.nombre(), f.categoria(), f.unidad(), stock,
                f.stockMinimo(), f.stockMaximo(), f.costoReferencia(), valor, stock.compareTo(f.stockMinimo()) < 0);
    }
}
