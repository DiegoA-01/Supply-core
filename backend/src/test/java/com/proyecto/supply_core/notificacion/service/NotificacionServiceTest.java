package com.proyecto.supply_core.notificacion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.notificacion.email.EmailClient;
import com.proyecto.supply_core.notificacion.entity.ConfigNotificacion;
import com.proyecto.supply_core.notificacion.entity.Notificacion;
import com.proyecto.supply_core.notificacion.enums.CanalNotificacion;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.repository.ConfigNotificacionRepository;
import com.proyecto.supply_core.notificacion.repository.NotificacionRepository;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

class NotificacionServiceTest {

    private final NotificacionRepository notificaciones = mock(NotificacionRepository.class);
    private final ConfigNotificacionRepository config = mock(ConfigNotificacionRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final EmailClient email = mock(EmailClient.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final NotificacionService service =
            new NotificacionService(notificaciones, config, usuarios, email, auditoria);

    @BeforeEach
    void sesion() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(SesionActual.ATRIBUTO,
                new UsuarioAutenticado(5L, "comp@x.com", "Comprador", Set.of("COMPRADOR"), null));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void notificarRolGuardaUnaPorUsuarioYEnviaCorreo() {
        when(usuarios.findByActivoTrueAndRoles_Codigo("ADMIN_COMPRAS"))
                .thenReturn(List.of(usuario(1L, "a@x.com"), usuario(2L, "b@x.com")));

        int n = service.notificarRol("ADMIN_COMPRAS", EventoNotificacion.PROVEEDOR_PENDIENTE, "  Nuevo proveedor  ");

        assertEquals(2, n);
        ArgumentCaptor<List<Notificacion>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificaciones).saveAll(captor.capture());
        assertEquals(List.of(1L, 2L), captor.getValue().stream().map(Notificacion::getUsuarioId).toList());
        assertEquals("Nuevo proveedor", captor.getValue().get(0).getMensaje());
        verify(email).enviar("a@x.com", "Supply-Core: Proveedor pendiente de aprobación", "Nuevo proveedor");
        verify(email).enviar(eq("b@x.com"), anyString(), anyString());
    }

    @Test
    void canalDesactivadoNoSeUsa() {
        when(usuarios.findByActivoTrueAndRoles_Codigo("COMPRADOR")).thenReturn(List.of(usuario(1L, "a@x.com")));
        when(config.findByEventoAndCanal(EventoNotificacion.STOCK_BAJO_MINIMO, CanalNotificacion.EMAIL))
                .thenReturn(Optional.of(new ConfigNotificacion(EventoNotificacion.STOCK_BAJO_MINIMO,
                        CanalNotificacion.EMAIL, false)));

        service.notificarRol("COMPRADOR", EventoNotificacion.STOCK_BAJO_MINIMO, "Bajo mínimo");

        verify(notificaciones).saveAll(anyList()); // INTERNA sin fila = activo
        verifyNoInteractions(email);
    }

    @Test
    void elCorreoSaleSoloDespuesDelCommit() {
        TransactionSynchronizationManager.initSynchronization();
        when(usuarios.findById(1L)).thenReturn(Optional.of(usuario(1L, "a@x.com")));

        service.notificar(1L, EventoNotificacion.PROVEEDOR_PENDIENTE, "Hola");
        verify(email, never()).enviar(anyString(), anyString(), anyString());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(email).enviar(eq("a@x.com"), anyString(), eq("Hola"));
    }

    @Test
    void fallaDelCorreoNoRompeLaOperacion() {
        when(usuarios.findById(1L)).thenReturn(Optional.of(usuario(1L, "a@x.com")));
        doThrow(new IllegalStateException("SMTP caído")).when(email).enviar(anyString(), anyString(), anyString());

        service.notificar(1L, EventoNotificacion.PROVEEDOR_PENDIENTE, "Hola");

        verify(notificaciones).saveAll(anyList());
    }

    @Test
    void usuarioInactivoOSinDestinatariosNoGeneraNada() {
        Usuario inactivo = usuario(1L, "a@x.com");
        inactivo.setActivo(false);
        when(usuarios.findById(1L)).thenReturn(Optional.of(inactivo));

        service.notificar(1L, EventoNotificacion.PROVEEDOR_PENDIENTE, "Hola");
        assertEquals(0, service.notificarRol("AUDITOR", EventoNotificacion.PROVEEDOR_PENDIENTE, "Hola"));

        verifyNoInteractions(notificaciones, email);
    }

    @Test
    void mensajeLargoSeRecorta() {
        String largo = "x".repeat(600);
        String r = NotificacionService.recortar(largo);
        assertEquals(NotificacionService.MAX_MENSAJE, r.length());
        assertTrue(r.endsWith("…"));
        assertEquals("", NotificacionService.recortar(null));
    }

    @Test
    void soloSeMarcaLaPropiaYLaAjenaEs404() {
        Notificacion propia = new Notificacion(5L, EventoNotificacion.STOCK_BAJO_MINIMO, "m");
        when(notificaciones.findByIdAndUsuarioId(10L, 5L)).thenReturn(Optional.of(propia));
        when(notificaciones.findByIdAndUsuarioId(11L, 5L)).thenReturn(Optional.empty());

        assertTrue(service.marcarLeida(10L).leida());
        assertThrows(NoEncontradoException.class, () -> service.marcarLeida(11L));
    }

    @Test
    void marcarTodasYContarUsanElUsuarioDeLaSesion() {
        when(notificaciones.marcarTodasLeidas(5L)).thenReturn(3);
        when(notificaciones.countByUsuarioIdAndLeidaFalse(5L)).thenReturn(4L);

        assertEquals(3, service.marcarTodasLeidas().cantidad());
        assertEquals(4, service.noLeidas().cantidad());
    }

    @Test
    void cambiarConfiguracionAuditaSoloSiCambia() {
        ConfigNotificacion c = new ConfigNotificacion(EventoNotificacion.STOCK_BAJO_MINIMO, CanalNotificacion.EMAIL, true);
        when(config.findById(1L)).thenReturn(Optional.of(c));

        service.cambiarConfiguracion(1L, true);
        verifyNoInteractions(auditoria);

        assertEquals(false, service.cambiarConfiguracion(1L, false).activo());
        verify(auditoria).exito(eq(AccionAuditoria.CONFIG_NOTIFICACION_CAMBIAR),
                eq(EntidadAuditada.CONFIG_NOTIFICACION), any(), anyString());
        assertThrows(NoEncontradoException.class, () -> service.cambiarConfiguracion(99L, true));
    }

    /**
     * Usuario activo de prueba.
     *
     * @param id     id
     * @param correo correo
     * @return usuario
     */
    private static Usuario usuario(Long id, String correo) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setCorreo(correo);
        u.setActivo(true);
        return u;
    }
}
