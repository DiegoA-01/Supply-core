package com.proyecto.supply_core.venta.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.venta.dto.HistorialPrecio;
import com.proyecto.supply_core.venta.dto.ListaPrecioRequest;
import com.proyecto.supply_core.venta.dto.ListaPrecioResponse;
import com.proyecto.supply_core.venta.dto.PrecioFila;
import com.proyecto.supply_core.venta.dto.PrecioRequest;
import com.proyecto.supply_core.venta.dto.PreciosResponse;
import com.proyecto.supply_core.venta.dto.ProductoVentaRequest;
import com.proyecto.supply_core.venta.dto.TarifaImpuestoRequest;
import com.proyecto.supply_core.venta.dto.TarifaImpuestoResponse;
import com.proyecto.supply_core.venta.service.PreciosService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * Precios de venta y margen (fase F1 de Ventas). Lo ven el dueño, el jefe de compras y auditoría (los costos son
 * información sensible); fija precios el dueño, o el jefe de compras si se lo delegó (lo valida el servicio).
 */
@RequiereModulo(Modulos.VENTAS)
@RestController
@RequestMapping("/api/ventas")
@UsuarioInterno
@RequiereRol({ Roles.ADMIN_SISTEMA, Roles.ADMIN_COMPRAS, Roles.AUDITOR })
public class PreciosController {

    private final PreciosService preciosService;

    /**
     * @param preciosService lógica de precios
     */
    public PreciosController(PreciosService preciosService) {
        this.preciosService = preciosService;
    }

    /**
     * @return impuestos de venta
     */
    @GetMapping("/impuestos")
    public List<TarifaImpuestoResponse> impuestos() {
        return preciosService.tarifas();
    }

    /**
     * @param request impuesto nuevo
     * @return impuesto creado (HTTP 201)
     */
    @PostMapping("/impuestos")
    @ResponseStatus(HttpStatus.CREATED)
    public TarifaImpuestoResponse crearImpuesto(@Valid @RequestBody TarifaImpuestoRequest request) {
        return preciosService.crearTarifa(request);
    }

    /**
     * @param id     impuesto
     * @param activo nuevo estado
     * @return impuesto
     */
    @PatchMapping("/impuestos/{id}")
    public TarifaImpuestoResponse estadoImpuesto(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @RequestParam boolean activo) {
        return preciosService.cambiarEstadoTarifa(id, activo);
    }

    /**
     * @return listas de precios
     */
    @GetMapping("/listas")
    public List<ListaPrecioResponse> listas() {
        return preciosService.listas();
    }

    /**
     * @param request nombre
     * @return lista creada (HTTP 201)
     */
    @PostMapping("/listas")
    @ResponseStatus(HttpStatus.CREATED)
    public ListaPrecioResponse crearLista(@Valid @RequestBody ListaPrecioRequest request) {
        return preciosService.crearLista(request);
    }

    /**
     * @param id      lista
     * @param request nombre y estado
     * @return lista actualizada
     */
    @PutMapping("/listas/{id}")
    public ListaPrecioResponse editarLista(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody ListaPrecioRequest request) {
        return preciosService.editarLista(id, request);
    }

    /**
     * @param listaId lista (vacío = General)
     * @return precios con margen y resumen
     */
    @GetMapping("/precios")
    public PreciosResponse precios(
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long listaId) {
        return preciosService.precios(listaId);
    }

    /**
     * @param request lista, producto, precio, mínimo y motivo
     * @return el producto con su nuevo margen
     */
    @PutMapping("/precios")
    public PrecioFila fijarPrecio(@Valid @RequestBody PrecioRequest request) {
        return preciosService.fijarPrecio(request);
    }

    /**
     * @param listaId    lista
     * @param productoId producto
     * @return historial de precios
     */
    @GetMapping("/precios/historial")
    public List<HistorialPrecio> historial(
            @RequestParam @Positive(message = "debe ser un id válido") Long listaId,
            @RequestParam @Positive(message = "debe ser un id válido") Long productoId) {
        return preciosService.historial(listaId, productoId);
    }

    /**
     * @param productoId producto
     * @param request    impuesto, vendible, si el precio incluye impuesto y código de barras
     * @return el producto en la lista General
     */
    @PutMapping("/productos/{productoId}")
    public PrecioFila configurarProducto(
            @PathVariable @Positive(message = "debe ser un id válido") Long productoId,
            @Valid @RequestBody ProductoVentaRequest request) {
        return preciosService.configurarProducto(productoId, request);
    }
}
