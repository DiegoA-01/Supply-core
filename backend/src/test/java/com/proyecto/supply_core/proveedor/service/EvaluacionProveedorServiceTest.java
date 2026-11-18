package com.proyecto.supply_core.proveedor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.proveedor.dto.IndicadoresEvaluacion;
import com.proyecto.supply_core.proveedor.entity.Proveedor;
import com.proyecto.supply_core.proveedor.repository.EvaluacionProveedorRepository;
import com.proyecto.supply_core.proveedor.repository.ProveedorRepository;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

class EvaluacionProveedorServiceTest {

    private final EvaluacionProveedorRepository evaluaciones = mock(EvaluacionProveedorRepository.class);
    private final ProveedorRepository proveedores = mock(ProveedorRepository.class);
    private final EvaluacionProveedorService service = new EvaluacionProveedorService(evaluaciones, proveedores);

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void scoreEsElPromedioDeLosIndicadoresConDatos() {
        var i = new IndicadoresEvaluacion(new BigDecimal("100"), new BigDecimal("80"), null, null,
                new BigDecimal("75.5"), null);
        assertEquals(new BigDecimal("85.17"), i.score()); // (100 + 80 + 75.5) / 3, los null no cuentan
        assertNull(new IndicadoresEvaluacion(null, null, null, null, null, null).score());
    }

    @Test
    void indicadorFueraDeRangoSeRechaza() {
        assertThrows(IllegalArgumentException.class,
                () -> new IndicadoresEvaluacion(new BigDecimal("100.01"), null, null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new IndicadoresEvaluacion(null, null, new BigDecimal("-1"), null, null, null));
    }

    @Test
    void registrarGuardaYRecalculaElScoreActual() {
        Proveedor p = new Proveedor();
        p.setId(40L);
        when(proveedores.findById(40L)).thenReturn(Optional.of(p));
        when(evaluaciones.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(evaluaciones.promedioScore(40L)).thenReturn(87.125);

        var r = service.registrar(40L, 7L, new IndicadoresEvaluacion(new BigDecimal("90"), null, null, null, null, null));

        assertEquals(new BigDecimal("90.00"), r.score());
        assertNull(r.calidad());
        assertEquals(new BigDecimal("87.13"), p.getScoreActual());
    }

    @Test
    void sinEvaluacionesConDatosElScoreActualQuedaNull() {
        Proveedor p = new Proveedor();
        p.setScoreActual(new BigDecimal("50"));
        when(proveedores.findById(40L)).thenReturn(Optional.of(p));
        when(evaluaciones.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(evaluaciones.promedioScore(40L)).thenReturn(null);

        service.registrar(40L, 7L, new IndicadoresEvaluacion(null, null, null, null, null, null));

        assertNull(p.getScoreActual());
    }

    @Test
    void ordenYaEvaluadaOProveedorInexistente() {
        when(proveedores.findById(40L)).thenReturn(Optional.of(new Proveedor()));
        when(evaluaciones.existsByProveedorIdAndOrdenCompraId(40L, 7L)).thenReturn(true);
        var vacios = new IndicadoresEvaluacion(null, null, null, null, null, null);

        assertEquals("EVALUACION_DUPLICADA",
                assertThrows(NegocioException.class, () -> service.registrar(40L, 7L, vacios)).getCodigo());
        assertThrows(NoEncontradoException.class, () -> service.registrar(99L, 7L, vacios));
        verify(evaluaciones, never()).saveAndFlush(any());
    }

    @Test
    void elPortalSoloVeLasSuyas() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(SesionActual.ATRIBUTO,
                new UsuarioAutenticado(1L, "c@x.com", "Compras", Set.of("ADMIN_COMPRAS"), null, 1L));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
        assertThrows(AccesoDenegadoException.class, () -> service.mias(PageRequest.of(0, 20)));

        req.setAttribute(SesionActual.ATRIBUTO,
                new UsuarioAutenticado(9L, "p@x.com", "Prov", Set.of("PROVEEDOR"), 40L, 1L));
        when(proveedores.existsById(40L)).thenReturn(true);
        when(evaluaciones.findByProveedorId(any(), any())).thenReturn(Page.empty());

        assertEquals(0, service.mias(PageRequest.of(0, 20)).totalElementos());
        verify(evaluaciones).findByProveedorId(eq(40L), any());
    }
}
