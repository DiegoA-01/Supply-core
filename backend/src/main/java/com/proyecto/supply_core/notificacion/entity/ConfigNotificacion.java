package com.proyecto.supply_core.notificacion.entity;

import com.proyecto.supply_core.notificacion.enums.CanalNotificacion;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Indica si un evento se notifica por un canal (tabla {@code config_notificacion}, UNIQUE evento+canal).
 * Las filas las siembran las migraciones; por la API solo se activa o desactiva.
 */
@Entity
@Table(name = "config_notificacion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConfigNotificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 80, updatable = false)
    private EventoNotificacion evento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private CanalNotificacion canal;

    @Setter
    @Column(nullable = false)
    private boolean activo;

    /**
     * Crea una configuración (usado en pruebas; en producción las filas vienen de Flyway).
     *
     * @param evento evento
     * @param canal  canal
     * @param activo si se envía
     */
    public ConfigNotificacion(EventoNotificacion evento, CanalNotificacion canal, boolean activo) {
        this.evento = evento;
        this.canal = canal;
        this.activo = activo;
    }
}
