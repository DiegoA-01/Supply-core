package com.proyecto.supply_core.compra.orden.service;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.compra.orden.entity.OrdenCompra;
import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;
import com.proyecto.supply_core.compra.orden.repository.OrdenCompraRepository;
import com.proyecto.supply_core.documento.acceso.ReglaAccesoDocumentos;
import com.proyecto.supply_core.documento.enums.EntidadDocumento;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

/**
 * Archivos de una orden (guía de despacho, fotos, orden firmada).
 * <ul>
 *   <li>Adjunta el proveedor dueño mientras alista o despacha, y compras en cualquier momento.</li>
 *   <li>Ven el proveedor dueño y los internos que participan (compras, bodega, administrativo, aprobador,
 *       auditor).</li>
 * </ul>
 */
@Component
public class OrdenReglaDocumentos implements ReglaAccesoDocumentos {

    /** Estados en que el proveedor puede adjuntar evidencia. */
    private static final Set<EstadoOrden> PROVEEDOR_ADJUNTA = EnumSet.of(EstadoOrden.ACEPTADA_PROVEEDOR,
            EstadoOrden.EN_PREPARACION, EstadoOrden.DESPACHADA);

    private final OrdenCompraRepository ordenRepository;

    /**
     * @param ordenRepository órdenes
     */
    public OrdenReglaDocumentos(OrdenCompraRepository ordenRepository) {
        this.ordenRepository = ordenRepository;
    }

    /**
     * Entidad gobernada.
     *
     * @return {@link EntidadDocumento#ORDEN_COMPRA}
     */
    @Override
    public EntidadDocumento entidad() {
        return EntidadDocumento.ORDEN_COMPRA;
    }

    /**
     * Proveedor dueño (aceptada, en preparación o despachada) o compras.
     *
     * @param ordenId orden
     * @param usuario usuario de la sesión
     */
    @Override
    public void validarSubida(Long ordenId, UsuarioAutenticado usuario) {
        OrdenCompra o = buscar(ordenId);
        boolean compras = !usuario.esProveedor() && usuario.tieneAlgunRol(Roles.COMPRADOR, Roles.ADMIN_COMPRAS);
        boolean duenoActivo = esDueno(o, usuario) && PROVEEDOR_ADJUNTA.contains(o.getEstado());
        if (!compras && !duenoActivo) {
            throw new AccesoDenegadoException("No puede adjuntar archivos a esta orden en su estado actual");
        }
    }

    /**
     * Proveedor dueño o internos que participan en la compra.
     *
     * @param ordenId orden
     * @param usuario usuario de la sesión
     */
    @Override
    public void validarLectura(Long ordenId, UsuarioAutenticado usuario) {
        OrdenCompra o = buscar(ordenId);
        boolean interno = !usuario.esProveedor() && usuario.tieneAlgunRol(Roles.COMPRADOR, Roles.ADMIN_COMPRAS,
                Roles.ALMACENISTA, Roles.ADMINISTRATIVO, Roles.APROBADOR, Roles.AUDITOR);
        if (!interno && !esDueno(o, usuario)) {
            throw new AccesoDenegadoException("No puede ver los archivos de esta orden");
        }
    }

    /**
     * Carga la orden.
     *
     * @param id orden
     * @return orden con proveedor
     */
    private OrdenCompra buscar(Long id) {
        return ordenRepository.findDetalleById(id).orElseThrow(() -> new NoEncontradoException("Orden", id));
    }

    /**
     * Indica si el usuario es del proveedor de la orden.
     *
     * @param o       orden
     * @param usuario usuario de la sesión
     * @return {@code true} si es su orden
     */
    private static boolean esDueno(OrdenCompra o, UsuarioAutenticado usuario) {
        return usuario.esProveedor() && o.getProveedor().getId().equals(usuario.proveedorId());
    }
}
