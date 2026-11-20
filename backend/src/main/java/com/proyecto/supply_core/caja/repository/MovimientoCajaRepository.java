package com.proyecto.supply_core.caja.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.caja.entity.MovimientoCaja;
import com.proyecto.supply_core.caja.enums.MedioPago;

/** Libro de caja ({@code @TenantId}). */
public interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, Long> {

    /**
     * @param turnoId turno
     * @return movimientos del turno en orden
     */
    List<MovimientoCaja> findByTurnoIdOrderByIdAsc(Long turnoId);

    /**
     * @param turnoId turno
     * @param medio   medio de pago
     * @return suma de los movimientos del turno en ese medio (0 si no hay)
     */
    @Query("select coalesce(sum(m.monto), 0) from MovimientoCaja m where m.turnoId = :turnoId and m.medioPago = :medio")
    BigDecimal saldo(@Param("turnoId") Long turnoId, @Param("medio") MedioPago medio);

    /**
     * @param referenciaTipo documento de origen ({@code VENTA})
     * @param referenciaId   id del documento
     * @return movimientos del documento
     */
    java.util.List<MovimientoCaja> findByReferenciaTipoAndReferenciaId(String referenciaTipo, Long referenciaId);
}
