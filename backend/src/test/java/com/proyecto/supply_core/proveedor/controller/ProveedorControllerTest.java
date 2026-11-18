package com.proyecto.supply_core.proveedor.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.proyecto.supply_core.catalogo.entity.CategoriaProducto;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.web.WebTestBase;
import com.proyecto.supply_core.catalogo.dto.CategoriaResponse;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.proveedor.dto.EvaluacionResponse;
import com.proyecto.supply_core.proveedor.dto.ProveedorResponse;
import com.proyecto.supply_core.proveedor.dto.RegistroProveedorResponse;
import com.proyecto.supply_core.proveedor.enums.EstadoProveedor;
import com.proyecto.supply_core.proveedor.service.EvaluacionProveedorService;
import com.proyecto.supply_core.proveedor.service.ProveedorService;

@WebMvcTest({ RegistroProveedorController.class, ProveedorController.class, PortalProveedorController.class })
class ProveedorControllerTest extends WebTestBase {

    private static final String REGISTRO_VALIDO = """
            {"razonSocial":"Tech SAS","nit":"900123456-7","telefono":"+57 300 123 4567","categoriaIds":[1],
             "nombreContacto":"Laura","correo":"ventas@tech.com","password":"Clave1234"}""";

    @MockitoBean
    private ProveedorService proveedorService;

    @MockitoBean
    private EvaluacionProveedorService evaluacionService;

    private final ProveedorResponse tech = new ProveedorResponse(40L, "Tech SAS", "900123456-7", "ventas@tech.com",
            null, null, null, EstadoProveedor.HABILITADO, null, List.of(new CategoriaResponse(1L, "Tecnología", true)),
            null, null);

    @BeforeEach
    void categoriaUnoActiva() {
        when(empresaRepository.idPorCodigoPublico("demo")).thenReturn(java.util.Optional.of(1L));
        when(empresaRepository.idPorCodigoPublico("no-existe")).thenReturn(java.util.Optional.empty());
        when(categoriaRepository.findByIdInAndActivoTrue(anyCollection())).thenAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            return ids.stream().filter(id -> id == 1L).map(id -> {
                CategoriaProducto c = new CategoriaProducto();
                c.setId(id);
                return c;
            }).toList();
        });
    }

    // ------------------------------------------------------------- registro público

    @Test
    void registroEsPublicoYDevuelve201Pendiente() throws Exception {
        when(proveedorService.registrar(any()))
                .thenReturn(new RegistroProveedorResponse(40L, EstadoProveedor.PENDIENTE, "Registro recibido."));

        mvc.perform(post("/api/portal/registro/demo").contentType(MediaType.APPLICATION_JSON).content(REGISTRO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    void registroConCodigoDeEmpresaDesconocidoDevuelve404() throws Exception {
        mvc.perform(post("/api/portal/registro/no-existe").contentType(MediaType.APPLICATION_JSON).content(REGISTRO_VALIDO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("La empresa indicada no existe"));
    }

    @Test
    void categoriasDelRegistroSonPublicas() throws Exception {
        when(proveedorService.categoriasDisponibles()).thenReturn(List.of(new CategoriaResponse(1L, "Tecnología", true)));

        mvc.perform(get("/api/portal/registro/demo/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Tecnología"));
    }

    @Test
    void registroConNitYCorreoRepetidosDevuelve400() throws Exception {
        when(proveedorRepository.existsByNitIgnoreCase("900123456-7")).thenReturn(true);
        when(usuarioRepository.existsByCorreoIgnoreCase("ventas@tech.com")).thenReturn(true);

        mvc.perform(post("/api/portal/registro/demo").contentType(MediaType.APPLICATION_JSON).content(REGISTRO_VALIDO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItems(
                        "nit: ya está registrado por otro proveedor",
                        "correo: ya está registrado por otro usuario")));
        verifyNoInteractions(proveedorService);
    }

    @Test
    void registroInvalidoReportaCadaCampo() throws Exception {
        mvc.perform(post("/api/portal/registro/demo").contentType(MediaType.APPLICATION_JSON).content("""
                        {"razonSocial":"","nit":"90.012","telefono":"abc","categoriaIds":[1,999],
                         "nombreContacto":"","correo":"x","password":"abc"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItems(
                        "razonSocial: es obligatoria",
                        "nit: solo números, con dígito de verificación opcional (ej. 900123456-7)",
                        "telefono: no tiene un formato de teléfono válido",
                        "categoriaIds: contiene categorías inexistentes o inactivas: [999]",
                        "correo: no tiene un formato de correo válido")));
    }

    @Test
    void elRegistroNoAbreOtrasRutasDelPortal() throws Exception {
        mvc.perform(get("/api/portal/proveedor")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------- administración

    @Test
    void bandejaParaComprasCompradorYAuditorPeroNoProveedor() throws Exception {
        when(proveedorService.buscar(any(), any(), any(), any())).thenReturn(new com.proyecto.supply_core.common.dto
                .PageResponse<>(List.of(tech), 0, 20, 1, 1));

        mvc.perform(get("/api/proveedores?estado=PENDIENTE").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/proveedores").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/proveedores").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void soloAdminComprasAprueba() throws Exception {
        when(proveedorService.habilitar(40L)).thenReturn(tech);

        mvc.perform(patch("/api/proveedores/40/habilitar").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/proveedores/40/habilitar").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("HABILITADO"));
    }

    @Test
    void transicionInvalidaDevuelve409() throws Exception {
        when(proveedorService.habilitar(40L)).thenThrow(
                new NegocioException("TRANSICION_INVALIDA", "Un proveedor RECHAZADO no puede pasar a HABILITADO"));

        mvc.perform(patch("/api/proveedores/40/habilitar").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
    }

    @Test
    void suspenderExigeMotivoYReportaElCampo() throws Exception {
        mvc.perform(patch("/api/proveedores/40/suspender").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"motivo":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItem("motivo: es obligatorio")));
        verifyNoInteractions(proveedorService);
    }

    @Test
    void suspenderConMotivoConTildeFunciona() throws Exception {
        when(proveedorService.suspender(eq(40L), any())).thenReturn(tech);

        mvc.perform(patch("/api/proveedores/40/suspender").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"motivo":"Incumplió las entregas"}"""))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------- portal

    @Test
    void portalSoloParaProveedores() throws Exception {
        when(proveedorService.miProveedor()).thenReturn(tech);

        mvc.perform(get("/api/portal/proveedor").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(40));
        mvc.perform(get("/api/portal/proveedor").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS")))
                .andExpect(status().isForbidden());
    }

    @Test
    void elProveedorListaLasCategoriasParaEditarSuPerfilYSoloEl() throws Exception {
        when(proveedorService.categoriasDisponibles()).thenReturn(List.of(new CategoriaResponse(1L, "Tecnología", true)));

        mvc.perform(get("/api/portal/proveedor/categorias").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Tecnología"));
        mvc.perform(get("/api/portal/proveedor/categorias").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/portal/proveedor/categorias")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------- paso 9: perfil y evaluaciones

    @Test
    void edicionDePerfilValidadaYSoloPorRolCorrecto() throws Exception {
        when(proveedorService.actualizarMio(any())).thenReturn(tech);
        when(proveedorService.actualizar(eq(40L), any())).thenReturn(tech);
        String valido = """
                {"correoContacto":"compras@tech.com","telefono":"3001234567","categoriaIds":[1]}""";

        mvc.perform(put("/api/portal/proveedor").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L))
                        .contentType(MediaType.APPLICATION_JSON).content(valido))
                .andExpect(status().isOk());
        mvc.perform(put("/api/proveedores/40").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN_COMPRAS"))
                        .contentType(MediaType.APPLICATION_JSON).content(valido))
                .andExpect(status().isOk());
        mvc.perform(put("/api/proveedores/40").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(valido))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/portal/proveedor").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correoContacto\":\"no-es-correo\",\"categoriaIds\":[9]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles", hasItems("correoContacto: no tiene un formato de correo válido")));
    }

    @Test
    void evaluacionesParaInternosYParaElPropioProveedor() throws Exception {
        PageResponse<EvaluacionResponse> pagina = new PageResponse<>(List.of(new EvaluacionResponse(1L, 7L,
                new BigDecimal("90.00"), null, null, null, null, null, new BigDecimal("90.00"), null)), 0, 20, 1, 1);
        when(evaluacionService.listar(eq(40L), any())).thenReturn(pagina);
        when(evaluacionService.mias(any())).thenReturn(pagina);

        mvc.perform(get("/api/proveedores/40/evaluaciones").header(HttpHeaders.AUTHORIZATION, bearer("AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].score").value(90.0))
                .andExpect(jsonPath("$.contenido[0].calidad").doesNotExist());
        mvc.perform(get("/api/portal/proveedor/evaluaciones").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/proveedores/40/evaluaciones").header(HttpHeaders.AUTHORIZATION, bearerProveedor(40L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void filtroPorCategoriaValidado() throws Exception {
        mvc.perform(get("/api/proveedores?categoriaId=0").header(HttpHeaders.AUTHORIZATION, bearer("COMPRADOR")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(proveedorService);
    }
}
