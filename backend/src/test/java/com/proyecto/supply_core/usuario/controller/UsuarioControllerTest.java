package com.proyecto.supply_core.usuario.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.common.web.WebTestBase;
import com.proyecto.supply_core.usuario.dto.RolResponse;
import com.proyecto.supply_core.usuario.dto.UsuarioResponse;
import com.proyecto.supply_core.usuario.entity.Rol;
import com.proyecto.supply_core.usuario.service.UsuarioService;

@WebMvcTest({ UsuarioController.class, PerfilController.class, RolController.class })
class UsuarioControllerTest extends WebTestBase {

    private static final Set<String> ROLES_BD = Set.of("ADMIN_SISTEMA", "COMPRADOR", "AUDITOR", "PROVEEDOR");
    private static final String ALTA_VALIDA = """
            {"nombreCompleto":"Ana","correo":"ana@x.com","password":"Clave1234","roles":["comprador"]}""";

    @MockitoBean
    private UsuarioService usuarioService;

    private final UsuarioResponse ana = new UsuarioResponse(5L, "Ana", "ana@x.com", true, Set.of("COMPRADOR"),
            null, null, null, null);

    @BeforeEach
    void rolesExistentes() {
        when(rolRepository.findByCodigoIn(anyCollection())).thenAnswer(inv -> {
            Collection<String> pedidos = inv.getArgument(0);
            return pedidos.stream().filter(ROLES_BD::contains).map(c -> {
                Rol r = new Rol();
                r.setCodigo(c);
                return r;
            }).toList();
        });
    }

    // ------------------------------------------------------------- permisos

    @Test
    void sinTokenDevuelve401() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
    }

    @Test
    void compradorNoPuedeAdministrarUsuarios() throws Exception {
        mvc.perform(get("/api/usuarios").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
        verifyNoInteractions(usuarioService);
    }

    @Test
    void auditorConsultaPeroNoCrea() throws Exception {
        when(usuarioService.buscar(any(), any(), any(), any())).thenReturn(new PageResponse<>(List.of(ana), 0, 20, 1, 1));

        mvc.perform(get("/api/usuarios").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].correo").value("ana@x.com"))
                .andExpect(jsonPath("$.totalElementos").value(1));
        mvc.perform(post("/api/usuarios").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(ALTA_VALIDA))
                .andExpect(status().isForbidden());
    }

    @Test
    void rolesSoloParaAdminYAuditor() throws Exception {
        when(usuarioService.listarRoles()).thenReturn(List.of(new RolResponse(1L, "COMPRADOR", "Comprador", null)));

        mvc.perform(get("/api/roles").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].codigo").value("COMPRADOR"));
        mvc.perform(get("/api/roles").header(HttpHeaders.AUTHORIZATION, bearer("SOLICITANTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void perfilParaCualquierAutenticado() throws Exception {
        when(usuarioService.perfil()).thenReturn(ana);

        mvc.perform(get("/api/perfil").header(HttpHeaders.AUTHORIZATION, bearer("SOLICITANTE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombreCompleto").value("Ana"));
    }

    // ------------------------------------------------------------- alta

    @Test
    void altaValidaDevuelve201SinHash() throws Exception {
        when(usuarioService.crear(any())).thenReturn(ana);

        mvc.perform(post("/api/usuarios").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA"))
                        .contentType(MediaType.APPLICATION_JSON).content(ALTA_VALIDA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.hashPassword").doesNotExist());
    }

    @Test
    void altaConCorreoYaRegistradoDevuelve400() throws Exception {
        when(usuarioRepository.existsByCorreoIgnoreCase("ana@x.com")).thenReturn(true);

        mvc.perform(post("/api/usuarios").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA"))
                        .contentType(MediaType.APPLICATION_JSON).content(ALTA_VALIDA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("correo: ya está registrado por otro usuario")));
        verifyNoInteractions(usuarioService);
    }

    @Test
    void altaInvalidaReportaCadaCampo() throws Exception {
        mvc.perform(post("/api/usuarios").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"nombreCompleto":"","correo":"malo","password":"corta","roles":["JEFE","PROVEEDOR"]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItems(
                        "nombreCompleto: es obligatorio",
                        "correo: no tiene un formato de correo válido",
                        "password: debe tener entre 8 y 72 caracteres",
                        "roles: contiene roles inexistentes: JEFE",
                        "proveedorId: es obligatorio para usuarios con rol PROVEEDOR")));
    }

    // ------------------------------------------------------------- edición y estados

    @Test
    void edicionConIdYBodyInvalidosReportaLosCamposDelBody() throws Exception {
        mvc.perform(put("/api/usuarios/5").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"nombreCompleto":"","correo":"x@x.com","roles":["COMPRADOR"]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("nombreCompleto: es obligatorio")));
    }

    @Test
    void idNegativoDevuelve400() throws Exception {
        mvc.perform(get("/api/usuarios/-1").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("id: debe ser un id válido")));
    }

    @Test
    void idNoNumericoDevuelve400() throws Exception {
        mvc.perform(get("/api/usuarios/abc").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PETICION_INVALIDA"));
    }

    @Test
    void usuarioInexistenteDevuelve404() throws Exception {
        when(usuarioService.obtener(99L)).thenThrow(new NoEncontradoException("Usuario", 99L));

        mvc.perform(get("/api/usuarios/99").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Usuario con id 99 no existe"));
    }

    @Test
    void reglaDeNegocioDevuelve409ConSuCodigo() throws Exception {
        when(usuarioService.desactivar(1L))
                .thenThrow(new NegocioException("AUTO_DESACTIVACION", "No puede desactivar su propio usuario"));

        mvc.perform(patch("/api/usuarios/1/desactivar").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("AUTO_DESACTIVACION"));
    }

    @Test
    void restablecerPasswordValidaLaClaveYResponde204() throws Exception {
        mvc.perform(put("/api/usuarios/5/password").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"passwordNueva":"soloLetras"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("passwordNueva: debe tener al menos una letra y un número")));

        mvc.perform(put("/api/usuarios/5/password").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"passwordNueva":"Nueva1234"}"""))
                .andExpect(status().isNoContent());
        verify(usuarioService).restablecerPassword(eq(5L), any());
    }

    // ------------------------------------------------------------- errores generales

    @Test
    void ordenNoPermitidoDevuelve400() throws Exception {
        when(usuarioService.buscar(any(), any(), any(), any()))
                .thenThrow(new ParametroInvalidoException("No se puede ordenar por 'hashPassword'"));

        mvc.perform(get("/api/usuarios?sort=hashPassword").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PETICION_INVALIDA"));
    }

    @Test
    void metodoNoPermitidoDevuelve405() throws Exception {
        mvc.perform(delete("/api/usuarios/5").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_SISTEMA")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));
    }
}
