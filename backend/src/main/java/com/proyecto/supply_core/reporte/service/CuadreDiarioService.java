package com.proyecto.supply_core.reporte.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.reporte.dto.CuadreResponse;
import com.proyecto.supply_core.reporte.entity.CuadreDiario;
import com.proyecto.supply_core.reporte.repository.CuadreDiarioRepository;
import com.proyecto.supply_core.security.context.Roles;

import tools.jackson.databind.json.JsonMapper;

/**
 * Cuadre diario automático (spec 009): compara, para las ventas de un día, lo cobrado contra lo que registró la caja y
 * lo vendido contra lo que salió del inventario, y busca turnos olvidados abiertos. Si algo no cuadra avisa al dueño.
 * Cada consulta lleva {@code empresa_id} a mano (SQL nativo).
 */
@Service
public class CuadreDiarioService {

    /** Horas que un turno puede estar abierto antes de considerarse olvidado. */
    static final int HORAS_TURNO = 24;

    private final JdbcTemplate jdbc;
    private final CuadreDiarioRepository repository;
    private final NotificacionService notificacionService;
    private final JsonMapper json = JsonMapper.builder().build();

    /**
     * @param jdbc                consultas de solo lectura
     * @param repository          resultados guardados
     * @param notificacionService aviso al dueño
     */
    public CuadreDiarioService(JdbcTemplate jdbc, CuadreDiarioRepository repository,
            NotificacionService notificacionService) {
        this.jdbc = jdbc;
        this.repository = repository;
        this.notificacionService = notificacionService;
    }

    /** Detalle que se guarda como JSON (las fechas van como texto). */
    private record Detalle(List<CuadreResponse.VentaDescuadrada> ventas, List<TurnoJson> turnos) {
    }

    /**
     * @param turnoId   turno
     * @param caja      caja
     * @param cajero    cajero
     * @param abiertoEn apertura en ISO
     */
    private record TurnoJson(Long turnoId, String caja, String cajero, String abiertoEn) {
    }

    /**
     * Revisa un día de la empresa del contexto y guarda el resultado (reemplaza el anterior del mismo día).
     *
     * @param fecha día a revisar (no puede ser futuro)
     * @return resultado
     * @throws ParametroInvalidoException si la fecha es futura
     */
    @Transactional
    public CuadreResponse revisar(LocalDate fecha) {
        if (fecha.isAfter(LocalDate.now())) {
            throw new ParametroInvalidoException("No se puede cuadrar un día que no ha llegado");
        }
        Long empresa = EmpresaContexto.requerida();
        Timestamp ini = Timestamp.valueOf(fecha.atStartOfDay());
        Timestamp fin = Timestamp.valueOf(fecha.plusDays(1).atStartOfDay());

        // 1 y 2) Por cada venta del día: lo cobrado por medios de caja contra la caja, y lo vendido contra el inventario
        List<CuadreResponse.VentaDescuadrada> ventas = jdbc.query("""
                SELECT v.id, v.consecutivo,
                  COALESCE((SELECT SUM(p.monto) FROM venta_pago p
                            WHERE p.venta_id = v.id AND p.medio NOT IN ('CREDITO', 'SALDO_FAVOR')), 0) AS cobrado,
                  COALESCE((SELECT SUM(m.monto) FROM movimiento_caja m
                            WHERE m.empresa_id = v.empresa_id AND m.tipo = 'VENTA' AND m.referencia_tipo = 'VENTA'
                              AND m.referencia_id = v.id), 0) AS en_caja,
                  COALESCE((SELECT SUM(l.cantidad_unidad_producto) FROM venta_linea l WHERE l.venta_id = v.id), 0) AS vendidas,
                  COALESCE((SELECT SUM(i.cantidad) FROM movimiento_inventario i
                            WHERE i.empresa_id = v.empresa_id AND i.tipo = 'SALIDA_VENTA' AND i.referencia_tipo = 'VENTA'
                              AND i.referencia_id = v.id), 0) AS salidas
                FROM venta v
                WHERE v.empresa_id = ? AND v.confirmada_en >= ? AND v.confirmada_en < ?
                ORDER BY v.id
                """, (rs, i) -> new CuadreResponse.VentaDescuadrada(rs.getLong("id"), rs.getString("consecutivo"),
                        rs.getBigDecimal("cobrado"), rs.getBigDecimal("en_caja"), rs.getBigDecimal("vendidas"),
                        rs.getBigDecimal("salidas")), empresa, ini, fin);

        // 3) Turnos olvidados: más de 24 horas abiertos al momento de revisar
        LocalDateTime ahora = LocalDateTime.now();
        List<TurnoJson> turnos = jdbc.query("""
                SELECT t.id, c.nombre AS caja, u.nombre_completo AS cajero, t.abierto_en
                FROM turno_caja t JOIN caja c ON c.id = t.caja_id JOIN usuario u ON u.id = t.cajero_usuario_id
                WHERE t.empresa_id = ? AND t.estado = 'ABIERTO' AND t.abierto_en < ?
                ORDER BY t.abierto_en
                """, (rs, i) -> new TurnoJson(rs.getLong("id"), rs.getString("caja"), rs.getString("cajero"),
                        rs.getTimestamp("abierto_en").toLocalDateTime().toString()),
                empresa, Timestamp.valueOf(ahora.minusHours(HORAS_TURNO)));

        BigDecimal cobrado = ventas.stream().map(CuadreResponse.VentaDescuadrada::cobrado).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal enCaja = ventas.stream().map(CuadreResponse.VentaDescuadrada::enCaja).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal vendidas = ventas.stream().map(CuadreResponse.VentaDescuadrada::vendidas).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal salidas = ventas.stream().map(CuadreResponse.VentaDescuadrada::salidas).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<CuadreResponse.VentaDescuadrada> malas = ventas.stream().filter(CuadreDiarioService::descuadrada).toList();
        boolean cuadra = malas.isEmpty() && turnos.isEmpty();

        CuadreDiario c = repository.findByFecha(fecha).orElseGet(CuadreDiario::new);
        c.setFecha(fecha);
        c.setVentasConfirmadas(cobrado.setScale(2));
        c.setVentasEnCaja(enCaja.setScale(2));
        c.setUnidadesVendidas(vendidas.setScale(4));
        c.setUnidadesSalida(salidas.setScale(4));
        c.setTurnosAbiertos24h(turnos.size());
        c.setCuadra(cuadra);
        c.setDetalle(cuadra ? null : json.writeValueAsString(new Detalle(malas, turnos)));
        c.setRevisadoEn(ahora);
        repository.save(c);

        if (!cuadra) {
            notificacionService.notificarRol(Roles.ADMIN_SISTEMA, EventoNotificacion.CUADRE_DESCUADRE,
                    "El cuadre del " + fecha + " no cuadra: " + malas.size() + " venta(s) con caja o inventario distinto y "
                            + turnos.size() + " turno(s) abiertos hace más de " + HORAS_TURNO
                            + " horas. Revíselo en Reportes de ventas, pestaña Cuadre.");
        }
        return respuesta(c);
    }

    /**
     * @param soloDescuadres {@code true} para ver solo los días que no cuadran
     * @param pageable       página (por defecto los días más recientes primero)
     * @return días revisados
     */
    @Transactional(readOnly = true)
    public PageResponse<CuadreResponse> listar(boolean soloDescuadres, Pageable pageable) {
        return PageResponse.of(soloDescuadres ? repository.findByCuadra(false, pageable) : repository.findAllBy(pageable),
                this::respuesta);
    }

    /**
     * @param v venta del día
     * @return {@code true} si su caja o su inventario no coinciden con lo vendido
     */
    static boolean descuadrada(CuadreResponse.VentaDescuadrada v) {
        return v.cobrado().compareTo(v.enCaja()) != 0 || v.vendidas().compareTo(v.salidas()) != 0;
    }

    /**
     * @param c fila guardada
     * @return respuesta con el detalle leído del JSON
     */
    private CuadreResponse respuesta(CuadreDiario c) {
        Detalle d = c.getDetalle() == null ? new Detalle(List.of(), List.of()) : json.readValue(c.getDetalle(), Detalle.class);
        List<CuadreResponse.TurnoOlvidado> turnos = d.turnos() == null ? List.of() : d.turnos().stream()
                .map(t -> new CuadreResponse.TurnoOlvidado(t.turnoId(), t.caja(), t.cajero(), LocalDateTime.parse(t.abiertoEn())))
                .toList();
        return new CuadreResponse(c.getFecha(), c.isCuadra(), c.getVentasConfirmadas(), c.getVentasEnCaja(),
                c.getUnidadesVendidas(), c.getUnidadesSalida(), c.getTurnosAbiertos24h(),
                d.ventas() == null ? List.of() : d.ventas(), turnos, c.getRevisadoEn());
    }
}
