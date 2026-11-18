package com.proyecto.supply_core.common.tenant;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Empresa (tenant) con la que trabaja el hilo actual. Es la base del aislamiento SaaS: Hibernate agrega
 * {@code empresa_id = ?} a toda consulta de una entidad con {@code @TenantId} usando este valor.
 * <p>Tres estados: <b>empresa</b> (lo normal, lo fija el {@code AuthFilter} desde el JWT), <b>sin empresa</b>
 * (solo para flujos públicos o globales como login y registro, que no tocan datos de negocio) y <b>nada</b>
 * (no se fijó nada: abrir una sesión de BD falla en lugar de ver datos de todas las empresas).</p>
 */
public final class EmpresaContexto {

    /** Marca interna: el hilo trabaja sin empresa (modo raíz); Hibernate no filtra por empresa. */
    static final long SIN_EMPRESA = -1L;

    private static final ThreadLocal<Long> ACTUAL = new ThreadLocal<>();

    /** Clase utilitaria: no se instancia. */
    private EmpresaContexto() {
    }

    /**
     * Valor crudo del contexto.
     *
     * @return id de empresa, {@link #SIN_EMPRESA} en modo raíz, o {@code null} si no se fijó nada
     */
    static Long crudo() {
        return ACTUAL.get();
    }

    /**
     * Empresa del hilo actual.
     *
     * @return id de la empresa
     * @throws IllegalStateException si el hilo no tiene empresa (jobs o hilos sin contexto)
     */
    public static Long requerida() {
        Long id = ACTUAL.get();
        if (id == null || id == SIN_EMPRESA) {
            throw new IllegalStateException("Esta operación necesita una empresa en el contexto");
        }
        return id;
    }

    /**
     * Empresa del hilo actual si hay una.
     *
     * @return id de la empresa o {@code null} (modo raíz o sin contexto)
     */
    public static Long opcional() {
        Long id = ACTUAL.get();
        return id == null || id == SIN_EMPRESA ? null : id;
    }

    /**
     * Fija la empresa del hilo hasta que se llame a {@link #limpiar()}. Lo usa el filtro de autenticación.
     *
     * @param empresaId id de la empresa
     */
    public static void fijar(Long empresaId) {
        ACTUAL.set(empresaId);
    }

    /**
     * Fija el modo sin empresa hasta que se llame a {@link #limpiar()}. Solo para rutas públicas.
     */
    public static void fijarSinEmpresa() {
        ACTUAL.set(SIN_EMPRESA);
    }

    /** Quita el contexto del hilo (siempre en un {@code finally}, los hilos se reutilizan). */
    public static void limpiar() {
        ACTUAL.remove();
    }

    /**
     * Envuelve una tarea para que, al correr en otro hilo (p. ej. {@code @Async}), use la misma empresa que
     * tiene el hilo que la creó. Si el hilo que la crea no tiene empresa, la tarea tampoco la tendrá y abrir
     * una sesión de BD fallará (nunca ve datos de todas las empresas).
     *
     * @param tarea tarea original
     * @return tarea que fija y restaura el contexto alrededor de la original
     */
    public static Runnable propagar(Runnable tarea) {
        Long capturado = ACTUAL.get();
        if (capturado == null) {
            return tarea;
        }
        return () -> {
            Long anterior = ACTUAL.get();
            ACTUAL.set(capturado);
            try {
                tarea.run();
            } finally {
                restaurar(anterior);
            }
        };
    }

    /**
     * Ejecuta el código como la empresa indicada y restaura el contexto anterior al terminar.
     * El código debe abrir sus transacciones <i>dentro</i> del bloque (la sesión toma la empresa al abrirse).
     *
     * @param empresaId id de la empresa
     * @param accion    código a ejecutar
     * @param <T>       tipo del resultado
     * @return resultado de la acción
     */
    public static <T> T como(Long empresaId, Supplier<T> accion) {
        return ejecutar(empresaId, accion);
    }

    /**
     * Igual que {@link #como(Long, Supplier)} para código sin resultado.
     *
     * @param empresaId id de la empresa
     * @param accion    código a ejecutar
     */
    public static void como(Long empresaId, Runnable accion) {
        ejecutar(empresaId, () -> {
            accion.run();
            return null;
        });
    }

    /**
     * Ejecuta el código sin empresa (modo raíz). Solo para flujos que deben buscar entre todas las
     * empresas, como el login (busca al usuario por correo) o listar empresas en un job.
     *
     * @param accion código a ejecutar
     * @param <T>    tipo del resultado
     * @return resultado de la acción
     */
    public static <T> T sinEmpresa(Supplier<T> accion) {
        return ejecutar(SIN_EMPRESA, accion);
    }

    /**
     * Igual que {@link #sinEmpresa(Supplier)} para código sin resultado.
     *
     * @param accion código a ejecutar
     */
    public static void sinEmpresa(Runnable accion) {
        ejecutar(SIN_EMPRESA, () -> {
            accion.run();
            return null;
        });
    }

    /**
     * Ejecuta una tarea con la empresa dada y propaga cualquier excepción comprobada sin envolverla.
     *
     * @param empresaId id de la empresa
     * @param accion    tarea
     * @param <T>       tipo del resultado
     * @return resultado de la tarea
     * @throws Exception lo que lance la tarea
     */
    public static <T> T comoLlamable(Long empresaId, Callable<T> accion) throws Exception {
        Long anterior = ACTUAL.get();
        ACTUAL.set(empresaId);
        try {
            return accion.call();
        } finally {
            restaurar(anterior);
        }
    }

    private static <T> T ejecutar(Long valor, Supplier<T> accion) {
        Long anterior = ACTUAL.get();
        ACTUAL.set(valor);
        try {
            return accion.get();
        } finally {
            restaurar(anterior);
        }
    }

    private static void restaurar(Long anterior) {
        if (anterior == null) {
            ACTUAL.remove();
        } else {
            ACTUAL.set(anterior);
        }
    }
}
