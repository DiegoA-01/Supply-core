package com.proyecto.supply_core.reorden.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.util.DiasHabiles;
import com.proyecto.supply_core.compra.solicitud.dto.LineaSolicitudRequest;
import com.proyecto.supply_core.compra.solicitud.dto.SolicitudRequest;
import com.proyecto.supply_core.compra.solicitud.dto.SolicitudResponse;
import com.proyecto.supply_core.compra.solicitud.service.SolicitudService;
import com.proyecto.supply_core.empresa.dto.EmpresaResponse;
import com.proyecto.supply_core.empresa.service.EmpresaService;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.reorden.dto.EntregaHistorica;
import com.proyecto.supply_core.reorden.dto.ReabastecimientoBorradorRequest;
import com.proyecto.supply_core.reorden.dto.ReabastecimientoItem;
import com.proyecto.supply_core.reorden.dto.ReabastecimientoResponse;
import com.proyecto.supply_core.reorden.enums.EstadoReabastecimiento;
import com.proyecto.supply_core.reorden.repository.ReordenRepository;
import com.proyecto.supply_core.reporte.dto.RotacionItem;
import com.proyecto.supply_core.reporte.dto.RotacionResponse;
import com.proyecto.supply_core.reporte.service.RotacionService;

/**
 * Plan de reabastecimiento: pedir a tiempo según lo que se consume y lo que tarda el proveedor.
 * <p>Para cada producto activo, con L = días de calendario hasta que llega un pedido hecho hoy (plazo en días
 * hábiles), S = días de calendario del colchón de seguridad y C = días de cobertura de la empresa:</p>
 * <pre>
 * consumo diario  = salidas de los últimos {@value RotacionService#DIAS_POR_DEFECTO} días ÷ días
 * disponible      = stock + por recibir de órdenes + ya pedido en solicitudes
 * punto de pedido = máx(consumo × (L + S), stock mínimo)
 * cantidad        = máx(consumo × (L + S + C), stock mínimo) − disponible, redondeada hacia arriba
 *                   (sin pasar del stock máximo si lo tiene)
 * </pre>
 * <ul>
 *   <li>{@code URGENTE}: stock + por recibir no alcanza ni para L días: se acaba antes de que llegue lo que se pida hoy.</li>
 *   <li>{@code PEDIR_YA}: disponible ≤ punto de pedido, o stock bajo el mínimo.</li>
 *   <li>{@code PRONTO}: el disponible llega al punto de pedido en {@value #DIAS_PRONTO} días hábiles o menos.</li>
 *   <li>{@code SIN_CONSUMO}: sin salidas en el período y sobre el mínimo.</li>
 * </ul>
 * <p>Los días hábiles excluyen sábados, domingos y festivos de Colombia ({@link DiasHabiles}). El plazo histórico
 * (promedio de lo que tardaron las entregas del último año) es solo una pista para ajustar el plazo del producto.</p>
 * <p><b>Solo sugiere</b> (regla de oro 1): el borrador es una solicitud BORRADOR que sigue el flujo normal.</p>
 */
@Service
public class ReabastecimientoService {

    /** Días hábiles hacia adelante en los que un pedido se considera "pronto". */
    public static final int DIAS_PRONTO = 5;
    /** Días hacia atrás de órdenes usadas para el plazo histórico. */
    static final int DIAS_HISTORIAL = 365;
    /** Texto del motivo de las solicitudes creadas desde el plan. */
    static final String MOTIVO_BORRADOR = "Reabastecimiento por consumo y plazo de entrega";
    private static final int ESCALA = 4;
    /** Fechas de los motivos como se leen en Colombia: "25 de noviembre". */
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("es-CO"));
    /** Igual que {@link #FECHA} pero con el año, para fechas de otro año. */
    private static final DateTimeFormatter FECHA_ANIO = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy",
            Locale.forLanguageTag("es-CO"));

    private final RotacionService rotacionService;
    private final ProductoRepository productoRepository;
    private final ReordenService reordenService;
    private final ReordenRepository reordenRepository;
    private final EmpresaService empresaService;
    private final SolicitudService solicitudService;
    private final AuditoriaService auditoriaService;
    private final Clock reloj;

    /**
     * @param rotacionService    consumo de cada producto en el período
     * @param productoRepository plazos propios, máximos y unidad de cada producto
     * @param reordenService     lo que viene en camino (órdenes y solicitudes)
     * @param reordenRepository  historial de entregas
     * @param empresaService     valores por defecto de la empresa
     * @param solicitudService   creación del borrador
     * @param auditoriaService   registro de borradores generados
     */
    @Autowired
    public ReabastecimientoService(RotacionService rotacionService, ProductoRepository productoRepository,
            ReordenService reordenService, ReordenRepository reordenRepository, EmpresaService empresaService,
            SolicitudService solicitudService, AuditoriaService auditoriaService) {
        this(rotacionService, productoRepository, reordenService, reordenRepository, empresaService, solicitudService,
                auditoriaService, Clock.systemDefaultZone());
    }

    /**
     * @param rotacionService    consumo de cada producto en el período
     * @param productoRepository plazos propios, máximos y unidad de cada producto
     * @param reordenService     lo que viene en camino (órdenes y solicitudes)
     * @param reordenRepository  historial de entregas
     * @param empresaService     valores por defecto de la empresa
     * @param solicitudService   creación del borrador
     * @param auditoriaService   registro de borradores generados
     * @param reloj              reloj para saber qué día es hoy (se cambia en las pruebas)
     */
    ReabastecimientoService(RotacionService rotacionService, ProductoRepository productoRepository,
            ReordenService reordenService, ReordenRepository reordenRepository, EmpresaService empresaService,
            SolicitudService solicitudService, AuditoriaService auditoriaService, Clock reloj) {
        this.rotacionService = rotacionService;
        this.productoRepository = productoRepository;
        this.reordenService = reordenService;
        this.reordenRepository = reordenRepository;
        this.empresaService = empresaService;
        this.solicitudService = solicitudService;
        this.auditoriaService = auditoriaService;
        this.reloj = reloj;
    }

    /**
     * Plan de reabastecimiento de todos los productos activos de la empresa de la sesión.
     *
     * @return plan con resumen, de más a menos urgente
     */
    @Transactional(readOnly = true)
    public ReabastecimientoResponse plan() {
        LocalDate hoy = LocalDate.now(reloj);
        EmpresaResponse empresa = empresaService.obtener();
        RotacionResponse rotacion = rotacionService.rotacion(null, hoy, null);
        List<Long> ids = rotacion.items().stream().map(RotacionItem::productoId).toList();
        if (ids.isEmpty()) {
            return new ReabastecimientoResponse(hoy, rotacion.dias(), empresa.plazoEntregaDiasDefecto(),
                    empresa.diasSeguridadDefecto(), empresa.diasCoberturaDefecto(), 0, 0, 0, 0, 0, List.of());
        }
        Map<Long, Producto> productos = productoRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Producto::getId, Function.identity()));
        Map<Long, BigDecimal> porRecibir = reordenService.porRecibir(ids);
        Map<Long, BigDecimal> enSolicitudes = reordenService.enSolicitudes(ids);
        Map<Long, Historial> historial = historial(ids, hoy);

        List<ReabastecimientoItem> items = new ArrayList<>();
        for (RotacionItem r : rotacion.items()) {
            Producto p = productos.get(r.productoId());
            if (p == null) {
                continue;
            }
            Parametros parametros = new Parametros(
                    p.getPlazoEntregaDias() != null ? p.getPlazoEntregaDias() : empresa.plazoEntregaDiasDefecto(),
                    p.getPlazoEntregaDias() != null,
                    p.getDiasSeguridad() != null ? p.getDiasSeguridad() : empresa.diasSeguridadDefecto(),
                    empresa.diasCoberturaDefecto());
            items.add(calcular(r, p.getStockMaximo(), porRecibir.getOrDefault(r.productoId(), BigDecimal.ZERO),
                    enSolicitudes.getOrDefault(r.productoId(), BigDecimal.ZERO), rotacion.dias(), parametros,
                    historial.get(r.productoId()), hoy));
        }
        items.sort(Comparator.comparing(ReabastecimientoItem::estado)
                .thenComparing(ReabastecimientoItem::pedirAntesDe, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ReabastecimientoItem::nombre));
        Map<EstadoReabastecimiento, Long> conteo = items.stream()
                .collect(Collectors.groupingBy(ReabastecimientoItem::estado, Collectors.counting()));
        return new ReabastecimientoResponse(hoy, rotacion.dias(), empresa.plazoEntregaDiasDefecto(),
                empresa.diasSeguridadDefecto(), empresa.diasCoberturaDefecto(),
                conteo.getOrDefault(EstadoReabastecimiento.URGENTE, 0L),
                conteo.getOrDefault(EstadoReabastecimiento.PEDIR_YA, 0L),
                conteo.getOrDefault(EstadoReabastecimiento.PRONTO, 0L),
                conteo.getOrDefault(EstadoReabastecimiento.OK, 0L),
                conteo.getOrDefault(EstadoReabastecimiento.SIN_CONSUMO, 0L), List.copyOf(items));
    }

    /**
     * Crea una solicitud BORRADOR del usuario de la sesión con la cantidad sugerida de los productos elegidos.
     *
     * @param req fecha requerida y productos
     * @return solicitud en BORRADOR
     * @throws NegocioException si algún producto no tiene cantidad sugerida
     */
    @Transactional
    public SolicitudResponse crearBorrador(ReabastecimientoBorradorRequest req) {
        Set<Long> pedidos = new HashSet<>(req.productoIds());
        List<ReabastecimientoItem> elegidos = plan().items().stream()
                .filter(i -> pedidos.contains(i.productoId()))
                .filter(i -> i.cantidadSugerida() != null && i.cantidadSugerida().signum() > 0)
                .toList();
        Set<Long> faltan = new HashSet<>(pedidos);
        elegidos.forEach(i -> faltan.remove(i.productoId()));
        if (!faltan.isEmpty()) {
            throw new NegocioException("PRODUCTO_SIN_SUGERENCIA",
                    "Estos productos no tienen cantidad para pedir (ya alcanzan o no tienen consumo): " + faltan);
        }
        Map<Long, Producto> productos = productoRepository.findAllById(pedidos).stream()
                .collect(Collectors.toMap(Producto::getId, Function.identity()));
        List<LineaSolicitudRequest> lineas = elegidos.stream()
                .map(i -> new LineaSolicitudRequest(i.productoId(),
                        productos.get(i.productoId()).getUnidadMedida().getId(), i.cantidadSugerida()))
                .toList();
        SolicitudResponse borrador = solicitudService.crear(new SolicitudRequest(MOTIVO_BORRADOR,
                req.fechaRequerida(), lineas));
        auditoriaService.exito(AccionAuditoria.REORDEN_GENERAR, EntidadAuditada.SOLICITUD_COMPRA, borrador.id(),
                borrador.consecutivo() + "; reabastecimiento; productos="
                        + elegidos.stream().map(ReabastecimientoItem::codigo).collect(Collectors.joining(",")));
        return borrador;
    }

    // ----------------------------------------------------------------- cálculo

    /**
     * Plazo, seguridad y cobertura que se aplican a un producto.
     *
     * @param plazo       días hábiles de entrega
     * @param plazoPropio {@code true} si el plazo es del producto
     * @param seguridad   días hábiles de colchón
     * @param cobertura   días de calendario que debe cubrir el pedido al llegar
     */
    record Parametros(int plazo, boolean plazoPropio, int seguridad, int cobertura) {
    }

    /**
     * Promedio de lo que tardaron las entregas de un producto.
     *
     * @param promedio días hábiles promedio
     * @param entregas cuántas entregas
     */
    record Historial(BigDecimal promedio, int entregas) {
    }

    /**
     * Calcula el plan de un producto. Es estático y sin base de datos para poder probarlo con números a mano.
     *
     * @param r             rotación del producto (stock, mínimo y salidas del período)
     * @param stockMaximo   máximo del producto o {@code null}
     * @param porRecibir    lo que falta por llegar de órdenes
     * @param enSolicitudes lo pedido en solicitudes en curso
     * @param diasPeriodo   días del período de la rotación
     * @param prm           plazo, seguridad y cobertura
     * @param historial     plazo histórico o {@code null}
     * @param hoy           día del cálculo
     * @return plan del producto
     */
    static ReabastecimientoItem calcular(RotacionItem r, BigDecimal stockMaximo, BigDecimal porRecibir,
            BigDecimal enSolicitudes, int diasPeriodo, Parametros prm, Historial historial, LocalDate hoy) {
        BigDecimal stock = r.stock();
        BigDecimal minimo = r.stockMinimo() == null ? BigDecimal.ZERO : r.stockMinimo();
        BigDecimal consumo = r.salidas().divide(BigDecimal.valueOf(diasPeriodo), 6, RoundingMode.HALF_UP);
        boolean hayConsumo = consumo.signum() > 0;
        BigDecimal disponible = stock.add(porRecibir).add(enSolicitudes);

        LocalDate llegada = DiasHabiles.sumar(hoy, prm.plazo());
        long diasLlegada = DiasHabiles.calendario(hoy, llegada);
        long diasSeguridad = DiasHabiles.calendario(llegada, DiasHabiles.sumar(llegada, prm.seguridad()));

        // se multiplica antes de dividir para no arrastrar el redondeo del consumo diario
        BigDecimal puntoPedido = consumoEn(r.salidas(), diasLlegada + diasSeguridad, diasPeriodo).max(minimo)
                .setScale(ESCALA, RoundingMode.CEILING);
        BigDecimal objetivo = consumoEn(r.salidas(), diasLlegada + diasSeguridad + prm.cobertura(), diasPeriodo)
                .max(minimo);
        if (stockMaximo != null) {
            // sin consumo (solo bajo el mínimo) se repone hasta el máximo, como la reorden por mínimo
            objetivo = hayConsumo ? objetivo.min(stockMaximo.max(puntoPedido)) : stockMaximo;
        }
        boolean bajoMinimo = stock.compareTo(minimo) < 0;
        // sin consumo no hay con qué proyectar: solo se repone si está bajo el mínimo
        BigDecimal cantidad = !hayConsumo && !bajoMinimo ? BigDecimal.ZERO.setScale(ESCALA)
                : objetivo.subtract(disponible).max(BigDecimal.ZERO).setScale(0, RoundingMode.CEILING).setScale(ESCALA);

        BigDecimal cobertura = null;
        LocalDate agotamiento = null;
        LocalDate pedirAntesDe = null;
        if (hayConsumo) {
            cobertura = stock.signum() <= 0 ? BigDecimal.ZERO.setScale(1)
                    : stock.divide(consumo, 1, RoundingMode.HALF_UP);
            agotamiento = hoy.plusDays(cobertura.longValue());
            BigDecimal margen = disponible.subtract(puntoPedido);
            pedirAntesDe = margen.signum() <= 0 ? hoy
                    : ultimoHabilHasta(hoy.plusDays(margen.divide(consumo, 0, RoundingMode.FLOOR).longValue()), hoy);
        }

        EstadoReabastecimiento estado;
        String motivo;
        if (hayConsumo && stock.add(porRecibir).compareTo(consumoEn(r.salidas(), diasLlegada, diasPeriodo)) < 0) {
            estado = EstadoReabastecimiento.URGENTE;
            motivo = "Se acaba hacia el " + texto(agotamiento, hoy) + " y un pedido hecho hoy llega el "
                    + texto(llegada, hoy) + " (" + prm.plazo() + " días hábiles).";
        } else if (disponible.compareTo(puntoPedido) <= 0 && (hayConsumo || bajoMinimo)) {
            estado = EstadoReabastecimiento.PEDIR_YA;
            motivo = bajoMinimo && !hayConsumo ? "Está bajo el stock mínimo."
                    : "Lo disponible ya no cubre el plazo de entrega más la seguridad: hay que pedir hoy.";
        } else if (!hayConsumo) {
            estado = EstadoReabastecimiento.SIN_CONSUMO;
            motivo = "Sin salidas en los últimos " + diasPeriodo + " días.";
        } else if (DiasHabiles.entre(hoy, pedirAntesDe) <= DIAS_PRONTO) {
            estado = EstadoReabastecimiento.PRONTO;
            motivo = "Hay que pedir a más tardar el " + texto(pedirAntesDe, hoy) + ".";
        } else {
            estado = EstadoReabastecimiento.OK;
            motivo = "Alcanza; pedir a más tardar el " + texto(pedirAntesDe, hoy) + ".";
        }

        return new ReabastecimientoItem(r.productoId(), r.codigo(), r.nombre(), r.categoria(), r.unidad(), stock,
                minimo, stockMaximo, porRecibir.setScale(ESCALA, RoundingMode.HALF_UP),
                enSolicitudes.setScale(ESCALA, RoundingMode.HALF_UP), consumo.setScale(ESCALA, RoundingMode.HALF_UP),
                cobertura, agotamiento, prm.plazo(), prm.plazoPropio(), prm.seguridad(),
                historial == null ? null : historial.promedio(), historial == null ? 0 : historial.entregas(),
                llegada, puntoPedido, pedirAntesDe, cantidad, estado, motivo);
    }

    /**
     * @param fecha fecha del motivo
     * @param hoy   día del cálculo
     * @return "25 de noviembre", o "19 de enero de 2028" si es de otro año
     */
    private static String texto(LocalDate fecha, LocalDate hoy) {
        return fecha.format(fecha.getYear() == hoy.getYear() ? FECHA : FECHA_ANIO);
    }

    /**
     * Consumo esperado en un número de días al ritmo del período: {@code salidas × dias ÷ diasPeriodo}.
     *
     * @param salidas     salidas del período
     * @param dias        días a cubrir
     * @param diasPeriodo días del período
     * @return consumo esperado con {@value #ESCALA} decimales
     */
    private static BigDecimal consumoEn(BigDecimal salidas, long dias, int diasPeriodo) {
        return salidas.multiply(BigDecimal.valueOf(dias)).divide(BigDecimal.valueOf(diasPeriodo), ESCALA,
                RoundingMode.HALF_UP);
    }

    /**
     * Último día hábil en o antes de {@code fecha}, sin bajar de {@code hoy} (los pedidos se hacen en días hábiles).
     *
     * @param fecha fecha límite calculada
     * @param hoy   día del cálculo
     * @return la fecha si es hábil; si no, el día hábil anterior (o hoy)
     */
    private static LocalDate ultimoHabilHasta(LocalDate fecha, LocalDate hoy) {
        LocalDate f = fecha;
        while (f.isAfter(hoy) && !DiasHabiles.esHabil(f)) {
            f = f.minusDays(1);
        }
        return f;
    }

    /**
     * Promedio de días hábiles entre la emisión de la orden y su primera recepción, por producto, en el último año.
     *
     * @param ids productos
     * @param hoy día del cálculo
     * @return historial por producto (solo los que tienen entregas)
     */
    private Map<Long, Historial> historial(List<Long> ids, LocalDate hoy) {
        Map<Long, List<Integer>> dias = new HashMap<>();
        for (EntregaHistorica e : reordenRepository.entregasHistoricas(ids, hoy.minusDays(DIAS_HISTORIAL).atStartOfDay())) {
            dias.computeIfAbsent(e.productoId(), k -> new ArrayList<>())
                    .add(DiasHabiles.entre(e.pedidoEn().toLocalDate(), e.recibidoEn().toLocalDate()));
        }
        Map<Long, Historial> resultado = new HashMap<>();
        dias.forEach((id, lista) -> resultado.put(id, new Historial(BigDecimal.valueOf(
                lista.stream().mapToInt(Integer::intValue).sum()).divide(BigDecimal.valueOf(lista.size()), 1,
                        RoundingMode.HALF_UP), lista.size())));
        return resultado;
    }
}
