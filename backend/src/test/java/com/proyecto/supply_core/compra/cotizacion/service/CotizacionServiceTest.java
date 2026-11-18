package com.proyecto.supply_core.compra.cotizacion.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.compra.cotizacion.dto.CotizacionRequest;
import com.proyecto.supply_core.compra.cotizacion.dto.LineaCotizacionRequest;
import com.proyecto.supply_core.compra.cotizacion.entity.Cotizacion;
import com.proyecto.supply_core.compra.cotizacion.enums.EstadoCotizacion;
import com.proyecto.supply_core.compra.cotizacion.enums.OrigenCotizacion;
import com.proyecto.supply_core.compra.cotizacion.repository.CotizacionRepository;
import com.proyecto.supply_core.compra.rfq.entity.Rfq;
import com.proyecto.supply_core.compra.rfq.entity.RfqLinea;
import com.proyecto.supply_core.compra.rfq.enums.EstadoRfq;
import com.proyecto.supply_core.compra.rfq.repository.RfqRepository;
import com.proyecto.supply_core.documento.dto.DocumentoResponse;
import com.proyecto.supply_core.documento.enums.EntidadDocumento;
import com.proyecto.supply_core.documento.service.DocumentoService;
import com.proyecto.supply_core.ia.service.ExtraccionService;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.proveedor.entity.Proveedor;
import com.proyecto.supply_core.proveedor.repository.ProveedorRepository;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

class CotizacionServiceTest {

    private final CotizacionRepository cotizaciones = mock(CotizacionRepository.class);
    private final RfqRepository rfqs = mock(RfqRepository.class);
    private final ProveedorRepository proveedores = mock(ProveedorRepository.class);
    private final DocumentoService documentos = mock(DocumentoService.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final NotificacionService notificaciones = mock(NotificacionService.class);
    private final ExtraccionService extraccion = mock(ExtraccionService.class);
    private final CotizacionService service = new CotizacionService(cotizaciones, rfqs, proveedores, documentos,
            auditoria, notificaciones, extraccion);
    private final CotizacionReglaDocumentos regla = new CotizacionReglaDocumentos(cotizaciones);

    private MockHttpServletRequest request;
    private Rfq rfq;
    private Proveedor tech;
    private Cotizacion guardada;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        sesionProveedor(40L);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        tech = new Proveedor();
        tech.setId(40L);
        tech.setRazonSocial("Tech SAS");
        rfq = new Rfq();
        rfq.setId(9L);
        rfq.setConsecutivo("RFQ-000009");
        rfq.setUsuarioId(30L);
        rfq.setEstado(EstadoRfq.ABIERTA);
        rfq.setFechaLimite(LocalDateTime.now().plusDays(1));
        Producto lapiz = new Producto();
        lapiz.setId(100L);
        lapiz.setCodigo("LAP-01");
        RfqLinea linea = new RfqLinea();
        linea.setId(500L);
        linea.setProducto(lapiz);
        rfq.agregarLinea(linea);
        rfq.invitar(tech);
        when(rfqs.bloquear(9L)).thenReturn(Optional.of(rfq));
        when(rfqs.findWithDetalleById(9L)).thenReturn(Optional.of(rfq));
        when(rfqs.findById(9L)).thenReturn(Optional.of(rfq));
        when(proveedores.getReferenceById(40L)).thenReturn(tech);
        when(cotizaciones.findByRfq_IdAndProveedor_Id(9L, 40L)).thenAnswer(i -> Optional.ofNullable(guardada));
        when(cotizaciones.save(any())).thenAnswer(i -> {
            guardada = i.getArgument(0);
            guardada.setId(77L);
            return guardada;
        });
        when(cotizaciones.findWithRfqById(77L)).thenAnswer(i -> Optional.ofNullable(guardada));
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void guardarCreaBorradorYCalculaMontosEnJava() {
        var r = service.guardar(9L, req(new LineaCotizacionRequest(500L, new BigDecimal("2"), " caja x24 ",
                new BigDecimal("24"), new BigDecimal("12000"), new BigDecimal("10"), new BigDecimal("19"))));

        assertEquals(EstadoCotizacion.BORRADOR, r.estado());
        assertEquals(OrigenCotizacion.FORMULARIO, r.origen());
        assertEquals("COP", r.moneda());
        assertEquals("caja x24", r.lineas().get(0).unidadOfertada());
        assertEquals(new BigDecimal("450.0000"), r.lineas().get(0).precioUnitarioNormalizado());
        assertEquals(new BigDecimal("25704.00"), r.total()); // 21.600 + 19 %
        verify(auditoria).exito(eq(AccionAuditoria.COTIZACION_GUARDAR), eq(EntidadAuditada.COTIZACION), eq(77L),
                anyString());
    }

    @Test
    void lineaQueNoEsDeLaRfqSeRechaza() {
        assertEquals("LINEA_RFQ_INVALIDA", assertThrows(NegocioException.class, () -> service.guardar(9L,
                req(new LineaCotizacionRequest(999L, BigDecimal.ONE, "und", BigDecimal.ONE, BigDecimal.TEN, null,
                        null)))).getCodigo());
    }

    @Test
    void soloInvitadoConRfqAbiertaYVigente() {
        sesionProveedor(41L); // no invitado: no se revela que la RFQ existe
        assertThrows(NoEncontradoException.class, () -> service.guardar(9L, req()));

        sesionProveedor(40L);
        rfq.setFechaLimite(LocalDateTime.now().minusMinutes(1));
        assertEquals("RFQ_NO_RECIBE_COTIZACIONES",
                assertThrows(NegocioException.class, () -> service.guardar(9L, req())).getCodigo());

        rfq.setFechaLimite(LocalDateTime.now().plusDays(1));
        rfq.setEstado(EstadoRfq.CERRADA);
        assertThrows(NegocioException.class, () -> service.enviar(9L));
    }

    @Test
    void enviarAvisaAlCompradorYYaNoSeEdita() {
        service.guardar(9L, req(new LineaCotizacionRequest(500L, BigDecimal.ONE, "und", BigDecimal.ONE,
                BigDecimal.TEN, null, null)));

        var r = service.enviar(9L);

        assertEquals(EstadoCotizacion.ENVIADA, r.estado());
        assertNotNull(r.enviadoEn());
        verify(notificaciones).notificar(eq(30L), eq(EventoNotificacion.COTIZACION_RECIBIDA), anyString());
        assertEquals("COTIZACION_ENVIADA",
                assertThrows(NegocioException.class, () -> service.guardar(9L, req())).getCodigo());
    }

    @Test
    void sinPreciosNiArchivoNoSePuedeEnviar() {
        service.guardar(9L, req());
        assertEquals("COTIZACION_INCOMPLETA",
                assertThrows(NegocioException.class, () -> service.enviar(9L)).getCodigo());
        verify(notificaciones, never()).notificar(any(), any(), any());
    }

    @Test
    void conArchivoBastaElDocumentoParaEnviar() {
        var archivo = new MockMultipartFile("archivo", "cotizacion.pdf", "application/pdf", "%PDF-1.7".getBytes());
        when(documentos.subir(eq(EntidadDocumento.COTIZACION), eq(77L), any()))
                .thenReturn(new DocumentoResponse(3L, "cotizacion.pdf", "application/pdf", 8, "COTIZACION", 77L, 9L, null));

        var r = service.subirArchivo(9L, archivo);
        assertEquals(OrigenCotizacion.ARCHIVO, r.origen());
        assertEquals(3L, r.documentoOriginalId());
        assertNull(r.total()); // la IA leerá los precios en el paso 13

        assertEquals(EstadoCotizacion.ENVIADA, service.enviar(9L).estado());
        verify(extraccion).programar(guardada); // la IA la lee en segundo plano
    }

    @Test
    void compraVeSobreCerradoMientrasLaRfqEstaAbierta() {
        service.guardar(9L, req(new LineaCotizacionRequest(500L, BigDecimal.ONE, "und", BigDecimal.ONE,
                BigDecimal.TEN, null, null)));
        service.enviar(9L);
        when(cotizaciones.findByRfq_IdAndEstadoNotOrderByEnviadoEnAsc(9L, EstadoCotizacion.BORRADOR))
                .thenReturn(List.of(guardada));

        var sellada = service.deRfq(9L).get(0);
        assertTrue(sellada.sellada());
        assertNull(sellada.total());
        assertTrue(sellada.lineas().isEmpty());
        assertEquals("Tech SAS", sellada.proveedorRazonSocial());

        rfq.setEstado(EstadoRfq.CERRADA);
        assertEquals(new BigDecimal("10.00"), service.deRfq(9L).get(0).total());
    }

    @Test
    void reglaDeDocumentosRespetaDuenoYSobreCerrado() {
        service.guardar(9L, req());
        UsuarioAutenticado dueno = new UsuarioAutenticado(9L, "p@x.com", "P", Set.of("PROVEEDOR"), 40L, 1L);
        UsuarioAutenticado otro = new UsuarioAutenticado(8L, "o@x.com", "O", Set.of("PROVEEDOR"), 41L, 1L);
        UsuarioAutenticado comprador = new UsuarioAutenticado(30L, "c@x.com", "C", Set.of("COMPRADOR"), null, 1L);

        assertDoesNotThrow(() -> regla.validarSubida(77L, dueno));
        assertThrows(AccesoDenegadoException.class, () -> regla.validarSubida(77L, otro));
        assertThrows(AccesoDenegadoException.class, () -> regla.validarLectura(77L, comprador)); // borrador

        guardada.setEstado(EstadoCotizacion.ENVIADA);
        assertThrows(AccesoDenegadoException.class, () -> regla.validarSubida(77L, dueno)); // ya enviada
        assertThrows(AccesoDenegadoException.class, () -> regla.validarLectura(77L, comprador)); // RFQ abierta
        rfq.setEstado(EstadoRfq.CERRADA);
        assertDoesNotThrow(() -> regla.validarLectura(77L, comprador));
        assertDoesNotThrow(() -> regla.validarLectura(77L, dueno));
        assertThrows(AccesoDenegadoException.class, () -> regla.validarLectura(77L, otro));
    }

    private static CotizacionRequest req(LineaCotizacionRequest... lineas) {
        return new CotizacionRequest(null, 5, 12, null, "Pago a 30 días", List.of(lineas));
    }

    private void sesionProveedor(Long proveedorId) {
        request.setAttribute(SesionActual.ATRIBUTO,
                new UsuarioAutenticado(9L, "p@x.com", "Proveedor", Set.of("PROVEEDOR"), proveedorId, 1L));
    }
}
