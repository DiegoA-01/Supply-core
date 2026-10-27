package com.proyecto.supply_core.common.tenant;

import java.util.Map;

import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

/**
 * Le dice a Hibernate con qué empresa abrir cada sesión (multitenencia por discriminador, {@code @TenantId}).
 * <p>Si el hilo no tiene ni empresa ni modo raíz, falla: así un job o un hilo mal configurado nunca ve datos
 * de todas las empresas por accidente.</p>
 */
@Component
public class EmpresaResolver implements CurrentTenantIdentifierResolver<Long>, HibernatePropertiesCustomizer,
        ApplicationListener<ContextRefreshedEvent> {

    /**
     * Mientras la aplicación arranca (Spring Data valida sus consultas abriendo sesiones) no hay petición ni
     * empresa: se trabaja en modo raíz. Al terminar el arranque se cierra esa puerta para siempre.
     */
    private volatile boolean arrancando = true;

    /**
     * Empresa con la que se abre la sesión.
     *
     * @return id de la empresa, o la marca de modo raíz
     * @throws IllegalStateException si el hilo no tiene empresa ni modo raíz
     */
    @Override
    public Long resolveCurrentTenantIdentifier() {
        Long id = EmpresaContexto.crudo();
        if (id == null && arrancando) {
            return EmpresaContexto.SIN_EMPRESA;
        }
        if (id == null) {
            throw new IllegalStateException(
                    "No hay empresa en el contexto: fije EmpresaContexto antes de abrir una transacción");
        }
        return id;
    }

    /**
     * Marca el fin del arranque: desde aquí, abrir una sesión sin empresa en el contexto falla.
     *
     * @param evento contexto ya refrescado
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent evento) {
        arrancando = false;
    }

    /**
     * Las sesiones abiertas conservan su empresa aunque el contexto cambie después.
     *
     * @return {@code true}
     */
    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    /**
     * El modo raíz (login, registro público, jobs que recorren empresas) no se filtra por empresa.
     *
     * @param tenantId empresa de la sesión
     * @return {@code true} si es el modo raíz
     */
    @Override
    public boolean isRoot(Long tenantId) {
        return tenantId != null && tenantId == EmpresaContexto.SIN_EMPRESA;
    }

    /**
     * Registra este resolver en Hibernate.
     *
     * @param hibernateProperties propiedades de Hibernate a completar
     */
    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
