package com.proyecto.supply_core.presupuesto.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud;
import com.proyecto.supply_core.compra.solicitud.repository.SolicitudCompraRepository;
import com.proyecto.supply_core.empresa.repository.CentroCostoRepository;
import com.proyecto.supply_core.presupuesto.dto.EvaluacionPresupuesto;
import com.proyecto.supply_core.presupuesto.dto.PresupuestoRequest;
import com.proyecto.supply_core.presupuesto.dto.PresupuestoResponse;
import com.proyecto.supply_core.presupuesto.dto.PresupuestoUpdateRequest;
import com.proyecto.supply_core.presupuesto.entity.Presupuesto;
import com.proyecto.supply_core.presupuesto.repository.PresupuestoRepository;
import com.proyecto.supply_core.security.context.SesionActual;

/**
 * Presupuesto por centro de costo y mes (paso 18-a).
 * <p><b>Comprometido</b> de un mes: suma del monto estimado de las solicitudes del centro de costo cuya fecha
 * requerida cae en ese mes y que siguen vivas: pendientes de aprobación, aprobadas, en RFQ, adjudicadas o
 * cerradas. Las borrador, devueltas, rechazadas y canceladas no cuentan. Es un estimado (cantidad por costo de
 * referencia), no el gasto real de las órdenes.</p>
 * <p>Si el centro de costo no tiene presupuesto en el mes, no hay control. La decisión de qué hacer cuando se
 * excede la toma {@code SolicitudService}; aquí solo se calcula.</p>
 */
@Service
public class PresupuestoService {

    /** Estados de solicitud que comprometen presupuesto. */
    static final Set<EstadoSolicitud> ESTADOS_COMPROMETEN = EnumSet.of(EstadoSolicitud.PENDIENTE_APROBACION,
            EstadoSolicitud.APROBADA, EstadoSolicitud.EN_RFQ, EstadoSolicitud.ADJUDICADA, EstadoSolicitud.CERRADA);

    private final PresupuestoRepository presupuestoRepository;
    private final SolicitudCompraRepository solicitudRepository;
    private final CentroCostoRepository centroCostoRepository;
    private final AuditoriaService auditoriaService;

    /**
     * @param presupuestoRepository presupuestos
     * @param solicitudRepository   solicitudes (para sumar lo comprometido)
     * @param centroCostoRepository verificación del centro de costo
     * @param auditoriaService      registro de cambios
     */
    public PresupuestoService(PresupuestoRepository presupuestoRepository,
            SolicitudCompraRepository solicitudRepository, CentroCostoRepository centroCostoRepository,
            AuditoriaService auditoriaService) {
        this.presupuestoRepository = presupuestoRepository;
        this.solicitudRepository = solicitudRepository;
        this.centroCostoRepository = centroCostoRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Define el presupuesto de un centro de costo para un mes.
     *
     * @param req datos validados
     * @return presupuesto creado, con lo ya comprometido
     * @throws NoEncontradoException si el centro de costo no existe
     * @throws NegocioException      si el centro está inactivo o ya tiene presupuesto ese mes
     */
    @Transactional
    public PresupuestoResponse crear(PresupuestoRequest req) {
        var centro = centroCostoRepository.findById(req.centroCostoId())
                .orElseThrow(() -> new NoEncontradoException("Centro de costo", req.centroCostoId()));
        if (!centro.isActivo()) {
            throw new NegocioException("CENTRO_COSTO_INACTIVO",
                    "El centro de costo " + centro.getCodigo() + " está inactivo");
        }
        if (presupuestoRepository.existsByCentroCostoIdAndAnioAndMes(req.centroCostoId(), req.anio(), req.mes())) {
            throw new NegocioException("PRESUPUESTO_DUPLICADO", "El centro de costo " + centro.getCodigo()
                    + " ya tiene presupuesto en " + periodo(req.anio(), req.mes()) + "; corrija su monto");
        }
        Presupuesto p = new Presupuesto();
        p.setCentroCostoId(req.centroCostoId());
        p.setAnio(req.anio());
        p.setMes(req.mes());
        p.setMonto(req.monto().setScale(2, RoundingMode.HALF_UP));
        p.setCreadoPorUsuarioId(SesionActual.usuario().id());
        Presupuesto guardado = presupuestoRepository.save(p);
        auditoriaService.exito(AccionAuditoria.PRESUPUESTO_CREAR, EntidadAuditada.PRESUPUESTO, guardado.getId(),
                "centro=" + centro.getCodigo() + "; periodo=" + periodo(p.getAnio(), p.getMes()) + "; monto="
                        + p.getMonto().toPlainString());
        return respuesta(guardado);
    }

    /**
     * Corrige el monto de un presupuesto.
     *
     * @param id  presupuesto a corregir
     * @param req nuevo monto validado
     * @return presupuesto actualizado
     * @throws NoEncontradoException si no existe
     */
    @Transactional
    public PresupuestoResponse actualizar(Long id, PresupuestoUpdateRequest req) {
        Presupuesto p = buscar(id);
        BigDecimal antes = p.getMonto();
        p.setMonto(req.monto().setScale(2, RoundingMode.HALF_UP));
        auditoriaService.exito(AccionAuditoria.PRESUPUESTO_EDITAR, EntidadAuditada.PRESUPUESTO, id,
                "centro=" + p.getCentroCostoId() + "; periodo=" + periodo(p.getAnio(), p.getMes()) + "; monto "
                        + antes.toPlainString() + " -> " + p.getMonto().toPlainString());
        return respuesta(p);
    }

    /**
     * Detalle de un presupuesto con lo comprometido.
     *
     * @param id id del presupuesto
     * @return presupuesto
     * @throws NoEncontradoException si no existe
     */
    @Transactional(readOnly = true)
    public PresupuestoResponse obtener(Long id) {
        return respuesta(buscar(id));
    }

    /**
     * Lista presupuestos con lo comprometido, del mes más reciente al más antiguo.
     *
     * @param centroCostoId centro de costo ({@code null} = todos)
     * @param anio          año ({@code null} = todos)
     * @return presupuestos
     */
    @Transactional(readOnly = true)
    public List<PresupuestoResponse> listar(Long centroCostoId, Integer anio) {
        return presupuestoRepository.buscar(centroCostoId, anio).stream().map(this::respuesta).toList();
    }

    /**
     * Compara el monto de una solicitud con el presupuesto de su centro de costo en el mes de su fecha requerida.
     * Bloquea la fila del presupuesto hasta el fin de la transacción: dos envíos del mismo centro y mes se evalúan
     * uno tras otro. Debe llamarse dentro de la transacción del envío.
     *
     * @param centroCostoId       centro de costo de la solicitud
     * @param fechaRequerida      fecha requerida (define el mes)
     * @param monto               monto estimado de la solicitud
     * @param excluirSolicitudId  la propia solicitud, para no contarla dos veces
     * @return evaluación; {@link EvaluacionPresupuesto#SIN_PRESUPUESTO} si no hay presupuesto ese mes
     */
    @Transactional
    public EvaluacionPresupuesto evaluar(Long centroCostoId, LocalDate fechaRequerida, BigDecimal monto,
            Long excluirSolicitudId) {
        Optional<Presupuesto> presupuesto = presupuestoRepository.bloquear(centroCostoId, fechaRequerida.getYear(),
                fechaRequerida.getMonthValue());
        if (presupuesto.isEmpty()) {
            return EvaluacionPresupuesto.SIN_PRESUPUESTO;
        }
        BigDecimal tope = presupuesto.get().getMonto();
        BigDecimal comprometido = comprometido(centroCostoId, fechaRequerida, excluirSolicitudId);
        return new EvaluacionPresupuesto(true, tope, comprometido, tope.subtract(comprometido),
                comprometido.add(monto).compareTo(tope) > 0);
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Respuesta de un presupuesto con su comprometido.
     *
     * @param p presupuesto
     * @return DTO
     */
    private PresupuestoResponse respuesta(Presupuesto p) {
        return PresupuestoResponse.of(p,
                comprometido(p.getCentroCostoId(), LocalDate.of(p.getAnio(), p.getMes(), 1), null));
    }

    /**
     * Suma lo comprometido por las solicitudes vivas del centro de costo en el mes de la fecha.
     *
     * @param centroCostoId      centro de costo
     * @param fecha              cualquier día del mes
     * @param excluirSolicitudId solicitud que no se cuenta ({@code null} = contar todas)
     * @return monto comprometido con 2 decimales
     */
    private BigDecimal comprometido(Long centroCostoId, LocalDate fecha, Long excluirSolicitudId) {
        YearMonth mes = YearMonth.from(fecha);
        BigDecimal suma = solicitudRepository.comprometido(centroCostoId, mes.atDay(1), mes.atEndOfMonth(),
                ESTADOS_COMPROMETEN, excluirSolicitudId);
        return (suma == null ? BigDecimal.ZERO : suma).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Busca el presupuesto.
     *
     * @param id id
     * @return entidad
     * @throws NoEncontradoException si no existe
     */
    private Presupuesto buscar(Long id) {
        return presupuestoRepository.findById(id).orElseThrow(() -> new NoEncontradoException("Presupuesto", id));
    }

    /**
     * Texto del período para mensajes y auditoría.
     *
     * @param anio año
     * @param mes  mes del 1 al 12
     * @return p. ej. {@code 2026-11}
     */
    public static String periodo(int anio, int mes) {
        return "%d-%02d".formatted(anio, mes);
    }
}
