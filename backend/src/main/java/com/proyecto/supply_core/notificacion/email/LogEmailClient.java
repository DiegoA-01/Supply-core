package com.proyecto.supply_core.notificacion.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementación de desarrollo: no envía nada, solo deja el correo en el log.
 * Se reemplaza registrando otro bean {@link EmailClient}.
 */
@Component
public class LogEmailClient implements EmailClient {

    private static final Logger log = LoggerFactory.getLogger(LogEmailClient.class);

    /**
     * Registra el correo en el log (nivel INFO).
     *
     * @param para   destinatario
     * @param asunto asunto
     * @param cuerpo texto
     */
    @Override
    public void enviar(String para, String asunto, String cuerpo) {
        log.info("[EMAIL simulado] para={} asunto=\"{}\" cuerpo=\"{}\"", para, asunto, cuerpo);
    }
}
