package com.proyecto.supply_core.compra.rfq.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.time.LocalDateTime;
import java.util.List;
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
import com.proyecto.supply_core.catalogo.entity.CategoriaProducto;
import com.proyecto.supply_core.catalogo.entity.UnidadMedida;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.util.ConsecutivoGenerator;
import com.proyecto.supply_core.compra.rfq.dto.EspecificacionRequest;
import com.proyecto.supply_core.compra.rfq.dto.RfqCrearRequest;
import com.proyecto.supply_core.compra.rfq.dto.RfqDatosRequest;
import com.proyecto.supply_core.compra.rfq.entity.Rfq;
import com.proyecto.supply_core.compra.rfq.enums.EstadoRfq;
import com.proyecto.supply_core.compra.rfq.repository.RfqRepository;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompra;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompraLinea;
import com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud;
import com.proyecto.supply_core.compra.solicitud.repository.SolicitudCompraRepository;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.proveedor.entity.Proveedor;
import com.proyecto.supply_core.proveedor.enums.EstadoProveedor;
import com.proyecto.supply_core.proveedor.repository.ProveedorRepository;
import com.proyecto.supply_core.proveedor.service.ProveedorService;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

class RfqServiceTest {

    private final RfqRepository rfqs = mock(RfqRepository.class);
    private final SolicitudCompraRepository solicitudes = mock(SolicitudCompraRepository.class);
    private final ProveedorRepository proveedores = mock(ProveedorRepository.class);
    private final ProveedorService proveedorService = mock(ProveedorService.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final ConsecutivoGenerator consecutivos = mock(ConsecutivoGenerator.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final NotificacionService notificaciones = mock(NotificacionService.class);
    private final RfqService service = new RfqService(rfqs, solicitudes, proveedores, proveedorService, usuarios,
            consecutivos, auditoria, notificaciones);

    private MockHttpServletRequest request;
    private SolicitudCompra solicitud;
    private CategoriaProducto papeleria;
    private Proveedor habilitado;
    private Rfq guardada;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        sesion(new UsuarioAutenticado(30L, "c@x.com", "Comprador", Set.of("COMPRADOR"), null, 1L));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        papeleria = new CategoriaProducto();
        papeleria.setId(1L);
        papeleria.setNombre("Papelería");
        Producto lapiz = new Producto();
        lapiz.setId(100L);
        lapiz.setCategoria(papeleria);
        UnidadMedida und = new UnidadMedida();
        und.setId(1L);
        solicitud = new SolicitudCompra();
        solicitud.setId(5L);
        solicitud.setConsecutivo("SOL-000005");
        solicitud.setEstado(EstadoSolicitud.APROBADA);
        SolicitudCompraLinea l = new SolicitudCompraLinea();
        l.setProducto(lapiz);
        l.setUnidadMedida(und);
        l.setCantidad(new BigDecimal("10"));
        solicitud.agregarLinea(l);
        when(solicitudes.bloquear(5L)).thenReturn(Optional.of(solicitud));
        when(solicitudes.findWithLineasById(5L)).thenReturn(Optional.of(solicitud));

        habilitado = proveedor(40L, EstadoProveedor.HABILITADO, papeleria);
        when(proveedorService.habilitadosDeCategoria(1L)).thenReturn(List.of(habilitado));
        when(consecutivos.siguiente(ConsecutivoGenerator.RFQ)).thenReturn("RFQ-000001");
        when(rfqs.save(any())).thenAnswer(i -> {
            guardada = i.getArgument(0);
            guardada.setId(9L);
            when(rfqs.bloquear(9L)).thenReturn(Optional.of(guardada));
            when(rfqs.findWithDetalleById(9L)).thenReturn(Optional.of(guardada));
            return guardada;
        });
        Usuario portal = new Usuario();
        portal.setId(77L);
        when(usuarios.findByProveedorId(40L)).thenReturn(List.of(portal));
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void crearCopiaLineasEInvitaALosHabilitadosDeLaCategoria() {
        var r = service.crear(crear(datos(null, List.of(new EspecificacionRequest(100L, " HB, caja x 12 ")))));

        assertEquals(EstadoRfq.BORRADOR, r.estado());
        assertEquals("RFQ-000001", r.consecutivo());
        assertEquals(30L, r.usuarioId());
        assertEquals(1, r.lineas().size());
        assertEquals("HB, caja x 12", r.lineas().get(0).especificaciones());
        assertEquals(List.of(40L), r.invitados().stream().map(i -> i.proveedorId()).toList());
        assertTrue(r.alertas().isEmpty());
        assertEquals(EstadoSolicitud.APROBADA, solicitud.getEstado()); // cambia al abrir
        verify(auditoria).exito(eq(AccionAuditoria.RFQ_CREAR), eq(EntidadAuditada.RFQ), eq(9L), anyString());
    }

    @Test
    void alertaSiLaCategoriaNoTieneProveedoresHabilitados() {
        when(proveedorService.habilitadosDeCategoria(1L)).thenReturn(List.of());

        var r = service.crear(crear(datos(null, null)));

        assertTrue(r.invitados().isEmpty());
        assertTrue(r.alertas().contains("No hay proveedores habilitados para la categoría Papelería"));
        assertTrue(r.alertas().contains("La RFQ no tiene proveedores invitados; no se podrá abrir"));
    }

    @Test
    void soloDesdeSolicitudAprobadaYSinRfqVigente() {
        when(rfqs.existsBySolicitud_IdAndEstadoNot(5L, EstadoRfq.CANCELADA)).thenReturn(true);
        assertEquals("RFQ_EXISTENTE",
                assertThrows(NegocioException.class, () -> service.crear(crear(datos(null, null)))).getCodigo());

        solicitud.setEstado(EstadoSolicitud.PENDIENTE_APROBACION);
        assertEquals("SOLICITUD_NO_APROBADA",
                assertThrows(NegocioException.class, () -> service.crear(crear(datos(null, null)))).getCodigo());
        verify(rfqs, never()).save(any());
    }

    @Test
    void invitadoExplicitoDebeEstarHabilitadoYCotizarLaCategoria() {
        CategoriaProducto aseo = new CategoriaProducto();
        aseo.setId(2L);
        proveedor(41L, EstadoProveedor.HABILITADO, aseo);
        proveedor(42L, EstadoProveedor.SUSPENDIDO, papeleria);

        assertEquals("PROVEEDOR_SIN_CATEGORIA", assertThrows(NegocioException.class,
                () -> service.crear(crear(datos(Set.of(41L), null)))).getCodigo());
        assertEquals("PROVEEDOR_NO_HABILITADO", assertThrows(NegocioException.class,
                () -> service.crear(crear(datos(Set.of(42L), null)))).getCodigo());
        assertThrows(NoEncontradoException.class, () -> service.crear(crear(datos(Set.of(99L), null))));
    }

    @Test
    void especificacionDeUnProductoAjenoSeRechaza() {
        assertEquals("ESPECIFICACION_INVALIDA", assertThrows(NegocioException.class,
                () -> service.crear(crear(datos(null, List.of(new EspecificacionRequest(555L, "x")))))).getCodigo());
    }

    @Test
    void abrirPasaLaSolicitudAEnRfqYAvisaALosInvitados() {
        service.crear(crear(datos(null, null)));

        var r = service.abrir(9L);

        assertEquals(EstadoRfq.ABIERTA, r.estado());
        assertNotNull(r.abiertaEn());
        assertEquals(EstadoSolicitud.EN_RFQ, solicitud.getEstado());
        verify(notificaciones).notificar(eq(77L), eq(EventoNotificacion.RFQ_INVITACION), anyString());
        assertEquals("TRANSICION_INVALIDA",
                assertThrows(NegocioException.class, () -> service.abrir(9L)).getCodigo());
    }

    @Test
    void noSeAbreSinInvitadosNiConFechaVencida() {
        when(proveedorService.habilitadosDeCategoria(1L)).thenReturn(List.of());
        service.crear(crear(datos(null, null)));
        assertEquals("RFQ_SIN_PROVEEDORES",
                assertThrows(NegocioException.class, () -> service.abrir(9L)).getCodigo());

        guardada.invitar(habilitado);
        guardada.setFechaLimite(LocalDateTime.now().minusMinutes(1));
        assertEquals("FECHA_LIMITE_VENCIDA",
                assertThrows(NegocioException.class, () -> service.abrir(9L)).getCodigo());
    }

    @Test
    void cerrarVencidasSoloCierraLasAbiertasVencidas() {
        service.crear(crear(datos(null, null)));
        service.abrir(9L);
        guardada.setFechaLimite(LocalDateTime.now().minusSeconds(1));
        when(rfqs.vencidas(eq(EstadoRfq.ABIERTA), any())).thenReturn(List.of(9L));

        assertEquals(1, service.cerrarVencidas());
        assertEquals(EstadoRfq.CERRADA, guardada.getEstado());
        verify(notificaciones).notificar(eq(30L), eq(EventoNotificacion.RFQ_CERRADA), anyString());
        assertEquals(0, service.cerrarVencidas()); // ya no está ABIERTA
    }

    @Test
    void cancelarAbiertaDevuelveLaSolicitudAAprobada() {
        service.crear(crear(datos(null, null)));
        service.abrir(9L);

        assertEquals(EstadoRfq.CANCELADA, service.cancelar(9L).estado());
        assertEquals(EstadoSolicitud.APROBADA, solicitud.getEstado());
        verify(notificaciones).notificar(eq(77L), eq(EventoNotificacion.RFQ_CANCELADA), anyString());
        assertThrows(NegocioException.class, () -> service.cancelar(9L));
    }

    @Test
    void elProveedorSoloVeLasQueLeInvitaronYYaAbiertas() {
        service.crear(crear(datos(null, null)));
        sesion(new UsuarioAutenticado(77L, "p@x.com", "Prov", Set.of("PROVEEDOR"), 40L, 1L));
        assertThrows(NoEncontradoException.class, () -> service.obtenerParaProveedor(9L)); // BORRADOR

        guardada.setEstado(EstadoRfq.ABIERTA);
        assertEquals("RFQ-000001", service.obtenerParaProveedor(9L).consecutivo());

        sesion(new UsuarioAutenticado(78L, "o@x.com", "Otro", Set.of("PROVEEDOR"), 41L, 1L));
        assertThrows(NoEncontradoException.class, () -> service.obtenerParaProveedor(9L));
    }

    // ----------------------------------------------------------------- apoyo

    private Proveedor proveedor(Long id, EstadoProveedor estado, CategoriaProducto categoria) {
        Proveedor p = new Proveedor();
        p.setId(id);
        p.setRazonSocial("Proveedor " + id);
        p.setEstado(estado);
        p.getCategorias().add(categoria);
        when(proveedores.findWithCategoriasById(id)).thenReturn(Optional.of(p));
        return p;
    }

    private static RfqDatosRequest datos(Set<Long> invitados, List<EspecificacionRequest> especificaciones) {
        return new RfqDatosRequest(LocalDateTime.now().plusDays(3), "Entrega en bodega principal", invitados,
                especificaciones);
    }

    private static RfqCrearRequest crear(RfqDatosRequest datos) {
        return new RfqCrearRequest(5L, datos);
    }

    private void sesion(UsuarioAutenticado u) {
        request.setAttribute(SesionActual.ATRIBUTO, u);
    }
}
