package com.proyecto.supply_core.documento.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.documento.acceso.ReglaAccesoDocumentos;
import com.proyecto.supply_core.documento.dto.ArchivoDescarga;
import com.proyecto.supply_core.documento.dto.DocumentoResponse;
import com.proyecto.supply_core.documento.entity.Documento;
import com.proyecto.supply_core.documento.enums.EntidadDocumento;
import com.proyecto.supply_core.documento.enums.TipoArchivo;
import com.proyecto.supply_core.documento.repository.DocumentoRepository;
import com.proyecto.supply_core.documento.storage.StorageClient;
import com.proyecto.supply_core.documento.storage.StorageProperties;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

/**
 * Subida, consulta y descarga de documentos. Los archivos van al {@link StorageClient};
 * en la BD solo queda la metadata. Los documentos no se borran (son evidencia).
 * <p>Validación de cada archivo: no vacío, tamaño máximo, extensión permitida y firma real
 * del contenido coherente con la extensión. El acceso lo decide la {@link ReglaAccesoDocumentos}
 * del módulo dueño de la entidad.</p>
 */
@Service
public class DocumentoService {

    /** Largo máximo del nombre guardado ({@code documento.nombre_archivo}). */
    static final int LARGO_MAXIMO_NOMBRE = 250;

    private final DocumentoRepository documentoRepository;
    private final StorageClient storageClient;
    private final AuditoriaService auditoriaService;
    private final long tamanoMaximo;
    private final Map<EntidadDocumento, ReglaAccesoDocumentos> reglas = new EnumMap<>(EntidadDocumento.class);

    /**
     * @param documentoRepository acceso a la metadata
     * @param storageClient       almacenamiento de archivos
     * @param auditoriaService    registro de subidas
     * @param props               configuración (tamaño máximo)
     * @param reglas              reglas de acceso registradas por los módulos
     * @throws IllegalStateException si dos reglas declaran la misma entidad
     */
    public DocumentoService(DocumentoRepository documentoRepository, StorageClient storageClient,
            AuditoriaService auditoriaService, StorageProperties props, List<ReglaAccesoDocumentos> reglas) {
        this.documentoRepository = documentoRepository;
        this.storageClient = storageClient;
        this.auditoriaService = auditoriaService;
        this.tamanoMaximo = props.tamanoMaximoBytes();
        for (ReglaAccesoDocumentos r : reglas) {
            if (this.reglas.put(r.entidad(), r) != null) {
                throw new IllegalStateException("Hay dos reglas de documentos para " + r.entidad());
            }
        }
    }

    /**
     * Adjunta un archivo a un registro.
     * <p>Si la transacción se revierte después de guardar el archivo, el archivo se borra
     * (no quedan archivos huérfanos sin metadata).</p>
     *
     * @param entidad   tipo de registro
     * @param entidadId id del registro
     * @param archivo   archivo recibido
     * @return metadata del documento creado
     * @throws ParametroInvalidoException si el archivo está vacío, es muy grande o su tipo no se acepta
     * @throws NegocioException           si la entidad no admite documentos
     * @throws AccesoDenegadoException    si el usuario no puede adjuntar a ese registro
     */
    @Transactional
    public DocumentoResponse subir(EntidadDocumento entidad, Long entidadId, MultipartFile archivo) {
        UsuarioAutenticado usuario = SesionActual.usuario();
        regla(entidad).validarSubida(entidadId, usuario);
        TipoArchivo tipo = validarArchivo(archivo);

        String clave;
        try (InputStream in = archivo.getInputStream()) {
            clave = storageClient.guardar(in, tipo.extensionPrincipal());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo recibido", e);
        }
        borrarSiSeRevierte(clave);

        Documento d = new Documento();
        d.setNombreArchivo(nombreSeguro(archivo.getOriginalFilename(), tipo));
        d.setRutaStorage(clave);
        d.setTipoMime(tipo.mime());
        d.setTamanoBytes(archivo.getSize());
        d.setEntidadTipo(entidad.name());
        d.setEntidadId(entidadId);
        d.setSubidoPorUsuarioId(usuario.id());
        Documento guardado = documentoRepository.save(d);
        auditoriaService.exito(AccionAuditoria.DOCUMENTO_SUBIR, EntidadAuditada.DOCUMENTO, guardado.getId(),
                entidad + " " + entidadId + ": " + guardado.getNombreArchivo() + " (" + guardado.getTamanoBytes() + " bytes)");
        return DocumentoResponse.of(guardado);
    }

    /**
     * Documentos de un registro.
     *
     * @param entidad   tipo de registro
     * @param entidadId id del registro
     * @return metadata, más recientes primero
     * @throws AccesoDenegadoException si el usuario no puede verlos
     */
    @Transactional(readOnly = true)
    public List<DocumentoResponse> listar(EntidadDocumento entidad, Long entidadId) {
        regla(entidad).validarLectura(entidadId, SesionActual.usuario());
        return documentoRepository.findByEntidadTipoAndEntidadIdOrderByCreadoEnDesc(entidad.name(), entidadId)
                .stream().map(DocumentoResponse::of).toList();
    }

    /**
     * Metadata de un documento.
     *
     * @param id id del documento
     * @return metadata
     * @throws NoEncontradoException   si no existe
     * @throws AccesoDenegadoException si el usuario no puede verlo
     */
    @Transactional(readOnly = true)
    public DocumentoResponse obtener(Long id) {
        return DocumentoResponse.of(conAccesoDeLectura(id));
    }

    /**
     * Contenido de un documento para descargar.
     *
     * @param id id del documento
     * @return recurso, nombre, tipo y tamaño
     * @throws NoEncontradoException   si no existe o falta el archivo en el almacenamiento
     * @throws AccesoDenegadoException si el usuario no puede verlo
     */
    @Transactional(readOnly = true)
    public ArchivoDescarga descargar(Long id) {
        Documento d = conAccesoDeLectura(id);
        return new ArchivoDescarga(storageClient.abrir(d.getRutaStorage()), d.getNombreArchivo(), d.getTipoMime(),
                d.getTamanoBytes());
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Valida el archivo y detecta su tipo real.
     *
     * @param archivo archivo recibido
     * @return tipo aceptado
     * @throws ParametroInvalidoException si no cumple alguna regla
     */
    private TipoArchivo validarArchivo(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ParametroInvalidoException("El archivo está vacío");
        }
        if (archivo.getSize() > tamanoMaximo) {
            throw new ParametroInvalidoException("El archivo supera el máximo de " + (tamanoMaximo / (1024 * 1024)) + " MB");
        }
        TipoArchivo tipo = TipoArchivo.porNombre(archivo.getOriginalFilename()).orElseThrow(() ->
                new ParametroInvalidoException("Tipo de archivo no permitido. Se aceptan: " + TipoArchivo.extensionesAceptadas()));
        if (!tipo.coincide(cabecera(archivo))) {
            throw new ParametroInvalidoException("El contenido del archivo no corresponde a un " + tipo.name());
        }
        return tipo;
    }

    /**
     * Lee los primeros bytes del archivo para reconocer su firma.
     *
     * @param archivo archivo recibido
     * @return hasta {@link TipoArchivo#BYTES_FIRMA} bytes
     */
    private static byte[] cabecera(MultipartFile archivo) {
        try (InputStream in = archivo.getInputStream()) {
            return in.readNBytes(TipoArchivo.BYTES_FIRMA);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo recibido", e);
        }
    }

    /**
     * Deja solo el nombre (sin carpetas ni caracteres de control) y lo recorta.
     *
     * @param original nombre que envió el cliente
     * @param tipo     tipo detectado (para un nombre por defecto)
     * @return nombre seguro para mostrar y descargar
     */
    static String nombreSeguro(String original, TipoArchivo tipo) {
        String nombre = original == null ? "" : original;
        nombre = nombre.substring(Math.max(nombre.lastIndexOf('/'), nombre.lastIndexOf('\\')) + 1);
        nombre = nombre.replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (nombre.isEmpty() || nombre.startsWith(".")) {
            nombre = "archivo." + tipo.extensionPrincipal();
        }
        return nombre.length() <= LARGO_MAXIMO_NOMBRE ? nombre : nombre.substring(nombre.length() - LARGO_MAXIMO_NOMBRE);
    }

    /**
     * Si la transacción termina en rollback, borra el archivo ya guardado.
     *
     * @param clave clave del archivo en el almacenamiento
     */
    private void borrarSiSeRevierte(String clave) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    storageClient.eliminar(clave);
                }
            }
        });
    }

    /**
     * Busca el documento y verifica la lectura con la regla de su entidad.
     *
     * @param id id del documento
     * @return entidad
     * @throws NoEncontradoException   si no existe
     * @throws AccesoDenegadoException si no tiene acceso
     */
    private Documento conAccesoDeLectura(Long id) {
        Documento d = documentoRepository.findById(id).orElseThrow(() -> new NoEncontradoException("Documento", id));
        regla(EntidadDocumento.valueOf(d.getEntidadTipo())).validarLectura(d.getEntidadId(), SesionActual.usuario());
        return d;
    }

    /**
     * Regla de acceso de la entidad.
     *
     * @param entidad tipo de registro
     * @return regla registrada
     * @throws NegocioException si la entidad todavía no admite documentos
     */
    private ReglaAccesoDocumentos regla(EntidadDocumento entidad) {
        ReglaAccesoDocumentos r = reglas.get(entidad);
        if (r == null) {
            throw new NegocioException("ENTIDAD_SIN_DOCUMENTOS", "Los registros de tipo " + entidad + " no admiten documentos");
        }
        return r;
    }
}
