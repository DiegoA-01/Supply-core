package com.proyecto.supply_core.inventario.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.inventario.dto.MovimientoFiltro;
import com.proyecto.supply_core.inventario.dto.MovimientoRequest;
import com.proyecto.supply_core.inventario.dto.MovimientoResponse;
import com.proyecto.supply_core.inventario.dto.RegistroMovimientoResponse;
import com.proyecto.supply_core.inventario.dto.StockResponse;
import com.proyecto.supply_core.inventario.service.MovimientoInventarioService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * UC-03 Inventario: consulta de existencias y kardex (cualquier interno) y registro de movimientos
 * (ALMACENISTA). A propósito NO existe ningún endpoint para editar el stock directamente.
 */
@RestController
@RequestMapping("/api/inventario")
@UsuarioInterno
public class InventarioController {

    private final MovimientoInventarioService movimientoService;

    /**
     * @param movimientoService lógica de movimientos e inventario
     */
    public InventarioController(MovimientoInventarioService movimientoService) {
        this.movimientoService = movimientoService;
    }

    /**
     * Existencias por producto y bodega.
     *
     * @param productoId filtro opcional
     * @param bodegaId   filtro opcional
     * @return filas de stock
     */
    @GetMapping("/stock")
    public List<StockResponse> stock(
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long productoId,
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long bodegaId) {
        return movimientoService.existencias(productoId, bodegaId);
    }

    /**
     * Kardex con filtros, más recientes primero.
     *
     * @param filtro   filtros opcionales (validados)
     * @param pageable página (máximo 100) y orden
     * @return página de movimientos
     */
    @GetMapping("/movimientos")
    public PageResponse<MovimientoResponse> movimientos(@Valid @ModelAttribute MovimientoFiltro filtro,
            @PageableDefault(size = 50, sort = "creadoEn", direction = Sort.Direction.DESC) Pageable pageable) {
        return movimientoService.buscar(filtro, pageable);
    }

    /**
     * Registra una salida, ajuste o traslado.
     *
     * @param request movimiento validado según su tipo
     * @return movimiento, stock total y alerta de mínimo (HTTP 201)
     */
    @PostMapping("/movimientos")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol(Roles.ALMACENISTA)
    public RegistroMovimientoResponse registrar(@Valid @RequestBody MovimientoRequest request) {
        return movimientoService.registrar(request);
    }
}
