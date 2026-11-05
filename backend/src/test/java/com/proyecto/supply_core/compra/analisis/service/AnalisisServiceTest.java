package com.proyecto.supply_core.compra.analisis.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.compra.analisis.entity.Analisis;
import com.proyecto.supply_core.compra.analisis.entity.ConfigPesos;
import com.proyecto.supply_core.compra.analisis.entity.CriterioEvaluacion;
import com.proyecto.supply_core.compra.analisis.enums.EstadoAnalisis;
import com.proyecto.supply_core.compra.analisis.repository.AnalisisRepository;
import com.proyecto.supply_core.compra.cotizacion.entity.Cotizacion;
import com.proyecto.supply_core.compra.cotizacion.enums.EstadoCotizacion;
import com.proyecto.supply_core.compra.cotizacion.enums.OrigenCotizacion;
import com.proyecto.supply_core.compra.cotizacion.repository.CotizacionRepository;
import com.proyecto.supply_core.compra.cotizacion.service.ArmadorLineas;
import com.proyecto.supply_core.compra.rfq.entity.Rfq;
import com.proyecto.supply_core.compra.rfq.entity.RfqLinea;
import com.proyecto.supply_core.compra.rfq.enums.EstadoRfq;
import com.proyecto.supply_core.compra.rfq.repository.RfqRepository;
import com.proyecto.supply_core.compra.cotizacion.dto.LineaCotizacionRequest;
import com.proyecto.supply_core.ia.client.IAClient;
import com.proyecto.supply_core.ia.enums.EstadoProcesamiento;
import com.proyecto.supply_core.ia.service.ExtraccionService;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.proveedor.entity.Proveedor;
import com.proyecto.supply_core.proveedor.repository.EvaluacionProveedorRepository;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

class AnalisisServiceTest {

    private final AnalisisRepository analisis = mock(AnalisisRepository.class);
    private final RfqRepository rfqs = mock(RfqRepository.class);
    private final CotizacionRepository cotizaciones = mock(CotizacionRepository.class);
    private final EvaluacionProveedorRepository evaluaciones = mock(EvaluacionProveedorRepository.class);
    private final PesosService pesos = mock(PesosService.class);
    private final ExtraccionService extraccion = mock(ExtraccionService.class);
    private final IAClient ia = mock(IAClient.class);
    private final AnalisisService service = new AnalisisService(analisis, rfqs, cotizaciones, evaluaciones, pesos,
            extraccion, ia, mock(AuditoriaService.class), mock(PlatformTransactionManager.class));

    private Rfq rfq;
    private Cotizacion formulario;
    private Cotizacion archivo;

    @BeforeEach
    void setUp() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(SesionActual.ATRIBUTO, new UsuarioAutenticado(30L, "c@x.com", "C", Set.of("COMPRADOR"), null, 1L));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        rfq = new Rfq();
        rfq.setId(9L);
        rfq.setConsecutivo("RFQ-000009");
        rfq.setEstado(EstadoRfq.CERRADA);
        Producto p = new Producto();
        p.setId(100L);
        RfqLinea rl = new RfqLinea();
        rl.setId(500L);
        rl.setProducto(p);
        rfq.agregarLinea(rl);
        formulario = cotizacion(1L, "A", OrigenCotizacion.FORMULARIO, "450");
        archivo = cotizacion(2L, "B", OrigenCotizacion.ARCHIVO, "500");

        when(rfqs.findWithDetalleById(9L)).thenReturn(Optional.of(rfq));
        when(cotizaciones.findByRfq_IdAndEstadoNotOrderByEnviadoEnAsc(9L, EstadoCotizacion.BORRADOR))
                .thenReturn(List.of(formulario, archivo));
        ConfigPesos vigentes = pesosV2(); // se arma antes: crea mocks y no puede ir dentro de when(...)
        when(pesos.entidadVigente()).thenReturn(vigentes);
        when(analisis.findFirstByRfq_IdAndEstado(anyLong(), any())).thenReturn(Optional.empty());
        when(analisis.save(any())).thenAnswer(i -> {
            Analisis a = i.getArgument(0);
            a.setId(70L);
            return a;
        });
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void generaRankingConSnapshotDePesosYMarcaEnAnalisis() {
        when(extraccion.estados(any())).thenReturn(Map.of(2L, EstadoProcesamiento.COMPLETADO));

        var r = service.generar(9L);

        assertEquals(EstadoAnalisis.BORRADOR, r.estado());
        assertEquals(1L, r.configPesosId());
        assertEquals("A", r.resultados().get(0).proveedor());
        assertEquals(6, r.resultados().get(0).criterios().size());
        assertTrue(r.resultados().get(0).explicacion().startsWith("Puesto 1 de 2"));
        assertTrue(r.pendientes().isEmpty());
        assertEquals(EstadoCotizacion.EN_ANALISIS, formulario.getEstado());
    }

    @Test
    void soloConRfqCerradaYSinAnalisisPublicado() {
        rfq.setEstado(EstadoRfq.ABIERTA);
        assertEquals("RFQ_NO_CERRADA", assertThrows(NegocioException.class, () -> service.generar(9L)).getCodigo());

        rfq.setEstado(EstadoRfq.CERRADA);
        when(analisis.findFirstByRfq_IdAndEstado(9L, EstadoAnalisis.PUBLICADO)).thenReturn(Optional.of(new Analisis()));
        assertEquals("ANALISIS_PUBLICADO", assertThrows(NegocioException.class, () -> service.generar(9L)).getCodigo());
    }

    @Test
    void noSePublicaConCotizacionesPorRevisar() {
        when(extraccion.estados(any())).thenReturn(Map.of(2L, EstadoProcesamiento.REQUIERE_REVISION));
        var generado = service.generar(9L);
        assertEquals(1, generado.pendientes().size());
        assertTrue(generado.pendientes().get(0).startsWith("B: lectura REQUIERE_REVISION"));

        Analisis borrador = new Analisis();
        borrador.setRfq(rfq);
        Analisis.Resultado res = new Analisis.Resultado();
        res.setCotizacion(archivo);
        borrador.getResultados().add(res);
        when(analisis.findFirstByRfq_IdAndEstado(eq(9L), eq(EstadoAnalisis.BORRADOR))).thenReturn(Optional.of(borrador));
        assertEquals("EXTRACCION_PENDIENTE",
                assertThrows(NegocioException.class, () -> service.publicar(9L)).getCodigo());
    }

    @Test
    void explicarSinIaNoDisponible() {
        assertEquals("IA_NO_DISPONIBLE", assertThrows(NegocioException.class, () -> service.explicar(9L)).getCodigo());
    }

    private Cotizacion cotizacion(Long id, String razon, OrigenCotizacion origen, String precio) {
        Proveedor p = new Proveedor();
        p.setId(id + 40);
        p.setRazonSocial(razon);
        Cotizacion c = new Cotizacion();
        c.setId(id);
        c.setRfq(rfq);
        c.setProveedor(p);
        c.setOrigen(origen);
        c.setEstado(EstadoCotizacion.ENVIADA);
        c.setPlazoEntregaDias(3);
        c.setGarantiaMeses(12);
        ArmadorLineas.armar(c, rfq, List.of(new LineaCotizacionRequest(500L, new BigDecimal("48"), "und",
                BigDecimal.ONE, new BigDecimal(precio), null, null)));
        return c;
    }

    private static ConfigPesos pesosV2() {
        ConfigPesos c = new ConfigPesos();
        c.setId(1L);
        Map.of("PRECIO", "35", "CALIDAD", "20", "ENTREGA", "15", "GARANTIA", "10", "HISTORIAL", "15", "RIESGO", "5")
                .forEach((codigo, peso) -> c.agregar(criterio(codigo), new BigDecimal(peso)));
        return c;
    }

    private static CriterioEvaluacion criterio(String codigo) {
        CriterioEvaluacion c = mock(CriterioEvaluacion.class);
        when(c.getCodigo()).thenReturn(codigo);
        return c;
    }
}
