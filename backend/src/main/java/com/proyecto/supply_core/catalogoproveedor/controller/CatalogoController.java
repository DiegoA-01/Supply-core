package com.proyecto.supply_core.catalogoproveedor.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.catalogoproveedor.dto.CatalogoItemResponse;
import com.proyecto.supply_core.catalogoproveedor.dto.EquivalenciaRequest;
import com.proyecto.supply_core.catalogoproveedor.dto.Oportunidad;
import com.proyecto.supply_core.catalogoproveedor.enums.EstadoEquivalencia;
import com.proyecto.supply_core.catalogoproveedor.service.CatalogoService;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * Catálogos de proveedores para compras: catálogo completo, equivalencias y oportunidades. Lo ven compras, el dueño
 * y auditoría; confirman equivalencias el jefe de compras y el comprador.
 */
@RequiereModulo(Modulos.PROVEEDORES)
@RestController
@RequestMapping("/api/catalogos")
@UsuarioInterno
@RequiereRol({ Roles.ADMIN_COMPRAS, Roles.COMPRADOR, Roles.AUDITOR, Roles.ADMIN_SISTEMA })
public class CatalogoController {

    private final CatalogoService catalogoService;

    /**
     * @param catalogoService lógica de catálogos
     */
    public CatalogoController(CatalogoService catalogoService) {
        this.catalogoService = catalogoService;
    }

    /**
     * @param proveedorId proveedor, opcional
     * @param estado      estado de la equivalencia, opcional
     * @param texto       búsqueda, opcional
     * @param pageable    página (por defecto por descripción)
     * @return página de artículos
     */
    @GetMapping
    public PageResponse<CatalogoItemResponse> buscar(
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long proveedorId,
            @RequestParam(required = false) EstadoEquivalencia estado,
            @RequestParam(required = false) String texto,
            @PageableDefault(size = 30, sort = "descripcion", direction = Sort.Direction.ASC) Pageable pageable) {
        return catalogoService.buscar(proveedorId, estado, texto, pageable);
    }

    /**
     * @param id      artículo
     * @param request confirmar con producto o descartar
     * @return artículo actualizado
     */
    @PatchMapping("/{id}")
    @RequiereRol({ Roles.ADMIN_COMPRAS, Roles.COMPRADOR })
    public CatalogoItemResponse decidir(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody EquivalenciaRequest request) {
        return catalogoService.decidir(id, request);
    }

    /**
     * @return oportunidades de compra
     */
    @GetMapping("/oportunidades")
    public List<Oportunidad> oportunidades() {
        return catalogoService.oportunidades();
    }

    /**
     * @param productoId producto
     * @return ofertas confirmadas de ese producto
     */
    @GetMapping("/producto/{productoId}")
    public List<CatalogoItemResponse> ofertas(
            @PathVariable @Positive(message = "debe ser un id válido") Long productoId) {
        return catalogoService.ofertasDe(productoId);
    }
}
