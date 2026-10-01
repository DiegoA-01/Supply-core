package com.proyecto.supply_core.empresa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.util.EmpresaUnica;
import com.proyecto.supply_core.empresa.dto.PoliticaAprobacionRequest;
import com.proyecto.supply_core.empresa.entity.PoliticaAprobacion;
import com.proyecto.supply_core.empresa.enums.TipoPolitica;
import com.proyecto.supply_core.empresa.repository.PoliticaAprobacionRepository;
import com.proyecto.supply_core.usuario.entity.Rol;
import com.proyecto.supply_core.usuario.repository.RolRepository;

class PoliticaAprobacionServiceTest {

    private final PoliticaAprobacionRepository repo = mock(PoliticaAprobacionRepository.class);
    private final RolRepository roles = mock(RolRepository.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final PoliticaAprobacionService service = new PoliticaAprobacionService(repo, roles, auditoria);
    private final List<PoliticaAprobacion> activasSolicitud = new ArrayList<>();
    private Rol aprobador;

    @BeforeEach
    void setUp() {
        aprobador = new Rol();
        aprobador.setCodigo("APROBADOR");
        when(roles.findByCodigo("APROBADOR")).thenReturn(Optional.of(aprobador));
        when(repo.findByEmpresaIdAndTipoAndActivoTrue(EmpresaUnica.ID, TipoPolitica.SOLICITUD))
                .thenReturn(activasSolicitud);
        when(repo.save(any())).thenAnswer(inv -> {
            PoliticaAprobacion p = inv.getArgument(0);
            p.setId(99L);
            return p;
        });
        // vigentes: [0, 1.000.000) y [1.000.000, 5.000.000)
        activasSolicitud.add(politica(1L, "0", "1000000"));
        activasSolicitud.add(politica(2L, "1000000", "5000000"));
    }

    @Test
    void rangosContiguosNoSeSuperponen() {
        var r = service.crear(req("5000000", null)); // [5.000.000, sin tope)
        assertEquals(99L, r.id());
        assertEquals("APROBADOR", r.rolAprobador());
        verify(auditoria).exito(eq(AccionAuditoria.POLITICA_CREAR), eq(EntidadAuditada.POLITICA_APROBACION),
                eq(99L), anyString());
    }

    @Test
    void rangoQueSeCruzaConOtroSeRechaza() {
        var ex = assertThrows(NegocioException.class, () -> service.crear(req("4000000", "6000000")));
        assertEquals("RANGO_SUPERPUESTO", ex.getCodigo());
        assertTrue(ex.getMessage().contains("política 2"));
        verifyNoInteractions(auditoria);
    }

    @Test
    void rangoSinTopeSeCruzaConTodoLoQueEstaDespues() {
        assertThrows(NegocioException.class, () -> service.crear(req("500000", null)));
    }

    @Test
    void otroTipoNoCuentaParaLaSuperposicion() {
        when(repo.findByEmpresaIdAndTipoAndActivoTrue(EmpresaUnica.ID, TipoPolitica.ADJUDICACION))
                .thenReturn(List.of());
        var r = service.crear(new PoliticaAprobacionRequest(TipoPolitica.ADJUDICACION, "aprobador",
                new BigDecimal("0"), new BigDecimal("1000000")));
        assertEquals(TipoPolitica.ADJUDICACION, r.tipo());
    }

    @Test
    void editarNoChocaConsigoMisma() {
        PoliticaAprobacion p1 = activasSolicitud.get(0);
        when(repo.findWithRolById(1L)).thenReturn(Optional.of(p1));

        var r = service.actualizar(1L, req("0", "900000")); // achicar su propio rango
        assertEquals(new BigDecimal("900000.00"), r.montoHasta());
    }

    @Test
    void reactivarRevisaLaSuperposicion() {
        PoliticaAprobacion inactiva = politica(3L, "200000", "300000");
        inactiva.setActivo(false);
        when(repo.findWithRolById(3L)).thenReturn(Optional.of(inactiva));

        assertEquals("RANGO_SUPERPUESTO",
                assertThrows(NegocioException.class, () -> service.cambiarEstado(3L, true)).getCodigo());
        assertFalse(service.cambiarEstado(3L, false).activo()); // desactivar siempre se puede
    }

    @Test
    void aplicableUsaRangoSemiabierto() {
        assertEquals(1L, service.aplicable(TipoPolitica.SOLICITUD, new BigDecimal("0")).orElseThrow().getId());
        assertEquals(1L, service.aplicable(TipoPolitica.SOLICITUD, new BigDecimal("999999.99")).orElseThrow().getId());
        assertEquals(2L, service.aplicable(TipoPolitica.SOLICITUD, new BigDecimal("1000000")).orElseThrow().getId());
        assertTrue(service.aplicable(TipoPolitica.SOLICITUD, new BigDecimal("5000000")).isEmpty());

        var sinPolitica = service.consultarAplicable(TipoPolitica.SOLICITUD, new BigDecimal("7000000"));
        assertFalse(sinPolitica.requiereAprobacion());
    }

    @Test
    void reglasDeRangoDeLaEntidad() {
        PoliticaAprobacion p = politica(1L, "100", "200");
        assertTrue(p.cubre(new BigDecimal("100")));
        assertFalse(p.cubre(new BigDecimal("200")));
        assertTrue(p.seSuperponeCon(new BigDecimal("150"), null));
        assertFalse(p.seSuperponeCon(new BigDecimal("200"), new BigDecimal("300")));
        assertFalse(p.seSuperponeCon(new BigDecimal("0"), new BigDecimal("100")));
    }

    private PoliticaAprobacionRequest req(String desde, String hasta) {
        return new PoliticaAprobacionRequest(TipoPolitica.SOLICITUD, "aprobador", new BigDecimal(desde),
                hasta == null ? null : new BigDecimal(hasta));
    }

    private PoliticaAprobacion politica(Long id, String desde, String hasta) {
        PoliticaAprobacion p = new PoliticaAprobacion();
        p.setId(id);
        p.setEmpresaId(EmpresaUnica.ID);
        p.setTipo(TipoPolitica.SOLICITUD);
        p.setRolAprobador(aprobador);
        p.setMontoDesde(new BigDecimal(desde).setScale(2));
        p.setMontoHasta(hasta == null ? null : new BigDecimal(hasta).setScale(2));
        return p;
    }
}
