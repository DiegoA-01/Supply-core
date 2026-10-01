package com.proyecto.supply_core.usuario.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.usuario.dto.RestablecerPasswordRequest;
import com.proyecto.supply_core.usuario.dto.UsuarioRequest;
import com.proyecto.supply_core.usuario.dto.UsuarioResponse;
import com.proyecto.supply_core.usuario.dto.UsuarioUpdateRequest;
import com.proyecto.supply_core.usuario.service.UsuarioService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/** Administración de usuarios: solo ADMIN_SISTEMA (el AUDITOR puede consultar). */
@RestController
@RequestMapping("/api/usuarios")
@RequiereRol(Roles.ADMIN_SISTEMA)
public class UsuarioController {

    private final UsuarioService usuarioService;

    /**
     * @param usuarioService lógica de usuarios
     */
    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    /**
     * Lista usuarios con filtros y paginación ({@code ?page=0&size=20&sort=correo,desc}).
     *
     * @param texto    coincidencia en nombre o correo
     * @param activo   estado
     * @param rol      código de rol
     * @param pageable página (máximo 100 por página) y orden
     * @return página de usuarios
     */
    @GetMapping
    @RequiereRol({ Roles.ADMIN_SISTEMA, Roles.AUDITOR })
    public PageResponse<UsuarioResponse> buscar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(required = false) String rol,
            @PageableDefault(size = 20, sort = "nombreCompleto", direction = Sort.Direction.ASC) Pageable pageable) {
        return usuarioService.buscar(texto, activo, rol, pageable);
    }

    /**
     * Detalle de un usuario.
     *
     * @param id id positivo
     * @return usuario
     */
    @GetMapping("/{id}")
    @RequiereRol({ Roles.ADMIN_SISTEMA, Roles.AUDITOR })
    public UsuarioResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return usuarioService.obtener(id);
    }

    /**
     * Crea un usuario.
     *
     * @param request datos validados
     * @return usuario creado (HTTP 201)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse crear(@Valid @RequestBody UsuarioRequest request) {
        return usuarioService.crear(request);
    }

    /**
     * Edita datos y roles.
     *
     * @param id      id positivo
     * @param request datos validados
     * @return usuario actualizado
     */
    @PutMapping("/{id}")
    public UsuarioResponse actualizar(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody UsuarioUpdateRequest request) {
        return usuarioService.actualizar(id, request);
    }

    /**
     * Reactiva un usuario.
     *
     * @param id id positivo
     * @return usuario activo
     */
    @PatchMapping("/{id}/activar")
    public UsuarioResponse activar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return usuarioService.activar(id);
    }

    /**
     * Desactiva un usuario.
     *
     * @param id id positivo
     * @return usuario inactivo
     */
    @PatchMapping("/{id}/desactivar")
    public UsuarioResponse desactivar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return usuarioService.desactivar(id);
    }

    /**
     * Asigna una contraseña nueva a otro usuario.
     *
     * @param id      id positivo
     * @param request contraseña nueva validada
     */
    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restablecerPassword(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody RestablecerPasswordRequest request) {
        usuarioService.restablecerPassword(id, request);
    }
}
