package com.proyecto.supply_core.compra.analisis.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.proyecto.supply_core.common.tenant.EmpresaDePrueba;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.compra.analisis.entity.ConfigPesos;
import com.proyecto.supply_core.compra.analisis.entity.CriterioEvaluacion;
import com.proyecto.supply_core.compra.analisis.repository.ConfigPesosRepository;
import com.proyecto.supply_core.compra.analisis.repository.CriterioRepository;

@ExtendWith(EmpresaDePrueba.class)
class PesosServiceTest {

    private final ConfigPesosRepository configs = mock(ConfigPesosRepository.class);
    private final CriterioRepository criterios = mock(CriterioRepository.class);
    private final PesosService service = new PesosService(configs, criterios, mock(AuditoriaService.class));
    private ConfigPesos vigente;

    @BeforeEach
    void setUp() {
        List<CriterioEvaluacion> lista = List.of("CALIDAD", "ENTREGA", "GARANTIA", "HISTORIAL", "PRECIO", "RIESGO")
                .stream().map(PesosServiceTest::criterio).toList();
        when(criterios.findAllByOrderByCodigoAsc()).thenReturn(lista);
        vigente = new ConfigPesos();
        vigente.setId(1L);
        when(configs.findFirstByEmpresaIdAndActivoTrueOrderByVigenteDesdeDesc(any())).thenReturn(Optional.of(vigente));
        when(configs.save(any())).thenAnswer(i -> {
            ConfigPesos c = i.getArgument(0);
            c.setId(2L);
            return c;
        });
    }

    @Test
    void nuevaVersionCierraLaAnterior() {
        var r = service.cambiar(Map.of("precio", bd("40"), "CALIDAD", bd("20"), "ENTREGA", bd("15"),
                "GARANTIA", bd("5"), "HISTORIAL", bd("15"), "RIESGO", bd("5")));

        assertEquals(2L, r.id());
        assertEquals("PRECIO", r.pesos().get(0).codigo());
        assertFalse(vigente.isActivo());
        assertNotNull(vigente.getVigenteHasta());
    }

    @Test
    void debenEstarTodosYSumar100() {
        assertEquals("PESOS_NO_SUMAN_100", assertThrows(NegocioException.class, () -> service.cambiar(Map.of(
                "PRECIO", bd("50"), "CALIDAD", bd("20"), "ENTREGA", bd("15"), "GARANTIA", bd("10"),
                "HISTORIAL", bd("15"), "RIESGO", bd("5")))).getCodigo());
        assertEquals("PESOS_INCOMPLETOS", assertThrows(NegocioException.class,
                () -> service.cambiar(Map.of("PRECIO", bd("100")))).getCodigo());
        verify(configs, never()).save(any());
    }

    private static CriterioEvaluacion criterio(String codigo) {
        CriterioEvaluacion c = mock(CriterioEvaluacion.class);
        when(c.getCodigo()).thenReturn(codigo);
        when(c.getNombre()).thenReturn(codigo.toLowerCase());
        return c;
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
