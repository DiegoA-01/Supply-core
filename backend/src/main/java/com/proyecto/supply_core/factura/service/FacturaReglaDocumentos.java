package com.proyecto.supply_core.factura.service;

import org.springframework.stereotype.Component;

import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.documento.acceso.ReglaAccesoDocumentos;
import com.proyecto.supply_core.documento.enums.EntidadDocumento;
import com.proyecto.supply_core.factura.entity.FacturaProveedor;
import com.proyecto.supply_core.factura.enums.EstadoFactura;
import com.proyecto.supply_core.factura.repository.FacturaProveedorRepository;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

/**
 * PDF de una factura del proveedor.
 * <ul>
 *   <li>Adjunta ADMINISTRATIVO mientras la factura no esté anulada y no tenga archivo (por
 *       {@code POST /api/facturas/{id}/archivo}).</li>
 *   <li>Ven los internos que participan (administrativo, compras, aprobador, auditor) y el proveedor de la orden.</li>
 * </ul>
 */
@Component
public class FacturaReglaDocumentos implements ReglaAccesoDocumentos {

    private final FacturaProveedorRepository facturaRepository;

    /**
     * @param facturaRepository facturas
     */
    public FacturaReglaDocumentos(FacturaProveedorRepository facturaRepository) {
        this.facturaRepository = facturaRepository;
    }

    /**
     * Entidad gobernada.
     *
     * @return {@link EntidadDocumento#FACTURA}
     */
    @Override
    public EntidadDocumento entidad() {
        return EntidadDocumento.FACTURA;
    }

    /**
     * ADMINISTRATIVO, con la factura vigente y sin archivo.
     *
     * @param facturaId factura
     * @param usuario   usuario de la sesión
     */
    @Override
    public void validarSubida(Long facturaId, UsuarioAutenticado usuario) {
        FacturaProveedor f = buscar(facturaId);
        if (usuario.esProveedor() || !usuario.tieneAlgunRol(Roles.ADMINISTRATIVO)) {
            throw new AccesoDenegadoException("Solo el área administrativa adjunta el archivo de una factura");
        }
        if (f.getEstado() == EstadoFactura.ANULADA || f.getDocumentoId() != null) {
            throw new AccesoDenegadoException("La factura está anulada o ya tiene archivo");
        }
    }

    /**
     * Internos que participan o el proveedor de la orden.
     *
     * @param facturaId factura
     * @param usuario   usuario de la sesión
     */
    @Override
    public void validarLectura(Long facturaId, UsuarioAutenticado usuario) {
        FacturaProveedor f = buscar(facturaId);
        boolean interno = !usuario.esProveedor() && usuario.tieneAlgunRol(Roles.ADMINISTRATIVO, Roles.ADMIN_COMPRAS,
                Roles.COMPRADOR, Roles.APROBADOR, Roles.AUDITOR);
        boolean dueno = usuario.esProveedor() && f.getProveedor().getId().equals(usuario.proveedorId());
        if (!interno && !dueno) {
            throw new AccesoDenegadoException("No puede ver el archivo de esta factura");
        }
    }

    /**
     * Carga la factura.
     *
     * @param id factura
     * @return factura con orden y proveedor
     * @throws NoEncontradoException si no existe en la empresa
     */
    private FacturaProveedor buscar(Long id) {
        return facturaRepository.findDetalleById(id).orElseThrow(() -> new NoEncontradoException("Factura", id));
    }
}
