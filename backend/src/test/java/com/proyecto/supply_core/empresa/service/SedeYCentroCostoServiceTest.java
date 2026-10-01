package com.proyecto.supply_core.empresa.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.util.EmpresaUnica;
import com.proyecto.supply_core.empresa.dto.CentroCostoRequest;
import com.proyecto.supply_core.empresa.dto.CentroCostoUpdateRequest;
import com.proyecto.supply_core.empresa.dto.SedeRequest;
import com.proyecto.supply_core.empresa.entity.CentroCosto;
import com.proyecto.supply_core.empresa.entity.Sede;
import com.proyecto.supply_core.empresa.repository.CentroCostoRepository;
import com.proyecto.supply_core.empresa.repository.SedeRepository;
import com.proyecto.supply_core.inventario.repository.BodegaRepository;

class SedeYCentroCostoServiceTest {

    private final SedeRepository sedes = mock(SedeRepository.class);
    private final CentroCostoRepository centros = mock(CentroCostoRepository.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final SedeService sedeService = new SedeService(sedes, mock(BodegaRepository.class), auditoria);
    private final CentroCostoService centroService = new CentroCostoService(centros, auditoria);

    @Test
    void sedeConNombreRepetidoSeRechazaEnAltaYEdicion() {
        when(sedes.existsByEmpresaIdAndNombreIgnoreCase(EmpresaUnica.ID, "Principal")).thenReturn(true);
        assertEquals("NOMBRE_DUPLICADO", assertThrows(NegocioException.class,
                () -> sedeService.crear(new SedeRequest(" Principal ", null, null, null))).getCodigo());

        Sede s = new Sede();
        s.setId(2L);
        s.setNombre("Norte");
        when(sedes.findById(2L)).thenReturn(Optional.of(s));
        when(sedes.existsByEmpresaIdAndNombreIgnoreCaseAndIdNot(EmpresaUnica.ID, "Principal", 2L)).thenReturn(true);
        assertThrows(NegocioException.class,
                () -> sedeService.actualizar(2L, new SedeRequest("Principal", null, null, null)));
        verifyNoInteractions(auditoria);
    }

    @Test
    void sedeSeGuardaLimpiaYSeDesactiva() {
        when(sedes.save(any())).thenAnswer(inv -> {
            Sede s = inv.getArgument(0);
            s.setId(1L);
            return s;
        });
        var r = sedeService.crear(new SedeRequest(" Bodega Norte ", "  ", new BigDecimal("4.65"), new BigDecimal("-74.05")));
        assertEquals("Bodega Norte", r.nombre());
        assertEquals(null, r.direccion());

        Sede s = new Sede();
        s.setId(1L);
        when(sedes.findById(1L)).thenReturn(Optional.of(s));
        assertFalse(sedeService.cambiarEstado(1L, false).activo());
    }

    @Test
    void centroCostoGuardaElCodigoEnMayusculas() {
        when(centros.save(any())).thenAnswer(inv -> {
            CentroCosto c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });
        assertEquals("ADM-01", centroService.crear(new CentroCostoRequest(" adm-01 ", "Administración")).codigo());
    }

    @Test
    void editarCentroConCodigoDeOtroSeRechaza() {
        CentroCosto c = new CentroCosto();
        c.setId(1L);
        c.setCodigo("ADM-01");
        when(centros.findById(1L)).thenReturn(Optional.of(c));
        when(centros.existsByCodigoIgnoreCaseAndIdNot("VEN-01", 1L)).thenReturn(true);

        assertEquals("CODIGO_DUPLICADO", assertThrows(NegocioException.class,
                () -> centroService.actualizar(1L, new CentroCostoUpdateRequest("ven-01", "Ventas"))).getCodigo());
    }
}
