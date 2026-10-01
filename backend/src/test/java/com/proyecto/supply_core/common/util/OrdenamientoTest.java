package com.proyecto.supply_core.common.util;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.proyecto.supply_core.common.exception.ParametroInvalidoException;

class OrdenamientoTest {

    private static final Set<String> PERMITIDOS = Set.of("nombreCompleto", "correo");

    @Test
    void aceptaCamposPermitidosYSinOrden() {
        Pageable conOrden = PageRequest.of(0, 20, Sort.by("correo").descending());
        assertSame(conOrden, Ordenamiento.validar(conOrden, PERMITIDOS));
        Pageable sinOrden = PageRequest.of(0, 20);
        assertSame(sinOrden, Ordenamiento.validar(sinOrden, PERMITIDOS));
    }

    @Test
    void rechazaCampoNoPermitido() {
        assertThrows(ParametroInvalidoException.class,
                () -> Ordenamiento.validar(PageRequest.of(0, 20, Sort.by("hashPassword")), PERMITIDOS));
    }
}
