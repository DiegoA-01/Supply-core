package com.proyecto.supply_core.auditoria.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.auditoria.dto.AuditoriaFiltro;
import com.proyecto.supply_core.auditoria.dto.AuditoriaResponse;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * UC-16 Consultar auditoría. Solo lectura: no existen endpoints para crear, editar ni borrar
 * registros (los escribe cada Service al ejecutar una acción sensible).
 */
@RestController
@RequestMapping("/api/auditoria")
@RequiereRol({ Roles.AUDITOR, Roles.ADMIN_SISTEMA })
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    /**
     * @param auditoriaService lógica de auditoría
     */
    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    /**
     * Busca registros, los más recientes primero.
     * <p>Ej.: {@code /api/auditoria?usuarioId=3&accion=LOGIN&resultado=FALLO&desde=2026-10-01T00:00:00}</p>
     *
     * @param filtro   filtros opcionales (validados)
     * @param pageable página (máximo 100) y orden
     * @return página de registros
     */
    @GetMapping
    public PageResponse<AuditoriaResponse> buscar(@Valid @ModelAttribute AuditoriaFiltro filtro,
            @PageableDefault(size = 50, sort = "creadoEn", direction = Sort.Direction.DESC) Pageable pageable) {
        return auditoriaService.buscar(filtro, pageable);
    }

    /**
     * Detalle de un registro.
     *
     * @param id id positivo
     * @return registro
     */
    @GetMapping("/{id}")
    public AuditoriaResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return auditoriaService.obtener(id);
    }
}
