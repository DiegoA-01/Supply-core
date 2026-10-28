package com.proyecto.supply_core.pago.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.compra.adjudicacion.entity.Adjudicacion;
import com.proyecto.supply_core.compra.adjudicacion.repository.AdjudicacionRepository;
import com.proyecto.supply_core.compra.orden.entity.OrdenCompra;
import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;
import com.proyecto.supply_core.compra.orden.repository.OrdenCompraRepository;
import com.proyecto.supply_core.compra.rfq.entity.Rfq;
import com.proyecto.supply_core.compra.solicitud.entity.AprobacionSolicitud;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompra;
import com.proyecto.supply_core.compra.solicitud.enums.DecisionAprobacion;
import com.proyecto.supply_core.compra.solicitud.repository.AprobacionSolicitudRepository;
import com.proyecto.supply_core.compra.solicitud.repository.SolicitudCompraRepository;
import com.proyecto.supply_core.documento.dto.DocumentoResponse;
import com.proyecto.supply_core.documento.enums.EntidadDocumento;
import com.proyecto.supply_core.documento.service.DocumentoService;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.pago.dto.PagoResponse;
import com.proyecto.supply_core.pago.dto.RegistrarPagoRequest;
import com.proyecto.supply_core.pago.entity.Pago;
import com.proyecto.supply_core.pago.enums.EstadoPago;
import com.proyecto.supply_core.pago.enums.TipoPago;
import com.proyecto.supply_core.empresa.entity.Empresa;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.factura.entity.FacturaProveedor;
import com.proyecto.supply_core.factura.service.FacturaService;
import com.proyecto.supply_core.common.tenant.EmpresaDePrueba;
import org.junit.jupiter.api.extension.ExtendWith;
import com.proyecto.supply_core.pago.enums.MetodoPago;
import com.proyecto.supply_core.pago.repository.PagoRepository;
import com.proyecto.supply_core.proveedor.entity.Proveedor;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

@ExtendWith(EmpresaDePrueba.class)
class PagoServiceTest {

    private static final Long SOLICITANTE = 10L;
    private static final Long APROBADOR_SOLICITUD = 21L;
    private static final Long APROBADOR_ADJUDICACION = 20L;
    private static final Long COMPRADOR = 30L;
    private static final Long ADMINISTRATIVO = 60L;

    private final PagoRepository pagos = mock(PagoRepository.class);
    private final OrdenCompraRepository ordenes = mock(OrdenCompraRepository.class);
    private final AdjudicacionRepository adjudicaciones = mock(AdjudicacionRepository.class);
    private final AprobacionSolicitudRepository aprobaciones = mock(AprobacionSolicitudRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final DocumentoService documentos = mock(DocumentoService.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final NotificacionService notificaciones = mock(NotificacionService.class);
    private final EmpresaRepository empresas = mock(EmpresaRepository.class);
    private final FacturaService facturas = mock(FacturaService.class);
    private final PagoService service = new PagoService(pagos, ordenes, adjudicaciones, aprobaciones, usuarios,
            documentos, auditoria, notificaciones, empresas, facturas, mock(SolicitudCompraRepository.class));

    private MockHttpServletRequest request;
    private OrdenCompra orden;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        sesion(ADMINISTRATIVO, "ADMINISTRATIVO", null);

        Proveedor tech = new Proveedor();
        tech.setId(40L);
        tech.setRazonSocial("Tech SAS");
        orden = new OrdenCompra();
        orden.setId(11L);
        orden.setConsecutivo("OC-000001");
        orden.setProveedor(tech);
        orden.setUsuarioId(COMPRADOR);
        orden.setAdjudicacionId(50L);
        orden.setTotal(new BigDecimal("1000.00"));
        orden.setCreadoEn(LocalDateTime.now().minusDays(5));
        when(ordenes.bloquear(11L)).thenReturn(Optional.of(orden));
        when(ordenes.findDetalleById(11L)).thenReturn(Optional.of(orden));
        when(ordenes.findById(11L)).thenReturn(Optional.of(orden));
        when(ordenes.findByConsecutivoOrderByVersionAsc("OC-000001")).thenReturn(List.of(orden));

        SolicitudCompra s = new SolicitudCompra();
        s.setId(5L);
        s.setUsuarioSolicitanteId(SOLICITANTE);
        Rfq rfq = new Rfq();
        rfq.setSolicitud(s);
        Adjudicacion adj = new Adjudicacion();
        adj.setId(50L);
        adj.setRfq(rfq);
        adj.setUsuarioId(COMPRADOR);
        adj.setAprobadoPorUsuarioId(APROBADOR_ADJUDICACION);
        when(adjudicaciones.findDetalleById(50L)).thenReturn(Optional.of(adj));
        when(aprobaciones.findBySolicitudCompraIdOrderByDecididoEnAscIdAsc(5L)).thenReturn(List.of(
                new AprobacionSolicitud(5L, APROBADOR_SOLICITUD, 1L, DecisionAprobacion.APROBADA, null)));

        Usuario portal = new Usuario();
        portal.setId(77L);
        when(usuarios.findByProveedorId(40L)).thenReturn(List.of(portal));
        when(pagos.pagadoPorConsecutivo("OC-000001")).thenReturn(BigDecimal.ZERO);
        when(pagos.anticipadoPorConsecutivo("OC-000001")).thenReturn(BigDecimal.ZERO);
        empresa("100"); // tope de anticipos al 100 %: los demás casos no dependen del tope
        when(pagos.save(any())).thenAnswer(i -> {
            Pago p = i.getArgument(0);
            p.setId(900L);
            when(pagos.bloquear(900L)).thenReturn(Optional.of(p));
            when(pagos.findDetalleById(900L)).thenReturn(Optional.of(p));
            return p;
        });
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void registraConEvidenciaPendienteYAvisa() {
        PagoResponse r = registrar("400.00");

        assertEquals(EstadoPago.REGISTRADO, r.estado());
        assertTrue(r.evidenciaPendiente());
        assertEquals(ADMINISTRATIVO, r.usuarioId());
        assertEquals("OC-000001", r.ordenConsecutivo());
        verify(notificaciones).notificar(eq(77L), eq(EventoNotificacion.PAGO_REGISTRADO), anyString());
        verify(notificaciones).notificar(eq(COMPRADOR), eq(EventoNotificacion.PAGO_REGISTRADO), anyString());
        verify(auditoria).exito(eq(AccionAuditoria.PAGO_REGISTRAR), any(), eq(900L), anyString());
    }

    @Test
    void nuncaSuperaElSaldoDeLaOrden() {
        when(pagos.pagadoPorConsecutivo("OC-000001")).thenReturn(new BigDecimal("700.00"));

        assertEquals("PAGO_EXCEDE_SALDO", assertThrows(NegocioException.class, () -> registrar("300.01")).getCodigo());
        assertDoesNotThrow(() -> registrar("300.00")); // salda exacto
    }

    @Test
    void soloOrdenesVigentesYNoCanceladas() {
        orden.setEstado(EstadoOrden.REEMPLAZADA);
        assertEquals("ORDEN_NO_PAGABLE", assertThrows(NegocioException.class, () -> registrar("10")).getCodigo());
        orden.setEstado(EstadoOrden.CANCELADA);
        assertEquals("ORDEN_NO_PAGABLE", assertThrows(NegocioException.class, () -> registrar("10")).getCodigo());
        orden.setEstado(EstadoOrden.RECHAZADA_PROVEEDOR);
        assertThrows(NegocioException.class, () -> registrar("10"));
        orden.setEstado(EstadoOrden.DESPACHADA);
        assertDoesNotThrow(() -> registrar("10"));
        verify(pagos).save(any());
    }

    @Test
    void fechaNoAnteriorALaEmision() {
        RegistrarPagoRequest req = new RegistrarPagoRequest(MetodoPago.CHEQUE, BigDecimal.TEN,
                LocalDate.now().minusDays(6), "CH-1", TipoPago.ANTICIPO);
        assertThrows(ParametroInvalidoException.class, () -> service.registrar(11L, req));
    }

    @Test
    void segregacionDeFunciones() {
        for (Long involucrado : List.of(SOLICITANTE, APROBADOR_SOLICITUD, APROBADOR_ADJUDICACION, COMPRADOR)) {
            sesion(involucrado, "ADMINISTRATIVO", null);
            assertThrows(AccesoDenegadoException.class, () -> registrar("10"), "usuario " + involucrado);
        }
        verify(pagos, never()).save(any());
    }

    @Test
    void comprobanteUnaSolaVezYCierraLaEvidencia() {
        registrar("100");
        MockMultipartFile pdf = new MockMultipartFile("archivo", "c.pdf", "application/pdf", new byte[] { 1 });
        when(documentos.subir(EntidadDocumento.PAGO, 900L, pdf)).thenReturn(
                new DocumentoResponse(3L, "c.pdf", "application/pdf", 1, "PAGO", 900L, ADMINISTRATIVO, LocalDateTime.now()));

        PagoResponse r = service.adjuntarComprobante(900L, pdf);
        assertFalse(r.evidenciaPendiente());
        assertEquals(3L, r.comprobanteDocumentoId());
        assertEquals("COMPROBANTE_EXISTENTE",
                assertThrows(NegocioException.class, () -> service.adjuntarComprobante(900L, pdf)).getCodigo());
    }

    @Test
    void anularDejaDeSumarYNoSeRepite() {
        registrar("100");

        assertThrows(ParametroInvalidoException.class, () -> service.anular(900L, " "));
        PagoResponse r = service.anular(900L, "Valor digitado de otra orden");
        assertEquals(EstadoPago.ANULADO, r.estado());
        assertEquals(ADMINISTRATIVO, r.anuladoPorUsuarioId());
        verify(notificaciones).notificar(eq(77L), eq(EventoNotificacion.PAGO_ANULADO), anyString());
        assertEquals("PAGO_ANULADO", assertThrows(NegocioException.class,
                () -> service.anular(900L, "Otra vez por error")).getCodigo());
        assertThrows(NegocioException.class, () -> service.adjuntarComprobante(900L,
                new MockMultipartFile("archivo", new byte[] { 1 })));
    }

    @Test
    void resumenDeLaOrdenYPortal() {
        when(pagos.pagadoPorConsecutivo("OC-000001")).thenReturn(new BigDecimal("250.00"));
        var r = service.deOrden(11L);
        assertEquals(new BigDecimal("750.00"), r.saldo());
        assertEquals(11L, r.ordenVigenteId());

        sesion(77L, "PROVEEDOR", 40L);
        assertEquals(new BigDecimal("250.00"), service.deOrdenDelProveedor(11L).pagado());
        sesion(78L, "PROVEEDOR", 41L);
        assertThrows(NoEncontradoException.class, () -> service.deOrdenDelProveedor(11L));
    }

    @Test
    void buscarValidaElRango() {
        assertThrows(ParametroInvalidoException.class, () -> service.buscar(null, null, null,
                LocalDate.now(), LocalDate.now().minusDays(1), org.springframework.data.domain.Pageable.unpaged()));
    }

    @Test
    void reglaDeDocumentos() {
        registrar("100");
        PagoReglaDocumentos regla = new PagoReglaDocumentos(pagos);
        UsuarioAutenticado admin = new UsuarioAutenticado(ADMINISTRATIVO, "a@x.com", "A", Set.of("ADMINISTRATIVO"), null, 1L);
        UsuarioAutenticado bodega = new UsuarioAutenticado(5L, "b@x.com", "B", Set.of("ALMACENISTA"), null, 1L);
        UsuarioAutenticado dueno = new UsuarioAutenticado(77L, "p@x.com", "P", Set.of("PROVEEDOR"), 40L, 1L);
        UsuarioAutenticado ajeno = new UsuarioAutenticado(78L, "o@x.com", "O", Set.of("PROVEEDOR"), 41L, 1L);

        assertDoesNotThrow(() -> regla.validarSubida(900L, admin));
        assertThrows(AccesoDenegadoException.class, () -> regla.validarSubida(900L, dueno));
        assertDoesNotThrow(() -> regla.validarLectura(900L, dueno));
        assertThrows(AccesoDenegadoException.class, () -> regla.validarLectura(900L, ajeno));
        assertThrows(AccesoDenegadoException.class, () -> regla.validarLectura(900L, bodega));
    }

    private PagoResponse registrar(String valor) {
        return registrar(valor, TipoPago.ANTICIPO);
    }

    private PagoResponse registrar(String valor, TipoPago tipo) {
        return service.registrar(11L, new RegistrarPagoRequest(MetodoPago.TRANSFERENCIA, new BigDecimal(valor),
                LocalDate.now(), " TR-123 ", tipo));
    }

    private void empresa(String topeAnticipoPct) {
        Empresa e = new Empresa();
        e.setId(1L);
        e.setTopeAnticipoPct(new BigDecimal(topeAnticipoPct));
        when(empresas.findById(1L)).thenReturn(Optional.of(e));
    }

    private FacturaProveedor factura(String total) {
        FacturaProveedor f = new FacturaProveedor();
        f.setId(5L);
        f.setNumero("FE-100");
        f.setTotal(new BigDecimal(total));
        return f;
    }

    private void sesion(Long id, String rol, Long proveedorId) {
        request.setAttribute(SesionActual.ATRIBUTO, new UsuarioAutenticado(id, "u@x.com", "U", Set.of(rol), proveedorId, 1L));
    }

    // ------------------------------------------------------------------ tipo de pago (17-bis)

    @Test
    void elAnticipoNoSuperaElTopeDeLaEmpresa() {
        empresa("50");

        assertEquals("ANTICIPO_EXCEDE_TOPE", assertThrows(NegocioException.class, () -> registrar("500.01")).getCodigo());
        assertDoesNotThrow(() -> registrar("500.00")); // justo en el tope (50 % de 1000)

        when(pagos.anticipadoPorConsecutivo("OC-000001")).thenReturn(new BigDecimal("400.00"));
        assertEquals("ANTICIPO_EXCEDE_TOPE", assertThrows(NegocioException.class, () -> registrar("100.01")).getCodigo());
        assertDoesNotThrow(() -> registrar("100.00"));
    }

    @Test
    void unAnticipoQuedaRegistradoConSuTipo() {
        PagoResponse r = registrar("100.00");

        assertEquals(TipoPago.ANTICIPO, r.tipo());
    }

    @Test
    void elPagoFinalSoloSeHaceDespuesDeRecibir() {
        for (EstadoOrden antes : new EstadoOrden[] { EstadoOrden.EMITIDA, EstadoOrden.ACEPTADA_PROVEEDOR,
                EstadoOrden.EN_PREPARACION, EstadoOrden.DESPACHADA }) {
            orden.setEstado(antes);
            assertEquals("PAGO_FINAL_SIN_RECEPCION", assertThrows(NegocioException.class,
                    () -> registrar("10", TipoPago.PAGO_FINAL)).getCodigo(), antes.name());
        }
        verify(facturas, never()).exigirPagable(any());
        verify(pagos, never()).save(any());
    }

    @Test
    void elPagoFinalExigeUnaFacturaQueCuadre() {
        orden.setEstado(EstadoOrden.RECIBIDA_TOTAL);
        when(facturas.exigirPagable(orden)).thenThrow(new NegocioException("FACTURA_REQUERIDA", "Sin factura"));

        assertEquals("FACTURA_REQUERIDA", assertThrows(NegocioException.class,
                () -> registrar("10", TipoPago.PAGO_FINAL)).getCodigo());
        verify(pagos, never()).save(any());
    }

    @Test
    void elPagoFinalDescuentaLoYaPagadoYNoSuperaLaFactura() {
        orden.setEstado(EstadoOrden.RECIBIDA_TOTAL);
        when(facturas.exigirPagable(orden)).thenReturn(factura("800.00"));
        when(pagos.pagadoPorConsecutivo("OC-000001")).thenReturn(new BigDecimal("300.00")); // falta 500 de la factura

        assertEquals("PAGO_EXCEDE_FACTURA", assertThrows(NegocioException.class,
                () -> registrar("500.01", TipoPago.PAGO_FINAL)).getCodigo());
        PagoResponse r = registrar("500.00", TipoPago.PAGO_FINAL);

        assertEquals(TipoPago.PAGO_FINAL, r.tipo());
        verify(auditoria).exito(eq(AccionAuditoria.PAGO_REGISTRAR), any(), eq(900L), org.mockito.ArgumentMatchers.contains("tipo=PAGO_FINAL"));
    }

    @Test
    void elPagoFinalTampocoSuperaElSaldoDeLaOrden() {
        orden.setEstado(EstadoOrden.CERRADA);
        when(facturas.exigirPagable(orden)).thenReturn(factura("5000.00")); // factura mayor que la orden
        when(pagos.pagadoPorConsecutivo("OC-000001")).thenReturn(new BigDecimal("900.00"));

        assertEquals("PAGO_EXCEDE_SALDO", assertThrows(NegocioException.class,
                () -> registrar("100.01", TipoPago.PAGO_FINAL)).getCodigo());
    }
}
