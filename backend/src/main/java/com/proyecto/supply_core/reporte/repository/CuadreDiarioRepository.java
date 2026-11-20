package com.proyecto.supply_core.reporte.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.reporte.entity.CuadreDiario;

/** Resultados del cuadre diario de la empresa de la sesión (spec 009). */
public interface CuadreDiarioRepository extends JpaRepository<CuadreDiario, Long> {

    /**
     * @param fecha día revisado
     * @return su resultado, si ya se revisó
     */
    Optional<CuadreDiario> findByFecha(LocalDate fecha);

    /**
     * @param pageable página y orden
     * @return todos los días revisados
     */
    Page<CuadreDiario> findAllBy(Pageable pageable);

    /**
     * @param cuadra   {@code false} para ver solo los descuadres
     * @param pageable página y orden
     * @return días según si cuadran
     */
    Page<CuadreDiario> findByCuadra(boolean cuadra, Pageable pageable);
}
