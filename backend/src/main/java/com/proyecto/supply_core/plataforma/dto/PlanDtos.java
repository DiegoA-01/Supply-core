package com.proyecto.supply_core.plataforma.dto;

import java.util.List;
import java.util.Set;

import com.proyecto.supply_core.plataforma.entity.Modulo;
import com.proyecto.supply_core.plataforma.entity.Plan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpos y respuestas de planes y módulos.
 */
public final class PlanDtos {

    /** Clase contenedora: no se instancia. */
    private PlanDtos() {
    }

    /**
     * Alta de un plan.
     *
     * @param codigo  código en mayúsculas, números y guion bajo; único
     * @param nombre  nombre comercial
     * @param modulos códigos de los módulos que incluye (al menos uno)
     */
    public record Crear(
            @NotBlank(message = "es obligatorio")
            @Size(max = 30, message = "máximo 30 caracteres")
            @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$", message = "solo letras, números y guion bajo")
            String codigo,

            @NotBlank(message = "es obligatorio")
            @Size(max = 100, message = "máximo 100 caracteres")
            String nombre,

            @NotEmpty(message = "debe incluir al menos un módulo")
            Set<@NotBlank(message = "no puede estar vacío") String> modulos) {
    }

    /**
     * Cambio de un plan. Lo que no se envía no cambia.
     *
     * @param nombre  nombre nuevo
     * @param modulos módulos nuevos (reemplazan a los actuales; afectan a todas las empresas que usan el plan)
     * @param activo  {@code false} para retirarlo del catálogo (solo si ninguna empresa lo usa)
     */
    public record Editar(
            @Size(max = 100, message = "máximo 100 caracteres")
            String nombre,

            Set<@NotBlank(message = "no puede estar vacío") String> modulos,

            Boolean activo) {
    }

    /**
     * Plan con sus módulos.
     *
     * @param id       id
     * @param codigo   código
     * @param nombre   nombre
     * @param activo   si se puede asignar a empresas
     * @param modulos  códigos de módulo, ordenados
     * @param empresas cuántas empresas lo usan
     */
    public record Respuesta(Long id, String codigo, String nombre, boolean activo, List<String> modulos, long empresas) {

        /**
         * Convierte la entidad.
         *
         * @param p        plan
         * @param empresas empresas que lo usan
         * @return DTO
         */
        public static Respuesta of(Plan p, long empresas) {
            return new Respuesta(p.getId(), p.getCodigo(), p.getNombre(), p.isActivo(),
                    p.getModulos().stream().sorted().toList(), empresas);
        }
    }

    /**
     * Módulo del catálogo.
     *
     * @param codigo      código
     * @param nombre      nombre
     * @param descripcion descripción
     */
    public record ModuloRespuesta(String codigo, String nombre, String descripcion) {

        /**
         * Convierte la entidad.
         *
         * @param m módulo
         * @return DTO
         */
        public static ModuloRespuesta of(Modulo m) {
            return new ModuloRespuesta(m.getCodigo(), m.getNombre(), m.getDescripcion());
        }
    }
}
