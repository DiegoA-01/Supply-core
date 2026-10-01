package com.proyecto.supply_core.catalogo.controller;

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

import com.proyecto.supply_core.catalogo.dto.CategoriaRequest;
import com.proyecto.supply_core.catalogo.dto.CategoriaResponse;
import com.proyecto.supply_core.catalogo.dto.CategoriaUpdateRequest;
import com.proyecto.supply_core.catalogo.service.CategoriaService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/** Categorías de producto: cualquier usuario interno consulta; ADMIN_COMPRAS y ADMIN_SISTEMA administran. */
@RestController
@RequestMapping("/api/categorias")
@UsuarioInterno
public class CategoriaController {

    private final CategoriaService categoriaService;

    /**
     * @param categoriaService lógica de categorías
     */
    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    /**
     * Lista las categorías ({@code ?activo=true} para los selectores).
     *
     * @param activo filtro opcional por estado
     * @return categorías por nombre
     */
    @GetMapping
    public List<CategoriaResponse> listar(@RequestParam(required = false) Boolean activo) {
        return categoriaService.listar(activo);
    }

    /**
     * Detalle de una categoría.
     *
     * @param id id positivo
     * @return categoría
     */
    @GetMapping("/{id}")
    public CategoriaResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return categoriaService.obtener(id);
    }

    /**
     * Crea una categoría.
     *
     * @param request nombre único
     * @return categoría creada (HTTP 201)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol({ Roles.ADMIN_COMPRAS, Roles.ADMIN_SISTEMA })
    public CategoriaResponse crear(@Valid @RequestBody CategoriaRequest request) {
        return categoriaService.crear(request);
    }

    /**
     * Cambia el nombre de una categoría.
     *
     * @param id      id positivo
     * @param request nombre nuevo
     * @return categoría actualizada
     */
    @PutMapping("/{id}")
    @RequiereRol({ Roles.ADMIN_COMPRAS, Roles.ADMIN_SISTEMA })
    public CategoriaResponse actualizar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody CategoriaUpdateRequest request) {
        return categoriaService.actualizar(id, request);
    }

    /**
     * Activa una categoría.
     *
     * @param id id positivo
     * @return categoría activa
     */
    @PatchMapping("/{id}/activar")
    @RequiereRol({ Roles.ADMIN_COMPRAS, Roles.ADMIN_SISTEMA })
    public CategoriaResponse activar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return categoriaService.cambiarEstado(id, true);
    }

    /**
     * Desactiva una categoría.
     *
     * @param id id positivo
     * @return categoría inactiva
     */
    @PatchMapping("/{id}/desactivar")
    @RequiereRol({ Roles.ADMIN_COMPRAS, Roles.ADMIN_SISTEMA })
    public CategoriaResponse desactivar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return categoriaService.cambiarEstado(id, false);
    }
}
