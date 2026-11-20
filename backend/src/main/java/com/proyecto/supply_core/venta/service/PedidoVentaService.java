package com.proyecto.supply_core.venta.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.catalogo.entity.UnidadMedida;
import com.proyecto.supply_core.catalogo.repository.UnidadMedidaRepository;
import com.proyecto.supply_core.cliente.entity.Cliente;
import com.proyecto.supply_core.cliente.repository.ClienteRepository;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.util.ConsecutivoGenerator;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;
import com.proyecto.supply_core.venta.dto.LineaVentaRequest;
import com.proyecto.supply_core.venta.dto.PedidoVentaLineaResponse;
import com.proyecto.supply_core.venta.dto.PedidoVentaRequest;
import com.proyecto.supply_core.venta.dto.PedidoVentaResponse;
import com.proyecto.supply_core.venta.entity.PedidoVenta;
import com.proyecto.supply_core.venta.entity.PedidoVentaLinea;
import com.proyecto.supply_core.venta.enums.EstadoPedidoVenta;
import com.proyecto.supply_core.venta.repository.PedidoVentaRepository;

/**
 * Pedido de venta sin cobrar (spec 004): el vendedor atiende al cliente y arma el pedido; el cajero lo carga en el
 * POS y lo cobra. El pedido no toca el stock ni la caja, ni guarda montos: al cobrarlo la venta toma los precios del
 * día. Solo se edita o descarta mientras está pendiente, por quien lo armó o un supervisor.
 */
@Service
public class PedidoVentaService {

    private final PedidoVentaRepository pedidoRepository;
    private final ProductoRepository productoRepository;
    private final UnidadMedidaRepository unidadRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final VentaService ventaService;
    private final PreciosService preciosService;
    private final ConsecutivoGenerator consecutivos;
    private final AuditoriaService auditoriaService;

    /**
     * @param pedidoRepository   pedidos
     * @param productoRepository productos
     * @param unidadRepository   unidades
     * @param clienteRepository  clientes
     * @param usuarioRepository  nombres de vendedores
     * @param ventaService       cliente activo o consumidor final
     * @param preciosService     unidades válidas para un producto
     * @param consecutivos       consecutivo {@code PV}
     * @param auditoriaService   registro de cambios
     */
    public PedidoVentaService(PedidoVentaRepository pedidoRepository, ProductoRepository productoRepository,
            UnidadMedidaRepository unidadRepository, ClienteRepository clienteRepository,
            UsuarioRepository usuarioRepository, VentaService ventaService, PreciosService preciosService,
            ConsecutivoGenerator consecutivos, AuditoriaService auditoriaService) {
        this.pedidoRepository = pedidoRepository;
        this.productoRepository = productoRepository;
        this.unidadRepository = unidadRepository;
        this.clienteRepository = clienteRepository;
        this.usuarioRepository = usuarioRepository;
        this.ventaService = ventaService;
        this.preciosService = preciosService;
        this.consecutivos = consecutivos;
        this.auditoriaService = auditoriaService;
    }

    /**
     * @param req cliente, líneas y nota
     * @return pedido pendiente de cobro
     * @throws NoEncontradoException si el cliente o un producto no son de la empresa
     * @throws NegocioException      si el cliente está inactivo o un producto no se vende
     */
    @Transactional
    public PedidoVentaResponse crear(PedidoVentaRequest req) {
        PedidoVenta p = new PedidoVenta();
        p.setConsecutivo(consecutivos.siguiente(ConsecutivoGenerator.PEDIDO_VENTA));
        p.setVendedorUsuarioId(SesionActual.usuario().id());
        p.setCreadoEn(LocalDateTime.now());
        llenar(p, req);
        pedidoRepository.save(p);
        auditoriaService.exito(AccionAuditoria.PEDIDO_VENTA_CREAR, EntidadAuditada.PEDIDO_VENTA, p.getId(),
                p.getConsecutivo() + "; lineas=" + p.getLineas().size());
        return respuesta(p);
    }

    /**
     * @param id  pedido
     * @param req cliente, líneas y nota nuevos
     * @return pedido editado
     * @throws NegocioException        si ya no está pendiente
     * @throws AccesoDenegadoException si no lo armó la sesión ni es supervisor
     */
    @Transactional
    public PedidoVentaResponse editar(Long id, PedidoVentaRequest req) {
        PedidoVenta p = pendiente(id);
        p.getLineas().clear();
        pedidoRepository.flush();
        llenar(p, req);
        p.setActualizadoEn(LocalDateTime.now());
        auditoriaService.exito(AccionAuditoria.PEDIDO_VENTA_EDITAR, EntidadAuditada.PEDIDO_VENTA, p.getId(),
                p.getConsecutivo() + "; lineas=" + p.getLineas().size());
        return respuesta(p);
    }

    /**
     * @param id pedido
     * @return pedido descartado
     * @throws NegocioException        si ya no está pendiente
     * @throws AccesoDenegadoException si no lo armó la sesión ni es supervisor
     */
    @Transactional
    public PedidoVentaResponse descartar(Long id) {
        PedidoVenta p = pendiente(id);
        p.setEstado(EstadoPedidoVenta.DESCARTADO);
        p.setActualizadoEn(LocalDateTime.now());
        auditoriaService.exito(AccionAuditoria.PEDIDO_VENTA_DESCARTAR, EntidadAuditada.PEDIDO_VENTA, p.getId(),
                p.getConsecutivo());
        return respuesta(p);
    }

    /**
     * @param estado   estado ({@code null} = todos)
     * @param pageable página y orden
     * @return pedidos; el vendedor sin otro rol de venta solo ve los suyos
     */
    @Transactional(readOnly = true)
    public PageResponse<PedidoVentaResponse> buscar(EstadoPedidoVenta estado, Pageable pageable) {
        Long vendedor = soloVendedor() ? SesionActual.usuario().id() : null;
        return PageResponse.of(pedidoRepository.buscar(estado, vendedor, pageable), this::respuesta);
    }

    /**
     * @param id pedido
     * @return pedido
     * @throws NoEncontradoException si no es de la empresa o es de otro vendedor y la sesión solo vende
     */
    @Transactional(readOnly = true)
    public PedidoVentaResponse obtener(Long id) {
        PedidoVenta p = pedidoRepository.findById(id).orElseThrow(() -> new NoEncontradoException("Pedido no encontrado"));
        if (soloVendedor() && !p.getVendedorUsuarioId().equals(SesionActual.usuario().id())) {
            throw new NoEncontradoException("Pedido no encontrado");
        }
        return respuesta(p);
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * @return {@code true} si la sesión vende pero no cobra ni supervisa
     */
    private static boolean soloVendedor() {
        Set<String> roles = SesionActual.usuario().roles();
        return roles.contains(Roles.VENDEDOR) && !roles.contains(Roles.CAJERO) && !roles.contains(Roles.SUPERVISOR_VENTAS)
                && !roles.contains(Roles.ADMIN_SISTEMA);
    }

    /**
     * @param id pedido
     * @return el pedido bloqueado, pendiente y que la sesión puede cambiar
     */
    private PedidoVenta pendiente(Long id) {
        PedidoVenta p = pedidoRepository.bloquear(id).orElseThrow(() -> new NoEncontradoException("Pedido no encontrado"));
        Set<String> roles = SesionActual.usuario().roles();
        boolean supervisa = roles.contains(Roles.SUPERVISOR_VENTAS) || roles.contains(Roles.ADMIN_SISTEMA);
        if (!supervisa && !p.getVendedorUsuarioId().equals(SesionActual.usuario().id())) {
            throw new AccesoDenegadoException("Solo quien armó el pedido o un supervisor lo cambia");
        }
        if (p.getEstado() != EstadoPedidoVenta.PENDIENTE_COBRO) {
            throw new NegocioException("PEDIDO_NO_PENDIENTE", "El pedido " + p.getConsecutivo() + " ya no está pendiente");
        }
        return p;
    }

    /**
     * @param p   pedido
     * @param req datos
     */
    private void llenar(PedidoVenta p, PedidoVentaRequest req) {
        p.setClienteId(ventaService.cliente(req.clienteId()).getId());
        p.setNota(req.nota() == null || req.nota().isBlank() ? null : req.nota().trim());
        Map<Long, Producto> productos = productoRepository.findAllById(req.lineas().stream()
                .map(LineaVentaRequest::productoId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Producto::getId, Function.identity()));
        for (LineaVentaRequest l : req.lineas()) {
            Producto producto = productos.get(l.productoId());
            if (producto == null) {
                throw new NoEncontradoException("Producto no encontrado");
            }
            if (!producto.isActivo() || !producto.isVendible()) {
                throw new NegocioException("PRODUCTO_NO_VENDIBLE", "El producto " + producto.getCodigo() + " no se vende");
            }
            PedidoVentaLinea x = new PedidoVentaLinea();
            x.setPedido(p);
            x.setProductoId(producto.getId());
            x.setUnidadMedidaId(preciosService.unidadDelPrecio(producto, l.unidadMedidaId()).getId());
            x.setCantidad(l.cantidad());
            x.setDescuentoPct(l.descuentoPct() == null ? java.math.BigDecimal.ZERO : l.descuentoPct());
            p.getLineas().add(x);
        }
    }

    /**
     * @param p pedido
     * @return respuesta con nombres
     */
    private PedidoVentaResponse respuesta(PedidoVenta p) {
        Map<Long, Producto> productos = productoRepository.findAllById(p.getLineas().stream()
                .map(PedidoVentaLinea::getProductoId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Producto::getId, Function.identity()));
        Set<Long> unidades = new HashSet<>();
        p.getLineas().forEach(l -> unidades.add(l.getUnidadMedidaId()));
        Map<Long, String> codigos = unidadRepository.findAllById(unidades).stream()
                .collect(Collectors.toMap(UnidadMedida::getId, UnidadMedida::getCodigo));
        String cliente = clienteRepository.findById(p.getClienteId()).map(Cliente::getNombre).orElse(null);
        String vendedor = usuarioRepository.findById(p.getVendedorUsuarioId()).map(Usuario::getNombreCompleto).orElse(null);
        List<PedidoVentaLineaResponse> lineas = p.getLineas().stream().map(l -> {
            Producto x = productos.get(l.getProductoId());
            return new PedidoVentaLineaResponse(l.getProductoId(), x == null ? null : x.getCodigo(),
                    x == null ? null : x.getNombre(), l.getUnidadMedidaId(), codigos.get(l.getUnidadMedidaId()),
                    l.getCantidad(), l.getDescuentoPct());
        }).toList();
        return new PedidoVentaResponse(p.getId(), p.getConsecutivo(), p.getEstado(), p.getClienteId(), cliente, vendedor,
                p.getNota(), p.getVentaId(), p.getCreadoEn(), lineas);
    }
}
