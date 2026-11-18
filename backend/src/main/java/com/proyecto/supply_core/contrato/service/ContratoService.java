package com.proyecto.supply_core.contrato.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
import com.proyecto.supply_core.catalogo.service.UnidadMedidaService;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.util.Ordenamiento;
import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompra;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompraLinea;
import com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud;
import com.proyecto.supply_core.compra.solicitud.repository.SolicitudCompraRepository;
import com.proyecto.supply_core.contrato.dto.CalculoContrato;
import com.proyecto.supply_core.contrato.dto.ConsumoPrecio;
import com.proyecto.supply_core.contrato.dto.ContratoRequest;
import com.proyecto.supply_core.contrato.dto.ContratoResponse;
import com.proyecto.supply_core.contrato.dto.ContratoResumenResponse;
import com.proyecto.supply_core.contrato.dto.OfertaContratoResponse;
import com.proyecto.supply_core.contrato.entity.Contrato;
import com.proyecto.supply_core.contrato.entity.ContratoPrecio;
import com.proyecto.supply_core.contrato.enums.EstadoContrato;
import com.proyecto.supply_core.contrato.repository.ContratoRepository;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.proveedor.entity.Proveedor;
import com.proyecto.supply_core.proveedor.enums.EstadoProveedor;
import com.proyecto.supply_core.proveedor.repository.ProveedorRepository;
import com.proyecto.supply_core.security.context.SesionActual;

/**
 * Contratos con proveedores y precios pactados (paso 18-b).
 * <ul>
 *   <li>Un contrato tiene vigencia y, por producto, precio, impuesto y tope de cantidad. Los precios no se editan:
 *       para cambiarlos se anula el contrato y se crea otro. Solo se mueve la fecha de fin o se anula.</li>
 *   <li>Un mismo proveedor no puede tener dos contratos ACTIVOS cuya vigencia se cruce para el mismo producto.</li>
 *   <li>Un contrato vencido o anulado nunca se aplica. El vencimiento se calcula con las fechas, no con un job.</li>
 *   <li><b>Oferta:</b> para una solicitud APROBADA, Java valora cada contrato vigente al precio pactado y dice si
 *       cubre todo, si cabe en el tope y si no supera lo aprobado. No decide: la persona elige entre la orden
 *       directa y el RFQ.</li>
 *   <li>El tope descuenta lo pedido en órdenes vivas (se excluyen las canceladas y las reemplazadas).</li>
 * </ul>
 * <p>La emisión de la orden directa está en {@code OrdenDirectaService}.</p>
 */
@Service
public class ContratoService {

    /** Estados de orden que ya no consumen el tope de un contrato. */
    static final Set<EstadoOrden> ORDENES_NO_VIVAS = EnumSet.of(EstadoOrden.CANCELADA, EstadoOrden.REEMPLAZADA);
    /** Campos por los que se puede ordenar el listado. */
    private static final Set<String> CAMPOS_ORDENABLES = Set.of("creadoEn", "numero", "vigenteDesde", "vigenteHasta",
            "estado");

    private final ContratoRepository contratoRepository;
    private final ProveedorRepository proveedorRepository;
    private final ProductoRepository productoRepository;
    private final SolicitudCompraRepository solicitudRepository;
    private final UnidadMedidaService unidadMedidaService;
    private final AuditoriaService auditoriaService;

    /**
     * @param contratoRepository   contratos y su consumo
     * @param proveedorRepository  proveedor del contrato
     * @param productoRepository   productos de los precios
     * @param solicitudRepository  solicitud para las ofertas
     * @param unidadMedidaService  conversión de la cantidad pedida a la unidad del producto
     * @param auditoriaService     registro de cambios
     */
    public ContratoService(ContratoRepository contratoRepository, ProveedorRepository proveedorRepository,
            ProductoRepository productoRepository, SolicitudCompraRepository solicitudRepository,
            UnidadMedidaService unidadMedidaService, AuditoriaService auditoriaService) {
        this.contratoRepository = contratoRepository;
        this.proveedorRepository = proveedorRepository;
        this.productoRepository = productoRepository;
        this.solicitudRepository = solicitudRepository;
        this.unidadMedidaService = unidadMedidaService;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Crea un contrato con sus precios pactados.
     *
     * @param req datos validados
     * @return contrato creado
     * @throws NoEncontradoException si el proveedor no existe
     * @throws NegocioException      si el proveedor no está habilitado, el número se repite, la vigencia ya terminó,
     *                               un producto está inactivo o ya hay un contrato activo que se cruza
     */
    @Transactional
    public ContratoResponse crear(ContratoRequest req) {
        LocalDate hoy = LocalDate.now();
        Proveedor proveedor = proveedorRepository.findById(req.proveedorId())
                .orElseThrow(() -> new NoEncontradoException("Proveedor", req.proveedorId()));
        if (proveedor.getEstado() != EstadoProveedor.HABILITADO) {
            throw new NegocioException("PROVEEDOR_NO_HABILITADO", "El proveedor " + proveedor.getRazonSocial()
                    + " está " + proveedor.getEstado() + "; solo se firman contratos con proveedores HABILITADOS");
        }
        String numero = req.numero().trim().toUpperCase(Locale.ROOT);
        if (contratoRepository.existsByNumeroIgnoreCase(numero)) {
            throw new NegocioException("NUMERO_DUPLICADO", "Ya existe un contrato con el número " + numero);
        }
        if (req.vigenteHasta().isBefore(hoy)) {
            throw new NegocioException("VIGENCIA_PASADA", "La vigencia ya terminó el " + req.vigenteHasta());
        }
        List<Long> productoIds = req.precios().stream().map(ContratoRequest.PrecioRequest::productoId).toList();
        exigirSinSolape(proveedor.getId(), productoIds, req.vigenteDesde(), req.vigenteHasta(), null);

        Contrato c = new Contrato();
        c.setProveedor(proveedor);
        c.setNumero(numero);
        c.setDescripcion(req.descripcion() == null || req.descripcion().isBlank() ? null : req.descripcion().trim());
        c.setVigenteDesde(req.vigenteDesde());
        c.setVigenteHasta(req.vigenteHasta());
        c.setCreadoPorUsuarioId(SesionActual.usuario().id());
        for (ContratoRequest.PrecioRequest pr : req.precios()) {
            Producto producto = productoRepository.findById(pr.productoId()).filter(Producto::isActivo)
                    .orElseThrow(() -> new NegocioException("PRODUCTO_INACTIVO",
                            "El producto " + pr.productoId() + " no existe o está inactivo"));
            ContratoPrecio p = new ContratoPrecio();
            p.setProducto(producto);
            p.setPrecioUnitario(pr.precioUnitario().setScale(4, RoundingMode.HALF_UP));
            p.setImpuestoPct(pr.impuestoPct().setScale(2, RoundingMode.HALF_UP));
            p.setCantidadMaxima(pr.cantidadMaxima() == null ? null : pr.cantidadMaxima().setScale(4, RoundingMode.HALF_UP));
            c.agregarPrecio(p);
        }
        Contrato guardado = contratoRepository.save(c);
        auditoriaService.exito(AccionAuditoria.CONTRATO_CREAR, EntidadAuditada.CONTRATO, guardado.getId(),
                numero + "; proveedor=" + proveedor.getId() + "; vigencia=" + c.getVigenteDesde() + " a "
                        + c.getVigenteHasta() + "; productos=" + productoIds.size());
        return respuesta(guardado, hoy);
    }

    /**
     * Detalle de un contrato con lo consumido de cada tope.
     *
     * @param id contrato
     * @return contrato
     * @throws NoEncontradoException si no existe
     */
    @Transactional(readOnly = true)
    public ContratoResponse obtener(Long id) {
        return respuesta(detalle(id), LocalDate.now());
    }

    /**
     * Lista contratos con filtros.
     *
     * @param proveedorId proveedor ({@code null} = todos)
     * @param estado      estado ({@code null} = todos)
     * @param vigente     {@code true} para ver solo los que se pueden usar hoy
     * @param pageable    página y orden
     * @return página de contratos
     */
    @Transactional(readOnly = true)
    public PageResponse<ContratoResumenResponse> buscar(Long proveedorId, EstadoContrato estado, Boolean vigente,
            Pageable pageable) {
        LocalDate hoy = LocalDate.now();
        return PageResponse.of(contratoRepository.buscar(proveedorId, estado, Boolean.TRUE.equals(vigente) ? hoy : null,
                Ordenamiento.validar(pageable, CAMPOS_ORDENABLES)), c -> ContratoResumenResponse.of(c, hoy));
    }

    /**
     * Mueve la fecha de fin de un contrato activo (extenderlo o acortarlo).
     *
     * @param id    contrato
     * @param hasta nueva fecha de fin
     * @return contrato actualizado
     * @throws NegocioException si está anulado, la fecha es anterior a hoy o al inicio, o la extensión se cruza con
     *                          otro contrato activo del proveedor para el mismo producto
     */
    @Transactional
    public ContratoResponse cambiarVigencia(Long id, LocalDate hasta) {
        LocalDate hoy = LocalDate.now();
        contratoRepository.bloquear(id).orElseThrow(() -> new NoEncontradoException("Contrato", id));
        Contrato c = detalle(id);
        exigirActivo(c);
        if (hasta.isBefore(hoy)) {
            throw new NegocioException("VIGENCIA_PASADA", "La fecha de fin no puede ser anterior a hoy; para cerrarlo "
                    + "ya, anule el contrato");
        }
        if (hasta.isBefore(c.getVigenteDesde())) {
            throw new NegocioException("VIGENCIA_INVALIDA", "La fecha de fin no puede ser anterior al inicio ("
                    + c.getVigenteDesde() + ")");
        }
        exigirSinSolape(c.getProveedor().getId(), c.getPrecios().stream().map(p -> p.getProducto().getId()).toList(),
                c.getVigenteDesde(), hasta, c.getId());
        LocalDate antes = c.getVigenteHasta();
        c.setVigenteHasta(hasta);
        auditoriaService.exito(AccionAuditoria.CONTRATO_EDITAR, EntidadAuditada.CONTRATO, id,
                c.getNumero() + "; fin " + antes + " -> " + hasta);
        return respuesta(c, hoy);
    }

    /**
     * Anula un contrato activo. Las órdenes ya emitidas con él no cambian.
     *
     * @param id     contrato
     * @param motivo motivo obligatorio
     * @return contrato anulado
     * @throws NegocioException si ya estaba anulado
     */
    @Transactional
    public ContratoResponse anular(Long id, String motivo) {
        contratoRepository.bloquear(id).orElseThrow(() -> new NoEncontradoException("Contrato", id));
        Contrato c = detalle(id);
        exigirActivo(c);
        c.setEstado(EstadoContrato.ANULADO);
        c.setMotivoAnulacion(motivo.trim());
        c.setAnuladoPorUsuarioId(SesionActual.usuario().id());
        c.setAnuladoEn(LocalDateTime.now());
        auditoriaService.exito(AccionAuditoria.CONTRATO_ANULAR, EntidadAuditada.CONTRATO, id,
                c.getNumero() + "; motivo=" + c.getMotivoAnulacion());
        return respuesta(c, LocalDate.now());
    }

    /**
     * Valora una solicitud APROBADA contra cada contrato vigente que incluya alguno de sus productos. Primero van
     * las ofertas que se pueden emitir y, entre ellas, la más barata.
     *
     * @param solicitudId solicitud APROBADA
     * @return ofertas, vacío si ningún contrato vigente toca sus productos
     * @throws NoEncontradoException si la solicitud no existe
     * @throws NegocioException      si la solicitud no está APROBADA
     */
    @Transactional(readOnly = true)
    public List<OfertaContratoResponse> ofertas(Long solicitudId) {
        SolicitudCompra s = solicitudRepository.findWithLineasById(solicitudId)
                .orElseThrow(() -> new NoEncontradoException("Solicitud", solicitudId));
        if (s.getEstado() != EstadoSolicitud.APROBADA) {
            throw new NegocioException("SOLICITUD_NO_APROBADA", "La solicitud " + s.getConsecutivo() + " está "
                    + s.getEstado() + "; solo una solicitud APROBADA puede comprarse con un contrato");
        }
        List<Long> productoIds = s.getLineas().stream().map(l -> l.getProducto().getId()).toList();
        List<Contrato> contratos = contratoRepository.vigentesConProductos(productoIds, LocalDate.now());
        return contratos.stream().map(c -> calcular(s, c, consumoDe(c)))
                .sorted(Comparator.comparing((CalculoContrato c) -> !c.puedeEmitirse()).thenComparing(CalculoContrato::total))
                .map(OfertaContratoResponse::of).toList();
    }

    /**
     * Valora la solicitud completa al precio pactado del contrato.
     *
     * @param s         solicitud con líneas, productos y unidades cargados
     * @param c         contrato con precios y productos cargados
     * @param consumido cantidad ya pedida por id de precio pactado
     * @return cálculo con líneas, productos sin cubrir y totales
     */
    public CalculoContrato calcular(SolicitudCompra s, Contrato c, Map<Long, BigDecimal> consumido) {
        Map<Long, ContratoPrecio> porProducto = c.getPrecios().stream()
                .collect(Collectors.toMap(p -> p.getProducto().getId(), Function.identity()));
        List<CalculoContrato.Linea> lineas = new ArrayList<>();
        List<String> sinCubrir = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal impuestos = BigDecimal.ZERO;
        for (SolicitudCompraLinea l : s.getLineas()) {
            ContratoPrecio precio = porProducto.get(l.getProducto().getId());
            if (precio == null) {
                sinCubrir.add(l.getProducto().getNombre());
                continue;
            }
            BigDecimal cantidad = unidadMedidaService.convertir(l.getCantidad(), l.getUnidadMedida().getId(),
                    l.getProducto().getUnidadMedida().getId());
            BigDecimal neto = cantidad.multiply(precio.getPrecioUnitario()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal disponible = precio.getCantidadMaxima() == null ? null
                    : precio.getCantidadMaxima().subtract(consumido.getOrDefault(precio.getId(), BigDecimal.ZERO));
            lineas.add(new CalculoContrato.Linea(precio, l.getProducto(), cantidad, precio.getPrecioUnitario(),
                    precio.getImpuestoPct(), neto, disponible, disponible != null && cantidad.compareTo(disponible) > 0));
            subtotal = subtotal.add(neto);
            impuestos = impuestos.add(neto.multiply(precio.getImpuestoPct()).divide(BigDecimal.valueOf(100), 2,
                    RoundingMode.HALF_UP));
        }
        return new CalculoContrato(c, lineas, sinCubrir, subtotal, impuestos, subtotal.add(impuestos),
                s.getMontoEstimado(), s.isMontoIncompleto());
    }

    /**
     * Cantidad ya pedida contra cada precio del contrato, sin contar órdenes canceladas ni reemplazadas.
     *
     * @param c contrato con precios cargados
     * @return cantidad por id de precio pactado (falta el precio que nadie ha pedido)
     */
    public Map<Long, BigDecimal> consumoDe(Contrato c) {
        List<Long> precioIds = c.getPrecios().stream().map(ContratoPrecio::getId).toList();
        Map<Long, BigDecimal> consumido = new HashMap<>();
        if (precioIds.isEmpty()) {
            return consumido;
        }
        for (ConsumoPrecio f : contratoRepository.consumo(precioIds, ORDENES_NO_VIVAS)) {
            consumido.put(f.precioId(), f.consumido());
        }
        return consumido;
    }

    /**
     * Exige que el contrato sea usable hoy: ACTIVO y dentro de su vigencia. Un precio vencido nunca se aplica.
     *
     * @param c contrato
     * @throws NegocioException si está anulado o fuera de vigencia
     */
    public static void exigirVigente(Contrato c) {
        if (c.getEstado() != EstadoContrato.ACTIVO) {
            throw new NegocioException("CONTRATO_NO_VIGENTE", "El contrato " + c.getNumero() + " está "
                    + c.getEstado());
        }
        if (!c.esVigente(LocalDate.now())) {
            throw new NegocioException("CONTRATO_NO_VIGENTE", "El contrato " + c.getNumero() + " vigente del "
                    + c.getVigenteDesde() + " al " + c.getVigenteHasta() + " no se puede usar hoy");
        }
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Carga el contrato con precios y productos.
     *
     * @param id contrato
     * @return contrato
     * @throws NoEncontradoException si no existe
     */
    private Contrato detalle(Long id) {
        return contratoRepository.findDetalleById(id).orElseThrow(() -> new NoEncontradoException("Contrato", id));
    }

    /**
     * Respuesta con lo consumido de cada tope.
     *
     * @param c   contrato cargado
     * @param hoy fecha con la que se calcula si está vigente
     * @return DTO
     */
    private ContratoResponse respuesta(Contrato c, LocalDate hoy) {
        return ContratoResponse.of(c, consumoDe(c), hoy);
    }

    /**
     * Exige que el contrato esté ACTIVO.
     *
     * @param c contrato
     * @throws NegocioException si está anulado
     */
    private static void exigirActivo(Contrato c) {
        if (c.getEstado() != EstadoContrato.ACTIVO) {
            throw new NegocioException("CONTRATO_ANULADO", "El contrato " + c.getNumero() + " ya está anulado");
        }
    }

    /**
     * Exige que no haya otro contrato activo del proveedor que se cruce en fechas para alguno de los productos.
     *
     * @param proveedorId proveedor
     * @param productoIds productos del contrato
     * @param desde       inicio
     * @param hasta       fin
     * @param excluirId   contrato que se está editando ({@code null} si es nuevo)
     * @throws NegocioException si hay solape
     */
    private void exigirSinSolape(Long proveedorId, List<Long> productoIds, LocalDate desde, LocalDate hasta,
            Long excluirId) {
        if (contratoRepository.contarSolapados(proveedorId, productoIds, desde, hasta, excluirId) > 0) {
            throw new NegocioException("CONTRATO_SOLAPADO", "El proveedor ya tiene un contrato activo que se cruza en "
                    + "fechas con este para alguno de los mismos productos");
        }
    }
}
