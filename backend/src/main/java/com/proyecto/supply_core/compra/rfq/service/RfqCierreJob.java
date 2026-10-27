package com.proyecto.supply_core.compra.rfq.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;

/**
 * Tarea programada que cierra las RFQ ABIERTAS cuya fecha límite ya pasó.
 * Corre cada {@code app.rfq.cierre-cada-ms} (por defecto 60 s). Se desactiva con
 * {@code app.rfq.cierre-automatico=false}.
 */
@Component
@ConditionalOnProperty(name = "app.rfq.cierre-automatico", havingValue = "true", matchIfMissing = true)
public class RfqCierreJob {

    private static final Logger log = LoggerFactory.getLogger(RfqCierreJob.class);

    private final RfqService rfqService;
    private final EmpresaRepository empresaRepository;

    /**
     * @param rfqService        lógica de RFQ
     * @param empresaRepository empresas a recorrer
     */
    public RfqCierreJob(RfqService rfqService, EmpresaRepository empresaRepository) {
        this.rfqService = rfqService;
        this.empresaRepository = empresaRepository;
    }

    /**
     * Cierra las vencidas, empresa por empresa (cada una con su propio contexto). Un error en una empresa
     * se registra y no impide cerrar las de las demás; se reintenta en la siguiente ejecución.
     */
    @Scheduled(fixedDelayString = "${app.rfq.cierre-cada-ms:60000}", initialDelayString = "${app.rfq.cierre-cada-ms:60000}")
    public void cerrarVencidas() {
        List<Long> empresas;
        try {
            empresas = EmpresaContexto.sinEmpresa(empresaRepository::todosLosIds);
        } catch (RuntimeException e) {
            log.error("No se pudo listar las empresas para el cierre automático de RFQ: {}", e.getMessage(), e);
            return;
        }
        for (Long empresaId : empresas) {
            try {
                int cerradas = EmpresaContexto.como(empresaId, () -> rfqService.cerrarVencidas());
                if (cerradas > 0) {
                    log.info("Cierre automático (empresa {}): {} RFQ cerradas por fecha límite", empresaId, cerradas);
                }
            } catch (RuntimeException e) {
                log.error("Falló el cierre automático de RFQ de la empresa {}: {}", empresaId, e.getMessage(), e);
            }
        }
    }
}
