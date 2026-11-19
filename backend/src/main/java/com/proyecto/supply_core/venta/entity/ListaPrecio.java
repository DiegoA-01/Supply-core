package com.proyecto.supply_core.venta.entity;

import org.hibernate.annotations.TenantId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Lista de precios de venta (tabla {@code lista_precio}): "General" es obligatoria; otras para mayoristas, talleres… */
@Entity
@Table(name = "lista_precio")
@Getter
@Setter
@NoArgsConstructor
public class ListaPrecio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Empresa dueña del registro. La asigna Hibernate al guardar y filtra toda consulta ({@code @TenantId}). */
    @TenantId
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Long empresaId;

    @Column(nullable = false, length = 100)
    private String nombre;

    /** La lista General no se puede desactivar. */
    @Column(name = "es_general", nullable = false, updatable = false)
    private boolean esGeneral;

    @Column(nullable = false)
    private boolean activo = true;
}
