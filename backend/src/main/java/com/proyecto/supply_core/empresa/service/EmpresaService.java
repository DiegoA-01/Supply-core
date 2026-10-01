package com.proyecto.supply_core.empresa.service;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.util.EmpresaUnica;
import com.proyecto.supply_core.empresa.dto.EmpresaRequest;
import com.proyecto.supply_core.empresa.dto.EmpresaResponse;
import com.proyecto.supply_core.empresa.entity.Empresa;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;

/** Consulta y edición de los datos de la (única) empresa. */
@Service
public class EmpresaService {

    private final EmpresaRepository empresaRepository;
    private final AuditoriaService auditoriaService;

    /**
     * @param empresaRepository acceso a la empresa
     * @param auditoriaService  registro de cambios
     */
    public EmpresaService(EmpresaRepository empresaRepository, AuditoriaService auditoriaService) {
        this.empresaRepository = empresaRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Datos de la empresa.
     *
     * @return empresa
     * @throws NoEncontradoException si falta la empresa sembrada en V2
     */
    @Transactional(readOnly = true)
    public EmpresaResponse obtener() {
        return EmpresaResponse.of(buscar());
    }

    /**
     * Actualiza los datos de la empresa.
     *
     * @param req datos validados
     * @return empresa actualizada
     * @throws NegocioException si otra empresa ya usa el NIT
     */
    @Transactional
    public EmpresaResponse actualizar(EmpresaRequest req) {
        Empresa e = buscar();
        String nit = req.nit().trim();
        if (empresaRepository.existsByNitIgnoreCaseAndIdNot(nit, e.getId())) {
            throw new NegocioException("NIT_DUPLICADO", "Otra empresa ya usa el NIT " + nit);
        }
        String antes = describir(e);
        e.setRazonSocial(req.razonSocial().trim());
        e.setNit(nit);
        e.setDireccionPrincipal(req.direccionPrincipal() == null || req.direccionPrincipal().isBlank()
                ? null : req.direccionPrincipal().trim());
        e.setMonedaBase(req.monedaBase().trim().toUpperCase(Locale.ROOT));
        auditoriaService.exito(AccionAuditoria.EMPRESA_EDITAR, EntidadAuditada.EMPRESA, e.getId(),
                "antes: " + antes + " | después: " + describir(e));
        return EmpresaResponse.of(e);
    }

    /**
     * Carga la empresa única.
     *
     * @return entidad
     * @throws NoEncontradoException si no existe (migración V2 no aplicada)
     */
    private Empresa buscar() {
        return empresaRepository.findById(EmpresaUnica.ID)
                .orElseThrow(() -> new NoEncontradoException("Empresa", EmpresaUnica.ID));
    }

    /**
     * Resumen para la auditoría.
     *
     * @param e empresa
     * @return texto con razón social, NIT y moneda
     */
    private static String describir(Empresa e) {
        return "razonSocial=" + e.getRazonSocial() + ", nit=" + e.getNit() + ", moneda=" + e.getMonedaBase();
    }
}
