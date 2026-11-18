package com.proyecto.supply_core.plataforma.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

class TotpServiceTest {

    /** Secreto de prueba del RFC 6238 ("12345678901234567890") en base32. */
    private static final String SECRETO_RFC = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    private final TotpService totp = new TotpService();

    @Test
    void coincideConLosVectoresDePruebaDelRfc6238() {
        // los vectores del RFC son de 8 dígitos; los 6 últimos son el código de 6
        assertEquals("287082", totp.codigo(SECRETO_RFC, totp.paso(Instant.ofEpochSecond(59))));
        assertEquals("081804", totp.codigo(SECRETO_RFC, totp.paso(Instant.ofEpochSecond(1111111109L))));
        assertEquals("050471", totp.codigo(SECRETO_RFC, totp.paso(Instant.ofEpochSecond(1111111111L))));
        assertEquals("005924", totp.codigo(SECRETO_RFC, totp.paso(Instant.ofEpochSecond(1234567890L))));
        assertEquals("279037", totp.codigo(SECRETO_RFC, totp.paso(Instant.ofEpochSecond(2000000000L))));
    }

    @Test
    void base32VaYVuelveYLeeMinusculasYEspacios() {
        byte[] bytes = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

        assertEquals(SECRETO_RFC, TotpService.codificar(bytes));
        assertArrayEquals(bytes, TotpService.decodificar(SECRETO_RFC));
        assertArrayEquals(bytes, TotpService.decodificar(SECRETO_RFC.toLowerCase().replace("GEZD", "GEZD ")));
        assertThrows(IllegalArgumentException.class, () -> TotpService.decodificar("ESTO-NO-ES-BASE32!"));
    }

    @Test
    void cadaSecretoNuevoEsDistintoYDe32Caracteres() {
        String a = totp.nuevoSecreto();
        String b = totp.nuevoSecreto();

        assertEquals(32, a.length());
        assertNotEquals(a, b);
        assertTrue(a.matches("[A-Z2-7]{32}"));
    }

    @Test
    void laUriTrae_elSecreto_elEmisorYLosParametros() {
        String uri = totp.uri("dueno@supplycore.local", SECRETO_RFC);

        assertTrue(uri.startsWith("otpauth://totp/Supply-Core:dueno@supplycore.local?"));
        assertTrue(uri.contains("secret=" + SECRETO_RFC));
        assertTrue(uri.contains("issuer=Supply-Core"));
        assertTrue(uri.contains("digits=6") && uri.contains("period=30") && uri.contains("algorithm=SHA1"));
    }

    @Test
    void aceptaElPasoActualYUnoAntesYDespuesPeroNoMasLejos() {
        Instant ahora = Instant.ofEpochSecond(1_700_000_010L);
        long paso = totp.paso(ahora);

        assertEquals(OptionalLong.of(paso), totp.verificar(SECRETO_RFC, totp.codigo(SECRETO_RFC, paso), null, ahora));
        assertEquals(OptionalLong.of(paso - 1), totp.verificar(SECRETO_RFC, totp.codigo(SECRETO_RFC, paso - 1), null, ahora));
        assertEquals(OptionalLong.of(paso + 1), totp.verificar(SECRETO_RFC, totp.codigo(SECRETO_RFC, paso + 1), null, ahora));
        assertTrue(totp.verificar(SECRETO_RFC, totp.codigo(SECRETO_RFC, paso - 3), null, ahora).isEmpty());
        assertTrue(totp.verificar(SECRETO_RFC, totp.codigo(SECRETO_RFC, paso + 3), null, ahora).isEmpty());
    }

    @Test
    void unCodigoNoSirveDosVecesNiUnoAnteriorAlUltimoUsado() {
        Instant ahora = Instant.ofEpochSecond(1_700_000_010L);
        long paso = totp.paso(ahora);
        String codigo = totp.codigo(SECRETO_RFC, paso);

        assertTrue(totp.verificar(SECRETO_RFC, codigo, paso, ahora).isEmpty());          // el mismo, ya usado
        assertTrue(totp.verificar(SECRETO_RFC, totp.codigo(SECRETO_RFC, paso - 1), paso, ahora).isEmpty()); // uno anterior
        assertEquals(OptionalLong.of(paso + 1),
                totp.verificar(SECRETO_RFC, totp.codigo(SECRETO_RFC, paso + 1), paso, ahora)); // el siguiente sí
    }

    @Test
    void rechazaCodigosMalFormadosYSecretosVacios() {
        Instant ahora = Instant.now();

        assertTrue(totp.verificar(SECRETO_RFC, null, null, ahora).isEmpty());
        assertTrue(totp.verificar(SECRETO_RFC, "12345", null, ahora).isEmpty());
        assertTrue(totp.verificar(SECRETO_RFC, "abcdef", null, ahora).isEmpty());
        assertTrue(totp.verificar(SECRETO_RFC, "1234567", null, ahora).isEmpty());
        assertTrue(totp.verificar(null, "123456", null, ahora).isEmpty());
    }
}
