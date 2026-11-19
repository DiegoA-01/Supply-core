package com.proyecto.supply_core.cliente.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.cliente.enums.TipoDocumento;
import com.proyecto.supply_core.common.exception.NegocioException;

/** Validación y normalización del documento de un cliente. */
class DocumentoClienteTest {

    @Test
    void elDigitoDeVerificacionDelNitSeCalculaComoLaDian() {
        // NIT de la DIAN: 800197268-4; de Bancolombia: 890903938-8
        assertEquals(4, DocumentoCliente.digitoVerificacion("800197268"));
        assertEquals(8, DocumentoCliente.digitoVerificacion("890903938"));
    }

    @Test
    void elNitSeAceptaConOSinGuionYSeGuardaConGuion() {
        assertEquals("800197268-4", DocumentoCliente.normalizar(TipoDocumento.NIT, "800.197.268-4"));
        assertEquals("800197268-4", DocumentoCliente.normalizar(TipoDocumento.NIT, "8001972684"));
        NegocioException e = assertThrows(NegocioException.class,
                () -> DocumentoCliente.normalizar(TipoDocumento.NIT, "800197268-5"));
        assertEquals("DOCUMENTO_INVALIDO", e.getCodigo());
    }

    @Test
    void cadaTipoTieneSuFormato() {
        assertEquals("1020304050", DocumentoCliente.normalizar(TipoDocumento.CC, " 1.020.304.050 "));
        assertThrows(NegocioException.class, () -> DocumentoCliente.normalizar(TipoDocumento.CC, "12A45"));
        assertThrows(NegocioException.class, () -> DocumentoCliente.normalizar(TipoDocumento.CC, "12345678901"));
        assertEquals("123456789012", DocumentoCliente.normalizar(TipoDocumento.CE, "123456789012"));
        assertEquals("AB12345", DocumentoCliente.normalizar(TipoDocumento.PAS, "ab12345"));
        assertThrows(NegocioException.class, () -> DocumentoCliente.normalizar(TipoDocumento.PAS, "AB-12"));
        assertThrows(NegocioException.class, () -> DocumentoCliente.normalizar(null, "123"));
    }

    @Test
    void losComodinesDeLaBusquedaSeBuscanComoTexto() {
        assertEquals("!%", ClienteService.escaparLike("%"));
        assertEquals("a!_b", ClienteService.escaparLike("a_b"));
    }
}
