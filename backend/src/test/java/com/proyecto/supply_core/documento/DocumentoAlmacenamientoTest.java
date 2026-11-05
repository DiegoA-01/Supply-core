package com.proyecto.supply_core.documento;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.documento.enums.TipoArchivo;
import com.proyecto.supply_core.documento.storage.LocalStorageClient;
import com.proyecto.supply_core.documento.storage.StorageProperties;

/** Firmas de archivo y almacenamiento local. */
class DocumentoAlmacenamientoTest {

    static final byte[] PDF = "%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII);
    static final byte[] PNG = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0 };
    static final byte[] EXE = { 0x4D, 0x5A, (byte) 0x90, 0, 3, 0, 0, 0, 4, 0, 0, 0 }; // "MZ" de Windows

    @TempDir
    Path carpeta;

    @Test
    void reconoceTiposPorExtensionSinImportarMayusculas() {
        assertEquals(TipoArchivo.PDF, TipoArchivo.porNombre("Cotización.PDF").orElseThrow());
        assertEquals(TipoArchivo.JPEG, TipoArchivo.porNombre("foto.jpeg").orElseThrow());
        assertEquals(TipoArchivo.XLSX, TipoArchivo.porNombre("precios.xlsx").orElseThrow());
        assertTrue(TipoArchivo.porNombre("virus.exe").isEmpty());
        assertTrue(TipoArchivo.porNombre("sin_extension").isEmpty());
    }

    @Test
    void verificaLaFirmaRealDelContenido() {
        assertTrue(TipoArchivo.PDF.coincide(PDF));
        assertTrue(TipoArchivo.PNG.coincide(PNG));
        assertFalse(TipoArchivo.PDF.coincide(EXE)); // .exe renombrado a .pdf
        assertFalse(TipoArchivo.PDF.coincide(new byte[] { 0x25 }));
        assertTrue(TipoArchivo.WEBP.coincide("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII)));
        assertFalse(TipoArchivo.WEBP.coincide("RIFF\0\0\0\0WAVEfmt ".getBytes(StandardCharsets.US_ASCII))); // un WAV
    }

    @Test
    void guardaYRecuperaElMismoContenidoConClaveGenerada() throws Exception {
        LocalStorageClient storage = new LocalStorageClient(new StorageProperties(carpeta.toString(), 1024));

        String clave = storage.guardar(new ByteArrayInputStream(PDF), "pdf");

        assertTrue(clave.matches("\\d{4}/\\d{2}/[0-9a-f-]{36}\\.pdf"), clave);
        assertArrayEquals(PDF, storage.abrir(clave).getContentAsByteArray());
        try (var archivos = Files.walk(carpeta)) {
            assertTrue(archivos.noneMatch(p -> p.toString().endsWith(".tmp"))); // no quedan temporales
        }
        storage.eliminar(clave);
        assertThrows(NoEncontradoException.class, () -> storage.abrir(clave));
    }

    @Test
    void noPermiteSalirDeLaCarpetaRaiz() {
        LocalStorageClient storage = new LocalStorageClient(new StorageProperties(carpeta.toString(), 1024));
        assertThrows(IllegalArgumentException.class, () -> storage.abrir("../../windows/system32/config"));
        assertThrows(IllegalArgumentException.class, () -> storage.eliminar("../fuera.txt"));
    }
}
