package com.proyecto.supply_core.compra.solicitud.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;

/**
 * Tarea programada del SLA de aprobación: avisa y escala las solicitudes pendientes, empresa por empresa.
 * Corre cada {@code app.aprobacion.sla-cada-ms} (por defecto 5 minutos). Se desactiva con
 * {@code app.aprobacion.sla-automatico=false}.
 */
@Component
@ConditionalOnProperty(name = "app.aprobacion.sla-automatico", havingValue = "true", matchIfMissing = true)
public class SlaAprobacionJob {

    private static final Logger log = LoggerFactory.getLogger(SlaAprobacionJob.class);

    private final SlaAprobacionService slaService;
    private final EmpresaRepository empresaRepository;

    /**
     * @param slaService        lógica del SLA
     * @param empresaRepository empresas a recorrer
     */
    public SlaAprobacionJob(SlaAprobacionService slaService, EmpresaRepository empresaRepository) {
        this.slaService = slaService;
        this.empresaRepository = empresaRepository;
    }

    /**
     * Revisa el SLA de cada empresa con su propio contexto. Un error en una empresa se registra y no impide revisar
     * las demás; se reintenta en la siguiente ejecución.
     */
    @Scheduled(fixedDelayString = "${app.aprobacion.sla-cada-ms:300000}",
            initialDelayString = "${app.aprobacion.sla-cada-ms:300000}")
    public void revisar() {
        List<Long> empresas;
        try {
            empresas = EmpresaContexto.sinEmpresa(empresaRepository::todosLosIds);
        } catch (RuntimeException e) {
            log.error("No se pudo listar las empresas para el SLA de aprobación: {}", e.getMessage(), e);
            return;
        }
        for (Long empresaId : empresas) {
            try {
                SlaAprobacionService.Resultado r = EmpresaContexto.como(empresaId,
                        () -> slaService.procesar(LocalDateTime.now()));
                if (r.avisadas() > 0 || r.escaladas() > 0) {
                    log.info("SLA de aprobación (empresa {}): {} avisadas, {} escaladas", empresaId, r.avisadas(),
                            r.escaladas());
                }
            } catch (RuntimeException e) {
                log.error("Falló el SLA de aprobación de la empresa {}: {}", empresaId, e.getMessage(), e);
            }
        }
    }
}
