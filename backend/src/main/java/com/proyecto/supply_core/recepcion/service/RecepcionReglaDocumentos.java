package com.proyecto.supply_core.recepcion.service;

import org.springframework.stereotype.Component;

import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.documento.acceso.ReglaAccesoDocumentos;
import com.proyecto.supply_core.documento.enums.EntidadDocumento;
import com.proyecto.supply_core.recepcion.entity.Recepcion;
import com.proyecto.supply_core.recepcion.repository.RecepcionRepository;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

/**
 * Evidencia fotográfica de una recepción (mercancía dañada, empaque, remisión).
 * <ul>
 *   <li>Sube el ALMACENISTA o ADMIN_COMPRAS, vía {@code POST /api/documentos} con entidad RECEPCION y el id de la
 *       recepción.</li>
 *   <li>Ven los internos que participan (compras, administrativo, aprobador, auditor, almacén) y el proveedor
 *       de la orden, para ver con qué evidencia se rechazó su mercancía.</li>
 * </ul>
 */
@Component
public class RecepcionReglaDocumentos implements ReglaAccesoDocumentos {

    private final RecepcionRepository recepcionRepository;

    /**
     * @param recepcionRepository recepciones
     */
    public RecepcionReglaDocumentos(RecepcionRepository recepcionRepository) {
        this.recepcionRepository = recepcionRepository;
    }

    /**
     * Entidad gobernada.
     *
     * @return {@link EntidadDocumento#RECEPCION}
     */
    @Override
    public EntidadDocumento entidad() {
        return EntidadDocumento.RECEPCION;
    }

    /**
     * ALMACENISTA o ADMIN_COMPRAS, sobre una recepción de su empresa.
     *
     * @param recepcionId recepción
     * @param usuario     usuario de la sesión
     */
    @Override
    public void validarSubida(Long recepcionId, UsuarioAutenticado usuario) {
        buscar(recepcionId);
        if (usuario.esProveedor() || !usuario.tieneAlgunRol(Roles.ALMACENISTA, Roles.ADMIN_COMPRAS)) {
            throw new AccesoDenegadoException("Solo almacén o compras adjuntan evidencia de una recepción");
        }
    }

    /**
     * Internos que participan o el proveedor de la orden.
     *
     * @param recepcionId recepción
     * @param usuario     usuario de la sesión
     */
    @Override
    public void validarLectura(Long recepcionId, UsuarioAutenticado usuario) {
        Recepcion r = buscar(recepcionId);
        boolean interno = !usuario.esProveedor() && usuario.tieneAlgunRol(Roles.ALMACENISTA, Roles.COMPRADOR,
                Roles.ADMIN_COMPRAS, Roles.ADMINISTRATIVO, Roles.APROBADOR, Roles.AUDITOR);
        boolean dueno = usuario.esProveedor() && r.getOrden().getProveedor().getId().equals(usuario.proveedorId());
        if (!interno && !dueno) {
            throw new AccesoDenegadoException("No puede ver la evidencia de esta recepción");
        }
    }

    /**
     * Carga la recepción.
     *
     * @param id recepción
     * @return recepción con orden y proveedor
     * @throws NoEncontradoException si no existe en la empresa
     */
    private Recepcion buscar(Long id) {
        return recepcionRepository.findDetalleById(id).orElseThrow(() -> new NoEncontradoException("Recepción", id));
    }
}
