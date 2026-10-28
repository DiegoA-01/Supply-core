package com.proyecto.supply_core.plataforma.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.OptionalLong;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

/**
 * Códigos de un solo uso por tiempo (TOTP, RFC 6238) para el MFA de la plataforma: HMAC-SHA1, 6 dígitos y pasos
 * de 30 segundos, compatible con Google Authenticator, Microsoft Authenticator y similares.
 * <p>Se acepta el paso actual y uno antes o después (desfase de reloj), pero nunca un paso igual o anterior al último
 * ya usado: un código sirve una sola vez.</p>
 */
@Service
public class TotpService {

    /** Duración de cada código. */
    static final int PASO_SEGUNDOS = 30;
    /** Cantidad de dígitos del código. */
    static final int DIGITOS = 6;
    /** Pasos de tolerancia antes y después del actual. */
    static final int VENTANA = 1;
    private static final String ALFABETO = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int BYTES_SECRETO = 20;
    private static final String EMISOR = "Supply-Core";

    private final SecureRandom azar = new SecureRandom();

    /**
     * Genera un secreto nuevo de 160 bits.
     *
     * @return secreto en base32 (32 caracteres)
     */
    public String nuevoSecreto() {
        byte[] bytes = new byte[BYTES_SECRETO];
        azar.nextBytes(bytes);
        return codificar(bytes);
    }

    /**
     * Dirección {@code otpauth://} que la app de autenticación lee desde un código QR para registrar la cuenta.
     *
     * @param cuenta  correo del usuario
     * @param secreto secreto en base32
     * @return URI de configuración
     */
    public String uri(String cuenta, String secreto) {
        return "otpauth://totp/" + UriUtils.encodePath(EMISOR + ":" + cuenta, StandardCharsets.UTF_8) + "?secret=" + secreto
                + "&issuer=" + EMISOR + "&algorithm=SHA1&digits=" + DIGITOS + "&period=" + PASO_SEGUNDOS;
    }

    /**
     * Número del intervalo de 30 s que contiene el instante.
     *
     * @param instante momento
     * @return paso, contado desde 1970
     */
    public long paso(Instant instante) {
        return instante.getEpochSecond() / PASO_SEGUNDOS;
    }

    /**
     * Calcula el código de un paso.
     *
     * @param secreto secreto en base32
     * @param paso    intervalo de 30 s
     * @return código de 6 dígitos con ceros a la izquierda
     */
    public String codigo(String secreto, long paso) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodificar(secreto), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(paso).array());
            int desplazamiento = hash[hash.length - 1] & 0x0F;
            int truncado = ((hash[desplazamiento] & 0x7F) << 24) | ((hash[desplazamiento + 1] & 0xFF) << 16)
                    | ((hash[desplazamiento + 2] & 0xFF) << 8) | (hash[desplazamiento + 3] & 0xFF);
            int modulo = 1;
            for (int i = 0; i < DIGITOS; i++) {
                modulo *= 10;
            }
            return String.format("%0" + DIGITOS + "d", truncado % modulo);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo calcular el código TOTP", e);
        }
    }

    /**
     * Verifica un código contra el secreto.
     *
     * @param secreto    secreto en base32
     * @param codigo     código escrito por la persona
     * @param ultimoPaso último paso ya aceptado ({@code null} si nunca se usó)
     * @param ahora      momento de la verificación
     * @return el paso al que corresponde el código, o vacío si es inválido o ya se usó
     */
    public OptionalLong verificar(String secreto, String codigo, Long ultimoPaso, Instant ahora) {
        if (secreto == null || codigo == null || !codigo.matches("\\d{" + DIGITOS + "}")) {
            return OptionalLong.empty();
        }
        long actual = paso(ahora);
        for (long p = actual - VENTANA; p <= actual + VENTANA; p++) {
            if (ultimoPaso != null && p <= ultimoPaso) {
                continue;
            }
            if (MessageDigest.isEqual(codigo(secreto, p).getBytes(StandardCharsets.UTF_8),
                    codigo.getBytes(StandardCharsets.UTF_8))) {
                return OptionalLong.of(p);
            }
        }
        return OptionalLong.empty();
    }

    // ----------------------------------------------------------------- base32 (RFC 4648, sin relleno)

    /**
     * Codifica bytes en base32.
     *
     * @param bytes datos
     * @return texto en mayúsculas
     */
    static String codificar(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sb.append(ALFABETO.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(ALFABETO.charAt((buffer << (5 - bits)) & 31));
        }
        return sb.toString();
    }

    /**
     * Decodifica base32 (sin distinguir mayúsculas, ignorando espacios y relleno).
     *
     * @param texto secreto en base32
     * @return bytes
     * @throws IllegalArgumentException si el texto tiene un carácter fuera del alfabeto
     */
    static byte[] decodificar(String texto) {
        String limpio = texto.replace(" ", "").replace("=", "").toUpperCase(Locale.ROOT);
        java.io.ByteArrayOutputStream salida = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : limpio.toCharArray()) {
            int valor = ALFABETO.indexOf(c);
            if (valor < 0) {
                throw new IllegalArgumentException("Secreto base32 inválido");
            }
            buffer = (buffer << 5) | valor;
            bits += 5;
            if (bits >= 8) {
                salida.write((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return salida.toByteArray();
    }
}
