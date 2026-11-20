package com.proyecto.supply_core.reporte.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.reporte.dto.VentaReporte;
import com.proyecto.supply_core.reporte.enums.AgrupacionVenta;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

/**
 * Reporte de ventas netas (spec 009): lee las líneas vendidas, las devueltas y los cobros del periodo con SQL de solo
 * lectura (con {@code empresa_id} a mano) y los suma {@link AgregadorVentas}. El cajero solo ve sus ventas y el
 * vendedor solo las de sus pedidos; supervisión, el dueño y auditoría ven todo.
 */
@Service
public class VentaReporteService {

    /** Periodo más largo que se puede pedir de una vez. */
    public static final int DIAS_MAXIMO = 366;

    /** Nombre de cada medio de pago (y de lo devuelto a la cuenta del cliente). */
    public static final Map<String, String> MEDIOS = Map.of("EFECTIVO", "Efectivo", "TARJETA_DEBITO", "Tarjeta débito",
            "TARJETA_CREDITO", "Tarjeta crédito", "TRANSFERENCIA", "Transferencia", "BILLETERA", "Billetera digital",
            "CREDITO", "Crédito", "SALDO_FAVOR", "Saldo a favor", AgregadorVentas.A_CUENTA, "Devuelto a crédito o saldo");

    private static final Set<String> VEN_TODO = Set.of(Roles.ADMIN_SISTEMA, Roles.SUPERVISOR_VENTAS, Roles.AUDITOR);

    private static final String DIMENSIONES = """
            v.id AS venta_id, v.caja_id, c.nombre AS caja, v.cajero_usuario_id, uc.nombre_completo AS cajero,
            v.vendedor_usuario_id, uv.nombre_completo AS vendedor, v.cliente_id, cl.nombre AS cliente, l.producto_id,
            CONCAT(p.codigo, ' - ', p.nombre) AS producto, p.categoria_producto_id, cat.nombre AS categoria,
            l.costo_unitario
            """;
    private static final String UNIONES = """
            JOIN caja c ON c.id = v.caja_id
            JOIN usuario uc ON uc.id = v.cajero_usuario_id
            LEFT JOIN usuario uv ON uv.id = v.vendedor_usuario_id
            JOIN cliente cl ON cl.id = v.cliente_id
            JOIN producto p ON p.id = l.producto_id
            JOIN categoria_producto cat ON cat.id = p.categoria_producto_id
            """;

    private final JdbcTemplate jdbc;

    /**
     * @param jdbc consultas de solo lectura
     */
    public VentaReporteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Quién puede ver qué: {@code null} = todo; si no, solo las ventas de ese usuario como cajero o como vendedor.
     *
     * @param u usuario de la sesión
     * @return usuario al que se limita el reporte
     */
    static Long limiteDe(UsuarioAutenticado u) {
        return u.roles().stream().anyMatch(VEN_TODO::contains) ? null : u.id();
    }

    /**
     * @param desde   primer día (vacío = primer día del mes en curso)
     * @param hasta   último día (vacío = hoy)
     * @param agrupar agrupación (vacío = por día)
     * @return reporte de ventas netas
     * @throws ParametroInvalidoException si el periodo está al revés o pasa {@value #DIAS_MAXIMO} días
     */
    @Transactional(readOnly = true)
    public VentaReporte reporte(LocalDate desde, LocalDate hasta, AgrupacionVenta agrupar) {
        LocalDate[] p = periodo(desde, hasta);
        Long propio = limiteDe(SesionActual.usuario());
        AgrupacionVenta a = agrupar == null ? AgrupacionVenta.DIA : agrupar;
        Datos d = leer(p[0], p[1], propio, a == AgrupacionVenta.MEDIO_PAGO);
        return AgregadorVentas.armar(p[0], p[1], a, d.lineas(), d.cobros(), d.redondeo(), propio != null);
    }

    /**
     * Mismo cálculo para otros servicios (tablero, informe de compras), sin filtro por usuario.
     *
     * @param desde   primer día
     * @param hasta   último día
     * @param agrupar agrupación
     * @return reporte de toda la empresa
     */
    @Transactional(readOnly = true)
    public VentaReporte deEmpresa(LocalDate desde, LocalDate hasta, AgrupacionVenta agrupar) {
        Datos d = leer(desde, hasta, null, agrupar == AgrupacionVenta.MEDIO_PAGO);
        return AgregadorVentas.armar(desde, hasta, agrupar, d.lineas(), d.cobros(), d.redondeo(), false);
    }

    /**
     * @param desde primer día pedido
     * @param hasta último día pedido
     * @return periodo con los valores por defecto aplicados
     * @throws ParametroInvalidoException si está al revés o es muy largo
     */
    public static LocalDate[] periodo(LocalDate desde, LocalDate hasta) {
        LocalDate h = hasta == null ? LocalDate.now() : hasta;
        LocalDate d = desde == null ? h.withDayOfMonth(1) : desde;
        if (d.isAfter(h)) {
            throw new ParametroInvalidoException("La fecha inicial no puede ser posterior a la final");
        }
        if (ChronoUnit.DAYS.between(d, h) >= DIAS_MAXIMO) {
            throw new ParametroInvalidoException("El periodo no puede pasar de " + DIAS_MAXIMO + " días");
        }
        return new LocalDate[] { d, h };
    }

    /**
     * Lo leído para un periodo.
     *
     * @param lineas   líneas vendidas y devueltas
     * @param cobros   cobros y reembolsos (vacío si no se pidió por medio)
     * @param redondeo redondeo cobrado menos el devuelto
     */
    private record Datos(List<AgregadorVentas.Linea> lineas, List<AgregadorVentas.Cobro> cobros, BigDecimal redondeo) {
    }

    /**
     * @param desde      primer día
     * @param hasta      último día
     * @param propio     usuario al que se limita ({@code null} = todos)
     * @param conCobros  si hay que leer cobros y reembolsos
     * @return datos del periodo
     */
    private Datos leer(LocalDate desde, LocalDate hasta, Long propio, boolean conCobros) {
        Long empresa = EmpresaContexto.requerida();
        Timestamp ini = Timestamp.valueOf(desde.atStartOfDay());
        Timestamp fin = Timestamp.valueOf(hasta.plusDays(1).atStartOfDay());
        String filtro = propio == null ? "" : " AND (v.cajero_usuario_id = ? OR v.vendedor_usuario_id = ?)";
        List<Object> args = new ArrayList<>(List.of(empresa, ini, fin));
        if (propio != null) {
            args.add(propio);
            args.add(propio);
        }
        Object[] a = args.toArray();

        List<AgregadorVentas.Linea> lineas = new ArrayList<>(jdbc.query("SELECT " + DIMENSIONES
                + ", DATE(v.confirmada_en) AS fecha, l.cantidad_unidad_producto AS unidades, l.total_linea AS total,"
                + " l.base_gravable AS base FROM venta_linea l JOIN venta v ON v.id = l.venta_id " + UNIONES
                + " WHERE v.empresa_id = ? AND v.estado = 'CONFIRMADA' AND v.confirmada_en >= ? AND v.confirmada_en < ?"
                + filtro, (rs, i) -> linea(rs, rs.getBigDecimal("unidades"), rs.getBigDecimal("total"),
                        rs.getBigDecimal("base"), false), a));
        lineas.addAll(jdbc.query("SELECT " + DIMENSIONES
                + ", DATE(d.creado_en) AS fecha, dl.cantidad_unidad_producto AS unidades, dl.valor AS total,"
                + " l.base_gravable * dl.cantidad / l.cantidad AS base FROM devolucion_venta_linea dl"
                + " JOIN devolucion_venta d ON d.id = dl.devolucion_id JOIN venta_linea l ON l.id = dl.venta_linea_id"
                + " JOIN venta v ON v.id = d.venta_id " + UNIONES
                + " WHERE d.empresa_id = ? AND v.estado = 'CONFIRMADA' AND d.creado_en >= ? AND d.creado_en < ?" + filtro,
                (rs, i) -> linea(rs, rs.getBigDecimal("unidades"), rs.getBigDecimal("total"), rs.getBigDecimal("base"),
                        true), a));

        BigDecimal cobrado = jdbc.queryForObject("SELECT COALESCE(SUM(v.redondeo), 0) FROM venta v WHERE v.empresa_id = ?"
                + " AND v.estado = 'CONFIRMADA' AND v.confirmada_en >= ? AND v.confirmada_en < ?" + filtro, BigDecimal.class, a);
        BigDecimal devuelto = jdbc.queryForObject("SELECT COALESCE(SUM(d.total_reembolso - (SELECT COALESCE(SUM(x.valor), 0)"
                + " FROM devolucion_venta_linea x WHERE x.devolucion_id = d.id)), 0) FROM devolucion_venta d"
                + " JOIN venta v ON v.id = d.venta_id WHERE d.empresa_id = ? AND v.estado = 'CONFIRMADA'"
                + " AND d.creado_en >= ? AND d.creado_en < ?" + filtro, BigDecimal.class, a);

        List<AgregadorVentas.Cobro> cobros = new ArrayList<>();
        if (conCobros) {
            cobros.addAll(jdbc.query("SELECT v.id, DATE(v.confirmada_en) AS fecha, p.medio, p.monto FROM venta_pago p"
                    + " JOIN venta v ON v.id = p.venta_id WHERE v.empresa_id = ? AND v.estado = 'CONFIRMADA'"
                    + " AND v.confirmada_en >= ? AND v.confirmada_en < ?" + filtro,
                    (rs, i) -> cobro(rs.getLong("id"), rs, rs.getString("medio"), rs.getBigDecimal("monto"), false), a));
            // Cada devolución sale por su medio de caja; lo que no salió de la caja fue a la deuda o al saldo a favor
            jdbc.query("SELECT d.id, v.id AS venta_id, DATE(d.creado_en) AS fecha, d.total_reembolso,"
                    + " m.medio_pago, COALESCE(-SUM(m.monto), 0) AS por_caja FROM devolucion_venta d"
                    + " JOIN venta v ON v.id = d.venta_id LEFT JOIN movimiento_caja m ON m.empresa_id = d.empresa_id"
                    + " AND m.tipo = 'DEVOLUCION' AND m.referencia_tipo = 'DEVOLUCION_VENTA' AND m.referencia_id = d.id"
                    + " WHERE d.empresa_id = ? AND v.estado = 'CONFIRMADA' AND d.creado_en >= ? AND d.creado_en < ?" + filtro
                    + " GROUP BY d.id, v.id, d.creado_en, d.total_reembolso, m.medio_pago", rs -> {
                        long venta = rs.getLong("venta_id");
                        BigDecimal total = rs.getBigDecimal("total_reembolso");
                        BigDecimal porCaja = rs.getBigDecimal("por_caja");
                        if (rs.getString("medio_pago") != null && porCaja.signum() > 0) {
                            cobros.add(cobro(venta, rs, rs.getString("medio_pago"), porCaja, true));
                        }
                        BigDecimal aCuenta = total.subtract(porCaja);
                        if (aCuenta.signum() > 0) {
                            cobros.add(cobro(venta, rs, AgregadorVentas.A_CUENTA, aCuenta, true));
                        }
                    }, a);
        }
        return new Datos(lineas, cobros, cobrado.subtract(devuelto));
    }

    /**
     * @param rs         fila con las dimensiones
     * @param unidades   unidades
     * @param total      valor con IVA
     * @param base       valor sin IVA
     * @param devolucion si resta
     * @return línea para el agregador
     * @throws SQLException si la fila no trae una columna
     */
    private static AgregadorVentas.Linea linea(ResultSet rs, BigDecimal unidades, BigDecimal total, BigDecimal base,
            boolean devolucion) throws SQLException {
        BigDecimal costoUnitario = rs.getBigDecimal("costo_unitario");
        BigDecimal costo = costoUnitario == null ? null : costoUnitario.multiply(unidades).setScale(2, RoundingMode.HALF_UP);
        Long vendedor = rs.getObject("vendedor_usuario_id") == null ? null : rs.getLong("vendedor_usuario_id");
        return new AgregadorVentas.Linea(rs.getLong("venta_id"), rs.getDate("fecha").toLocalDate(), rs.getLong("caja_id"),
                rs.getString("caja"), rs.getLong("cajero_usuario_id"), rs.getString("cajero"),
                vendedor, rs.getString("vendedor"), rs.getLong("cliente_id"),
                rs.getString("cliente"), rs.getLong("producto_id"), rs.getString("producto"),
                rs.getLong("categoria_producto_id"), rs.getString("categoria"), unidades, total,
                base.setScale(2, RoundingMode.HALF_UP), costo, devolucion);
    }

    /**
     * @param ventaId    venta
     * @param rs         fila con la fecha
     * @param medio      código del medio
     * @param monto      valor
     * @param devolucion si resta
     * @return cobro para el agregador
     * @throws SQLException si la fila no trae la fecha
     */
    private static AgregadorVentas.Cobro cobro(long ventaId, ResultSet rs, String medio, BigDecimal monto, boolean devolucion)
            throws SQLException {
        return new AgregadorVentas.Cobro(ventaId, rs.getDate("fecha").toLocalDate(), medio, MEDIOS.getOrDefault(medio, medio),
                monto, devolucion);
    }
}
