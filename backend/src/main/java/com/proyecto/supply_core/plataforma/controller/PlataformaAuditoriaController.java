package com.proyecto.supply_core.plataforma.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.plataforma.dto.AuditoriaPlataformaResponse;
import com.proyecto.supply_core.plataforma.service.PlataformaAuditoriaService;

import jakarta.validation.constraints.Positive;

/**
 * Auditoría global de la plataforma: logins, MFA y cambios sobre empresas y planes. Solo lectura y solo con el token
 * de la plataforma.
 */
@RestController
@RequestMapping("/api/plataforma/auditoria")
public class PlataformaAuditoriaController {

    private final PlataformaAuditoriaService auditoriaService;

    /**
     * @param auditoriaService consulta de la auditoría global
     */
    public PlataformaAuditoriaController(PlataformaAuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    /**
     * Registros de auditoría, el más reciente primero.
     *
     * @param empresaId empresa afectada (todas si no se indica)
     * @param pageable  página (máximo 100)
     * @return página de registros
     */
    @GetMapping
    public PageResponse<AuditoriaPlataformaResponse> buscar(
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long empresaId,
            @PageableDefault(size = 20) Pageable pageable) {
        return auditoriaService.buscar(empresaId, pageable);
    }
}
