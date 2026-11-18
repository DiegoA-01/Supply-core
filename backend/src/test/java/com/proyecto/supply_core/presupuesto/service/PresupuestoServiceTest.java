package com.proyecto.supply_core.presupuesto.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.compra.solicitud.repository.SolicitudCompraRepository;
import com.proyecto.supply_core.empresa.entity.CentroCosto;
import com.proyecto.supply_core.empresa.repository.CentroCostoRepository;
import com.proyecto.supply_core.presupuesto.dto.EvaluacionPresupuesto;
import com.proyecto.supply_core.presupuesto.dto.PresupuestoRequest;
import com.proyecto.supply_core.presupuesto.dto.PresupuestoUpdateRequest;
import com.proyecto.supply_core.presupuesto.entity.Presupuesto;
import com.proyecto.supply_core.presupuesto.repository.PresupuestoRepository;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

class PresupuestoServiceTest {

    private final PresupuestoRepository presupuestos = mock(PresupuestoRepository.class);
    private final SolicitudCompraRepository solicitudes = mock(SolicitudCompraRepository.class);
    private final CentroCostoRepository centros = mock(CentroCostoRepository.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final PresupuestoService service = new PresupuestoService(presupuestos, solicitudes, centros, auditoria);

    private CentroCosto centro;

    @BeforeEach
    void setUp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(SesionActual.ATRIBUTO,
                new UsuarioAutenticado(20L, "a@x.com", "Admin", Set.of("ADMIN_COMPRAS"), null, 1L));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        centro = new CentroCosto();
        centro.setId(5L);
        centro.setCodigo("ADM-01");
        centro.setActivo(true);
        when(centros.findById(5L)).thenReturn(Optional.of(centro));
        when(presupuestos.save(any())).thenAnswer(i -> {
            Presupuesto p = i.getArgument(0);
            p.setId(1L);
            return p;
        });
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void crearGuardaElPresupuestoCalculaElUsoYAudita() {
        when(solicitudes.comprometido(eq(5L), eq(LocalDate.of(2026, 11, 1)), eq(LocalDate.of(2026, 11, 30)), any(), any()))
                .thenReturn(new BigDecimal("2500"));

        var r = service.crear(new PresupuestoRequest(5L, 2026, 11, new BigDecimal("10000")));

        assertEquals(new BigDecimal("10000.00"), r.monto());
        assertEquals(new BigDecimal("2500.00"), r.comprometido());
        assertEquals(new BigDecimal("7500.00"), r.disponible());
        assertEquals(new BigDecimal("25.00"), r.usoPct());
        verify(auditoria).exito(eq(AccionAuditoria.PRESUPUESTO_CREAR), eq(EntidadAuditada.PRESUPUESTO), eq(1L),
                anyString());
    }

    @Test
    void crearRechazaCentroInexistenteInactivoOMesRepetido() {
        assertThrows(NoEncontradoException.class,
                () -> service.crear(new PresupuestoRequest(99L, 2026, 11, new BigDecimal("1"))));

        centro.setActivo(false);
        assertEquals("CENTRO_COSTO_INACTIVO", assertThrows(NegocioException.class,
                () -> service.crear(new PresupuestoRequest(5L, 2026, 11, new BigDecimal("1")))).getCodigo());

        centro.setActivo(true);
        when(presupuestos.existsByCentroCostoIdAndAnioAndMes(5L, 2026, 11)).thenReturn(true);
        var ex = assertThrows(NegocioException.class,
                () -> service.crear(new PresupuestoRequest(5L, 2026, 11, new BigDecimal("1"))));
        assertEquals("PRESUPUESTO_DUPLICADO", ex.getCodigo());
        assertTrue(ex.getMessage().contains("2026-11"));
        verify(presupuestos, never()).save(any());
    }

    @Test
    void actualizarCorrigeSoloElMontoYAudita() {
        Presupuesto p = presupuesto(10000);
        when(presupuestos.findById(1L)).thenReturn(Optional.of(p));

        var r = service.actualizar(1L, new PresupuestoUpdateRequest(new BigDecimal("12500.5")));

        assertEquals(new BigDecimal("12500.50"), r.monto());
        assertEquals(5L, r.centroCostoId());
        assertEquals(11, r.mes());
        verify(auditoria).exito(eq(AccionAuditoria.PRESUPUESTO_EDITAR), eq(EntidadAuditada.PRESUPUESTO), eq(1L),
                anyString());
        assertThrows(NoEncontradoException.class,
                () -> service.actualizar(2L, new PresupuestoUpdateRequest(new BigDecimal("1"))));
    }

    @Test
    void sinPresupuestoEnElMesNoHayControl() {
        when(presupuestos.bloquear(5L, 2026, 11)).thenReturn(Optional.empty());

        var ev = service.evaluar(5L, LocalDate.of(2026, 11, 20), new BigDecimal("999999"), 1L);

        assertSame(EvaluacionPresupuesto.SIN_PRESUPUESTO, ev);
        assertFalse(ev.excede());
        verify(solicitudes, never()).comprometido(any(), any(), any(), any(), any());
    }

    @Test
    void excedeSoloCuandoLoComprometidoMasLaSolicitudSuperaElTope() {
        when(presupuestos.bloquear(5L, 2026, 11)).thenReturn(Optional.of(presupuesto(10000)));
        when(solicitudes.comprometido(eq(5L), eq(LocalDate.of(2026, 11, 1)), eq(LocalDate.of(2026, 11, 30)),
                eq(PresupuestoService.ESTADOS_COMPROMETEN), eq(7L))).thenReturn(new BigDecimal("8000"));

        var justo = service.evaluar(5L, LocalDate.of(2026, 11, 20), new BigDecimal("2000.00"), 7L);
        var pasado = service.evaluar(5L, LocalDate.of(2026, 11, 20), new BigDecimal("2000.01"), 7L);

        assertTrue(justo.hayPresupuesto());
        assertFalse(justo.excede()); // llegar justo al tope se permite
        assertTrue(pasado.excede());
        assertEquals(new BigDecimal("10000.00"), pasado.presupuesto());
        assertEquals(new BigDecimal("8000.00"), pasado.comprometido());
        assertEquals(new BigDecimal("2000.00"), pasado.disponible());
    }

    @Test
    void sinSolicitudesVigentesLoComprometidoEsCero() {
        when(presupuestos.bloquear(5L, 2026, 2)).thenReturn(Optional.of(presupuesto(500)));
        when(solicitudes.comprometido(any(), any(), any(), any(), any())).thenReturn(null);

        var ev = service.evaluar(5L, LocalDate.of(2026, 2, 10), new BigDecimal("500"), null);

        assertEquals(new BigDecimal("0.00"), ev.comprometido());
        assertFalse(ev.excede());
        verify(solicitudes).comprometido(eq(5L), eq(LocalDate.of(2026, 2, 1)), eq(LocalDate.of(2026, 2, 28)), any(),
                eq(null));
    }

    @Test
    void listarYObtenerAgregaLoComprometidoDeCadaMes() {
        when(presupuestos.buscar(5L, 2026)).thenReturn(List.of(presupuesto(10000)));
        when(solicitudes.comprometido(any(), any(), any(), any(), any())).thenReturn(new BigDecimal("12000"));

        var fila = service.listar(5L, 2026).get(0);

        assertEquals(new BigDecimal("-2000.00"), fila.disponible()); // pasado por una excepción aprobada
        assertEquals(new BigDecimal("120.00"), fila.usoPct());
        assertThrows(NoEncontradoException.class, () -> service.obtener(9L));
    }

    private static Presupuesto presupuesto(int monto) {
        Presupuesto p = new Presupuesto();
        p.setId(1L);
        p.setCentroCostoId(5L);
        p.setAnio(2026);
        p.setMes(monto == 500 ? 2 : 11);
        p.setMonto(new BigDecimal(monto).setScale(2));
        return p;
    }
}
