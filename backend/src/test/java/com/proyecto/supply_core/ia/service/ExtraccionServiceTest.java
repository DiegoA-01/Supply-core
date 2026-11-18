package com.proyecto.supply_core.ia.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.catalogo.entity.UnidadMedida;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.compra.analisis.entity.Analisis;
import com.proyecto.supply_core.compra.analisis.enums.EstadoAnalisis;
import com.proyecto.supply_core.compra.analisis.repository.AnalisisRepository;
import com.proyecto.supply_core.compra.cotizacion.dto.LineaCotizacionRequest;
import com.proyecto.supply_core.compra.cotizacion.entity.Cotizacion;
import com.proyecto.supply_core.compra.cotizacion.enums.EstadoCotizacion;
import com.proyecto.supply_core.compra.cotizacion.enums.OrigenCotizacion;
import com.proyecto.supply_core.compra.cotizacion.repository.CotizacionRepository;
import com.proyecto.supply_core.compra.rfq.entity.Rfq;
import com.proyecto.supply_core.compra.rfq.entity.RfqLinea;
import com.proyecto.supply_core.compra.rfq.enums.EstadoRfq;
import com.proyecto.supply_core.documento.service.DocumentoService;
import com.proyecto.supply_core.ia.client.IAClient;
import com.proyecto.supply_core.ia.client.IAException;
import com.proyecto.supply_core.ia.config.IAProperties;
import com.proyecto.supply_core.ia.dto.ExtraccionIA;
import com.proyecto.supply_core.ia.entity.ExtraccionCampo;
import com.proyecto.supply_core.ia.entity.ProcesamientoIa;
import com.proyecto.supply_core.ia.enums.EstadoProcesamiento;
import com.proyecto.supply_core.ia.repository.ExtraccionCampoRepository;
import com.proyecto.supply_core.ia.repository.ProcesamientoIaRepository;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.proveedor.entity.Proveedor;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

class ExtraccionServiceTest {

    private final ProcesamientoIaRepository procesamientos = mock(ProcesamientoIaRepository.class);
    private final ExtraccionCampoRepository campos = mock(ExtraccionCampoRepository.class);
    private final CotizacionRepository cotizaciones = mock(CotizacionRepository.class);
    private final DocumentoService documentos = mock(DocumentoService.class);
    private final IAClient ia = mock(IAClient.class);
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    private final NotificacionService notificaciones = mock(NotificacionService.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final AnalisisRepository analisisRepo = mock(AnalisisRepository.class);
    private final IAProperties props = new IAProperties(IAProperties.Proveedor.OLLAMA, new BigDecimal("0.80"),
            new IAProperties.Claude(null, "claude-opus-5-5"), new IAProperties.Ollama("http://x", "m", 60),
            new IAProperties.Gemini(null, "gemini-2.5-flash", 60));
    private final ExtraccionService service = new ExtraccionService(procesamientos, campos, cotizaciones, documentos,
            ia, props, eventos, notificaciones, auditoria, analisisRepo);

    private Cotizacion cot;
    private Rfq rfq;
    private ProcesamientoIa proc;
    private final List<ExtraccionCampo> guardados = new ArrayList<>();

    @BeforeEach
    void setUp() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(SesionActual.ATRIBUTO, new UsuarioAutenticado(30L, "c@x.com", "C", Set.of("COMPRADOR"), null, 1L));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        rfq = new Rfq();
        rfq.setId(9L);
        rfq.setConsecutivo("RFQ-000009");
        rfq.setUsuarioId(30L);
        rfq.setEstado(EstadoRfq.CERRADA);
        Producto lapiz = new Producto();
        lapiz.setId(100L);
        lapiz.setCodigo("LAP");
        UnidadMedida und = new UnidadMedida();
        und.setCodigo("UND");
        RfqLinea rl = new RfqLinea();
        rl.setId(500L);
        rl.setProducto(lapiz);
        rl.setUnidadMedida(und);
        rl.setCantidad(new BigDecimal("48"));
        rfq.agregarLinea(rl);
        Proveedor p = new Proveedor();
        p.setId(40L);
        p.setRazonSocial("Tech SAS");
        cot = new Cotizacion();
        cot.setId(77L);
        cot.setRfq(rfq);
        cot.setProveedor(p);
        cot.setOrigen(OrigenCotizacion.ARCHIVO);
        cot.setEstado(EstadoCotizacion.ENVIADA);
        cot.setDocumentoOriginalId(3L);
        proc = new ProcesamientoIa();
        proc.setId(5L);
        proc.setCotizacionId(77L);

        when(cotizaciones.findWithRfqById(77L)).thenReturn(Optional.of(cot));
        when(procesamientos.findById(5L)).thenReturn(Optional.of(proc));
        when(procesamientos.findByCotizacionId(77L)).thenReturn(Optional.of(proc));
        AtomicLong ids = new AtomicLong(900);
        when(cotizaciones.saveAndFlush(any())).thenAnswer(i -> {
            cot.getLineas().forEach(l -> {
                if (l.getId() == null) {
                    l.setId(ids.incrementAndGet());
                }
            });
            return cot;
        });
        when(campos.saveAll(any())).thenAnswer(i -> {
            Iterable<ExtraccionCampo> it = i.getArgument(0);
            it.forEach(guardados::add);
            return guardados;
        });
        when(campos.findByProcesamiento_IdOrderByIdAsc(5L)).thenAnswer(i -> guardados);
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void lecturaConfiableQuedaCompletadaConMontosDeJava() {
        service.guardarResultado(5L, leido("2", "caja x24", "24", "$ 12.000", "10", "19", 0.95, 0.9, 0.97));

        assertEquals(EstadoProcesamiento.COMPLETADO, proc.getEstado());
        assertEquals(1, cot.getLineas().size());
        assertEquals(new BigDecimal("450.0000"), cot.getLineas().get(0).getPrecioUnitarioNormalizado());
        assertEquals(new BigDecimal("25704.00"), cot.getTotal());
        assertEquals(30, cot.getPlazoEntregaDias());
        assertTrue(guardados.stream().anyMatch(c -> c.getCampo().equals("precioUnitario") && c.isCritico()));
        verify(notificaciones, never()).notificar(anyLong(), any(), anyString());
    }

    @Test
    void precioDudosoPideRevisionYAvisaAlComprador() {
        service.guardarResultado(5L, leido("2", "caja x24", "24", "12.000", "0", "19", 0.95, 0.9, 0.40));

        assertEquals(EstadoProcesamiento.REQUIERE_REVISION, proc.getEstado());
        verify(notificaciones).notificar(eq(30L), eq(EventoNotificacion.EXTRACCION_REQUIERE_REVISION), anyString());
    }

    @Test
    void renglonIlegibleNoSeInventa() {
        service.guardarResultado(5L, leido("dos", "und", "1", "", "0", "0", 0.9, 0.9, 0.9));

        assertTrue(cot.getLineas().isEmpty());
        assertNull(cot.getTotal());
        assertEquals(EstadoProcesamiento.REQUIERE_REVISION, proc.getEstado());
        assertTrue(guardados.stream().anyMatch(c -> c.getCampo().equals(ExtraccionService.RENGLON_NO_LEIDO)));
    }

    @Test
    void confirmarCorrigeElPrecioRecalculaYCompleta() {
        service.guardarResultado(5L, leido("2", "caja x24", "24", "12.000", "0", "0", 0.95, 0.9, 0.40));
        ExtraccionCampo precio = guardados.stream().filter(c -> c.getCampo().equals("precioUnitario")).findFirst()
                .orElseThrow();
        precio.setId(1L);
        when(campos.findByIdAndProcesamiento_Id(1L, 5L)).thenReturn(Optional.of(precio));

        var r = service.confirmar(77L, 1L, "11.500");

        assertEquals(EstadoProcesamiento.COMPLETADO, r.estado());
        assertEquals(new BigDecimal("23000.00"), cot.getTotal());
        assertEquals("11.500", precio.getValorConfirmado());
        assertThrows(ParametroInvalidoException.class, () -> service.confirmar(77L, 1L, "gratis"));
    }

    @Test
    void sobreCerradoMientrasLaRfqEstaAbierta() {
        rfq.setEstado(EstadoRfq.ABIERTA);
        assertEquals("SOBRE_CERRADO", assertThrows(NegocioException.class, () -> service.obtener(77L)).getCodigo());
        assertThrows(NegocioException.class, () -> service.digitar(77L, List.of()));
    }

    @Test
    void digitarDejaLaCotizacionListaAunqueYaEsteEnAnalisis() {
        cot.setEstado(EstadoCotizacion.EN_ANALISIS);
        var r = service.digitar(77L, List.of(new LineaCotizacionRequest(500L, new BigDecimal("48"), "und",
                BigDecimal.ONE, new BigDecimal("480"), null, new BigDecimal("19"))));

        assertEquals(EstadoProcesamiento.COMPLETADO, r.estado());
        assertEquals("manual", proc.getModelo());
        assertEquals(new BigDecimal("27417.60"), cot.getTotal());
        verify(campos).borrarDe(5L);
    }

    @Test
    void conAnalisisPublicadoLosDatosQuedanFijos() {
        when(analisisRepo.findFirstByRfq_IdAndEstado(9L, EstadoAnalisis.PUBLICADO))
                .thenReturn(Optional.of(new Analisis()));
        assertEquals("ANALISIS_PUBLICADO", assertThrows(NegocioException.class,
                () -> service.digitar(77L, List.of())).getCodigo());
    }

    @Test
    void sinIaQuedaParaDigitarYConIaSeProgramaLaLectura() {
        when(ia.disponible()).thenReturn(false);
        when(ia.modelo()).thenReturn("ninguno");
        service.programar(cot);
        assertEquals(EstadoProcesamiento.REQUIERE_REVISION, proc.getEstado());
        verify(eventos, never()).publishEvent(any(Object.class));

        when(ia.disponible()).thenReturn(true);
        service.programar(cot);
        assertEquals(EstadoProcesamiento.PENDIENTE, proc.getEstado());
        verify(eventos).publishEvent(new LecturaProgramadaEvent(5L));
    }

    @Test
    void procesadorGuardaElErrorSiLaIaFalla() {
        when(documentos.leerParaSistema(3L))
                .thenReturn(new DocumentoService.ContenidoDocumento("%PDF".getBytes(), "application/pdf", "c.pdf"));
        when(ia.extraer(any(), any())).thenThrow(new IAException("Ollama no respondió"));
        new ProcesadorExtraccion(service, ia).procesar(5L);

        assertEquals(EstadoProcesamiento.ERROR, proc.getEstado());
        assertEquals("Ollama no respondió", proc.getError());
        assertEquals(1, proc.getIntentos());
    }

    @Test
    void procesadorConIaFalsaLeeYGuarda() {
        when(documentos.leerParaSistema(3L))
                .thenReturn(new DocumentoService.ContenidoDocumento("%PDF".getBytes(), "application/pdf", "c.pdf"));
        when(ia.extraer(any(), any())).thenReturn(leido("48", "und", "1", "480", "0", "19", 0.99, 0.99, 0.99));
        new ProcesadorExtraccion(service, ia).procesar(5L);

        assertEquals(EstadoProcesamiento.COMPLETADO, proc.getEstado());
        assertEquals(new BigDecimal("27417.60"), cot.getTotal());
    }

    // ------------------------------------------------------------- cruce del total impreso

    private ExtraccionCampo campoDeCabecera(String nombre) {
        return guardados.stream().filter(c -> c.getCotizacionLineaId() == null && c.getCampo().equals(nombre))
                .findFirst().orElse(null);
    }

    @Test
    void siElTotalImpresoCuadraConLoCalculadoPorJavaQuedaCompletadoYVerificado() {
        service.guardarResultado(5L, conTotal("$ 27.417,60", "48", "480"));

        assertEquals(EstadoProcesamiento.COMPLETADO, proc.getEstado());
        ExtraccionCampo total = campoDeCabecera(ExtraccionService.TOTAL_IMPRESO);
        assertEquals("$ 27.417,60", total.getValorExtraido());
        assertEquals(new BigDecimal("1.000"), total.getConfianza());
        assertTrue(total.isCritico());
        assertTrue(service.obtener(77L).alertas().isEmpty());
        verify(notificaciones, never()).notificar(anyLong(), any(), anyString());
    }

    @Test
    void unaDiferenciaDentroDelUnoPorCientoSeAcepta() {
        // calculado 27.417,60: el 1 % de 27.600 son 276 y la diferencia es 182,40
        service.guardarResultado(5L, conTotal("27.600", "48", "480"));

        assertEquals(EstadoProcesamiento.COMPLETADO, proc.getEstado());
    }

    @Test
    void siElTotalImpresoNoCuadraPideRevisionConLaAlertaYAvisaAlComprador() {
        // la IA leyó 480 por unidad con confianza alta, pero el documento imprime un total de 30.000.000
        service.guardarResultado(5L, conTotal("30.000.000", "48", "480"));

        assertEquals(EstadoProcesamiento.REQUIERE_REVISION, proc.getEstado());
        assertTrue(proc.getError().startsWith("TOTAL_NO_CUADRA"));
        assertEquals(new BigDecimal("27417.60"), cot.getTotal()); // el sistema no toma el total de la IA
        ExtraccionCampo total = campoDeCabecera(ExtraccionService.TOTAL_IMPRESO);
        assertEquals(new BigDecimal("0.000"), total.getConfianza());
        assertTrue(total.pendiente(new BigDecimal("0.80")));
        assertEquals(List.of("TOTAL_NO_CUADRA"), service.obtener(77L).alertas());
        verify(notificaciones).notificar(eq(30L), eq(EventoNotificacion.EXTRACCION_REQUIERE_REVISION),
                org.mockito.ArgumentMatchers.contains("TOTAL_NO_CUADRA"));
    }

    @Test
    void corregirUnRenglonHastaQueCuadreCompletaSinConfirmarElTotal() {
        // el documento imprime 28.560 (precio 500); la IA leyó 480 con confianza alta
        service.guardarResultado(5L, conTotal("28.560", "48", "480"));
        assertEquals(EstadoProcesamiento.REQUIERE_REVISION, proc.getEstado());
        ExtraccionCampo precio = guardados.stream().filter(c -> c.getCampo().equals("precioUnitario")).findFirst()
                .orElseThrow();
        precio.setId(1L);
        when(campos.findByIdAndProcesamiento_Id(1L, 5L)).thenReturn(Optional.of(precio));

        var r = service.confirmar(77L, 1L, "500");

        assertEquals(new BigDecimal("28560.00"), cot.getTotal());
        assertEquals(EstadoProcesamiento.COMPLETADO, r.estado());
        assertEquals(new BigDecimal("1.000"), campoDeCabecera(ExtraccionService.TOTAL_IMPRESO).getConfianza());
        assertTrue(r.alertas().isEmpty());
    }

    @Test
    void siSeCorrigeOtroDatoPeroLaDiferenciaSigueLaAlertaSigueConLasCifrasNuevas() {
        service.guardarResultado(5L, conTotal("30.000.000", "48", "480"));
        ExtraccionCampo precio = guardados.stream().filter(c -> c.getCampo().equals("precioUnitario")).findFirst()
                .orElseThrow();
        precio.setId(1L);
        when(campos.findByIdAndProcesamiento_Id(1L, 5L)).thenReturn(Optional.of(precio));

        var r = service.confirmar(77L, 1L, "490");

        assertEquals(EstadoProcesamiento.REQUIERE_REVISION, r.estado());
        assertEquals(List.of("TOTAL_NO_CUADRA"), r.alertas());
        assertTrue(r.error().startsWith("TOTAL_NO_CUADRA"));
        assertTrue(r.error().contains("27988.80")); // 48 × 490 con IVA 19 %: ya refleja el precio corregido
    }

    @Test
    void laPersonaPuedeConfirmarElTotalImpresoCuandoElDocumentoDelProveedorNoCuadra() {
        service.guardarResultado(5L, conTotal("30.000.000", "48", "480"));
        ExtraccionCampo total = campoDeCabecera(ExtraccionService.TOTAL_IMPRESO);
        total.setId(2L);
        when(campos.findByIdAndProcesamiento_Id(2L, 5L)).thenReturn(Optional.of(total));

        assertThrows(ParametroInvalidoException.class, () -> service.confirmar(77L, 2L, "mucho"));
        var r = service.confirmar(77L, 2L, "30.000.000");

        assertEquals(EstadoProcesamiento.COMPLETADO, r.estado()); // decisión de una persona, con su usuario y hora
        assertEquals("30.000.000", total.getValorConfirmado());
        assertEquals(30L, total.getConfirmadoPorUsuarioId());
        assertEquals(new BigDecimal("27417.60"), cot.getTotal()); // el total de la cotización sigue siendo el de Java
    }

    @Test
    void sinTotalImpresoNoHayNadaQueCruzar() {
        service.guardarResultado(5L, leido("48", "und", "1", "480", "0", "19", 0.99, 0.99, 0.99));

        assertNull(campoDeCabecera(ExtraccionService.TOTAL_IMPRESO));
        assertEquals(EstadoProcesamiento.COMPLETADO, proc.getEstado());
    }

    private static ExtraccionIA conTotal(String total, String cantidad, String precio) {
        return new ExtraccionIA("COP", "30 días", "12", "", "Pago a 30 días", 0.9, List.of(
                new ExtraccionIA.LineaExtraida(500L, "Lápiz HB", cantidad, "und", "1", precio, "0", "19", 0.99, 0.99,
                        0.99)), total);
    }

    private static ExtraccionIA leido(String cant, String unidad, String factor, String precio, String desc, String iva,
            double cc, double cu, double cp) {
        return new ExtraccionIA("COP", "30 días", "12", "", "Pago a 30 días", 0.9, List.of(
                new ExtraccionIA.LineaExtraida(500L, "Lápiz HB", cant, unidad, factor, precio, desc, iva, cc, cu, cp)));
    }
}
