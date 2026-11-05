package com.proyecto.supply_core.common.tenant;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Extensión de JUnit para las pruebas de servicios que consultan "su empresa": fija la empresa 1 en el
 * contexto antes de cada prueba y la limpia al terminar (los hilos de prueba se reutilizan).
 * Uso: {@code @ExtendWith(EmpresaDePrueba.class)}.
 */
public class EmpresaDePrueba implements BeforeEachCallback, AfterEachCallback {

    /** Empresa que usan las pruebas. */
    public static final long EMPRESA = 1L;

    /**
     * Fija la empresa de prueba.
     *
     * @param context contexto de la prueba
     */
    @Override
    public void beforeEach(ExtensionContext context) {
        EmpresaContexto.fijar(EMPRESA);
    }

    /**
     * Limpia el contexto.
     *
     * @param context contexto de la prueba
     */
    @Override
    public void afterEach(ExtensionContext context) {
        EmpresaContexto.limpiar();
    }
}
