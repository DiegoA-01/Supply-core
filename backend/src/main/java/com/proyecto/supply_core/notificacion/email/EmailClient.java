package com.proyecto.supply_core.notificacion.email;

/**
 * Envío de correos detrás de una interfaz propia: hoy {@link LogEmailClient}; más adelante
 * SMTP o un proveedor externo sin tocar a quien notifica.
 */
public interface EmailClient {

    /**
     * Envía un correo de texto plano.
     *
     * @param para   destinatario
     * @param asunto asunto
     * @param cuerpo texto
     */
    void enviar(String para, String asunto, String cuerpo);
}
