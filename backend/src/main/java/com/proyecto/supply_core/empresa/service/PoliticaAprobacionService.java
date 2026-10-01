package com.proyecto.supply_core.empresa.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.util.EmpresaUnica;
import com.proyecto.supply_core.empresa.dto.PoliticaAplicableResponse;
import com.proyecto.supply_core.empresa.dto.PoliticaAprobacionRequest;
import com.proyecto.supply_core.empresa.dto.PoliticaAprobacionResponse;
import com.proyecto.supply_core.empresa.entity.PoliticaAprobacion;
import com.proyecto.supply_core.empresa.enums.TipoPolitica;
import com.proyecto.supply_core.empresa.repository.PoliticaAprobacionRepository;
import com.proyecto.supply_core.usuario.entity.Rol;
import com.proyecto.supply_core.usuario.repository.RolRepository;
import com.proyecto.supply_core.usuario.validation.CodigosRol;

/**
 * Políticas de aprobación por tipo y rango de montos {@code [desde, hasta)}.
 * <p>Regla clave: entre políticas ACTIVAS del mismo tipo los rangos no se superponen, así cada
 * monto tiene a lo sumo una política aplicable. Si ningún rango cubre un monto, la operación no
 * requiere aprobación. Lo usan las solicitudes (paso 10) y las adjudicaciones (paso 14).</p>
 */
@Service
public class PoliticaAprobacionService {

    private final PoliticaAprobacionRepository politicaRepository;
    private final RolRepository rolRepository;
    private final AuditoriaService auditoriaService;

    /**
     * @param politicaRepository acceso a políticas
     * @param rolRepository      acceso a roles
     * @param auditoriaService   registro de cambios
     */
    public PoliticaAprobacionService(PoliticaAprobacionRepository politicaRepository, RolRepository rolRepository,
            AuditoriaService auditoriaService) {
        this.politicaRepository = politicaRepository;
        this.rolRepository = rolRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Lista todas las políticas.
     *
     * @return políticas por tipo y monto inicial
     */
    @Transactional(readOnly = true)
    public List<PoliticaAprobacionResponse> listar() {
        return politicaRepository.findByEmpresaIdOrderByTipoAscMontoDesdeAsc(EmpresaUnica.ID).stream()
                .map(PoliticaAprobacionResponse::of).toList();
    }

    /**
     * Detalle de una política.
     *
     * @param id id
     * @return política
     * @throws NoEncontradoException si no existe
     */
    @Transactional(readOnly = true)
    public PoliticaAprobacionResponse obtener(Long id) {
        return PoliticaAprobacionResponse.of(buscar(id));
    }

    /**
     * Crea una política activa.
     *
     * @param req datos validados (rol interno, rango coherente)
     * @return política creada
     * @throws NegocioException si se superpone con otra política activa del mismo tipo
     */
    @Transactional
    public PoliticaAprobacionResponse crear(PoliticaAprobacionRequest req) {
        BigDecimal desde = monto(req.montoDesde());
        BigDecimal hasta = req.montoHasta() == null ? null : monto(req.montoHasta());
        validarSinSuperposicion(req.tipo(), desde, hasta, null);

        PoliticaAprobacion p = new PoliticaAprobacion();
        p.setEmpresaId(EmpresaUnica.ID);
        p.setTipo(req.tipo());
        p.setRolAprobador(rol(req.rolAprobador()));
        p.setMontoDesde(desde);
        p.setMontoHasta(hasta);
        PoliticaAprobacion guardada = politicaRepository.save(p);
        auditoriaService.exito(AccionAuditoria.POLITICA_CREAR, EntidadAuditada.POLITICA_APROBACION, guardada.getId(),
                describir(guardada));
        return PoliticaAprobacionResponse.of(guardada);
    }

    /**
     * Edita una política. Si está activa, el nuevo rango tampoco puede superponerse.
     *
     * @param id  política a editar
     * @param req datos validados
     * @return política actualizada
     * @throws NoEncontradoException si no existe
     * @throws NegocioException      si se superpone con otra activa del mismo tipo
     */
    @Transactional
    public PoliticaAprobacionResponse actualizar(Long id, PoliticaAprobacionRequest req) {
        PoliticaAprobacion p = buscar(id);
        BigDecimal desde = monto(req.montoDesde());
        BigDecimal hasta = req.montoHasta() == null ? null : monto(req.montoHasta());
        if (p.isActivo()) {
            validarSinSuperposicion(req.tipo(), desde, hasta, id);
        }
        String antes = describir(p);
        p.setTipo(req.tipo());
        p.setRolAprobador(rol(req.rolAprobador()));
        p.setMontoDesde(desde);
        p.setMontoHasta(hasta);
        auditoriaService.exito(AccionAuditoria.POLITICA_EDITAR, EntidadAuditada.POLITICA_APROBACION, id,
                "antes: " + antes + " | después: " + describir(p));
        return PoliticaAprobacionResponse.of(p);
    }

    /**
     * Activa o desactiva una política. Al activar se vuelve a revisar la superposición.
     *
     * @param id     política
     * @param activo nuevo estado
     * @return política actualizada
     * @throws NoEncontradoException si no existe
     * @throws NegocioException      si al activarla se superpone con otra activa
     */
    @Transactional
    public PoliticaAprobacionResponse cambiarEstado(Long id, boolean activo) {
        PoliticaAprobacion p = buscar(id);
        if (activo && !p.isActivo()) {
            validarSinSuperposicion(p.getTipo(), p.getMontoDesde(), p.getMontoHasta(), id);
        }
        p.setActivo(activo);
        auditoriaService.exito(AccionAuditoria.POLITICA_CAMBIAR_ESTADO, EntidadAuditada.POLITICA_APROBACION, id,
                describir(p) + ", activo=" + activo);
        return PoliticaAprobacionResponse.of(p);
    }

    /**
     * Política activa que cubre el monto (la usarán solicitudes y adjudicaciones).
     *
     * @param tipo  tipo de operación
     * @param monto monto a aprobar (≥ 0)
     * @return política aplicable o vacío si no requiere aprobación
     */
    @Transactional(readOnly = true)
    public Optional<PoliticaAprobacion> aplicable(TipoPolitica tipo, BigDecimal monto) {
        BigDecimal valor = monto(monto);
        return politicaRepository.findByEmpresaIdAndTipoAndActivoTrue(EmpresaUnica.ID, tipo).stream()
                .filter(p -> p.cubre(valor)).findFirst();
    }

    /**
     * Versión para la API de {@link #aplicable}.
     *
     * @param tipo  tipo de operación
     * @param monto monto a aprobar
     * @return si requiere aprobación y con qué política
     */
    @Transactional(readOnly = true)
    public PoliticaAplicableResponse consultarAplicable(TipoPolitica tipo, BigDecimal monto) {
        return aplicable(tipo, monto)
                .map(p -> new PoliticaAplicableResponse(true, PoliticaAprobacionResponse.of(p)))
                .orElseGet(() -> new PoliticaAplicableResponse(false, null));
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Impide que dos políticas activas del mismo tipo compartan algún monto.
     *
     * @param tipo     tipo de operación
     * @param desde    inicio del rango nuevo (inclusive)
     * @param hasta    fin del rango nuevo (exclusivo; {@code null} = sin tope)
     * @param excluirId política que se está editando ({@code null} en altas)
     * @throws NegocioException si hay superposición, indicando con cuál política
     */
    private void validarSinSuperposicion(TipoPolitica tipo, BigDecimal desde, BigDecimal hasta, Long excluirId) {
        politicaRepository.findByEmpresaIdAndTipoAndActivoTrue(EmpresaUnica.ID, tipo).stream()
                .filter(p -> !p.getId().equals(excluirId))
                .filter(p -> p.seSuperponeCon(desde, hasta))
                .findFirst()
                .ifPresent(p -> {
                    throw new NegocioException("RANGO_SUPERPUESTO", "El rango se superpone con la política "
                            + p.getId() + " (" + rango(p.getMontoDesde(), p.getMontoHasta()) + ")");
                });
    }

    /**
     * Busca el rol interno por código ({@code @RolInterno} ya lo validó).
     *
     * @param codigo código recibido
     * @return rol
     * @throws NegocioException si no existe (solo si se llamó al Service sin {@code @Valid})
     */
    private Rol rol(String codigo) {
        return rolRepository.findByCodigo(CodigosRol.normalizar(codigo))
                .orElseThrow(() -> new NegocioException("ROL_INEXISTENTE", "No existe el rol " + codigo));
    }

    /**
     * Busca la política con su rol.
     *
     * @param id id
     * @return entidad
     * @throws NoEncontradoException si no existe
     */
    private PoliticaAprobacion buscar(Long id) {
        return politicaRepository.findWithRolById(id)
                .orElseThrow(() -> new NoEncontradoException("Política de aprobación", id));
    }

    /**
     * Lleva el monto a 2 decimales (escala de la columna) para comparar siempre igual.
     *
     * @param valor monto recibido
     * @return monto con escala 2
     */
    private static BigDecimal monto(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Texto del rango para mensajes.
     *
     * @param desde inicio
     * @param hasta fin o {@code null}
     * @return p. ej. {@code [0.00, 1000000.00)} o {@code [1000000.00, sin tope)}
     */
    private static String rango(BigDecimal desde, BigDecimal hasta) {
        return "[" + desde.toPlainString() + ", " + (hasta == null ? "sin tope" : hasta.toPlainString()) + ")";
    }

    /**
     * Resumen para la auditoría.
     *
     * @param p política
     * @return texto con tipo, rol y rango
     */
    private static String describir(PoliticaAprobacion p) {
        return "tipo=" + p.getTipo() + ", rol=" + p.getRolAprobador().getCodigo() + ", rango="
                + rango(p.getMontoDesde(), p.getMontoHasta());
    }
}
