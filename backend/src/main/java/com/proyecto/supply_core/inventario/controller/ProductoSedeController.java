package com.proyecto.supply_core.inventario.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.inventario.dto.ProductoSedeRequest;
import com.proyecto.supply_core.inventario.dto.ProductoSedeResponse;
import com.proyecto.supply_core.inventario.service.ProductoSedeService;
import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * Un producto en cada sede: stock y mínimo/máximo propios. Lo leen los usuarios internos; los límites los fija el
 * jefe de compras.
 */
@RequiereModulo(Modulos.INVENTARIO)
@RestController
@RequestMapping("/api/productos/{productoId}/sedes")
@UsuarioInterno
public class ProductoSedeController {

    private final ProductoSedeService productoSedeService;

    /**
     * @param productoSedeService límites por sede
     */
    public ProductoSedeController(ProductoSedeService productoSedeService) {
        this.productoSedeService = productoSedeService;
    }

    /**
     * El producto en cada sede activa.
     *
     * @param productoId producto
     * @return una fila por sede
     */
    @GetMapping
    public List<ProductoSedeResponse> listar(
            @PathVariable @Positive(message = "debe ser un id válido") Long productoId) {
        return productoSedeService.listar(productoId);
    }

    /**
     * Fija el mínimo y el máximo del producto en una sede.
     *
     * @param productoId producto
     * @param sedeId     sede
     * @param request    límites
     * @return el producto en esa sede
     */
    @PutMapping("/{sedeId}")
    @RequiereRol(Roles.ADMIN_COMPRAS)
    public ProductoSedeResponse guardar(@PathVariable @Positive(message = "debe ser un id válido") Long productoId,
            @PathVariable @Positive(message = "debe ser un id válido") Long sedeId,
            @Valid @RequestBody ProductoSedeRequest request) {
        return productoSedeService.guardar(productoId, sedeId, request);
    }
}
