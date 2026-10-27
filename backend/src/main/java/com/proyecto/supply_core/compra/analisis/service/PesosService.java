package com.proyecto.supply_core.compra.analisis.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.compra.analisis.dto.PesosResponse;
import com.proyecto.supply_core.compra.analisis.entity.ConfigPesos;
import com.proyecto.supply_core.compra.analisis.entity.CriterioEvaluacion;
import com.proyecto.supply_core.compra.analisis.repository.ConfigPesosRepository;
import com.proyecto.supply_core.compra.analisis.repository.CriterioRepository;

/**
 * Matriz de pesos versionada. Cambiar pesos crea una versión nueva y cierra la vigente: los análisis ya
 * hechos conservan la versión que usaron (snapshot).
 */
@Service
public class PesosService {

    /** Cien. */
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final ConfigPesosRepository configRepository;
    private final CriterioRepository criterioRepository;
    private final AuditoriaService auditoriaService;

    /**
     * @param configRepository   versiones de pesos
     * @param criterioRepository criterios
     * @param auditoriaService   registro de cambios
     */
    public PesosService(ConfigPesosRepository configRepository, CriterioRepository criterioRepository,
            AuditoriaService auditoriaService) {
        this.configRepository = configRepository;
        this.criterioRepository = criterioRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Versión vigente.
     *
     * @return pesos vigentes
     * @throws NegocioException si no hay ninguna versión activa
     */
    @Transactional(readOnly = true)
    public PesosResponse vigente() {
        return PesosResponse.of(entidadVigente());
    }

    /**
     * Versión vigente (entidad, para el análisis).
     *
     * @return versión con detalles
     * @throws NegocioException si no hay ninguna versión activa
     */
    @Transactional(readOnly = true)
    public ConfigPesos entidadVigente() {
        return configRepository.findFirstByEmpresaIdAndActivoTrueOrderByVigenteDesdeDesc(EmpresaContexto.requerida())
                .orElseThrow(() -> new NegocioException("SIN_PESOS", "No hay una matriz de pesos vigente"));
    }

    /**
     * Crea una versión nueva con todos los criterios y suma 100, y cierra la anterior.
     *
     * @param pesos peso por código de criterio
     * @return versión nueva
     * @throws NegocioException si falta o sobra un criterio o la suma no es 100
     */
    @Transactional
    public PesosResponse cambiar(Map<String, BigDecimal> pesos) {
        List<CriterioEvaluacion> criterios = criterioRepository.findAllByOrderByCodigoAsc();
        Set<String> codigos = criterios.stream().map(CriterioEvaluacion::getCodigo).collect(Collectors.toSet());
        Map<String, BigDecimal> normalizados = pesos.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().trim().toUpperCase(), Map.Entry::getValue));
        if (!normalizados.keySet().equals(codigos)) {
            throw new NegocioException("PESOS_INCOMPLETOS", "Debe dar un peso a cada criterio, y solo a ellos: " + codigos);
        }
        BigDecimal suma = normalizados.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (suma.compareTo(CIEN) != 0) {
            throw new NegocioException("PESOS_NO_SUMAN_100", "Los pesos suman " + suma.toPlainString() + "; deben sumar 100");
        }
        LocalDateTime ahora = LocalDateTime.now();
        configRepository.findFirstByEmpresaIdAndActivoTrueOrderByVigenteDesdeDesc(EmpresaContexto.requerida()).ifPresent(v -> {
            v.setActivo(false);
            v.setVigenteHasta(ahora);
        });
        ConfigPesos nueva = new ConfigPesos();
        nueva.setVigenteDesde(ahora);
        criterios.forEach(c -> nueva.agregar(c, normalizados.get(c.getCodigo())));
        ConfigPesos guardada = configRepository.save(nueva);
        auditoriaService.exito(AccionAuditoria.PESOS_CAMBIAR, EntidadAuditada.CONFIG_PESOS, guardada.getId(),
                "pesos=" + normalizados);
        return PesosResponse.of(guardada);
    }
}
