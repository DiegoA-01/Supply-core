package com.proyecto.supply_core.compra.cotizacion.service;

import org.springframework.stereotype.Component;

import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.compra.cotizacion.entity.Cotizacion;
import com.proyecto.supply_core.compra.cotizacion.enums.EstadoCotizacion;
import com.proyecto.supply_core.compra.cotizacion.repository.CotizacionRepository;
import com.proyecto.supply_core.compra.rfq.enums.EstadoRfq;
import com.proyecto.supply_core.documento.acceso.ReglaAccesoDocumentos;
import com.proyecto.supply_core.documento.enums.EntidadDocumento;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

/**
 * Quién puede adjuntar y ver los archivos de una cotización.
 * <ul>
 *   <li>Adjunta solo el proveedor dueño y mientras la cotización está en BORRADOR.</li>
 *   <li>Ve el proveedor dueño siempre. Compras, aprobadores y auditoría solo cuando la cotización ya se
 *       envió y la RFQ dejó de estar ABIERTA (sobre cerrado).</li>
 * </ul>
 */
@Component
public class CotizacionReglaDocumentos implements ReglaAccesoDocumentos {

    private final CotizacionRepository cotizacionRepository;

    /**
     * @param cotizacionRepository cotizaciones
     */
    public CotizacionReglaDocumentos(CotizacionRepository cotizacionRepository) {
        this.cotizacionRepository = cotizacionRepository;
    }

    /**
     * Entidad gobernada.
     *
     * @return {@link EntidadDocumento#COTIZACION}
     */
    @Override
    public EntidadDocumento entidad() {
        return EntidadDocumento.COTIZACION;
    }

    /**
     * Solo el proveedor dueño, con la cotización en BORRADOR.
     *
     * @param cotizacionId cotización
     * @param usuario      usuario de la sesión
     */
    @Override
    public void validarSubida(Long cotizacionId, UsuarioAutenticado usuario) {
        Cotizacion c = buscar(cotizacionId);
        if (!esDueno(c, usuario)) {
            throw new AccesoDenegadoException("Solo el proveedor dueño puede adjuntar archivos a esta cotización");
        }
        if (c.getEstado() != EstadoCotizacion.BORRADOR) {
            throw new AccesoDenegadoException("La cotización ya fue enviada; no se pueden adjuntar más archivos");
        }
    }

    /**
     * El dueño siempre; los internos autorizados solo con sobre abierto.
     *
     * @param cotizacionId cotización
     * @param usuario      usuario de la sesión
     */
    @Override
    public void validarLectura(Long cotizacionId, UsuarioAutenticado usuario) {
        Cotizacion c = buscar(cotizacionId);
        if (esDueno(c, usuario)) {
            return;
        }
        boolean interno = !usuario.esProveedor()
                && usuario.tieneAlgunRol(Roles.COMPRADOR, Roles.ADMIN_COMPRAS, Roles.APROBADOR, Roles.AUDITOR);
        boolean sobreAbierto = c.getEstado() != EstadoCotizacion.BORRADOR && c.getRfq().getEstado() != EstadoRfq.ABIERTA;
        if (!interno || !sobreAbierto) {
            throw new AccesoDenegadoException("Los archivos de esta cotización aún no se pueden ver");
        }
    }

    /**
     * Carga la cotización con su RFQ y proveedor.
     *
     * @param id id de la cotización
     * @return cotización
     * @throws NoEncontradoException si no existe
     */
    private Cotizacion buscar(Long id) {
        return cotizacionRepository.findWithRfqById(id).orElseThrow(() -> new NoEncontradoException("Cotización", id));
    }

    /**
     * Indica si el usuario es del proveedor dueño de la cotización.
     *
     * @param c       cotización
     * @param usuario usuario de la sesión
     * @return {@code true} si es su cotización
     */
    private static boolean esDueno(Cotizacion c, UsuarioAutenticado usuario) {
        return usuario.esProveedor() && c.getProveedor().getId().equals(usuario.proveedorId());
    }
}
