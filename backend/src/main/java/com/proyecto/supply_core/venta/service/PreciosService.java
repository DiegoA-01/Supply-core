package com.proyecto.supply_core.venta.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.empresa.service.EmpresaService;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.inventario.repository.StockProductoBodegaRepository;
import com.proyecto.supply_core.inventario.repository.StockProductoBodegaRepository.TotalPorProducto;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;
import com.proyecto.supply_core.venta.dto.HistorialPrecio;
import com.proyecto.supply_core.venta.dto.ListaPrecioRequest;
import com.proyecto.supply_core.venta.dto.ListaPrecioResponse;
import com.proyecto.supply_core.venta.dto.PrecioFila;
import com.proyecto.supply_core.venta.dto.PrecioRequest;
import com.proyecto.supply_core.venta.dto.PreciosResponse;
import com.proyecto.supply_core.venta.dto.ProductoVentaRequest;
import com.proyecto.supply_core.venta.dto.TarifaImpuestoRequest;
import com.proyecto.supply_core.venta.dto.TarifaImpuestoResponse;
import com.proyecto.supply_core.venta.entity.ListaPrecio;
import com.proyecto.supply_core.venta.entity.PrecioProducto;
import com.proyecto.supply_core.venta.entity.TarifaImpuesto;
import com.proyecto.supply_core.venta.repository.ListaPrecioRepository;
import com.proyecto.supply_core.venta.repository.PrecioProductoRepository;
import com.proyecto.supply_core.venta.repository.TarifaImpuestoRepository;

/**
 * Precios de venta (fase F1 del módulo de Ventas): impuestos, listas de precios con historial y margen teórico.
 * <ul>
 *   <li>Los precios los fija el dueño (ADMIN_SISTEMA); el jefe de compras solo si la empresa se lo delegó.</li>
 *   <li>Cambiar un precio cierra la vigencia del anterior y crea uno nuevo: el historial no se borra.</li>
 *   <li>El margen lo calcula Java ({@link CalculoMargen}) sobre la base sin impuesto y el costo de referencia.</li>
 *   <li>Cada empresa arranca con IVA 19 %, IVA 5 %, exento, excluido y la lista General (se crean si faltan).</li>
 * </ul>
 */
@Service
public class PreciosService {

    private final TarifaImpuestoRepository tarifaRepository;
    private final ListaPrecioRepository listaRepository;
    private final PrecioProductoRepository precioRepository;
    private final ProductoRepository productoRepository;
    private final StockProductoBodegaRepository stockRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmpresaService empresaService;
    private final AuditoriaService auditoriaService;

    /**
     * @param tarifaRepository  impuestos de venta
     * @param listaRepository   listas de precios
     * @param precioRepository  historial de precios
     * @param productoRepository productos
     * @param stockRepository   stock total por producto (margen ponderado)
     * @param usuarioRepository nombres en el historial
     * @param empresaService    delegación de precios al jefe de compras
     * @param auditoriaService  registro de cambios
     */
    public PreciosService(TarifaImpuestoRepository tarifaRepository, ListaPrecioRepository listaRepository,
            PrecioProductoRepository precioRepository, ProductoRepository productoRepository,
            StockProductoBodegaRepository stockRepository, UsuarioRepository usuarioRepository,
            EmpresaService empresaService, AuditoriaService auditoriaService) {
        this.tarifaRepository = tarifaRepository;
        this.listaRepository = listaRepository;
        this.precioRepository = precioRepository;
        this.productoRepository = productoRepository;
        this.stockRepository = stockRepository;
        this.usuarioRepository = usuarioRepository;
        this.empresaService = empresaService;
        this.auditoriaService = auditoriaService;
    }

    // ----------------------------------------------------------------- impuestos y listas

    /**
     * @return impuestos de venta de la empresa (crea los básicos si no tiene)
     */
    @Transactional
    public List<TarifaImpuestoResponse> tarifas() {
        asegurarBase();
        return tarifaRepository.findAllByOrderByPorcentajeDescNombreAsc().stream().map(TarifaImpuestoResponse::of)
                .toList();
    }

    /**
     * @param req impuesto nuevo
     * @return impuesto creado
     * @throws NegocioException si el código ya existe
     */
    @Transactional
    public TarifaImpuestoResponse crearTarifa(TarifaImpuestoRequest req) {
        exigirEdicion();
        String codigo = req.codigo().trim().toUpperCase();
        if (tarifaRepository.existsByCodigoIgnoreCase(codigo)) {
            throw new NegocioException("TARIFA_DUPLICADA", "Ya existe un impuesto con el código " + codigo);
        }
        TarifaImpuesto t = new TarifaImpuesto();
        t.setCodigo(codigo);
        t.setNombre(req.nombre().trim());
        t.setPorcentaje(req.porcentaje());
        TarifaImpuesto guardada = tarifaRepository.save(t);
        auditoriaService.exito(AccionAuditoria.PRECIO_CONFIGURAR, EntidadAuditada.TARIFA_IMPUESTO, guardada.getId(),
                "impuesto " + codigo + " = " + req.porcentaje() + " %");
        return TarifaImpuestoResponse.of(guardada);
    }

    /**
     * @param id     impuesto
     * @param activo nuevo estado (inactivo no se asigna a productos nuevos)
     * @return impuesto
     */
    @Transactional
    public TarifaImpuestoResponse cambiarEstadoTarifa(Long id, boolean activo) {
        exigirEdicion();
        TarifaImpuesto t = tarifaRepository.findById(id).orElseThrow(() -> new NoEncontradoException("Impuesto no encontrado"));
        t.setActivo(activo);
        auditoriaService.exito(AccionAuditoria.PRECIO_CONFIGURAR, EntidadAuditada.TARIFA_IMPUESTO, id,
                t.getCodigo() + " activo=" + activo);
        return TarifaImpuestoResponse.of(t);
    }

    /**
     * @return listas de precios (crea la General si no existe)
     */
    @Transactional
    public List<ListaPrecioResponse> listas() {
        asegurarBase();
        return listaRepository.findAllByOrderByEsGeneralDescNombreAsc().stream().map(ListaPrecioResponse::of).toList();
    }

    /**
     * @param req nombre de la lista
     * @return lista creada
     * @throws NegocioException si el nombre ya existe
     */
    @Transactional
    public ListaPrecioResponse crearLista(ListaPrecioRequest req) {
        exigirEdicion();
        String nombre = req.nombre().trim();
        if (listaRepository.existsByNombreIgnoreCaseAndIdNot(nombre, -1L)) {
            throw new NegocioException("LISTA_DUPLICADA", "Ya existe la lista " + nombre);
        }
        ListaPrecio l = new ListaPrecio();
        l.setNombre(nombre);
        ListaPrecio guardada = listaRepository.save(l);
        auditoriaService.exito(AccionAuditoria.PRECIO_CONFIGURAR, EntidadAuditada.LISTA_PRECIO, guardada.getId(),
                "lista " + nombre);
        return ListaPrecioResponse.of(guardada);
    }

    /**
     * @param id  lista
     * @param req nombre y estado
     * @return lista actualizada
     * @throws NegocioException si el nombre ya existe o se intenta desactivar la General
     */
    @Transactional
    public ListaPrecioResponse editarLista(Long id, ListaPrecioRequest req) {
        exigirEdicion();
        ListaPrecio l = lista(id);
        String nombre = req.nombre().trim();
        if (listaRepository.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw new NegocioException("LISTA_DUPLICADA", "Ya existe la lista " + nombre);
        }
        if (Boolean.FALSE.equals(req.activo()) && l.isEsGeneral()) {
            throw new NegocioException("LISTA_GENERAL", "La lista General no se puede desactivar");
        }
        l.setNombre(nombre);
        if (req.activo() != null) {
            l.setActivo(req.activo());
        }
        auditoriaService.exito(AccionAuditoria.PRECIO_CONFIGURAR, EntidadAuditada.LISTA_PRECIO, id,
                "lista " + nombre + " activo=" + l.isActivo());
        return ListaPrecioResponse.of(l);
    }

    // ----------------------------------------------------------------- precios y margen

    /**
     * Precios de una lista con el margen de cada producto y el resumen.
     *
     * @param listaId lista ({@code null} = la General)
     * @return precios y margen
     * @throws NoEncontradoException si la lista no es de la empresa
     */
    @Transactional
    public PreciosResponse precios(Long listaId) {
        asegurarBase();
        ListaPrecio lista = listaId == null ? general() : lista(listaId);
        List<Producto> productos = precioRepository.productosVendibles();
        Map<Long, PrecioProducto> vigentes = precioRepository.vigentes(lista.getId()).stream()
                .collect(Collectors.toMap(p -> p.getProducto().getId(), Function.identity(), (a, b) -> a));
        Map<Long, BigDecimal> stock = productos.isEmpty() ? Map.of()
                : stockRepository.totalesDe(productos.stream().map(Producto::getId).toList()).stream()
                        .collect(Collectors.toMap(TotalPorProducto::getProductoId, TotalPorProducto::getTotal));
        List<PrecioFila> filas = productos.stream()
                .map(p -> fila(p, vigentes.get(p.getId()), stock.getOrDefault(p.getId(), BigDecimal.ZERO))).toList();
        return resumen(ListaPrecioResponse.of(lista), filas, puedeEditar());
    }

    /**
     * Fija el precio de un producto en una lista: cierra el vigente y crea el nuevo.
     *
     * @param req lista, producto, precio, mínimo y motivo
     * @return la fila del producto con su nuevo margen
     * @throws NoEncontradoException si la lista o el producto no son de la empresa
     * @throws NegocioException      si la lista está inactiva o el producto no es vendible
     */
    @Transactional
    public PrecioFila fijarPrecio(PrecioRequest req) {
        exigirEdicion();
        ListaPrecio lista = lista(req.listaId());
        if (!lista.isActivo()) {
            throw new NegocioException("LISTA_INACTIVA", "La lista " + lista.getNombre() + " está inactiva");
        }
        Producto producto = productoRepository.findById(req.productoId())
                .orElseThrow(() -> new NoEncontradoException("Producto no encontrado"));
        if (!producto.isActivo() || !producto.isVendible()) {
            throw new NegocioException("PRODUCTO_NO_VENDIBLE", "El producto " + producto.getCodigo() + " no se vende");
        }
        LocalDateTime ahora = LocalDateTime.now();
        BigDecimal anterior = null;
        PrecioProducto vigente = precioRepository.vigenteParaCambiar(lista.getId(), producto.getId()).orElse(null);
        if (vigente != null) {
            anterior = vigente.getPrecio();
            vigente.setVigenteHasta(ahora);
        }
        PrecioProducto nuevo = new PrecioProducto();
        nuevo.setLista(lista);
        nuevo.setProducto(producto);
        nuevo.setUnidadMedidaId(producto.getUnidadMedida().getId());
        nuevo.setPrecio(req.precio().setScale(2, RoundingMode.HALF_UP));
        nuevo.setPrecioMinimo(req.precioMinimo() == null ? null : req.precioMinimo().setScale(2, RoundingMode.HALF_UP));
        nuevo.setVigenteDesde(ahora);
        nuevo.setCreadoPorUsuarioId(SesionActual.usuario().id());
        nuevo.setMotivo(req.motivo() == null || req.motivo().isBlank() ? null : req.motivo().trim());
        precioRepository.saveAndFlush(nuevo);
        auditoriaService.exito(AccionAuditoria.PRECIO_CAMBIAR, EntidadAuditada.PRECIO_PRODUCTO, nuevo.getId(),
                producto.getCodigo() + " en " + lista.getNombre() + ": " + (anterior == null ? "sin precio" : anterior)
                        + " -> " + nuevo.getPrecio() + (nuevo.getMotivo() == null ? "" : "; motivo=" + nuevo.getMotivo()));
        return fila(producto, nuevo, stockRepository.totalDeProducto(producto.getId()));
    }

    /**
     * @param listaId    lista
     * @param productoId producto
     * @return todos sus precios en la lista, del más reciente al más antiguo
     */
    @Transactional(readOnly = true)
    public List<HistorialPrecio> historial(Long listaId, Long productoId) {
        lista(listaId);
        productoRepository.findById(productoId).orElseThrow(() -> new NoEncontradoException("Producto no encontrado"));
        List<PrecioProducto> precios = precioRepository.historial(listaId, productoId);
        Set<Long> ids = precios.stream().map(PrecioProducto::getCreadoPorUsuarioId).collect(Collectors.toSet());
        Map<Long, String> nombres = new HashMap<>();
        usuarioRepository.findAllById(ids).forEach(u -> nombres.put(u.getId(), u.getNombreCompleto()));
        return precios.stream().map(p -> new HistorialPrecio(p.getPrecio(), p.getPrecioMinimo(), p.getVigenteDesde(),
                p.getVigenteHasta(), nombres.getOrDefault(p.getCreadoPorUsuarioId(), "—"), p.getMotivo())).toList();
    }

    /**
     * Datos de venta del producto: impuesto, si se vende, si el precio incluye impuesto y código de barras.
     *
     * @param productoId producto
     * @param req        datos de venta
     * @return la fila del producto en la lista General
     * @throws NegocioException si el código de barras ya lo usa otro producto
     */
    @Transactional
    public PrecioFila configurarProducto(Long productoId, ProductoVentaRequest req) {
        exigirEdicion();
        Producto p = productoRepository.findById(productoId)
                .orElseThrow(() -> new NoEncontradoException("Producto no encontrado"));
        TarifaImpuesto tarifa = req.tarifaImpuestoId() == null ? null
                : tarifaRepository.findById(req.tarifaImpuestoId())
                        .orElseThrow(() -> new NoEncontradoException("Impuesto no encontrado"));
        String codigoBarras = req.codigoBarras() == null || req.codigoBarras().isBlank() ? null : req.codigoBarras().trim();
        if (codigoBarras != null && precioRepository.codigoBarrasEnUso(codigoBarras, productoId)) {
            throw new NegocioException("CODIGO_BARRAS_DUPLICADO", "Otro producto ya usa el código de barras " + codigoBarras);
        }
        String antes = "impuesto=" + (p.getTarifaImpuesto() == null ? "—" : p.getTarifaImpuesto().getCodigo())
                + ", vendible=" + p.isVendible() + ", incluye=" + p.isPrecioIncluyeImpuesto() + ", barras=" + p.getCodigoBarras();
        p.setTarifaImpuesto(tarifa);
        p.setVendible(req.vendible());
        p.setPrecioIncluyeImpuesto(req.precioIncluyeImpuesto());
        p.setCodigoBarras(codigoBarras);
        auditoriaService.exito(AccionAuditoria.PRODUCTO_EDITAR, EntidadAuditada.PRODUCTO, productoId,
                "venta; antes: " + antes + " | después: impuesto=" + (tarifa == null ? "—" : tarifa.getCodigo())
                        + ", vendible=" + p.isVendible() + ", incluye=" + p.isPrecioIncluyeImpuesto() + ", barras="
                        + codigoBarras);
        ListaPrecio general = general();
        return fila(p, precioRepository.vigentes(general.getId()).stream()
                .filter(x -> x.getProducto().getId().equals(productoId)).findFirst().orElse(null),
                stockRepository.totalDeProducto(productoId));
    }

    // ----------------------------------------------------------------- cálculo

    /**
     * Fila de un producto con su margen.
     *
     * @param p       producto
     * @param precio  precio vigente o {@code null}
     * @param stock   stock total
     * @return fila
     */
    static PrecioFila fila(Producto p, PrecioProducto precio, BigDecimal stock) {
        TarifaImpuesto t = p.getTarifaImpuesto();
        BigDecimal pct = t == null ? null : t.getPorcentaje();
        BigDecimal costo = p.getCostoReferencia();
        List<String> alertas = new ArrayList<>();
        if (costo == null) {
            alertas.add("SIN_COSTO");
        }
        if (t == null) {
            alertas.add("SIN_IMPUESTO");
        }
        if (precio == null) {
            alertas.add("SIN_PRECIO");
            return new PrecioFila(p.getId(), p.getCodigo(), p.getNombre(), p.getCategoria().getNombre(),
                    p.getUnidadMedida().getCodigo(), p.isVendible(), p.getCodigoBarras(), t == null ? null : t.getId(),
                    t == null ? null : t.getNombre(), pct, p.isPrecioIncluyeImpuesto(), costo, null, null, null, null,
                    null, null, null, stock, List.copyOf(alertas));
        }
        CalculoMargen.Resultado r = CalculoMargen.calcular(precio.getPrecio(), p.isPrecioIncluyeImpuesto(), pct, costo);
        if (r.margen() != null && r.margen().signum() < 0) {
            alertas.add("BAJO_COSTO");
        }
        if (precio.getPrecioMinimo() != null && costo != null && CalculoMargen.calcular(precio.getPrecioMinimo(),
                p.isPrecioIncluyeImpuesto(), pct, costo).margen().signum() < 0) {
            alertas.add("MINIMO_BAJO_COSTO");
        }
        return new PrecioFila(p.getId(), p.getCodigo(), p.getNombre(), p.getCategoria().getNombre(),
                p.getUnidadMedida().getCodigo(), p.isVendible(), p.getCodigoBarras(), t == null ? null : t.getId(),
                t == null ? null : t.getNombre(), pct, p.isPrecioIncluyeImpuesto(), costo, precio.getPrecio(),
                precio.getPrecioMinimo(), precio.getVigenteDesde(), r.base(), r.impuesto(), r.margen(), r.margenPct(),
                stock, List.copyOf(alertas));
    }

    /**
     * Resumen de la lista: margen promedio simple y margen global ponderado por el inventario.
     *
     * @param lista       lista
     * @param filas       productos con su margen
     * @param puedeEditar si la sesión puede fijar precios
     * @return respuesta completa
     */
    static PreciosResponse resumen(ListaPrecioResponse lista, List<PrecioFila> filas, boolean puedeEditar) {
        List<PrecioFila> conMargen = filas.stream().filter(f -> f.margenPct() != null).toList();
        BigDecimal promedio = conMargen.isEmpty() ? null
                : conMargen.stream().map(PrecioFila::margenPct).reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(conMargen.size()), 1, RoundingMode.HALF_UP);
        BigDecimal sumaMargen = BigDecimal.ZERO;
        BigDecimal sumaBase = BigDecimal.ZERO;
        for (PrecioFila f : conMargen) {
            if (f.stock() != null && f.stock().signum() > 0) {
                sumaMargen = sumaMargen.add(f.margen().multiply(f.stock()));
                sumaBase = sumaBase.add(f.base().multiply(f.stock()));
            }
        }
        BigDecimal ponderado = sumaBase.signum() == 0 ? null
                : sumaMargen.multiply(BigDecimal.valueOf(100)).divide(sumaBase, 1, RoundingMode.HALF_UP);
        long conPrecio = filas.stream().filter(f -> f.precio() != null).count();
        long bajoCosto = filas.stream().filter(f -> f.alertas().contains("BAJO_COSTO")).count();
        return new PreciosResponse(lista, filas.size(), conPrecio, filas.size() - conPrecio, bajoCosto, promedio,
                ponderado, sumaMargen.setScale(2, RoundingMode.HALF_UP), puedeEditar, filas);
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * @return {@code true} si la sesión puede fijar precios: el dueño, o el jefe de compras si se lo delegaron
     */
    private boolean puedeEditar() {
        UsuarioAutenticado s = SesionActual.usuario();
        return s.roles().contains(Roles.ADMIN_SISTEMA)
                || (s.roles().contains(Roles.ADMIN_COMPRAS) && empresaService.obtener().preciosDelegadosCompras());
    }

    /**
     * @throws AccesoDenegadoException si la sesión no puede fijar precios
     */
    private void exigirEdicion() {
        if (!puedeEditar()) {
            throw new AccesoDenegadoException("Solo el dueño fija los precios de venta (o el jefe de compras si se lo delegó)");
        }
    }

    /**
     * @param id lista
     * @return lista de la empresa
     */
    private ListaPrecio lista(Long id) {
        return listaRepository.findById(id).orElseThrow(() -> new NoEncontradoException("Lista de precios no encontrada"));
    }

    /**
     * @return la lista General de la empresa
     */
    private ListaPrecio general() {
        return listaRepository.findAllByOrderByEsGeneralDescNombreAsc().stream().filter(ListaPrecio::isEsGeneral)
                .findFirst().orElseThrow(() -> new IllegalStateException("La empresa no tiene lista General"));
    }

    /**
     * Crea los impuestos básicos y la lista General si la empresa aún no los tiene (empresas creadas después de V29).
     */
    void asegurarBase() {
        if (tarifaRepository.count() == 0) {
            for (Object[] t : new Object[][] { { "IVA19", "IVA 19 %", "19.00" }, { "IVA5", "IVA 5 %", "5.00" },
                    { "EXENTO", "Exento", "0.00" }, { "EXCLUIDO", "Excluido", "0.00" } }) {
                TarifaImpuesto nueva = new TarifaImpuesto();
                nueva.setCodigo((String) t[0]);
                nueva.setNombre((String) t[1]);
                nueva.setPorcentaje(new BigDecimal((String) t[2]));
                tarifaRepository.save(nueva);
            }
        }
        if (listaRepository.findAllByOrderByEsGeneralDescNombreAsc().stream().noneMatch(ListaPrecio::isEsGeneral)) {
            ListaPrecio general = new ListaPrecio();
            general.setNombre("General");
            general.setEsGeneral(true);
            listaRepository.save(general);
        }
    }
}
