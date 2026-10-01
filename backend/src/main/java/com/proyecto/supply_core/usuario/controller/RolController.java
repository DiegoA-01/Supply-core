package com.proyecto.supply_core.usuario.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.usuario.dto.RolResponse;
import com.proyecto.supply_core.usuario.service.UsuarioService;

/** Catálogo de roles (solo lectura; los roles se siembran en V2). */
@RestController
@RequestMapping("/api/roles")
@RequiereRol({ Roles.ADMIN_SISTEMA, Roles.AUDITOR })
public class RolController {

    private final UsuarioService usuarioService;

    /**
     * @param usuarioService lógica de usuarios (expone el catálogo de roles)
     */
    public RolController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    /**
     * Lista los roles disponibles para asignar.
     *
     * @return roles ordenados por nombre
     */
    @GetMapping
    public List<RolResponse> listar() {
        return usuarioService.listarRoles();
    }
}
