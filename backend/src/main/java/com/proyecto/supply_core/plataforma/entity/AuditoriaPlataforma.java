package com.proyecto.supply_core.plataforma.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.Immutable;

import com.proyecto.supply_core.plataforma.enums.AccionPlataforma;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Registro de la auditoría global de la plataforma (tabla {@code auditoria_plataforma}). Solo se inserta: no se
 * edita ni se borra.
 */
@Entity
@Immutable
@Table(name = "auditoria_plataforma")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditoriaPlataforma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_plataforma_id", updatable = false)
    private Long usuarioPlataformaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 60)
    private AccionPlataforma accion;

    @Column(name = "empresa_id", updatable = false)
    private Long empresaId;

    /** EXITO o FALLO. */
    @Column(nullable = false, updatable = false, length = 10)
    private String resultado;

    @Column(length = 45, updatable = false)
    private String ip;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String detalle;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    /**
     * Crea un registro listo para insertar.
     *
     * @param usuarioPlataformaId quién actuó ({@code null} si no se identificó)
     * @param accion              qué hizo
     * @param empresaId           empresa afectada ({@code null} si no aplica)
     * @param exito               {@code true} si salió bien
     * @param ip                  dirección de origen
     * @param detalle             texto con los datos relevantes
     */
    public AuditoriaPlataforma(Long usuarioPlataformaId, AccionPlataforma accion, Long empresaId, boolean exito,
            String ip, String detalle) {
        this.usuarioPlataformaId = usuarioPlataformaId;
        this.accion = accion;
        this.empresaId = empresaId;
        this.resultado = exito ? "EXITO" : "FALLO";
        this.ip = ip;
        this.detalle = detalle;
    }

    /** Fija la fecha al insertar. */
    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }
}
