package com.proyecto.supply_core.informe.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.proyecto.supply_core.informe.enums.EstadoInforme;

/**
 * Informe de compras. En la lista {@code contenido} y {@code analisis} van vacíos; el detalle los trae completos.
 *
 * @param id               id
 * @param estado           PEDIDO o ENVIADO
 * @param desde            primer día del periodo
 * @param hasta            último día del periodo
 * @param mensajePedido    lo que escribió el administrador al pedirlo
 * @param pedidoPor        quién lo pidió
 * @param pedidoEn         cuándo lo pidió
 * @param enviadoPor       quién lo envió
 * @param enviadoEn        cuándo se envió
 * @param leidoEn          primera vez que el administrador lo abrió
 * @param analisis         análisis del área de compras (solo en el detalle)
 * @param contenido        foto fija del periodo (solo en el detalle)
 */
public record InformeResponse(Long id, EstadoInforme estado, LocalDate desde, LocalDate hasta, String mensajePedido,
        String pedidoPor, LocalDateTime pedidoEn, String enviadoPor, LocalDateTime enviadoEn, LocalDateTime leidoEn,
        String analisis, InformeContenido contenido) {
}
