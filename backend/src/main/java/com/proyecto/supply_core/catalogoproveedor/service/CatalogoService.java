package com.proyecto.supply_core.catalogoproveedor.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
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
import com.proyecto.supply_core.catalogoproveedor.dto.CatalogoItemResponse;
import com.proyecto.supply_core.catalogoproveedor.dto.EquivalenciaRequest;
import com.proyecto.supply_core.catalogoproveedor.dto.Oportunidad;
import com.proyecto.supply_core.catalogoproveedor.entity.CatalogoItem;
import com.proyecto.supply_core.catalogoproveedor.enums.EstadoEquivalencia;
import com.proyecto.supply_core.catalogoproveedor.repository.CatalogoItemRepository;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.util.Ordenamiento;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.reorden.dto.ReabastecimientoItem;
import com.proyecto.supply_core.reorden.enums.EstadoReabastecimiento;
import com.proyecto.supply_core.reorden.service.ReabastecimientoService;
import com.proyecto.supply_core.security.context.SesionActual;

/**
 * Catálogos de proveedores para compras: el catálogo completo con sus equivalencias, la confirmación de cada
 * equivalencia y las oportunidades. Las oportunidades salen solo de equivalencias confirmadas por una persona; los
 * precios y el ahorro los calcula Java. El proveedor no ve nada de esto.
 */
@Service
public class CatalogoService {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    private static final Set<String> CAMPOS_ORDENABLES = Set.of("descripcion", "precio", "actualizadoEn");
    private static final Set<EstadoReabastecimiento> HAY_QUE_PEDIR = EnumSet.of(EstadoReabastecimiento.URGENTE,
            EstadoReabastecimiento.PEDIR_YA, EstadoReabastecimiento.PRONTO);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("es-CO"));

    private final CatalogoItemRepository catalogoRepository;
    private final ProductoRepository productoRepository;
    private final ReabastecimientoService reabastecimientoService;
    private final AuditoriaService auditoriaService;

    /**
     * @param catalogoRepository      catálogos
     * @param productoRepository      productos para confirmar equivalencias
     * @param reabastecimientoService qué hay que pedir y cuándo
     * @param auditoriaService        registro de decisiones
     */
    public CatalogoService(CatalogoItemRepository catalogoRepository, ProductoRepository productoRepository,
            ReabastecimientoService reabastecimientoService, AuditoriaService auditoriaService) {
        this.catalogoRepository = catalogoRepository;
        this.productoRepository = productoRepository;
        this.reabastecimientoService = reabastecimientoService;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Catálogo completo de los proveedores.
     *
     * @param proveedorId proveedor, opcional
     * @param estado      estado de la equivalencia, opcional
     * @param texto       búsqueda, opcional
     * @param pageable    página
     * @return página de artículos
     */
    @Transactional(readOnly = true)
    public PageResponse<CatalogoItemResponse> buscar(Long proveedorId, EstadoEquivalencia estado, String texto,
            Pageable pageable) {
        String t = texto == null || texto.isBlank() ? null : texto.trim();
        return PageResponse.of(catalogoRepository.buscar(proveedorId, estado, t,
                Ordenamiento.validar(pageable, CAMPOS_ORDENABLES)), CatalogoItemResponse::of);
    }

    /**
     * Compras confirma a qué producto corresponde un artículo, o lo descarta.
     *
     * @param id  artículo
     * @param req decisión
     * @return artículo actualizado
     * @throws NoEncontradoException si el artículo o el producto no son de la empresa
     */
    @Transactional
    public CatalogoItemResponse decidir(Long id, EquivalenciaRequest req) {
        CatalogoItem c = catalogoRepository.findById(id)
                .orElseThrow(() -> new NoEncontradoException("Artículo no encontrado"));
        if (req.estado() == EstadoEquivalencia.CONFIRMADA) {
            Producto p = productoRepository.findById(req.productoId())
                    .orElseThrow(() -> new NoEncontradoException("Producto no encontrado"));
            c.setProducto(p);
        } else {
            c.setProducto(null);
        }
        c.setEstadoEquivalencia(req.estado());
        c.setRevisadoPorUsuarioId(SesionActual.usuario().id());
        auditoriaService.exito(AccionAuditoria.CATALOGO_EQUIVALENCIA, EntidadAuditada.CATALOGO_ITEM, id,
                c.getProveedor().getRazonSocial() + " " + c.getCodigoProveedor() + " -> " + req.estado()
                        + (c.getProducto() == null ? "" : " " + c.getProducto().getCodigo()));
        return CatalogoItemResponse.of(c);
    }

    /**
     * Ofertas confirmadas de un producto en los catálogos, de la más barata a la más cara.
     *
     * @param productoId producto
     * @return ofertas
     */
    @Transactional(readOnly = true)
    public List<CatalogoItemResponse> ofertasDe(Long productoId) {
        productoRepository.findById(productoId).orElseThrow(() -> new NoEncontradoException("Producto no encontrado"));
        return catalogoRepository.ofertasDe(productoId).stream().map(CatalogoItemResponse::of).toList();
    }

    /**
     * Oportunidades: por producto, la mejor oferta confirmada si es más barata que el último costo o si el producto
     * hay que pedirlo pronto. Primero lo que hay que pedir, luego lo que más ahorra.
     *
     * @return oportunidades
     */
    @Transactional(readOnly = true)
    public List<Oportunidad> oportunidades() {
        LocalDate hoy = LocalDate.now();
        Map<Long, List<CatalogoItem>> porProducto = catalogoRepository.confirmados().stream()
                .filter(c -> c.getVigenteHasta() == null || !c.getVigenteHasta().isBefore(hoy))
                .collect(Collectors.groupingBy(c -> c.getProducto().getId()));
        if (porProducto.isEmpty()) {
            return List.of();
        }
        Map<Long, ReabastecimientoItem> plan = reabastecimientoService.plan().items().stream()
                .collect(Collectors.toMap(ReabastecimientoItem::productoId, Function.identity(), (a, b) -> a));
        List<Oportunidad> resultado = new ArrayList<>();
        porProducto.forEach((productoId, ofertas) -> {
            CatalogoItem mejor = ofertas.stream().min(Comparator.comparing(CatalogoService::precioSinIva)).orElseThrow();
            Oportunidad o = oportunidad(mejor, ofertas.size(), plan.get(productoId));
            boolean ahorra = o.ahorroPct() != null && o.ahorroPct().signum() > 0;
            boolean pedir = o.estadoReabastecimiento() != null && HAY_QUE_PEDIR.contains(o.estadoReabastecimiento());
            if (ahorra || pedir) {
                resultado.add(o);
            }
        });
        resultado.sort(Comparator.comparing((Oportunidad o) -> o.estadoReabastecimiento() == null
                || !HAY_QUE_PEDIR.contains(o.estadoReabastecimiento()))
                .thenComparing(o -> o.estadoReabastecimiento() == null ? EstadoReabastecimiento.SIN_CONSUMO : o.estadoReabastecimiento())
                .thenComparing(Oportunidad::ahorroPct, Comparator.nullsLast(Comparator.reverseOrder())));
        return resultado;
    }

    /**
     * Arma la oportunidad de un producto con su mejor oferta.
     *
     * @param c       mejor oferta (con proveedor y producto cargados)
     * @param ofertas cuántos proveedores lo ofrecen
     * @param plan    plan de reabastecimiento del producto o {@code null}
     * @return oportunidad con su explicación
     */
    static Oportunidad oportunidad(CatalogoItem c, int ofertas, ReabastecimientoItem plan) {
        Producto p = c.getProducto();
        BigDecimal sinIva = precioSinIva(c);
        boolean comparable = c.getUnidad() == null || c.getUnidad().equalsIgnoreCase(p.getUnidadMedida().getCodigo());
        BigDecimal costo = p.getCostoReferencia();
        BigDecimal ahorro = comparable && costo != null && costo.signum() > 0
                ? costo.subtract(sinIva).multiply(CIEN).divide(costo, 1, RoundingMode.HALF_UP) : null;
        StringBuilder motivo = new StringBuilder(c.getProveedor().getRazonSocial()).append(" lo ofrece a $")
                .append(sinIva.setScale(0, RoundingMode.HALF_UP).toPlainString()).append(" sin IVA");
        if (ahorro != null) {
            motivo.append(ahorro.signum() >= 0 ? ", " + ahorro.abs().toPlainString() + " % menos que tu último costo"
                    : ", " + ahorro.abs().toPlainString() + " % más caro que tu último costo");
        } else if (!comparable) {
            motivo.append(" por ").append(c.getUnidad()).append(" (tu unidad es ").append(p.getUnidadMedida().getCodigo())
                    .append(": revisa la conversión)");
        }
        motivo.append('.');
        if (plan != null && HAY_QUE_PEDIR.contains(plan.estado())) {
            motivo.append(plan.estado() == EstadoReabastecimiento.URGENTE ? " Urgente: se acaba antes de que llegue un pedido."
                    : " Hay que pedirlo" + (plan.pedirAntesDe() == null ? "." : " a más tardar el " + plan.pedirAntesDe().format(FECHA) + "."));
        }
        if (ofertas > 1) {
            motivo.append(" Hay ").append(ofertas).append(" proveedores que lo ofrecen.");
        }
        return new Oportunidad(p.getId(), p.getCodigo(), p.getNombre(), p.getUnidadMedida().getCodigo(),
                c.getProveedor().getId(), c.getProveedor().getRazonSocial(), c.getCodigoProveedor(), c.getDescripcion(),
                c.getMarca(), c.getPrecio(), sinIva, costo, ahorro, comparable, ofertas,
                plan == null ? null : plan.estado(), plan == null ? null : plan.cantidadComprar(),
                plan == null ? null : plan.pedirAntesDe(), motivo.toString());
    }

    /**
     * Precio comparable con el costo de compra: sin IVA. Si el catálogo trae el IVA incluido se le quita con la tarifa
     * del producto (19 % si no tiene una definida).
     *
     * @param c artículo con producto
     * @return precio sin IVA, con 2 decimales
     */
    static BigDecimal precioSinIva(CatalogoItem c) {
        if (!c.isIncluyeIva()) {
            return c.getPrecio().setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal pct = c.getProducto() != null && c.getProducto().getTarifaImpuesto() != null
                ? c.getProducto().getTarifaImpuesto().getPorcentaje() : BigDecimal.valueOf(19);
        return c.getPrecio().multiply(CIEN).divide(CIEN.add(pct), 2, RoundingMode.HALF_UP);
    }
}
