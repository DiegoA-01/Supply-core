package com.proyecto.supply_core.reporte.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.reorden.dto.ReabastecimientoItem;
import com.proyecto.supply_core.reorden.dto.ReabastecimientoResponse;
import com.proyecto.supply_core.reorden.enums.EstadoReabastecimiento;
import com.proyecto.supply_core.reorden.service.ReabastecimientoService;
import com.proyecto.supply_core.reporte.dto.ClaseRotacion;
import com.proyecto.supply_core.reporte.dto.GastoItem;
import com.proyecto.supply_core.reporte.dto.GastoMes;
import com.proyecto.supply_core.reporte.dto.GastoResponse;
import com.proyecto.supply_core.reporte.dto.InventarioResumen;
import com.proyecto.supply_core.reporte.dto.RotacionItem;
import com.proyecto.supply_core.reporte.dto.RotacionResponse;
import com.proyecto.supply_core.reporte.dto.TableroResponse;

/**
 * Arma el tablero del dueño reutilizando los reportes que ya existen (gasto, inventario, rotación y plan de
 * reabastecimiento). No consulta nada por su cuenta: solo junta, ordena y calcula los totales que la pantalla muestra.
 */
@Service
public class TableroService {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final ReporteService reporteService;
    private final RotacionService rotacionService;
    private final ReabastecimientoService reabastecimientoService;
    private final Clock reloj;

    /**
     * @param reporteService          gasto e inventario
     * @param rotacionService         rotación de productos
     * @param reabastecimientoService plan de reabastecimiento
     */
    @Autowired
    public TableroService(ReporteService reporteService, RotacionService rotacionService,
            ReabastecimientoService reabastecimientoService) {
        this(reporteService, rotacionService, reabastecimientoService, Clock.systemDefaultZone());
    }

    /**
     * @param reporteService          gasto e inventario
     * @param rotacionService         rotación de productos
     * @param reabastecimientoService plan de reabastecimiento
     * @param reloj                   reloj para saber qué día es hoy (se cambia en las pruebas)
     */
    TableroService(ReporteService reporteService, RotacionService rotacionService,
            ReabastecimientoService reabastecimientoService, Clock reloj) {
        this.reporteService = reporteService;
        this.rotacionService = rotacionService;
        this.reabastecimientoService = reabastecimientoService;
        this.reloj = reloj;
    }

    /**
     * Tablero de la empresa de la sesión: últimos doce meses de gasto, inventario y reabastecimiento.
     *
     * @return tablero calculado
     */
    @Transactional(readOnly = true)
    public TableroResponse tablero() {
        return tablero(null);
    }

    /**
     * Tablero de la empresa o de una sede. Por sede: compras que llegan a sus bodegas, stock y salidas de esas
     * bodegas y su plan de reabastecimiento. "Bajo el mínimo" sigue siendo de toda la empresa (el mínimo es total).
     *
     * @param sedeId sede, o {@code null} para toda la empresa
     * @return tablero calculado
     * @throws com.proyecto.supply_core.common.exception.NoEncontradoException si la sede no es de la empresa
     */
    @Transactional(readOnly = true)
    public TableroResponse tablero(Long sedeId) {
        LocalDate hoy = LocalDate.now(reloj);
        YearMonth mesActual = YearMonth.from(hoy);
        LocalDate desde = mesActual.minusMonths(11).atDay(1);
        GastoResponse gasto = reporteService.gasto(desde, hoy, null, null, sedeId);
        InventarioResumen inventario = reporteService.inventario(null, false);
        RotacionResponse rotacion = rotacionService.rotacion(null, hoy, null, sedeId);
        ReabastecimientoResponse plan = reabastecimientoService.plan(sedeId);
        List<GastoItem> porSede = sedeId == null ? reporteService.gastoPorSede(desde, hoy) : List.of();

        List<GastoMes> meses = doceMeses(gasto.porMes(), mesActual);
        BigDecimal actual = meses.get(meses.size() - 1).total();
        BigDecimal anterior = meses.get(meses.size() - 2).total();
        BigDecimal variacion = anterior.signum() == 0 ? null
                : actual.subtract(anterior).multiply(CIEN).divide(anterior, 1, RoundingMode.HALF_UP);

        Map<ClaseRotacion, Long> porClase = new EnumMap<>(ClaseRotacion.class);
        for (ClaseRotacion c : ClaseRotacion.values()) {
            porClase.put(c, 0L);
        }
        rotacion.items().forEach(i -> porClase.merge(i.clase(), 1L, Long::sum));

        List<RotacionItem> masMovidos = rotacion.items().stream().filter(i -> i.salidas().signum() > 0)
                .sorted(Comparator.comparing(RotacionItem::salidas).reversed()).limit(TableroResponse.MAXIMO_LISTA)
                .toList();
        List<RotacionItem> menosMovidos = rotacion.items().stream()
                .filter(i -> i.clase() == ClaseRotacion.SIN_MOVIMIENTO)
                .sorted(Comparator.comparing((RotacionItem i) -> i.valorStock() == null ? BigDecimal.ZERO : i.valorStock())
                        .reversed().thenComparing(RotacionItem::nombre))
                .limit(TableroResponse.MAXIMO_LISTA).toList();
        List<ReabastecimientoItem> porPedir = plan.items().stream()
                .filter(i -> i.estado() == EstadoReabastecimiento.URGENTE || i.estado() == EstadoReabastecimiento.PEDIR_YA
                        || i.estado() == EstadoReabastecimiento.PRONTO)
                .limit(TableroResponse.MAXIMO_LISTA).toList();

        return new TableroResponse(hoy, desde, gasto.total(), gasto.pagado(),
                gasto.total().subtract(gasto.pagado()).max(BigDecimal.ZERO), gasto.ordenes(), actual, anterior,
                variacion, meses, primeros(gasto.porCategoria()), primeros(gasto.porProveedor()),
                rotacion.resumen().valorInventario(), rotacion.resumen().valorSinMovimiento(),
                rotacion.resumen().productos(), rotacion.resumen().agotados(), inventario.bajoMinimo(), rotacion.dias(),
                porClase, masMovidos, menosMovidos, plan.urgentes(), plan.pedirYa(), plan.pronto(), porPedir, sedeId,
                porSede);
    }

    /**
     * Doce meses seguidos hasta el actual; los que no tuvieron compras quedan en cero.
     *
     * @param conDatos  meses que devolvió el reporte de gasto
     * @param mesActual último mes
     * @return doce meses del más antiguo al actual
     */
    static List<GastoMes> doceMeses(List<GastoMes> conDatos, YearMonth mesActual) {
        Map<String, GastoMes> porPeriodo = conDatos.stream()
                .collect(Collectors.toMap(GastoMes::periodo, Function.identity(), (a, b) -> a));
        List<GastoMes> meses = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            String periodo = mesActual.minusMonths(i).toString();
            meses.add(porPeriodo.getOrDefault(periodo,
                    new GastoMes(periodo, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)));
        }
        return meses;
    }

    /**
     * @param items lista ordenada de mayor a menor
     * @return los primeros {@value TableroResponse#MAXIMO_LISTA}
     */
    private static List<GastoItem> primeros(List<GastoItem> items) {
        return items.stream().sorted(Comparator.comparing(GastoItem::total).reversed())
                .limit(TableroResponse.MAXIMO_LISTA).toList();
    }
}
