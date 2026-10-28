package com.proyecto.supply_core.security.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyecto.supply_core.security.entity.RefreshToken;

/** Acceso a la tabla {@code refresh_token}. */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Busca un token por el hash de su valor.
     *
     * @param tokenHash SHA-256 en hexadecimal
     * @return el token, si existe
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revoca todos los tokens vigentes de una sesión.
     *
     * @param familia identificador de la sesión
     * @param ahora   momento de la revocación
     * @return filas modificadas
     */
    @Modifying
    @Query("update RefreshToken t set t.revocadoEn = :ahora where t.familia = :familia and t.revocadoEn is null")
    int revocarFamilia(@Param("familia") String familia, @Param("ahora") LocalDateTime ahora);

    /**
     * Revoca todas las sesiones de un usuario (cerrar sesión en todos los dispositivos).
     *
     * @param usuarioId usuario dueño de las sesiones
     * @param ahora     momento de la revocación
     * @return filas modificadas
     */
    @Modifying
    @Query("update RefreshToken t set t.revocadoEn = :ahora where t.usuarioId = :usuarioId and t.revocadoEn is null")
    int revocarDeUsuario(@Param("usuarioId") Long usuarioId, @Param("ahora") LocalDateTime ahora);

    /**
     * Revoca todas las sesiones de los usuarios de una empresa (se suspendió o canceló su suscripción). Va en SQL
     * nativo porque la plataforma trabaja sin empresa en el contexto y {@code usuario} se filtra por empresa.
     *
     * @param empresaId empresa suspendida
     * @param ahora     momento de la revocación
     * @return filas modificadas
     */
    @Modifying
    @Query(value = "UPDATE refresh_token SET revocado_en = :ahora WHERE revocado_en IS NULL "
            + "AND usuario_id IN (SELECT id FROM usuario WHERE empresa_id = :empresaId)", nativeQuery = true)
    int revocarDeEmpresa(@Param("empresaId") Long empresaId, @Param("ahora") LocalDateTime ahora);

    /**
     * Borra los tokens ya vencidos (limpieza periódica).
     *
     * @param limite fecha anterior a la cual el token ya no sirve para nada
     * @return filas borradas
     */
    @Modifying
    @Query("delete from RefreshToken t where t.expiraEn < :limite")
    int borrarVencidos(@Param("limite") LocalDateTime limite);
}
