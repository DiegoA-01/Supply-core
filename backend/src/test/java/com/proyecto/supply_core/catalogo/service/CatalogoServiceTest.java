package com.proyecto.supply_core.catalogo.service;

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
import com.proyecto.supply_core.catalogo.dto.CategoriaRequest;
import com.proyecto.supply_core.catalogo.dto.CategoriaUpdateRequest;
import com.proyecto.supply_core.catalogo.dto.UnidadMedidaRequest;
import com.proyecto.supply_core.catalogo.dto.UnidadMedidaUpdateRequest;
import com.proyecto.supply_core.catalogo.entity.CategoriaProducto;
import com.proyecto.supply_core.catalogo.entity.UnidadMedida;
import com.proyecto.supply_core.catalogo.enums.Magnitud;
import com.proyecto.supply_core.catalogo.repository.CategoriaProductoRepository;
import com.proyecto.supply_core.catalogo.repository.UnidadMedidaRepository;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;

class CatalogoServiceTest {

    private final CategoriaProductoRepository categorias = mock(CategoriaProductoRepository.class);
    private final UnidadMedidaRepository unidades = mock(UnidadMedidaRepository.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final CategoriaService categoriaService = new CategoriaService(categorias, auditoria);
    private final UnidadMedidaService unidadService = new UnidadMedidaService(unidades, auditoria);

    private static final UnidadMedida UND = unidad(1L, "UND", Magnitud.CANTIDAD, "1");
    private static final UnidadMedida DOC = unidad(2L, "DOC", Magnitud.CANTIDAD, "12");
    private static final UnidadMedida CAJA24 = unidad(3L, "CAJA24", Magnitud.CANTIDAD, "24");
    private static final UnidadMedida KG = unidad(4L, "KG", Magnitud.MASA, "1");
    private static final UnidadMedida G = unidad(5L, "G", Magnitud.MASA, "0.001");
    private static final UnidadMedida LT = unidad(6L, "LT", Magnitud.VOLUMEN, "1");

    // ------------------------------------------------------------- conversión

    @Test
    void convierteDentroDeLaMismaMagnitud() {
        assertEquals(new BigDecimal("24.0000"), UnidadMedidaService.convertir(new BigDecimal("2"), DOC, UND));
        assertEquals(new BigDecimal("2.0000"), UnidadMedidaService.convertir(new BigDecimal("24"), UND, DOC));
        assertEquals(new BigDecimal("4.0000"), UnidadMedidaService.convertir(new BigDecimal("2"), CAJA24, DOC));
        assertEquals(new BigDecimal("1.2500"), UnidadMedidaService.convertir(new BigDecimal("1250"), G, KG));
    }

    @Test
    void redondeaACuatroDecimales() {
        // 1 UND = 1/12 DOC = 0.08333... → 0.0833
        assertEquals(new BigDecimal("0.0833"), UnidadMedidaService.convertir(BigDecimal.ONE, UND, DOC));
    }

    @Test
    void noConvierteEntreMagnitudesDistintas() {
        var ex = assertThrows(NegocioException.class,
                () -> UnidadMedidaService.convertir(BigDecimal.ONE, KG, LT));
        assertEquals("UNIDADES_INCOMPATIBLES", ex.getCodigo());
    }

    @Test
    void convertirPorIdBuscaLasUnidades() {
        when(unidades.findById(2L)).thenReturn(Optional.of(DOC));
        when(unidades.findById(1L)).thenReturn(Optional.of(UND));
        var r = unidadService.consultarConversion(new BigDecimal("3"), 2L, 1L);
        assertEquals("DOC", r.unidadOrigen());
        assertEquals(new BigDecimal("36.0000"), r.resultado());

        assertThrows(NoEncontradoException.class, () -> unidadService.convertir(BigDecimal.ONE, 99L, 1L));
    }

    // ------------------------------------------------------------- administración

    @Test
    void unidadNuevaGuardaElCodigoEnMayusculas() {
        when(unidades.save(any())).thenAnswer(inv -> {
            UnidadMedida u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });
        var r = unidadService.crear(new UnidadMedidaRequest(" paq6 ", "Paquete x 6", Magnitud.CANTIDAD,
                new BigDecimal("6.000000")));
        assertEquals("PAQ6", r.codigo());
        assertEquals(new BigDecimal("6"), r.factorConversionBase());
    }

    @Test
    void editarUnidadSoloCambiaElNombre() {
        UnidadMedida doc = unidad(2L, "DOC", Magnitud.CANTIDAD, "12");
        when(unidades.findById(2L)).thenReturn(Optional.of(doc));
        var r = unidadService.actualizar(2L, new UnidadMedidaUpdateRequest("Docena (12 und)"));
        assertEquals("Docena (12 und)", r.nombre());
        assertEquals(new BigDecimal("12"), r.factorConversionBase());
        assertFalse(unidadService.cambiarEstado(2L, false).activo());
    }

    @Test
    void categoriaConNombreDeOtraSeRechazaAlEditar() {
        CategoriaProducto c = new CategoriaProducto();
        c.setId(1L);
        c.setNombre("Aseo");
        when(categorias.findById(1L)).thenReturn(Optional.of(c));
        when(categorias.existsByNombreIgnoreCaseAndIdNot("Tecnología", 1L)).thenReturn(true);

        assertEquals("NOMBRE_DUPLICADO", assertThrows(NegocioException.class,
                () -> categoriaService.actualizar(1L, new CategoriaUpdateRequest(" Tecnología "))).getCodigo());
        verifyNoInteractions(auditoria);
    }

    @Test
    void categoriaSeCreaSinEspaciosYSeDesactiva() {
        when(categorias.save(any())).thenAnswer(inv -> {
            CategoriaProducto c = inv.getArgument(0);
            c.setId(7L);
            return c;
        });
        assertEquals("Químicos", categoriaService.crear(new CategoriaRequest("  Químicos ")).nombre());

        CategoriaProducto c = new CategoriaProducto();
        c.setId(7L);
        when(categorias.findById(7L)).thenReturn(Optional.of(c));
        assertFalse(categoriaService.cambiarEstado(7L, false).activo());
    }

    private static UnidadMedida unidad(Long id, String codigo, Magnitud magnitud, String factor) {
        UnidadMedida u = new UnidadMedida();
        u.setId(id);
        u.setCodigo(codigo);
        u.setNombre(codigo);
        u.setMagnitud(magnitud);
        u.setFactorConversionBase(new BigDecimal(factor));
        return u;
    }
}
