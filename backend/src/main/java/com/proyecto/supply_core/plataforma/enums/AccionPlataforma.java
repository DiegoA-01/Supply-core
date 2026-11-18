package com.proyecto.supply_core.plataforma.enums;

/** Acciones que quedan en la auditoría global de la plataforma ({@code auditoria_plataforma.accion}). */
public enum AccionPlataforma {
    /** Contraseña verificada y desafío de MFA entregado (EXITO o FALLO). */
    LOGIN,
    /** Código de MFA verificado: sesión de plataforma emitida (EXITO o FALLO). */
    MFA,
    /** Primer código válido: el usuario dejó configurado su MFA. */
    MFA_CONFIGURAR,
    /** Alta de una empresa con su administrador inicial. */
    EMPRESA_CREAR,
    /** Cambio de plan de una empresa. */
    EMPRESA_CAMBIAR_PLAN,
    /** Excepción de módulo para una empresa. */
    EMPRESA_MODULO,
    /** Cambio de estado de la suscripción de una empresa. */
    EMPRESA_ESTADO,
    /** Alta de un plan. */
    PLAN_CREAR,
    /** Cambio de los módulos o del nombre de un plan. */
    PLAN_EDITAR
}
