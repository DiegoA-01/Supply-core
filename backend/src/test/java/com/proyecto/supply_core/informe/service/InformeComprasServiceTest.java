package com.proyecto.supply_core.informe.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.catalogo.entity.UnidadMedida;
import com.proyecto.supply_core.catalogo.service.UnidadMedidaService;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompra;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompraLinea;
import com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud;
import com.proyecto.supply_core.empresa.repository.CentroCostoRepository;
import com.proyecto.supply_core.informe.dto.InformeContenido.SolicitudFila;
import com.proyecto.supply_core.informe.repository.InformeComprasRepository;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.reporte.service.ReporteService;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

/** Detalle de lo pedido en el informe de compras: productos, cantidades y comprobación del monto estimado. */
class InformeComprasServiceTest {

    private final InformeComprasRepository informes = mock(InformeComprasRepository.class);
    private final UnidadMedidaService unidades = mock(UnidadMedidaService.class);
    private final InformeComprasService service = new InformeComprasService(informes, mock(UsuarioRepository.class),
            mock(CentroCostoRepository.class), mock(ReporteService.class), mock(NotificacionService.class),
            mock(AuditoriaService.class), unidades,
            mock(com.proyecto.supply_core.reporte.service.VentaReporteService.class),
            mock(com.proyecto.supply_core.plataforma.service.AccesoEmpresaService.class));

    private final UnidadMedida und = unidad(1L, "UND");
    private final UnidadMedida caja = unidad(2L, "CAJA24");

    /** Sin RFQ, cotizaciones ni adjudicaciones; 1 caja = 24 unidades. */
    @BeforeEach
    void setUp() {
        when(informes.rfqsCreadas(any(), any())).thenReturn(List.of());
        when(informes.cotizacionesRecibidas(any(), any())).thenReturn(List.of());
        when(informes.adjudicaciones(any(), any())).thenReturn(List.of());
        when(unidades.convertir(any(), eq(1L), eq(1L))).thenAnswer(i -> i.getArgument(0));
        when(unidades.convertir(any(), eq(2L), eq(1L))).thenAnswer(i -> ((BigDecimal) i.getArgument(0)).multiply(new BigDecimal("24")));
    }

    @Test
    void cadaLineaMuestraCantidadCostoYSubtotalYElTotalCuadraConElEstimado() {
        SolicitudCompra s = solicitud("1110000.00",
                linea(producto("CAB-12", "Cable 12 AWG", "2800"), und, "150"),       // 150 x 2.800 = 420.000
                linea(producto("BRO-03", "Brocha 3 pulgadas", "9000"), caja, "3.2")); // 3,2 cajas = 76,8 und x 9.000 = 691.200
        when(informes.solicitudesEnviadas(any(), any())).thenReturn(List.of(s));

        SolicitudFila f = service.vistaPrevia(LocalDate.now(), LocalDate.now()).solicitudes().get(0);

        assertEquals(2, f.lineas().size());
        assertEquals(new BigDecimal("150"), f.lineas().get(0).cantidad());
        assertEquals(0, new BigDecimal("420000").compareTo(f.lineas().get(0).subtotal()));
        assertEquals("CAJA24", f.lineas().get(1).unidad());
        assertEquals(0, new BigDecimal("76.8").compareTo(f.lineas().get(1).cantidadEnUnidadProducto()));
        assertEquals(0, new BigDecimal("691200").compareTo(f.lineas().get(1).subtotal()));
        assertEquals(new BigDecimal("1111200.00"), f.montoCalculado());
        assertFalse(f.cuadra()); // el estimado guardado (1.110.000) no coincide: el costo cambió después de enviarla
    }

    @Test
    void siLaCuentaDaLoMismoQueElEstimadoCuadra() {
        SolicitudCompra s = solicitud("420000.00", linea(producto("CAB-12", "Cable 12 AWG", "2800"), und, "150"));
        when(informes.solicitudesEnviadas(any(), any())).thenReturn(List.of(s));

        SolicitudFila f = service.vistaPrevia(LocalDate.now(), LocalDate.now()).solicitudes().get(0);

        assertTrue(f.cuadra());
        assertEquals(new BigDecimal("420000.00"), f.montoCalculado());
    }

    @Test
    void unProductoSinCostoNoSePuedeComprobar() {
        SolicitudCompra s = solicitud("420000.00",
                linea(producto("CAB-12", "Cable 12 AWG", "2800"), und, "150"),
                linea(producto("LAM-09", "Lámpara LED", null), und, "10"));
        when(informes.solicitudesEnviadas(any(), any())).thenReturn(List.of(s));

        SolicitudFila f = service.vistaPrevia(LocalDate.now(), LocalDate.now()).solicitudes().get(0);

        assertNull(f.lineas().get(1).subtotal());
        assertNull(f.montoCalculado());
        assertNull(f.cuadra());
    }

    // ------------------------------------------------------------------ datos

    /**
     * @param id     id de la unidad
     * @param codigo código
     * @return unidad de medida
     */
    private static UnidadMedida unidad(Long id, String codigo) {
        UnidadMedida u = new UnidadMedida();
        u.setId(id);
        u.setCodigo(codigo);
        return u;
    }

    /**
     * @param codigo código
     * @param nombre nombre
     * @param costo  costo de referencia por unidad ({@code null} = sin costo)
     * @return producto que se cuenta por unidad
     */
    private Producto producto(String codigo, String nombre, String costo) {
        Producto p = new Producto();
        p.setCodigo(codigo);
        p.setNombre(nombre);
        p.setUnidadMedida(und);
        p.setCostoReferencia(costo == null ? null : new BigDecimal(costo));
        return p;
    }

    /**
     * @param p        producto
     * @param unidad   unidad en que se pide
     * @param cantidad cantidad pedida
     * @return línea de solicitud
     */
    private static SolicitudCompraLinea linea(Producto p, UnidadMedida unidad, String cantidad) {
        SolicitudCompraLinea l = new SolicitudCompraLinea();
        l.setProducto(p);
        l.setUnidadMedida(unidad);
        l.setCantidad(new BigDecimal(cantidad));
        return l;
    }

    /**
     * @param montoEstimado monto que se guardó al enviarla
     * @param lineas        productos pedidos
     * @return solicitud enviada
     */
    private static SolicitudCompra solicitud(String montoEstimado, SolicitudCompraLinea... lineas) {
        SolicitudCompra s = new SolicitudCompra();
        s.setConsecutivo("SOL-000009");
        s.setEstado(EstadoSolicitud.PENDIENTE_APROBACION);
        s.setUsuarioSolicitanteId(10L);
        s.setMontoEstimado(new BigDecimal(montoEstimado));
        s.setEnviadoEn(LocalDateTime.now());
        for (SolicitudCompraLinea l : lineas) {
            s.agregarLinea(l);
        }
        return s;
    }
}
