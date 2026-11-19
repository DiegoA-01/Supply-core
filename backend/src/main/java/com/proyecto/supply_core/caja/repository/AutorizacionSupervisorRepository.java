package com.proyecto.supply_core.caja.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.caja.entity.AutorizacionSupervisor;

import jakarta.persistence.LockModeType;

/** Autorizaciones de un solo uso ({@code @TenantId}). */
public interface AutorizacionSupervisorRepository extends JpaRepository<AutorizacionSupervisor, Long> {

    /**
     * @param tokenHash SHA-256 del token
     * @return la autorización bloqueada para consumirla una sola vez
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AutorizacionSupervisor a where a.tokenHash = :tokenHash")
    Optional<AutorizacionSupervisor> bloquearPorToken(@Param("tokenHash") String tokenHash);
}
