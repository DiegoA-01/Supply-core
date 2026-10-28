package com.proyecto.supply_core.plataforma.service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.plataforma.entity.EmpresaModulo;
import com.proyecto.supply_core.plataforma.entity.Suscripcion;
import com.proyecto.supply_core.plataforma.enums.EstadoSuscripcion;
import com.proyecto.supply_core.plataforma.repository.EmpresaModuloRepository;
import com.proyecto.supply_core.plataforma.repository.SuscripcionRepository;

/**
 * Lo que una empresa puede usar hoy: el estado de su suscripción y sus módulos (los de su plan más o menos sus
 * excepciones). Lo consulta el interceptor en cada petición de una empresa, por eso el resultado se recuerda unos
 * segundos ({@code app.plataforma.cache-segundos}). Cuando la plataforma cambia algo en esta misma instancia, la
 * memoria se limpia al instante; con varias instancias el cambio llega al vencer el tiempo.
 * <ul>
 *   <li>SUSPENDIDA o CANCELADA: 403 {@code SUSCRIPCION_SUSPENDIDA} en todo.</li>
 *   <li>MOROSA: 403 {@code SUSCRIPCION_MOROSA} en todo lo que no sea consultar.</li>
 *   <li>Módulo fuera del plan: 403 {@code MODULO_NO_CONTRATADO}.</li>
 *   <li>Una empresa sin suscripción no entra: es un dato incompleto, no una empresa gratis.</li>
 * </ul>
 */
@Service
public class AccesoEmpresaService {

    private static final Set<String> METODOS_DE_LECTURA = Set.of("GET", "HEAD", "OPTIONS");

    private final SuscripcionRepository suscripcionRepository;
    private final EmpresaModuloRepository empresaModuloRepository;
    private final long vigenciaMs;
    private final Map<Long, Entrada> memoria = new ConcurrentHashMap<>();

    /**
     * Lo recordado de una empresa.
     *
     * @param estado   estado de la suscripción ({@code null} si no tiene)
     * @param modulos  módulos que puede usar
     * @param vence    momento (ms) hasta el que vale lo recordado
     * @param plan     código del plan ({@code null} si no tiene suscripción)
     */
    private record Entrada(EstadoSuscripcion estado, Set<String> modulos, long vence, String plan) {
    }

    /**
     * Resumen de lo que puede usar una empresa.
     *
     * @param estado  estado de la suscripción ({@code null} si no tiene)
     * @param plan    código del plan ({@code null} si no tiene)
     * @param modulos módulos activos
     */
    public record Resumen(EstadoSuscripcion estado, String plan, Set<String> modulos) {
    }

    /**
     * @param suscripcionRepository   suscripciones
     * @param empresaModuloRepository excepciones de módulo por empresa
     * @param cacheSegundos           segundos que se recuerda el acceso de una empresa
     */
    public AccesoEmpresaService(SuscripcionRepository suscripcionRepository,
            EmpresaModuloRepository empresaModuloRepository,
            @Value("${app.plataforma.cache-segundos:30}") long cacheSegundos) {
        this.suscripcionRepository = suscripcionRepository;
        this.empresaModuloRepository = empresaModuloRepository;
        this.vigenciaMs = cacheSegundos * 1000;
    }

    /**
     * Exige que la suscripción permita la petición: lectura y escritura en PRUEBA y ACTIVA, solo lectura en MOROSA y
     * nada en SUSPENDIDA y CANCELADA.
     *
     * @param empresaId  empresa del usuario
     * @param metodoHttp método de la petición (GET, POST...)
     * @throws AccesoDenegadoException si la suscripción no permite esa petición
     */
    public void exigirSuscripcion(Long empresaId, String metodoHttp) {
        Entrada e = entrada(empresaId);
        exigirPuedeEntrar(e);
        if (!e.estado().permiteEscritura() && !METODOS_DE_LECTURA.contains(metodoHttp.toUpperCase())) {
            throw new AccesoDenegadoException("SUSCRIPCION_MOROSA",
                    "La suscripción tiene un pago pendiente: por ahora solo puede consultar");
        }
    }

    /**
     * Exige que la empresa pueda entrar (login y renovación de sesión).
     *
     * @param empresaId empresa del usuario
     * @throws AccesoDenegadoException si la suscripción está suspendida o cancelada, o no existe
     */
    public void exigirPuedeEntrar(Long empresaId) {
        exigirPuedeEntrar(entrada(empresaId));
    }

    /**
     * Exige que el módulo esté activo para la empresa.
     *
     * @param empresaId empresa del usuario
     * @param modulo    código de módulo (constante de {@code Modulos})
     * @throws AccesoDenegadoException si el módulo no está en su plan ni en sus excepciones
     */
    public void exigirModulo(Long empresaId, String modulo) {
        if (!entrada(empresaId).modulos().contains(modulo)) {
            throw new AccesoDenegadoException("MODULO_NO_CONTRATADO",
                    "Su empresa no tiene contratado el módulo " + modulo);
        }
    }

    /**
     * Estado, plan y módulos de una empresa.
     *
     * @param empresaId empresa
     * @return resumen de acceso
     */
    public Resumen resumen(Long empresaId) {
        Entrada e = entrada(empresaId);
        return new Resumen(e.estado(), e.plan(), e.modulos());
    }

    /**
     * Olvida lo recordado de una empresa (la plataforma cambió su plan, sus módulos o su estado).
     *
     * @param empresaId empresa
     */
    public void invalidar(Long empresaId) {
        memoria.remove(empresaId);
    }

    /** Olvida lo recordado de todas las empresas (cambió un plan que usan varias). */
    public void invalidarTodo() {
        memoria.clear();
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Falla si la suscripción no permite entrar.
     *
     * @param e acceso recordado
     */
    private static void exigirPuedeEntrar(Entrada e) {
        if (e.estado() == null) {
            throw new AccesoDenegadoException("SUSCRIPCION_SUSPENDIDA",
                    "La empresa no tiene una suscripción activa; contacte a soporte");
        }
        if (!e.estado().permiteAcceso()) {
            throw new AccesoDenegadoException("SUSCRIPCION_SUSPENDIDA", e.estado() == EstadoSuscripcion.CANCELADA
                    ? "La suscripción de la empresa fue cancelada" : "La suscripción de la empresa está suspendida; "
                            + "contacte a soporte");
        }
    }

    /**
     * Acceso de la empresa, de la memoria o de la base si venció.
     *
     * @param empresaId empresa
     * @return acceso vigente
     */
    private Entrada entrada(Long empresaId) {
        long ahora = System.currentTimeMillis();
        Entrada guardada = memoria.get(empresaId);
        if (guardada != null && guardada.vence() > ahora) {
            return guardada;
        }
        Entrada nueva = calcular(empresaId, ahora + vigenciaMs);
        memoria.put(empresaId, nueva);
        return nueva;
    }

    /**
     * Calcula el acceso desde la base.
     *
     * @param empresaId empresa
     * @param vence     momento (ms) hasta el que valdrá
     * @return acceso calculado
     */
    Entrada calcular(Long empresaId, long vence) {
        Suscripcion s = suscripcionRepository.findByEmpresaId(empresaId).orElse(null);
        if (s == null) {
            return new Entrada(null, Set.of(), vence, null);
        }
        Set<String> modulos = new HashSet<>(s.getPlan().getModulos());
        for (EmpresaModulo ex : empresaModuloRepository.findByEmpresaId(empresaId)) {
            if (ex.isHabilitado()) {
                modulos.add(ex.getModuloCodigo());
            } else {
                modulos.remove(ex.getModuloCodigo());
            }
        }
        return new Entrada(s.getEstado(), Set.copyOf(modulos), vence, s.getPlan().getCodigo());
    }
}
