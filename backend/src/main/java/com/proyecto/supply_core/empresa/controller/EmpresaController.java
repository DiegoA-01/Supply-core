package com.proyecto.supply_core.empresa.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.empresa.dto.EmpresaRequest;
import com.proyecto.supply_core.empresa.dto.EmpresaResponse;
import com.proyecto.supply_core.empresa.service.EmpresaService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;

/** Datos de la empresa: cualquier usuario interno consulta; ADMIN_SISTEMA edita. */
@RestController
@RequestMapping("/api/empresa")
@UsuarioInterno
public class EmpresaController {

    private final EmpresaService empresaService;

    /**
     * @param empresaService lógica de la empresa
     */
    public EmpresaController(EmpresaService empresaService) {
        this.empresaService = empresaService;
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
