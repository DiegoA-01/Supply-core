package com.proyecto.supply_core.documento.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.documento.acceso.ReglaAccesoDocumentos;
import com.proyecto.supply_core.documento.entity.Documento;
import com.proyecto.supply_core.documento.enums.EntidadDocumento;
import com.proyecto.supply_core.documento.enums.TipoArchivo;
import com.proyecto.supply_core.documento.repository.DocumentoRepository;
import com.proyecto.supply_core.documento.storage.LocalStorageClient;
import com.proyecto.supply_core.documento.storage.StorageProperties;
import com.proyecto.supply_core.proveedor.repository.ProveedorRepository;
import com.proyecto.supply_core.proveedor.service.ProveedorReglaDocumentos;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

class DocumentoServiceTest {

    private static final byte[] PDF = "%PDF-1.7 contenido".getBytes();

    @TempDir
    Path carpeta;

    private final DocumentoRepository repo = mock(DocumentoRepository.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final ProveedorRepository proveedores = mock(ProveedorRepository.class);
    private final ReglaAccesoDocumentos regla = new ProveedorReglaDocumentos(proveedores);
    private DocumentoService service;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        service = new DocumentoService(repo, new LocalStorageClient(new StorageProperties(carpeta.toString(), 1024)),
                auditoria, new StorageProperties(carpeta.toString(), 1024), List.of(regla));
        request = new MockHttpServletRequest();
        sesion(new UsuarioAutenticado(9L, "p@x.com", "Proveedor", Set.of("PROVEEDOR"), 40L));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(proveedores.existsById(40L)).thenReturn(true);
        when(proveedores.existsById(41L)).thenReturn(true);
        when(repo.save(any())).thenAnswer(inv -> {
            Documento d = inv.getArgument(0);
            d.setId(5L);
            return d;
        });
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void elProveedorSubeSuPropioDocumento() throws Exception {
        var r = service.subir(EntidadDocumento.PROVEEDOR, 40L,
                new MockMultipartFile("archivo", "C:\\fakepath\\RUT 2026.PDF", "text/plain", PDF));

        assertEquals("RUT 2026.PDF", r.nombreArchivo());       // sin la ruta del cliente
        assertEquals("application/pdf", r.tipoMime());         // MIME real, no el "text/plain" del cliente
        assertEquals(PDF.length, r.tamanoBytes());
        ArgumentCaptor<Documento> d = ArgumentCaptor.forClass(Documento.class);
        verify(repo).save(d.capture());
        assertTrue(Files.isRegularFile(carpeta.resolve(d.getValue().getRutaStorage())));
        verify(auditoria).exito(eq(AccionAuditoria.DOCUMENTO_SUBIR), eq(EntidadAuditada.DOCUMENTO), eq(5L), anyString());
    }

    @Test
    void archivoVacioGrandeOTipoNoPermitidoSeRechaza() {
        assertThrows(ParametroInvalidoException.class, () -> service.subir(EntidadDocumento.PROVEEDOR, 40L,
                new MockMultipartFile("archivo", "rut.pdf", "application/pdf", new byte[0])));
        assertThrows(ParametroInvalidoException.class, () -> service.subir(EntidadDocumento.PROVEEDOR, 40L,
                new MockMultipartFile("archivo", "rut.pdf", "application/pdf", new byte[2048])));
        var ex = assertThrows(ParametroInvalidoException.class, () -> service.subir(EntidadDocumento.PROVEEDOR, 40L,
                new MockMultipartFile("archivo", "script.exe", "application/pdf", PDF)));
        assertTrue(ex.getMessage().contains("Se aceptan"));
        verifyNoInteractions(repo);
    }

    @Test
    void ejecutableRenombradoAPdfSeRechaza() {
        byte[] exe = { 0x4D, 0x5A, (byte) 0x90, 0, 3, 0, 0, 0, 4, 0, 0, 0 };
        var ex = assertThrows(ParametroInvalidoException.class, () -> service.subir(EntidadDocumento.PROVEEDOR, 40L,
                new MockMultipartFile("archivo", "factura.pdf", "application/pdf", exe)));
        assertEquals("El contenido del archivo no corresponde a un PDF", ex.getMessage());
    }

    @Test
    void unProveedorNoVeNiSubeDocumentosDeOtro() {
        assertThrows(AccesoDenegadoException.class, () -> service.subir(EntidadDocumento.PROVEEDOR, 41L,
                new MockMultipartFile("archivo", "rut.pdf", "application/pdf", PDF)));
        assertThrows(AccesoDenegadoException.class, () -> service.listar(EntidadDocumento.PROVEEDOR, 41L));

        Documento ajeno = new Documento();
        ajeno.setEntidadTipo("PROVEEDOR");
        ajeno.setEntidadId(41L);
        when(repo.findById(7L)).thenReturn(Optional.of(ajeno));
        assertThrows(AccesoDenegadoException.class, () -> service.descargar(7L));
    }

    @Test
    void internosSegunSuRol() {
        sesion(new UsuarioAutenticado(1L, "c@x.com", "Comprador", Set.of("COMPRADOR"), null));
        when(repo.findByEntidadTipoAndEntidadIdOrderByCreadoEnDesc("PROVEEDOR", 41L)).thenReturn(List.of());
        assertTrue(service.listar(EntidadDocumento.PROVEEDOR, 41L).isEmpty()); // consulta
        assertThrows(AccesoDenegadoException.class, () -> service.subir(EntidadDocumento.PROVEEDOR, 41L,
                new MockMultipartFile("archivo", "rut.pdf", "application/pdf", PDF))); // pero no sube

        sesion(new UsuarioAutenticado(2L, "a@x.com", "Almacén", Set.of("ALMACENISTA"), null));
        assertThrows(AccesoDenegadoException.class, () -> service.listar(EntidadDocumento.PROVEEDOR, 41L));
    }

    @Test
    void entidadSinReglaNoAdmiteDocumentos() {
        var ex = assertThrows(NegocioException.class, () -> service.subir(EntidadDocumento.PAGO, 1L,
                new MockMultipartFile("archivo", "pago.pdf", "application/pdf", PDF)));
        assertEquals("ENTIDAD_SIN_DOCUMENTOS", ex.getCodigo());
    }

    @Test
    void siLaTransaccionSeRevierteSeBorraElArchivo() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        doThrow(new IllegalStateException("fallo al auditar")).when(auditoria)
                .exito(any(), any(), any(), anyString());

        assertThrows(IllegalStateException.class, () -> service.subir(EntidadDocumento.PROVEEDOR, 40L,
                new MockMultipartFile("archivo", "rut.pdf", "application/pdf", PDF)));
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK); // lo que hace Spring al revertir
        }
        try (var archivos = Files.walk(carpeta)) {
            assertFalse(archivos.anyMatch(Files::isRegularFile));
        }
    }

    @Test
    void nombreSeguroQuitaRutasYControles() {
        assertEquals("rut.pdf", DocumentoService.nombreSeguro("../../etc/rut.pdf", TipoArchivo.PDF));
        assertEquals("archivo.pdf", DocumentoService.nombreSeguro(".pdf", TipoArchivo.PDF));
        assertEquals("archivo.jpeg", DocumentoService.nombreSeguro(null, TipoArchivo.JPEG));
        assertEquals("ab.pdf", DocumentoService.nombreSeguro("a\"\nb.pdf", TipoArchivo.PDF));
        assertEquals(250, DocumentoService.nombreSeguro("x".repeat(300) + ".pdf", TipoArchivo.PDF).length());
    }

    @Test
    void dosReglasParaLaMismaEntidadImpidenArrancar() {
        assertThrows(IllegalStateException.class, () -> new DocumentoService(repo,
                new LocalStorageClient(new StorageProperties(carpeta.toString(), 1024)), auditoria,
                new StorageProperties(carpeta.toString(), 1024), List.of(regla, regla)));
    }

    private void sesion(UsuarioAutenticado u) {
        request.setAttribute(SesionActual.ATRIBUTO, u);
    }
}
