package com.proyecto.supply_core.caja.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.caja.dto.AbrirTurnoRequest;
import com.proyecto.supply_core.caja.dto.AprobarTurnoRequest;
import com.proyecto.supply_core.caja.dto.CerrarTurnoRequest;
import com.proyecto.supply_core.caja.dto.MovimientoCajaRequest;
import com.proyecto.supply_core.caja.dto.TurnoResponse;
import com.proyecto.supply_core.caja.enums.EstadoTurno;
import com.proyecto.supply_core.caja.service.TurnoService;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * Turnos de caja (spec 003): abrir, retiros, gastos e ingresos, cierre ciego y aprobación de diferencias. Abren y
 * cierran su turno el cajero y el supervisor; supervisión, el dueño y auditoría consultan todos.
 */
@RequiereModulo(Modulos.CAJA)
@RestController
@RequestMapping("/api/turnos")
@UsuarioInterno
@RequiereRol({ Roles.CAJERO, Roles.SUPERVISOR_VENTAS })
public class TurnoController {

    private final TurnoService turnoService;

    /**
     * @param turnoService lógica de turnos
     */
    public TurnoController(TurnoService turnoService) {
        this.turnoService = turnoService;
    }

    /**
     * @param request caja y base en efectivo
     * @return turno abierto (HTTP 201)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TurnoResponse abrir(@Valid @RequestBody AbrirTurnoRequest request) {
        return turnoService.abrir(request);
    }

    /**
     * @return el turno abierto de la sesión (404 si no tiene)
     */
    @GetMapping("/actual")
    public TurnoResponse actual() {
        return turnoService.actual();
    }

    /**
     * @param id      turno
     * @param request retiro, gasto o ingreso con su autorización
     * @return turno con el movimiento
     */
    @PostMapping("/{id}/movimientos")
    public TurnoResponse registrarMovimiento(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody MovimientoCajaRequest request) {
        return turnoService.registrarMovimiento(id, request);
    }

    /**
     * @param id      turno
     * @param request lo contado (cierre ciego)
     * @return turno cerrado con esperado, contado y diferencias
     */
    @PostMapping("/{id}/cerrar")
    public TurnoResponse cerrar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody CerrarTurnoRequest request) {
        return turnoService.cerrar(id, request);
    }

    /**
     * @param id      turno
     * @param request explicación de la diferencia
     * @return turno aprobado
     */
    @PostMapping("/{id}/aprobar")
    @RequiereRol({ Roles.SUPERVISOR_VENTAS, Roles.ADMIN_SISTEMA })
    public TurnoResponse aprobar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody AprobarTurnoRequest request) {
        return turnoService.aprobar(id, request);
    }

    /**
     * @param estado     filtro por estado
     * @param pendientes solo los que esperan aprobación
     * @param cajaId     filtro por caja
     * @param pageable   página (20 por defecto)
     * @return turnos, los más recientes primero
     */
    @GetMapping
    @RequiereRol({ Roles.SUPERVISOR_VENTAS, Roles.ADMIN_SISTEMA, Roles.AUDITOR })
    public PageResponse<TurnoResponse> buscar(@RequestParam(required = false) EstadoTurno estado,
            @RequestParam(defaultValue = "false") boolean pendientes,
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long cajaId,
            @PageableDefault(size = 20) Pageable pageable) {
        return turnoService.buscar(estado, pendientes, cajaId, pageable);
    }

    /**
     * Detalle y tiquete Z de un turno (el cajero solo ve los suyos).
     *
     * @param id turno
     * @return turno con su libro de caja
     */
    @GetMapping("/{id}")
    @RequiereRol({ Roles.CAJERO, Roles.SUPERVISOR_VENTAS, Roles.ADMIN_SISTEMA, Roles.AUDITOR })
    public TurnoResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return turnoService.obtener(id);
    }
}
