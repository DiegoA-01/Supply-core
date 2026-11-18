package com.proyecto.supply_core.documento.storage;

import java.io.InputStream;

import org.springframework.core.io.Resource;

/**
 * Almacenamiento de archivos fuera de la BD. El resto del sistema solo conoce esta interfaz:
 * hoy {@link LocalStorageClient} (carpeta local); mañana se puede cambiar por S3 u otro sin
 * tocar el dominio.
 */
public interface StorageClient {

    /**
     * Guarda un archivo con una clave generada por el sistema.
     *
     * @param contenido flujo del archivo (quien llama lo cierra)
     * @param extension extensión sin punto (p. ej. {@code pdf})
     * @return clave interna para recuperarlo después
     */
    String guardar(InputStream contenido, String extension);

    /**
     * Abre un archivo guardado.
     *
     * @param clave clave devuelta por {@link #guardar}
     * @return recurso legible
     */
    Resource abrir(String clave);

    /**
     * Elimina un archivo. Solo se usa para deshacer una subida cuya metadata no se pudo guardar.
     *
     * @param clave clave del archivo
     */
    void eliminar(String clave);
}
