package com.proyecto.supply_core.notificacion.enums;

/** Canal de entrega de una notificación (ENUM de {@code config_notificacion.canal}). */
public enum CanalNotificacion {
    /** Correo electrónico (por {@code EmailClient}). */
    EMAIL,
    /** Bandeja de notificaciones dentro de la aplicación (tabla {@code notificacion}). */
    INTERNA
}
