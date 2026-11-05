package com.proyecto.supply_core.empresa.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.empresa.dto.EmpresaRequest;
import com.proyecto.supply_core.empresa.dto.ModulosEmpresaResponse;
import com.proyecto.supply_core.empresa.dto.EmpresaResponse;
import com.proyecto.supply_core.empresa.service.EmpresaService;
import com.proyecto.supply_core.plataforma.service.AccesoEmpresaService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.SesionActual;

import jakarta.validation.Valid;

/** Datos de la empresa: cualquier usuario interno consulta; ADMIN_SISTEMA edita. */
@RestController
@RequestMapping("/api/empresa")
@UsuarioInterno
public class EmpresaController {

    private final EmpresaService empresaService;
    private final AccesoEmpresaService accesoEmpresa;

    /**
     * @param empresaService lógica de la empresa
     * @param accesoEmpresa  suscripción y módulos de la empresa
     */
    public EmpresaController(EmpresaService empresaService, AccesoEmpresaService accesoEmpresa) {
        this.empresaService = empresaService;
        this.accesoEmpresa = accesoEmpresa;
    }

    /**
     * Estado de la suscripción y módulos que la empresa puede usar hoy (los decide el dueño de la plataforma).
     *
     * @return estado, plan y módulos activos
     */
    @GetMapping("/modulos")
    public ModulosEmpresaResponse modulos() {
        AccesoEmpresaService.Resumen r = accesoEmpresa.resumen(SesionActual.usuario().empresaId());
        return new ModulosEmpresaResponse(r.estado(), r.plan(), r.modulos().stream().sorted().toList());
    }

    /**
     * Datos de la empresa.
     *
     * @return empresa
     */
    @GetMapping
    public EmpresaResponse obtener() {
        return empresaService.obtener();
    }

    /**
     * Edita los datos de la empresa.
     *
     * @param request datos validados
     * @return empresa actualizada
     */
    @PutMapping
    @RequiereRol(Roles.ADMIN_SISTEMA)
    public EmpresaResponse actualizar(@Valid @RequestBody EmpresaRequest request) {
        return empresaService.actualizar(request);
    }
}
