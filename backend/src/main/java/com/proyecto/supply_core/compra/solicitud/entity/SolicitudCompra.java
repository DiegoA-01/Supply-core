package com.proyecto.supply_core.compra.solicitud.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Solicitud de compra (UC-04, tabla {@code solicitud_compra}).
 * <p>Las FK a otros módulos (centro de costo, solicitante, política) se guardan como {@code Long}.
 * Las líneas se reemplazan completas al editar (cascade + orphanRemoval). Nunca se borra:
 * se cancela.</p>
 */
@Entity
@Table(name = "solicitud_compra")
@Getter
@Setter
@NoArgsConstructor
public class SolicitudCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @Column(name = "centro_costo_id", nullable = false)
    private Long centroCostoId;

    @Column(name = "usuario_solicitante_id", nullable = false, updatable = false)
    private Long usuarioSolicitanteId;

    @Column(nullable = false, unique = true, length = 30, updatable = false)
    private String consecutivo;

    /** Justificación de la compra. */
    @Column(length = 500)
    private String motivo;

    @Column(name = "fecha_requerida", nullable = false)
    private LocalDate fechaRequerida;

    /** Suma de cantidad x costo de referencia de las líneas con costo (se calcula al enviar). */
    @Column(name = "monto_estimado", precision = 18, scale = 2)
    private BigDecimal montoEstimado;

    /** {@code true} si alguna línea no tenía costo de referencia (entonces se exige aprobación). */
    @Column(name = "monto_incompleto", nullable = false)
    private boolean montoIncompleto;

    /** Política asignada al enviar; {@code null} si ninguna aplicó (aprobada directa). */
    @Column(name = "politica_aprobacion_id")
    private Long politicaAprobacionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoSolicitud estado = EstadoSolicitud.BORRADOR;

    @Column(name = "enviado_en")
    private LocalDateTime enviadoEn;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @OneToMany(mappedBy = "solicitud", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<SolicitudCompraLinea> lineas = new ArrayList<>();

    /**
     * Agrega una línea enlazándola a esta solicitud.
     *
     * @param linea línea nueva
     */
    public void agregarLinea(SolicitudCompraLinea linea) {
        linea.setSolicitud(this);
        lineas.add(linea);
    }

    /** Fija la fecha de creación al insertar. */
    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }
}
