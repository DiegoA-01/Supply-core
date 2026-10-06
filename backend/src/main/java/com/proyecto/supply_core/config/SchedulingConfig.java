package com.proyecto.supply_core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activa las tareas programadas ({@code @Scheduled}), p. ej. el cierre automático de RFQ. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
