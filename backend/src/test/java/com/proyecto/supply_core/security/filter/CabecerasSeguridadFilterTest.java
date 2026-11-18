package com.proyecto.supply_core.security.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CabecerasSeguridadFilterTest {

    private final CabecerasSeguridadFilter filtro = new CabecerasSeguridadFilter();

    private MockHttpServletResponse responder(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filtro.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void laApiLlevaTodasLasCabecerasYNoSeGuardaEnCache() throws Exception {
        MockHttpServletResponse r = responder(new MockHttpServletRequest("GET", "/api/productos"));

        assertEquals("nosniff", r.getHeader("X-Content-Type-Options"));
        assertEquals("DENY", r.getHeader("X-Frame-Options"));
        assertEquals("no-referrer", r.getHeader("Referrer-Policy"));
        assertEquals("same-origin", r.getHeader("Cross-Origin-Resource-Policy"));
        assertEquals("geolocation=(), microphone=(), camera=()", r.getHeader("Permissions-Policy"));
        assertEquals(CabecerasSeguridadFilter.POLITICA_CONTENIDO, r.getHeader("Content-Security-Policy"));
        assertEquals("no-store", r.getHeader("Cache-Control"));
    }

    @Test
    void laDocumentacionNoRecibeLaPoliticaDeContenidoQueLeRompeLosScripts() throws Exception {
        MockHttpServletResponse r = responder(new MockHttpServletRequest("GET", "/swagger-ui/index.html"));

        assertEquals("nosniff", r.getHeader("X-Content-Type-Options"));
        assertNull(r.getHeader("Content-Security-Policy"));
        assertNull(r.getHeader("Cache-Control"));
    }

    @Test
    void elTransporteSeguroSoloSeExigeCuandoLaPeticionLlegoPorHttps() throws Exception {
        MockHttpServletRequest http = new MockHttpServletRequest("GET", "/api/productos");
        MockHttpServletRequest https = new MockHttpServletRequest("GET", "/api/productos");
        https.setSecure(true);

        assertNull(responder(http).getHeader("Strict-Transport-Security"));
        assertEquals(CabecerasSeguridadFilter.TRANSPORTE_SEGURO, responder(https).getHeader("Strict-Transport-Security"));
    }

    @Test
    void elContextPathNoEngañaALaRutaDeLaApi() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/supply/api/productos");
        request.setContextPath("/supply");

        assertEquals("no-store", responder(request).getHeader("Cache-Control"));
    }
}
