package com.proyecto.supply_core.delegacion.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.delegacion.dto.DelegacionRequest;
import com.proyecto.supply_core.delegacion.dto.DelegadoCandidato;
import com.proyecto.supply_core.delegacion.dto.DelegacionResponse;
import com.proyecto.supply_core.delegacion.service.DelegacionService;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * Delegación de aprobación por fechas (paso 18-c). Cualquier usuario interno consulta las suyas; el Service exige
 * que quien delega tenga un rol que apruebe en alguna política activa. Solo usuarios internos.
 */
@RequiereModulo(Modulos.COMPRAS)
@RestController
@RequestMapping("/api/delegaciones")
@UsuarioInterno
public class DelegacionController {

    private final DelegacionService delegacionService;

    /**
     * @param delegacionService lógica de delegaciones
     */
    public DelegacionController(DelegacionService delegacionService) {
        this.delegacionService = delegacionService;
    }

    /**
     * Delega las aprobaciones del usuario de la sesión en otro usuario durante un rango de fechas.
     *
     * @param request datos validados
     * @return delegación creada (HTTP 201)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DelegacionResponse crear(@Valid @RequestBody DelegacionRequest request) {
        return delegacionService.crear(request);
    }

    /**
     * Revoca una delegación (quien la dio o ADMIN_COMPRAS).
     *
     * @param id id positivo
     * @return delegación revocada
     */
    @PatchMapping("/{id}/revocar")
    public DelegacionResponse revocar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return delegacionService.revocar(id);
    }

    /**
     * Usuarios en quienes se puede delegar (solo id y nombre), para armar el formulario de delegación.
     *
     * @return candidatos por nombre
     */
    @GetMapping("/candidatos")
    public List<DelegadoCandidato> candidatos() {
        return delegacionService.candidatos();
    }

    /**
     * Delegaciones que dio el usuario de la sesión.
     *
     * @return delegaciones, la más reciente primero
     */
    @GetMapping("/otorgadas")
    public List<DelegacionResponse> otorgadas() {
        return delegacionService.otorgadas();
    }

    /**
     * Delegaciones que recibió el usuario de la sesión.
     *
     * @return delegaciones, la más reciente primero
     */
    @GetMapping("/recibidas")
    public List<DelegacionResponse> recibidas() {
        return delegacionService.recibidas();
    }
}
