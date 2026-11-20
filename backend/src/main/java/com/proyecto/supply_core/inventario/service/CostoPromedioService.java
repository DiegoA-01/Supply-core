package com.proyecto.supply_core.inventario.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.StockProductoBodegaRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

/**
 * Costo promedio ponderado del producto (spec 008). Lo llama la recepción ANTES de registrar la entrada, dentro de su
 * transacción: bloquea el producto y lee la existencia con bloqueo, así dos recepciones a la vez del mismo producto van
 * en fila y cada una parte del costo y la existencia que dejó la otra.
 */
@Service
public class CostoPromedioService {

    @PersistenceContext
    private EntityManager em;

    private final StockProductoBodegaRepository stockRepository;

    /**
     * @param stockRepository existencia total con bloqueo
     */
    public CostoPromedioService(StockProductoBodegaRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    /**
     * Actualiza el costo promedio por una compra que va a entrar.
     *
     * @param producto    producto recibido (se vuelve a leer con bloqueo)
     * @param cantidad    cantidad que entra, en la unidad del producto
     * @param costoCompra costo unitario de la compra (sin impuestos)
     * @return nuevo costo promedio
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal registrarCompra(Producto producto, BigDecimal cantidad, BigDecimal costoCompra) {
        // Primero se guardan los cambios pendientes del producto; luego se relee con bloqueo (valor confirmado más reciente)
        em.flush();
        em.refresh(producto, LockModeType.PESSIMISTIC_WRITE);
        BigDecimal existencia = stockRepository.existenciaBloqueada(EmpresaContexto.requerida(), producto.getId());
        BigDecimal nuevo = CalculoCosto.promedio(existencia, producto.getCostoPromedio(), cantidad, costoCompra);
        producto.setCostoPromedio(nuevo);
        return nuevo;
    }
}
