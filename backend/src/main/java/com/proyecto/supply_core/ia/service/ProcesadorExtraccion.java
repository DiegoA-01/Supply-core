package com.proyecto.supply_core.ia.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.proyecto.supply_core.ia.client.IAClient;
import com.proyecto.supply_core.ia.client.IAException;
import com.proyecto.supply_core.ia.dto.ExtraccionIA;

/**
 * Lee cotizaciones con la IA en segundo plano.
 * <p>Se ejecuta después del commit (la cotización ya está guardada) y en otro hilo ({@code @Async}): la
 * llamada a la IA puede tardar segundos (Claude) o minutos (Ollama en CPU), y no debe bloquear al proveedor
 * ni tener una transacción de BD abierta mientras espera. Por eso hay tres pasos: preparar (transacción
 * corta), llamar a la IA (sin transacción) y guardar (transacción corta).</p>
 */
@Component
public class ProcesadorExtraccion {

    private static final Logger log = LoggerFactory.getLogger(ProcesadorExtraccion.class);

    private final ExtraccionService extraccionService;
    private final IAClient iaClient;

    /**
     * @param extraccionService pasos transaccionales
     * @param iaClient          IA configurada
     */
    public ProcesadorExtraccion(ExtraccionService extraccionService, IAClient iaClient) {
        this.extraccionService = extraccionService;
        this.iaClient = iaClient;
    }

    /**
     * Procesa una lectura programada.
     *
     * @param evento procesamiento a leer
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alProgramar(LecturaProgramadaEvent evento) {
        procesar(evento.procesamientoId());
    }

    /**
     * Prepara, llama a la IA y guarda el resultado o el error. Nunca lanza excepciones.
     *
     * @param procesamientoId procesamiento
     */
    void procesar(Long procesamientoId) {
        try {
            ExtraccionService.Lectura lectura = extraccionService.prepararLectura(procesamientoId);
            long inicio = System.currentTimeMillis();
            ExtraccionIA resultado = iaClient.extraer(lectura.archivo(), lectura.productos());
            log.info("Procesamiento {} leído por {} en {} ms", procesamientoId, iaClient.modelo(),
                    System.currentTimeMillis() - inicio);
            extraccionService.guardarResultado(procesamientoId, resultado);
        } catch (IAException e) {
            extraccionService.guardarError(procesamientoId, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Error inesperado leyendo el procesamiento {}", procesamientoId, e);
            extraccionService.guardarError(procesamientoId, "Error inesperado: " + e.getMessage());
        }
    }
}
