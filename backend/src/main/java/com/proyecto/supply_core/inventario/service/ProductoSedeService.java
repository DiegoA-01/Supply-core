package com.proyecto.supply_core.inventario.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.empresa.entity.Sede;
import com.proyecto.supply_core.empresa.repository.SedeRepository;
import com.proyecto.supply_core.inventario.dto.ProductoSedeRequest;
import com.proyecto.supply_core.inventario.dto.ProductoSedeResponse;
import com.proyecto.supply_core.inventario.dto.StockSede;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.entity.ProductoSede;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.inventario.repository.ProductoSedeRepository;
import com.proyecto.supply_core.inventario.repository.StockProductoBodegaRepository;

/**
 * Límites de un producto en cada sede: cada sede se maneja como un punto independiente con su propio mínimo y
 * máximo. Sin límites propios, la sede se guía por su consumo (plan de reabastecimiento).
 */
@Service
public class ProductoSedeService {

    private final ProductoSedeRepository productoSedeRepository;
    private final ProductoRepository productoRepository;
    private final SedeRepository sedeRepository;
    private final StockProductoBodegaRepository stockRepository;
    private final AuditoriaService auditoriaService;

    /**
     * @param productoSedeRepository límites por sede
     * @param productoRepository     productos de la empresa
     * @param sedeRepository         sedes de la empresa
     * @param stockRepository        stock por sede
     * @param auditoriaService       registro de cambios
     */
    public ProductoSedeService(ProductoSedeRepository productoSedeRepository, ProductoRepository productoRepository,
            SedeRepository sedeRepository, StockProductoBodegaRepository stockRepository,
            AuditoriaService auditoriaService) {
        this.productoSedeRepository = productoSedeRepository;
        this.productoRepository = productoRepository;
        this.sedeRepository = sedeRepository;
        this.stockRepository = stockRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * El producto en cada sede activa: stock y límites propios.
     *
     * @param productoId producto
     * @return una fila por sede activa, por nombre
     * @throws NoEncontradoException si el producto no es de la empresa
     */
    @Transactional(readOnly = true)
    public List<ProductoSedeResponse> listar(Long productoId) {
        producto(productoId);
        Map<Long, ProductoSede> limites = productoSedeRepository.deProducto(productoId).stream()
                .collect(Collectors.toMap(ps -> ps.getSede().getId(), Function.identity()));
        Map<Long, BigDecimal> stock = stockRepository.stockPorSede(productoId).stream()
                .collect(Collectors.toMap(StockSede::sedeId, StockSede::cantidad));
        return productoSedeRepository.sedesActivas().stream()
                .map(s -> respuesta(s, stock.getOrDefault(s.getId(), BigDecimal.ZERO), limites.get(s.getId())))
                .toList();
    }

    /**
     * Fija el mínimo y el máximo del producto en una sede (los crea si no existían).
     *
     * @param productoId producto
     * @param sedeId     sede
     * @param req        límites
     * @return el producto en esa sede
     * @throws NoEncontradoException si el producto o la sede no son de la empresa
     */
    @Transactional
    public ProductoSedeResponse guardar(Long productoId, Long sedeId, ProductoSedeRequest req) {
        Producto producto = producto(productoId);
        Sede sede = sedeRepository.findById(sedeId)
                .orElseThrow(() -> new NoEncontradoException("Sede no encontrada"));
        ProductoSede ps = productoSedeRepository.deProductoEnSede(productoId, sedeId).orElseGet(() -> {
            ProductoSede nueva = new ProductoSede();
            nueva.setProducto(producto);
            nueva.setSede(sede);
            return nueva;
        });
        String antes = ps.getId() == null ? "sin límites" : "min=" + ps.getStockMinimo() + ", max=" + ps.getStockMaximo();
        ps.setStockMinimo(req.stockMinimo());
        ps.setStockMaximo(req.stockMaximo());
        ProductoSede guardada = productoSedeRepository.save(ps);
        auditoriaService.exito(AccionAuditoria.PRODUCTO_EDITAR, EntidadAuditada.PRODUCTO, productoId,
                "codigo=" + producto.getCodigo() + "; sede=" + sede.getNombre() + "; antes: " + antes
                        + " | después: min=" + guardada.getStockMinimo() + ", max=" + guardada.getStockMaximo());
        return respuesta(sede, stockRepository.totalDeProductoEnSede(productoId, sedeId), guardada);
    }

    /**
     * @param productoId producto
     * @return el producto de la empresa
     * @throws NoEncontradoException si no existe en la empresa
     */
    private Producto producto(Long productoId) {
        return productoRepository.findById(productoId)
                .orElseThrow(() -> new NoEncontradoException("Producto no encontrado"));
    }

    /**
     * @param sede   sede
     * @param stock  stock en la sede
     * @param limite límites propios o {@code null}
     * @return fila de respuesta
     */
    private static ProductoSedeResponse respuesta(Sede sede, BigDecimal stock, ProductoSede limite) {
        BigDecimal minimo = limite == null ? BigDecimal.ZERO : limite.getStockMinimo();
        return new ProductoSedeResponse(sede.getId(), sede.getNombre(), stock, minimo,
                limite == null ? null : limite.getStockMaximo(), limite != null,
                minimo.signum() > 0 && stock.compareTo(minimo) < 0);
    }
}
