package com.proyecto.supply_core.proveedor.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.util.Ordenamiento;
import com.proyecto.supply_core.proveedor.dto.EvaluacionResponse;
import com.proyecto.supply_core.proveedor.dto.IndicadoresEvaluacion;
import com.proyecto.supply_core.proveedor.entity.EvaluacionProveedor;
import com.proyecto.supply_core.proveedor.entity.Proveedor;
import com.proyecto.supply_core.proveedor.repository.EvaluacionProveedorRepository;
import com.proyecto.supply_core.proveedor.repository.ProveedorRepository;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

/**
 * UC-14 Evaluar proveedor.
 * <p>La evaluación la dispara el sistema al cerrar la recepción de una orden (paso 17) con
 * {@link #registrar}: guarda los indicadores (sin datos = {@code null}), calcula el score como
 * promedio de los que tienen datos y recalcula {@code proveedor.score_actual} como promedio de
 * todas sus evaluaciones con score. No hay endpoint para crear evaluaciones a mano.</p>
 */
@Service
public class EvaluacionProveedorService {

    private static final Logger log = LoggerFactory.getLogger(EvaluacionProveedorService.class);
    /** Campos por los que se puede ordenar el listado. */
    private static final Set<String> CAMPOS_ORDENABLES = Set.of("creadoEn", "score");

    private final EvaluacionProveedorRepository evaluacionRepository;
    private final ProveedorRepository proveedorRepository;

    /**
     * @param evaluacionRepository evaluaciones
     * @param proveedorRepository  proveedores (para actualizar el score)
     */
    public EvaluacionProveedorService(EvaluacionProveedorRepository evaluacionRepository,
            ProveedorRepository proveedorRepository) {
        this.evaluacionRepository = evaluacionRepository;
        this.proveedorRepository = proveedorRepository;
    }

    /**
     * Registra la evaluación de una orden y actualiza el score del proveedor.
     * Se une a la transacción de quien llama (cierre de recepción).
     *
     * @param proveedorId   proveedor evaluado
     * @param ordenCompraId orden evaluada
     * @param indicadores   indicadores 0–100 ({@code null} = sin datos)
     * @return evaluación guardada
     * @throws NoEncontradoException si el proveedor no existe
     * @throws NegocioException      si la orden ya fue evaluada (EVALUACION_DUPLICADA)
     */
    @Transactional
    public EvaluacionResponse registrar(Long proveedorId, Long ordenCompraId, IndicadoresEvaluacion indicadores) {
        Proveedor proveedor = proveedorRepository.findById(proveedorId)
                .orElseThrow(() -> new NoEncontradoException("Proveedor", proveedorId));
        if (evaluacionRepository.existsByProveedorIdAndOrdenCompraId(proveedorId, ordenCompraId)) {
            throw new NegocioException("EVALUACION_DUPLICADA",
                    "La orden " + ordenCompraId + " ya fue evaluada para este proveedor");
        }
        EvaluacionProveedor guardada = evaluacionRepository.saveAndFlush(
                new EvaluacionProveedor(proveedorId, ordenCompraId, indicadores, indicadores.score()));

        Double promedio = evaluacionRepository.promedioScore(proveedorId);
        proveedor.setScoreActual(promedio == null ? null : BigDecimal.valueOf(promedio).setScale(2, RoundingMode.HALF_UP));
        log.info("Proveedor {} evaluado en la orden {}: score {}, score actual {}", proveedorId, ordenCompraId,
                guardada.getScore(), proveedor.getScoreActual());
        return EvaluacionResponse.of(guardada);
    }

    /**
     * Evaluaciones de un proveedor (uso interno de compras y auditoría).
     *
     * @param proveedorId proveedor
     * @param pageable    página y orden
     * @return página de evaluaciones
     * @throws NoEncontradoException si el proveedor no existe
     */
    @Transactional(readOnly = true)
    public PageResponse<EvaluacionResponse> listar(Long proveedorId, Pageable pageable) {
        if (!proveedorRepository.existsById(proveedorId)) {
            throw new NoEncontradoException("Proveedor", proveedorId);
        }
        return PageResponse.of(evaluacionRepository.findByProveedorId(proveedorId,
                Ordenamiento.validar(pageable, CAMPOS_ORDENABLES)), EvaluacionResponse::of);
    }

    /**
     * Evaluaciones del proveedor del usuario logueado (portal).
     *
     * @param pageable página y orden
     * @return página de sus evaluaciones
     * @throws AccesoDenegadoException si el usuario no pertenece a un proveedor
     */
    @Transactional(readOnly = true)
    public PageResponse<EvaluacionResponse> mias(Pageable pageable) {
        UsuarioAutenticado u = SesionActual.usuario();
        if (!u.esProveedor()) {
            throw new AccesoDenegadoException("Su usuario no pertenece a un proveedor");
        }
        return listar(u.proveedorId(), pageable);
    }
}
