package com.proyecto.supply_core.despacho.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.catalogo.entity.UnidadMedida;
import com.proyecto.supply_core.catalogo.repository.UnidadMedidaRepository;
import com.proyecto.supply_core.catalogo.service.UnidadMedidaService;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.util.ConsecutivoGenerator;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.common.util.Ordenamiento;
import com.proyecto.supply_core.despacho.dto.DespachoRequest;
import com.proyecto.supply_core.despacho.dto.DespachoResponse;
import com.proyecto.supply_core.despacho.dto.DespachoResumenResponse;
import com.proyecto.supply_core.despacho.dto.LineaDespachoRequest;
import com.proyecto.supply_core.despacho.entity.Despacho;
import com.proyecto.supply_core.despacho.entity.DespachoLinea;
import com.proyecto.supply_core.despacho.enums.EstadoDespacho;
import com.proyecto.supply_core.despacho.repository.DespachoRepository;
import com.proyecto.supply_core.inventario.dto.RegistroMovimientoResponse;
import com.proyecto.supply_core.inventario.entity.Bodega;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.BodegaRepository;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.inventario.service.MovimientoInventarioService;
import com.proyecto.supply_core.security.context.SesionActual;

/**
 * Despachos (remisiones de salida): lo que se vende o entrega sale del stock con un documento.
 * <ul>
 *   <li>En BORRADOR se arma y se edita sin tocar el stock.</li>
 *   <li>Al confirmar, cada línea se convierte a la unidad del producto y se registra una salida
 *       SALIDA_VENTA, todo en una transacción: si un producto no alcanza, no sale nada.</li>
 *   <li>Las líneas se procesan en orden de producto para que dos despachos simultáneos bloqueen
 *       el stock en el mismo orden y no se interbloqueen.</li>
 *   <li>Anular un despacho confirmado devuelve el stock con ANULACION_DESPACHO (con motivo).</li>
 * </ul>
 * <p>El stock solo lo cambia {@link MovimientoInventarioService}; aquí no se toca directamente.</p>
 */
@Service
public class DespachoService {

    private static final Logger log = LoggerFactory.getLogger(DespachoService.class);
    /** Tipo de documento que se guarda como referencia en los movimientos. */
    static final String REFERENCIA = "DESPACHO";
    /** Campos por los que se puede ordenar. */
    private static final Set<String> CAMPOS_ORDENABLES = Set.of("creadoEn", "confirmadoEn", "consecutivo", "estado");

    private final DespachoRepository despachoRepository;
    private final BodegaRepository bodegaRepository;
    private final ProductoRepository productoRepository;
    private final UnidadMedidaRepository unidadRepository;
    private final UnidadMedidaService unidadMedidaService;
    private final MovimientoInventarioService movimientoService;
    private final ConsecutivoGenerator consecutivos;
    private final AuditoriaService auditoriaService;

    /**
     * @param despachoRepository  despachos
     * @param bodegaRepository    bodegas
     * @param productoRepository  productos de las líneas
     * @param unidadRepository    unidades de las líneas
     * @param unidadMedidaService conversión a la unidad del producto
     * @param movimientoService   único que modifica el stock
     * @param consecutivos        números DES-000001
     * @param auditoriaService    registro de acciones
     */
    public DespachoService(DespachoRepository despachoRepository, BodegaRepository bodegaRepository,
            ProductoRepository productoRepository, UnidadMedidaRepository unidadRepository,
            UnidadMedidaService unidadMedidaService, MovimientoInventarioService movimientoService,
            ConsecutivoGenerator consecutivos, AuditoriaService auditoriaService) {
        this.despachoRepository = despachoRepository;
        this.bodegaRepository = bodegaRepository;
        this.productoRepository = productoRepository;
        this.unidadRepository = unidadRepository;
        this.unidadMedidaService = unidadMedidaService;
        this.movimientoService = movimientoService;
        this.consecutivos = consecutivos;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Crea un despacho en BORRADOR (no toca el stock).
     *
     * @param req datos validados
     * @return despacho creado
     * @throws NegocioException si la bodega está inactiva o una unidad no mide lo mismo que su producto
     */
    @Transactional
    public DespachoResponse crear(DespachoRequest req) {
        Despacho d = new Despacho();
        d.setUsuarioId(SesionActual.usuario().id());
        d.setConsecutivo(consecutivos.siguiente(ConsecutivoGenerator.DESPACHO));
        copiar(d, req);
        Despacho guardado = despachoRepository.save(d);
        auditoriaService.exito(AccionAuditoria.DESPACHO_CREAR, EntidadAuditada.DESPACHO, guardado.getId(),
                guardado.getConsecutivo() + "; lineas=" + guardado.getLineas().size());
        return DespachoResponse.of(guardado, List.of());
    }

    /**
     * Edita un despacho en BORRADOR (reemplaza bodega, datos y líneas).
     *
     * @param id  id del despacho
     * @param req datos validados
     * @return despacho actualizado
     * @throws NegocioException si ya no está en BORRADOR
     */
    @Transactional
    public DespachoResponse actualizar(Long id, DespachoRequest req) {
        Despacho d = bloquear(id);
        exigirBorrador(d, "editar");
        d.getLineas().clear();
        despachoRepository.flush(); // borra las líneas viejas antes de insertar las nuevas (UNIQUE despacho+producto)
        copiar(d, req);
        auditoriaService.exito(AccionAuditoria.DESPACHO_EDITAR, EntidadAuditada.DESPACHO, id,
                d.getConsecutivo() + "; lineas=" + d.getLineas().size());
        return DespachoResponse.of(d, List.of());
    }

    /**
     * Confirma el despacho: descuenta del stock todas las líneas en una sola transacción.
     *
     * @param id id del despacho
     * @return despacho confirmado, con los productos que quedaron bajo su mínimo
     * @throws NegocioException si no está en BORRADOR, la bodega está inactiva o algún producto no
     *                          tiene stock suficiente (STOCK_INSUFICIENTE: no se descuenta nada)
     */
    @Transactional
    public DespachoResponse confirmar(Long id) {
        Despacho d = bloquear(id);
        exigirBorrador(d, "confirmar");
        bodegaActiva(d.getBodega().getId());
        List<String> bajoMinimo = new ArrayList<>();
        List<DespachoLinea> enOrden = d.getLineas().stream()
                .sorted(Comparator.comparing(l -> l.getProducto().getId())).toList();
        for (DespachoLinea l : enOrden) {
            l.setCantidadBase(unidadMedidaService.convertir(l.getCantidad(), l.getUnidadMedida().getId(),
                    l.getProducto().getUnidadMedida().getId()));
            RegistroMovimientoResponse r = movimientoService.registrarSalidaVenta(l.getProducto().getId(),
                    d.getBodega().getId(), l.getCantidadBase(), REFERENCIA, d.getId());
            if (r.bajoStockMinimo()) {
                bajoMinimo.add(l.getProducto().getCodigo());
            }
        }
        d.setEstado(EstadoDespacho.CONFIRMADO);
        d.setConfirmadoEn(LocalDateTime.now());
        auditoriaService.exito(AccionAuditoria.DESPACHO_CONFIRMAR, EntidadAuditada.DESPACHO, id,
                d.getConsecutivo() + "; bodega=" + d.getBodega().getNombre() + "; lineas=" + d.getLineas().size());
        log.info("Despacho {} confirmado: {} líneas desde {}", d.getConsecutivo(), d.getLineas().size(),
                d.getBodega().getNombre());
        return DespachoResponse.of(d, bajoMinimo);
    }

    /**
     * Anula un despacho. Si estaba CONFIRMADO, devuelve al stock lo que se había descontado.
     *
     * @param id     id del despacho
     * @param motivo motivo validado
     * @return despacho anulado
     * @throws NegocioException si ya estaba anulado
     */
    @Transactional
    public DespachoResponse anular(Long id, String motivo) {
        Despacho d = bloquear(id);
        if (d.getEstado() == EstadoDespacho.ANULADO) {
            throw new NegocioException("TRANSICION_INVALIDA", "El despacho " + d.getConsecutivo() + " ya está anulado");
        }
        String texto = motivo.trim();
        EstadoDespacho anterior = d.getEstado();
        if (anterior == EstadoDespacho.CONFIRMADO) {
            d.getLineas().stream().sorted(Comparator.comparing(l -> l.getProducto().getId()))
                    .forEach(l -> movimientoService.registrarAnulacionDespacho(l.getProducto().getId(),
                            d.getBodega().getId(), l.getCantidadBase(), "Anulación " + d.getConsecutivo() + ": " + texto,
                            REFERENCIA, d.getId()));
        }
        d.setEstado(EstadoDespacho.ANULADO);
        d.setAnuladoEn(LocalDateTime.now());
        d.setAnuladoPorUsuarioId(SesionActual.usuario().id());
        d.setMotivoAnulacion(texto);
        auditoriaService.exito(AccionAuditoria.DESPACHO_ANULAR, EntidadAuditada.DESPACHO, id,
                d.getConsecutivo() + "; estado anterior=" + anterior + "; motivo=" + texto);
        return DespachoResponse.of(d, List.of());
    }

    /**
     * Detalle de un despacho.
     *
     * @param id id del despacho
     * @return despacho con líneas
     * @throws NoEncontradoException si no existe
     */
    @Transactional(readOnly = true)
    public DespachoResponse obtener(Long id) {
        return DespachoResponse.of(despachoRepository.findWithDetalleById(id)
                .orElseThrow(() -> new NoEncontradoException("Despacho", id)), List.of());
    }

    /**
     * Lista despachos con filtros.
     *
     * @param texto    coincidencia en consecutivo, cliente o referencia externa
     * @param estado   estado
     * @param bodegaId bodega
     * @param pageable página y orden
     * @return página de despachos
     */
    @Transactional(readOnly = true)
    public PageResponse<DespachoResumenResponse> buscar(String texto, EstadoDespacho estado, Long bodegaId,
            Pageable pageable) {
        String filtro = texto == null || texto.isBlank() ? null : texto.trim();
        return PageResponse.of(despachoRepository.buscar(filtro, estado, bodegaId,
                Ordenamiento.validar(pageable, CAMPOS_ORDENABLES)), DespachoResumenResponse::of);
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Copia bodega, datos y líneas del request. Verifica que cada unidad mida lo mismo que la del producto.
     *
     * @param d   despacho destino (sin líneas)
     * @param req datos validados
     */
    private void copiar(Despacho d, DespachoRequest req) {
        d.setBodega(bodegaActiva(req.bodegaId()));
        d.setCliente(textoOpcional(req.cliente()));
        d.setReferenciaExterna(textoOpcional(req.referenciaExterna()));
        d.setObservaciones(textoOpcional(req.observaciones()));
        for (LineaDespachoRequest l : req.lineas()) {
            Producto producto = productoRepository.findWithRelacionesById(l.productoId())
                    .filter(Producto::isActivo)
                    .orElseThrow(() -> new NegocioException("PRODUCTO_INACTIVO",
                            "El producto " + l.productoId() + " no existe o está inactivo"));
            UnidadMedida unidad = unidadRepository.findById(l.unidadMedidaId())
                    .orElseThrow(() -> new NoEncontradoException("Unidad de medida", l.unidadMedidaId()));
            // lanza 409 UNIDADES_INCOMPATIBLES si la unidad no mide lo mismo que la del producto
            unidadMedidaService.convertir(l.cantidad(), unidad.getId(), producto.getUnidadMedida().getId());
            DespachoLinea linea = new DespachoLinea();
            linea.setProducto(producto);
            linea.setUnidadMedida(unidad);
            linea.setCantidad(l.cantidad());
            d.agregarLinea(linea);
        }
    }

    /**
     * Bodega existente y activa.
     *
     * @param id id de la bodega
     * @return bodega
     * @throws NoEncontradoException si no existe
     * @throws NegocioException      si está inactiva
     */
    private Bodega bodegaActiva(Long id) {
        Bodega b = bodegaRepository.findById(id).orElseThrow(() -> new NoEncontradoException("Bodega", id));
        if (!b.isActivo()) {
            throw new NegocioException("BODEGA_INACTIVA", "La bodega " + b.getNombre() + " está inactiva");
        }
        return b;
    }

    /**
     * Verifica que el despacho siga en BORRADOR.
     *
     * @param d         despacho
     * @param operacion operación (para el mensaje)
     * @throws NegocioException si no está en BORRADOR
     */
    private static void exigirBorrador(Despacho d, String operacion) {
        if (d.getEstado() != EstadoDespacho.BORRADOR) {
            throw new NegocioException("TRANSICION_INVALIDA", "Solo se puede " + operacion
                    + " un despacho en BORRADOR; el " + d.getConsecutivo() + " está " + d.getEstado());
        }
    }

    /**
     * Bloquea el despacho y lo carga con su detalle.
     *
     * @param id id del despacho
     * @return despacho bloqueado
     * @throws NoEncontradoException si no existe
     */
    private Despacho bloquear(Long id) {
        despachoRepository.bloquear(id).orElseThrow(() -> new NoEncontradoException("Despacho", id));
        return despachoRepository.findWithDetalleById(id).orElseThrow(() -> new NoEncontradoException("Despacho", id));
    }

    /**
     * Convierte texto vacío en {@code null}.
     *
     * @param texto texto recibido
     * @return texto sin espacios extremos o {@code null}
     */
    private static String textoOpcional(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
