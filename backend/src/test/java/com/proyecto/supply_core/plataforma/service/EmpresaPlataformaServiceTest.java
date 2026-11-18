package com.proyecto.supply_core.plataforma.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.empresa.entity.Empresa;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.plataforma.dto.EmpresaCrearRequest;
import com.proyecto.supply_core.plataforma.entity.EmpresaModulo;
import com.proyecto.supply_core.plataforma.entity.Modulo;
import com.proyecto.supply_core.plataforma.entity.Plan;
import com.proyecto.supply_core.plataforma.entity.Suscripcion;
import com.proyecto.supply_core.plataforma.enums.AccionPlataforma;
import com.proyecto.supply_core.plataforma.enums.EstadoSuscripcion;
import com.proyecto.supply_core.plataforma.repository.EmpresaModuloRepository;
import com.proyecto.supply_core.plataforma.repository.ModuloRepository;
import com.proyecto.supply_core.plataforma.repository.PlanRepository;
import com.proyecto.supply_core.plataforma.repository.SuscripcionRepository;
import com.proyecto.supply_core.security.context.PlataformaActual;
import com.proyecto.supply_core.security.context.PlataformaAutenticada;
import com.proyecto.supply_core.security.repository.RefreshTokenRepository;
import com.proyecto.supply_core.usuario.repository.AdministradorVista;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

class EmpresaPlataformaServiceTest {

    private final EmpresaRepository empresas = mock(EmpresaRepository.class);
    private final SuscripcionRepository suscripciones = mock(SuscripcionRepository.class);
    private final PlanRepository planes = mock(PlanRepository.class);
    private final ModuloRepository modulos = mock(ModuloRepository.class);
    private final EmpresaModuloRepository excepciones = mock(EmpresaModuloRepository.class);
    private final EmpresaAltaService alta = mock(EmpresaAltaService.class);
    private final AccesoEmpresaService acceso = mock(AccesoEmpresaService.class);
    private final PlataformaAuditoriaService auditoria = mock(PlataformaAuditoriaService.class);
    private final RefreshTokenRepository refresh = mock(RefreshTokenRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final EmpresaPlataformaService service = new EmpresaPlataformaService(empresas, suscripciones, planes,
            modulos, excepciones, alta, acceso, auditoria, refresh, usuarios);

    private Plan basico;
    private Plan pro;
    private Empresa empresa;
    private Suscripcion suscripcion;

    @BeforeEach
    void setUp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(PlataformaActual.ATRIBUTO, new PlataformaAutenticada(3L, "dueno@x.com", "Dueño"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        basico = plan(1L, "BASICO", "INVENTARIO", "COMPRAS");
        pro = plan(2L, "PROFESIONAL", "INVENTARIO", "COMPRAS", "REPORTES");
        when(planes.findByCodigoIgnoreCase("BASICO")).thenReturn(Optional.of(basico));
        when(planes.findByCodigoIgnoreCase("PROFESIONAL")).thenReturn(Optional.of(pro));
        empresa = new Empresa();
        empresa.setId(8L);
        empresa.setRazonSocial("Ferretería El Tornillo");
        empresa.setNit("900456789-1");
        empresa.setCodigoPublico("el-tornillo");
        suscripcion = new Suscripcion();
        suscripcion.setEmpresaId(8L);
        suscripcion.setPlan(basico);
        suscripcion.setEstado(EstadoSuscripcion.ACTIVA);
        when(empresas.findById(8L)).thenReturn(Optional.of(empresa));
        when(suscripciones.findByEmpresaId(8L)).thenReturn(Optional.of(suscripcion));
        when(excepciones.findByEmpresaId(8L)).thenReturn(List.of());
        List<Modulo> catalogo = List.of(modulo("COMPRAS"), modulo("INVENTARIO"), modulo("REPORTES"));
        when(modulos.findAllByOrderByCodigoAsc()).thenReturn(catalogo);
        when(modulos.existsById(anyString())).thenAnswer(i -> Set.of("COMPRAS", "INVENTARIO", "REPORTES").contains(i.getArgument(0)));
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void elDetalleMuestraQuienAdministraLaEmpresaConSoloNombreYCorreo() {
        AdministradorVista admin = mock(AdministradorVista.class);
        when(admin.getNombre()).thenReturn("Laura Gómez");
        when(admin.getCorreo()).thenReturn("laura@eltornillo.co");
        when(usuarios.administradorDe(8L)).thenReturn(List.of(admin));

        var d = service.obtener(8L);

        assertEquals("Laura Gómez", d.administrador().nombre());
        assertEquals("laura@eltornillo.co", d.administrador().correo());
        assertEquals("Ferretería El Tornillo", d.empresa().razonSocial());
        assertEquals("900456789-1", d.empresa().nit());
        assertEquals("el-tornillo", d.empresa().codigoPublico());
    }

    @Test
    void sinAdministradorElDetalleNoFalla() {
        when(usuarios.administradorDe(8L)).thenReturn(List.of());

        assertEquals(null, service.obtener(8L).administrador());
    }

    @Test
    void elCodigoSeGuardaComoSeEscribioSinEspaciosDeMas() {
        assertEquals("Ferretería DON Martín 2", EmpresaAltaService.normalizarCodigo("  Ferretería   DON\tMartín 2 "));
        assertEquals("demo", EmpresaAltaService.normalizarCodigo("demo"));
    }

    @Test
    void listaLasEmpresasConSuPlanYEstadoSinTocarDatosDeNegocio() {
        when(empresas.findAll()).thenReturn(List.of(empresa));
        when(suscripciones.findAllByOrderByEmpresaIdDesc()).thenReturn(List.of(suscripcion));

        var lista = service.listar();

        assertEquals(1, lista.size());
        assertEquals("BASICO", lista.get(0).plan());
        assertEquals(EstadoSuscripcion.ACTIVA, lista.get(0).estado());
        assertEquals("el-tornillo", lista.get(0).codigoPublico());
    }

    @Test
    void elDetalleMuestraQueModulosVienenDelPlanYCualesTienenExcepcion() {
        when(excepciones.findByEmpresaId(8L)).thenReturn(List.of(excepcion("REPORTES", true), excepcion("COMPRAS", false)));

        var d = service.obtener(8L);

        var porCodigo = d.modulos().stream().collect(java.util.stream.Collectors.toMap(m -> m.codigo(), m -> m));
        assertTrue(porCodigo.get("INVENTARIO").activo());
        assertFalse(porCodigo.get("COMPRAS").activo());     // el plan lo trae pero se lo quitaron
        assertTrue(porCodigo.get("COMPRAS").enPlan());
        assertTrue(porCodigo.get("REPORTES").activo());     // el plan no lo trae pero se lo dieron
        assertFalse(porCodigo.get("REPORTES").enPlan());
        assertEquals(null, porCodigo.get("INVENTARIO").excepcion());
        assertThrows(NoEncontradoException.class, () -> service.obtener(99L));
    }

    @Test
    void altaCreaLaEmpresaSiembraAlAdministradorYAudita() {
        Empresa nueva = new Empresa();
        nueva.setId(9L);
        nueva.setRazonSocial("Ferrecentro");
        nueva.setNit("901223114-5");
        nueva.setCodigoPublico("ferrecentro");
        Suscripcion s = new Suscripcion();
        s.setEmpresaId(9L);
        s.setPlan(pro);
        s.setEstado(EstadoSuscripcion.PRUEBA);
        when(alta.crearEmpresa(any(), eq(pro), eq(EstadoSuscripcion.PRUEBA))).thenReturn(nueva);
        when(empresas.findById(9L)).thenReturn(Optional.of(nueva));
        when(suscripciones.findByEmpresaId(9L)).thenReturn(Optional.of(s));
        when(excepciones.findByEmpresaId(9L)).thenReturn(List.of());

        var r = service.crear(solicitud("901223114-5", "ferrecentro", "PROFESIONAL", null));

        assertEquals(9L, r.empresa().id());
        assertEquals("PROFESIONAL", r.empresa().plan());
        verify(alta).sembrarAdministrador("Laura Gómez", "Laura@Ferrecentro.co", "Clave12345"); // la normalización del correo la hace el alta
        verify(auditoria).registrar(eq(3L), eq(AccionPlataforma.EMPRESA_CREAR), eq(9L), eq(true), anyString());
    }

    @Test
    void elAltaSeRechazaConPlanInvalidoEstadoInvalidoNitOCodigoRepetidos() {
        assertEquals("PLAN_INVALIDO", codigo(() -> service.crear(solicitud("901000111", "nueva", "NOEXISTE", null))));
        basico.setActivo(false);
        assertEquals("PLAN_INVALIDO", codigo(() -> service.crear(solicitud("901000111", "nueva", "BASICO", null))));
        basico.setActivo(true);
        assertEquals("ESTADO_INICIAL_INVALIDO", codigo(() -> service.crear(
                solicitud("901000111", "nueva", "BASICO", EstadoSuscripcion.SUSPENDIDA))));
        when(empresas.existsByNitIgnoreCase("901000111")).thenReturn(true);
        assertEquals("NIT_DUPLICADO", codigo(() -> service.crear(solicitud("901000111", "nueva", "BASICO", null))));
        when(empresas.existsByNitIgnoreCase("901000111")).thenReturn(false);
        when(empresas.idPorCodigoPublico("nueva")).thenReturn(Optional.of(5L));
        assertEquals("CODIGO_DUPLICADO", codigo(() -> service.crear(solicitud("901000111", "nueva", "BASICO", null))));
        verify(alta, never()).crearEmpresa(any(), any(), any());
    }

    @Test
    void siFallaElAdministradorSeDeshaceLaEmpresaYQuedaElFalloAuditado() {
        Empresa nueva = new Empresa();
        nueva.setId(9L);
        when(alta.crearEmpresa(any(), eq(basico), any())).thenReturn(nueva);
        doThrow(new IllegalStateException("falta el rol")).when(alta).sembrarAdministrador(anyString(), anyString(), anyString());

        assertThrows(IllegalStateException.class, () -> service.crear(solicitud("901000111", "nueva", "BASICO", null)));

        verify(alta).deshacer(9L);
        verify(auditoria).registrar(eq(3L), eq(AccionPlataforma.EMPRESA_CREAR), isNull(), eq(false), anyString());
    }

    @Test
    void cambiarDePlanLimpiaLaMemoriaYAudita() {
        var r = service.cambiarPlan(8L, "PROFESIONAL");

        assertEquals("PROFESIONAL", r.empresa().plan());
        assertTrue(r.modulos().stream().filter(m -> m.codigo().equals("REPORTES")).findFirst().orElseThrow().activo());
        verify(acceso).invalidar(8L);
        verify(auditoria).registrar(eq(3L), eq(AccionPlataforma.EMPRESA_CAMBIAR_PLAN), eq(8L), eq(true), anyString());
        assertEquals("PLAN_INVALIDO", codigo(() -> service.cambiarPlan(8L, "NOEXISTE")));
    }

    @Test
    void laExcepcionDeModuloSeCreaSeCambiaYSeBorra() {
        service.excepcionDeModulo(8L, "reportes", true);
        verify(excepciones).save(any(EmpresaModulo.class));

        EmpresaModulo existente = excepcion("REPORTES", true);
        when(excepciones.findByEmpresaIdAndModuloCodigo(8L, "REPORTES")).thenReturn(Optional.of(existente));
        service.excepcionDeModulo(8L, "REPORTES", false);
        assertFalse(existente.isHabilitado());

        service.excepcionDeModulo(8L, "REPORTES", null);
        verify(excepciones).delete(existente);
        verify(acceso, org.mockito.Mockito.times(3)).invalidar(8L);
        assertEquals("MODULO_INEXISTENTE", codigo(() -> service.excepcionDeModulo(8L, "NOEXISTE", true)));
    }

    @Test
    void suspenderExigeMotivoRevocaLasSesionesYLimpiaLaMemoria() {
        assertThrows(ParametroInvalidoException.class, () -> service.cambiarEstado(8L, EstadoSuscripcion.SUSPENDIDA, "  "));

        var r = service.cambiarEstado(8L, EstadoSuscripcion.SUSPENDIDA, "No pagó el plan");

        assertEquals(EstadoSuscripcion.SUSPENDIDA, r.empresa().estado());
        assertEquals("No pagó el plan", suscripcion.getMotivoEstado());
        verify(refresh).revocarDeEmpresa(eq(8L), any());
        verify(acceso).invalidar(8L);
        verify(auditoria).registrar(eq(3L), eq(AccionPlataforma.EMPRESA_ESTADO), eq(8L), eq(true), anyString());
    }

    @Test
    void marcarMorosaNoCortaLasSesionesYReactivarNoPideMotivo() {
        service.cambiarEstado(8L, EstadoSuscripcion.MOROSA, "Debe 2 meses");
        verify(refresh, never()).revocarDeEmpresa(any(), any());

        var r = service.cambiarEstado(8L, EstadoSuscripcion.ACTIVA, null);
        assertEquals(EstadoSuscripcion.ACTIVA, r.empresa().estado());
        assertEquals(null, suscripcion.getMotivoEstado());
    }

    @Test
    void unaCancelacionEsFinalYNoSeVuelveAPrueba() {
        service.cambiarEstado(8L, EstadoSuscripcion.CANCELADA, "Cerró el negocio");

        assertEquals("TRANSICION_INVALIDA", codigo(() -> service.cambiarEstado(8L, EstadoSuscripcion.ACTIVA, null)));
        suscripcion.setEstado(EstadoSuscripcion.ACTIVA);
        assertEquals("TRANSICION_INVALIDA", codigo(() -> service.cambiarEstado(8L, EstadoSuscripcion.PRUEBA, null)));
    }

    // ----------------------------------------------------------------- apoyo

    private String codigo(Runnable accion) {
        return assertThrows(NegocioException.class, accion::run).getCodigo();
    }

    private static EmpresaCrearRequest solicitud(String nit, String codigo, String plan, EstadoSuscripcion estado) {
        return new EmpresaCrearRequest("Ferrecentro S.A.S.", nit, codigo, plan, estado, "Laura Gómez",
                "Laura@Ferrecentro.co", "Clave12345");
    }

    private static Plan plan(Long id, String codigo, String... modulos) {
        Plan p = new Plan();
        p.setId(id);
        p.setCodigo(codigo);
        p.setNombre(codigo);
        p.getModulos().addAll(Set.of(modulos));
        return p;
    }

    private static Modulo modulo(String codigo) {
        Modulo m = mock(Modulo.class);
        when(m.getCodigo()).thenReturn(codigo);
        when(m.getNombre()).thenReturn(codigo);
        return m;
    }

    private static EmpresaModulo excepcion(String modulo, boolean habilitado) {
        EmpresaModulo e = new EmpresaModulo();
        e.setEmpresaId(8L);
        e.setModuloCodigo(modulo);
        e.setHabilitado(habilitado);
        return e;
    }
}
