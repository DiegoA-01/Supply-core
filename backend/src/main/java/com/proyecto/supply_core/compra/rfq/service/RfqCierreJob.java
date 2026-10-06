package com.proyecto.supply_core.compra.rfq.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    /**
     * @param rfqService lógica de RFQ
     */
    public RfqCierreJob(RfqService rfqService) {
        this.rfqService = rfqService;
    }

    /** Cierra las vencidas; un error se registra y se reintenta en la siguiente ejecución. */
    @Scheduled(fixedDelayString = "${app.rfq.cierre-cada-ms:60000}", initialDelayString = "${app.rfq.cierre-cada-ms:60000}")
    public void cerrarVencidas() {
        try {
            int cerradas = rfqService.cerrarVencidas();
            if (cerradas > 0) {
                log.info("Cierre automático: {} RFQ cerradas por fecha límite", cerradas);
            }
        } catch (RuntimeException e) {
            log.error("Falló el cierre automático de RFQ: {}", e.getMessage(), e);
        }
    }
}
