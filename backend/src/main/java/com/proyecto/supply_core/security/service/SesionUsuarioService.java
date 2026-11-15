package com.proyecto.supply_core.security.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.proyecto.supply_core.common.exception.NoAutenticadoException;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.EstadoSesion;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

/**
 * Decide si el JWT de un usuario sigue valiendo y corta sus sesiones cuando hace falta.
 * <p>Cada JWT lleva la versión de sesión del usuario. Al cambiar la clave o desactivar la cuenta la versión sube y
 * se revocan sus refresh tokens: el JWT ya emitido deja de servir en la siguiente petición (con varias instancias,
 * al vencer la memoria de {@code app.sesion.cache-segundos}), en vez de esperar sus 15 minutos.</p>
 */
@Service
public class SesionUsuarioService {

    /** Mensaje único: no se explica si fue por cambio de clave o por desactivación. */
    static final String SESION_CERRADA = "La sesión ya no es válida, inicie sesión de nuevo";

    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenService refreshTokenService;
    private final long vigenciaMs;
    private final Map<Long, Entrada> memoria = new ConcurrentHashMap<>();

    /**
     * Estado recordado de un usuario.
     *
     * @param estado estado leído de la base ({@code null} si el usuario ya no existe)
     * @param vence  momento (ms) hasta el que vale lo recordado
     */
    private record Entrada(EstadoSesion estado, long vence) {
    }

    /**
     * @param usuarioRepository   consulta del estado de sesión
     * @param refreshTokenService revocación de las sesiones renovables
     * @param cacheSegundos       segundos que se recuerda el estado de un usuario (0 = siempre se consulta)
     */
    public SesionUsuarioService(UsuarioRepository usuarioRepository, RefreshTokenService refreshTokenService,
            @Value("${app.sesion.cache-segundos:15}") long cacheSegundos) {
        this.usuarioRepository = usuarioRepository;
        this.refreshTokenService = refreshTokenService;
        this.vigenciaMs = cacheSegundos * 1000;
    }

    /**
     * Exige que el token siga vigente: usuario existente, activo y con la misma versión de sesión.
     * Debe llamarse con la empresa del token ya fijada en el contexto.
     *
     * @param usuarioId     dueño del token
     * @param versionToken  versión de sesión con la que se emitió
     * @throws NoAutenticadoException si la cuenta se desactivó o la clave cambió después de emitir el token
     */
    public void exigirVigente(Long usuarioId, int versionToken) {
        EstadoSesion estado = estado(usuarioId);
        if (estado == null || !estado.activo() || estado.versionSesion() != versionToken) {
            throw new NoAutenticadoException(SESION_CERRADA);
        }
    }

    /**
     * Corta todas las sesiones del usuario: sube su versión (invalida los JWT en curso) y revoca sus refresh tokens.
     * Se llama dentro de la transacción que cambia la clave o desactiva la cuenta, con la entidad ya cargada.
     *
     * @param usuario usuario cuyas sesiones se cortan
     */
    public void revocarSesiones(Usuario usuario) {
        usuario.setVersionSesion(usuario.getVersionSesion() + 1);
        refreshTokenService.cerrarTodas(usuario.getId());
        memoria.remove(usuario.getId());
    }

    /**
     * Olvida lo recordado de un usuario.
     *
     * @param usuarioId usuario
     */
    public void olvidar(Long usuarioId) {
        memoria.remove(usuarioId);
    }

    /**
     * Estado del usuario, de la memoria o de la base si venció.
     *
     * @param usuarioId usuario
     * @return estado actual ({@code null} si no existe)
     */
    private EstadoSesion estado(Long usuarioId) {
        long ahora = System.currentTimeMillis();
        Entrada guardada = memoria.get(usuarioId);
        if (guardada != null && guardada.vence() > ahora) {
            return guardada.estado();
        }
        EstadoSesion leido = usuarioRepository.estadoSesion(usuarioId).orElse(null);
        if (vigenciaMs > 0) {
            memoria.put(usuarioId, new Entrada(leido, ahora + vigenciaMs));
        }
        return leido;
    }
}
