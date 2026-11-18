package com.proyecto.supply_core.notificacion.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.web.WebTestBase;
import com.proyecto.supply_core.notificacion.dto.CantidadResponse;
import com.proyecto.supply_core.notificacion.dto.ConfigNotificacionResponse;
import com.proyecto.supply_core.notificacion.dto.NotificacionResponse;
import com.proyecto.supply_core.notificacion.enums.CanalNotificacion;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;

@WebMvcTest({ NotificacionController.class, ConfigNotificacionController.class })
class NotificacionControllerTest extends WebTestBase {

    @MockitoBean
    private NotificacionService notificacionService;

    @Test
    void bandejaParaCualquierUsuarioIncluidoProveedor() throws Exception {
        when(notificacionService.mias(eq(true), any())).thenReturn(new PageResponse<>(
                List.of(new NotificacionResponse(1L, EventoNotificacion.STOCK_BAJO_MINIMO, "m", false, null)),
                0, 20, 1, 1));
        when(notificacionService.noLeidas()).thenReturn(new CantidadResponse(1));

        mvc.perform(get("/api/notificaciones?soloNoLeidas=true").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].evento").value("STOCK_BAJO_MINIMO"));
        mvc.perform(get("/api/notificaciones/no-leidas").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidad").value(1));
        mvc.perform(get("/api/notificaciones")).andExpect(status().isUnauthorized());
    }

    @Test
    void marcarLeida() throws Exception {
        when(notificacionService.marcarLeida(7L)).thenReturn(
                new NotificacionResponse(7L, EventoNotificacion.PROVEEDOR_PENDIENTE, "m", true, null));
        when(notificacionService.marcarLeida(8L)).thenThrow(new NoEncontradoException("Notificación no encontrada"));
        when(notificacionService.marcarTodasLeidas()).thenReturn(new CantidadResponse(3));

        mvc.perform(patch("/api/notificaciones/7/leida").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.leida").value(true));
        mvc.perform(patch("/api/notificaciones/8/leida").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS")))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/notificaciones/0/leida").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("id: debe ser un id válido")));
        mvc.perform(patch("/api/notificaciones/leidas").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cantidad").value(3));
    }

    @Test
    void configuracionSoloAdminSistemaYValidada() throws Exception {
        when(notificacionService.configuracion()).thenReturn(List.of(
                new ConfigNotificacionResponse(1L, EventoNotificacion.STOCK_BAJO_MINIMO, CanalNotificacion.EMAIL, true)));
        when(notificacionService.cambiarConfiguracion(1L, false)).thenReturn(
                new ConfigNotificacionResponse(1L, EventoNotificacion.STOCK_BAJO_MINIMO, CanalNotificacion.EMAIL, false));

        mvc.perform(get("/api/config-notificaciones").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/config-notificaciones").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].canal").value("EMAIL"));
        mvc.perform(patch("/api/config-notificaciones/1").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("activo: es obligatorio")));
        mvc.perform(patch("/api/config-notificaciones/1").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
        verify(notificacionService).cambiarConfiguracion(1L, false);
    }

    @Test
    void proveedorNoConfigura() throws Exception {
        mvc.perform(patch("/api/config-notificaciones/1").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(notificacionService);
    }
}
