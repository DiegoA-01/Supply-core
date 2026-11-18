package com.proyecto.supply_core.common.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.proyecto.supply_core.compra.solicitud.service.SlaAprobacionService;
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

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.proyecto.supply_core.notificacion.email.EmailClient emailClient;

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
    @Autowired private SlaAprobacionService sla;
    @Autowired private com.proyecto.supply_core.plataforma.service.TotpService totp;
    @Autowired private com.proyecto.supply_core.plataforma.repository.UsuarioPlataformaRepository usuariosPlataforma;

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
        jdbc.update("INSERT INTO suscripcion (empresa_id, plan_id, estado) SELECT ?, id, 'ACTIVA' FROM plan WHERE codigo = 'EMPRESARIAL'", empresaB);

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
    void lasExportacionesSoloLlevanDatosDeLaEmpresaDelTokenYQuedanAuditadas() throws Exception {
        String compradorA = token(empresaA, usuarioA, "COMPRADOR");
        mvc.perform(get("/api/exportaciones/ordenes/" + ordenB + "?formato=pdf").header(HttpHeaders.AUTHORIZATION, compradorA))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/exportaciones/ordenes/" + ordenB + "/correo?formato=pdf")
                        .header(HttpHeaders.AUTHORIZATION, compradorA))
                .andExpect(status().isNotFound());

        byte[] pdf = mvc.perform(get("/api/exportaciones/ordenes/" + ordenA + "?formato=pdf")
                        .header(HttpHeaders.AUTHORIZATION, compradorA))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            String texto = new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
            assertTrue(texto.contains("OC-FUGA-A"));
            assertTrue(texto.contains("NIT del proveedor"), texto);
            assertFalse(texto.contains("Empresa B"));
        }

        byte[] xlsx = mvc.perform(get("/api/exportaciones/inventario/stock?formato=xlsx")
                        .header(HttpHeaders.AUTHORIZATION, compradorA))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook libro = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(xlsx))) {
            for (org.apache.poi.ss.usermodel.Row fila : libro.getSheetAt(0)) {
                for (org.apache.poi.ss.usermodel.Cell celda : fila) {
                    assertFalse(celda.toString().contains("empresa B"), celda.toString());
                }
            }
        }
        mvc.perform(get("/api/exportaciones/inventario/kardex?formato=xlsx&productoId=" + productoB)
                        .header(HttpHeaders.AUTHORIZATION, compradorA))
                .andExpect(status().isOk());

        Integer auditadasA = jdbc.queryForObject("SELECT COUNT(*) FROM auditoria WHERE accion = 'EXPORTAR_ARCHIVO'"
                + " AND empresa_id = ? AND entidad_id = ?", Integer.class, empresaA, ordenA);
        Integer auditadasB = jdbc.queryForObject("SELECT COUNT(*) FROM auditoria WHERE accion = 'EXPORTAR_ARCHIVO'"
                + " AND empresa_id = ?", Integer.class, empresaB);
        assertEquals(1, auditadasA);
        assertEquals(0, auditadasB);
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
    void laRotacionSoloMuestraLosProductosYLasSalidasDeLaEmpresa() throws Exception {
        // la empresa B consume 777 unidades de su producto; la A no debe ver nada de eso
        jdbc.update("INSERT INTO movimiento_inventario (empresa_id, producto_id, bodega_origen_id, usuario_id, tipo, cantidad, "
                + "stock_resultante, creado_en) VALUES (?, ?, ?, ?, 'SALIDA_CONSUMO', 777, 0, NOW())",
                empresaB, productoB, bodegaB, usuarioB);

        mvc.perform(get("/api/reportes/rotacion").header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "ADMIN_SISTEMA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productoId").value(productoA))
                .andExpect(jsonPath("$.items[0].salidas").value(0.0));
        mvc.perform(get("/api/reportes/rotacion").header(HttpHeaders.AUTHORIZATION, token(empresaB, usuarioB, "ADMIN_SISTEMA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productoId").value(productoB))
                .andExpect(jsonPath("$.items[0].salidas").value(777.0))
                .andExpect(jsonPath("$.items[0].diasSinSalida").value(0));
        // una categoría de la otra empresa no devuelve nada
        mvc.perform(get("/api/reportes/rotacion?categoriaId=" + categoriaB)
                        .header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "ADMIN_SISTEMA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
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

    // ------------------------------------------------------------------ presupuesto (18-a)

    private Long centroDe(Long empresa) {
        return jdbc.queryForObject("SELECT id FROM centro_costo WHERE empresa_id = ? AND codigo = 'CC-1'", Long.class,
                empresa);
    }

    private static String presupuestoJson(Long centro, int anio, int mes, int monto) {
        return "{\"centroCostoId\":" + centro + ",\"anio\":" + anio + ",\"mes\":" + mes + ",\"monto\":" + monto + "}";
    }

    @Test
    void cadaEmpresaDefineYVeSoloSusPresupuestos() throws Exception {
        Long centroA = centroDe(empresaA);
        Long centroB = centroDe(empresaB);
        String adminA = token(empresaA, usuarioA, "ADMIN_COMPRAS");
        String adminB = token(empresaB, usuarioB, "ADMIN_COMPRAS");

        mvc.perform(post("/api/presupuestos").header(HttpHeaders.AUTHORIZATION, adminA)
                        .contentType(MediaType.APPLICATION_JSON).content(presupuestoJson(centroA, 2026, 11, 10000)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.comprometido").value(0.0));
        // A no puede ponerle presupuesto a un centro de costo de B
        mvc.perform(post("/api/presupuestos").header(HttpHeaders.AUTHORIZATION, adminA)
                        .contentType(MediaType.APPLICATION_JSON).content(presupuestoJson(centroB, 2026, 11, 10000)))
                .andExpect(status().isNotFound());
        // el mismo mes en B no choca con el de A, pero repetirlo dentro de A sí
        mvc.perform(post("/api/presupuestos").header(HttpHeaders.AUTHORIZATION, adminB)
                        .contentType(MediaType.APPLICATION_JSON).content(presupuestoJson(centroB, 2026, 11, 777)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/presupuestos").header(HttpHeaders.AUTHORIZATION, adminA)
                        .contentType(MediaType.APPLICATION_JSON).content(presupuestoJson(centroA, 2026, 11, 5)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PRESUPUESTO_DUPLICADO"));

        mvc.perform(get("/api/presupuestos?anio=2026").header(HttpHeaders.AUTHORIZATION, adminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].centroCostoId").value(centroA))
                .andExpect(jsonPath("$[0].monto").value(10000.0));
        mvc.perform(get("/api/presupuestos?anio=2026").header(HttpHeaders.AUTHORIZATION, adminB))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].centroCostoId").value(centroB))
                .andExpect(jsonPath("$[0].monto").value(777.0));

        Long presupuestoB = jdbc.queryForObject("SELECT id FROM presupuesto WHERE empresa_id = ?", Long.class,
                empresaB);
        mvc.perform(get("/api/presupuestos/" + presupuestoB).header(HttpHeaders.AUTHORIZATION, adminA))
                .andExpect(status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/presupuestos/" + presupuestoB).header(HttpHeaders.AUTHORIZATION, adminA)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"monto\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void elControlDePresupuestoYDeDuplicadasUsaLaBaseRealYSoloCuentaLaEmpresaDeLaSesion() throws Exception {
        Long centroA = centroDe(empresaA);
        Long unidad = jdbc.queryForObject("SELECT unidad_medida_id FROM producto WHERE id = ?", Long.class, productoA);
        java.math.BigDecimal costoOriginal = jdbc.queryForObject("SELECT costo_referencia FROM producto WHERE id = ?",
                java.math.BigDecimal.class, productoA);
        Long centroOriginal = jdbc.queryForObject("SELECT centro_costo_id FROM usuario WHERE id = ?", Long.class,
                usuarioA);
        String adminA = token(empresaA, usuarioA, "ADMIN_COMPRAS");
        String solicitanteA = token(empresaA, usuarioA, "SOLICITANTE");
        String json = MediaType.APPLICATION_JSON_VALUE;
        try {
            jdbc.update("UPDATE producto SET costo_referencia = 1000 WHERE id = ?", productoA);
            jdbc.update("UPDATE usuario SET centro_costo_id = ? WHERE id = ?", centroA, usuarioA);
            mvc.perform(post("/api/presupuestos").header(HttpHeaders.AUTHORIZATION, adminA).contentType(json)
                            .content(presupuestoJson(centroA, 2099, 1, 5000)))
                    .andExpect(status().isCreated());

            // 1) 4 x $ 1.000 = $ 4.000 dentro de $ 5.000 y sin política: queda aprobada
            Long s1 = crearSolicitud(solicitanteA, productoA, unidad, 4);
            mvc.perform(post("/api/solicitudes/" + s1 + "/enviar").header(HttpHeaders.AUTHORIZATION, solicitanteA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("APROBADA"))
                    .andExpect(jsonPath("$.montoEstimado").value(4000.0));

            // 2) $ 1.000 más llega justo al tope, pero repite el producto de la solicitud aprobada
            Long s2 = crearSolicitud(solicitanteA, productoA, unidad, 1);
            mvc.perform(post("/api/solicitudes/" + s2 + "/enviar").header(HttpHeaders.AUTHORIZATION, solicitanteA))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("SOLICITUD_DUPLICADA"));
            mvc.perform(post("/api/solicitudes/" + s2 + "/enviar").header(HttpHeaders.AUTHORIZATION, solicitanteA)
                            .contentType(json).content("{\"justificacionExcepcion\":\"Es para otra obra\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("APROBADA"))
                    .andExpect(jsonPath("$.excedePresupuesto").value(false))
                    .andExpect(jsonPath("$.justificacionExcepcion").value("Es para otra obra"));

            // 3) con $ 5.000 ya comprometidos, otra de $ 1.000 excede; sin política que la autorice no hay excepción
            Long s3 = crearSolicitud(solicitanteA, productoA, unidad, 1);
            mvc.perform(post("/api/solicitudes/" + s3 + "/enviar").header(HttpHeaders.AUTHORIZATION, solicitanteA))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("PRESUPUESTO_EXCEDIDO"));
            mvc.perform(post("/api/solicitudes/" + s3 + "/enviar").header(HttpHeaders.AUTHORIZATION, solicitanteA)
                            .contentType(json).content("{\"justificacionExcepcion\":\"Urgente\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("PRESUPUESTO_EXCEDIDO"));

            // 4) la base suma lo comprometido; B no ve nada de esto
            mvc.perform(get("/api/presupuestos?anio=2099").header(HttpHeaders.AUTHORIZATION, adminA))
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].comprometido").value(5000.0))
                    .andExpect(jsonPath("$[0].disponible").value(0.0))
                    .andExpect(jsonPath("$[0].usoPct").value(100.0));
            mvc.perform(get("/api/presupuestos?anio=2099")
                            .header(HttpHeaders.AUTHORIZATION, token(empresaB, usuarioB, "ADMIN_COMPRAS")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        } finally {
            // las solicitudes de la prueba no deben dejar compras abiertas que choquen con otras pruebas
            jdbc.update("UPDATE solicitud_compra SET estado = 'CANCELADA' WHERE centro_costo_id = ? "
                    + "AND estado IN ('PENDIENTE_APROBACION', 'APROBADA', 'ADJUDICADA')", centroA);
            jdbc.update("UPDATE producto SET costo_referencia = ? WHERE id = ?", costoOriginal, productoA);
            jdbc.update("UPDATE usuario SET centro_costo_id = ? WHERE id = ?", centroOriginal, usuarioA);
        }
    }

    /** Crea una solicitud en BORRADOR de una sola línea para el 15 de enero de 2099 y devuelve su id. */
    private Long crearSolicitud(String bearer, Long producto, Long unidad, int cantidad) throws Exception {
        return crearSolicitud(bearer, producto, unidad, cantidad, "2099-01-15");
    }

    /** Igual que {@link #crearSolicitud(String, Long, Long, int)} pero para la fecha requerida indicada. */
    private Long crearSolicitud(String bearer, Long producto, Long unidad, int cantidad, String fecha) throws Exception {
        String cuerpo = "{\"motivo\":\"Prueba de presupuesto\",\"fechaRequerida\":\"" + fecha + "\",\"lineas\":[{"
                + "\"productoId\":" + producto + ",\"unidadMedidaId\":" + unidad + ",\"cantidad\":" + cantidad + "}]}";
        String respuesta = mvc.perform(post("/api/solicitudes").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(respuesta).get("id").asLong();
    }

    // ------------------------------------------------------------------ contratos y orden directa (18-b)

    private static String contratoJson(Long proveedor, String numero, Long producto, String precio, String tope,
            java.time.LocalDate desde, java.time.LocalDate hasta) {
        return "{\"proveedorId\":" + proveedor + ",\"numero\":\"" + numero + "\",\"vigenteDesde\":\"" + desde
                + "\",\"vigenteHasta\":\"" + hasta + "\",\"precios\":[{\"productoId\":" + producto
                + ",\"precioUnitario\":" + precio + ",\"impuestoPct\":19" + (tope == null ? "" : ",\"cantidadMaxima\":" + tope)
                + "}]}";
    }

    @Test
    void cadaEmpresaDefineYVeSoloSusContratosYElSolapeSeDetectaEnLaBase() throws Exception {
        String estadoA = jdbc.queryForObject("SELECT estado FROM proveedor WHERE id = ?", String.class, proveedorA);
        String estadoB = jdbc.queryForObject("SELECT estado FROM proveedor WHERE id = ?", String.class, proveedorB);
        String json = MediaType.APPLICATION_JSON_VALUE;
        String adminA = token(empresaA, usuarioA, "ADMIN_COMPRAS");
        String adminB = token(empresaB, usuarioB, "ADMIN_COMPRAS");
        java.time.LocalDate hoy = java.time.LocalDate.now();
        try {
            jdbc.update("UPDATE proveedor SET estado = 'HABILITADO' WHERE id IN (?, ?)", proveedorA, proveedorB);

            mvc.perform(post("/api/contratos").header(HttpHeaders.AUTHORIZATION, adminA).contentType(json)
                            .content(contratoJson(proveedorA, "ct-fuga", productoA, "900", "10", hoy, hoy.plusDays(30))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.numero").value("CT-FUGA"))
                    .andExpect(jsonPath("$.vigente").value(true));
            // A no puede firmar con un proveedor de B ni poner un producto de B
            mvc.perform(post("/api/contratos").header(HttpHeaders.AUTHORIZATION, adminA).contentType(json)
                            .content(contratoJson(proveedorB, "CT-X", productoA, "900", null, hoy, hoy.plusDays(30))))
                    .andExpect(status().isNotFound());
            mvc.perform(post("/api/contratos").header(HttpHeaders.AUTHORIZATION, adminA).contentType(json)
                            .content(contratoJson(proveedorA, "CT-Y", productoB, "900", null, hoy, hoy.plusDays(30))))
                    .andExpect(status().isBadRequest());
            // el mismo número en B no choca con el de A, pero repetirlo dentro de A sí
            mvc.perform(post("/api/contratos").header(HttpHeaders.AUTHORIZATION, adminB).contentType(json)
                            .content(contratoJson(proveedorB, "CT-FUGA", productoB, "800", null, hoy, hoy.plusDays(30))))
                    .andExpect(status().isCreated());
            mvc.perform(post("/api/contratos").header(HttpHeaders.AUTHORIZATION, adminA).contentType(json)
                            .content(contratoJson(proveedorA, "ct-fuga", productoA, "900", null, hoy.plusDays(60), hoy.plusDays(90))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("NUMERO_DUPLICADO"));
            // otro contrato activo del mismo proveedor y producto con fechas que se cruzan
            mvc.perform(post("/api/contratos").header(HttpHeaders.AUTHORIZATION, adminA).contentType(json)
                            .content(contratoJson(proveedorA, "CT-SOLAPE", productoA, "850", null, hoy.plusDays(10), hoy.plusDays(40))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("CONTRATO_SOLAPADO"));
            // en fechas que no se cruzan sí se puede
            mvc.perform(post("/api/contratos").header(HttpHeaders.AUTHORIZATION, adminA).contentType(json)
                            .content(contratoJson(proveedorA, "CT-SIGUIENTE", productoA, "850", null, hoy.plusDays(31), hoy.plusDays(90))))
                    .andExpect(status().isCreated());

            mvc.perform(get("/api/contratos").header(HttpHeaders.AUTHORIZATION, adminA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido[?(@.numero=='CT-FUGA')]").isNotEmpty())
                    .andExpect(jsonPath("$.contenido[?(@.numero=='CT-SIGUIENTE')]").isNotEmpty())
                    .andExpect(jsonPath("$.contenido[?(@.proveedorId != " + proveedorA + ")]").isEmpty());
            mvc.perform(get("/api/contratos").header(HttpHeaders.AUTHORIZATION, adminB))
                    .andExpect(jsonPath("$.contenido.length()").value(1))
                    .andExpect(jsonPath("$.contenido[0].proveedorId").value(proveedorB));
            Long contratoB = jdbc.queryForObject("SELECT id FROM contrato_proveedor WHERE empresa_id = ?", Long.class,
                    empresaB);
            mvc.perform(get("/api/contratos/" + contratoB).header(HttpHeaders.AUTHORIZATION, adminA))
                    .andExpect(status().isNotFound());
            mvc.perform(patch("/api/contratos/" + contratoB + "/anular").header(HttpHeaders.AUTHORIZATION, adminA)
                            .contentType(json).content("{\"motivo\":\"Intento desde otra empresa\"}"))
                    .andExpect(status().isNotFound());
        } finally {
            // se borran solo los contratos de esta prueba: así no se cruzan en fechas con los de otras
            jdbc.update("DELETE FROM contrato_precio WHERE contrato_id IN (SELECT id FROM contrato_proveedor "
                    + "WHERE numero IN ('CT-FUGA', 'CT-SIGUIENTE'))");
            jdbc.update("DELETE FROM contrato_proveedor WHERE numero IN ('CT-FUGA', 'CT-SIGUIENTE')");
            jdbc.update("UPDATE proveedor SET estado = ? WHERE id = ?", estadoA, proveedorA);
            jdbc.update("UPDATE proveedor SET estado = ? WHERE id = ?", estadoB, proveedorB);
        }
    }

    @Test
    void laOrdenDirectaDeContratoUsaLaBaseRealRespetaElTopeYNoCruzaEmpresas() throws Exception {
        Long centroA = centroDe(empresaA);
        Long unidad = jdbc.queryForObject("SELECT unidad_medida_id FROM producto WHERE id = ?", Long.class, productoA);
        java.math.BigDecimal costoOriginal = jdbc.queryForObject("SELECT costo_referencia FROM producto WHERE id = ?",
                java.math.BigDecimal.class, productoA);
        Long centroOriginal = jdbc.queryForObject("SELECT centro_costo_id FROM usuario WHERE id = ?", Long.class,
                usuarioA);
        String estadoA = jdbc.queryForObject("SELECT estado FROM proveedor WHERE id = ?", String.class, proveedorA);
        String json = MediaType.APPLICATION_JSON_VALUE;
        String adminA = token(empresaA, usuarioA, "ADMIN_COMPRAS");
        String solicitanteA = token(empresaA, usuarioA, "SOLICITANTE");
        String compradorA = token(empresaA, usuarioA2, "COMPRADOR");
        String compradorB = token(empresaB, usuarioB, "COMPRADOR");
        java.time.LocalDate hoy = java.time.LocalDate.now();
        try {
            jdbc.update("UPDATE producto SET costo_referencia = 1000 WHERE id = ?", productoA);
            jdbc.update("UPDATE usuario SET centro_costo_id = ? WHERE id = ?", centroA, usuarioA);
            jdbc.update("UPDATE proveedor SET estado = 'HABILITADO' WHERE id = ?", proveedorA);
            // precio pactado $ 900 (el de referencia es $ 1.000), con tope de 10 unidades
            String respuestaContrato = mvc.perform(post("/api/contratos").header(HttpHeaders.AUTHORIZATION, adminA)
                            .contentType(json).content(contratoJson(proveedorA, "CT-DIRECTA", productoA, "900", "10",
                                    hoy.minusDays(1), hoy.plusDays(30))))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            Long contrato = new com.fasterxml.jackson.databind.ObjectMapper().readTree(respuestaContrato).get("id").asLong();

            // 4 unidades: se aprueban por $ 4.000 (sin política) y al precio pactado cuestan $ 3.600 + 19 %
            Long s1 = crearSolicitud(solicitanteA, productoA, unidad, 4, "2098-03-10");
            mvc.perform(post("/api/solicitudes/" + s1 + "/enviar").header(HttpHeaders.AUTHORIZATION, solicitanteA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("APROBADA"));
            mvc.perform(get("/api/contratos/ofertas?solicitudId=" + s1).header(HttpHeaders.AUTHORIZATION, compradorA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].contratoId").value(contrato))
                    .andExpect(jsonPath("$[0].puedeEmitirse").value(true))
                    .andExpect(jsonPath("$[0].subtotal").value(3600.0));

            String directa = "{\"solicitudId\":" + s1 + ",\"contratoId\":" + contrato + ",\"bodegaDestinoId\":" + bodegaA
                    + ",\"lugarEntrega\":\"Bodega 1\",\"fechaEntregaEsperada\":\"" + hoy.plusDays(7) + "\"}";
            // el solicitante no puede emitir la orden de su propia solicitud
            mvc.perform(post("/api/ordenes/directa").header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "COMPRADOR"))
                            .contentType(json).content(directa))
                    .andExpect(status().isForbidden());
            // otra empresa no ve ni la solicitud ni el contrato
            mvc.perform(post("/api/ordenes/directa").header(HttpHeaders.AUTHORIZATION, compradorB).contentType(json)
                            .content(directa))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/contratos/ofertas?solicitudId=" + s1).header(HttpHeaders.AUTHORIZATION, compradorB))
                    .andExpect(status().isNotFound());

            String orden = mvc.perform(post("/api/ordenes/directa").header(HttpHeaders.AUTHORIZATION, compradorA)
                            .contentType(json).content(directa))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.adjudicacionId").doesNotExist())
                    .andExpect(jsonPath("$.contratoId").value(contrato))
                    .andExpect(jsonPath("$.solicitudId").value(s1))
                    .andExpect(jsonPath("$.subtotal").value(3600.0))
                    .andExpect(jsonPath("$.total").value(4284.0))
                    .andReturn().getResponse().getContentAsString();
            Long ordenId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(orden).get("id").asLong();
            assertEquals(empresaA, jdbc.queryForObject("SELECT empresa_id FROM orden_compra WHERE id = ?", Long.class, ordenId));
            assertEquals("ADJUDICADA", jdbc.queryForObject("SELECT estado FROM solicitud_compra WHERE id = ?", String.class, s1));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM orden_compra_linea WHERE orden_compra_id = ? "
                    + "AND contrato_precio_id IS NOT NULL", Integer.class, ordenId));
            // la solicitud ya no se puede comprar otra vez y el tope descontó lo pedido
            mvc.perform(post("/api/ordenes/directa").header(HttpHeaders.AUTHORIZATION, compradorA).contentType(json)
                            .content(directa))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("SOLICITUD_NO_APROBADA"));
            mvc.perform(get("/api/contratos/" + contrato).header(HttpHeaders.AUTHORIZATION, adminA))
                    .andExpect(jsonPath("$.precios[0].consumido").value(4.0))
                    .andExpect(jsonPath("$.precios[0].disponible").value(6.0));

            // 7 unidades pasan del tope (quedan 6)
            Long s2 = crearSolicitud(solicitanteA, productoA, unidad, 7, "2098-03-10");
            mvc.perform(post("/api/solicitudes/" + s2 + "/enviar").header(HttpHeaders.AUTHORIZATION, solicitanteA))
                    .andExpect(status().isOk());
            mvc.perform(post("/api/ordenes/directa").header(HttpHeaders.AUTHORIZATION, compradorA).contentType(json)
                            .content(directa.replace("\"solicitudId\":" + s1, "\"solicitudId\":" + s2)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("CONTRATO_TOPE_EXCEDIDO"));

            // cancelar la orden libera el tope y devuelve la solicitud a APROBADA
            mvc.perform(post("/api/ordenes/" + ordenId + "/cancelar").header(HttpHeaders.AUTHORIZATION, compradorA)
                            .contentType(json).content("{\"observacion\":\"El proveedor no entrega\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("CANCELADA"));
            assertEquals("APROBADA", jdbc.queryForObject("SELECT estado FROM solicitud_compra WHERE id = ?", String.class, s1));
            mvc.perform(get("/api/contratos/" + contrato).header(HttpHeaders.AUTHORIZATION, adminA))
                    .andExpect(jsonPath("$.precios[0].consumido").value(0.0))
                    .andExpect(jsonPath("$.precios[0].disponible").value(10.0));

            // un contrato anulado ya no se aplica
            mvc.perform(patch("/api/contratos/" + contrato + "/anular").header(HttpHeaders.AUTHORIZATION, adminA)
                            .contentType(json).content("{\"motivo\":\"Termina la relación\"}"))
                    .andExpect(status().isOk());
            mvc.perform(post("/api/ordenes/directa").header(HttpHeaders.AUTHORIZATION, compradorA).contentType(json)
                            .content(directa))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("CONTRATO_NO_VIGENTE"));
        } finally {
            // las solicitudes de la prueba no deben dejar compras abiertas que choquen con otras pruebas
            jdbc.update("UPDATE solicitud_compra SET estado = 'CANCELADA' WHERE centro_costo_id = ? "
                    + "AND estado IN ('PENDIENTE_APROBACION', 'APROBADA', 'ADJUDICADA')", centroA);
            jdbc.update("UPDATE producto SET costo_referencia = ? WHERE id = ?", costoOriginal, productoA);
            jdbc.update("UPDATE usuario SET centro_costo_id = ? WHERE id = ?", centroOriginal, usuarioA);
            jdbc.update("UPDATE proveedor SET estado = ? WHERE id = ?", estadoA, proveedorA);
        }
    }

    // ------------------------------------------------------------------ delegación y SLA (18-c)

    private Long rolId(String codigo) {
        return jdbc.queryForObject("SELECT id FROM rol WHERE codigo = ?", Long.class, codigo);
    }

    private Long crearPoliticaSolicitud(Long empresa, String rol, String desde, String hasta) {
        jdbc.update("INSERT INTO politica_aprobacion (empresa_id, tipo, rol_aprobador_id, monto_desde, monto_hasta, "
                + "activo) VALUES (?, 'SOLICITUD', ?, ?, ?, 1)", empresa, rolId(rol), desde, hasta);
        return jdbc.queryForObject("SELECT MAX(id) FROM politica_aprobacion WHERE empresa_id = ?", Long.class, empresa);
    }

    private Long usuarioInternoDeA(String correo, String rol) {
        return EmpresaContexto.como(empresaA, () -> {
            Usuario u = new Usuario();
            u.setNombreCompleto("Usuario " + correo);
            u.setCorreo(correo);
            u.setHashPassword(passwordEncoder.encode(CLAVE));
            u.getRoles().add(roles.findByCodigo(rol).orElseThrow());
            return usuarios.save(u).getId();
        });
    }

    private static String decision(String valor) {
        return "{\"decision\":\"" + valor + "\",\"observaciones\":\"ok\"}";
    }

    @Test
    void laDelegacionPermiteDecidirSoloMientrasValeYNoSaltaLaSegregacionNiCruzaEmpresas() throws Exception {
        Long centroA = centroDe(empresaA);
        Long unidad = jdbc.queryForObject("SELECT unidad_medida_id FROM producto WHERE id = ?", Long.class, productoA);
        java.math.BigDecimal costoOriginal = jdbc.queryForObject("SELECT costo_referencia FROM producto WHERE id = ?",
                java.math.BigDecimal.class, productoA);
        Long centroUsuarioA = jdbc.queryForObject("SELECT centro_costo_id FROM usuario WHERE id = ?", Long.class, usuarioA);
        Long centroUsuarioA2 = jdbc.queryForObject("SELECT centro_costo_id FROM usuario WHERE id = ?", Long.class, usuarioA2);
        Long delegadoA = usuarioInternoDeA("delegado@a.com", "ALMACENISTA");
        String json = MediaType.APPLICATION_JSON_VALUE;
        String solicitanteA = token(empresaA, usuarioA, "SOLICITANTE");
        String solicitanteA2 = token(empresaA, usuarioA2, "SOLICITANTE");
        String deleganteA = token(empresaA, usuarioA2, "ADMIN_COMPRAS");
        String delegado = token(empresaA, delegadoA, "ALMACENISTA");
        String justificacion = "{\"justificacionExcepcion\":\"Prueba de delegación\"}";
        java.time.LocalDate hoy = java.time.LocalDate.now();
        Long politica = null;
        try {
            jdbc.update("UPDATE producto SET costo_referencia = 1000 WHERE id = ?", productoA);
            jdbc.update("UPDATE usuario SET centro_costo_id = ? WHERE id IN (?, ?)", centroA, usuarioA, usuarioA2);
            politica = crearPoliticaSolicitud(empresaA, "ADMIN_COMPRAS", "0", null);

            Long s1 = crearSolicitud(solicitanteA, productoA, unidad, 4, "2097-05-05");
            mvc.perform(post("/api/solicitudes/" + s1 + "/enviar").header(HttpHeaders.AUTHORIZATION, solicitanteA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("PENDIENTE_APROBACION"));

            // sin delegación, un almacenista no decide
            mvc.perform(post("/api/solicitudes/" + s1 + "/decision").header(HttpHeaders.AUTHORIZATION, delegado)
                            .contentType(json).content(decision("APROBADA")))
                    .andExpect(status().isForbidden());
            // la delegación la crea quien tiene un rol que aprueba; otra empresa no la ve ni la revoca
            String creada = mvc.perform(post("/api/delegaciones").header(HttpHeaders.AUTHORIZATION, deleganteA)
                            .contentType(json).content("{\"delegadoId\":" + delegadoA + ",\"vigenteDesde\":\"" + hoy
                                    + "\",\"vigenteHasta\":\"" + hoy.plusDays(1) + "\",\"motivo\":\"Vacaciones\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.vigente").value(true))
                    .andReturn().getResponse().getContentAsString();
            Long delegacion = new com.fasterxml.jackson.databind.ObjectMapper().readTree(creada).get("id").asLong();
            mvc.perform(patch("/api/delegaciones/" + delegacion + "/revocar")
                            .header(HttpHeaders.AUTHORIZATION, token(empresaB, usuarioB, "ADMIN_COMPRAS")))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/delegaciones/otorgadas")
                            .header(HttpHeaders.AUTHORIZATION, token(empresaB, usuarioB, "ADMIN_COMPRAS")))
                    .andExpect(jsonPath("$").isEmpty());
            mvc.perform(post("/api/delegaciones").header(HttpHeaders.AUTHORIZATION, solicitanteA).contentType(json)
                            .content("{\"delegadoId\":" + delegadoA + ",\"vigenteDesde\":\"" + hoy + "\",\"vigenteHasta\":\""
                                    + hoy + "\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("SIN_ROL_APROBADOR"));

            // con la delegación vigente la ve en su bandeja y decide
            mvc.perform(get("/api/delegaciones/recibidas").header(HttpHeaders.AUTHORIZATION, delegado))
                    .andExpect(jsonPath("$[0].deleganteId").value(usuarioA2));
            mvc.perform(get("/api/solicitudes/pendientes-aprobacion").header(HttpHeaders.AUTHORIZATION, delegado))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido[?(@.id==" + s1 + ")]").isNotEmpty());
            mvc.perform(post("/api/solicitudes/" + s1 + "/decision").header(HttpHeaders.AUTHORIZATION, delegado)
                            .contentType(json).content(decision("APROBADA")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("APROBADA"));
            assertEquals(delegacion, jdbc.queryForObject(
                    "SELECT delegacion_id FROM aprobacion_solicitud WHERE solicitud_compra_id = ?", Long.class, s1));
            assertEquals(delegadoA, jdbc.queryForObject(
                    "SELECT usuario_aprobador_id FROM aprobacion_solicitud WHERE solicitud_compra_id = ?", Long.class, s1));

            // segregación: no decide lo que pidió quien le delegó
            Long s2 = crearSolicitud(solicitanteA2, productoA, unidad, 4, "2097-05-05");
            mvc.perform(post("/api/solicitudes/" + s2 + "/enviar").header(HttpHeaders.AUTHORIZATION, solicitanteA2)
                            .contentType(json).content(justificacion))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("PENDIENTE_APROBACION"));
            mvc.perform(post("/api/solicitudes/" + s2 + "/decision").header(HttpHeaders.AUTHORIZATION, delegado)
                            .contentType(json).content(decision("APROBADA")))
                    .andExpect(status().isForbidden());

            // revocada la delegación, deja de poder decidir
            Long s3 = crearSolicitud(solicitanteA, productoA, unidad, 4, "2097-05-05");
            mvc.perform(post("/api/solicitudes/" + s3 + "/enviar").header(HttpHeaders.AUTHORIZATION, solicitanteA)
                            .contentType(json).content(justificacion))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("PENDIENTE_APROBACION"));
            mvc.perform(patch("/api/delegaciones/" + delegacion + "/revocar").header(HttpHeaders.AUTHORIZATION, deleganteA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("REVOCADA"));
            mvc.perform(post("/api/solicitudes/" + s3 + "/decision").header(HttpHeaders.AUTHORIZATION, delegado)
                            .contentType(json).content(decision("APROBADA")))
                    .andExpect(status().isForbidden());
            mvc.perform(patch("/api/delegaciones/" + delegacion + "/revocar").header(HttpHeaders.AUTHORIZATION, deleganteA))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("DELEGACION_REVOCADA"));
        } finally {
            jdbc.update("UPDATE politica_aprobacion SET activo = 0 WHERE empresa_id = ?", empresaA);
            jdbc.update("UPDATE solicitud_compra SET estado = 'CANCELADA' WHERE centro_costo_id = ? "
                    + "AND estado IN ('PENDIENTE_APROBACION', 'APROBADA', 'ADJUDICADA')", centroA);
            // el usuario delegado de la prueba se borra con lo suyo, para no alterar los conteos de otras pruebas
            jdbc.update("DELETE FROM aprobacion_solicitud WHERE usuario_aprobador_id = ?", delegadoA);
            jdbc.update("DELETE FROM delegacion_aprobacion WHERE empresa_id = ?", empresaA);
            jdbc.update("DELETE FROM notificacion WHERE usuario_id = ?", delegadoA);
            jdbc.update("DELETE FROM auditoria WHERE usuario_id = ?", delegadoA);
            jdbc.update("DELETE FROM usuario_rol WHERE usuario_id = ?", delegadoA);
            jdbc.update("DELETE FROM usuario WHERE id = ?", delegadoA);
            jdbc.update("UPDATE producto SET costo_referencia = ? WHERE id = ?", costoOriginal, productoA);
            jdbc.update("UPDATE usuario SET centro_costo_id = ? WHERE id = ?", centroUsuarioA, usuarioA);
            jdbc.update("UPDATE usuario SET centro_costo_id = ? WHERE id = ?", centroUsuarioA2, usuarioA2);
        }
    }

    @Test
    void elSlaAvisaYEscalaSoloLaEmpresaQueCorresponde() throws Exception {
        Long centroA = centroDe(empresaA);
        Long unidad = jdbc.queryForObject("SELECT unidad_medida_id FROM producto WHERE id = ?", Long.class, productoA);
        java.math.BigDecimal costoOriginal = jdbc.queryForObject("SELECT costo_referencia FROM producto WHERE id = ?",
                java.math.BigDecimal.class, productoA);
        Long centroUsuarioA = jdbc.queryForObject("SELECT centro_costo_id FROM usuario WHERE id = ?", Long.class, usuarioA);
        String json = MediaType.APPLICATION_JSON_VALUE;
        String solicitanteA = token(empresaA, usuarioA, "SOLICITANTE");
        try {
            jdbc.update("UPDATE producto SET costo_referencia = 1000 WHERE id = ?", productoA);
            jdbc.update("UPDATE usuario SET centro_costo_id = ? WHERE id = ?", centroA, usuarioA);
            Long bajo = crearPoliticaSolicitud(empresaA, "ADMIN_COMPRAS", "0", "1000000");
            Long alto = crearPoliticaSolicitud(empresaA, "ADMIN_SISTEMA", "1000000", null);
            assertEquals(24, jdbc.queryForObject("SELECT sla_aprobacion_horas FROM empresa WHERE id = ?", Integer.class,
                    empresaA));

            Long s1 = crearSolicitud(solicitanteA, productoA, unidad, 4, "2097-06-06");
            mvc.perform(post("/api/solicitudes/" + s1 + "/enviar").header(HttpHeaders.AUTHORIZATION, solicitanteA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("PENDIENTE_APROBACION"));
            assertEquals(bajo, jdbc.queryForObject("SELECT politica_aprobacion_id FROM solicitud_compra WHERE id = ?",
                    Long.class, s1));

            // 20 h de 24 (más del 80 %): avisa una sola vez
            ponerEnviadoHace(s1, 20);
            assertEquals(0, EmpresaContexto.como(empresaB, () -> sla.procesar(java.time.LocalDateTime.now())).avisadas());
            assertEquals(1, EmpresaContexto.como(empresaA, () -> sla.procesar(java.time.LocalDateTime.now())).avisadas());
            assertEquals(0, EmpresaContexto.como(empresaA, () -> sla.procesar(java.time.LocalDateTime.now())).avisadas());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM solicitud_compra WHERE id = ? "
                    + "AND sla_aviso_en IS NOT NULL AND escalada_en IS NULL", Integer.class, s1));
            assertEquals(bajo, jdbc.queryForObject("SELECT politica_aprobacion_id FROM solicitud_compra WHERE id = ?",
                    Long.class, s1));

            // 25 h: la empresa B no la toca; la A la escala a la política de mayor tramo
            ponerEnviadoHace(s1, 25);
            assertEquals(0, EmpresaContexto.como(empresaB, () -> sla.procesar(java.time.LocalDateTime.now())).escaladas());
            assertEquals(bajo, jdbc.queryForObject("SELECT politica_aprobacion_id FROM solicitud_compra WHERE id = ?",
                    Long.class, s1));
            assertEquals(1, EmpresaContexto.como(empresaA, () -> sla.procesar(java.time.LocalDateTime.now())).escaladas());
            assertEquals(0, EmpresaContexto.como(empresaA, () -> sla.procesar(java.time.LocalDateTime.now())).escaladas());
            assertEquals(alto, jdbc.queryForObject("SELECT politica_aprobacion_id FROM solicitud_compra WHERE id = ?",
                    Long.class, s1));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM solicitud_compra WHERE id = ? "
                    + "AND escalada_en IS NOT NULL", Integer.class, s1));
            // usuarioA recibe el aviso como solicitante y, por ser ADMIN_SISTEMA, el del rol al que se escaló
            assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM notificacion WHERE usuario_id = ? "
                    + "AND evento = 'SOLICITUD_ESCALADA'", Integer.class, usuarioA) >= 1);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM auditoria WHERE entidad = 'SOLICITUD_COMPRA' "
                    + "AND entidad_id = ? AND accion = 'SOLICITUD_ESCALAR'", Integer.class, s1));

            // tras escalar, el rol de abajo ya no decide y el de arriba sí
            mvc.perform(post("/api/solicitudes/" + s1 + "/decision")
                            .header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA2, "ADMIN_COMPRAS"))
                            .contentType(json).content(decision("APROBADA")))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/solicitudes/" + s1 + "/decision")
                            .header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA2, "ADMIN_SISTEMA"))
                            .contentType(json).content(decision("APROBADA")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("APROBADA"));
        } finally {
            jdbc.update("UPDATE politica_aprobacion SET activo = 0 WHERE empresa_id = ?", empresaA);
            jdbc.update("UPDATE solicitud_compra SET estado = 'CANCELADA' WHERE centro_costo_id = ? "
                    + "AND estado IN ('PENDIENTE_APROBACION', 'APROBADA', 'ADJUDICADA')", centroA);
            jdbc.update("UPDATE producto SET costo_referencia = ? WHERE id = ?", costoOriginal, productoA);
            jdbc.update("UPDATE usuario SET centro_costo_id = ? WHERE id = ?", centroUsuarioA, usuarioA);
        }
    }

    /** Hace que la solicitud parezca enviada hace las horas indicadas. */
    private void ponerEnviadoHace(Long solicitud, int horas) {
        jdbc.update("UPDATE solicitud_compra SET enviado_en = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusHours(horas)), solicitud);
    }

    // ------------------------------------------------------------------ reorden y carga masiva (18-d)

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** Arma un .xlsx en memoria: la primera fila es el encabezado y las demás son datos. */
    private static org.springframework.mock.web.MockMultipartFile excel(String[] encabezado, Object[]... filas)
            throws java.io.IOException {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook libro = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
                java.io.ByteArrayOutputStream salida = new java.io.ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet hoja = libro.createSheet("datos");
            org.apache.poi.ss.usermodel.Row cabecera = hoja.createRow(0);
            for (int j = 0; j < encabezado.length; j++) {
                cabecera.createCell(j).setCellValue(encabezado[j]);
            }
            for (int i = 0; i < filas.length; i++) {
                org.apache.poi.ss.usermodel.Row fila = hoja.createRow(i + 1);
                for (int j = 0; j < filas[i].length; j++) {
                    if (filas[i][j] instanceof Number n) {
                        fila.createCell(j).setCellValue(n.doubleValue());
                    } else if (filas[i][j] != null) {
                        fila.createCell(j).setCellValue(filas[i][j].toString());
                    }
                }
            }
            libro.write(salida);
            return new org.springframework.mock.web.MockMultipartFile("archivo", "productos.xlsx", XLSX,
                    salida.toByteArray());
        }
    }

    @Test
    void laCargaMasivaCreaYActualizaSoloEnLaEmpresaDelTokenYEsDeTodoONada() throws Exception {
        String unidad = jdbc.queryForObject("SELECT codigo FROM unidad_medida WHERE id = (SELECT unidad_medida_id "
                + "FROM producto WHERE id = ?)", String.class, productoA);
        String[] cabecera = { "codigo", "nombre", "categoria", "unidad", "stock_minimo", "stock_maximo",
                "costo_referencia" };
        String adminA = token(empresaA, usuarioA, "ADMIN_COMPRAS");
        String adminB = token(empresaB, usuarioB, "ADMIN_COMPRAS");
        try {
            var deA = excel(cabecera, new Object[] { "cm-1", "Cable de carga", "Carga masiva", unidad, 5, 20, 1500 },
                    new Object[] { "CM-2", "Tubo de carga", "carga masiva", unidad, null, null, 320 });

            // revisar no guarda nada
            mvc.perform(multipart("/api/productos/carga-masiva").file(deA).param("soloValidar", "true")
                            .header(HttpHeaders.AUTHORIZATION, adminA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.aplicado").value(false))
                    .andExpect(jsonPath("$.creados").value(2))
                    .andExpect(jsonPath("$.categoriasCreadas").value(1));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM producto WHERE codigo LIKE 'CM-%'", Integer.class));

            // carga real en A: se crean los productos y la categoría, solo en A
            mvc.perform(multipart("/api/productos/carga-masiva").file(deA).header(HttpHeaders.AUTHORIZATION, adminA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.aplicado").value(true))
                    .andExpect(jsonPath("$.creados").value(2))
                    .andExpect(jsonPath("$.errores").isEmpty());
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM producto WHERE codigo LIKE 'CM-%' AND empresa_id = ?",
                    Integer.class, empresaA));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM categoria_producto WHERE nombre = 'Carga masiva' "
                    + "AND empresa_id = ?", Integer.class, empresaA));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM producto WHERE codigo LIKE 'CM-%' AND empresa_id = ?",
                    Integer.class, empresaB));

            // B sube los mismos códigos con otro precio: no choca con A ni le cambia nada
            var deB = excel(cabecera, new Object[] { "CM-1", "Cable de B", "Carga masiva", unidad, 1, 2, 999 });
            mvc.perform(multipart("/api/productos/carga-masiva").file(deB).header(HttpHeaders.AUTHORIZATION, adminB))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.creados").value(1))
                    .andExpect(jsonPath("$.actualizados").value(0));
            assertEquals(0, new java.math.BigDecimal("1500").compareTo(jdbc.queryForObject(
                    "SELECT costo_referencia FROM producto WHERE codigo = 'CM-1' AND empresa_id = ?",
                    java.math.BigDecimal.class, empresaA)));
            assertEquals(0, new java.math.BigDecimal("999").compareTo(jdbc.queryForObject(
                    "SELECT costo_referencia FROM producto WHERE codigo = 'CM-1' AND empresa_id = ?",
                    java.math.BigDecimal.class, empresaB)));

            // un archivo de solo precios actualiza el costo y deja lo demás como estaba
            var precios = excel(new String[] { "codigo", "costo_referencia" }, new Object[] { "CM-1", 1750 });
            mvc.perform(multipart("/api/productos/carga-masiva").file(precios).header(HttpHeaders.AUTHORIZATION, adminA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.actualizados").value(1))
                    .andExpect(jsonPath("$.creados").value(0));
            assertEquals(0, new java.math.BigDecimal("1750").compareTo(jdbc.queryForObject(
                    "SELECT costo_referencia FROM producto WHERE codigo = 'CM-1' AND empresa_id = ?",
                    java.math.BigDecimal.class, empresaA)));
            assertEquals("Cable de carga", jdbc.queryForObject(
                    "SELECT nombre FROM producto WHERE codigo = 'CM-1' AND empresa_id = ?", String.class, empresaA));

            // todo o nada: con una fila mala no se guarda ni la buena
            var conError = excel(cabecera, new Object[] { "CM-3", "Bueno", "Carga masiva", unidad, 1, 2, 10 },
                    new Object[] { "CM-4", "Malo", "Carga masiva", "NOEXISTE", 1, 2, 10 });
            mvc.perform(multipart("/api/productos/carga-masiva").file(conError).header(HttpHeaders.AUTHORIZATION, adminA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.aplicado").value(false))
                    .andExpect(jsonPath("$.errores[0].fila").value(3));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM producto WHERE codigo IN ('CM-3', 'CM-4')",
                    Integer.class));

            mvc.perform(multipart("/api/productos/carga-masiva").file(deA)
                            .header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "COMPRADOR")))
                    .andExpect(status().isForbidden());
        } finally {
            jdbc.update("DELETE FROM producto WHERE codigo LIKE 'CM-%'");
            jdbc.update("DELETE FROM categoria_producto WHERE nombre = 'Carga masiva'");
        }
    }

    @Test
    void laReordenSugiereSoloLoDeLaEmpresaYElBorradorNoCompraNada() throws Exception {
        Long centroA = centroDe(empresaA);
        Long centroUsuarioA = jdbc.queryForObject("SELECT centro_costo_id FROM usuario WHERE id = ?", Long.class, usuarioA);
        String solicitanteA = token(empresaA, usuarioA, "SOLICITANTE");
        String json = MediaType.APPLICATION_JSON_VALUE;
        try {
            // el stock mínimo enorme deja a los dos productos bajo el mínimo
            jdbc.update("UPDATE producto SET stock_minimo = 1000000, stock_maximo = 2000000 WHERE id IN (?, ?)",
                    productoA, productoB);
            jdbc.update("UPDATE usuario SET centro_costo_id = ? WHERE id = ?", centroA, usuarioA);

            String respuesta = mvc.perform(get("/api/reorden/sugerencias").header(HttpHeaders.AUTHORIZATION, solicitanteA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.productoId==" + productoB + ")]").isEmpty())
                    .andExpect(jsonPath("$[?(@.productoId==" + productoA + ")]").isNotEmpty())
                    .andReturn().getResponse().getContentAsString();
            var nodo = new com.fasterxml.jackson.databind.ObjectMapper().readTree(respuesta).get(0);
            java.math.BigDecimal maximo = new java.math.BigDecimal(nodo.get("stockMaximo").asText());
            java.math.BigDecimal esperado = maximo.subtract(new java.math.BigDecimal(nodo.get("stockActual").asText()))
                    .subtract(new java.math.BigDecimal(nodo.get("enOrdenes").asText()))
                    .subtract(new java.math.BigDecimal(nodo.get("enSolicitudes").asText()));
            assertEquals(0, esperado.compareTo(new java.math.BigDecimal(nodo.get("cantidadSugerida").asText())));
            assertTrue(esperado.signum() > 0);

            mvc.perform(get("/api/reorden/sugerencias").header(HttpHeaders.AUTHORIZATION,
                            token(empresaB, usuarioB, "COMPRADOR")))
                    .andExpect(jsonPath("$[?(@.productoId==" + productoA + ")]").isEmpty())
                    .andExpect(jsonPath("$[?(@.productoId==" + productoB + ")]").isNotEmpty());

            // el borrador queda en BORRADOR, del solicitante, con la cantidad sugerida
            String borrador = mvc.perform(post("/api/reorden/borrador").header(HttpHeaders.AUTHORIZATION, solicitanteA)
                            .contentType(json).content("{\"fechaRequerida\":\"2096-02-02\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.estado").value("BORRADOR"))
                    .andExpect(jsonPath("$.lineas[0].productoId").value(productoA))
                    .andReturn().getResponse().getContentAsString();
            var lineas = new com.fasterxml.jackson.databind.ObjectMapper().readTree(borrador).get("lineas");
            assertEquals(1, lineas.size());
            assertEquals(0, esperado.compareTo(new java.math.BigDecimal(lineas.get(0).get("cantidad").asText())));
            // un producto de otra empresa no se puede pedir
            mvc.perform(post("/api/reorden/borrador").header(HttpHeaders.AUTHORIZATION, solicitanteA).contentType(json)
                            .content("{\"fechaRequerida\":\"2096-02-02\",\"productoIds\":[" + productoB + "]}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("PRODUCTO_SIN_SUGERENCIA"));
        } finally {
            jdbc.update("UPDATE producto SET stock_minimo = 0, stock_maximo = NULL WHERE id IN (?, ?)", productoA, productoB);
            jdbc.update("UPDATE solicitud_compra SET estado = 'CANCELADA' WHERE centro_costo_id = ? "
                    + "AND estado IN ('BORRADOR', 'PENDIENTE_APROBACION', 'APROBADA', 'ADJUDICADA')", centroA);
            jdbc.update("UPDATE usuario SET centro_costo_id = ? WHERE id = ?", centroUsuarioA, usuarioA);
        }
    }

    // ------------------------------------------------------------------ consola de plataforma, planes y módulos (SaaS 2)

    private static final String CLAVE_PLATAFORMA = "ClavePlataforma2026";

    /** Crea un dueño de plataforma y entra con su contraseña y un código de MFA real; devuelve el header Authorization. */
    private String entrarALaPlataforma(String correo) throws Exception {
        EmpresaContexto.sinEmpresa(() -> {
            com.proyecto.supply_core.plataforma.entity.UsuarioPlataforma u =
                    new com.proyecto.supply_core.plataforma.entity.UsuarioPlataforma();
            u.setCorreo(correo);
            u.setNombre("Dueño de prueba");
            u.setHashPassword(passwordEncoder.encode(CLAVE_PLATAFORMA));
            return usuariosPlataforma.save(u);
        });
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        String paso1 = mvc.perform(post("/api/plataforma/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo + "\",\"password\":\"" + CLAVE_PLATAFORMA + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaConfigurado").value(false))
                .andReturn().getResponse().getContentAsString();
        String secreto = json.readTree(paso1).get("secretoMfa").asText();
        String desafio = json.readTree(paso1).get("desafio").asText();
        String codigo = totp.codigo(secreto, totp.paso(java.time.Instant.now()));
        String sesion = mvc.perform(post("/api/plataforma/auth/mfa").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"desafio\":\"" + desafio + "\",\"codigo\":\"" + codigo + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // el mismo código no se acepta dos veces
        mvc.perform(post("/api/plataforma/auth/mfa").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"desafio\":\"" + desafio + "\",\"codigo\":\"" + codigo + "\"}"))
                .andExpect(status().isUnauthorized());
        return "Bearer " + json.readTree(sesion).get("token").asText();
    }

    private void borrarDuenoDePrueba(String correo) {
        jdbc.update("DELETE FROM auditoria_plataforma WHERE usuario_plataforma_id IN "
                + "(SELECT id FROM usuario_plataforma WHERE correo = ?)", correo);
        jdbc.update("DELETE FROM usuario_plataforma WHERE correo = ?", correo);
    }

    private void borrarEmpresaDePrueba(Long id) {
        String deLaEmpresa = "(SELECT id FROM usuario WHERE empresa_id = ?)";
        jdbc.update("DELETE FROM refresh_token WHERE usuario_id IN " + deLaEmpresa, id);
        jdbc.update("DELETE FROM notificacion WHERE usuario_id IN " + deLaEmpresa, id);
        jdbc.update("DELETE FROM auditoria WHERE empresa_id = ? OR usuario_id IN " + deLaEmpresa, id, id);
        jdbc.update("DELETE FROM usuario_rol WHERE usuario_id IN " + deLaEmpresa, id);
        jdbc.update("DELETE FROM usuario WHERE empresa_id = ?", id);
        jdbc.update("DELETE FROM config_pesos_evaluacion_detalle WHERE config_pesos_evaluacion_id IN "
                + "(SELECT id FROM config_pesos_evaluacion WHERE empresa_id = ?)", id);
        jdbc.update("DELETE FROM config_pesos_evaluacion WHERE empresa_id = ?", id);
        jdbc.update("DELETE FROM empresa_modulo WHERE empresa_id = ?", id);
        jdbc.update("DELETE FROM suscripcion WHERE empresa_id = ?", id);
        jdbc.update("DELETE FROM empresa WHERE id = ?", id);
    }

    @Test
    void laPlataformaYLasEmpresasNoMezclanSusTokensNiLaPlataformaLeeDatosDeNegocio() throws Exception {
        String correo = "dueno-aislamiento@plataforma.test";
        try {
            String plataforma = entrarALaPlataforma(correo);

            mvc.perform(get("/api/plataforma/empresas").header(HttpHeaders.AUTHORIZATION, plataforma))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.codigoPublico=='demo')]").isNotEmpty())
                    .andExpect(jsonPath("$[?(@.codigoPublico=='empresa-b')]").isNotEmpty());
            // su token no entra a ninguna API de empresa: ni usuarios, ni productos, ni solicitudes
            for (String ruta : new String[] { "/api/usuarios", "/api/productos", "/api/solicitudes", "/api/empresa" }) {
                mvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, plataforma)).andExpect(status().isUnauthorized());
            }
            // y el token de un administrador de empresa no abre la consola
            for (String ruta : new String[] { "/api/plataforma/empresas", "/api/plataforma/planes",
                    "/api/plataforma/auditoria" }) {
                mvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "ADMIN_SISTEMA")))
                        .andExpect(status().isUnauthorized());
            }
            mvc.perform(get("/api/plataforma/empresas")).andExpect(status().isUnauthorized());
        } finally {
            borrarDuenoDePrueba(correo);
        }
    }

    @Test
    void elAltaDeUnaEmpresaCreaSuAdministradorYElPlanYElEstadoGobiernanLoQuePuedeHacer() throws Exception {
        String correo = "dueno-alta@plataforma.test";
        Long empresaC = null;
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        String contentType = MediaType.APPLICATION_JSON_VALUE;
        try {
            String plataforma = entrarALaPlataforma(correo);

            // alta: plan Básico, activa, con su administrador
            String alta = mvc.perform(post("/api/plataforma/empresas").header(HttpHeaders.AUTHORIZATION, plataforma)
                            .contentType(contentType).content("{\"razonSocial\":\"Empresa C S.A.S.\",\"nit\":\"901777888-1\","
                                    + "\"codigoPublico\":\"empresa-c-fuga\",\"planCodigo\":\"BASICO\",\"estadoInicial\":\"ACTIVA\","
                                    + "\"adminNombre\":\"Admin de C\",\"adminCorreo\":\"Admin@C.com\",\"adminPassword\":\"ClaveDeC2026\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.empresa.plan").value("BASICO"))
                    .andExpect(jsonPath("$.empresa.estado").value("ACTIVA"))
                    .andExpect(jsonPath("$.modulos[?(@.codigo=='COMPRAS')].activo").value(true))
                    .andExpect(jsonPath("$.modulos[?(@.codigo=='REPORTES')].activo").value(false))
                    .andReturn().getResponse().getContentAsString();
            empresaC = json.readTree(alta).get("empresa").get("id").asLong();
            String base = "/api/plataforma/empresas/" + empresaC;
            // NIT y código no se pueden repetir
            mvc.perform(post("/api/plataforma/empresas").header(HttpHeaders.AUTHORIZATION, plataforma)
                            .contentType(contentType).content("{\"razonSocial\":\"Otra\",\"nit\":\"901777888-1\","
                                    + "\"codigoPublico\":\"otra-c\",\"planCodigo\":\"BASICO\",\"adminNombre\":\"X\","
                                    + "\"adminCorreo\":\"x@x.com\",\"adminPassword\":\"ClaveDeC2026\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("NIT_DUPLICADO"));

            // el administrador entra con el código de su empresa y solo ve lo suyo
            String login = mvc.perform(post("/api/auth/login").contentType(contentType)
                            .content("{\"empresa\":\"empresa-c-fuga\",\"correo\":\"admin@c.com\",\"password\":\"ClaveDeC2026\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.usuario.roles[0]").value("ADMIN_SISTEMA"))
                    .andReturn().getResponse().getContentAsString();
            Long adminC = json.readTree(login).get("usuario").get("id").asLong();
            String refreshC = json.readTree(login).get("refreshToken").asText();
            String adminToken = "Bearer " + json.readTree(login).get("token").asText();
            mvc.perform(get("/api/usuarios").header(HttpHeaders.AUTHORIZATION, adminToken))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.contenido.length()").value(1));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM config_pesos_evaluacion WHERE empresa_id = ?",
                    Integer.class, empresaC));
            mvc.perform(get("/api/empresa/modulos").header(HttpHeaders.AUTHORIZATION, adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.plan").value("BASICO"))
                    .andExpect(jsonPath("$.modulos[?(@=='COMPRAS')]").isNotEmpty())
                    .andExpect(jsonPath("$.modulos[?(@=='REPORTES')]").isEmpty());

            // doble permiso: el módulo lo decide el dueño, el rol lo decide la empresa
            String auditorC = token(empresaC, adminC, "AUDITOR");
            String comprasC = token(empresaC, adminC, "ADMIN_COMPRAS");
            mvc.perform(get("/api/reportes/gasto").header(HttpHeaders.AUTHORIZATION, auditorC))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("MODULO_NO_CONTRATADO"));
            mvc.perform(get("/api/solicitudes").header(HttpHeaders.AUTHORIZATION, comprasC)).andExpect(status().isOk());
            // con el módulo pero sin el rol, tampoco
            mvc.perform(put(base + "/modulos/REPORTES").header(HttpHeaders.AUTHORIZATION, plataforma)
                            .contentType(contentType).content("{\"habilitado\":true}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.modulos[?(@.codigo=='REPORTES')].excepcion").value(true));
            mvc.perform(get("/api/reportes/gasto").header(HttpHeaders.AUTHORIZATION, auditorC)).andExpect(status().isOk());
            mvc.perform(get("/api/reportes/gasto").header(HttpHeaders.AUTHORIZATION, token(empresaC, adminC, "SOLICITANTE")))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
            // quitar la excepción vuelve a mandar el plan; cambiar de plan da el módulo
            mvc.perform(put(base + "/modulos/REPORTES").header(HttpHeaders.AUTHORIZATION, plataforma)
                            .contentType(contentType).content("{}"))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/reportes/gasto").header(HttpHeaders.AUTHORIZATION, auditorC)).andExpect(status().isForbidden());
            mvc.perform(put(base + "/plan").header(HttpHeaders.AUTHORIZATION, plataforma).contentType(contentType)
                            .content("{\"planCodigo\":\"PROFESIONAL\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.empresa.plan").value("PROFESIONAL"));
            mvc.perform(get("/api/reportes/gasto").header(HttpHeaders.AUTHORIZATION, auditorC)).andExpect(status().isOk());
            // quitarle a la empresa un módulo que su plan sí trae
            mvc.perform(put(base + "/modulos/COMPRAS").header(HttpHeaders.AUTHORIZATION, plataforma)
                            .contentType(contentType).content("{\"habilitado\":false}"))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/solicitudes").header(HttpHeaders.AUTHORIZATION, comprasC)).andExpect(status().isForbidden());
            mvc.perform(put(base + "/modulos/COMPRAS").header(HttpHeaders.AUTHORIZATION, plataforma)
                            .contentType(contentType).content("{}"))
                    .andExpect(status().isOk());

            // morosa: solo consulta
            mvc.perform(patch(base + "/estado").header(HttpHeaders.AUTHORIZATION, plataforma).contentType(contentType)
                            .content("{\"estado\":\"MOROSA\"}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(patch(base + "/estado").header(HttpHeaders.AUTHORIZATION, plataforma).contentType(contentType)
                            .content("{\"estado\":\"MOROSA\",\"motivo\":\"Debe dos meses\"}"))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/solicitudes").header(HttpHeaders.AUTHORIZATION, comprasC)).andExpect(status().isOk());
            mvc.perform(post("/api/solicitudes").header(HttpHeaders.AUTHORIZATION, token(empresaC, adminC, "SOLICITANTE"))
                            .contentType(contentType).content("{}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("SUSCRIPCION_MOROSA"));

            // suspendida: sin acceso, sin login y sin renovar la sesión; las demás empresas siguen igual
            mvc.perform(patch(base + "/estado").header(HttpHeaders.AUTHORIZATION, plataforma).contentType(contentType)
                            .content("{\"estado\":\"SUSPENDIDA\",\"motivo\":\"Sin pago\"}"))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/solicitudes").header(HttpHeaders.AUTHORIZATION, comprasC))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("SUSCRIPCION_SUSPENDIDA"));
            mvc.perform(post("/api/auth/login").contentType(contentType)
                            .content("{\"empresa\":\"empresa-c-fuga\",\"correo\":\"admin@c.com\",\"password\":\"ClaveDeC2026\"}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("SUSCRIPCION_SUSPENDIDA"));
            mvc.perform(post("/api/auth/refresh").contentType(contentType).content("{\"refreshToken\":\"" + refreshC + "\"}"))
                    .andExpect(status().is4xxClientError());
            mvc.perform(get("/api/solicitudes").header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "ADMIN_COMPRAS")))
                    .andExpect(status().isOk());
            // la clave mala no revela que está suspendida
            mvc.perform(post("/api/auth/login").contentType(contentType)
                            .content("{\"empresa\":\"empresa-c-fuga\",\"correo\":\"admin@c.com\",\"password\":\"Mala123456\"}"))
                    .andExpect(status().isUnauthorized());

            // reactivar la devuelve a la normalidad
            mvc.perform(patch(base + "/estado").header(HttpHeaders.AUTHORIZATION, plataforma).contentType(contentType)
                            .content("{\"estado\":\"ACTIVA\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.empresa.motivoEstado").doesNotExist());
            mvc.perform(get("/api/solicitudes").header(HttpHeaders.AUTHORIZATION, comprasC)).andExpect(status().isOk());
            mvc.perform(post("/api/auth/login").contentType(contentType)
                            .content("{\"empresa\":\"empresa-c-fuga\",\"correo\":\"admin@c.com\",\"password\":\"ClaveDeC2026\"}"))
                    .andExpect(status().isOk());

            // cancelar es final
            mvc.perform(patch(base + "/estado").header(HttpHeaders.AUTHORIZATION, plataforma).contentType(contentType)
                            .content("{\"estado\":\"CANCELADA\",\"motivo\":\"Cerró el negocio\"}"))
                    .andExpect(status().isOk());
            mvc.perform(patch(base + "/estado").header(HttpHeaders.AUTHORIZATION, plataforma).contentType(contentType)
                            .content("{\"estado\":\"ACTIVA\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));

            // todo quedó en la auditoría global, con la empresa afectada
            mvc.perform(get("/api/plataforma/auditoria?empresaId=" + empresaC).header(HttpHeaders.AUTHORIZATION, plataforma))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido[?(@.accion=='EMPRESA_CREAR')]").isNotEmpty())
                    .andExpect(jsonPath("$.contenido[?(@.accion=='EMPRESA_MODULO')]").isNotEmpty())
                    .andExpect(jsonPath("$.contenido[?(@.accion=='EMPRESA_CAMBIAR_PLAN')]").isNotEmpty())
                    .andExpect(jsonPath("$.contenido[?(@.accion=='EMPRESA_ESTADO')]").isNotEmpty());
        } finally {
            if (empresaC != null) {
                borrarEmpresaDePrueba(empresaC);
            }
            borrarDuenoDePrueba(correo);
        }
    }

    private com.fasterxml.jackson.databind.JsonNode entrarComo(String empresa, String correo, String clave) throws Exception {
        String cuerpo = "{\"empresa\":\"" + empresa + "\",\"correo\":\"" + correo + "\",\"password\":\"" + clave + "\"}";
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void cambiarLaClaveODesactivarCortanElJwtEnCursoYLosRefreshTokensAlInstante() throws Exception {
        String ct = MediaType.APPLICATION_JSON_VALUE;
        Long victima = usuarioInternoDeA("corte@mismo.com", "COMPRADOR");
        String admin = token(empresaA, usuarioA, "ADMIN_SISTEMA");
        try {
            // dos dispositivos con sesión abierta
            var s1 = entrarComo("demo", "corte@mismo.com", CLAVE);
            var s2 = entrarComo("demo", "corte@mismo.com", CLAVE);
            String t1 = "Bearer " + s1.get("token").asText();
            String t2 = "Bearer " + s2.get("token").asText();
            mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, t1)).andExpect(status().isOk());

            // el admin restablece la clave: los dos JWT y los dos refresh dejan de servir enseguida
            mvc.perform(put("/api/usuarios/" + victima + "/password").header(HttpHeaders.AUTHORIZATION, admin)
                            .contentType(ct).content("{\"passwordNueva\":\"NuevaClave2026\"}"))
                    .andExpect(status().is2xxSuccessful());
            mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, t1)).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, t2)).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/auth/refresh").contentType(ct)
                            .content("{\"refreshToken\":\"" + s1.get("refreshToken").asText() + "\"}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/auth/login").contentType(ct)
                            .content("{\"empresa\":\"demo\",\"correo\":\"corte@mismo.com\",\"password\":\"" + CLAVE + "\"}"))
                    .andExpect(status().isUnauthorized());

            // con la clave nueva entra y su token sirve
            var s3 = entrarComo("demo", "corte@mismo.com", "NuevaClave2026");
            String t3 = "Bearer " + s3.get("token").asText();
            mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, t3)).andExpect(status().isOk());

            // desactivarlo corta su sesión al instante; reactivarlo no revive la sesión vieja
            mvc.perform(patch("/api/usuarios/" + victima + "/desactivar").header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, t3)).andExpect(status().isUnauthorized());
            mvc.perform(patch("/api/usuarios/" + victima + "/activar").header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, t3)).andExpect(status().isUnauthorized());

            // cambiar su propia clave también cierra todas sus sesiones, la actual incluida
            String t4 = "Bearer " + entrarComo("demo", "corte@mismo.com", "NuevaClave2026").get("token").asText();
            mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, t4)).andExpect(status().isOk());
            mvc.perform(put("/api/perfil/password").header(HttpHeaders.AUTHORIZATION, t4).contentType(ct)
                            .content("{\"passwordActual\":\"NuevaClave2026\",\"passwordNueva\":\"OtraClave2026\"}"))
                    .andExpect(status().isNoContent());
            mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, t4)).andExpect(status().isUnauthorized());
            entrarComo("demo", "corte@mismo.com", "OtraClave2026");

            // la otra empresa no se entera: su admin sigue entrando con su token
            mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, token(empresaB, usuarioB, "ADMIN_SISTEMA")))
                    .andExpect(status().isOk());
        } finally {
            jdbc.update("DELETE FROM refresh_token WHERE usuario_id = ?", victima);
            jdbc.update("DELETE FROM notificacion WHERE usuario_id = ?", victima);
            jdbc.update("DELETE FROM auditoria WHERE usuario_id = ?", victima);
            jdbc.update("DELETE FROM usuario_rol WHERE usuario_id = ?", victima);
            jdbc.update("DELETE FROM usuario WHERE id = ?", victima);
        }
    }

    private void borrarUsuarioDePrueba(Long id) {
        jdbc.update("DELETE FROM recuperacion_password WHERE usuario_id = ?", id);
        jdbc.update("DELETE FROM refresh_token WHERE usuario_id = ?", id);
        jdbc.update("DELETE FROM notificacion WHERE usuario_id = ?", id);
        jdbc.update("DELETE FROM auditoria WHERE usuario_id = ?", id);
        jdbc.update("DELETE FROM usuario_rol WHERE usuario_id = ?", id);
        jdbc.update("DELETE FROM usuario WHERE id = ?", id);
    }

    @Test
    void recuperarLaClaveConUnTokenDeUnSoloUsoCortaLasSesionesYNoCruzaEmpresas() throws Exception {
        String ct = MediaType.APPLICATION_JSON_VALUE;
        Long usuario = usuarioInternoDeA("recupera@mismo.com", "COMPRADOR");
        org.mockito.Mockito.clearInvocations(emailClient);
        try {
            var sesion = entrarComo("demo", "recupera@mismo.com", CLAVE);
            String t1 = "Bearer " + sesion.get("token").asText();

            // responde lo mismo exista o no el correo o la empresa
            String respuesta = mvc.perform(post("/api/auth/recuperar").contentType(ct)
                            .content("{\"empresa\":\"demo\",\"correo\":\"recupera@mismo.com\"}"))
                    .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
            assertEquals(respuesta, mvc.perform(post("/api/auth/recuperar").contentType(ct)
                            .content("{\"empresa\":\"demo\",\"correo\":\"nadie@mismo.com\"}"))
                    .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
            assertEquals(respuesta, mvc.perform(post("/api/auth/recuperar").contentType(ct)
                            .content("{\"empresa\":\"no-existe\",\"correo\":\"recupera@mismo.com\"}"))
                    .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());

            // solo salió un correo, con el valor en claro; en la base queda únicamente su hash
            org.mockito.ArgumentCaptor<String> cuerpo = org.mockito.ArgumentCaptor.forClass(String.class);
            org.mockito.Mockito.verify(emailClient, org.mockito.Mockito.times(1))
                    .enviar(org.mockito.ArgumentMatchers.eq("recupera@mismo.com"), org.mockito.ArgumentMatchers.anyString(),
                            cuerpo.capture());
            String texto = cuerpo.getValue();
            int desde = texto.indexOf("es: ") + 4;
            String token = texto.substring(desde, texto.indexOf(" .", desde));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM recuperacion_password WHERE token_hash = ?",
                    Integer.class, token));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM recuperacion_password WHERE usuario_id = ?",
                    Integer.class, usuario));

            // pedirlo otra vez enseguida no manda otro correo
            mvc.perform(post("/api/auth/recuperar").contentType(ct)
                            .content("{\"empresa\":\"demo\",\"correo\":\"recupera@mismo.com\"}"))
                    .andExpect(status().isAccepted());
            org.mockito.Mockito.verify(emailClient, org.mockito.Mockito.times(1)).enviar(
                    org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString());

            // el token de una empresa no sirve en la otra ni con una clave débil
            mvc.perform(post("/api/auth/restablecer").contentType(ct)
                            .content("{\"empresa\":\"empresa-b\",\"token\":\"" + token + "\",\"passwordNueva\":\"NuevaClave2026\"}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/auth/restablecer").contentType(ct)
                            .content("{\"empresa\":\"demo\",\"token\":\"" + token + "\",\"passwordNueva\":\"corta\"}"))
                    .andExpect(status().isBadRequest());

            // en su empresa sí: cambia la clave y corta la sesión que había
            mvc.perform(post("/api/auth/restablecer").contentType(ct)
                            .content("{\"empresa\":\"demo\",\"token\":\"" + token + "\",\"passwordNueva\":\"NuevaClave2026\"}"))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, t1)).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/auth/refresh").contentType(ct)
                            .content("{\"refreshToken\":\"" + sesion.get("refreshToken").asText() + "\"}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/auth/login").contentType(ct)
                            .content("{\"empresa\":\"demo\",\"correo\":\"recupera@mismo.com\",\"password\":\"" + CLAVE + "\"}"))
                    .andExpect(status().isUnauthorized());
            entrarComo("demo", "recupera@mismo.com", "NuevaClave2026");

            // sirve una sola vez
            mvc.perform(post("/api/auth/restablecer").contentType(ct)
                            .content("{\"empresa\":\"demo\",\"token\":\"" + token + "\",\"passwordNueva\":\"OtraClave2026\"}"))
                    .andExpect(status().isUnauthorized());

            // vencido tampoco: se reabre el token y se le adelanta la fecha de vencimiento
            jdbc.update("UPDATE recuperacion_password SET usado_en = NULL, expira_en = ? WHERE usuario_id = ?",
                    java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusMinutes(1)), usuario);
            mvc.perform(post("/api/auth/restablecer").contentType(ct)
                            .content("{\"empresa\":\"demo\",\"token\":\"" + token + "\",\"passwordNueva\":\"OtraClave2026\"}"))
                    .andExpect(status().isUnauthorized());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM auditoria WHERE usuario_id = ? AND accion = "
                    + "'PASSWORD_RECUPERAR_USAR'", Integer.class, usuario));
        } finally {
            borrarUsuarioDePrueba(usuario);
        }
    }

    @Test
    void elMfaVoluntarioExigeElCodigoAlEntrarYUnAdminPuedeReiniciarlo() throws Exception {
        String ct = MediaType.APPLICATION_JSON_VALUE;
        Long usuario = usuarioInternoDeA("mfa@mismo.com", "COMPRADOR");
        String admin = token(empresaA, usuarioA, "ADMIN_SISTEMA");
        try {
            // sin MFA entra directo y puede vincular su app
            var sesion = entrarComo("demo", "mfa@mismo.com", CLAVE);
            String t0 = "Bearer " + sesion.get("token").asText();
            var json = new com.fasterxml.jackson.databind.ObjectMapper();
            String secreto = json.readTree(mvc.perform(post("/api/auth/mfa/configurar").header(HttpHeaders.AUTHORIZATION, t0))
                            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                    .get("secretoMfa").asText();
            mvc.perform(post("/api/auth/mfa/activar").header(HttpHeaders.AUTHORIZATION, t0).contentType(ct)
                            .content("{\"codigo\":\"000000\"}"))
                    .andExpect(status().is4xxClientError());
            long paso = totp.paso(java.time.Instant.now());
            mvc.perform(post("/api/auth/mfa/activar").header(HttpHeaders.AUTHORIZATION, t0).contentType(ct)
                            .content("{\"codigo\":\"" + totp.codigo(secreto, paso) + "\"}"))
                    .andExpect(status().isNoContent());

            // desde ahora el login da un desafío y no una sesión
            var desafio = json.readTree(mvc.perform(post("/api/auth/login").contentType(ct)
                            .content("{\"empresa\":\"demo\",\"correo\":\"mfa@mismo.com\",\"password\":\"" + CLAVE + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mfaConfigurado").value(true))
                    .andExpect(jsonPath("$.token").doesNotExist())
                    .andExpect(jsonPath("$.secretoMfa").doesNotExist())
                    .andReturn().getResponse().getContentAsString());
            String d = desafio.get("desafioMfa").asText();

            // un código malo, uno ya usado y un token de sesión como desafío no abren nada
            mvc.perform(post("/api/auth/mfa").contentType(ct)
                            .content("{\"desafio\":\"" + d + "\",\"codigo\":\"000000\"}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/auth/mfa").contentType(ct)
                            .content("{\"desafio\":\"" + d + "\",\"codigo\":\"" + totp.codigo(secreto, paso) + "\"}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/auth/mfa").contentType(ct)
                            .content("{\"desafio\":\"" + t0.substring(7) + "\",\"codigo\":\"" + totp.codigo(secreto, paso + 1) + "\"}"))
                    .andExpect(status().isUnauthorized());

            // el código bueno y nuevo abre la sesión
            String sesionMfa = json.readTree(mvc.perform(post("/api/auth/mfa").contentType(ct)
                            .content("{\"desafio\":\"" + d + "\",\"codigo\":\"" + totp.codigo(secreto, paso + 1) + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.refreshToken").isNotEmpty())
                    .andReturn().getResponse().getContentAsString()).get("token").asText();
            mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + sesionMfa))
                    .andExpect(status().isOk());

            // un administrador le reinicia el MFA: se corta su sesión y vuelve a entrar sin segundo factor
            mvc.perform(patch("/api/usuarios/" + usuario + "/mfa/reiniciar").header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isNoContent());
            mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + sesionMfa))
                    .andExpect(status().isUnauthorized());
            entrarComo("demo", "mfa@mismo.com", CLAVE);

            // no se puede reiniciar el propio y la otra empresa no ve a este usuario
            mvc.perform(patch("/api/usuarios/" + usuarioA + "/mfa/reiniciar").header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().is4xxClientError());
            mvc.perform(patch("/api/usuarios/" + usuario + "/mfa/reiniciar")
                            .header(HttpHeaders.AUTHORIZATION, token(empresaB, usuarioB, "ADMIN_SISTEMA")))
                    .andExpect(status().isNotFound());
        } finally {
            borrarUsuarioDePrueba(usuario);
        }
    }

    @Test
    void unCodigoDeIngresoLibreFuncionaEnElLoginYElRegistroYLaConsolaMuestraSuAdministrador() throws Exception {
        String correo = "dueno-codigo@plataforma.test";
        Long empresaD = null;
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        String ct = MediaType.APPLICATION_JSON_VALUE;
        try {
            String plataforma = entrarALaPlataforma(correo);

            // el código se guarda como se escribió (mayúsculas, tildes), solo sin espacios de más
            String alta = mvc.perform(post("/api/plataforma/empresas").header(HttpHeaders.AUTHORIZATION, plataforma)
                            .contentType(ct).content("{\"razonSocial\":\"Ferretería Don Martín S.A.S.\",\"nit\":\"901888999-2\","
                                    + "\"codigoPublico\":\"  Ferretería   Don Martín \",\"planCodigo\":\"BASICO\","
                                    + "\"estadoInicial\":\"ACTIVA\",\"adminNombre\":\"Martín Pérez\","
                                    + "\"adminCorreo\":\"Admin@D.com\",\"adminPassword\":\"ClaveDeD2026\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.empresa.codigoPublico").value("Ferretería Don Martín"))
                    .andExpect(jsonPath("$.administrador.nombre").value("Martín Pérez"))
                    .andExpect(jsonPath("$.administrador.correo").value("admin@d.com"))
                    .andReturn().getResponse().getContentAsString();
            empresaD = json.readTree(alta).get("empresa").get("id").asLong();

            // la misma empresa con otras mayúsculas ya existe; un código con barra no se acepta
            mvc.perform(post("/api/plataforma/empresas").header(HttpHeaders.AUTHORIZATION, plataforma)
                            .contentType(ct).content("{\"razonSocial\":\"Otra\",\"nit\":\"901888111-3\","
                                    + "\"codigoPublico\":\"FERRETERÍA DON MARTÍN\",\"planCodigo\":\"BASICO\","
                                    + "\"adminNombre\":\"X\",\"adminCorreo\":\"x@x.com\",\"adminPassword\":\"ClaveDeD2026\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CODIGO_DUPLICADO"));
            mvc.perform(post("/api/plataforma/empresas").header(HttpHeaders.AUTHORIZATION, plataforma)
                            .contentType(ct).content("{\"razonSocial\":\"Otra\",\"nit\":\"901888222-4\","
                                    + "\"codigoPublico\":\"con/barra\",\"planCodigo\":\"BASICO\","
                                    + "\"adminNombre\":\"X\",\"adminCorreo\":\"y@x.com\",\"adminPassword\":\"ClaveDeD2026\"}"))
                    .andExpect(status().isBadRequest());

            // el administrador entra escribiendo el código tal cual o con otras mayúsculas
            for (String escrito : new String[] { "Ferretería Don Martín", "ferretería don martín" }) {
                mvc.perform(post("/api/auth/login").contentType(ct)
                                .content("{\"empresa\":\"" + escrito + "\",\"correo\":\"admin@d.com\",\"password\":\"ClaveDeD2026\"}"))
                        .andExpect(status().isOk());
            }

            // el registro de proveedores resuelve la empresa aunque el código llegue codificado en la dirección
            mvc.perform(post("/api/portal/registro/{empresa}", "Ferretería Don Martín").contentType(ct).content("{}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(post("/api/portal/registro/{empresa}", "No Existe Esta Empresa").contentType(ct).content("{}"))
                    .andExpect(status().isNotFound());

            // la consola muestra el administrador de cada empresa, sin mezclarlos
            mvc.perform(get("/api/plataforma/empresas/" + empresaD).header(HttpHeaders.AUTHORIZATION, plataforma))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.administrador.correo").value("admin@d.com"));
            mvc.perform(get("/api/plataforma/empresas/" + empresaA).header(HttpHeaders.AUTHORIZATION, plataforma))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.administrador.correo").value("admin@mismo.com"));
        } finally {
            if (empresaD != null) {
                borrarEmpresaDePrueba(empresaD);
            }
            borrarDuenoDePrueba(correo);
        }
    }
}
