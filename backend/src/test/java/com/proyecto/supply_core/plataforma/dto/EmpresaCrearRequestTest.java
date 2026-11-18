package com.proyecto.supply_core.plataforma.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class EmpresaCrearRequestTest {

    private final Validator validador = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<ConstraintViolation<EmpresaCrearRequest>> validar(String codigo) {
        return validador.validate(new EmpresaCrearRequest("Ferretería Don Martín", "900123456-7", codigo, "BASICO", null,
                "Martín", "martin@donmartin.co", "ClaveSegura1"));
    }

    @Test
    void elCodigoAceptaEspaciosMayusculasTildesYNumeros() {
        for (String codigo : new String[] { "Ferretería Don Martín", "ferreteria don martin", "Tornillos 2000",
                "El Tornillo S.A.S.", "el-tornillo", "ÑANDÚ & Hijos", "Don_Martín (Sede 2)" }) {
            assertTrue(validar(codigo).isEmpty(), "debería aceptar: " + codigo);
        }
    }

    @Test
    void soloSeRechazaLoQueRompeLaDireccionDelRegistroOEstaVacio() {
        for (String codigo : new String[] { "a/b c", "x?y z", "ver #1", "100% real", "con\\barra", "campana\u0007x", "ab", "   " }) {
            assertEquals(false, validar(codigo).isEmpty(), "debería rechazar: " + codigo);
        }
    }

    @Test
    void unCodigoLargoSeRechaza() {
        assertEquals(false, validar("a".repeat(41)).isEmpty());
        assertTrue(validar("a".repeat(40)).isEmpty());
    }
}
