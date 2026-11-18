package com.proyecto.supply_core.proveedor.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.catalogo.dto.CategoriaResponse;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.proveedor.dto.ActualizarProveedorRequest;
import com.proyecto.supply_core.proveedor.dto.EvaluacionResponse;
import com.proyecto.supply_core.proveedor.dto.ProveedorResponse;
import com.proyecto.supply_core.proveedor.service.EvaluacionProveedorService;
import com.proyecto.supply_core.proveedor.service.ProveedorService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;

/**
 * Portal del proveedor (solo rol PROVEEDOR). Todo se filtra por el {@code proveedorId} del token:
 * un proveedor nunca ve datos de otro. Incluye su perfil y sus evaluaciones; aquí se sumarán sus RFQ y cotizaciones (pasos 11–12).
 */
@RequiereModulo(Modulos.PROVEEDORES)
@RestController
@RequestMapping("/api/portal")
@RequiereRol(Roles.PROVEEDOR)
public class PortalProveedorController {

    private final ProveedorService proveedorService;
    private final EvaluacionProveedorService evaluacionService;

    /**
     * @param proveedorService  lógica de proveedores
     * @param evaluacionService evaluaciones (UC-14)
     */
    public PortalProveedorController(ProveedorService proveedorService, EvaluacionProveedorService evaluacionService) {
        this.proveedorService = proveedorService;
        this.evaluacionService = evaluacionService;
    }

    /**
     * Edita sus datos de contacto y las categorías que cotiza.
     *
     * @param request datos nuevos
     * @return su proveedor actualizado
     */
    @PutMapping("/proveedor")
    public ProveedorResponse actualizarMiProveedor(@Valid @RequestBody ActualizarProveedorRequest request) {
        return proveedorService.actualizarMio(request);
    }

    /**
     * Sus propias evaluaciones, las más recientes primero.
     *
     * @param pageable página (máximo 100) y orden
     * @return página de evaluaciones
     */
    @GetMapping("/proveedor/evaluaciones")
    public PageResponse<EvaluacionResponse> misEvaluaciones(
            @PageableDefault(size = 20, sort = "creadoEn", direction = Sort.Direction.DESC) Pageable pageable) {
        return evaluacionService.mias(pageable);
    }

    /**
     * Categorías activas de la empresa, para que el proveedor elija en cuáles cotiza (editar su perfil). El proveedor no
     * puede leer el catálogo completo, solo esta lista.
     *
     * @return categorías activas de su empresa
     */
    @GetMapping("/proveedor/categorias")
    public List<CategoriaResponse> categoriasDisponibles() {
        return proveedorService.categoriasDisponibles();
    }

    /**
     * Datos del propio proveedor.
     *
     * @return su proveedor
     */
    @GetMapping("/proveedor")
    public ProveedorResponse miProveedor() {
        return proveedorService.miProveedor();
    }
}
