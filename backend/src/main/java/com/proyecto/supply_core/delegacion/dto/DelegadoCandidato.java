package com.proyecto.supply_core.delegacion.dto;

/**
 * Usuario en quien se puede delegar una aprobación: solo su id y su nombre, nada más del usuario.
 *
 * @param id     id del usuario
 * @param nombre nombre completo
 */
public record DelegadoCandidato(Long id, String nombre) {
}
