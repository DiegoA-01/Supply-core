package com.proyecto.supply_core.pedido.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.util.ConsecutivoGenerator;
import com.proyecto.supply_core.common.util.Ordenamiento;
import com.proyecto.supply_core.empresa.repository.SedeRepository;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.pedido.dto.GestionPedidoRequest;
import com.proyecto.supply_core.pedido.dto.PedidoInternoRequest;
import com.proyecto.supply_core.pedido.dto.PedidoInternoResponse;
import com.proyecto.supply_core.pedido.entity.PedidoInterno;
import com.proyecto.supply_core.pedido.enums.EstadoPedidoInterno;
import com.proyecto.supply_core.pedido.repository.PedidoInternoRepository;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

/**
 * Pedidos internos ("Necesito este producto"). Cualquier usuario interno pide; solo compras ve todos los pedidos y
 * responde. El proveedor nunca se entera: la decisión de cotizar, trasladar o comprar es de compras.
 */
@Service
public class PedidoInternoService {

    /** Roles que ven todos los pedidos de la empresa. */
    static final Set<String> ROLES_QUE_VEN_TODO = Set.of(Roles.ADMIN_COMPRAS, Roles.COMPRADOR, Roles.AUDITOR,
            Roles.ADMIN_SISTEMA);
    /** Campos por los que se puede ordenar el listado. */
    private static final Set<String> CAMPOS_ORDENABLES = Set.of("creadoEn", "estado");

    private final PedidoInternoRepository pedidoRepository;
    private final ProductoRepository productoRepository;
    private final SedeRepository sedeRepository;
    private final UsuarioRepository usuarioRepository;
    private final ConsecutivoGenerator consecutivos;
    private final NotificacionService notificacionService;
    private final AuditoriaService auditoriaService;

    /**
     * @param pedidoRepository    pedidos
     * @param productoRepository  productos de la empresa
     * @param sedeRepository      sedes de la empresa
     * @param usuarioRepository   nombres de quien pide y de quien responde
     * @param consecutivos        numeración PED-000001
     * @param notificacionService avisos a compras y a quien pidió
     * @param auditoriaService    registro de pedidos y respuestas
     */
    public PedidoInternoService(PedidoInternoRepository pedidoRepository, ProductoRepository productoRepository,
            SedeRepository sedeRepository, UsuarioRepository usuarioRepository, ConsecutivoGenerator consecutivos,
            NotificacionService notificacionService, AuditoriaService auditoriaService) {
        this.pedidoRepository = pedidoRepository;
        this.productoRepository = productoRepository;
        this.sedeRepository = sedeRepository;
        this.usuarioRepository = usuarioRepository;
        this.consecutivos = consecutivos;
        this.notificacionService = notificacionService;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Crea el pedido y avisa a compras (comprador y jefe de compras).
     *
     * @param req qué hace falta
     * @return pedido PENDIENTE
     * @throws NoEncontradoException si el producto o la sede no son de la empresa
     */
    @Transactional
    public PedidoInternoResponse crear(PedidoInternoRequest req) {
        UsuarioAutenticado sesion = SesionActual.usuario();
        PedidoInterno p = new PedidoInterno();
        if (req.productoId() != null) {
            p.setProducto(productoRepository.findById(req.productoId())
                    .orElseThrow(() -> new NoEncontradoException("Producto no encontrado")));
        }
        if (req.sedeId() != null) {
            p.setSede(sedeRepository.findById(req.sedeId())
                    .orElseThrow(() -> new NoEncontradoException("Sede no encontrada")));
        }
        p.setConsecutivo(consecutivos.siguiente(ConsecutivoGenerator.PEDIDO_INTERNO));
        p.setUsuarioId(sesion.id());
        p.setDescripcion(limpio(req.descripcion()));
        p.setCantidad(req.cantidad());
        p.setNota(limpio(req.nota()));
        PedidoInterno guardado = pedidoRepository.save(p);

        String mensaje = sesion.nombre() + " necesita " + que(guardado)
                + (guardado.getSede() == null ? "" : " en " + guardado.getSede().getNombre())
                + (guardado.getNota() == null ? "" : ": " + guardado.getNota()) + " (" + guardado.getConsecutivo() + ")";
        notificacionService.notificarRol(Roles.COMPRADOR, EventoNotificacion.PEDIDO_INTERNO, mensaje);
        notificacionService.notificarRol(Roles.ADMIN_COMPRAS, EventoNotificacion.PEDIDO_INTERNO, mensaje);
        auditoriaService.exito(AccionAuditoria.PEDIDO_INTERNO_CREAR, EntidadAuditada.PEDIDO_INTERNO, guardado.getId(),
                guardado.getConsecutivo() + "; " + que(guardado));
        return respuesta(guardado, nombres(Set.of(guardado.getUsuarioId())));
    }

    /**
     * Pedidos de la empresa. Compras, auditoría y el administrador ven todos (o solo los suyos si lo piden); los demás
     * ven solo los suyos.
     *
     * @param soloMios {@code true} para ver solo los propios
     * @param estado   filtro por estado, opcional
     * @param pageable página
     * @return página de pedidos
     */
    @Transactional(readOnly = true)
    public PageResponse<PedidoInternoResponse> listar(boolean soloMios, EstadoPedidoInterno estado, Pageable pageable) {
        UsuarioAutenticado sesion = SesionActual.usuario();
        boolean veTodo = sesion.roles().stream().anyMatch(ROLES_QUE_VEN_TODO::contains);
        Long usuario = soloMios || !veTodo ? sesion.id() : null;
        Page<PedidoInterno> pagina = pedidoRepository.buscar(usuario, estado,
                Ordenamiento.validar(pageable, CAMPOS_ORDENABLES));
        Set<Long> ids = new HashSet<>();
        pagina.getContent().forEach(p -> {
            ids.add(p.getUsuarioId());
            if (p.getAtendidoPorUsuarioId() != null) {
                ids.add(p.getAtendidoPorUsuarioId());
            }
        });
        Map<Long, String> nombres = nombres(ids);
        return PageResponse.of(pagina, p -> respuesta(p, nombres));
    }

    /**
     * Compras toma, atiende o descarta un pedido y le avisa a quien lo pidió.
     *
     * @param id  pedido
     * @param req nuevo estado y respuesta
     * @return pedido actualizado
     * @throws NoEncontradoException si no es de la empresa
     * @throws NegocioException      si ya estaba cerrado, se pide volver a PENDIENTE o se descarta sin explicar
     */
    @Transactional
    public PedidoInternoResponse gestionar(Long id, GestionPedidoRequest req) {
        PedidoInterno p = pedidoRepository.findById(id)
                .orElseThrow(() -> new NoEncontradoException("Pedido no encontrado"));
        if (p.getEstado().cerrado()) {
            throw new NegocioException("PEDIDO_CERRADO", "El pedido " + p.getConsecutivo() + " ya está "
                    + p.getEstado().name().toLowerCase());
        }
        if (req.estado() == EstadoPedidoInterno.PENDIENTE) {
            throw new NegocioException("ESTADO_INVALIDO", "Un pedido no vuelve a pendiente");
        }
        String respuesta = limpio(req.respuesta());
        if (req.estado() == EstadoPedidoInterno.DESCARTADO && respuesta == null) {
            throw new NegocioException("RESPUESTA_REQUERIDA", "Explica por qué no se hará el pedido");
        }
        UsuarioAutenticado sesion = SesionActual.usuario();
        p.setEstado(req.estado());
        if (respuesta != null) {
            p.setRespuesta(respuesta);
        }
        p.setAtendidoPorUsuarioId(sesion.id());
        p.setAtendidoEn(LocalDateTime.now());
        String estado = switch (req.estado()) {
            case EN_GESTION -> "está en gestión";
            case ATENDIDO -> "fue atendido";
            default -> "no se hará";
        };
        if (!p.getUsuarioId().equals(sesion.id())) {
            notificacionService.notificar(p.getUsuarioId(), EventoNotificacion.PEDIDO_INTERNO_RESPONDIDO,
                    "Tu pedido " + p.getConsecutivo() + " (" + que(p) + ") " + estado
                            + (respuesta == null ? "" : ": " + respuesta));
        }
        auditoriaService.exito(AccionAuditoria.PEDIDO_INTERNO_GESTIONAR, EntidadAuditada.PEDIDO_INTERNO, id,
                p.getConsecutivo() + "; estado=" + req.estado());
        return respuesta(p, nombres(Set.of(p.getUsuarioId(), sesion.id())));
    }

    /**
     * @param p pedido
     * @return "5 UND de CAB-012 - Cable" o la descripción libre
     */
    private static String que(PedidoInterno p) {
        String cantidad = p.getCantidad() == null ? ""
                : p.getCantidad().stripTrailingZeros().toPlainString()
                        + (p.getProducto() == null ? "" : " " + p.getProducto().getUnidadMedida().getCodigo()) + " de ";
        return cantidad + (p.getProducto() != null ? p.getProducto().getCodigo() + " - " + p.getProducto().getNombre()
                : p.getDescripcion());
    }

    /**
     * @param texto texto escrito
     * @return el texto sin espacios de más, o {@code null} si quedó vacío
     */
    private static String limpio(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    /**
     * @param ids usuarios
     * @return nombre de cada usuario de la empresa
     */
    private Map<Long, String> nombres(Set<Long> ids) {
        Map<Long, String> nombres = new HashMap<>();
        usuarioRepository.findAllById(ids).forEach(u -> nombres.put(u.getId(), u.getNombreCompleto()));
        return nombres;
    }

    /**
     * @param p       pedido
     * @param nombres nombres de usuarios
     * @return respuesta de la API
     */
    private static PedidoInternoResponse respuesta(PedidoInterno p, Map<Long, String> nombres) {
        Producto pr = p.getProducto();
        return new PedidoInternoResponse(p.getId(), p.getConsecutivo(), p.getUsuarioId(),
                nombres.getOrDefault(p.getUsuarioId(), "—"), pr == null ? null : pr.getId(),
                pr == null ? null : pr.getCodigo() + " - " + pr.getNombre(), p.getDescripcion(), p.getCantidad(),
                pr == null ? null : pr.getUnidadMedida().getCodigo(), p.getSede() == null ? null : p.getSede().getId(),
                p.getSede() == null ? null : p.getSede().getNombre(), p.getNota(), p.getEstado(), p.getRespuesta(),
                p.getAtendidoPorUsuarioId() == null ? null : nombres.get(p.getAtendidoPorUsuarioId()),
                p.getAtendidoEn(), p.getCreadoEn());
    }
}
