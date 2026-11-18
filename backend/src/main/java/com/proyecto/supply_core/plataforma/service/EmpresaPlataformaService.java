package com.proyecto.supply_core.plataforma.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.empresa.entity.Empresa;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.plataforma.dto.EmpresaCrearRequest;
import com.proyecto.supply_core.plataforma.dto.EmpresaDetalleResponse;
import com.proyecto.supply_core.plataforma.dto.EmpresaPlataformaResponse;
import com.proyecto.supply_core.plataforma.entity.EmpresaModulo;
import com.proyecto.supply_core.plataforma.entity.Modulo;
import com.proyecto.supply_core.plataforma.entity.Plan;
import com.proyecto.supply_core.plataforma.entity.Suscripcion;
import com.proyecto.supply_core.plataforma.enums.AccionPlataforma;
import com.proyecto.supply_core.plataforma.enums.EstadoSuscripcion;
import com.proyecto.supply_core.plataforma.repository.EmpresaModuloRepository;
import com.proyecto.supply_core.plataforma.repository.ModuloRepository;
import com.proyecto.supply_core.plataforma.repository.PlanRepository;
import com.proyecto.supply_core.plataforma.repository.SuscripcionRepository;
import com.proyecto.supply_core.security.context.PlataformaActual;
import com.proyecto.supply_core.security.repository.RefreshTokenRepository;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

/**
 * Lo que el dueño de la plataforma hace con las empresas clientes: darlas de alta con su administrador, cambiarles el
 * plan, darles o quitarles módulos aparte del plan y cambiar el estado de su suscripción.
 * <p>Trabaja solo con empresa, suscripción, plan y módulos: nunca lee ni escribe datos de negocio de una empresa
 * (el único dato de empresa que crea es su administrador inicial, y lo hace dentro del contexto de esa empresa). Cada
 * acción queda en la auditoría global y limpia lo que el interceptor recuerda de la empresa.</p>
 * <p>Estados: la plataforma puede pasar una suscripción a ACTIVA, MOROSA, SUSPENDIDA o CANCELADA siguiendo
 * {@link EstadoSuscripcion#puedePasarA}; MOROSA, SUSPENDIDA y CANCELADA piden motivo. Al suspender o cancelar se
 * revocan las sesiones renovables de la empresa.</p>
 */
@Service
public class EmpresaPlataformaService {

    private static final Logger log = LoggerFactory.getLogger(EmpresaPlataformaService.class);
    private static final Set<EstadoSuscripcion> ESTADOS_QUE_PIDEN_MOTIVO = Set.of(EstadoSuscripcion.MOROSA,
            EstadoSuscripcion.SUSPENDIDA, EstadoSuscripcion.CANCELADA);

    private final EmpresaRepository empresaRepository;
    private final SuscripcionRepository suscripcionRepository;
    private final PlanRepository planRepository;
    private final ModuloRepository moduloRepository;
    private final EmpresaModuloRepository empresaModuloRepository;
    private final EmpresaAltaService altaService;
    private final AccesoEmpresaService accesoEmpresa;
    private final PlataformaAuditoriaService auditoria;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UsuarioRepository usuarioRepository;

    /**
     * @param empresaRepository       empresas
     * @param suscripcionRepository   suscripciones
     * @param planRepository          planes
     * @param moduloRepository        catálogo de módulos
     * @param empresaModuloRepository excepciones de módulo
     * @param altaService             pasos transaccionales del alta
     * @param accesoEmpresa           memoria de acceso que se limpia al cambiar algo
     * @param auditoria               auditoría global
     * @param refreshTokenRepository  sesiones renovables que se revocan al suspender
     * @param usuarioRepository       solo para mostrar quién administra cada empresa (nombre y correo)
     */
    public EmpresaPlataformaService(EmpresaRepository empresaRepository, SuscripcionRepository suscripcionRepository,
            PlanRepository planRepository, ModuloRepository moduloRepository,
            EmpresaModuloRepository empresaModuloRepository, EmpresaAltaService altaService,
            AccesoEmpresaService accesoEmpresa, PlataformaAuditoriaService auditoria,
            RefreshTokenRepository refreshTokenRepository, UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
        this.empresaRepository = empresaRepository;
        this.suscripcionRepository = suscripcionRepository;
        this.planRepository = planRepository;
        this.moduloRepository = moduloRepository;
        this.empresaModuloRepository = empresaModuloRepository;
        this.altaService = altaService;
        this.accesoEmpresa = accesoEmpresa;
        this.auditoria = auditoria;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * Lista las empresas con su plan y estado, la más nueva primero.
     *
     * @return empresas
     */
    @Transactional(readOnly = true)
    public List<EmpresaPlataformaResponse> listar() {
        Map<Long, Empresa> empresas = empresaRepository.findAll().stream()
                .collect(Collectors.toMap(Empresa::getId, Function.identity()));
        return suscripcionRepository.findAllByOrderByEmpresaIdDesc().stream()
                .filter(s -> empresas.containsKey(s.getEmpresaId()))
                .map(s -> EmpresaPlataformaResponse.of(empresas.get(s.getEmpresaId()), s)).toList();
    }

    /**
     * Detalle de una empresa con la situación de cada módulo.
     *
     * @param id empresa
     * @return empresa y módulos
     * @throws NoEncontradoException si no existe o no tiene suscripción
     */
    @Transactional(readOnly = true)
    public EmpresaDetalleResponse obtener(Long id) {
        return detalle(empresa(id), suscripcion(id));
    }

    /**
     * Da de alta una empresa con su suscripción y su administrador inicial.
     *
     * @param req datos del alta
     * @return empresa creada con su situación de módulos
     * @throws NegocioException si el NIT o el código ya existen, el plan no existe o no está activo, o el estado
     *                          inicial no es PRUEBA ni ACTIVA
     */
    public EmpresaDetalleResponse crear(EmpresaCrearRequest req) {
        Plan plan = planRepository.findByCodigoIgnoreCase(req.planCodigo().trim())
                .filter(Plan::isActivo)
                .orElseThrow(() -> new NegocioException("PLAN_INVALIDO",
                        "El plan " + req.planCodigo() + " no existe o no está activo"));
        EstadoSuscripcion estado = req.estadoInicial() == null ? EstadoSuscripcion.PRUEBA : req.estadoInicial();
        if (estado != EstadoSuscripcion.PRUEBA && estado != EstadoSuscripcion.ACTIVA) {
            throw new NegocioException("ESTADO_INICIAL_INVALIDO", "Una empresa nueva empieza en PRUEBA o ACTIVA");
        }
        String nit = req.nit().trim();
        if (empresaRepository.existsByNitIgnoreCase(nit)) {
            throw new NegocioException("NIT_DUPLICADO", "Ya existe una empresa con el NIT " + nit);
        }
        if (EmpresaContexto.sinEmpresa(() -> empresaRepository.idPorCodigoPublico(EmpresaAltaService.normalizarCodigo(req.codigoPublico())))
                .isPresent()) {
            throw new NegocioException("CODIGO_DUPLICADO", "Ya existe una empresa con el código " + EmpresaAltaService.normalizarCodigo(req.codigoPublico()));
        }
        Empresa empresa = altaService.crearEmpresa(req, plan, estado);
        try {
            EmpresaContexto.como(empresa.getId(),
                    () -> altaService.sembrarAdministrador(req.adminNombre(), req.adminCorreo(), req.adminPassword()));
        } catch (RuntimeException e) {
            log.error("Falló el alta de la empresa {}: {}; se deshace", empresa.getId(), e.getMessage(), e);
            altaService.deshacer(empresa.getId());
            auditoria.registrar(PlataformaActual.usuario().id(), AccionPlataforma.EMPRESA_CREAR, null, false,
                    "nit=" + nit + "; motivo=" + e.getMessage());
            throw e;
        }
        auditoria.registrar(PlataformaActual.usuario().id(), AccionPlataforma.EMPRESA_CREAR, empresa.getId(), true,
                "nit=" + nit + "; codigo=" + empresa.getCodigoPublico() + "; plan=" + plan.getCodigo() + "; estado="
                        + estado + "; admin=" + req.adminCorreo().trim().toLowerCase());
        return obtener(empresa.getId());
    }

    /**
     * Cambia el plan de una empresa.
     *
     * @param id         empresa
     * @param planCodigo plan nuevo
     * @return empresa con su situación de módulos
     * @throws NegocioException      si el plan no existe o no está activo
     * @throws NoEncontradoException si la empresa no existe
     */
    @Transactional
    public EmpresaDetalleResponse cambiarPlan(Long id, String planCodigo) {
        Suscripcion s = suscripcion(id);
        Plan plan = planRepository.findByCodigoIgnoreCase(planCodigo.trim()).filter(Plan::isActivo)
                .orElseThrow(() -> new NegocioException("PLAN_INVALIDO",
                        "El plan " + planCodigo + " no existe o no está activo"));
        String antes = s.getPlan().getCodigo();
        s.setPlan(plan);
        s.setActualizadaEn(LocalDateTime.now());
        accesoEmpresa.invalidar(id);
        auditoria.registrar(PlataformaActual.usuario().id(), AccionPlataforma.EMPRESA_CAMBIAR_PLAN, id, true,
                "plan " + antes + " -> " + plan.getCodigo());
        return detalle(empresa(id), s);
    }

    /**
     * Da, quita o restablece un módulo de una empresa aparte de su plan.
     *
     * @param id           empresa
     * @param moduloCodigo módulo
     * @param habilitado   {@code true} lo da, {@code false} lo quita, {@code null} borra la excepción
     * @return empresa con su situación de módulos
     * @throws NegocioException      si el módulo no existe
     * @throws NoEncontradoException si la empresa no existe
     */
    @Transactional
    public EmpresaDetalleResponse excepcionDeModulo(Long id, String moduloCodigo, Boolean habilitado) {
        Suscripcion s = suscripcion(id);
        String codigo = moduloCodigo.trim().toUpperCase();
        if (!moduloRepository.existsById(codigo)) {
            throw new NegocioException("MODULO_INEXISTENTE", "El módulo " + moduloCodigo + " no existe");
        }
        var existente = empresaModuloRepository.findByEmpresaIdAndModuloCodigo(id, codigo);
        if (habilitado == null) {
            existente.ifPresent(empresaModuloRepository::delete);
        } else if (existente.isPresent()) {
            existente.get().setHabilitado(habilitado);
        } else {
            EmpresaModulo nueva = new EmpresaModulo();
            nueva.setEmpresaId(id);
            nueva.setModuloCodigo(codigo);
            nueva.setHabilitado(habilitado);
            empresaModuloRepository.save(nueva);
        }
        empresaModuloRepository.flush();
        accesoEmpresa.invalidar(id);
        auditoria.registrar(PlataformaActual.usuario().id(), AccionPlataforma.EMPRESA_MODULO, id, true,
                "modulo=" + codigo + "; excepcion=" + (habilitado == null ? "ninguna" : habilitado));
        return detalle(empresa(id), s);
    }

    /**
     * Cambia el estado de la suscripción (suspender, reactivar, marcar morosa o cancelar).
     *
     * @param id     empresa
     * @param estado estado nuevo
     * @param motivo motivo (obligatorio para MOROSA, SUSPENDIDA y CANCELADA)
     * @return empresa con su situación de módulos
     * @throws ParametroInvalidoException si falta el motivo
     * @throws NegocioException           si la transición no está permitida
     * @throws NoEncontradoException      si la empresa no existe
     */
    @Transactional
    public EmpresaDetalleResponse cambiarEstado(Long id, EstadoSuscripcion estado, String motivo) {
        Suscripcion s = suscripcion(id);
        String texto = motivo == null || motivo.isBlank() ? null : motivo.trim();
        if (ESTADOS_QUE_PIDEN_MOTIVO.contains(estado) && texto == null) {
            throw new ParametroInvalidoException("motivo: es obligatorio para pasar a " + estado);
        }
        if (!s.getEstado().puedePasarA(estado)) {
            throw new NegocioException("TRANSICION_INVALIDA",
                    "Una suscripción " + s.getEstado() + " no puede pasar a " + estado);
        }
        EstadoSuscripcion antes = s.getEstado();
        s.setEstado(estado);
        s.setMotivoEstado(texto);
        s.setActualizadaEn(LocalDateTime.now());
        if (!estado.permiteAcceso()) {
            refreshTokenRepository.revocarDeEmpresa(id, LocalDateTime.now());
        }
        accesoEmpresa.invalidar(id);
        auditoria.registrar(PlataformaActual.usuario().id(), AccionPlataforma.EMPRESA_ESTADO, id, true,
                antes + " -> " + estado + (texto == null ? "" : "; motivo=" + texto));
        return detalle(empresa(id), s);
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Busca la empresa.
     *
     * @param id empresa
     * @return empresa
     * @throws NoEncontradoException si no existe
     */
    private Empresa empresa(Long id) {
        return empresaRepository.findById(id).orElseThrow(() -> new NoEncontradoException("Empresa", id));
    }

    /**
     * Busca la suscripción de la empresa.
     *
     * @param id empresa
     * @return suscripción
     * @throws NoEncontradoException si no existe
     */
    private Suscripcion suscripcion(Long id) {
        empresa(id);
        return suscripcionRepository.findByEmpresaId(id)
                .orElseThrow(() -> new NoEncontradoException("Suscripción de la empresa", id));
    }

    /**
     * Arma el detalle con la situación de cada módulo del catálogo.
     *
     * @param e empresa
     * @param s suscripción
     * @return detalle
     */
    private EmpresaDetalleResponse detalle(Empresa e, Suscripcion s) {
        Map<String, Boolean> excepciones = empresaModuloRepository.findByEmpresaId(e.getId()).stream()
                .collect(Collectors.toMap(EmpresaModulo::getModuloCodigo, EmpresaModulo::isHabilitado));
        Set<String> delPlan = new HashSet<>(s.getPlan().getModulos());
        List<EmpresaDetalleResponse.ModuloEmpresa> modulos = moduloRepository.findAllByOrderByCodigoAsc().stream()
                .map((Modulo m) -> {
                    boolean enPlan = delPlan.contains(m.getCodigo());
                    Boolean excepcion = excepciones.get(m.getCodigo());
                    return new EmpresaDetalleResponse.ModuloEmpresa(m.getCodigo(), m.getNombre(), enPlan, excepcion,
                            excepcion != null ? excepcion : enPlan);
                }).toList();
        EmpresaDetalleResponse.Administrador administrador = usuarioRepository.administradorDe(e.getId()).stream()
                .findFirst().map(a -> new EmpresaDetalleResponse.Administrador(a.getNombre(), a.getCorreo()))
                .orElse(null);
        return new EmpresaDetalleResponse(EmpresaPlataformaResponse.of(e, s), modulos, administrador);
    }
}
