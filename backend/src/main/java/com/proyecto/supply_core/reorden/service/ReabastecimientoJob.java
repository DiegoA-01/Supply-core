package com.proyecto.supply_core.reorden.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;

/**
 * Envía cada mañana (por defecto a las 7:00, de lunes a sábado) el resumen de reabastecimiento de cada empresa.
 * La hora se cambia con {@code app.reabastecimiento.resumen-cron}; {@code "-"} lo apaga.
 */
@Component
public class ReabastecimientoJob {

    private static final Logger log = LoggerFactory.getLogger(ReabastecimientoJob.class);

    private final ReabastecimientoAvisoService avisoService;
    private final EmpresaRepository empresaRepository;

    /**
     * @param avisoService      resumen y aviso de una empresa
     * @param empresaRepository empresas a recorrer
     */
    public ReabastecimientoJob(ReabastecimientoAvisoService avisoService, EmpresaRepository empresaRepository) {
        this.avisoService = avisoService;
        this.empresaRepository = empresaRepository;
    }

    /**
     * Revisa cada empresa con su propio contexto. Un error en una empresa se registra y no impide revisar las demás.
     */
    @Scheduled(cron = "${app.reabastecimiento.resumen-cron:0 0 7 * * MON-SAT}")
    public void enviarResumen() {
        List<Long> empresas;
        try {
            empresas = EmpresaContexto.sinEmpresa(empresaRepository::todosLosIds);
        } catch (RuntimeException e) {
            log.error("No se pudo listar las empresas para el resumen de reabastecimiento: {}", e.getMessage(), e);
            return;
        }
        for (Long empresaId : empresas) {
            try {
                int productos = EmpresaContexto.como(empresaId, avisoService::avisar);
                if (productos > 0) {
                    log.info("Resumen de reabastecimiento (empresa {}): {} producto(s) por pedir", empresaId, productos);
                }
            } catch (RuntimeException e) {
                log.error("Falló el resumen de reabastecimiento de la empresa {}: {}", empresaId, e.getMessage(), e);
            }
        }
    }
}
