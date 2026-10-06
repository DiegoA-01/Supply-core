package com.proyecto.supply_core.ia.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un dato leído por la IA con su confianza (tabla {@code extraccion_campo}). Si es crítico y la confianza
 * está bajo el umbral, una persona lo confirma o corrige ({@code valorConfirmado}) antes de publicar.
 */
@Entity
@Table(name = "extraccion_campo")
@Getter
@Setter
@NoArgsConstructor
public class ExtraccionCampo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "procesamiento_ia_id", nullable = false, updatable = false)
    private ProcesamientoIa procesamiento;

    /** Línea de la cotización a la que pertenece; {@code null} = dato de cabecera. */
    @Column(name = "cotizacion_linea_id")
    private Long cotizacionLineaId;

    /** Nombre del dato: cantidad, factorConversion, precioUnitario, plazoEntregaDias... */
    @Column(nullable = false, length = 50)
    private String campo;

    @Column(name = "valor_extraido", length = 500)
    private String valorExtraido;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal confianza;

    @Column(name = "es_critico", nullable = false)
    private boolean critico;

    @Column(name = "valor_confirmado", length = 500)
    private String valorConfirmado;

    @Column(name = "confirmado_por_usuario_id")
    private Long confirmadoPorUsuarioId;

    @Column(name = "confirmado_en")
    private LocalDateTime confirmadoEn;

    /**
     * Indica si bloquea la publicación: crítico, confianza bajo el umbral y aún sin confirmar.
     *
     * @param umbral confianza mínima
     * @return {@code true} si falta revisión humana
     */
    public boolean pendiente(BigDecimal umbral) {
        return critico && valorConfirmado == null && confianza.compareTo(umbral) < 0;
    }
}
