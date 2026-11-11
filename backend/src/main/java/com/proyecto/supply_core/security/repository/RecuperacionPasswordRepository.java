package com.proyecto.supply_core.security.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.security.entity.RecuperacionPassword;

/** Acceso a la tabla {@code recuperacion_password}. */
public interface RecuperacionPasswordRepository extends JpaRepository<RecuperacionPassword, Long> {

    /**
     * Busca un token por el hash de su valor (solo entre los de la empresa del contexto).
     *
     * @param tokenHash SHA-256 en hexadecimal
     * @return el token, si existe
     */
    Optional<RecuperacionPassword> findByTokenHash(String tokenHash);

    /**
     * Indica si al usuario ya se le pidió un token después del momento dado (límite de frecuencia).
     *
     * @param usuarioId usuario
     * @param desde     momento desde el cual se cuenta
     * @return {@code true} si hay uno creado después de {@code desde}
     */
    boolean existsByUsuarioIdAndCreadoEnAfter(Long usuarioId, LocalDateTime desde);

    /**
     * Deja sin efecto los tokens pendientes del usuario (se pidió uno nuevo o ya cambió la clave).
     *
     * @param usuarioId usuario
     * @param ahora     momento en que se cierran
     * @return filas modificadas
     */
    @Modifying
    @Query("update RecuperacionPassword r set r.usadoEn = :ahora where r.usuarioId = :usuarioId and r.usadoEn is null")
    int cerrarPendientes(@Param("usuarioId") Long usuarioId, @Param("ahora") LocalDateTime ahora);

    /**
     * Borra los tokens vencidos hace más de un día (limpieza periódica, sin empresa en el contexto).
     *
     * @param limite fecha anterior a la cual el token ya no sirve para nada
     * @return filas borradas
     */
    @Modifying
    @Query("delete from RecuperacionPassword r where r.expiraEn < :limite")
    int borrarVencidos(@Param("limite") LocalDateTime limite);
}
