package com.proyecto.supply_core.caja.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.caja.dto.AbrirTurnoRequest;
import com.proyecto.supply_core.caja.dto.AprobarTurnoRequest;
import com.proyecto.supply_core.caja.dto.CerrarTurnoRequest;
import com.proyecto.supply_core.caja.dto.MovimientoCajaRequest;
import com.proyecto.supply_core.caja.dto.MovimientoCajaResponse;
import com.proyecto.supply_core.caja.dto.TurnoResponse;
import com.proyecto.supply_core.caja.entity.Caja;
import com.proyecto.supply_core.caja.entity.MovimientoCaja;
import com.proyecto.supply_core.caja.entity.TurnoCaja;
import com.proyecto.supply_core.caja.enums.AccionAutorizada;
import com.proyecto.supply_core.caja.enums.EstadoTurno;
import com.proyecto.supply_core.caja.enums.MedioPago;
import com.proyecto.supply_core.caja.enums.TipoMovimientoCaja;
import com.proyecto.supply_core.caja.repository.CajaRepository;
import com.proyecto.supply_core.caja.repository.MovimientoCajaRepository;
import com.proyecto.supply_core.caja.repository.TurnoCajaRepository;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turnos de caja (spec 003): apertura con base, retiros, gastos e ingresos con autorización, cierre ciego y aprobación
 * de diferencias.
 * <ul>
 *   <li>Un solo turno abierto por caja y por cajero (lo garantiza la base de datos, V33).</li>
 *   <li>Todo peso que entra o sale queda en {@code movimiento_caja}; el esperado es la suma de esos registros.</li>
 *   <li>Cierre ciego: el cajero no ve el esperado hasta guardar lo contado; Java calcula y guarda las diferencias.</li>
 *   <li>Si alguna diferencia pasa la tolerancia, la aprueba un supervisor que no sea el cajero.</li>
 * </ul>
 */
@Service
public class TurnoService {

    /** Movimientos que el cajero ve con el turno abierto (los demás revelarían cuánto debería tener). */
    private static final Set<TipoMovimientoCaja> VISIBLES_AL_CAJERO = Set.of(TipoMovimientoCaja.BASE,
            TipoMovimientoCaja.RETIRO, TipoMovimientoCaja.GASTO, TipoMovimientoCaja.INGRESO);

    private final TurnoCajaRepository turnoRepository;
    private final CajaRepository cajaRepository;
    private final MovimientoCajaRepository movimientoRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AutorizacionService autorizacionService;
    private final NotificacionService notificacionService;
    private final AuditoriaService auditoriaService;
    private final JsonMapper json = JsonMapper.builder().build();

    /**
     * @param turnoRepository      turnos
     * @param cajaRepository       cajas
     * @param movimientoRepository libro de caja
     * @param empresaRepository    tolerancia de diferencia
     * @param usuarioRepository    nombres de cajeros y supervisores
     * @param autorizacionService  autorizaciones de un solo uso
     * @param notificacionService  aviso de diferencias por aprobar
     * @param auditoriaService     registro de cada acción
     */
    public TurnoService(TurnoCajaRepository turnoRepository, CajaRepository cajaRepository,
            MovimientoCajaRepository movimientoRepository, EmpresaRepository empresaRepository,
            UsuarioRepository usuarioRepository, AutorizacionService autorizacionService,
            NotificacionService notificacionService, AuditoriaService auditoriaService) {
        this.turnoRepository = turnoRepository;
        this.cajaRepository = cajaRepository;
        this.movimientoRepository = movimientoRepository;
        this.empresaRepository = empresaRepository;
        this.usuarioRepository = usuarioRepository;
        this.autorizacionService = autorizacionService;
        this.notificacionService = notificacionService;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Abre el turno de la sesión en una caja con su base en efectivo.
     *
     * @param req caja y base
     * @return turno abierto
     * @throws NoEncontradoException si la caja no es de la empresa
     * @throws NegocioException      si la caja está inactiva u ocupada, o el cajero ya tiene un turno abierto
     */
    @Transactional
    public TurnoResponse abrir(AbrirTurnoRequest req) {
        Long cajero = SesionActual.usuario().id();
        Caja caja = cajaRepository.conDetalle(req.cajaId()).orElseThrow(() -> new NoEncontradoException("Caja no encontrada"));
        if (!caja.isActiva()) {
            throw new NegocioException("CAJA_INACTIVA", "La caja " + caja.getNombre() + " está inactiva");
        }
        turnoRepository.abiertoDe(cajero).ifPresent(t -> {
            throw new NegocioException("TURNO_ABIERTO", "Ya tienes un turno abierto en " + t.getCaja().getNombre());
        });
        if (turnoRepository.existsByCajaIdAndEstado(caja.getId(), EstadoTurno.ABIERTO)) {
            throw new NegocioException("CAJA_OCUPADA", "La caja " + caja.getNombre() + " ya tiene un turno abierto");
        }
        TurnoCaja t = new TurnoCaja();
        t.setCaja(caja);
        t.setCajeroUsuarioId(cajero);
        t.setAbiertoEn(LocalDateTime.now());
        t.setBaseApertura(req.baseApertura().setScale(2, RoundingMode.HALF_UP));
        try {
            turnoRepository.saveAndFlush(t);
        } catch (DataIntegrityViolationException e) {
            throw new NegocioException("TURNO_ABIERTO", "La caja o el cajero ya tienen un turno abierto");
        }
        if (t.getBaseApertura().signum() > 0) {
            movimientoRepository.save(movimiento(t, TipoMovimientoCaja.BASE, t.getBaseApertura(), "Base de apertura",
                    null, null));
        }
        auditoriaService.exito(AccionAuditoria.TURNO_ABRIR, EntidadAuditada.TURNO_CAJA, t.getId(),
                "caja=" + caja.getId() + "; base=" + t.getBaseApertura());
        return respuesta(t);
    }

    /**
     * @return el turno abierto de la sesión
     * @throws NoEncontradoException si no tiene uno
     */
    @Transactional(readOnly = true)
    public TurnoResponse actual() {
        TurnoCaja t = turnoRepository.abiertoDe(SesionActual.usuario().id())
                .orElseThrow(() -> new NoEncontradoException("No tienes un turno abierto"));
        return respuesta(t);
    }

    /**
     * Retiro a la bóveda, gasto menor o ingreso de efectivo, con autorización de un supervisor.
     *
     * @param turnoId turno
     * @param req     tipo, monto, motivo, soporte y autorización
     * @return turno con el movimiento
     * @throws NegocioException si el turno no está abierto o no es de la sesión, el tipo no es válido, no hay efectivo
     *                          suficiente o la autorización no sirve
     */
    @Transactional
    public TurnoResponse registrarMovimiento(Long turnoId, MovimientoCajaRequest req) {
        TurnoCaja t = abiertoDeLaSesion(turnoId);
        AccionAutorizada accion = switch (req.tipo()) {
            case RETIRO -> AccionAutorizada.RETIRO;
            case GASTO -> AccionAutorizada.GASTO;
            case INGRESO -> AccionAutorizada.INGRESO;
            default -> throw new NegocioException("TIPO_NO_PERMITIDO",
                    "Aquí solo se registran retiros, gastos e ingresos de efectivo");
        };
        BigDecimal monto = req.monto().setScale(2, RoundingMode.HALF_UP);
        boolean sale = req.tipo() != TipoMovimientoCaja.INGRESO;
        if (sale && movimientoRepository.saldo(t.getId(), MedioPago.EFECTIVO).compareTo(monto) < 0) {
            throw new NegocioException("EFECTIVO_INSUFICIENTE", "No hay suficiente efectivo en la caja para ese monto");
        }
        Long supervisor = autorizacionService.consumir(req.autorizacion(), accion, t.getId(), null);
        MovimientoCaja m = movimientoRepository.save(movimiento(t, req.tipo(), sale ? monto.negate() : monto,
                req.motivo().trim(), req.soporte() == null || req.soporte().isBlank() ? null : req.soporte().trim(),
                supervisor));
        auditoriaService.exito(AccionAuditoria.TURNO_MOVIMIENTO, EntidadAuditada.TURNO_CAJA, t.getId(),
                req.tipo() + " " + monto + "; movimiento=" + m.getId() + "; autorizó=" + supervisor);
        return respuesta(t);
    }

    /**
     * Cierre ciego: guarda lo contado, calcula el esperado y la diferencia por medio, y deja el turno cerrado. Si
     * alguna diferencia pasa la tolerancia, avisa a los supervisores para que la aprueben.
     *
     * @param turnoId turno
     * @param req     contado y observación
     * @return turno cerrado con esperado, contado y diferencias (tiquete Z)
     */
    @Transactional
    public TurnoResponse cerrar(Long turnoId, CerrarTurnoRequest req) {
        TurnoCaja t = abiertoDeLaSesion(turnoId);
        if (req.contadoOtros() != null) {
            req.contadoOtros().forEach((medio, valor) -> {
                if (valor != null && valor.signum() < 0) {
                    throw new NegocioException("CONTADO_NEGATIVO", "Lo contado en " + medio + " no puede ser negativo");
                }
            });
        }
        Map<MedioPago, BigDecimal> esperado = CalculoCaja.esperado(movimientoRepository.findByTurnoIdOrderByIdAsc(t.getId()));
        Map<MedioPago, BigDecimal> contado = CalculoCaja.contado(req.contadoEfectivo(), req.contadoOtros());
        Map<MedioPago, BigDecimal> diferencias = CalculoCaja.diferencias(contado, esperado);
        BigDecimal tolerancia = empresaRepository.findById(EmpresaContexto.requerida())
                .orElseThrow(() -> new NoEncontradoException("Empresa no encontrada")).getToleranciaDiferenciaCaja();
        boolean requiere = CalculoCaja.requiereAprobacion(diferencias, tolerancia);
        t.setEstado(EstadoTurno.CERRADO);
        t.setCerradoEn(LocalDateTime.now());
        t.setContadoEfectivo(contado.get(MedioPago.EFECTIVO));
        t.setContadoOtros(aJson(sinEfectivo(contado)));
        t.setEsperadoEfectivo(esperado.get(MedioPago.EFECTIVO));
        t.setEsperadoOtros(aJson(sinEfectivo(esperado)));
        t.setDiferencias(aJson(diferencias));
        t.setDiferencia(CalculoCaja.total(diferencias));
        t.setRequiereAprobacion(requiere);
        t.setObservacionCierre(req.observacion() == null || req.observacion().isBlank() ? null : req.observacion().trim());
        turnoRepository.saveAndFlush(t);
        auditoriaService.exito(AccionAuditoria.TURNO_CERRAR, EntidadAuditada.TURNO_CAJA, t.getId(),
                "diferencia=" + t.getDiferencia() + "; porMedio=" + t.getDiferencias() + "; tolerancia=" + tolerancia
                        + "; requiereAprobacion=" + requiere);
        if (requiere) {
            String mensaje = "El turno " + t.getId() + " de " + t.getCaja().getNombre() + " cerró con una diferencia de $ "
                    + t.getDiferencia().toPlainString() + ": revísala y apruébala en Cajas y turnos";
            notificacionService.notificarRol(Roles.SUPERVISOR_VENTAS, EventoNotificacion.TURNO_DIFERENCIA, mensaje);
            notificacionService.notificarRol(Roles.ADMIN_SISTEMA, EventoNotificacion.TURNO_DIFERENCIA, mensaje);
        }
        return respuesta(t);
    }

    /**
     * Un supervisor explica y aprueba la diferencia de un turno cerrado. Nadie aprueba su propio turno.
     *
     * @param turnoId turno
     * @param req     explicación
     * @return turno aprobado
     * @throws NegocioException si el turno no espera aprobación o es del mismo supervisor
     */
    @Transactional
    public TurnoResponse aprobar(Long turnoId, AprobarTurnoRequest req) {
        TurnoCaja t = turnoRepository.bloquear(turnoId).orElseThrow(() -> new NoEncontradoException("Turno no encontrado"));
        if (t.getEstado() != EstadoTurno.CERRADO || !t.isRequiereAprobacion()) {
            throw new NegocioException("SIN_APROBACION_PENDIENTE", "Este turno no tiene una diferencia por aprobar");
        }
        Long supervisor = SesionActual.usuario().id();
        if (t.getCajeroUsuarioId().equals(supervisor)) {
            throw new NegocioException("APRUEBA_LO_SUYO", "Nadie aprueba la diferencia de su propio turno");
        }
        t.setEstado(EstadoTurno.APROBADO);
        t.setAprobadoPorUsuarioId(supervisor);
        t.setAprobadoEn(LocalDateTime.now());
        t.setExplicacionAprobacion(req.explicacion().trim());
        turnoRepository.saveAndFlush(t);
        auditoriaService.exito(AccionAuditoria.TURNO_APROBAR, EntidadAuditada.TURNO_CAJA, t.getId(),
                "diferencia=" + t.getDiferencia() + "; explicación=" + t.getExplicacionAprobacion());
        return respuesta(t);
    }

    /**
     * @param estado     filtro por estado
     * @param pendientes solo los que esperan aprobación
     * @param cajaId     filtro por caja
     * @param pageable   página
     * @return turnos con su resumen (sin movimientos)
     */
    @Transactional(readOnly = true)
    public PageResponse<TurnoResponse> buscar(EstadoTurno estado, boolean pendientes, Long cajaId, Pageable pageable) {
        var pagina = turnoRepository.buscar(estado, pendientes, cajaId, pageable);
        Map<Long, String> nombres = nombres(pagina.getContent().stream()
                .flatMap(t -> java.util.stream.Stream.of(t.getCajeroUsuarioId(), t.getAprobadoPorUsuarioId())).toList());
        return PageResponse.of(pagina, t -> armar(t, false, List.of(), nombres));
    }

    /**
     * Detalle de un turno: el supervisor, el dueño y auditoría ven todos; el cajero solo los suyos, y con el turno
     * abierto no ve lo esperado ni las ventas.
     *
     * @param turnoId turno
     * @return turno con su libro de caja
     * @throws NoEncontradoException si no existe o es de otro cajero y la sesión no supervisa
     */
    @Transactional(readOnly = true)
    public TurnoResponse obtener(Long turnoId) {
        TurnoCaja t = turnoRepository.conCaja(turnoId).orElseThrow(() -> new NoEncontradoException("Turno no encontrado"));
        if (!supervisa() && !t.getCajeroUsuarioId().equals(SesionActual.usuario().id())) {
            throw new NoEncontradoException("Turno no encontrado");
        }
        return respuesta(t);
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * @param turnoId turno
     * @return el turno bloqueado, abierto y de la sesión
     */
    private TurnoCaja abiertoDeLaSesion(Long turnoId) {
        TurnoCaja t = turnoRepository.bloquear(turnoId).orElseThrow(() -> new NoEncontradoException("Turno no encontrado"));
        if (!t.getCajeroUsuarioId().equals(SesionActual.usuario().id())) {
            throw new AccesoDenegadoException("Solo el cajero del turno registra movimientos y lo cierra");
        }
        if (t.getEstado() != EstadoTurno.ABIERTO) {
            throw new NegocioException("TURNO_NO_ABIERTO", "El turno ya está cerrado");
        }
        return t;
    }

    /**
     * @param t turno
     * @return respuesta según quién mira: con el turno abierto, al cajero solo se le muestran base, retiros, gastos e
     *         ingresos (no las ventas ni el esperado)
     */
    private TurnoResponse respuesta(TurnoCaja t) {
        List<MovimientoCaja> movimientos = movimientoRepository.findByTurnoIdOrderByIdAsc(t.getId());
        Set<Long> ids = new HashSet<>();
        ids.add(t.getCajeroUsuarioId());
        ids.add(t.getAprobadoPorUsuarioId());
        movimientos.forEach(m -> {
            ids.add(m.getUsuarioId());
            ids.add(m.getAutorizadoPorUsuarioId());
        });
        Map<Long, String> nombres = nombres(ids);
        boolean ocultar = t.getEstado() == EstadoTurno.ABIERTO && !supervisa();
        List<MovimientoCaja> visibles = ocultar
                ? movimientos.stream().filter(m -> VISIBLES_AL_CAJERO.contains(m.getTipo())).toList()
                : movimientos;
        boolean debeRetirar = t.getEstado() == EstadoTurno.ABIERTO && CalculoCaja.debeRetirar(
                CalculoCaja.esperado(movimientos).get(MedioPago.EFECTIVO), t.getCaja().getTopeEfectivo());
        return armar(t, debeRetirar, visibles, nombres);
    }

    /**
     * @param t           turno
     * @param debeRetirar marca de tope de efectivo
     * @param movimientos movimientos que se muestran
     * @param nombres     nombres de usuario por id
     * @return respuesta
     */
    private TurnoResponse armar(TurnoCaja t, boolean debeRetirar, List<MovimientoCaja> movimientos,
            Map<Long, String> nombres) {
        boolean cerrado = t.getEstado() != EstadoTurno.ABIERTO;
        Map<MedioPago, BigDecimal> esperado = null;
        Map<MedioPago, BigDecimal> contado = null;
        if (cerrado) {
            esperado = conEfectivo(t.getEsperadoEfectivo(), deJson(t.getEsperadoOtros()));
            contado = conEfectivo(t.getContadoEfectivo(), deJson(t.getContadoOtros()));
        }
        List<MovimientoCajaResponse> lista = movimientos.stream().map(m -> new MovimientoCajaResponse(m.getId(),
                m.getTipo(), m.getMedioPago(), m.getMonto(), m.getMotivo(), m.getSoporte(), nombres.get(m.getUsuarioId()),
                m.getAutorizadoPorUsuarioId() == null ? null : nombres.get(m.getAutorizadoPorUsuarioId()),
                m.getCreadoEn())).toList();
        return new TurnoResponse(t.getId(), t.getCaja().getId(), t.getCaja().getNombre(),
                t.getCaja().getBodega() == null ? null : t.getCaja().getBodega().getNombre(), t.getCajeroUsuarioId(),
                nombres.get(t.getCajeroUsuarioId()), t.getEstado(), t.getAbiertoEn(), t.getBaseApertura(), debeRetirar,
                t.getCerradoEn(), esperado, contado, cerrado ? deJson(t.getDiferencias()) : null,
                cerrado ? t.getDiferencia() : null, t.isRequiereAprobacion(), t.getObservacionCierre(),
                t.getAprobadoPorUsuarioId() == null ? null : nombres.get(t.getAprobadoPorUsuarioId()), t.getAprobadoEn(),
                t.getExplicacionAprobacion(), lista);
    }

    /**
     * @param t             turno
     * @param tipo          tipo
     * @param monto         monto con signo
     * @param motivo        motivo
     * @param soporte       soporte
     * @param autorizadoPor supervisor que autorizó
     * @return movimiento en efectivo del cajero de la sesión
     */
    private static MovimientoCaja movimiento(TurnoCaja t, TipoMovimientoCaja tipo, BigDecimal monto, String motivo,
            String soporte, Long autorizadoPor) {
        MovimientoCaja m = new MovimientoCaja();
        m.setTurnoId(t.getId());
        m.setTipo(tipo);
        m.setMedioPago(MedioPago.EFECTIVO);
        m.setMonto(monto);
        m.setMotivo(motivo);
        m.setSoporte(soporte);
        m.setUsuarioId(SesionActual.usuario().id());
        m.setAutorizadoPorUsuarioId(autorizadoPor);
        return m;
    }

    /**
     * @return {@code true} si la sesión es supervisor de ventas, dueño o auditor
     */
    private static boolean supervisa() {
        Set<String> roles = SesionActual.usuario().roles();
        return roles.contains(Roles.SUPERVISOR_VENTAS) || roles.contains(Roles.ADMIN_SISTEMA)
                || roles.contains(Roles.AUDITOR);
    }

    /**
     * @param ids usuarios ({@code null} se ignora)
     * @return nombre por id
     */
    private Map<Long, String> nombres(java.util.Collection<Long> ids) {
        Map<Long, String> nombres = new HashMap<>();
        List<Long> validos = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (!validos.isEmpty()) {
            for (Usuario u : usuarioRepository.findAllById(validos)) {
                nombres.put(u.getId(), u.getNombreCompleto());
            }
        }
        return nombres;
    }

    /**
     * @param mapa valores por medio
     * @return el mismo mapa sin el efectivo
     */
    private static Map<MedioPago, BigDecimal> sinEfectivo(Map<MedioPago, BigDecimal> mapa) {
        Map<MedioPago, BigDecimal> copia = new EnumMap<>(mapa);
        copia.remove(MedioPago.EFECTIVO);
        return copia;
    }

    /**
     * @param efectivo efectivo
     * @param otros    otros medios
     * @return mapa con todos los medios
     */
    private static Map<MedioPago, BigDecimal> conEfectivo(BigDecimal efectivo, Map<MedioPago, BigDecimal> otros) {
        Map<MedioPago, BigDecimal> mapa = new EnumMap<>(MedioPago.class);
        mapa.put(MedioPago.EFECTIVO, efectivo);
        mapa.putAll(otros);
        return mapa;
    }

    /**
     * @param mapa valores por medio
     * @return JSON
     */
    private String aJson(Map<MedioPago, BigDecimal> mapa) {
        return json.writeValueAsString(mapa);
    }

    /**
     * @param texto JSON guardado ({@code null} = vacío)
     * @return valores por medio
     */
    private Map<MedioPago, BigDecimal> deJson(String texto) {
        if (texto == null || texto.isBlank()) {
            return new EnumMap<>(MedioPago.class);
        }
        Map<MedioPago, BigDecimal> mapa = new EnumMap<>(MedioPago.class);
        mapa.putAll(json.readValue(texto, new TypeReference<Map<MedioPago, BigDecimal>>() { }));
        return mapa;
    }
}
