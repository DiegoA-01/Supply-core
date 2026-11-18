package com.proyecto.supply_core.delegacion.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.proyecto.supply_core.common.web.WebTestBase;
import com.proyecto.supply_core.delegacion.dto.DelegacionResponse;
import com.proyecto.supply_core.delegacion.enums.EstadoDelegacion;
import com.proyecto.supply_core.delegacion.service.DelegacionService;

@WebMvcTest(DelegacionController.class)
class DelegacionControllerTest extends WebTestBase {

    @MockitoBean
    private DelegacionService delegacionService;

    private final LocalDate hoy = LocalDate.now();

    private DelegacionResponse respuesta() {
        return new DelegacionResponse(9L, 20L, "Mario", 30L, "Sofía", hoy, hoy.plusDays(7), EstadoDelegacion.ACTIVA,
                true, "Vacaciones", null, null);
    }

    private String cuerpo(String delegado, String desde, String hasta) {
        return "{\"delegadoId\":" + delegado + ",\"vigenteDesde\":\"" + desde + "\",\"vigenteHasta\":\"" + hasta
                + "\",\"motivo\":\"Vacaciones\"}";
    }

    @Test
    void losCandidatosSoloMuestranIdYNombreYExigenSesionInterna() throws Exception {
        when(delegacionService.candidatos()).thenReturn(java.util.List.of(
                new com.proyecto.supply_core.delegacion.dto.DelegadoCandidato(30L, "Sofía")));

        mvc.perform(get("/api/delegaciones/candidatos").header(HttpHeaders.AUTHORIZATION, bearer("APROBADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(30))
                .andExpect(jsonPath("$[0].nombre").value("Sofía"))
                .andExpect(jsonPath("$[0].correo").doesNotExist());
        mvc.perform(get("/api/delegaciones/candidatos").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/delegaciones/candidatos")).andExpect(status().isUnauthorized());
    }

    @Test
    void cualquierUsuarioInternoDelegaYSeValidanLosDatos() throws Exception {
        when(delegacionService.crear(any())).thenReturn(respuesta());

        mvc.perform(post("/api/delegaciones").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("30", hoy.toString(), hoy.plusDays(7).toString()))
                        .header(HttpHeaders.AUTHORIZATION, bearer("APROBADOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.delegadoNombre").value("Sofía"));

        String[] malos = {
                cuerpo("0", hoy.toString(), hoy.toString()),
                cuerpo("30", hoy.plusDays(5).toString(), hoy.toString()),
                "{\"vigenteDesde\":\"" + hoy + "\",\"vigenteHasta\":\"" + hoy + "\"}",
                "{\"delegadoId\":30,\"vigenteHasta\":\"" + hoy + "\"}" };
        for (String malo : malos) {
            mvc.perform(post("/api/delegaciones").contentType(MediaType.APPLICATION_JSON).content(malo)
                            .header(HttpHeaders.AUTHORIZATION, bearer("APROBADOR")))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void elProveedorYQuienNoEstaAutenticadoNoDelegan() throws Exception {
        mvc.perform(post("/api/delegaciones").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("30", hoy.toString(), hoy.toString()))
                        .header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/delegaciones").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("30", hoy.toString(), hoy.toString())))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(delegacionService);
    }

    @Test
    void revocarYConsultarLasPropias() throws Exception {
        when(delegacionService.revocar(9L)).thenReturn(respuesta());
        when(delegacionService.otorgadas()).thenReturn(List.of(respuesta()));
        when(delegacionService.recibidas()).thenReturn(List.of());

        mvc.perform(patch("/api/delegaciones/9/revocar").header(HttpHeaders.AUTHORIZATION, bearer("APROBADOR")))
                .andExpect(status().isOk());
        verify(delegacionService).revocar(9L);
        mvc.perform(patch("/api/delegaciones/0/revocar").header(HttpHeaders.AUTHORIZATION, bearer("APROBADOR")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/delegaciones/otorgadas").header(HttpHeaders.AUTHORIZATION, bearer("APROBADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(9));
        mvc.perform(get("/api/delegaciones/recibidas").header(HttpHeaders.AUTHORIZATION, bearer("ALMACENISTA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
