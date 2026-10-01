package com.proyecto.supply_core.catalogo.controller;

import java.math.BigDecimal;
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

import com.proyecto.supply_core.catalogo.dto.ConversionResponse;
import com.proyecto.supply_core.catalogo.dto.UnidadMedidaRequest;
import com.proyecto.supply_core.catalogo.dto.UnidadMedidaResponse;
import com.proyecto.supply_core.catalogo.dto.UnidadMedidaUpdateRequest;
import com.proyecto.supply_core.catalogo.service.UnidadMedidaService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** Unidades de medida: cualquier usuario interno consulta y convierte; ADMIN_COMPRAS y ADMIN_SISTEMA administran. */
@RestController
@RequestMapping("/api/unidades-medida")
@UsuarioInterno
public class UnidadMedidaController {

    private final UnidadMedidaService unidadService;

    /**
     * @param unidadService lógica de unidades
     */
    public UnidadMedidaController(UnidadMedidaService unidadService) {
        this.unidadService = unidadService;
    }

    /**
     * Lista las unidades ({@code ?activo=true} para los selectores).
     *
     * @param activo filtro opcional por estado
     * @return unidades por magnitud y código
     */
    @GetMapping
    public List<UnidadMedidaResponse> listar(@RequestParam(required = false) Boolean activo) {
        return unidadService.listar(activo);
    }

    /**
     * Convierte una cantidad entre dos unidades de la misma magnitud.
     * <p>Ej.: {@code ?cantidad=2&desde=2&hacia=1} (2 docenas → 24 unidades).</p>
     *
     * @param cantidad cantidad a convertir (≥ 0)
     * @param desde    id de la unidad de origen
     * @param hacia    id de la unidad de destino
     * @return cantidad equivalente
     */
    @GetMapping("/convertir")
    public ConversionResponse convertir(
            @RequestParam @NotNull(message = "es obligatoria") @PositiveOrZero(message = "no puede ser negativa")
            @Digits(integer = 14, fraction = 4, message = "máximo 14 enteros y 4 decimales") BigDecimal cantidad,
            @RequestParam @NotNull(message = "es obligatorio") @Positive(message = "debe ser un id válido") Long desde,
            @RequestParam @NotNull(message = "es obligatorio") @Positive(message = "debe ser un id válido") Long hacia) {
        return unidadService.consultarConversion(cantidad, desde, hacia);
    }

    /**
     * Detalle de una unidad.
     *
     * @param id id positivo
     * @return unidad
     */
    @GetMapping("/{id}")
    public UnidadMedidaResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return unidadService.obtener(id);
    }

    /**
     * Crea una unidad.
     *
     * @param request datos validados
     * @return unidad creada (HTTP 201)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol({ Roles.ADMIN_COMPRAS, Roles.ADMIN_SISTEMA })
    public UnidadMedidaResponse crear(@Valid @RequestBody UnidadMedidaRequest request) {
        return unidadService.crear(request);
    }

    /**
     * Cambia el nombre de una unidad (código, magnitud y factor son inmutables).
     *
     * @param id      id positivo
     * @param request nombre nuevo
     * @return unidad actualizada
     */
    @PutMapping("/{id}")
    @RequiereRol({ Roles.ADMIN_COMPRAS, Roles.ADMIN_SISTEMA })
    public UnidadMedidaResponse actualizar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody UnidadMedidaUpdateRequest request) {
        return unidadService.actualizar(id, request);
    }

    /**
     * Activa una unidad.
     *
     * @param id id positivo
     * @return unidad activa
     */
    @PatchMapping("/{id}/activar")
    @RequiereRol({ Roles.ADMIN_COMPRAS, Roles.ADMIN_SISTEMA })
    public UnidadMedidaResponse activar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return unidadService.cambiarEstado(id, true);
    }

    /**
     * Desactiva una unidad.
     *
     * @param id id positivo
     * @return unidad inactiva
     */
    @PatchMapping("/{id}/desactivar")
    @RequiereRol({ Roles.ADMIN_COMPRAS, Roles.ADMIN_SISTEMA })
    public UnidadMedidaResponse desactivar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return unidadService.cambiarEstado(id, false);
    }
}
