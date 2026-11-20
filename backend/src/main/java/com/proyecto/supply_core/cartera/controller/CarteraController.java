package com.proyecto.supply_core.cartera.controller;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.cartera.dto.AbonoRequest;
import com.proyecto.supply_core.cartera.dto.AbonoResponse;
import com.proyecto.supply_core.cartera.dto.ClienteCartera;
import com.proyecto.supply_core.cartera.dto.DetalleCartera;
import com.proyecto.supply_core.cartera.dto.ExcepcionCreditoRequest;
import com.proyecto.supply_core.cartera.dto.ResumenCartera;
import com.proyecto.supply_core.cartera.service.AbonoService;
import com.proyecto.supply_core.cartera.service.CarteraService;
import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/** Cartera (spec 007): saldos y edades, cuentas de un cliente, excepción de crédito y abonos en la caja. */
@RequiereModulo(Modulos.CARTERA)
@RestController
@RequestMapping("/api")
@UsuarioInterno
@Validated
public class CarteraController {

    private final CarteraService carteraService;
    private final AbonoService abonoService;

    /**
     * @param carteraService cartera
     * @param abonoService   abonos
     */
    public CarteraController(CarteraService carteraService, AbonoService abonoService) {
        this.carteraService = carteraService;
        this.abonoService = abonoService;
    }

    /**
     * @return saldos por cliente y edades
     */
    @GetMapping("/cartera")
    @RequiereRol({ Roles.CAJERO, Roles.SUPERVISOR_VENTAS, Roles.ADMIN_SISTEMA, Roles.AUDITOR })
    public ResumenCartera resumen() {
        return carteraService.resumen();
    }

    /**
     * @param id cliente
     * @return sus cuentas y abonos
     */
    @GetMapping("/cartera/clientes/{id}")
    @RequiereRol({ Roles.CAJERO, Roles.SUPERVISOR_VENTAS, Roles.ADMIN_SISTEMA, Roles.AUDITOR })
    public DetalleCartera detalle(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return carteraService.detalle(id);
    }

    /**
     * @param id      cliente
     * @param request fecha límite y motivo
     * @return cartera del cliente
     */
    @PutMapping("/cartera/clientes/{id}/excepcion")
    @RequiereRol({ Roles.SUPERVISOR_VENTAS, Roles.ADMIN_SISTEMA })
    public ClienteCartera fijarExcepcion(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody ExcepcionCreditoRequest request) {
        return carteraService.fijarExcepcion(id, request);
    }

    /**
     * @param id cliente
     * @return cartera del cliente
     */
    @DeleteMapping("/cartera/clientes/{id}/excepcion")
    @RequiereRol({ Roles.SUPERVISOR_VENTAS, Roles.ADMIN_SISTEMA })
    public ClienteCartera quitarExcepcion(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return carteraService.quitarExcepcion(id);
    }

    /**
     * @param request cliente, valor y medio
     * @return el abono con su recibo
     */
    @PostMapping("/abonos")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol({ Roles.CAJERO, Roles.SUPERVISOR_VENTAS })
    public AbonoResponse abonar(@Valid @RequestBody AbonoRequest request) {
        return abonoService.registrar(request);
    }

    /**
     * @param id abono
     * @return el recibo del abono
     */
    @GetMapping("/abonos/{id}/recibo")
    @RequiereRol({ Roles.CAJERO, Roles.SUPERVISOR_VENTAS, Roles.ADMIN_SISTEMA, Roles.AUDITOR })
    public AbonoResponse recibo(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return abonoService.obtener(id);
    }
}
