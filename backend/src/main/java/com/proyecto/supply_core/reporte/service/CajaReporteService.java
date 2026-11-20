package com.proyecto.supply_core.reporte.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.reporte.dto.CajaReporte;

/**
 * Reporte de caja (spec 009): lo que pasó en los turnos de un periodo. Solo lectura, con {@code empresa_id} a mano en
 * cada consulta. Lo ven supervisión, el dueño y auditoría.
 */
@Service
public class CajaReporteService {

    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2);

    private final JdbcTemplate jdbc;

    /**
     * @param jdbc consultas de solo lectura
     */
    public CajaReporteService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param desde primer día (vacío = primer día del mes en curso)
     * @param hasta último día (vacío = hoy)
     * @return reporte de caja
     */
    @Transactional(readOnly = true)
    public CajaReporte reporte(LocalDate desde, LocalDate hasta) {
        LocalDate[] p = VentaReporteService.periodo(desde, hasta);
        Object[] a = { EmpresaContexto.requerida(), Timestamp.valueOf(p[0].atStartOfDay()),
                Timestamp.valueOf(p[1].plusDays(1).atStartOfDay()) };

        List<Object[]> crudos = new ArrayList<>();
        List<CajaReporte.Turno> turnos = jdbc.query("""
                SELECT t.id, t.cajero_usuario_id, c.nombre AS caja, u.nombre_completo AS cajero, t.estado, t.abierto_en,
                       t.cerrado_en, t.esperado_efectivo, t.contado_efectivo, t.diferencia, t.requiere_aprobacion,
                       ua.nombre_completo AS aprobado_por, t.explicacion_aprobacion
                FROM turno_caja t
                JOIN caja c ON c.id = t.caja_id
                JOIN usuario u ON u.id = t.cajero_usuario_id
                LEFT JOIN usuario ua ON ua.id = t.aprobado_por_usuario_id
                WHERE t.empresa_id = ? AND t.abierto_en >= ? AND t.abierto_en < ?
                ORDER BY t.abierto_en DESC, t.id DESC
                """, (rs, i) -> {
                    String estado = rs.getString("estado");
                    boolean porAprobar = rs.getBoolean("requiere_aprobacion") && "CERRADO".equals(estado);
                    CajaReporte.Turno t = new CajaReporte.Turno(rs.getLong("id"), rs.getString("caja"),
                            rs.getString("cajero"), estado, fecha(rs, "abierto_en"), fecha(rs, "cerrado_en"),
                            rs.getBigDecimal("esperado_efectivo"), rs.getBigDecimal("contado_efectivo"),
                            rs.getBigDecimal("diferencia"), porAprobar, rs.getString("aprobado_por"),
                            rs.getString("explicacion_aprobacion"));
                    crudos.add(new Object[] { rs.getLong("cajero_usuario_id"), t });
                    return t;
                }, a);

        List<CajaReporte.Cajero> porCajero = porCajero(crudos);

        List<CajaReporte.Movimiento> movimientos = jdbc.query("""
                SELECT m.tipo, c.nombre AS caja, m.medio_pago, ABS(m.monto) AS monto, m.motivo, m.creado_en,
                       u.nombre_completo AS registro, ua.nombre_completo AS autorizo
                FROM movimiento_caja m
                JOIN turno_caja t ON t.id = m.turno_id
                JOIN caja c ON c.id = t.caja_id
                JOIN usuario u ON u.id = m.usuario_id
                LEFT JOIN usuario ua ON ua.id = m.autorizado_por_usuario_id
                WHERE m.empresa_id = ? AND m.tipo IN ('RETIRO', 'GASTO') AND m.creado_en >= ? AND m.creado_en < ?
                ORDER BY m.creado_en DESC, m.id DESC
                """, (rs, i) -> new CajaReporte.Movimiento(rs.getString("tipo"), rs.getString("caja"),
                        rs.getString("medio_pago"), rs.getBigDecimal("monto"), rs.getString("motivo"),
                        rs.getString("registro"), rs.getString("autorizo"), fecha(rs, "creado_en")), a);

        List<CajaReporte.Anulacion> anulaciones = jdbc.query("""
                SELECT v.id, v.consecutivo, v.total + v.redondeo AS total, v.motivo_anulacion, v.anulada_en,
                       u.nombre_completo AS anulo, ua.nombre_completo AS autorizo
                FROM venta v
                LEFT JOIN usuario u ON u.id = v.anulada_por_usuario_id
                LEFT JOIN usuario ua ON ua.id = v.anulacion_autorizada_por_usuario_id
                WHERE v.empresa_id = ? AND v.estado = 'ANULADA' AND v.anulada_en >= ? AND v.anulada_en < ?
                ORDER BY v.anulada_en DESC, v.id DESC
                """, (rs, i) -> new CajaReporte.Anulacion(rs.getLong("id"), rs.getString("consecutivo"),
                        rs.getBigDecimal("total"), rs.getString("motivo_anulacion"), rs.getString("anulo"),
                        rs.getString("autorizo"), fecha(rs, "anulada_en")), a);

        List<CajaReporte.Autorizado> autorizados = jdbc.query("""
                SELECT v.id, v.consecutivo, CONCAT(p.codigo, ' - ', p.nombre) AS producto, l.descuento_pct,
                       l.descuento_valor, v.confirmada_en, uc.nombre_completo AS cajero, ua.nombre_completo AS autorizo
                FROM venta_linea l
                JOIN venta v ON v.id = l.venta_id
                JOIN producto p ON p.id = l.producto_id
                JOIN usuario uc ON uc.id = v.cajero_usuario_id
                JOIN usuario ua ON ua.id = l.autorizado_por_usuario_id
                WHERE v.empresa_id = ? AND v.estado = 'CONFIRMADA' AND l.autorizado_por_usuario_id IS NOT NULL
                  AND v.confirmada_en >= ? AND v.confirmada_en < ?
                ORDER BY v.confirmada_en DESC, l.id
                """, (rs, i) -> {
                    BigDecimal pct = rs.getBigDecimal("descuento_pct");
                    return new CajaReporte.Autorizado(rs.getLong("id"), rs.getString("consecutivo"),
                            rs.getString("producto"), pct != null && pct.signum() > 0 ? "Descuento" : "Precio bajo el mínimo",
                            pct, rs.getBigDecimal("descuento_valor"), rs.getString("cajero"), rs.getString("autorizo"),
                            fecha(rs, "confirmada_en"));
                }, a);

        BigDecimal faltantes = porCajero.stream().map(CajaReporte.Cajero::faltantes).reduce(CERO, BigDecimal::add);
        BigDecimal sobrantes = porCajero.stream().map(CajaReporte.Cajero::sobrantes).reduce(CERO, BigDecimal::add);
        CajaReporte.Totales totales = new CajaReporte.Totales(turnos.size(), faltantes.add(sobrantes), faltantes,
                sobrantes, turnos.stream().filter(CajaReporte.Turno::porAprobar).count(),
                suma(movimientos.stream().filter(m -> "RETIRO".equals(m.tipo())).map(CajaReporte.Movimiento::monto).toList()),
                suma(movimientos.stream().filter(m -> "GASTO".equals(m.tipo())).map(CajaReporte.Movimiento::monto).toList()),
                suma(anulaciones.stream().map(CajaReporte.Anulacion::total).toList()),
                suma(autorizados.stream().map(CajaReporte.Autorizado::descuentoValor).toList()));
        return new CajaReporte(p[0], p[1], turnos, porCajero, movimientos, anulaciones, autorizados, totales);
    }

    /**
     * @param crudos pares (cajero, turno)
     * @return diferencias por cajero, del mayor faltante al menor
     */
    static List<CajaReporte.Cajero> porCajero(List<Object[]> crudos) {
        Map<Long, List<CajaReporte.Turno>> grupos = new LinkedHashMap<>();
        Map<Long, String> nombres = new LinkedHashMap<>();
        for (Object[] c : crudos) {
            CajaReporte.Turno t = (CajaReporte.Turno) c[1];
            grupos.computeIfAbsent((Long) c[0], k -> new ArrayList<>()).add(t);
            nombres.putIfAbsent((Long) c[0], t.cajero());
        }
        List<CajaReporte.Cajero> filas = new ArrayList<>();
        grupos.forEach((id, lista) -> {
            BigDecimal faltantes = suma(lista.stream().map(CajaReporte.Turno::diferencia)
                    .filter(d -> d != null && d.signum() < 0).toList());
            BigDecimal sobrantes = suma(lista.stream().map(CajaReporte.Turno::diferencia)
                    .filter(d -> d != null && d.signum() > 0).toList());
            filas.add(new CajaReporte.Cajero(id, nombres.get(id), lista.size(),
                    lista.stream().filter(t -> t.cerradoEn() != null).count(), faltantes.add(sobrantes), faltantes,
                    sobrantes, lista.stream().filter(CajaReporte.Turno::porAprobar).count()));
        });
        filas.sort(Comparator.comparing(CajaReporte.Cajero::faltantes).thenComparing(CajaReporte.Cajero::cajero));
        return filas;
    }

    /**
     * @param valores valores ({@code null} cuenta como 0)
     * @return suma con 2 decimales
     */
    private static BigDecimal suma(List<BigDecimal> valores) {
        return valores.stream().filter(v -> v != null).reduce(CERO, BigDecimal::add).setScale(2);
    }

    /**
     * @param rs     fila
     * @param campo  columna de fecha y hora
     * @return fecha y hora ({@code null} si está vacía)
     * @throws SQLException si la columna no existe
     */
    private static LocalDateTime fecha(ResultSet rs, String campo) throws SQLException {
        Timestamp t = rs.getTimestamp(campo);
        return t == null ? null : t.toLocalDateTime();
    }
}
