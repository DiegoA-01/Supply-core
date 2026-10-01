package com.proyecto.supply_core.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ConsecutivoGeneratorTest {

    @Test
    void formateaConSeisDigitos() {
        assertEquals("SOL-000001", ConsecutivoGenerator.formatear("SOL", 1));
        assertEquals("OC-123456", ConsecutivoGenerator.formatear("OC", 123456));
    }
}
