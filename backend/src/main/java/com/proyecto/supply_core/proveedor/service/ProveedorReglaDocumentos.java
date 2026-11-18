package com.proyecto.supply_core.proveedor.service;

import org.springframework.stereotype.Component;

import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.documento.acceso.ReglaAccesoDocumentos;
import com.proyecto.supply_core.documento.enums.EntidadDocumento;
import com.proyecto.supply_core.proveedor.repository.ProveedorRepository;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

/**
 * Documentos del proveedor (RUT, cámara de comercio, certificaciones):
 * el propio proveedor sube y ve los suyos; ADMIN_COMPRAS también sube; COMPRADOR y AUDITOR consultan.
 * Un proveedor nunca ve los documentos de otro.
 */
@Component
public class ProveedorReglaDocumentos implements ReglaAccesoDocumentos {

    private final ProveedorRepository proveedorRepository;

    /**
     * @param proveedorRepository para verificar que el proveedor exista
     */
    public ProveedorReglaDocumentos(ProveedorRepository proveedorRepository) {
        this.proveedorRepository = proveedorRepository;
    }

    /**
     * Entidad gobernada.
     *
     * @return {@link EntidadDocumento#PROVEEDOR}
     */
    @Override
    public EntidadDocumento entidad() {
        return EntidadDocumento.PROVEEDOR;
    }

    /**
     * El propio proveedor o ADMIN_COMPRAS pueden adjuntar.
     *
     * @param proveedorId proveedor
     * @param usuario     usuario de la sesión
     * @throws NoEncontradoException   si el proveedor no existe
     * @throws AccesoDenegadoException si no es su proveedor ni ADMIN_COMPRAS
     */
    @Override
    public void validarSubida(Long proveedorId, UsuarioAutenticado usuario) {
        existe(proveedorId);
        if (!esElMismoProveedor(proveedorId, usuario) && !usuario.tieneAlgunRol(Roles.ADMIN_COMPRAS)) {
            throw new AccesoDenegadoException("No puede adjuntar documentos a este proveedor");
        }
    }

    /**
     * El propio proveedor, ADMIN_COMPRAS, COMPRADOR o AUDITOR pueden consultar.
     *
     * @param proveedorId proveedor
     * @param usuario     usuario de la sesión
     * @throws NoEncontradoException   si el proveedor no existe
     * @throws AccesoDenegadoException si no tiene acceso
     */
    @Override
    public void validarLectura(Long proveedorId, UsuarioAutenticado usuario) {
        existe(proveedorId);
        boolean interno = !usuario.esProveedor()
                && usuario.tieneAlgunRol(Roles.ADMIN_COMPRAS, Roles.COMPRADOR, Roles.AUDITOR);
        if (!esElMismoProveedor(proveedorId, usuario) && !interno) {
            throw new AccesoDenegadoException("No puede ver los documentos de este proveedor");
        }
    }

    /**
     * Indica si el usuario pertenece a ese proveedor.
     *
     * @param proveedorId proveedor
     * @param usuario     usuario de la sesión
     * @return {@code true} si es un usuario del portal de ese proveedor
     */
    private static boolean esElMismoProveedor(Long proveedorId, UsuarioAutenticado usuario) {
        return usuario.esProveedor() && proveedorId.equals(usuario.proveedorId());
    }

    /**
     * Verifica que el proveedor exista.
     *
     * @param proveedorId proveedor
     * @throws NoEncontradoException si no existe
     */
    private void existe(Long proveedorId) {
        if (!proveedorRepository.existsById(proveedorId)) {
            throw new NoEncontradoException("Proveedor", proveedorId);
        }
    }
}
