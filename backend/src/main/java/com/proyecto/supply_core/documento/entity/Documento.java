package com.proyecto.supply_core.documento.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Metadata de un archivo subido (tabla {@code documento}). El archivo vive en el almacenamiento
 * ({@code StorageClient}), nunca en la BD. Inmutable y sin borrado: es evidencia.
 * <p>{@code entidadTipo} + {@code entidadId} es una referencia polimórfica (sin relación JPA).</p>
 */
@Entity
@Immutable
@Table(name = "documento")
@Getter
@Setter
@NoArgsConstructor
public class Documento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nombre original saneado (solo para mostrar y para la descarga). */
    @Column(name = "nombre_archivo", nullable = false, length = 250, updatable = false)
    private String nombreArchivo;

    /** Clave interna en el almacenamiento (generada por el sistema; nunca se expone). */
    @Column(name = "ruta_storage", nullable = false, length = 500, updatable = false)
    private String rutaStorage;

    /** MIME canónico según el tipo detectado (no el que mandó el cliente). */
    @Column(name = "tipo_mime", nullable = false, length = 100, updatable = false)
    private String tipoMime;

    @Column(name = "tamano_bytes", nullable = false, updatable = false)
    private long tamanoBytes;

    @Column(name = "entidad_tipo", nullable = false, length = 50, updatable = false)
    private String entidadTipo;

    @Column(name = "entidad_id", nullable = false, updatable = false)
    private Long entidadId;

    @Column(name = "subido_por_usuario_id", updatable = false)
    private Long subidoPorUsuarioId;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    /** Fija la fecha de subida al insertar. */
    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }
}
