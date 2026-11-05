package com.proyecto.supply_core.documento.acceso;

import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.documento.enums.EntidadDocumento;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

/**
 * Quién puede subir y leer documentos de un tipo de entidad. Cada módulo dueño de la entidad
 * implementa la suya como {@code @Component} (p. ej. {@code ProveedorReglaDocumentos}); el módulo
 * {@code documento} no conoce las reglas de negocio de los demás.
 * <p>Si una entidad no tiene regla registrada, no se pueden subir ni leer sus documentos.</p>
 */
public interface ReglaAccesoDocumentos {

    /**
     * Entidad que gobierna esta regla.
     *
     * @return tipo de entidad
     */
    EntidadDocumento entidad();

    /**
     * Verifica que el usuario pueda adjuntar documentos al registro.
     *
     * @param entidadId id del registro
     * @param usuario   usuario de la sesión
     * @throws NoEncontradoException   si el registro no existe
     * @throws AccesoDenegadoException si el usuario no puede adjuntar
     */
    void validarSubida(Long entidadId, UsuarioAutenticado usuario);

    /**
     * Verifica que el usuario pueda ver los documentos del registro.
     *
     * @param entidadId id del registro
     * @param usuario   usuario de la sesión
     * @throws NoEncontradoException   si el registro no existe
     * @throws AccesoDenegadoException si el usuario no puede verlos
     */
    void validarLectura(Long entidadId, UsuarioAutenticado usuario);
}
