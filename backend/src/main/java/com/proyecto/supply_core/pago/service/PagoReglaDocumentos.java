package com.proyecto.supply_core.pago.service;

import org.springframework.stereotype.Component;

import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.documento.acceso.ReglaAccesoDocumentos;
import com.proyecto.supply_core.documento.enums.EntidadDocumento;
import com.proyecto.supply_core.pago.entity.Pago;
import com.proyecto.supply_core.pago.enums.EstadoPago;
import com.proyecto.supply_core.pago.repository.PagoRepository;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

/**
 * Comprobante de un pago.
 * <ul>
 *   <li>Adjunta ADMINISTRATIVO mientras el pago esté REGISTRADO y sin comprobante (por
 *       {@code POST /api/pagos/{id}/comprobante}, que además cierra la evidencia pendiente).</li>
 *   <li>Ven los internos que participan (administrativo, compras, aprobador, auditor) y el proveedor de la orden.</li>
 * </ul>
 */
@Component
public class PagoReglaDocumentos implements ReglaAccesoDocumentos {

    private final PagoRepository pagoRepository;

    /**
     * @param pagoRepository pagos
     */
    public PagoReglaDocumentos(PagoRepository pagoRepository) {
        this.pagoRepository = pagoRepository;
    }

    /**
     * Entidad gobernada.
     *
     * @return {@link EntidadDocumento#PAGO}
     */
    @Override
    public EntidadDocumento entidad() {
        return EntidadDocumento.PAGO;
    }

    /**
     * ADMINISTRATIVO, con el pago registrado y sin comprobante.
     *
     * @param pagoId  pago
     * @param usuario usuario de la sesión
     */
    @Override
    public void validarSubida(Long pagoId, UsuarioAutenticado usuario) {
        Pago p = buscar(pagoId);
        if (usuario.esProveedor() || !usuario.tieneAlgunRol(Roles.ADMINISTRATIVO)) {
            throw new AccesoDenegadoException("Solo el área administrativa adjunta comprobantes de pago");
        }
        if (p.getEstado() != EstadoPago.REGISTRADO || p.getComprobanteDocumentoId() != null) {
            throw new AccesoDenegadoException("El pago está anulado o ya tiene comprobante");
        }
    }

    /**
     * Internos que participan o el proveedor de la orden.
     *
     * @param pagoId  pago
     * @param usuario usuario de la sesión
     */
    @Override
    public void validarLectura(Long pagoId, UsuarioAutenticado usuario) {
        Pago p = buscar(pagoId);
        boolean interno = !usuario.esProveedor() && usuario.tieneAlgunRol(Roles.ADMINISTRATIVO, Roles.COMPRADOR,
                Roles.ADMIN_COMPRAS, Roles.APROBADOR, Roles.AUDITOR);
        boolean dueno = usuario.esProveedor() && p.getOrden().getProveedor().getId().equals(usuario.proveedorId());
        if (!interno && !dueno) {
            throw new AccesoDenegadoException("No puede ver el comprobante de este pago");
        }
    }

    /**
     * Carga el pago.
     *
     * @param id pago
     * @return pago con orden y proveedor
     */
    private Pago buscar(Long id) {
        return pagoRepository.findDetalleById(id).orElseThrow(() -> new NoEncontradoException("Pago", id));
    }
}
