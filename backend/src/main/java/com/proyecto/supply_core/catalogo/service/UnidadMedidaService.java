package com.proyecto.supply_core.catalogo.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.catalogo.dto.ConversionResponse;
import com.proyecto.supply_core.catalogo.dto.UnidadMedidaRequest;
import com.proyecto.supply_core.catalogo.dto.UnidadMedidaResponse;
import com.proyecto.supply_core.catalogo.dto.UnidadMedidaUpdateRequest;
import com.proyecto.supply_core.catalogo.entity.UnidadMedida;
import com.proyecto.supply_core.catalogo.repository.UnidadMedidaRepository;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;

/**
 * Administración de unidades de medida y conversión entre ellas.
 * <p>Conversión: {@code cantidad × factorOrigen ÷ factorDestino}, solo dentro de la misma magnitud.
 * Ej.: 2 DOC → UND = 2 × 12 ÷ 1 = 24. Este cálculo lo usará la normalización de cotizaciones
 * (paso 13): siempre en Java, nunca en la IA.</p>
 */
@Service
public class UnidadMedidaService {

    /** Escala de las cantidades del sistema ({@code DECIMAL(18,4)}). */
    static final int ESCALA_CANTIDAD = 4;

    private final UnidadMedidaRepository unidadRepository;
    private final AuditoriaService auditoriaService;

    /**
     * @param unidadRepository acceso a unidades
     * @param auditoriaService registro de cambios
     */
    public UnidadMedidaService(UnidadMedidaRepository unidadRepository, AuditoriaService auditoriaService) {
        this.unidadRepository = unidadRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Lista las unidades.
     *
     * @param activo filtro por estado ({@code null} = todas)
     * @return unidades por magnitud y código
     */
    @Transactional(readOnly = true)
    public List<UnidadMedidaResponse> listar(Boolean activo) {
        List<UnidadMedida> unidades = activo == null
                ? unidadRepository.findAllByOrderByMagnitudAscCodigoAsc()
                : unidadRepository.findByActivoOrderByMagnitudAscCodigoAsc(activo);
        return unidades.stream().map(UnidadMedidaResponse::of).toList();
    }

    /**
     * Detalle de una unidad.
     *
     * @param id id
     * @return unidad
     * @throws NoEncontradoException si no existe
     */
    @Transactional(readOnly = true)
    public UnidadMedidaResponse obtener(Long id) {
        return UnidadMedidaResponse.of(buscar(id));
    }

    /**
     * Crea una unidad ({@code @CodigoUnidadUnico} ya validó el código).
     *
     * @param req datos validados
     * @return unidad creada
     */
    @Transactional
    public UnidadMedidaResponse crear(UnidadMedidaRequest req) {
        UnidadMedida u = new UnidadMedida();
        u.setCodigo(req.codigo().trim().toUpperCase(Locale.ROOT));
        u.setNombre(req.nombre().trim());
        u.setMagnitud(req.magnitud());
        u.setFactorConversionBase(req.factorConversionBase());
        UnidadMedida guardada = unidadRepository.save(u);
        auditoriaService.exito(AccionAuditoria.UNIDAD_CREAR, EntidadAuditada.UNIDAD_MEDIDA, guardada.getId(),
                "codigo=" + guardada.getCodigo() + ", magnitud=" + guardada.getMagnitud() + ", factor="
                        + guardada.getFactorConversionBase().stripTrailingZeros().toPlainString());
        return UnidadMedidaResponse.of(guardada);
    }

    /**
     * Cambia el nombre de una unidad (lo único editable).
     *
     * @param id  unidad
     * @param req nombre nuevo
     * @return unidad actualizada
     * @throws NoEncontradoException si no existe
     */
    @Transactional
    public UnidadMedidaResponse actualizar(Long id, UnidadMedidaUpdateRequest req) {
        UnidadMedida u = buscar(id);
        String antes = u.getNombre();
        u.setNombre(req.nombre().trim());
        auditoriaService.exito(AccionAuditoria.UNIDAD_EDITAR, EntidadAuditada.UNIDAD_MEDIDA, id,
                "codigo=" + u.getCodigo() + ", nombre: " + antes + " -> " + u.getNombre());
        return UnidadMedidaResponse.of(u);
    }

    /**
     * Activa o desactiva una unidad.
     *
     * @param id     unidad
     * @param activo nuevo estado
     * @return unidad actualizada
     * @throws NoEncontradoException si no existe
     */
    @Transactional
    public UnidadMedidaResponse cambiarEstado(Long id, boolean activo) {
        UnidadMedida u = buscar(id);
        u.setActivo(activo);
        auditoriaService.exito(AccionAuditoria.UNIDAD_CAMBIAR_ESTADO, EntidadAuditada.UNIDAD_MEDIDA, id,
                "codigo=" + u.getCodigo() + ", activo=" + activo);
        return UnidadMedidaResponse.of(u);
    }

    /**
     * Convierte una cantidad entre dos unidades de la misma magnitud.
     *
     * @param cantidad cantidad en la unidad de origen
     * @param origenId unidad de origen
     * @param destinoId unidad de destino
     * @return cantidad equivalente con 4 decimales
     * @throws NoEncontradoException si alguna unidad no existe
     * @throws NegocioException      si las unidades miden cosas distintas
     */
    @Transactional(readOnly = true)
    public BigDecimal convertir(BigDecimal cantidad, Long origenId, Long destinoId) {
        return convertir(cantidad, buscar(origenId), buscar(destinoId));
    }

    /**
     * Versión para la API de {@link #convertir(BigDecimal, Long, Long)}.
     *
     * @param cantidad  cantidad en la unidad de origen
     * @param origenId  unidad de origen
     * @param destinoId unidad de destino
     * @return cantidades y códigos de ambas unidades
     */
    @Transactional(readOnly = true)
    public ConversionResponse consultarConversion(BigDecimal cantidad, Long origenId, Long destinoId) {
        UnidadMedida origen = buscar(origenId);
        UnidadMedida destino = buscar(destinoId);
        return new ConversionResponse(cantidad, origen.getCodigo(), convertir(cantidad, origen, destino),
                destino.getCodigo());
    }

    /**
     * Cálculo de la conversión.
     *
     * @param cantidad cantidad en origen
     * @param origen   unidad de origen
     * @param destino  unidad de destino
     * @return cantidad equivalente con 4 decimales (redondeo HALF_UP)
     * @throws NegocioException si las magnitudes son distintas
     */
    static BigDecimal convertir(BigDecimal cantidad, UnidadMedida origen, UnidadMedida destino) {
        if (origen.getMagnitud() != destino.getMagnitud()) {
            throw new NegocioException("UNIDADES_INCOMPATIBLES", "No se puede convertir " + origen.getCodigo()
                    + " (" + origen.getMagnitud() + ") a " + destino.getCodigo() + " (" + destino.getMagnitud() + ")");
        }
        return cantidad.multiply(origen.getFactorConversionBase())
                .divide(destino.getFactorConversionBase(), ESCALA_CANTIDAD, RoundingMode.HALF_UP);
    }

    /**
     * Busca la unidad.
     *
     * @param id id
     * @return entidad
     * @throws NoEncontradoException si no existe
     */
    private UnidadMedida buscar(Long id) {
        return unidadRepository.findById(id).orElseThrow(() -> new NoEncontradoException("Unidad de medida", id));
    }
}
