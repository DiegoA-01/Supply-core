package com.proyecto.supply_core.inventario.validation;

import com.proyecto.supply_core.inventario.dto.MovimientoRequest;
import com.proyecto.supply_core.inventario.enums.TipoMovimiento;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Implementa {@link MovimientoConsistente}; cada regla se reporta sobre su campo. */
public class MovimientoConsistenteValidator implements ConstraintValidator<MovimientoConsistente, MovimientoRequest> {

    /** Largo mínimo del motivo de un ajuste (que explique algo). */
    static final int MOTIVO_MINIMO = 5;

    /**
     * Valida el request contra las reglas de su tipo.
     *
     * @param req     movimiento recibido
     * @param context contexto de validación
     * @return {@code true} si es consistente o si falta el tipo (eso lo reporta {@code @NotNull})
     */
    @Override
    public boolean isValid(MovimientoRequest req, ConstraintValidatorContext context) {
        if (req == null || req.tipo() == null) {
            return true;
        }
        TipoMovimiento tipo = req.tipo();
        context.disableDefaultConstraintViolation();
        boolean valido = true;

        if (!tipo.esManual()) {
            return error(context, "tipo", tipo + " solo se registra desde la recepción de mercancía");
        }
        if (tipo.usaOrigen() && req.bodegaOrigenId() == null) {
            valido = error(context, "bodegaOrigenId", "es obligatoria para " + tipo);
        }
        if (!tipo.usaOrigen() && req.bodegaOrigenId() != null) {
            valido = error(context, "bodegaOrigenId", "no aplica para " + tipo);
        }
        if (tipo.usaDestino() && req.bodegaDestinoId() == null) {
            valido = error(context, "bodegaDestinoId", "es obligatoria para " + tipo);
        }
        if (!tipo.usaDestino() && req.bodegaDestinoId() != null) {
            valido = error(context, "bodegaDestinoId", "no aplica para " + tipo);
        }
        if (tipo == TipoMovimiento.TRASLADO && req.bodegaOrigenId() != null
                && req.bodegaOrigenId().equals(req.bodegaDestinoId())) {
            valido = error(context, "bodegaDestinoId", "debe ser distinta a la bodega de origen");
        }
        if (tipo.exigeMotivo() && (req.motivo() == null || req.motivo().trim().length() < MOTIVO_MINIMO)) {
            valido = error(context, "motivo", "es obligatorio para " + tipo + " (mínimo " + MOTIVO_MINIMO + " caracteres)");
        }
        return valido;
    }

    /**
     * Agrega un error sobre un campo concreto para que salga como "campo: mensaje".
     *
     * @param context contexto de validación
     * @param campo   nombre del campo afectado
     * @param mensaje texto del error
     * @return siempre {@code false}
     */
    private static boolean error(ConstraintValidatorContext context, String campo, String mensaje) {
        context.buildConstraintViolationWithTemplate(mensaje).addPropertyNode(campo).addConstraintViolation();
        return false;
    }
}
