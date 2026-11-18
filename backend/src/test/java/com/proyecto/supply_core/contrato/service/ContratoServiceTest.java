package com.proyecto.supply_core.contrato.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.catalogo.entity.UnidadMedida;
import com.proyecto.supply_core.catalogo.service.UnidadMedidaService;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompra;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompraLinea;
import com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud;
import com.proyecto.supply_core.compra.solicitud.repository.SolicitudCompraRepository;
import com.proyecto.supply_core.contrato.dto.ConsumoPrecio;
import com.proyecto.supply_core.contrato.dto.ContratoRequest;
import com.proyecto.supply_core.contrato.entity.Contrato;
import com.proyecto.supply_core.contrato.entity.ContratoPrecio;
import com.proyecto.supply_core.contrato.enums.EstadoContrato;
import com.proyecto.supply_core.contrato.repository.ContratoRepository;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.proveedor.entity.Proveedor;
import com.proyecto.supply_core.proveedor.enums.EstadoProveedor;
import com.proyecto.supply_core.proveedor.repository.ProveedorRepository;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

class ContratoServiceTest {

    private final ContratoRepository contratos = mock(ContratoRepository.class);
    private final ProveedorRepository proveedores = mock(ProveedorRepository.class);
    private final ProductoRepository productos = mock(ProductoRepository.class);
    private final SolicitudCompraRepository solicitudes = mock(SolicitudCompraRepository.class);
    private final UnidadMedidaService unidades = mock(UnidadMedidaService.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final ContratoService service = new ContratoService(contratos, proveedores, productos, solicitudes,
            unidades, auditoria);

    private final LocalDate hoy = LocalDate.now();
    private Proveedor tech;
    private Producto cable;
    private Producto breaker;

    @BeforeEach
    void setUp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(SesionActual.ATRIBUTO,
                new UsuarioAutenticado(20L, "a@x.com", "Admin", Set.of("ADMIN_COMPRAS"), null, 1L));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        UnidadMedida und = new UnidadMedida();
        und.setId(1L);
        und.setCodigo("UND");
        tech = new Proveedor();
        tech.setId(40L);
        tech.setRazonSocial("Tech SAS");
        tech.setEstado(EstadoProveedor.HABILITADO);
        cable = producto(100L, "Cable THHN", und);
        breaker = producto(101L, "Breaker 20 A", und);
        when(proveedores.findById(40L)).thenReturn(Optional.of(tech));
        when(productos.findById(100L)).thenReturn(Optional.of(cable));
        when(productos.findById(101L)).thenReturn(Optional.of(breaker));
        when(unidades.convertir(any(), any(), any())).thenAnswer(i -> i.getArgument(0));
        when(contratos.save(any())).thenAnswer(i -> {
            Contrato c = i.getArgument(0);
            c.setId(5L);
            long n = 500;
            for (ContratoPrecio p : c.getPrecios()) {
                p.setId(n++);
            }
            return c;
        });
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void crearNormalizaElNumeroGuardaLosPreciosYAudita() {
        var r = service.crear(request("  ct-2026-01 ", hoy, hoy.plusMonths(6), precio(100L, "95000", "19", "50")));

        assertEquals("CT-2026-01", r.numero());
        assertEquals(EstadoContrato.ACTIVO, r.estado());
        assertTrue(r.vigente());
        assertEquals(new BigDecimal("95000.0000"), r.precios().get(0).precioUnitario());
        assertEquals(new BigDecimal("19.00"), r.precios().get(0).impuestoPct());
        assertEquals(new BigDecimal("50.0000"), r.precios().get(0).disponible());
        verify(auditoria).exito(eq(AccionAuditoria.CONTRATO_CREAR), eq(EntidadAuditada.CONTRATO), eq(5L), anyString());
    }

    @Test
    void crearRechazaProveedorNoHabilitadoNumeroRepetidoVigenciaPasadaYProductoInactivo() {
        tech.setEstado(EstadoProveedor.SUSPENDIDO);
        assertEquals("PROVEEDOR_NO_HABILITADO", codigo(() -> service.crear(
                request("CT-1", hoy, hoy.plusDays(5), precio(100L, "1", "0", null)))));
        tech.setEstado(EstadoProveedor.HABILITADO);

        when(contratos.existsByNumeroIgnoreCase("CT-1")).thenReturn(true);
        assertEquals("NUMERO_DUPLICADO", codigo(() -> service.crear(
                request("ct-1", hoy, hoy.plusDays(5), precio(100L, "1", "0", null)))));

        assertEquals("VIGENCIA_PASADA", codigo(() -> service.crear(
                request("CT-2", hoy.minusDays(30), hoy.minusDays(1), precio(100L, "1", "0", null)))));

        cable.setActivo(false);
        assertEquals("PRODUCTO_INACTIVO", codigo(() -> service.crear(
                request("CT-3", hoy, hoy.plusDays(5), precio(100L, "1", "0", null)))));
        assertThrows(NoEncontradoException.class, () -> service.crear(new ContratoRequest(99L, "CT-4", null, hoy,
                hoy.plusDays(5), List.of(precio(100L, "1", "0", null)))));
        verify(contratos, never()).save(any());
    }

    @Test
    void noPuedeHaberDosContratosActivosQueSeCrucenParaElMismoProducto() {
        when(contratos.contarSolapados(eq(40L), eq(List.of(100L, 101L)), eq(hoy), eq(hoy.plusDays(30)), eq(null)))
                .thenReturn(1L);

        assertEquals("CONTRATO_SOLAPADO", codigo(() -> service.crear(request("CT-9", hoy, hoy.plusDays(30),
                precio(100L, "1", "0", null), precio(101L, "1", "0", null)))));
    }

    @Test
    void cambiarLaVigenciaSoloEnContratosActivosYSinSolape() {
        Contrato c = contrato(5L, EstadoContrato.ACTIVO, hoy.plusDays(5), hoy.plusDays(20), precioDe(500L, cable, "100", "0", null));
        when(contratos.bloquear(5L)).thenReturn(Optional.of(c));
        when(contratos.findDetalleById(5L)).thenReturn(Optional.of(c));

        var r = service.cambiarVigencia(5L, hoy.plusDays(90));
        assertEquals(hoy.plusDays(90), r.vigenteHasta());
        verify(auditoria).exito(eq(AccionAuditoria.CONTRATO_EDITAR), eq(EntidadAuditada.CONTRATO), eq(5L), anyString());

        assertEquals("VIGENCIA_PASADA", codigo(() -> service.cambiarVigencia(5L, hoy.minusDays(1))));
        assertEquals("VIGENCIA_INVALIDA", codigo(() -> service.cambiarVigencia(5L, hoy.plusDays(2)))); // antes del inicio
        when(contratos.contarSolapados(eq(40L), any(), any(), any(), eq(5L))).thenReturn(1L);
        assertEquals("CONTRATO_SOLAPADO", codigo(() -> service.cambiarVigencia(5L, hoy.plusDays(200))));

        c.setEstado(EstadoContrato.ANULADO);
        assertEquals("CONTRATO_ANULADO", codigo(() -> service.cambiarVigencia(5L, hoy.plusDays(30))));
    }

    @Test
    void anularGuardaElMotivoYNoSePuedeRepetir() {
        Contrato c = contrato(5L, EstadoContrato.ACTIVO, hoy, hoy.plusDays(20), precioDe(500L, cable, "100", "0", null));
        when(contratos.bloquear(5L)).thenReturn(Optional.of(c));
        when(contratos.findDetalleById(5L)).thenReturn(Optional.of(c));

        var r = service.anular(5L, "  Cambió el proveedor  ");

        assertEquals(EstadoContrato.ANULADO, r.estado());
        assertFalse(r.vigente());
        assertEquals("Cambió el proveedor", r.motivoAnulacion());
        assertEquals(20L, c.getAnuladoPorUsuarioId());
        verify(auditoria).exito(eq(AccionAuditoria.CONTRATO_ANULAR), eq(EntidadAuditada.CONTRATO), eq(5L), anyString());
        assertEquals("CONTRATO_ANULADO", codigo(() -> service.anular(5L, "otra vez")));
    }

    @Test
    void unPrecioVencidoOAnuladoNuncaSeAplica() {
        ContratoService.exigirVigente(contrato(1L, EstadoContrato.ACTIVO, hoy, hoy, precioDe(1L, cable, "1", "0", null)));

        assertEquals("CONTRATO_NO_VIGENTE", codigo(() -> ContratoService.exigirVigente(
                contrato(2L, EstadoContrato.ACTIVO, hoy.minusDays(30), hoy.minusDays(1), precioDe(1L, cable, "1", "0", null)))));
        assertEquals("CONTRATO_NO_VIGENTE", codigo(() -> ContratoService.exigirVigente(
                contrato(3L, EstadoContrato.ACTIVO, hoy.plusDays(1), hoy.plusDays(30), precioDe(1L, cable, "1", "0", null)))));
        assertEquals("CONTRATO_NO_VIGENTE", codigo(() -> ContratoService.exigirVigente(
                contrato(4L, EstadoContrato.ANULADO, hoy, hoy.plusDays(30), precioDe(1L, cable, "1", "0", null)))));
    }

    @Test
    void calcularValoraAlPrecioPactadoYMarcaLoQueNoCabeEnElTope() {
        Contrato c = contrato(5L, EstadoContrato.ACTIVO, hoy, hoy.plusDays(60),
                precioDe(500L, cable, "1000", "19", "12"), precioDe(501L, breaker, "200", "0", null));
        SolicitudCompra s = solicitud(EstadoSolicitud.APROBADA, "13000.00", false, linea(cable, "10"), linea(breaker, "5"));

        var calculo = service.calcular(s, c, Map.of(500L, new BigDecimal("5")));

        assertEquals(new BigDecimal("11000.00"), calculo.subtotal()); // 10 x 1000 + 5 x 200
        assertEquals(new BigDecimal("1900.00"), calculo.impuestos()); // 19 % de 10.000
        assertEquals(new BigDecimal("12900.00"), calculo.total());
        assertTrue(calculo.cubreTodo());
        assertFalse(calculo.dentroDelTope()); // el tope es 12, ya se pidieron 5: quedan 7 y se piden 10
        assertEquals(new BigDecimal("7.0000"), calculo.lineas().get(0).disponible());
        assertTrue(calculo.lineas().get(0).excedeTope());
        assertEquals(null, calculo.lineas().get(1).disponible()); // sin tope
        assertTrue(calculo.dentroDeLoAprobado());
        assertFalse(calculo.puedeEmitirse());
    }

    @Test
    void elMontoAprobadoIncompletoOSuperadoImpideLaOrdenDirecta() {
        Contrato c = contrato(5L, EstadoContrato.ACTIVO, hoy, hoy.plusDays(60), precioDe(500L, cable, "1000", "0", null));

        assertFalse(service.calcular(solicitud(EstadoSolicitud.APROBADA, "9999.99", false, linea(cable, "10")), c, Map.of())
                .dentroDeLoAprobado()); // cuesta 10.000 y se aprobó por 9.999,99
        assertTrue(service.calcular(solicitud(EstadoSolicitud.APROBADA, "10000.00", false, linea(cable, "10")), c, Map.of())
                .puedeEmitirse()); // justo lo aprobado
        assertFalse(service.calcular(solicitud(EstadoSolicitud.APROBADA, "50000.00", true, linea(cable, "10")), c, Map.of())
                .dentroDeLoAprobado()); // lo aprobado no era comparable
    }

    @Test
    void ofertasListaPrimeroLasQueSePuedenEmitirYLaMasBarata() {
        SolicitudCompra s = solicitud(EstadoSolicitud.APROBADA, "20000.00", false, linea(cable, "10"), linea(breaker, "5"));
        when(solicitudes.findWithLineasById(7L)).thenReturn(Optional.of(s));
        Contrato parcial = contrato(1L, EstadoContrato.ACTIVO, hoy, hoy.plusDays(10), precioDe(10L, cable, "500", "0", null));
        Contrato caro = contrato(2L, EstadoContrato.ACTIVO, hoy, hoy.plusDays(20),
                precioDe(20L, cable, "1500", "0", null), precioDe(21L, breaker, "300", "0", null));
        Contrato barato = contrato(3L, EstadoContrato.ACTIVO, hoy, hoy.plusDays(30),
                precioDe(30L, cable, "1000", "0", null), precioDe(31L, breaker, "200", "0", null));
        when(contratos.vigentesConProductos(eq(List.of(100L, 101L)), any())).thenReturn(List.of(parcial, caro, barato));
        when(contratos.consumo(any(), any())).thenReturn(List.of(new ConsumoPrecio(30L, new BigDecimal("1"))));

        var ofertas = service.ofertas(7L);

        assertEquals(List.of(3L, 2L, 1L), ofertas.stream().map(o -> o.contratoId()).toList());
        assertTrue(ofertas.get(0).puedeEmitirse());
        assertEquals(new BigDecimal("11000.00"), ofertas.get(0).subtotal());
        assertTrue(ofertas.get(1).puedeEmitirse());
        assertEquals(new BigDecimal("16500.00"), ofertas.get(1).subtotal());
        assertFalse(ofertas.get(2).cubreTodo());
        assertEquals(List.of("Breaker 20 A"), ofertas.get(2).productosSinCubrir());
    }

    @Test
    void ofertasSoloDeUnaSolicitudAprobada() {
        when(solicitudes.findWithLineasById(7L))
                .thenReturn(Optional.of(solicitud(EstadoSolicitud.EN_RFQ, "1.00", false, linea(cable, "1"))));
        assertEquals("SOLICITUD_NO_APROBADA", codigo(() -> service.ofertas(7L)));
        assertThrows(NoEncontradoException.class, () -> service.ofertas(8L));
    }

    // ----------------------------------------------------------------- apoyo

    private String codigo(Runnable accion) {
        return assertThrows(NegocioException.class, accion::run).getCodigo();
    }

    private ContratoRequest request(String numero, LocalDate desde, LocalDate hasta,
            ContratoRequest.PrecioRequest... precios) {
        return new ContratoRequest(40L, numero, "Contrato de prueba", desde, hasta, List.of(precios));
    }

    private static ContratoRequest.PrecioRequest precio(Long productoId, String precio, String impuesto, String tope) {
        return new ContratoRequest.PrecioRequest(productoId, new BigDecimal(precio), new BigDecimal(impuesto),
                tope == null ? null : new BigDecimal(tope));
    }

    private static Producto producto(Long id, String nombre, UnidadMedida unidad) {
        Producto p = new Producto();
        p.setId(id);
        p.setCodigo("P-" + id);
        p.setNombre(nombre);
        p.setUnidadMedida(unidad);
        p.setActivo(true);
        return p;
    }

    private Contrato contrato(Long id, EstadoContrato estado, LocalDate desde, LocalDate hasta, ContratoPrecio... precios) {
        Contrato c = new Contrato();
        c.setId(id);
        c.setProveedor(tech);
        c.setNumero("CT-" + id);
        c.setEstado(estado);
        c.setVigenteDesde(desde);
        c.setVigenteHasta(hasta);
        for (ContratoPrecio p : precios) {
            c.agregarPrecio(p);
        }
        return c;
    }

    private static ContratoPrecio precioDe(Long id, Producto producto, String precio, String impuesto, String tope) {
        ContratoPrecio p = new ContratoPrecio();
        p.setId(id);
        p.setProducto(producto);
        p.setPrecioUnitario(new BigDecimal(precio));
        p.setImpuestoPct(new BigDecimal(impuesto));
        p.setCantidadMaxima(tope == null ? null : new BigDecimal(tope).setScale(4));
        return p;
    }

    private SolicitudCompra solicitud(EstadoSolicitud estado, String aprobado, boolean incompleto,
            SolicitudCompraLinea... lineas) {
        SolicitudCompra s = new SolicitudCompra();
        s.setId(7L);
        s.setConsecutivo("SOL-000007");
        s.setEstado(estado);
        s.setMontoEstimado(new BigDecimal(aprobado));
        s.setMontoIncompleto(incompleto);
        for (SolicitudCompraLinea l : lineas) {
            s.agregarLinea(l);
        }
        return s;
    }

    private static SolicitudCompraLinea linea(Producto producto, String cantidad) {
        SolicitudCompraLinea l = new SolicitudCompraLinea();
        l.setProducto(producto);
        l.setUnidadMedida(producto.getUnidadMedida());
        l.setCantidad(new BigDecimal(cantidad));
        return l;
    }
}
