package com.proyecto.supply_core.proveedor.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.catalogo.dto.CategoriaResponse;
import com.proyecto.supply_core.proveedor.dto.RegistroProveedorRequest;
import com.proyecto.supply_core.proveedor.dto.RegistroProveedorResponse;
import com.proyecto.supply_core.proveedor.service.ProveedorService;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;

/**
 * Autorregistro del proveedor. Rutas PÚBLICAS (sin token): están en la lista de excepciones del
 * {@code AuthFilter}. No dan acceso a nada: el proveedor queda PENDIENTE hasta que lo habiliten.
 * <p>La ruta lleva el código público de la empresa a la que el proveedor se registra
 * ({@code /api/portal/registro/{empresa}}); el {@code AuthFilter} lo resuelve y fija la empresa de la petición.</p>
 */
@RestController
@RequestMapping("/api/portal/registro/{empresa}")
@SecurityRequirements // Swagger no pide token aquí
public class RegistroProveedorController {

    private final ProveedorService proveedorService;

    /**
     * @param proveedorService lógica de proveedores
     */
    public RegistroProveedorController(ProveedorService proveedorService) {
        this.proveedorService = proveedorService;
    }

    /**
     * Registra el proveedor y su usuario del portal.
     *
     * @param empresa código público de la empresa (lo usa el filtro; aquí solo documenta la ruta)
     * @param request datos validados
     * @return id y estado PENDIENTE (HTTP 201)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RegistroProveedorResponse registrar(@PathVariable String empresa,
            @Valid @RequestBody RegistroProveedorRequest request) {
        return proveedorService.registrar(request);
    }

    /**
     * Categorías que se pueden elegir en el formulario de registro de esa empresa.
     *
     * @param empresa código público de la empresa (lo usa el filtro; aquí solo documenta la ruta)
     * @return categorías activas
     */
    @GetMapping("/categorias")
    public List<CategoriaResponse> categorias(@PathVariable String empresa) {
        return proveedorService.categoriasDisponibles();
    }
}
