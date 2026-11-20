package com.proyecto.supply_core.cartera.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyecto.supply_core.cartera.entity.AbonoCliente;

/** Abonos de la empresa del contexto ({@code @TenantId}). */
public interface AbonoClienteRepository extends JpaRepository<AbonoCliente, Long> {

    /**
     * @param clienteId cliente
     * @return sus abonos, los más recientes primero
     */
    List<AbonoCliente> findByClienteIdOrderByIdDesc(Long clienteId);
}
