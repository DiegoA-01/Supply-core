package com.proyecto.supply_core.compra.analisis.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.proyecto.supply_core.compra.analisis.entity.Analisis;
import com.proyecto.supply_core.compra.analisis.enums.EstadoAnalisis;

/**
 * Análisis comparativo con el ranking, el detalle por criterio y las alertas.
 *
 * @param id                 análisis
 * @param rfqId              RFQ
 * @param estado             BORRADOR o PUBLICADO
 * @param configPesosId      versión de pesos usada
 * @param explicacionGeneral explicación del ranking redactada por la IA (opcional)
 * @param creadoEn           fecha de generación
 * @param publicadoEn        fecha de publicación
 * @param pendientes         motivos que impiden publicar (vacío = se puede publicar)
 * @param resultados         ranking (posición 1 = recomendada)
 */
public record AnalisisResponse(Long id, Long rfqId, EstadoAnalisis estado, Long configPesosId, String explicacionGeneral,
        LocalDateTime creadoEn, LocalDateTime publicadoEn, List<String> pendientes, List<Resultado> resultados) {

    /**
     * Convierte la entidad (resultados, detalles y anomalías cargados).
     *
     * @param a          análisis
     * @param pendientes motivos que impiden publicar
     * @return DTO
     */
    public static AnalisisResponse of(Analisis a, List<String> pendientes) {
        Map<Long, List<Criterio>> porCotizacion = a.getDetalles().stream().collect(Collectors.groupingBy(
                Analisis.Detalle::getCotizacionId,
                Collectors.mapping(d -> new Criterio(d.getCriterio().getCodigo(), d.getPesoAplicado(), d.getPuntaje(),
                        d.getScorePonderado()), Collectors.toList())));
        List<Resultado> resultados = a.getResultados().stream()
                .sorted(Comparator.comparingInt(Analisis.Resultado::getPosicionRanking))
                .map(r -> new Resultado(r.getCotizacion().getId(), r.getCotizacion().getProveedor().getRazonSocial(),
                        r.getCostoTotal(), r.getScoreFinal(), r.getPosicionRanking(), r.getExplicacion(),
                        porCotizacion.getOrDefault(r.getCotizacion().getId(), List.of()).stream()
                                .sorted(Comparator.comparing(Criterio::peso).reversed()).toList(),
                        r.getAnomalias().stream().map(x -> new Anomalia(x.getTipo(), x.getSeveridad(), x.getDescripcion()))
                                .toList()))
                .toList();
        return new AnalisisResponse(a.getId(), a.getRfq().getId(), a.getEstado(), a.getConfigPesosId(),
                a.getExplicacionGeneral(), a.getCreadoEn(), a.getPublicadoEn(), pendientes, resultados);
    }

    /**
     * Resultado de una cotización.
     *
     * @param cotizacionId cotización
     * @param proveedor    razón social
     * @param costoTotal   total con impuestos
     * @param scoreFinal   score 0–100
     * @param posicion     lugar en el ranking
     * @param explicacion  fortalezas y debilidades
     * @param criterios    detalle por criterio
     * @param anomalias    alertas
     */
    public record Resultado(Long cotizacionId, String proveedor, BigDecimal costoTotal, BigDecimal scoreFinal,
            int posicion, String explicacion, List<Criterio> criterios, List<Anomalia> anomalias) {
    }

    /**
     * Puntaje en un criterio.
     *
     * @param codigo    criterio
     * @param peso      peso aplicado
     * @param puntaje   0–100
     * @param ponderado puntaje × peso / 100
     */
    public record Criterio(String codigo, BigDecimal peso, BigDecimal puntaje, BigDecimal ponderado) {
    }

    /**
     * Alerta.
     *
     * @param tipo        tipo
     * @param severidad   BAJA, MEDIA o ALTA
     * @param descripcion detalle
     */
    public record Anomalia(String tipo, String severidad, String descripcion) {
    }
}
