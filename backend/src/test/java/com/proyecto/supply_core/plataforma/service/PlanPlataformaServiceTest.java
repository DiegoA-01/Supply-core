package com.proyecto.supply_core.plataforma.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.plataforma.dto.PlanDtos;
import com.proyecto.supply_core.plataforma.entity.Plan;
import com.proyecto.supply_core.plataforma.enums.AccionPlataforma;
import com.proyecto.supply_core.plataforma.repository.ModuloRepository;
import com.proyecto.supply_core.plataforma.repository.PlanRepository;
import com.proyecto.supply_core.plataforma.repository.SuscripcionRepository;
import com.proyecto.supply_core.security.context.PlataformaActual;
import com.proyecto.supply_core.security.context.PlataformaAutenticada;

class PlanPlataformaServiceTest {

    private final PlanRepository planes = mock(PlanRepository.class);
    private final ModuloRepository modulos = mock(ModuloRepository.class);
    private final SuscripcionRepository suscripciones = mock(SuscripcionRepository.class);
    private final AccesoEmpresaService acceso = mock(AccesoEmpresaService.class);
    private final PlataformaAuditoriaService auditoria = mock(PlataformaAuditoriaService.class);
    private final PlanPlataformaService service = new PlanPlataformaService(planes, modulos, suscripciones, acceso,
            auditoria);

    private Plan basico;

    @BeforeEach
    void setUp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(PlataformaActual.ATRIBUTO, new PlataformaAutenticada(3L, "dueno@x.com", "Dueño"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(modulos.existsById(anyString())).thenAnswer(i -> Set.of("INVENTARIO", "COMPRAS", "REPORTES").contains(i.getArgument(0)));
        basico = new Plan();
        basico.setId(1L);
        basico.setCodigo("BASICO");
        basico.setNombre("Básico");
        basico.getModulos().addAll(Set.of("INVENTARIO", "COMPRAS"));
        when(planes.findById(1L)).thenReturn(Optional.of(basico));
        when(planes.save(any())).thenAnswer(i -> {
            Plan p = i.getArgument(0);
            p.setId(5L);
            return p;
        });
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void listaLosPlanesConSusModulosOrdenadosYCuantasEmpresasLosUsan() {
        when(planes.findAllByOrderByCodigoAsc()).thenReturn(List.of(basico));
        when(suscripciones.countByPlanId(1L)).thenReturn(4L);

        var lista = service.listar();

        assertEquals(List.of("COMPRAS", "INVENTARIO"), lista.get(0).modulos());
        assertEquals(4L, lista.get(0).empresas());
    }

    @Test
    void crearNormalizaElCodigoYValidaLosModulos() {
        var r = service.crear(new PlanDtos.Crear("pro_plus", " Pro Plus ", Set.of("compras", "reportes")));

        assertEquals("PRO_PLUS", r.codigo());
        assertEquals("Pro Plus", r.nombre());
        assertEquals(List.of("COMPRAS", "REPORTES"), r.modulos());
        assertTrue(r.activo());
        verify(auditoria).registrar(eq(3L), eq(AccionPlataforma.PLAN_CREAR), isNull(), eq(true), anyString());
    }

    @Test
    void crearRechazaCodigoRepetidoYModulosInexistentes() {
        when(planes.existsByCodigoIgnoreCase("BASICO")).thenReturn(true);
        assertEquals("CODIGO_DUPLICADO", codigo(() -> service.crear(new PlanDtos.Crear("basico", "x", Set.of("COMPRAS")))));

        var ex = assertThrows(NegocioException.class,
                () -> service.crear(new PlanDtos.Crear("NUEVO", "x", Set.of("COMPRAS", "VUELOS", "TELEPORTE"))));
        assertEquals("MODULO_INEXISTENTE", ex.getCodigo());
        assertTrue(ex.getMessage().contains("TELEPORTE") && ex.getMessage().contains("VUELOS"));
        verify(planes, never()).save(any());
    }

    @Test
    void editarReemplazaLosModulosYLimpiaLaMemoriaDeTodasLasEmpresas() {
        when(suscripciones.countByPlanId(1L)).thenReturn(2L);

        var r = service.editar(1L, new PlanDtos.Editar("Básico plus", Set.of("reportes"), null));

        assertEquals("Básico plus", r.nombre());
        assertEquals(List.of("REPORTES"), r.modulos());
        assertEquals(2L, r.empresas());
        verify(acceso).invalidarTodo();
        verify(auditoria).registrar(eq(3L), eq(AccionPlataforma.PLAN_EDITAR), isNull(), eq(true), anyString());
    }

    @Test
    void unPlanSinModulosOConModulosInexistentesNoSeAcepta() {
        assertEquals("PLAN_SIN_MODULOS", codigo(() -> service.editar(1L, new PlanDtos.Editar(null, Set.of(), null))));
        assertEquals("MODULO_INEXISTENTE", codigo(() -> service.editar(1L, new PlanDtos.Editar(null, Set.of("VUELOS"), null))));
        assertEquals(Set.of("INVENTARIO", "COMPRAS"), basico.getModulos());
    }

    @Test
    void unPlanEnUsoNoSeDesactivaPeroUnoLibreSi() {
        when(suscripciones.countByPlanId(1L)).thenReturn(1L);
        assertEquals("PLAN_EN_USO", codigo(() -> service.editar(1L, new PlanDtos.Editar(null, null, false))));
        assertTrue(basico.isActivo());

        when(suscripciones.countByPlanId(1L)).thenReturn(0L);
        var r = service.editar(1L, new PlanDtos.Editar(null, null, false));
        assertFalse(r.activo());
    }

    @Test
    void editarUnPlanInexistenteEsNoEncontrado() {
        assertThrows(NoEncontradoException.class, () -> service.editar(99L, new PlanDtos.Editar("x", null, null)));
    }

    private String codigo(Runnable accion) {
        return assertThrows(NegocioException.class, accion::run).getCodigo();
    }
}
