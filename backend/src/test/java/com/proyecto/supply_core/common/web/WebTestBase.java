package com.proyecto.supply_core.common.web;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.proyecto.supply_core.catalogo.repository.CategoriaProductoRepository;
import com.proyecto.supply_core.catalogo.repository.UnidadMedidaRepository;
import com.proyecto.supply_core.empresa.repository.CentroCostoRepository;
import com.proyecto.supply_core.empresa.repository.SedeRepository;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.proveedor.repository.ProveedorRepository;
import com.proyecto.supply_core.security.config.SecurityBeansConfig;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.security.interceptor.RolInterceptor;
import com.proyecto.supply_core.security.jwt.JwtUtil;
import com.proyecto.supply_core.usuario.repository.RolRepository;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

/**
 * Base de las pruebas de controllers ({@code @WebMvcTest}): levanta solo la capa web real
 * (controllers, {@code AuthFilter}, {@code RolInterceptor}, {@code GlobalExceptionHandler},
 * validación) sin base de datos. Los services se simulan en cada prueba con {@code @MockitoBean};
 * aquí se simulan los repositorios que usan los validadores ({@code @CorreoUnico}, {@code @NitUnico}...).
 */
@Import({ JwtUtil.class, SecurityBeansConfig.class, RolInterceptor.class })
public abstract class WebTestBase {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    protected UsuarioRepository usuarioRepository;

    @MockitoBean
    protected RolRepository rolRepository;

    @MockitoBean
    protected ProveedorRepository proveedorRepository;

    @MockitoBean
    protected CategoriaProductoRepository categoriaRepository;

    @MockitoBean
    protected CentroCostoRepository centroCostoRepository;

    @MockitoBean
    protected UnidadMedidaRepository unidadMedidaRepository;

    @MockitoBean
    protected ProductoRepository productoRepository;

    @MockitoBean
    protected SedeRepository sedeRepository;

    /**
     * Header {@code Authorization} con un JWT real de un usuario interno.
     *
     * @param roles roles del usuario
     * @return valor del header ("Bearer ...")
     */
    protected String bearer(String... roles) {
        return "Bearer " + jwtUtil.generar(new UsuarioAutenticado(1L, "usuario@prueba.com", "Usuario Prueba",
                Set.of(roles), null));
    }

    /**
     * Header {@code Authorization} con un JWT real de un usuario del portal proveedor.
     *
     * @param proveedorId proveedor al que pertenece
     * @return valor del header ("Bearer ...")
     */
    protected String bearerProveedor(Long proveedorId) {
        return "Bearer " + jwtUtil.generar(new UsuarioAutenticado(9L, "proveedor@prueba.com", "Proveedor Prueba",
                Set.of("PROVEEDOR"), proveedorId));
    }
}
