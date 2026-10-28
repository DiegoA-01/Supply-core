package com.proyecto.supply_core.inventario.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.catalogo.entity.CategoriaProducto;
import com.proyecto.supply_core.catalogo.entity.UnidadMedida;
import com.proyecto.supply_core.catalogo.repository.CategoriaProductoRepository;
import com.proyecto.supply_core.catalogo.repository.UnidadMedidaRepository;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.MovimientoInventarioRepository;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;

class ProductoCargaServiceTest {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String[] ENCABEZADO = { "codigo", "nombre", "categoria", "unidad", "descripcion",
            "stock_minimo", "stock_maximo", "costo_referencia" };

    private final ProductoRepository productos = mock(ProductoRepository.class);
    private final CategoriaProductoRepository categorias = mock(CategoriaProductoRepository.class);
    private final UnidadMedidaRepository unidades = mock(UnidadMedidaRepository.class);
    private final MovimientoInventarioRepository movimientos = mock(MovimientoInventarioRepository.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final ProductoCargaService service = new ProductoCargaService(productos, categorias, unidades, movimientos,
            auditoria);

    private UnidadMedida und;
    private UnidadMedida kg;

    @BeforeEach
    void setUp() {
        und = unidad(1L, "UND", true);
        kg = unidad(2L, "KG", true);
        when(unidades.findByCodigoIgnoreCase("UND")).thenReturn(Optional.of(und));
        when(unidades.findByCodigoIgnoreCase("KG")).thenReturn(Optional.of(kg));
        when(unidades.findByCodigoIgnoreCase("VIEJA")).thenReturn(Optional.of(unidad(3L, "VIEJA", false)));
        when(categorias.save(any())).thenAnswer(i -> {
            CategoriaProducto c = i.getArgument(0);
            c.setId(50L);
            return c;
        });
    }

    @Test
    void creaProductosNuevosYLaCategoriaQueNoExisteUnaSolaVezYAudita() throws Exception {
        var r = service.cargar(excel("datos.xlsx", ENCABEZADO,
                new Object[] { " cab-1 ", "Cable THHN", "Eléctricos", "und", "Cobre", 10, 50, 1500 },
                new Object[] { "TUB-1", "Tubo EMT", "eléctricos", "UND", null, "5", "20", "320,5" }), false);

        assertTrue(r.aplicado());
        assertEquals(2, r.filas());
        assertEquals(2, r.creados());
        assertEquals(0, r.actualizados());
        assertEquals(1, r.categoriasCreadas());
        assertTrue(r.errores().isEmpty());
        ArgumentCaptor<CategoriaProducto> categoria = ArgumentCaptor.forClass(CategoriaProducto.class);
        verify(categorias, times(1)).save(categoria.capture());
        assertEquals("Eléctricos", categoria.getValue().getNombre());
        ArgumentCaptor<Producto> producto = ArgumentCaptor.forClass(Producto.class);
        verify(productos, times(2)).save(producto.capture());
        Producto cable = producto.getAllValues().get(0);
        assertEquals("CAB-1", cable.getCodigo());
        assertEquals("Cable THHN", cable.getNombre());
        assertEquals(new BigDecimal("10.0000"), cable.getStockMinimo());
        assertEquals(new BigDecimal("50.0000"), cable.getStockMaximo());
        assertEquals(new BigDecimal("1500.0000"), cable.getCostoReferencia());
        assertEquals(und, cable.getUnidadMedida());
        assertEquals(50L, cable.getCategoria().getId());
        assertEquals(new BigDecimal("320.5000"), producto.getAllValues().get(1).getCostoReferencia());
        assertNull(producto.getAllValues().get(1).getDescripcion());
        verify(auditoria).exito(eq(AccionAuditoria.PRODUCTO_CARGA_MASIVA), eq(EntidadAuditada.PRODUCTO), eq(null),
                anyString());
    }

    @Test
    void conSoloCodigoYCostoActualizaLosPreciosYDejaTodoLoDemasComoEstaba() throws Exception {
        Producto existente = producto(10L, "CAB-1", "Cable viejo", und);
        existente.setStockMinimo(new BigDecimal("7"));
        existente.setCostoReferencia(new BigDecimal("1000"));
        when(productos.findByCodigoIgnoreCase("CAB-1")).thenReturn(Optional.of(existente));

        var r = service.cargar(excel("precios.xlsx", new String[] { "Código", "Costo referencia" },
                new Object[] { "cab-1", 1750.5 }), false);

        assertTrue(r.aplicado());
        assertEquals(0, r.creados());
        assertEquals(1, r.actualizados());
        assertEquals(new BigDecimal("1750.5000"), existente.getCostoReferencia());
        assertEquals("Cable viejo", existente.getNombre());
        assertEquals(new BigDecimal("7"), existente.getStockMinimo());
        verify(categorias, never()).save(any());
        verify(productos).save(existente);
    }

    @Test
    void siHayAlgunErrorNoSeGuardaNadaYSeDicenLasFilas() throws Exception {
        Producto existente = producto(10L, "VIE-1", "Viejo", und);
        when(productos.findByCodigoIgnoreCase("VIE-1")).thenReturn(Optional.of(existente));
        when(movimientos.existsByProductoId(10L)).thenReturn(true);

        var r = service.cargar(excel("datos.xlsx", ENCABEZADO,
                new Object[] { "OK-1", "Bueno", "Cat", "UND", null, 1, 5, 10 },
                new Object[] { "NUE-1", null, null, "XX", null, null, null, null },
                new Object[] { "NUE-2", "Mínimo mayor", "Cat", "UND", null, 10, 5, null },
                new Object[] { "NUE-3", "Negativo", "Cat", "UND", null, -3, null, null },
                new Object[] { "OK-1", "Repetido", "Cat", "UND", null, null, null, null },
                new Object[] { "NUE-4", "Unidad vieja", "Cat", "VIEJA", null, null, null, null },
                new Object[] { "VIE-1", null, null, "KG", null, null, null, null },
                new Object[] { "NUE-5", "Texto", "Cat", "UND", null, "abc", null, null },
                new Object[] { null, "Sin código", "Cat", "UND", null, null, null, null }), false);

        assertFalse(r.aplicado());
        assertEquals(9, r.filas());
        assertEquals(1, r.errores().stream().filter(e -> e.fila() == 3).count());
        var porFila = r.errores().stream().collect(java.util.stream.Collectors.toMap(e -> e.fila(), e -> e.mensaje()));
        assertTrue(porFila.get(3).contains("la unidad XX no existe") && porFila.get(3).contains("falta el nombre")
                && porFila.get(3).contains("falta la categoría"));
        assertTrue(porFila.get(4).contains("máximo no puede ser menor"));
        assertTrue(porFila.get(5).contains("stock_minimo no puede ser negativo"));
        assertTrue(porFila.get(6).contains("ya salió antes"));
        assertTrue(porFila.get(7).contains("la unidad VIEJA no existe o está inactiva"));
        assertTrue(porFila.get(8).contains("movimientos de inventario"));
        assertTrue(porFila.get(9).contains("stock_minimo no es un número"));
        assertTrue(porFila.get(10).contains("falta el código"));
        assertFalse(porFila.containsKey(2)); // la primera fila estaba bien
        verify(productos, never()).save(any());
        verify(categorias, never()).save(any());
        verify(auditoria, never()).exito(any(), any(), any(), anyString());
    }

    @Test
    void soloValidarCuentaSinGuardarNiAuditar() throws Exception {
        var r = service.cargar(excel("datos.xlsx", ENCABEZADO,
                new Object[] { "CAB-1", "Cable", "Nueva", "UND", null, null, null, null }), true);

        assertTrue(r.soloValidar());
        assertFalse(r.aplicado());
        assertEquals(1, r.creados());
        assertEquals(1, r.categoriasCreadas());
        assertTrue(r.errores().isEmpty());
        verify(productos, never()).save(any());
        verify(categorias, never()).save(any());
        verify(auditoria, never()).exito(any(), any(), any(), anyString());
    }

    @Test
    void laCategoriaInactivaNoSeUsaYLaExistenteSiSeReutiliza() throws Exception {
        CategoriaProducto activa = new CategoriaProducto();
        activa.setId(5L);
        activa.setNombre("Plomería");
        CategoriaProducto inactiva = new CategoriaProducto();
        inactiva.setId(6L);
        inactiva.setNombre("Vieja");
        inactiva.setActivo(false);
        when(categorias.findByNombreIgnoreCase("plomería")).thenReturn(Optional.of(activa));
        when(categorias.findByNombreIgnoreCase("Vieja")).thenReturn(Optional.of(inactiva));

        var buena = service.cargar(excel("a.xlsx", ENCABEZADO,
                new Object[] { "P-1", "Llave", "plomería", "UND", null, null, null, null }), false);
        assertTrue(buena.aplicado());
        assertEquals(0, buena.categoriasCreadas());
        verify(categorias, never()).save(any());

        var mala = service.cargar(excel("b.xlsx", ENCABEZADO,
                new Object[] { "P-2", "Otra", "Vieja", "UND", null, null, null, null }), false);
        assertFalse(mala.aplicado());
        assertTrue(mala.errores().get(0).mensaje().contains("categoría Vieja está inactiva"));
    }

    @Test
    void losEncabezadosAceptanTildesMayusculasYEspacios() throws Exception {
        var r = service.cargar(excel("datos.xlsx", new String[] { "CÓDIGO", "Nombre", "Categoría", "Unidad",
                "Stock Mínimo", "Stock Máximo", "Costo Referencia" },
                new Object[] { "CAB-1", "Cable", "Cat", "UND", 2, 9, 100 }), false);

        assertTrue(r.aplicado());
        ArgumentCaptor<Producto> producto = ArgumentCaptor.forClass(Producto.class);
        verify(productos).save(producto.capture());
        assertEquals(new BigDecimal("2.0000"), producto.getValue().getStockMinimo());
        assertEquals(new BigDecimal("9.0000"), producto.getValue().getStockMaximo());
        assertEquals(new BigDecimal("100.0000"), producto.getValue().getCostoReferencia());
    }

    @Test
    void rechazaArchivosQueNoSirven() throws Exception {
        assertEquals("ARCHIVO_VACIO", codigo(new MockMultipartFile("archivo", "a.xlsx", XLSX, new byte[0])));
        assertEquals("ARCHIVO_INVALIDO", codigo(new MockMultipartFile("archivo", "a.csv", "text/csv", "a,b".getBytes())));
        assertEquals("ARCHIVO_INVALIDO", codigo(new MockMultipartFile("archivo", "roto.xlsx", XLSX, "no es excel".getBytes())));
        assertEquals("ARCHIVO_GRANDE", codigo(new MockMultipartFile("archivo", "grande.xlsx", XLSX,
                new byte[(int) ProductoCargaService.MAX_BYTES + 1])));
        assertEquals("ARCHIVO_INVALIDO", codigo(excel("sin-codigo.xlsx", new String[] { "nombre", "unidad" },
                new Object[] { "Cable", "UND" })));
        assertEquals("ARCHIVO_VACIO", assertThrows(NegocioException.class, () -> service.cargar(null, false)).getCodigo());
    }

    @Test
    void rechazaArchivosConDemasiadasFilas() throws Exception {
        Object[][] filas = new Object[ProductoCargaService.MAX_FILAS + 1][];
        for (int i = 0; i < filas.length; i++) {
            filas[i] = new Object[] { "P-" + i, "Producto " + i, "Cat", "UND", null, null, null, null };
        }

        assertEquals("ARCHIVO_GRANDE", codigo(excel("muchas.xlsx", ENCABEZADO, filas)));
    }

    @Test
    void laPlantillaTraeLosEncabezadosYUnaFilaDeEjemplo() throws Exception {
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(service.plantilla()))) {
            Sheet hoja = libro.getSheetAt(0);
            for (int i = 0; i < ENCABEZADO.length; i++) {
                assertEquals(ENCABEZADO[i], hoja.getRow(0).getCell(i).getStringCellValue());
            }
            assertEquals("CAB-012", hoja.getRow(1).getCell(0).getStringCellValue());
        }
        // la propia plantilla se puede subir sin errores
        var r = service.cargar(new MockMultipartFile("archivo", "plantilla.xlsx", XLSX, service.plantilla()), true);
        assertTrue(r.errores().isEmpty());
        assertEquals(1, r.creados());
    }

    // ----------------------------------------------------------------- apoyo

    private String codigo(MockMultipartFile archivo) {
        return assertThrows(NegocioException.class, () -> service.cargar(archivo, false)).getCodigo();
    }

    private static UnidadMedida unidad(Long id, String codigo, boolean activa) {
        UnidadMedida u = new UnidadMedida();
        u.setId(id);
        u.setCodigo(codigo);
        u.setActivo(activa);
        return u;
    }

    private static Producto producto(Long id, String codigo, String nombre, UnidadMedida unidad) {
        Producto p = new Producto();
        p.setId(id);
        p.setCodigo(codigo);
        p.setNombre(nombre);
        p.setUnidadMedida(unidad);
        return p;
    }

    /** Arma un .xlsx en memoria: la primera fila es el encabezado y las demás son datos. */
    private static MockMultipartFile excel(String nombre, String[] encabezado, Object[]... filas) throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("datos");
            Row cabecera = hoja.createRow(0);
            for (int j = 0; j < encabezado.length; j++) {
                cabecera.createCell(j).setCellValue(encabezado[j]);
            }
            for (int i = 0; i < filas.length; i++) {
                Row fila = hoja.createRow(i + 1);
                for (int j = 0; j < filas[i].length; j++) {
                    Object valor = filas[i][j];
                    if (valor instanceof Number n) {
                        fila.createCell(j).setCellValue(n.doubleValue());
                    } else if (valor != null) {
                        fila.createCell(j).setCellValue(valor.toString());
                    }
                }
            }
            libro.write(salida);
            return new MockMultipartFile("archivo", nombre, XLSX, salida.toByteArray());
        }
    }
}
