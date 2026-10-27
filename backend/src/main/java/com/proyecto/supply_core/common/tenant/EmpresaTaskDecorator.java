package com.proyecto.supply_core.common.tenant;

import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

/**
 * Hace que las tareas {@code @Async} corran con la misma empresa del hilo que las lanzó. Spring Boot lo
 * aplica solo al ejecutor de tareas por defecto.
 */
@Component
public class EmpresaTaskDecorator implements TaskDecorator {

    /**
     * Envuelve la tarea para que lleve la empresa del hilo actual.
     *
     * @param runnable tarea original
     * @return tarea con el contexto de empresa propagado
     */
    @Override
    public Runnable decorate(Runnable runnable) {
        return EmpresaContexto.propagar(runnable);
    }
}
