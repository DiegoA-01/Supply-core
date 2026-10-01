package com.proyecto.supply_core.documento.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.proyecto.supply_core.common.exception.NoEncontradoException;

/**
 * Almacenamiento en una carpeta local ({@code app.storage.ruta}).
 * <p>Clave: {@code AAAA/MM/uuid.ext}, generada aquí; el nombre del usuario nunca forma parte de
 * la ruta (evita {@code ../../} y choques de nombre). Se escribe primero a un temporal y luego
 * se mueve, así nunca queda un archivo a medias con su nombre final.</p>
 */
@Component
public class LocalStorageClient implements StorageClient {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageClient.class);

    private final Path raiz;

    /**
     * Crea la carpeta raíz si no existe.
     *
     * @param props configuración {@code app.storage.*}
     * @throws UncheckedIOException si no se puede crear la carpeta
     */
    public LocalStorageClient(StorageProperties props) {
        this.raiz = Path.of(props.ruta()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(raiz);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear la carpeta de almacenamiento " + raiz, e);
        }
        log.info("Almacenamiento de documentos en {}", raiz);
    }

    /**
     * Guarda el contenido en {@code AAAA/MM/uuid.ext}.
     *
     * @param contenido flujo del archivo
     * @param extension extensión sin punto
     * @return clave relativa con separador {@code /}
     * @throws UncheckedIOException si falla la escritura
     */
    @Override
    public String guardar(InputStream contenido, String extension) {
        LocalDate hoy = LocalDate.now();
        String clave = String.format("%d/%02d/%s.%s", hoy.getYear(), hoy.getMonthValue(), UUID.randomUUID(), extension);
        Path destino = resolver(clave);
        Path temporal = null;
        try {
            Files.createDirectories(destino.getParent());
            temporal = Files.createTempFile(destino.getParent(), "subida-", ".tmp");
            Files.copy(contenido, temporal, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE);
            return clave;
        } catch (IOException e) {
            borrarSilencioso(temporal);
            throw new UncheckedIOException("No se pudo guardar el archivo", e);
        }
    }

    /**
     * Abre el archivo de la clave.
     *
     * @param clave clave interna
     * @return recurso del archivo
     * @throws NoEncontradoException si el archivo ya no está en disco
     */
    @Override
    public Resource abrir(String clave) {
        Path archivo = resolver(clave);
        if (!Files.isRegularFile(archivo)) {
            log.error("Falta en disco el archivo {} registrado en la BD", clave);
            throw new NoEncontradoException("El archivo no está disponible en el almacenamiento");
        }
        return new PathResource(archivo);
    }

    /**
     * Elimina el archivo si existe.
     *
     * @param clave clave interna
     */
    @Override
    public void eliminar(String clave) {
        borrarSilencioso(resolver(clave));
    }

    /**
     * Convierte la clave en ruta absoluta, impidiendo salir de la carpeta raíz.
     *
     * @param clave clave interna
     * @return ruta dentro de la raíz
     * @throws IllegalArgumentException si la clave intenta salir de la raíz
     */
    Path resolver(String clave) {
        Path ruta = raiz.resolve(clave).normalize();
        if (!ruta.startsWith(raiz)) {
            throw new IllegalArgumentException("Clave de almacenamiento inválida");
        }
        return ruta;
    }

    /**
     * Borra un archivo sin propagar errores (limpieza).
     *
     * @param ruta archivo a borrar o {@code null}
     */
    private static void borrarSilencioso(Path ruta) {
        if (ruta == null) {
            return;
        }
        try {
            Files.deleteIfExists(ruta);
        } catch (IOException e) {
            log.warn("No se pudo borrar {}", ruta, e);
        }
    }
}
