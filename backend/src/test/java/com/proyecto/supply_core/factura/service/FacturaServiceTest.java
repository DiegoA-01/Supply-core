package com.proyecto.supply_core.factura.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.tenant.EmpresaDePrueba;
import com.proyecto.supply_core.compra.orden.entity.OrdenCompra;
import com.proyecto.supply_core.compra.orden.entity.OrdenCompraLinea;
import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;
import com.proyecto.supply_core.compra.orden.repository.OrdenCompraRepository;
import com.proyecto.supply_core.compra.orden.service.OrdenCompraService;
import com.proyecto.supply_core.documento.dto.DocumentoResponse;
import com.proyecto.supply_core.documento.enums.EntidadDocumento;
import com.proyecto.supply_core.documento.service.DocumentoService;
import com.proyecto.supply_core.empresa.entity.Empresa;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.factura.dto.FacturaResponse;
import com.proyecto.supply_core.factura.dto.RegistrarFacturaRequest;
import com.proyecto.supply_core.factura.entity.FacturaProveedor;
import com.proyecto.supply_core.factura.enums.EstadoFactura;
import com.proyecto.supply_core.factura.repository.FacturaProveedorRepository;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.pago.repository.PagoRepository;
import com.proyecto.supply_core.proveedor.entity.Proveedor;
import com.proyecto.supply_core.recepcion.repository.RecepcionLineaRepository;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

@ExtendWith(EmpresaDePrueba.class)
class FacturaServiceTest {

    private static final Long ADMINISTRATIVO = 60L;
    private static final Long COMPRAS = 21L;

    private final FacturaProveedorRepository facturas = mock(FacturaProveedorRepository.class);
    private final OrdenCompraService ordenService = mock(OrdenCompraService.class);
    private final OrdenCompraRepository ordenes = mock(OrdenCompraRepository.class);
    private final RecepcionLineaRepository recepciones = mock(RecepcionLineaRepository.class);
    private final EmpresaRepository empresas = mock(EmpresaRepository.class);
    private final PagoRepository pagos = mock(PagoRepository.class);
    private final DocumentoService documentos = mock(DocumentoService.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final NotificacionService notificaciones = mock(NotificacionService.class);
    private final FacturaService service = new FacturaService(facturas, ordenService, ordenes, recepciones, empresas,
            pagos, documentos, auditoria, notificaciones);

    private OrdenCompra orden;
    private Empresa empresa;
    private Proveedor proveedor;

    @BeforeEach
    void setUp() {
        sesion(ADMINISTRATIVO, "ADMINISTRATIVO", null);
        proveedor = new Proveedor();
        proveedor.setId(40L);
        proveedor.setRazonSocial("Tech SAS");
        orden = new OrdenCompra();
        orden.setId(11L);
        orden.setConsecutivo("OC-000001");
        orden.setVersion(1);
        orden.setProveedor(proveedor);
        orden.setEstado(EstadoOrden.RECIBIDA_TOTAL);
        // 10 de cable a $ 100 con IVA 19 %, aceptados 10: esperado 1000 + 190 = 1190
        OrdenCompraLinea l = new OrdenCompraLinea();
        l.setId(501L);
        l.setCantidad(new BigDecimal("10"));
        l.setPrecioUnitario(new BigDecimal("100"));
        l.setImpuestoPct(new BigDecimal("19"));
        orden.agregarLinea(l);
        empresa = new Empresa();
        empresa.setId(1L);
        empresa.setToleranciaFacturaPct(new BigDecimal("1.00"));
        empresa.setToleranciaFacturaMonto(BigDecimal.ZERO);

        when(ordenService.bloquear(11L)).thenReturn(orden);
        when(ordenes.findById(11L)).thenReturn(Optional.of(orden));
        when(ordenes.findDetalleById(11L)).thenReturn(Optional.of(orden));
        when(empresas.findById(1L)).thenReturn(Optional.of(empresa));
        aceptado("10");
        when(facturas.save(any(FacturaProveedor.class))).thenAnswer(i -> {
            FacturaProveedor f = i.getArgument(0);
            f.setId(70L);
            when(facturas.bloquear(70L)).thenReturn(Optional.of(f));
            when(facturas.findDetalleById(70L)).thenReturn(Optional.of(f));
            return f;
        });
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    /** Hace que la factura indicada sea la vigente de la orden (se lee antes de armar el stub). */
    private void vigente(Long id) {
        Optional<FacturaProveedor> f = facturas.findDetalleById(id);
        when(facturas.findFirstByOrden_ConsecutivoAndEstadoNot("OC-000001", EstadoFactura.ANULADA)).thenReturn(f);
    }

    private void aceptado(String cantidad) {
        var a = mock(RecepcionLineaRepository.AgregadoLinea.class);
        when(a.getOrdenLineaId()).thenReturn(501L);
        when(a.getAceptada()).thenReturn(new BigDecimal(cantidad));
        when(recepciones.agregadoPorLinea(11L)).thenReturn(List.of(a));
    }

    private static RegistrarFacturaRequest factura(String subtotal, String impuestos, String total) {
        return new RegistrarFacturaRequest("FE-100", LocalDate.now(), new BigDecimal(subtotal), new BigDecimal(impuestos),
                new BigDecimal(total));
    }

    // ------------------------------------------------------------------ registrar y cruzar

    @Test
    void unaFacturaQueCuadraQuedaCuadradaYGuardaLoEsperado() {
        FacturaResponse r = service.registrar(11L, factura("1000.00", "190.00", "1190.00"));

        assertEquals(EstadoFactura.CUADRADA, r.estado());
        assertEquals(0, new BigDecimal("1190.00").compareTo(r.esperadoTotal()));
        assertEquals(0, BigDecimal.ZERO.compareTo(r.diferencia()));
        assertEquals(ADMINISTRATIVO, r.usuarioId());
        verify(notificaciones, never()).notificarRol(anyString(), any(), anyString());
        verify(auditoria).exito(eq(AccionAuditoria.FACTURA_REGISTRAR), eq(EntidadAuditada.FACTURA), eq(70L), anyString());
    }

    @Test
    void dentroDeLaToleranciaCuadraYFueraQuedaEnRevisionYAvisaACompras() {
        // 1 % de 1190 = 11,90
        assertEquals(EstadoFactura.CUADRADA, service.registrar(11L, factura("1011.00", "190.00", "1201.00")).estado());

        FacturaResponse fuera = service.registrar(11L, factura("1100.00", "190.00", "1290.00"));

        assertEquals(EstadoFactura.EN_REVISION, fuera.estado());
        assertEquals(0, new BigDecimal("100.00").compareTo(fuera.diferencia()));
        verify(notificaciones).notificarRol(eq("ADMIN_COMPRAS"), eq(EventoNotificacion.FACTURA_EN_REVISION), anyString());
    }

    @Test
    void laToleranciaDeLaEmpresaSeRespeta() {
        empresa.setToleranciaFacturaPct(BigDecimal.ZERO);
        assertEquals(EstadoFactura.EN_REVISION, service.registrar(11L, factura("1000.00", "191.00", "1191.00")).estado());

        empresa.setToleranciaFacturaMonto(new BigDecimal("5"));
        assertEquals(EstadoFactura.CUADRADA, service.registrar(11L, factura("1000.00", "191.00", "1191.00")).estado());
    }

    @Test
    void soloSeFacturaConRecepciones() {
        for (EstadoOrden antes : new EstadoOrden[] { EstadoOrden.EMITIDA, EstadoOrden.EN_PREPARACION, EstadoOrden.DESPACHADA }) {
            orden.setEstado(antes);
            assertEquals("ORDEN_NO_FACTURABLE", assertThrows(NegocioException.class,
                    () -> service.registrar(11L, factura("1000.00", "190.00", "1190.00"))).getCodigo());
        }
        verify(facturas, never()).save(any());
    }

    @Test
    void unaSolaFacturaVigenteYNumeroUnicoPorProveedor() {
        FacturaProveedor vigente = new FacturaProveedor();
        when(facturas.findFirstByOrden_ConsecutivoAndEstadoNot("OC-000001", EstadoFactura.ANULADA)).thenReturn(Optional.of(vigente));
        assertEquals("FACTURA_EXISTENTE", assertThrows(NegocioException.class,
                () -> service.registrar(11L, factura("1000.00", "190.00", "1190.00"))).getCodigo());

        when(facturas.findFirstByOrden_ConsecutivoAndEstadoNot("OC-000001", EstadoFactura.ANULADA)).thenReturn(Optional.empty());
        when(facturas.existsByProveedor_IdAndNumeroIgnoreCaseAndEstadoNot(40L, "FE-100", EstadoFactura.ANULADA)).thenReturn(true);
        assertEquals("FACTURA_NUMERO_DUPLICADO", assertThrows(NegocioException.class,
                () -> service.registrar(11L, factura("1000.00", "190.00", "1190.00"))).getCodigo());
    }

    @Test
    void sinNadaAceptadoLaFacturaNuncaCuadra() {
        aceptado("0");

        assertEquals(EstadoFactura.EN_REVISION, service.registrar(11L, factura("1000.00", "190.00", "1190.00")).estado());
    }

    // ------------------------------------------------------------------ recruzar

    @Test
    void recruzarCambiaElEstadoSiLaRecepcionCambio() {
        service.registrar(11L, factura("1000.00", "190.00", "1190.00"));
        aceptado("5"); // se reversó parte de la recepción: ahora se esperaría la mitad

        FacturaResponse r = service.recruzar(70L);

        assertEquals(EstadoFactura.EN_REVISION, r.estado());
        assertEquals(0, new BigDecimal("595.00").compareTo(r.esperadoTotal()));
        verify(notificaciones).notificarRol(eq("ADMIN_COMPRAS"), eq(EventoNotificacion.FACTURA_EN_REVISION), anyString());
    }

    @Test
    void noSeRecruzaUnaFacturaAprobadaOAnulada() {
        service.registrar(11L, factura("1100.00", "190.00", "1290.00"));
        sesion(COMPRAS, "ADMIN_COMPRAS", null);
        service.aprobar(70L, "El proveedor cobró el flete pactado");

        assertEquals("FACTURA_NO_CRUZABLE", assertThrows(NegocioException.class, () -> service.recruzar(70L)).getCodigo());
    }

    // ------------------------------------------------------------------ aprobar y anular

    @Test
    void comprasApruebaUnaFacturaEnRevisionConMotivoYAvisaAlQueLaRegistro() {
        service.registrar(11L, factura("1100.00", "190.00", "1290.00"));
        sesion(COMPRAS, "ADMIN_COMPRAS", null);

        FacturaResponse r = service.aprobar(70L, "El proveedor cobró el flete pactado");

        assertEquals(EstadoFactura.APROBADA, r.estado());
        assertEquals(COMPRAS, r.aprobadoPorUsuarioId());
        assertNotNull(r.aprobadoEn());
        verify(notificaciones).notificar(eq(ADMINISTRATIVO), eq(EventoNotificacion.FACTURA_DECIDIDA), anyString());
        verify(auditoria).exito(eq(AccionAuditoria.FACTURA_APROBAR), eq(EntidadAuditada.FACTURA), eq(70L), anyString());
    }

    @Test
    void quienRegistraLaFacturaNoPuedeAprobarla() {
        service.registrar(11L, factura("1100.00", "190.00", "1290.00"));

        assertThrows(AccesoDenegadoException.class, () -> service.aprobar(70L, "Me autorizo yo mismo"));
    }

    @Test
    void soloSeApruebaUnaFacturaEnRevision() {
        service.registrar(11L, factura("1000.00", "190.00", "1190.00")); // cuadrada
        sesion(COMPRAS, "ADMIN_COMPRAS", null);

        assertEquals("FACTURA_NO_EN_REVISION", assertThrows(NegocioException.class,
                () -> service.aprobar(70L, "No hacía falta")).getCodigo());
    }

    @Test
    void anularLiberaElNumeroPeroNoConUnPagoFinalRegistrado() {
        service.registrar(11L, factura("1000.00", "190.00", "1190.00"));
        when(pagos.hayPagoFinalRegistrado("OC-000001")).thenReturn(true);
        assertEquals("FACTURA_CON_PAGO", assertThrows(NegocioException.class, () -> service.anular(70L, "Número mal digitado")).getCodigo());

        when(pagos.hayPagoFinalRegistrado("OC-000001")).thenReturn(false);
        sesion(COMPRAS, "ADMIN_COMPRAS", null);
        FacturaResponse r = service.anular(70L, "Número mal digitado");

        assertEquals(EstadoFactura.ANULADA, r.estado());
        assertEquals("Número mal digitado", r.motivoAnulacion());
        verify(notificaciones).notificar(eq(ADMINISTRATIVO), eq(EventoNotificacion.FACTURA_DECIDIDA), anyString());
        assertEquals("FACTURA_ANULADA", assertThrows(NegocioException.class, () -> service.anular(70L, "Otra vez")).getCodigo());
    }

    // ------------------------------------------------------------------ archivo

    @Test
    void elPdfSeAdjuntaUnaSolaVez() {
        service.registrar(11L, factura("1000.00", "190.00", "1190.00"));
        MockMultipartFile pdf = new MockMultipartFile("archivo", "f.pdf", "application/pdf", new byte[] { 1 });
        when(documentos.subir(EntidadDocumento.FACTURA, 70L, pdf)).thenReturn(
                new DocumentoResponse(3L, "f.pdf", "application/pdf", 1, "FACTURA", 70L, ADMINISTRATIVO, java.time.LocalDateTime.now()));

        FacturaResponse r = service.adjuntarArchivo(70L, pdf);

        assertEquals(3L, r.documentoId());
        assertEquals("ARCHIVO_EXISTENTE", assertThrows(NegocioException.class, () -> service.adjuntarArchivo(70L, pdf)).getCodigo());
    }

    // ------------------------------------------------------------------ pagable (para el pago final)

    @Test
    void sinFacturaVigenteNoHayPagoFinal() {
        assertEquals("FACTURA_REQUERIDA", assertThrows(NegocioException.class, () -> service.exigirPagable(orden)).getCodigo());
    }

    @Test
    void unaFacturaEnRevisionNoSePaga() {
        service.registrar(11L, factura("1100.00", "190.00", "1290.00"));
        vigente(70L);

        assertEquals("FACTURA_EN_REVISION", assertThrows(NegocioException.class, () -> service.exigirPagable(orden)).getCodigo());
    }

    @Test
    void unaFacturaCuadradaQueYaNoCuadraConLaRecepcionActualBloqueaElPago() {
        service.registrar(11L, factura("1000.00", "190.00", "1190.00"));
        vigente(70L);
        assertNotNull(service.exigirPagable(orden)); // todavía cuadra

        aceptado("8"); // la recepción cambió

        assertEquals("FACTURA_NO_CUADRA", assertThrows(NegocioException.class, () -> service.exigirPagable(orden)).getCodigo());
    }

    @Test
    void unaFacturaAprobadaSePagaAunqueNoCuadre() {
        service.registrar(11L, factura("1100.00", "190.00", "1290.00"));
        sesion(COMPRAS, "ADMIN_COMPRAS", null);
        service.aprobar(70L, "El proveedor cobró el flete pactado");
        vigente(70L);

        assertEquals(EstadoFactura.APROBADA, service.exigirPagable(orden).getEstado());
    }

    // ------------------------------------------------------------------ consulta

    @Test
    void elProveedorSoloVeLasFacturasDeSusOrdenes() {
        sesion(9L, "PROVEEDOR", 41L);
        assertThrows(NoEncontradoException.class, () -> service.deOrdenDelProveedor(11L));

        sesion(9L, "PROVEEDOR", 40L);
        when(facturas.findByOrden_ConsecutivoOrderByCreadoEnAscIdAsc("OC-000001")).thenReturn(List.of());
        assertTrue(service.deOrdenDelProveedor(11L).isEmpty());
    }

    @Test
    void reglaDeDocumentosDeLaFactura() {
        service.registrar(11L, factura("1000.00", "190.00", "1190.00"));
        var regla = new FacturaReglaDocumentos(facturas);
        var admin = new UsuarioAutenticado(ADMINISTRATIVO, "a@x.com", "A", Set.of("ADMINISTRATIVO"), null, 1L);
        var dueno = new UsuarioAutenticado(77L, "p@x.com", "P", Set.of("PROVEEDOR"), 40L, 1L);
        var ajeno = new UsuarioAutenticado(78L, "o@x.com", "O", Set.of("PROVEEDOR"), 41L, 1L);
        var bodega = new UsuarioAutenticado(5L, "b@x.com", "B", Set.of("ALMACENISTA"), null, 1L);

        assertSame(EntidadDocumento.FACTURA, regla.entidad());
        regla.validarSubida(70L, admin);
        assertThrows(AccesoDenegadoException.class, () -> regla.validarSubida(70L, dueno));
        regla.validarLectura(70L, dueno);
        assertThrows(AccesoDenegadoException.class, () -> regla.validarLectura(70L, ajeno));
        assertThrows(AccesoDenegadoException.class, () -> regla.validarLectura(70L, bodega));
    }

    private static void sesion(Long id, String rol, Long proveedorId) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(SesionActual.ATRIBUTO, new UsuarioAutenticado(id, "u@x.com", "U", Set.of(rol), proveedorId, 1L));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }
}
