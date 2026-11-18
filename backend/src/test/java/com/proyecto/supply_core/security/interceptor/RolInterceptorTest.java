package com.proyecto.supply_core.security.interceptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.lang.reflect.Method;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;

import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.plataforma.service.AccesoEmpresaService;
import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;

class RolInterceptorTest {

    /** Controller de prueba: un módulo a nivel de clase y otro distinto en un método. */
    @RequiereModulo(Modulos.COMPRAS)
    static class Demo {
        @RequiereRol(Roles.COMPRADOR)
        public void soloComprador() {
        }

        @RequiereModulo(Modulos.IA)
        public void conIa() {
        }

        public void libre() {
        }
    }

    private final AccesoEmpresaService acceso = mock(AccesoEmpresaService.class);
    private final RolInterceptor interceptor = new RolInterceptor(acceso);
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/solicitudes");
        sesion("COMPRADOR");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void revisaPrimeroLaSuscripcionLuegoElModuloYAlFinalElRol() throws Exception {
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), metodo("soloComprador")));

        InOrder orden = inOrder(acceso);
        orden.verify(acceso).exigirSuscripcion(1L, "POST");
        orden.verify(acceso).exigirModulo(1L, Modulos.COMPRAS);
    }

    @Test
    void laAnotacionDelMetodoReemplazaALaDeLaClase() throws Exception {
        interceptor.preHandle(request, new MockHttpServletResponse(), metodo("conIa"));

        verify(acceso).exigirModulo(1L, Modulos.IA);
        verify(acceso, never()).exigirModulo(1L, Modulos.COMPRAS);
    }

    @Test
    void conElModuloApagadoNoSeLlegaARevisarElRol() throws Exception {
        sesion("ALMACENISTA"); // no tendría el rol, pero el módulo falla antes
        doThrow(new AccesoDenegadoException("MODULO_NO_CONTRATADO", "sin módulo")).when(acceso)
                .exigirModulo(anyLong(), anyString());

        var error = assertThrows(AccesoDenegadoException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), metodo("soloComprador")));

        assertEquals("MODULO_NO_CONTRATADO", error.getCodigo());
    }

    @Test
    void conElModuloActivoSigueExigiendoElRol() throws Exception {
        sesion("ALMACENISTA");

        var error = assertThrows(AccesoDenegadoException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), metodo("soloComprador")));

        assertEquals("ACCESO_DENEGADO", error.getCodigo());
    }

    @Test
    void unaEmpresaSuspendidaPuedeCerrarSuSesion() throws Exception {
        request.setRequestURI("/api/auth/logout");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), metodo("libre")));

        verify(acceso, never()).exigirSuscripcion(anyLong(), anyString());
        verify(acceso).exigirModulo(1L, Modulos.COMPRAS); // el módulo del controller sí se sigue pidiendo
    }

    @Test
    void laConsolaDeLaPlataformaNoPasaPorLasReglasDeUnaEmpresa() throws Exception {
        request.setRequestURI("/api/plataforma/empresas");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), metodo("soloComprador")));

        verifyNoInteractions(acceso);
    }

    @Test
    void sinSesionNoHayEmpresaQueRevisar() throws Exception {
        request.removeAttribute(SesionActual.ATRIBUTO);

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), metodo("libre")));

        verify(acceso, never()).exigirSuscripcion(any(), any());
        verify(acceso, never()).exigirModulo(any(), any());
    }

    @Test
    void unaPeticionQueNoEsDeUnControllerPasaDeLargo() {
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        verifyNoInteractions(acceso);
    }

    private void sesion(String rol) {
        request.setAttribute(SesionActual.ATRIBUTO,
                new UsuarioAutenticado(10L, "u@x.com", "Usuario", Set.of(rol), null, 1L));
    }

    private static HandlerMethod metodo(String nombre) throws NoSuchMethodException {
        Method m = Demo.class.getMethod(nombre);
        return new HandlerMethod(new Demo(), m);
    }
}
