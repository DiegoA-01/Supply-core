package com.proyecto.supply_core.security.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.proyecto.supply_core.common.exception.DemasiadasPeticionesException;

class RateLimitFilterTest {

    private final HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
    private final RateLimitFilter filtro = new RateLimitFilter(3, resolver);

    private MockHttpServletRequest post(String ruta, String ip) {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", ruta);
        r.setRemoteAddr(ip);
        return r;
    }

    @Test
    void laPeticionSobreElLimiteSeRechazaConRetryAfter() throws Exception {
        for (int i = 0; i < 3; i++) {
            MockFilterChain cadena = new MockFilterChain();
            filtro.doFilter(post("/api/auth/login", "1.1.1.1"), new MockHttpServletResponse(), cadena);
            assertEquals(true, cadena.getRequest() != null);
        }

        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        MockFilterChain cadena = new MockFilterChain();
        filtro.doFilter(post("/api/auth/login", "1.1.1.1"), respuesta, cadena);

        assertNull(cadena.getRequest());
        assertEquals("60", respuesta.getHeader("Retry-After"));
        verify(resolver).resolveException(any(), any(), isNull(), any(DemasiadasPeticionesException.class));
    }

    @Test
    void cadaIpTieneSuPropioContador() throws Exception {
        for (int i = 0; i < 3; i++) {
            filtro.doFilter(post("/api/auth/refresh", "2.2.2.2"), new MockHttpServletResponse(),
                    new MockFilterChain());
        }

        MockFilterChain otraIp = new MockFilterChain();
        filtro.doFilter(post("/api/auth/refresh", "3.3.3.3"), new MockHttpServletResponse(), otraIp);

        assertEquals(true, otraIp.getRequest() != null);
    }

    @Test
    void lasRutasNoSensiblesYLosGetNoSeLimitan() throws Exception {
        for (int i = 0; i < 10; i++) {
            MockFilterChain cadena = new MockFilterChain();
            filtro.doFilter(post("/api/productos", "4.4.4.4"), new MockHttpServletResponse(), cadena);
            MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/auth/login");
            get.setRemoteAddr("4.4.4.4");
            MockFilterChain cadenaGet = new MockFilterChain();
            filtro.doFilter(get, new MockHttpServletResponse(), cadenaGet);
            assertEquals(true, cadena.getRequest() != null && cadenaGet.getRequest() != null);
        }
    }
}
