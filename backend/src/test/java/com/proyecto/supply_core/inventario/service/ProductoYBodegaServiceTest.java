package com.proyecto.supply_core.inventario.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.catalogo.entity.CategoriaProducto;
import com.proyecto.supply_core.catalogo.entity.UnidadMedida;
import com.proyecto.supply_core.catalogo.enums.Magnitud;
import com.proyecto.supply_core.catalogo.repository.CategoriaProductoRepository;
import com.proyecto.supply_core.catalogo.repository.UnidadMedidaRepository;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.empresa.repository.SedeRepository;
import com.proyecto.supply_core.empresa.service.SedeService;
import com.proyecto.supply_core.inventario.dto.ProductoRequest;
import com.proyecto.supply_core.inventario.dto.ProductoUpdateRequest;
import com.proyecto.supply_core.inventario.entity.Bodega;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.BodegaRepository;
import com.proyecto.supply_core.inventario.repository.MovimientoInventarioRepository;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.inventario.repository.StockProductoBodegaRepository;
import com.proyecto.supply_core.empresa.entity.Sede;

class ProductoYBodegaServiceTest {

    private final ProductoRepository productos = mock(ProductoRepository.class);
    private final CategoriaProductoRepository categorias = mock(CategoriaProductoRepository.class);
    private final UnidadMedidaRepository unidades = mock(UnidadMedidaRepository.class);
    private final StockProductoBodegaRepository stock = mock(StockProductoBodegaRepository.class);
    private final MovimientoInventarioRepository movimientos = mock(MovimientoInventarioRepository.class);
    private final BodegaRepository bodegas = mock(BodegaRepository.class);
    private final SedeRepository sedes = mock(SedeRepository.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final ProductoService productoService =
            new ProductoService(productos, categorias, unidades, stock, movimientos, auditoria);
    private final BodegaService bodegaService = new BodegaService(bodegas, sedes, stock, auditoria);

    private Producto producto;
    private UnidadMedida und;
    private UnidadMedida doc;

    @BeforeEach
    void setUp() {
        CategoriaProducto cat = new CategoriaProducto();
        cat.setId(1L);
        cat.setNombre("Papelería");
        und = unidad(1L, "UND");
        doc = unidad(2L, "DOC");
        when(categorias.findById(1L)).thenReturn(Optional.of(cat));
        when(unidades.findById(1L)).thenReturn(Optional.of(und));
        when(unidades.findById(2L)).thenReturn(Optional.of(doc));

        producto = new Producto();
        producto.setId(10L);
        producto.setCodigo("PAP-001");
        producto.setNombre("Resma");
        producto.setCategoria(cat);
        producto.setUnidadMedida(und);
        when(productos.findWithRelacionesById(10L)).thenReturn(Optional.of(producto));
        when(stock.totalDeProducto(10L)).thenReturn(new BigDecimal("3.0000"));
    }

    @Test
    void creaProductoConCodigoEnMayusculasYStockCero() {
        when(productos.save(any())).thenAnswer(inv -> {
            Producto p = inv.getArgument(0);
            p.setId(11L);
            return p;
        });
        var r = productoService.crear(new ProductoRequest(" pap-002 ", "Lapicero", null, 1L, 1L,
                new BigDecimal("10"), null, null));
        assertEquals("PAP-002", r.codigo());
        assertEquals(new BigDecimal("0.0000"), r.stockTotal());
        assertTrue(r.bajoStockMinimo()); // 0 < 10
    }

    @Test
    void noCambiaLaUnidadSiYaTieneMovimientos() {
        when(movimientos.existsByProductoId(10L)).thenReturn(true);
        var ex = assertThrows(NegocioException.class, () -> productoService.actualizar(10L,
                new ProductoUpdateRequest("PAP-001", "Resma", null, 1L, 2L, BigDecimal.ZERO, null, null)));
        assertEquals("UNIDAD_EN_USO", ex.getCodigo());

        // misma unidad: sí deja editar
        var r = productoService.actualizar(10L,
                new ProductoUpdateRequest("PAP-001", "Resma carta", null, 1L, 1L, new BigDecimal("2"), null, null));
        assertEquals("Resma carta", r.nombre());
        assertEquals(new BigDecimal("3.0000"), r.stockTotal());
    }

    @Test
    void sinMovimientosSiPuedeCambiarLaUnidad() {
        when(movimientos.existsByProductoId(10L)).thenReturn(false);
        var r = productoService.actualizar(10L,
                new ProductoUpdateRequest("PAP-001", "Resma", null, 1L, 2L, BigDecimal.ZERO, null, null));
        assertEquals("DOC", r.unidadMedida().codigo());
    }

    @Test
    void codigoDeOtroProductoSeRechazaAlEditar() {
        when(productos.existsByCodigoIgnoreCaseAndIdNot("PAP-009", 10L)).thenReturn(true);
        assertEquals("CODIGO_DUPLICADO", assertThrows(NegocioException.class, () -> productoService.actualizar(10L,
                new ProductoUpdateRequest("pap-009", "Resma", null, 1L, 1L, BigDecimal.ZERO, null, null))).getCodigo());
        verifyNoInteractions(auditoria);
    }

    @Test
    void listadoUsaUnaSolaConsultaDeTotales() {
        when(productos.bajoStockMinimo()).thenReturn(List.of(producto));
        when(stock.totalesDe(anyCollection())).thenReturn(List.of(total(10L, "3.0000")));
        assertEquals(new BigDecimal("3.0000"), productoService.alertasStockMinimo().get(0).stockTotal());
    }

    @Test
    void bodegaConStockNoSeDesactiva() {
        Bodega b = new Bodega();
        b.setId(1L);
        b.setNombre("Principal");
        when(bodegas.findWithSedeById(1L)).thenReturn(Optional.of(b));
        when(stock.existsByBodegaIdAndCantidadGreaterThan(1L, BigDecimal.ZERO)).thenReturn(true);

        assertEquals("BODEGA_CON_STOCK", assertThrows(NegocioException.class,
                () -> bodegaService.cambiarEstado(1L, false)).getCodigo());
        assertTrue(bodegaService.cambiarEstado(1L, true).activo()); // activar siempre se puede
    }

    @Test
    void sedeConBodegasActivasNoSeDesactiva() {
        SedeService sedeService = new SedeService(sedes, bodegas, auditoria);
        Sede s = new Sede();
        s.setId(3L);
        when(sedes.findById(3L)).thenReturn(Optional.of(s));
        when(bodegas.existsBySedeIdAndActivoTrue(3L)).thenReturn(true);

        assertEquals("SEDE_CON_BODEGAS", assertThrows(NegocioException.class,
                () -> sedeService.cambiarEstado(3L, false)).getCodigo());
    }

    private static UnidadMedida unidad(Long id, String codigo) {
        UnidadMedida u = new UnidadMedida();
        u.setId(id);
        u.setCodigo(codigo);
        u.setNombre(codigo);
        u.setMagnitud(Magnitud.CANTIDAD);
        u.setFactorConversionBase(BigDecimal.ONE);
        return u;
    }

    private static StockProductoBodegaRepository.TotalPorProducto total(Long id, String valor) {
        return new StockProductoBodegaRepository.TotalPorProducto() {
            @Override
            public Long getProductoId() {
                return id;
            }

            @Override
            public BigDecimal getTotal() {
                return new BigDecimal(valor);
            }
        };
    }
}
