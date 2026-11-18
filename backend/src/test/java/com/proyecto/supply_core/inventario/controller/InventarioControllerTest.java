package com.proyecto.supply_core.inventario.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.web.WebTestBase;
import com.proyecto.supply_core.inventario.dto.MovimientoResponse;
import com.proyecto.supply_core.inventario.dto.RegistroMovimientoResponse;
import com.proyecto.supply_core.inventario.enums.TipoMovimiento;
import com.proyecto.supply_core.inventario.service.BodegaService;
import com.proyecto.supply_core.inventario.service.MovimientoInventarioService;
import com.proyecto.supply_core.inventario.service.ProductoService;

@WebMvcTest({ InventarioController.class, ProductoController.class, BodegaController.class })
class InventarioControllerTest extends WebTestBase {

    @MockitoBean
    private MovimientoInventarioService movimientoService;
    @MockitoBean
    private ProductoService productoService;
    @MockitoBean
    private BodegaService bodegaService;

    @BeforeEach
    void catalogosActivos() {
        when(categoriaRepository.existsByIdAndActivoTrue(1L)).thenReturn(true);
        when(unidadMedidaRepository.existsByIdAndActivoTrue(1L)).thenReturn(true);
        when(sedeRepository.existsByIdAndActivoTrue(1L)).thenReturn(true);
    }

    // ------------------------------------------------------------- movimientos

    @Test
    void soloElAlmacenistaRegistraMovimientos() throws Exception {
        String salida = """
                {"productoId":10,"tipo":"SALIDA_CONSUMO","bodegaOrigenId":1,"cantidad":3}""";
        mvc.perform(post("/api/inventario/movimientos").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(salida))
                .andExpect(status().isForbidden());
        verifyNoInteractions(movimientoService);

        when(movimientoService.registrar(any())).thenReturn(new RegistroMovimientoResponse(
                new MovimientoResponse(100L, TipoMovimiento.SALIDA_CONSUMO, 10L, "PAP-001", "Resma", 1L, "Principal",
                        null, null, new BigDecimal("3"), new BigDecimal("7"), null, null, null, null, 7L, null),
                new BigDecimal("7"), false));
        mvc.perform(post("/api/inventario/movimientos").header(HttpHeaders.AUTHORIZATION, bearer("ALMACENISTA"))
                        .contentType(MediaType.APPLICATION_JSON).content(salida))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movimiento.stockResultante").value(7))
                .andExpect(jsonPath("$.bajoStockMinimo").value(false));
    }

    @Test
    void entradaPorCompraNoSePuedeRegistrarAMano() throws Exception {
        mvc.perform(post("/api/inventario/movimientos").header(HttpHeaders.AUTHORIZATION, bearer("ALMACENISTA"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"productoId":10,"tipo":"ENTRADA_COMPRA","bodegaDestinoId":1,"cantidad":5}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("tipo: ENTRADA_COMPRA solo se registra desde la recepción de mercancía")));
        verifyNoInteractions(movimientoService);
    }

    @Test
    void reglasPorTipoDeMovimiento() throws Exception {
        mvc.perform(post("/api/inventario/movimientos").header(HttpHeaders.AUTHORIZATION, bearer("ALMACENISTA"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"productoId":10,"tipo":"TRASLADO","bodegaOrigenId":1,"bodegaDestinoId":1,"cantidad":1}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("bodegaDestinoId: debe ser distinta a la bodega de origen")));
        mvc.perform(post("/api/inventario/movimientos").header(HttpHeaders.AUTHORIZATION, bearer("ALMACENISTA"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"productoId":10,"tipo":"AJUSTE_NEGATIVO","bodegaDestinoId":1,"cantidad":1,"motivo":"x"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItems(
                        "bodegaOrigenId: es obligatoria para AJUSTE_NEGATIVO",
                        "bodegaDestinoId: no aplica para AJUSTE_NEGATIVO",
                        "motivo: es obligatorio para AJUSTE_NEGATIVO (mínimo 5 caracteres)")));
        mvc.perform(post("/api/inventario/movimientos").header(HttpHeaders.AUTHORIZATION, bearer("ALMACENISTA"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"productoId":10,"tipo":"SALIDA_CONSUMO","bodegaOrigenId":1,"cantidad":0.00001}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("cantidad: máximo 14 enteros y 4 decimales")));
        verifyNoInteractions(movimientoService);
    }

    @Test
    void stockInsuficienteDevuelve409() throws Exception {
        when(movimientoService.registrar(any())).thenThrow(new NegocioException("STOCK_INSUFICIENTE",
                "Stock insuficiente de PAP-001 en Principal: disponible 2, solicitado 5"));

        mvc.perform(post("/api/inventario/movimientos").header(HttpHeaders.AUTHORIZATION, bearer("ALMACENISTA"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"productoId":10,"tipo":"SALIDA_CONSUMO","bodegaOrigenId":1,"cantidad":5}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("STOCK_INSUFICIENTE"));
    }

    @Test
    void elStockNoSeEditaPorApi() throws Exception {
        mvc.perform(put("/api/inventario/stock").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"productoId":10,"bodegaId":1,"cantidad":999}"""))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void kardexYStockParaInternosNoParaProveedores() throws Exception {
        when(movimientoService.existencias(any(), any())).thenReturn(List.of());
        mvc.perform(get("/api/inventario/stock?bodegaId=1").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/inventario/movimientos?tipo=TRASLADO&desde=2026-10-02T00:00:00&hasta=2026-10-01T00:00:00")
                        .header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("rangoValido: la fecha 'desde' no puede ser posterior a 'hasta'")));
        mvc.perform(get("/api/inventario/stock").header(HttpHeaders.AUTHORIZATION, bearerProveedor(4L)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------- productos y bodegas

    @Test
    void productoValidaCodigoCatalogosYRangoDeStock() throws Exception {
        when(productoRepository.existsByCodigoIgnoreCase("PAP-001")).thenReturn(true);

        mvc.perform(post("/api/productos").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"codigo":"pap-001","nombre":"Resma","categoriaId":2,"unidadMedidaId":9,
                                 "stockMinimo":10,"stockMaximo":5}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItems(
                        "codigo: ya está registrado por otro producto",
                        "categoriaId: no existe o está inactiva",
                        "unidadMedidaId: no existe o está inactiva",
                        "rangoStockValido: stockMaximo no puede ser menor que stockMinimo")));
        verifyNoInteractions(productoService);
    }

    @Test
    void soloAdminComprasAdministraProductosYAdminSistemaBodegas() throws Exception {
        mvc.perform(patch("/api/productos/10/desactivar").header(HttpHeaders.AUTHORIZATION, bearer("ALMACENISTA")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/bodegas").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"nombre":"Norte"}"""))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/bodegas").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"nombre":"Norte","sedeId":5}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("sedeId: no existe o está inactiva")));
        verifyNoInteractions(productoService, bodegaService);
    }
}
