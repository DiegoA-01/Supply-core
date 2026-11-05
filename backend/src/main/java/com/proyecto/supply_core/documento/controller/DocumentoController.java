package com.proyecto.supply_core.documento.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.proyecto.supply_core.documento.dto.ArchivoDescarga;
import com.proyecto.supply_core.documento.dto.DocumentoResponse;
import com.proyecto.supply_core.documento.enums.EntidadDocumento;
import com.proyecto.supply_core.documento.service.DocumentoService;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Documentos adjuntos (PDF, Excel, imágenes). Requiere sesión; quién puede subir o ver qué lo
 * decide la regla de acceso de cada entidad. No hay endpoint de borrado: los documentos son evidencia.
 */
@RestController
@RequestMapping("/api/documentos")
public class DocumentoController {

    private final DocumentoService documentoService;

    /**
     * @param documentoService lógica de documentos
     */
    public DocumentoController(DocumentoService documentoService) {
        this.documentoService = documentoService;
    }

    /**
     * Adjunta un archivo ({@code multipart/form-data}, campo {@code archivo}).
     *
     * @param entidad   tipo de registro (p. ej. PROVEEDOR)
     * @param entidadId id del registro
     * @param archivo   archivo (máx. 10 MB; pdf, xlsx, xls, png, jpg, webp)
     * @return metadata del documento (HTTP 201)
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentoResponse subir(
            @RequestParam @NotNull(message = "es obligatoria") EntidadDocumento entidad,
            @RequestParam @NotNull(message = "es obligatorio") @Positive(message = "debe ser un id válido") Long entidadId,
            @RequestPart("archivo") MultipartFile archivo) {
        return documentoService.subir(entidad, entidadId, archivo);
    }

    /**
     * Documentos de un registro.
     *
     * @param entidad   tipo de registro
     * @param entidadId id del registro
     * @return metadata, más recientes primero
     */
    @GetMapping
    public List<DocumentoResponse> listar(
            @RequestParam @NotNull(message = "es obligatoria") EntidadDocumento entidad,
            @RequestParam @NotNull(message = "es obligatorio") @Positive(message = "debe ser un id válido") Long entidadId) {
        return documentoService.listar(entidad, entidadId);
    }

    /**
     * Metadata de un documento.
     *
     * @param id id positivo
     * @return metadata
     */
    @GetMapping("/{id}")
    public DocumentoResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return documentoService.obtener(id);
    }

    /**
     * Descarga el archivo. Siempre como adjunto y con {@code nosniff}, para que el navegador no
     * ejecute ni reinterprete el contenido.
     *
     * @param id id positivo
     * @return bytes del archivo con su nombre y tipo
     */
    @GetMapping("/{id}/contenido")
    public ResponseEntity<Resource> descargar(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        ArchivoDescarga a = documentoService.descargar(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(a.tipoMime()))
                .contentLength(a.tamanoBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(a.nombreArchivo(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(a.contenido());
    }
}
