package com.proyecto.supply_core.common.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.proyecto.supply_core.catalogo.entity.CategoriaProducto;
import com.proyecto.supply_core.catalogo.repository.CategoriaProductoRepository;
import com.proyecto.supply_core.catalogo.repository.UnidadMedidaRepository;
import com.proyecto.supply_core.common.util.ConsecutivoGenerator;
import com.proyecto.supply_core.empresa.entity.CentroCosto;
import com.proyecto.supply_core.empresa.entity.Sede;
import com.proyecto.supply_core.empresa.repository.CentroCostoRepository;
import com.proyecto.supply_core.empresa.repository.SedeRepository;
import com.proyecto.supply_core.inventario.entity.Bodega;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.BodegaRepository;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.inventario.repository.StockProductoBodegaRepository;
import com.proyecto.supply_core.proveedor.entity.Proveedor;
import com.proyecto.supply_core.proveedor.repository.ProveedorRepository;
import com.proyecto.supply_core.security.context.UsuarioAutenticado;
import com.proyecto.supply_core.security.jwt.JwtUtil;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.RolRepository;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

/**
 * Pruebas de FUGA entre empresas contra MySQL real (base {@code supply_core_test}, que se recrea sola).
 * Crean dos empresas con los MISMOS códigos, NIT y correos y comprueban que cada una solo ve y toca lo suyo,
 * tanto por los repositorios como por la API completa (filtro, JWT, servicios).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AislamientoEmpresasTest.BaseDePruebaConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AislamientoEmpresasTest {

    private static final String CLAVE = "Secreta123";

    /** Recrea siempre la base de pruebas y se niega a tocar cualquier otra. */
    @TestConfiguration
    static class BaseDePruebaConfig {

        @Bean
        FlywayMigrationStrategy recrearBaseDePrueba(@Value("${spring.datasource.url}") String url) {
            return flyway -> {
                if (!url.contains("/supply_core_test")) {
                    throw new IllegalStateException("Estas pruebas solo pueden borrar supply_core_test, no " + url);
                }
                flyway.clean();
                flyway.migrate();
            };
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ConsecutivoGenerator consecutivos;
    @Autowired private ProductoRepository productos;
    @Autowired private CategoriaProductoRepository categorias;
    @Autowired private UnidadMedidaRepository unidades;
    @Autowired private ProveedorRepository proveedores;
    @Autowired private SedeRepository sedes;
    @Autowired private CentroCostoRepository centros;
    @Autowired private BodegaRepository bodegas;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private RolRepository roles;
    @Autowired private StockProductoBodegaRepository stock;
    @Autowired private PlatformTransactionManager transacciones;

    private Long empresaA;
    private Long empresaB;
    private Long productoA;
    private Long productoB;
    private Long usuarioA;
    private Long usuarioB;
    private Long categoriaA;
    private Long categoriaB;
    private Long bodegaA;
    private Long bodegaB;
    private Long proveedorA;
    private Long proveedorB;
    private Long ordenA;
    private Long ordenB;
    private Long lineaOrdenA;
    private Long lineaOrdenB;
    private Long usuarioA2;
    private Long ordenFacturaA;
    private Long lineaFacturaA;
    private Long ordenRevisionA;
    private Long lineaRevisionA;
    private Long ordenFacturaB;
    private Long lineaFacturaB;

    @BeforeAll
    void crearDosEmpresasConLosMismosDatos() {
        empresaA = jdbc.queryForObject("SELECT MIN(id) FROM empresa", Long.class);
        jdbc.update("INSERT INTO empresa (razon_social, nit, moneda_base, codigo_publico) VALUES (?, ?, 'COP', ?)",
                "Empresa B S.A.S.", "900000999-9", "empresa-b");
        empresaB = jdbc.queryForObject("SELECT id FROM empresa WHERE codigo_publico = 'empresa-b'", Long.class);

        Long[] a = sembrar(empresaA, "Cable de la empresa A", "a@x.com");
        Long[] b = sembrar(empresaB, "Cable de la empresa B", "b@x.com");
        productoA = a[0]; usuarioA = a[1]; categoriaA = a[2]; bodegaA = a[3];
        productoB = b[0]; usuarioB = b[1]; categoriaB = b[2]; bodegaB = b[3];
        proveedorA = a[4];
        proveedorB = b[4];
        Long[] oa = sembrarOrden(empresaA, proveedorA, bodegaA, productoA, "OC-FUGA-A");
        Long[] ob = sembrarOrden(empresaB, proveedorB, bodegaB, productoB, "OC-FUGA-B");
        ordenA = oa[0]; lineaOrdenA = oa[1];
        ordenB = ob[0]; lineaOrdenB = ob[1];
        Long[] fa = sembrarOrden(empresaA, proveedorA, bodegaA, productoA, "OC-FUGA-FA");
        Long[] ra = sembrarOrden(empresaA, proveedorA, bodegaA, productoA, "OC-FUGA-RA");
        Long[] fb = sembrarOrden(empresaB, proveedorB, bodegaB, productoB, "OC-FUGA-FB");
        ordenFacturaA = fa[0]; lineaFacturaA = fa[1];
        ordenRevisionA = ra[0]; lineaRevisionA = ra[1];
        ordenFacturaB = fb[0]; lineaFacturaB = fb[1];
        usuarioA2 = EmpresaContexto.como(empresaA, () -> {
            Usuario u = new Usuario();
            u.setNombreCompleto("Segundo usuario de A");
            u.setCorreo("segundo@mismo.com");
            u.setHashPassword(passwordEncoder.encode(CLAVE));
            u.getRoles().add(roles.findByCodigo("ADMIN_COMPRAS").orElseThrow());
            return usuarios.save(u).getId();
        });
    }

    /** Crea en la empresa los mismos códigos, NIT y nombres: lo único que cambia es la empresa y el nombre. */
    private Long[] sembrar(Long empresa, String nombreProducto, String correo) {
        return EmpresaContexto.como(empresa, () -> {
            CategoriaProducto cat = new CategoriaProducto();
            cat.setNombre("Eléctricos");
            cat = categorias.save(cat);

            Producto p = new Producto();
            p.setCodigo("P-001");
            p.setNombre(nombreProducto);
            p.setCategoria(cat);
            p.setUnidadMedida(unidades.findAll().get(0));
            p = productos.save(p);

            Proveedor prov = new Proveedor();
            prov.setRazonSocial("Proveedor común");
            prov.setNit("800123456-1");
            prov = proveedores.save(prov);

            Sede sede = new Sede();
            sede.setNombre("Sede principal");
            sede = sedes.save(sede);

            CentroCosto cc = new CentroCosto();
            cc.setCodigo("CC-1");
            cc.setNombre("Ventas");
            centros.save(cc);

            Bodega bodega = new Bodega();
            bodega.setNombre("Bodega 1");
            bodega.setSede(sede);
            bodega = bodegas.save(bodega);

            Usuario u = new Usuario();
            u.setNombreCompleto("Admin " + correo);
            u.setCorreo("admin@mismo.com"); // el mismo correo en las dos empresas
            u.setHashPassword(passwordEncoder.encode(CLAVE));
            u.getRoles().add(roles.findByCodigo("ADMIN_SISTEMA").orElseThrow());
            u = usuarios.save(u);
            return new Long[] { p.getId(), u.getId(), cat.getId(), bodega.getId(), prov.getId() };
        });
    }

    /**
     * Inserta por SQL una orden DESPACHADA de 10 unidades a $ 100 con una sola línea. Se apaga la revisión de
     * llaves foráneas solo durante el INSERT porque la orden apunta a una adjudicación que estas pruebas no necesitan.
     */
    private Long[] sembrarOrden(Long empresa, Long proveedor, Long bodega, Long producto, String consecutivo) {
        return jdbc.execute((ConnectionCallback<Long[]>) con -> {
            try (Statement st = con.createStatement()) {
                st.execute("SET FOREIGN_KEY_CHECKS=0");
                long ordenId;
                try (PreparedStatement ps = con.prepareStatement("INSERT INTO orden_compra (empresa_id, adjudicacion_id, "
                        + "proveedor_id, bodega_destino_id, consecutivo, version, subtotal, impuestos, total, lugar_entrega, "
                        + "fecha_entrega_esperada, estado) VALUES (?, 1, ?, ?, ?, 1, 1000, 0, 1000, 'Bodega', CURRENT_DATE, "
                        + "'DESPACHADA')", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, empresa);
                    ps.setLong(2, proveedor);
                    ps.setLong(3, bodega);
                    ps.setString(4, consecutivo);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        rs.next();
                        ordenId = rs.getLong(1);
                    }
                }
                long lineaId;
                try (PreparedStatement ps = con.prepareStatement("INSERT INTO orden_compra_linea (orden_compra_id, "
                        + "producto_id, cantidad, precio_unitario, impuesto_pct, subtotal) VALUES (?, ?, 10, 100, 0, 1000)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, ordenId);
                    ps.setLong(2, producto);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        rs.next();
                        lineaId = rs.getLong(1);
                    }
                }
                st.execute("SET FOREIGN_KEY_CHECKS=1");
                return new Long[] { ordenId, lineaId };
            }
        });
    }

    private String tokenProveedor(Long empresa, Long usuarioId, Long proveedorId) {
        return "Bearer " + jwtUtil.generar(new UsuarioAutenticado(usuarioId, "p@x.com", "P", Set.of("PROVEEDOR"),
                proveedorId, empresa));
    }

    private String token(Long empresa, Long usuarioId, String... rolesDelToken) {
        return "Bearer " + jwtUtil.generar(new UsuarioAutenticado(usuarioId, "t@x.com", "T", Set.of(rolesDelToken), null,
                empresa));
    }

    // ------------------------------------------------------------------ repositorios

    @Test
    void cadaEmpresaSoloVeSusProductosYNoPuedeLeerPorIdLosDeLaOtra() {
        List<Producto> deA = EmpresaContexto.como(empresaA, () -> productos.findAll());
        List<Producto> deB = EmpresaContexto.como(empresaB, () -> productos.findAll());

        assertEquals(List.of("Cable de la empresa A"), deA.stream().map(Producto::getNombre).toList());
        assertEquals(List.of("Cable de la empresa B"), deB.stream().map(Producto::getNombre).toList());
        assertTrue(EmpresaContexto.como(empresaA, () -> productos.findById(productoB)).isEmpty());
        assertTrue(EmpresaContexto.como(empresaB, () -> productos.findById(productoA)).isEmpty());
        assertEquals(1L, EmpresaContexto.como(empresaA, () -> productos.count()));
    }

    @Test
    void elMismoCodigoNitYCorreoSePuedenUsarEnDosEmpresasPeroNoDosVecesEnUna() {
        // sembrar() ya los repitió entre empresas sin error; dentro de una empresa sí chocan
        assertThrows(DataIntegrityViolationException.class, () -> EmpresaContexto.como(empresaA, () -> {
            Producto repetido = new Producto();
            repetido.setCodigo("P-001");
            repetido.setNombre("Repetido");
            repetido.setCategoria(categorias.findById(categoriaA).orElseThrow());
            repetido.setUnidadMedida(unidades.findAll().get(0));
            return productos.saveAndFlush(repetido);
        }));
    }

    @Test
    void guardarAsignaLaEmpresaDelContextoSinQueElCodigoLaIndique() {
        Long empresaDelProducto = jdbc.queryForObject("SELECT empresa_id FROM producto WHERE id = ?", Long.class, productoB);
        assertEquals(empresaB, empresaDelProducto);
        assertEquals(empresaA, jdbc.queryForObject("SELECT empresa_id FROM sede WHERE id = (SELECT MIN(id) FROM sede WHERE empresa_id = ?)",
                Long.class, empresaA));
    }

    @Test
    void losUsuariosConElMismoCorreoSonDeSuEmpresa() {
        Usuario deA = EmpresaContexto.como(empresaA, () -> usuarios.findByCorreoIgnoreCase("admin@mismo.com").orElseThrow());
        Usuario deB = EmpresaContexto.como(empresaB, () -> usuarios.findByCorreoIgnoreCase("admin@mismo.com").orElseThrow());

        assertEquals(usuarioA, deA.getId());
        assertEquals(usuarioB, deB.getId());
        assertEquals(1, EmpresaContexto.como(empresaA, () -> usuarios.findByActivoTrueAndRoles_Codigo("ADMIN_SISTEMA")).size());
        assertTrue(EmpresaContexto.como(empresaA, () -> usuarios.findById(usuarioB)).isEmpty());
    }

    @Test
    void cadaEmpresaLlevaSuPropiaNumeracion() {
        String a1 = EmpresaContexto.como(empresaA, () -> consecutivos.siguiente(ConsecutivoGenerator.SOLICITUD));
        String a2 = EmpresaContexto.como(empresaA, () -> consecutivos.siguiente(ConsecutivoGenerator.SOLICITUD));
        String b1 = EmpresaContexto.como(empresaB, () -> consecutivos.siguiente(ConsecutivoGenerator.SOLICITUD));

        assertNotEquals(a1, a2);
        assertEquals("SOL-000001", b1);
    }

    @Test
    void elSqlNativoDelStockEscribeLaEmpresaDelContexto() {
        EmpresaContexto.como(empresaB, () -> new TransactionTemplate(transacciones)
                .executeWithoutResult(estado -> stock.crearSiNoExiste(productoB, bodegaB)));

        assertEquals(empresaB, jdbc.queryForObject(
                "SELECT empresa_id FROM stock_producto_bodega WHERE producto_id = ? AND bodega_id = ?", Long.class,
                productoB, bodegaB));
        assertEquals(0, EmpresaContexto.como(empresaA, () -> stock.buscar(productoB, bodegaB)).size());
    }

    @Test
    void sinEmpresaEnElContextoNoSePuedeAbrirUnaSesion() {
        EmpresaContexto.limpiar();
        assertThrows(RuntimeException.class, () -> productos.count());
    }

    @Test
    void soloElModoRaizVeLasDosEmpresas() {
        long total = EmpresaContexto.sinEmpresa(() -> productos.count());
        assertTrue(total >= 2);
    }

    // ------------------------------------------------------------------ API completa

    @Test
    void laApiListaSoloLosProductosDeLaEmpresaDelToken() throws Exception {
        String interno = "ADMIN_COMPRAS";
        mvc.perform(get("/api/productos").header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, interno)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.contenido[0].nombre").value("Cable de la empresa A"));
        mvc.perform(get("/api/productos").header(HttpHeaders.AUTHORIZATION, token(empresaB, usuarioB, interno)))
                .andExpect(jsonPath("$.contenido[0].nombre").value("Cable de la empresa B"));
    }

    @Test
    void laApiRespondeNoEncontradoAlPedirPorIdUnDatoDeOtraEmpresa() throws Exception {
        mvc.perform(get("/api/productos/" + productoB)
                        .header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "ADMIN_COMPRAS")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/usuarios/" + usuarioB)
                        .header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "ADMIN_SISTEMA")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/usuarios/" + usuarioA)
                        .header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "ADMIN_SISTEMA")))
                .andExpect(status().isOk());
    }

    @Test
    void elAdminDeUnaEmpresaSoloListaSusUsuarios() throws Exception {
        mvc.perform(get("/api/usuarios").header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "ADMIN_SISTEMA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(2))
                .andExpect(jsonPath("$.contenido[?(@.id == " + usuarioB + ")]").isEmpty());
    }

    @Test
    void elLoginSoloAceptaAlUsuarioDeLaEmpresaIndicada() throws Exception {
        String cuerpoA = "{\"empresa\":\"demo\",\"correo\":\"admin@mismo.com\",\"password\":\"" + CLAVE + "\"}";
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(cuerpoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.id").value(usuarioA.intValue()));

        String cuerpoB = "{\"empresa\":\"empresa-b\",\"correo\":\"admin@mismo.com\",\"password\":\"" + CLAVE + "\"}";
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(cuerpoB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.id").value(usuarioB.intValue()));

        String empresaInexistente = "{\"empresa\":\"no-existe\",\"correo\":\"admin@mismo.com\",\"password\":\"" + CLAVE + "\"}";
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(empresaInexistente))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Credenciales inválidas"));
    }

    @Test
    void elTokenDeRefrescoDevuelveSesionDeLaMismaEmpresa() throws Exception {
        String cuerpo = "{\"empresa\":\"empresa-b\",\"correo\":\"admin@mismo.com\",\"password\":\"" + CLAVE + "\"}";
        String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andReturn().getResponse().getContentAsString();
        String refresh = new com.fasterxml.jackson.databind.ObjectMapper().readTree(respuesta).get("refreshToken").asText();

        String renovada = mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String jwt = new com.fasterxml.jackson.databind.ObjectMapper().readTree(renovada).get("token").asText();

        assertEquals(empresaB, jwtUtil.validar(jwt).empresaId());
    }

    @Test
    void elRegistroPublicoDeProveedoresCaeEnLaEmpresaDeLaRutaYNoVeCategoriasAjenas() throws Exception {
        long antesA = EmpresaContexto.como(empresaA, () -> proveedores.count());
        long antesB = EmpresaContexto.como(empresaB, () -> proveedores.count());

        String conCategoriaDeB = "{\"razonSocial\":\"Nuevo SAS\",\"nit\":\"810000111-2\",\"telefono\":\"+57 300 123 4567\","
                + "\"categoriaIds\":[" + categoriaB + "],\"nombreContacto\":\"Laura\",\"correo\":\"reg@b.com\","
                + "\"password\":\"Clave1234\"}";
        mvc.perform(post("/api/portal/registro/empresa-b").contentType(MediaType.APPLICATION_JSON).content(conCategoriaDeB))
                .andExpect(status().isCreated());

        assertEquals(antesB + 1, EmpresaContexto.como(empresaB, () -> proveedores.count()));
        assertEquals(antesA, EmpresaContexto.como(empresaA, () -> proveedores.count()));

        // la categoría de la empresa A no existe para la B
        String conCategoriaDeA = conCategoriaDeB.replace("[" + categoriaB + "]", "[" + categoriaA + "]")
                .replace("810000111-2", "810000222-3").replace("reg@b.com", "otro@b.com");
        mvc.perform(post("/api/portal/registro/empresa-b").contentType(MediaType.APPLICATION_JSON).content(conCategoriaDeA))
                .andExpect(status().isBadRequest());
    }

    @Test
    void laAuditoriaDeUnaEmpresaNoApareceEnLaOtra() throws Exception {
        // los inicios de sesión de las pruebas anteriores se auditaron en su empresa
        String sinFiltro = mvc.perform(get("/api/auditoria?size=100")
                        .header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "AUDITOR")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(sinFiltro.contains("\"usuarioId\":" + usuarioB + ","), "la auditoría de A no debe incluir al usuario de B");
    }

    // ------------------------------------------------------------------ recepción (paso 17)

    private String recibir(Long orden, String token, String lineas) throws Exception {
        return mvc.perform(post("/api/ordenes/" + orden + "/recepciones").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lineas\":[" + lineas + "]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void laRecepcionDeExtremoAExtremoSumaStockEvaluaYQuedaEnSuEmpresa() throws Exception {
        String almacen = token(empresaA, usuarioA, "ALMACENISTA");
        String compras = token(empresaA, usuarioA, "COMPRADOR");
        String cierra = token(empresaA, usuarioA, "ADMIN_COMPRAS");
        java.math.BigDecimal stockInicial = stockDe(productoA, bodegaA);

        // 1) llegan 8, 3 dañadas: entran 5 y faltan 5
        recibir(ordenA, almacen, "{\"ordenLineaId\":" + lineaOrdenA + ",\"cantidadRecibida\":8,\"cantidadRechazada\":3,"
                + "\"motivoRechazo\":\"DANADO\",\"observacionCalidad\":\"Caja mojada\"}");
        assertEquals("RECIBIDA_PARCIAL", jdbc.queryForObject("SELECT estado FROM orden_compra WHERE id = ?", String.class, ordenA));
        assertEquals(0, stockInicial.add(new java.math.BigDecimal("5")).compareTo(stockDe(productoA, bodegaA)));

        // 2) llegan 7 buenas: entran 5 (completa el pedido) y 2 quedan de excedente en revisión
        String segunda = recibir(ordenA, almacen, "{\"ordenLineaId\":" + lineaOrdenA + ",\"cantidadRecibida\":7}");
        assertEquals("RECIBIDA_TOTAL", jdbc.queryForObject("SELECT estado FROM orden_compra WHERE id = ?", String.class, ordenA));
        Long lineaExcedente = new com.fasterxml.jackson.databind.ObjectMapper().readTree(segunda).get("lineas").get(0).get("id").asLong();

        // 3) con excedente sin resolver no se cierra
        mvc.perform(post("/api/ordenes/" + ordenA + "/recepciones/cerrar").header(HttpHeaders.AUTHORIZATION, cierra)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EXCEDENTE_SIN_RESOLVER"));

        // 4) compras devuelve el excedente: el stock no cambia
        mvc.perform(post("/api/recepciones/lineas/" + lineaExcedente + "/resolver-exceso")
                        .header(HttpHeaders.AUTHORIZATION, compras).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"DEVOLVER\",\"motivo\":\"No lo necesitamos\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EXCEDENTE_DEVUELTO"));

        // 5) cierre: la orden queda CERRADA y el proveedor evaluado
        mvc.perform(post("/api/ordenes/" + ordenA + "/recepciones/cerrar").header(HttpHeaders.AUTHORIZATION, cierra)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoOrden").value("CERRADA"));

        assertEquals(0, stockInicial.add(new java.math.BigDecimal("10")).compareTo(stockDe(productoA, bodegaA)));
        assertEquals(0, new java.math.BigDecimal("100").compareTo(jdbc.queryForObject(
                "SELECT costo_referencia FROM producto WHERE id = ?", java.math.BigDecimal.class, productoA)));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM movimiento_inventario WHERE tipo = 'ENTRADA_COMPRA' "
                + "AND referencia_tipo = 'RECEPCION' AND empresa_id = ? AND referencia_id IN "
                + "(SELECT id FROM recepcion WHERE orden_compra_id = ?)", Integer.class, empresaA, ordenA));
        assertEquals(empresaA, jdbc.queryForObject("SELECT empresa_id FROM evaluacion_proveedor WHERE orden_compra_id = ?",
                Long.class, ordenA));
        assertTrue(jdbc.queryForObject("SELECT score_actual FROM proveedor WHERE id = ?", java.math.BigDecimal.class,
                proveedorA).signum() > 0);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM recepcion WHERE orden_compra_id = ? AND empresa_id = ?",
                Integer.class, ordenA, empresaA));
    }

    @Test
    void unaEmpresaNoPuedeRecibirConsultarNiCerrarLasOrdenesDeOtra() throws Exception {
        String almacenA = token(empresaA, usuarioA, "ALMACENISTA");
        String comprasA = token(empresaA, usuarioA, "COMPRADOR", "ADMIN_COMPRAS");

        // la empresa B recibe 12 de 10: queda un excedente que solo B puede resolver
        String respuesta = recibir(ordenB, token(empresaB, usuarioB, "ALMACENISTA"),
                "{\"ordenLineaId\":" + lineaOrdenB + ",\"cantidadRecibida\":12}");
        com.fasterxml.jackson.databind.JsonNode recepcionB = new com.fasterxml.jackson.databind.ObjectMapper().readTree(respuesta);
        long recepcionBId = recepcionB.get("id").asLong();
        long lineaBId = recepcionB.get("lineas").get(0).get("id").asLong();

        // la empresa A no ve nada de eso
        mvc.perform(post("/api/ordenes/" + ordenB + "/recepciones").header(HttpHeaders.AUTHORIZATION, almacenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lineas\":[{\"ordenLineaId\":" + lineaOrdenB + ",\"cantidadRecibida\":1}]}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/ordenes/" + ordenB + "/recepciones").header(HttpHeaders.AUTHORIZATION, comprasA))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/recepciones/" + recepcionBId).header(HttpHeaders.AUTHORIZATION, comprasA))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/ordenes/" + ordenB + "/recepciones/cerrar").header(HttpHeaders.AUTHORIZATION, comprasA)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/recepciones/lineas/" + lineaBId + "/resolver-exceso")
                        .header(HttpHeaders.AUTHORIZATION, comprasA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"ACEPTAR\",\"motivo\":\"Intento de otra empresa\"}"))
                .andExpect(status().isNotFound());

        // el excedente de B sigue intacto y B sí puede resolverlo
        assertEquals("EN_REVISION", jdbc.queryForObject("SELECT estado FROM recepcion_linea WHERE id = ?", String.class, lineaBId));
        mvc.perform(get("/api/recepciones/" + recepcionBId)
                        .header(HttpHeaders.AUTHORIZATION, token(empresaB, usuarioB, "COMPRADOR")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/recepciones/lineas/" + lineaBId + "/resolver-exceso")
                        .header(HttpHeaders.AUTHORIZATION, token(empresaB, usuarioB, "COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"ACEPTAR\",\"motivo\":\"Nos sirve para la obra\"}"))
                .andExpect(status().isOk());

        // todo lo de la orden B quedó en la empresa B
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM recepcion WHERE orden_compra_id = ? AND empresa_id <> ?",
                Integer.class, ordenB, empresaB));
    }

    @Test
    void elProveedorSoloVeLasRecepcionesDeSuEmpresaYDeSusOrdenes() throws Exception {
        // un proveedor de la empresa A pidiendo una orden de la B no la encuentra
        mvc.perform(get("/api/portal/ordenes/" + ordenB + "/recepciones")
                        .header(HttpHeaders.AUTHORIZATION, tokenProveedor(empresaA, usuarioA, proveedorA)))
                .andExpect(status().isNotFound());
        // y el proveedor de la B sí ve las suyas
        mvc.perform(get("/api/portal/ordenes/" + ordenB + "/recepciones")
                        .header(HttpHeaders.AUTHORIZATION, tokenProveedor(empresaB, usuarioB, proveedorB)))
                .andExpect(status().isOk());
        // un proveedor de otra empresa con el mismo id de proveedor no ve la orden ajena
        mvc.perform(get("/api/portal/ordenes/" + ordenB + "/recepciones")
                        .header(HttpHeaders.AUTHORIZATION, tokenProveedor(empresaB, usuarioB, proveedorA)))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ factura y pago final (paso 17-bis)

    private java.math.BigDecimal stockDe(Long producto, Long bodega) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(cantidad), 0) FROM stock_producto_bodega WHERE producto_id = ? "
                + "AND bodega_id = ?", java.math.BigDecimal.class, producto, bodega);
    }

    private String hoy() {
        return java.time.LocalDate.now().toString();
    }

    private String pagar(Long orden, String token, String tipo, String valor) throws Exception {
        return mvc.perform(post("/api/ordenes/" + orden + "/pagos").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metodo\":\"TRANSFERENCIA\",\"tipo\":\"" + tipo + "\",\"valor\":" + valor
                                + ",\"fechaPago\":\"" + hoy() + "\",\"referencia\":\"T-1\"}"))
                .andReturn().getResponse().getContentAsString();
    }

    private org.springframework.test.web.servlet.ResultActions facturar(Long orden, String token, String numero, String total)
            throws Exception {
        return mvc.perform(post("/api/ordenes/" + orden + "/facturas").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":\"" + numero + "\",\"fechaFactura\":\"" + hoy() + "\",\"subtotal\":" + total
                        + ",\"impuestos\":0,\"total\":" + total + "}"));
    }

    @Test
    void elAnticipoTieneTopeYElPagoFinalSeHaceContraFacturaQueCuadra() throws Exception {
        String almacen = token(empresaA, usuarioA, "ALMACENISTA");
        String administrativo = token(empresaA, usuarioA, "ADMINISTRATIVO");
        recibir(ordenFacturaA, almacen, "{\"ordenLineaId\":" + lineaFacturaA + ",\"cantidadRecibida\":10}");

        // anticipo: tope del 50 % de $ 1.000
        mvc.perform(post("/api/ordenes/" + ordenFacturaA + "/pagos").header(HttpHeaders.AUTHORIZATION, administrativo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metodo\":\"TRANSFERENCIA\",\"tipo\":\"ANTICIPO\",\"valor\":500.01,\"fechaPago\":\""
                                + hoy() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ANTICIPO_EXCEDE_TOPE"));
        assertTrue(pagar(ordenFacturaA, administrativo, "ANTICIPO", "500.00").contains("\"tipo\":\"ANTICIPO\""));

        // el pago final exige factura
        mvc.perform(post("/api/ordenes/" + ordenFacturaA + "/pagos").header(HttpHeaders.AUTHORIZATION, administrativo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metodo\":\"TRANSFERENCIA\",\"tipo\":\"PAGO_FINAL\",\"valor\":100,\"fechaPago\":\"" + hoy() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("FACTURA_REQUERIDA"));

        // la factura cuadra con lo recibido (10 x $ 100) y no se puede registrar otra vigente
        facturar(ordenFacturaA, administrativo, "FE-OK-1", "1000.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("CUADRADA"))
                .andExpect(jsonPath("$.esperadoTotal").value(1000.0));
        facturar(ordenFacturaA, administrativo, "FE-OK-2", "1000.00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("FACTURA_EXISTENTE"));

        // pago final: descuenta el anticipo (saldo 500)
        mvc.perform(post("/api/ordenes/" + ordenFacturaA + "/pagos").header(HttpHeaders.AUTHORIZATION, administrativo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metodo\":\"TRANSFERENCIA\",\"tipo\":\"PAGO_FINAL\",\"valor\":500.01,\"fechaPago\":\"" + hoy() + "\"}"))
                .andExpect(status().isConflict());
        assertTrue(pagar(ordenFacturaA, administrativo, "PAGO_FINAL", "500.00").contains("\"tipo\":\"PAGO_FINAL\""));

        // con un pago final registrado la factura ya no se anula
        Long facturaId = jdbc.queryForObject("SELECT id FROM factura_proveedor WHERE orden_compra_id = ?", Long.class, ordenFacturaA);
        mvc.perform(post("/api/facturas/" + facturaId + "/anular").header(HttpHeaders.AUTHORIZATION, administrativo)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Intento de anular\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("FACTURA_CON_PAGO"));

        assertEquals(empresaA, jdbc.queryForObject("SELECT empresa_id FROM factura_proveedor WHERE id = ?", Long.class, facturaId));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM pago WHERE orden_compra_id = ? AND empresa_id = ?",
                Integer.class, ordenFacturaA, empresaA));
        assertEquals("PAGO_FINAL", jdbc.queryForObject("SELECT tipo FROM pago WHERE orden_compra_id = ? ORDER BY id DESC LIMIT 1",
                String.class, ordenFacturaA));
    }

    @Test
    void unaFacturaQueNoCuadraEsperaALaAprobacionDeComprasYNoSeAutoaprueba() throws Exception {
        String almacen = token(empresaA, usuarioA, "ALMACENISTA");
        String administrativo = token(empresaA, usuarioA, "ADMINISTRATIVO");
        String compras = token(empresaA, usuarioA2, "ADMIN_COMPRAS");
        recibir(ordenRevisionA, almacen, "{\"ordenLineaId\":" + lineaRevisionA + ",\"cantidadRecibida\":10}");

        // facturó $ 1.300 y lo recibido vale $ 1.000: queda en revisión
        facturar(ordenRevisionA, administrativo, "FE-REV-1", "1300.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("EN_REVISION"))
                .andExpect(jsonPath("$.diferencia").value(300.0));
        Long facturaId = jdbc.queryForObject("SELECT id FROM factura_proveedor WHERE orden_compra_id = ?", Long.class, ordenRevisionA);

        mvc.perform(post("/api/ordenes/" + ordenRevisionA + "/pagos").header(HttpHeaders.AUTHORIZATION, administrativo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metodo\":\"TRANSFERENCIA\",\"tipo\":\"PAGO_FINAL\",\"valor\":100,\"fechaPago\":\"" + hoy() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("FACTURA_EN_REVISION"));

        // quien la registró no la aprueba; otra persona de compras sí
        mvc.perform(post("/api/facturas/" + facturaId + "/aprobar")
                        .header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "ADMIN_COMPRAS"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Me autorizo yo mismo\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/facturas/" + facturaId + "/aprobar").header(HttpHeaders.AUTHORIZATION, compras)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"El flete estaba pactado por correo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA"));

        // aprobada, ya se puede pagar
        assertTrue(pagar(ordenRevisionA, administrativo, "PAGO_FINAL", "1000.00").contains("\"tipo\":\"PAGO_FINAL\""));
    }

    @Test
    void unaEmpresaNoPuedeFacturarPagarNiAprobarLoDeOtra() throws Exception {
        String administrativoB = token(empresaB, usuarioB, "ADMINISTRATIVO");
        recibir(ordenFacturaB, token(empresaB, usuarioB, "ALMACENISTA"),
                "{\"ordenLineaId\":" + lineaFacturaB + ",\"cantidadRecibida\":10}");
        facturar(ordenFacturaB, administrativoB, "FE-B-1", "1300.00").andExpect(status().isCreated());
        Long facturaB = jdbc.queryForObject("SELECT id FROM factura_proveedor WHERE orden_compra_id = ?", Long.class, ordenFacturaB);

        String administrativoA = token(empresaA, usuarioA, "ADMINISTRATIVO");
        String comprasA = token(empresaA, usuarioA2, "ADMIN_COMPRAS");
        facturar(ordenFacturaB, administrativoA, "FE-A-X", "1000.00").andExpect(status().isNotFound());
        mvc.perform(get("/api/ordenes/" + ordenFacturaB + "/facturas").header(HttpHeaders.AUTHORIZATION, administrativoA))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/facturas/" + facturaB).header(HttpHeaders.AUTHORIZATION, administrativoA))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/facturas/" + facturaB + "/aprobar").header(HttpHeaders.AUTHORIZATION, comprasA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Intento de otra empresa\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/facturas/" + facturaB + "/anular").header(HttpHeaders.AUTHORIZATION, administrativoA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Intento de otra empresa\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/facturas/" + facturaB + "/recruzar").header(HttpHeaders.AUTHORIZATION, administrativoA))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/ordenes/" + ordenFacturaB + "/pagos").header(HttpHeaders.AUTHORIZATION, administrativoA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metodo\":\"TRANSFERENCIA\",\"tipo\":\"PAGO_FINAL\",\"valor\":100,\"fechaPago\":\"" + hoy() + "\"}"))
                .andExpect(status().isNotFound());

        // el listado de A no incluye la factura de B, y la factura de B sigue intacta
        String listadoA = mvc.perform(get("/api/facturas?size=100").header(HttpHeaders.AUTHORIZATION, administrativoA))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(listadoA.contains("FE-B-1"), "el listado de A no debe incluir facturas de B");
        assertEquals("EN_REVISION", jdbc.queryForObject("SELECT estado FROM factura_proveedor WHERE id = ?", String.class, facturaB));

        // el proveedor de A no ve las facturas de una orden de B
        mvc.perform(get("/api/portal/ordenes/" + ordenFacturaB + "/facturas")
                        .header(HttpHeaders.AUTHORIZATION, tokenProveedor(empresaA, usuarioA, proveedorA)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/portal/ordenes/" + ordenFacturaB + "/facturas")
                        .header(HttpHeaders.AUTHORIZATION, tokenProveedor(empresaB, usuarioB, proveedorB)))
                .andExpect(status().isOk());
    }

    @Test
    void laConfiguracionDeAnticipoYToleranciaEsDeCadaEmpresa() throws Exception {
        String adminA = token(empresaA, usuarioA, "ADMIN_SISTEMA");
        String nitA = jdbc.queryForObject("SELECT nit FROM empresa WHERE id = ?", String.class, empresaA);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/empresa")
                        .header(HttpHeaders.AUTHORIZATION, adminA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"razonSocial\":\"Empresa Demo S.A.S.\",\"nit\":\"" + nitA + "\",\"monedaBase\":\"COP\","
                                + "\"topeAnticipoPct\":30,\"toleranciaFacturaPct\":2.5,\"toleranciaFacturaMonto\":1500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topeAnticipoPct").value(30.0))
                .andExpect(jsonPath("$.toleranciaFacturaPct").value(2.5));

        assertEquals(0, new java.math.BigDecimal("30").compareTo(jdbc.queryForObject(
                "SELECT tope_anticipo_pct FROM empresa WHERE id = ?", java.math.BigDecimal.class, empresaA)));
        assertEquals(0, new java.math.BigDecimal("50").compareTo(jdbc.queryForObject(
                "SELECT tope_anticipo_pct FROM empresa WHERE id = ?", java.math.BigDecimal.class, empresaB)));
        assertEquals(0, new java.math.BigDecimal("1").compareTo(jdbc.queryForObject(
                "SELECT tolerancia_factura_pct FROM empresa WHERE id = ?", java.math.BigDecimal.class, empresaB)));

        // devolver la configuración de A para no afectar otras pruebas
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/empresa")
                        .header(HttpHeaders.AUTHORIZATION, adminA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"razonSocial\":\"Empresa Demo S.A.S.\",\"nit\":\"" + nitA + "\",\"monedaBase\":\"COP\","
                                + "\"topeAnticipoPct\":50,\"toleranciaFacturaPct\":1,\"toleranciaFacturaMonto\":0}"))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ reportes

    @Test
    void elGastoSumaSoloLasOrdenesDeLaEmpresaDelToken() throws Exception {
        // A tiene 3 órdenes de $ 1.000 y B tiene 2: la base agrupa y suma, y cada una ve solo lo suyo
        mvc.perform(get("/api/reportes/gasto").header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ordenes").value(3))
                .andExpect(jsonPath("$.subtotal").value(3000.0))
                .andExpect(jsonPath("$.porProveedor.length()").value(1))
                .andExpect(jsonPath("$.porProveedor[0].id").value(proveedorA))
                .andExpect(jsonPath("$.porCategoria[0].id").value(categoriaA))
                .andExpect(jsonPath("$.porMes.length()").value(1));
        mvc.perform(get("/api/reportes/gasto").header(HttpHeaders.AUTHORIZATION, token(empresaB, usuarioB, "AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ordenes").value(2))
                .andExpect(jsonPath("$.subtotal").value(2000.0))
                .andExpect(jsonPath("$.porProveedor[0].id").value(proveedorB));
    }

    @Test
    void filtrarElGastoPorProveedorOCategoriaAjenosNoDevuelveNadaNiError() throws Exception {
        String auditorA = token(empresaA, usuarioA, "AUDITOR");

        mvc.perform(get("/api/reportes/gasto?categoriaId=" + categoriaB).header(HttpHeaders.AUTHORIZATION, auditorA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ordenes").value(0))
                .andExpect(jsonPath("$.porProveedor").isEmpty());
        mvc.perform(get("/api/reportes/gasto?proveedorId=" + proveedorB).header(HttpHeaders.AUTHORIZATION, auditorA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ordenes").value(0))
                .andExpect(jsonPath("$.total").value(0.0));
        mvc.perform(get("/api/reportes/gasto?desde=2000-01-01&hasta=2000-01-31").header(HttpHeaders.AUTHORIZATION, auditorA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ordenes").value(0));
    }

    @Test
    void elInventarioYElRankingSoloMuestranLoDeLaEmpresa() throws Exception {
        mvc.perform(get("/api/reportes/inventario").header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "ALMACENISTA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].nombre").value("Cable de la empresa A"))
                .andExpect(jsonPath("$.items[0].productoId").value(productoA));
        mvc.perform(get("/api/reportes/inventario").header(HttpHeaders.AUTHORIZATION, token(empresaB, usuarioB, "ALMACENISTA")))
                .andExpect(jsonPath("$.items[0].nombre").value("Cable de la empresa B"))
                .andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/reportes/inventario?categoriaId=" + categoriaB)
                        .header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "ALMACENISTA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        mvc.perform(get("/api/reportes/proveedores").header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].proveedorId").value(proveedorA))
                .andExpect(jsonPath("$[0].ordenes").value(3));
    }

    @Test
    void elEstadoDeCuentaDeUnProveedorDeOtraEmpresaEsNoEncontrado() throws Exception {
        mvc.perform(get("/api/reportes/proveedores/" + proveedorB + "/estado-cuenta")
                        .header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "AUDITOR")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/reportes/proveedores/" + proveedorA + "/estado-cuenta")
                        .header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.razonSocial").value("Proveedor común"))
                .andExpect(jsonPath("$.ordenes.length()").value(3))
                .andExpect(jsonPath("$.totalOrdenado").value(3000.0));
        mvc.perform(get("/api/reportes/proveedores/" + proveedorB + "/estado-cuenta")
                        .header(HttpHeaders.AUTHORIZATION, token(empresaB, usuarioB, "AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ordenes.length()").value(2))
                .andExpect(jsonPath("$.totalOrdenado").value(2000.0));
    }
}
