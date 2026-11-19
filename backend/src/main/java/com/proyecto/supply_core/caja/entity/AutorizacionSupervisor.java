package com.proyecto.supply_core.caja.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.TenantId;

import com.proyecto.supply_core.caja.enums.AccionAutorizada;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Permiso de un solo uso que da un supervisor con su PIN (tabla {@code autorizacion_supervisor}, V33): vale para una
 * acción, de un turno y de quien lo pidió, durante 2 minutos. Solo se guarda el hash del token.
 */
@Entity
@Table(name = "autorizacion_supervisor")
@Getter
@Setter
@NoArgsConstructor
public class AutorizacionSupervisor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @Column(name = "supervisor_usuario_id", nullable = false, updatable = false)
    private Long supervisorUsuarioId;

    @Column(name = "solicitante_usuario_id", nullable = false, updatable = false)
    private Long solicitanteUsuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private AccionAutorizada accion;

    @Column(name = "turno_id", nullable = false, updatable = false)
    private Long turnoId;

    /** Venta u otro registro al que se liga el permiso; {@code null} si la acción no tiene referencia. */
    @Column(name = "referencia_id", updatable = false)
    private Long referenciaId;

    /** SHA-256 en hexadecimal del token que recibe la pantalla. */
    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "vence_en", nullable = false, updatable = false)
    private LocalDateTime venceEn;

    @Column(name = "usada_en")
    private LocalDateTime usadaEn;
}
