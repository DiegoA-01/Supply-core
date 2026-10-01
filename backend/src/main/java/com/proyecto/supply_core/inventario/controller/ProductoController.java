package com.proyecto.supply_core.inventario.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.inventario.dto.ProductoRequest;
import com.proyecto.supply_core.inventario.dto.ProductoResponse;
import com.proyecto.supply_core.inventario.dto.ProductoUpdateRequest;
import com.proyecto.supply_core.inventario.service.ProductoService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/** UC-02 Catálogo de productos: cualquier usuario interno consulta; ADMIN_COMPRAS administra. */
@RestController
@RequestMapping("/api/productos")
@UsuarioInterno
public class ProductoController {

    private final ProductoService productoService;

    /**
     * @param productoService lógica de productos
     */
    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    /**
     * Lista productos con su stock total.
     *
     * @param texto       coincidencia en código o nombre
     * @param categoriaId categoría
     * @param activo      estado
     * @param pageable    página (máximo 100) y orden (codigo, nombre)
     * @return página de productos
     */
    @GetMapping
    public PageResponse<ProductoResponse> buscar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long categoriaId,
            @RequestParam(required = false) Boolean activo,
            @PageableDefault(size = 20, sort = "codigo", direction = Sort.Direction.ASC) Pageable pageable) {
        return productoService.buscar(texto, categoriaId, activo, pageable);
    }

    /**
     * Productos activos por debajo de su stock mínimo (para generar solicitudes de compra).
     *
     * @return productos con alerta
     */
    @GetMapping("/alertas-stock")
    public List<ProductoResponse> alertasStock() {
        return productoService.alertasStockMinimo();
    }

    /**
     * Detalle de un producto.
     *
     * @param id id positivo
     * @return producto con stock total
     */
    @GetMapping("/{id}")
    public ProductoResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return productoService.obtener(id);
    }

    /**
     * Crea un producto.
     *
     * @param request datos validados
     * @return producto creado (HTTP 201)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol(Roles.ADMIN_COMPRAS)
    public ProductoResponse crear(@Valid @RequestBody ProductoRequest request) {
        return productoService.crear(request);
    }

    /**
     * Edita un producto.
     *
     * @param id      id positivo
     * @param request datos validados
     * @return producto actualizado
     */
    @PutMapping("/{id}")
    @RequiereRol(Roles.ADMIN_COMPRAS)
    public ProductoResponse actualizar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody ProductoUpdateRequest request) {
        return productoService.actualizar(id, request);
    }

    /**
     * Activa un producto.
     *
     * @param id id positivo
     * @return producto activo
     */
    @PatchMapping("/{id}/activar")
    @RequiereRol(Roles.ADMIN_COMPRAS)
    public ProductoResponse activar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return productoService.cambiarEstado(id, true);
    }

    /**
     * Desactiva un producto (no se usa en nuevas operaciones; conserva su historial).
     *
     * @param id id positivo
     * @return producto inactivo
     */
    @PatchMapping("/{id}/desactivar")
    @RequiereRol(Roles.ADMIN_COMPRAS)
    public ProductoResponse desactivar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return productoService.cambiarEstado(id, false);
    }
}
