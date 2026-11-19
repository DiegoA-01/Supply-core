package com.proyecto.supply_core.cliente.service;

import java.util.Locale;

import com.proyecto.supply_core.cliente.enums.TipoDocumento;
import com.proyecto.supply_core.common.exception.NegocioException;

/**
 * Normaliza y valida el número de documento de un cliente según su tipo (spec 002). Quita puntos y espacios; el NIT
 * se guarda con guion y su dígito de verificación, calculado con el método de la DIAN.
 */
public final class DocumentoCliente {

    /** Pesos de la DIAN para el dígito de verificación, del dígito más a la derecha hacia la izquierda. */
    private static final int[] PESOS = { 3, 7, 13, 17, 19, 23, 29, 37, 41, 43, 47, 53, 59, 67, 71 };

    /** No se instancia: solo métodos estáticos. */
    private DocumentoCliente() {
    }

    /**
     * @param tipo   tipo de documento
     * @param numero número como lo escribió la persona
     * @return número normalizado (sin puntos ni espacios; el NIT con guion y dígito de verificación)
     * @throws NegocioException {@code DOCUMENTO_INVALIDO} si no cumple el formato del tipo o el dígito no coincide
     */
    public static String normalizar(TipoDocumento tipo, String numero) {
        if (tipo == null || numero == null) {
            throw invalido("Indique el tipo y el número de documento");
        }
        String n = numero.replace(".", "").replace(" ", "").trim().toUpperCase(Locale.ROOT);
        return switch (tipo) {
            case CC -> digitos(n, 4, 10, "La cédula de ciudadanía lleva de 4 a 10 dígitos");
            case CE -> digitos(n, 4, 12, "La cédula de extranjería lleva de 4 a 12 dígitos");
            case PAS -> {
                if (!n.matches("[A-Z0-9]{5,20}")) {
                    throw invalido("El pasaporte lleva de 5 a 20 letras o números");
                }
                yield n;
            }
            case NIT -> nit(n);
        };
    }

    /**
     * Dígito de verificación de un NIT según la DIAN.
     *
     * @param base NIT sin el dígito de verificación (solo dígitos)
     * @return dígito de 0 a 9
     */
    public static int digitoVerificacion(String base) {
        int suma = 0;
        for (int i = 0; i < base.length(); i++) {
            int digito = base.charAt(base.length() - 1 - i) - '0';
            suma += digito * PESOS[i];
        }
        int residuo = suma % 11;
        return residuo > 1 ? 11 - residuo : residuo;
    }

    /**
     * @param n NIT con o sin guion, con su dígito de verificación al final
     * @return NIT normalizado {@code base-dv}
     */
    private static String nit(String n) {
        String sinGuion = n.replace("-", "");
        if (!sinGuion.matches("\\d{7,11}")) {
            throw invalido("El NIT lleva de 6 a 10 dígitos más el dígito de verificación, por ejemplo 900123456-7");
        }
        String base = sinGuion.substring(0, sinGuion.length() - 1);
        int dv = sinGuion.charAt(sinGuion.length() - 1) - '0';
        if (digitoVerificacion(base) != dv) {
            throw invalido("El dígito de verificación del NIT " + base + " no es " + dv);
        }
        return base + "-" + dv;
    }

    /**
     * @param n       número
     * @param minimo  dígitos mínimos
     * @param maximo  dígitos máximos
     * @param mensaje error si no cumple
     * @return el mismo número
     */
    private static String digitos(String n, int minimo, int maximo, String mensaje) {
        if (!n.matches("\\d{" + minimo + "," + maximo + "}")) {
            throw invalido(mensaje);
        }
        return n;
    }

    /**
     * @param mensaje explicación para la persona
     * @return excepción de negocio
     */
    private static NegocioException invalido(String mensaje) {
        return new NegocioException("DOCUMENTO_INVALIDO", mensaje);
    }
}
