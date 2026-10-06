package com.proyecto.supply_core.compra.analisis.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import com.proyecto.supply_core.compra.analisis.entity.ConfigPesos;

/**
 * Versión de pesos.
 *
 * @param id           versión
 * @param vigenteDesde inicio de vigencia
 * @param vigenteHasta fin de vigencia ({@code null} = vigente)
 * @param pesos        peso por criterio
 */
public record PesosResponse(Long id, LocalDateTime vigenteDesde, LocalDateTime vigenteHasta, List<Peso> pesos) {

    /**
     * Convierte la entidad.
     *
     * @param c versión con detalles
     * @return DTO
     */
    public static PesosResponse of(ConfigPesos c) {
        return new PesosResponse(c.getId(), c.getVigenteDesde(), c.getVigenteHasta(), c.getDetalles().stream()
                .map(d -> new Peso(d.getCriterio().getCodigo(), d.getCriterio().getNombre(), d.getPeso()))
                .sorted(Comparator.comparing(Peso::peso).reversed()).toList());
    }

    /**
     * Peso de un criterio.
     *
     * @param codigo código
     * @param nombre nombre
     * @param peso   peso (0–100)
     */
    public record Peso(String codigo, String nombre, BigDecimal peso) {
    }
}
