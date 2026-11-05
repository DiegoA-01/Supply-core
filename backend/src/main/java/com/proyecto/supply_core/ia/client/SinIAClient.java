package com.proyecto.supply_core.ia.client;

import java.util.List;

import com.proyecto.supply_core.ia.dto.ArchivoIA;
import com.proyecto.supply_core.ia.dto.ExtraccionIA;
import com.proyecto.supply_core.ia.dto.LineaContexto;

/**
 * Modo sin IA ({@code app.ia.proveedor=ninguno}): no lee nada. Las cotizaciones por archivo quedan para
 * que el comprador las digite, y la explicación del ranking la arma Java con plantillas.
 */
public class SinIAClient implements IAClient {

    /**
     * Nunca está disponible.
     *
     * @return {@code false}
     */
    @Override
    public boolean disponible() {
        return false;
    }

    /**
     * Nombre informativo.
     *
     * @return {@code "ninguno"}
     */
    @Override
    public String modelo() {
        return "ninguno";
    }

    /**
     * No se debe llamar.
     *
     * @param archivo   documento
     * @param productos líneas de la RFQ
     * @return nunca retorna
     * @throws IAException siempre
     */
    @Override
    public ExtraccionIA extraer(ArchivoIA archivo, List<LineaContexto> productos) {
        throw new IAException("No hay IA configurada (app.ia.proveedor=ninguno)");
    }

    /**
     * No se debe llamar.
     *
     * @param resumen datos del ranking
     * @return nunca retorna
     * @throws IAException siempre
     */
    @Override
    public String explicar(String resumen) {
        throw new IAException("No hay IA configurada (app.ia.proveedor=ninguno)");
    }
}
