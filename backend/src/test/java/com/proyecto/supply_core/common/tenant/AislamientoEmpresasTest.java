package com.proyecto.supply_core.common.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void losCandidatosDeDelegacionSoloSonLosUsuariosDeLaMismaEmpresa() throws Exception {
        Long otroDeA = usuarioInternoDeA("candidato.delegacion@a.com", "COMPRADOR");
        crearPoliticaSolicitud(empresaA, "ADMIN_COMPRAS", "0", "1000000");      // sin una política que apruebe no se puede delegar
        try {
            mvc.perform(get("/api/delegaciones/candidatos")
                            .header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "ADMIN_COMPRAS")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.id == " + otroDeA + ")]").isNotEmpty())   // un usuario de su empresa sí
                    .andExpect(jsonPath("$[?(@.id == " + usuarioB + ")]").isEmpty())     // uno de la otra empresa no
                    .andExpect(jsonPath("$[?(@.id == " + usuarioA + ")]").isEmpty());    // y no se ofrece a sí mismo
            // la empresa B no tiene ninguna política que apruebe: no puede ver candidatos
            mvc.perform(get("/api/delegaciones/candidatos")
                            .header(HttpHeaders.AUTHORIZATION, token(empresaB, usuarioB, "ADMIN_COMPRAS")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("SIN_ROL_APROBADOR"));
        } finally {
            jdbc.update("UPDATE politica_aprobacion SET activo = 0 WHERE empresa_id = ?", empresaA);
            jdbc.update("DELETE FROM usuario_rol WHERE usuario_id = ?", otroDeA);
            jdbc.update("DELETE FROM usuario WHERE id = ?", otroDeA);
        }
    }

    @Test
    void losInformesDeComprasSonDeCadaEmpresa() throws Exception {
        jdbc.update("INSERT INTO informe_compras (empresa_id, estado, periodo_desde, periodo_hasta, mensaje_pedido, "
                + "pedido_por_usuario_id, pedido_en) VALUES (?, 'PEDIDO', '2026-11-01', '2026-11-30', 'Informe de B', ?, NOW())",
                empresaB, usuarioB);
        Long informeB = jdbc.queryForObject("SELECT MAX(id) FROM informe_compras WHERE empresa_id = ?", Long.class, empresaB);
        String jefeA = token(empresaA, usuarioA, "ADMIN_COMPRAS");
        String duenoA = token(empresaA, usuarioA2, "ADMIN_SISTEMA");
        try {
            // A no ve, no lee y no responde el pedido de B
            mvc.perform(get("/api/informes-compras").header(HttpHeaders.AUTHORIZATION, jefeA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido[?(@.id == " + informeB + ")]").isEmpty());
            mvc.perform(get("/api/informes-compras/" + informeB).header(HttpHeaders.AUTHORIZATION, jefeA))
                    .andExpect(status().isNotFound());
            mvc.perform(post("/api/informes-compras/" + informeB + "/leido").header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(status().isNotFound());
            mvc.perform(post("/api/informes-compras").header(HttpHeaders.AUTHORIZATION, jefeA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"desde\":\"2026-11-01\",\"hasta\":\"2026-11-30\",\"analisis\":\"Responde un pedido ajeno\","
                                    + "\"pedidoId\":" + informeB + "}"))
                    .andExpect(status().isNotFound());

            // el dueño de A pide y su jefe de compras le responde: queda en A y el pedido de B sigue intacto
            String pedido = mvc.perform(post("/api/informes-compras/pedir").header(HttpHeaders.AUTHORIZATION, duenoA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"desde\":\"2026-11-01\",\"hasta\":\"2026-11-30\",\"mensaje\":\"Como vamos\"}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            Long informeA = Long.valueOf(pedido.replaceAll(".*\"id\":(\\d+).*", "$1"));
            mvc.perform(post("/api/informes-compras").header(HttpHeaders.AUTHORIZATION, jefeA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"desde\":\"2026-11-01\",\"hasta\":\"2026-11-30\",\"analisis\":\"Todo en orden este mes\","
                                    + "\"pedidoId\":" + informeA + "}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.estado").value("ENVIADO"))
                    .andExpect(jsonPath("$.contenido.resumen.solicitudes").isNumber());
            assertEquals(empresaA, jdbc.queryForObject("SELECT empresa_id FROM informe_compras WHERE id = ?", Long.class, informeA));
            assertEquals("PEDIDO", jdbc.queryForObject("SELECT estado FROM informe_compras WHERE id = ?", String.class, informeB));
            // el jefe de compras no pide ni marca leído; el dueño no envía
            mvc.perform(post("/api/informes-compras/" + informeA + "/leido").header(HttpHeaders.AUTHORIZATION, jefeA))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/informes-compras/vista-previa?desde=2026-11-01&hasta=2026-11-30")
                            .header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(status().isForbidden());
        } finally {
            jdbc.update("DELETE FROM notificacion WHERE evento IN ('INFORME_PEDIDO', 'INFORME_RECIBIDO')");
            jdbc.update("DELETE FROM informe_compras WHERE empresa_id IN (?, ?)", empresaA, empresaB);
        }
    }

    @Test
    void lasCategoriasQueVeUnProveedorSonSoloLasDeSuEmpresa() throws Exception {
        mvc.perform(get("/api/portal/proveedor/categorias").header(HttpHeaders.AUTHORIZATION, tokenProveedor(empresaA, usuarioA, proveedorA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + categoriaB + ")]").isEmpty())
                .andExpect(jsonPath("$[?(@.id == " + categoriaA + ")]").isNotEmpty());
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

    @Test
    void elPlanDeReabastecimientoSoloUsaElConsumoYLosProductosDeLaEmpresa() throws Exception {
        try {
            // la empresa B consume mucho de su producto; la A no debe verlo ni recibir sus avisos
            jdbc.update("INSERT INTO movimiento_inventario (empresa_id, producto_id, bodega_origen_id, usuario_id, tipo, "
                    + "cantidad, stock_resultante, creado_en) VALUES (?, ?, ?, ?, 'SALIDA_CONSUMO', 900, 0, NOW())",
                    empresaB, productoB, bodegaB, usuarioB);
            mvc.perform(get("/api/reabastecimiento").header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "COMPRADOR")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[?(@.productoId==" + productoB + ")]").isEmpty())
                    .andExpect(jsonPath("$.items[?(@.productoId==" + productoA + ")].consumoDiario").value(0.0));
            mvc.perform(get("/api/reabastecimiento").header(HttpHeaders.AUTHORIZATION, token(empresaB, usuarioB, "COMPRADOR")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[?(@.productoId==" + productoA + ")]").isEmpty())
                    // otras pruebas de esta clase también dejan salidas en B: basta con que tenga consumo
                    .andExpect(jsonPath("$.items[?(@.productoId==" + productoB + " && @.consumoDiario >= 10)]").isNotEmpty());
            // los avisos: B apaga un correo; A sigue viendo todos sus avisos activos
            String adminB = token(empresaB, usuarioB, "ADMIN_SISTEMA");
            mvc.perform(put("/api/config-notificaciones").header(HttpHeaders.AUTHORIZATION, adminB)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"evento\":\"REABASTECIMIENTO_DIARIO\",\"canal\":\"EMAIL\",\"activo\":false}"))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/config-notificaciones").header(HttpHeaders.AUTHORIZATION, adminB))
                    .andExpect(jsonPath("$[?(@.evento=='REABASTECIMIENTO_DIARIO' && @.canal=='EMAIL')].activo").value(false));
            mvc.perform(get("/api/config-notificaciones").header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "ADMIN_SISTEMA")))
                    .andExpect(jsonPath("$[?(@.evento=='REABASTECIMIENTO_DIARIO' && @.canal=='EMAIL')].activo").value(true));
            // el tablero del dueño tampoco mezcla empresas
            mvc.perform(get("/api/reportes/tablero").header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "ADMIN_SISTEMA")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.masMovidos").isEmpty())
                    .andExpect(jsonPath("$.gastoPorMes.length()").value(12));
            mvc.perform(get("/api/reportes/tablero").header(HttpHeaders.AUTHORIZATION, token(empresaB, usuarioB, "ADMIN_SISTEMA")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.masMovidos[0].productoId").value(productoB));
            mvc.perform(get("/api/reportes/tablero").header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "COMPRADOR")))
                    .andExpect(status().isForbidden());

            // el plazo propio del producto de B no se puede cambiar desde A
            String adminComprasA = token(empresaA, usuarioA, "ADMIN_COMPRAS");
            mvc.perform(patch("/api/productos/" + productoB + "/plazo").header(HttpHeaders.AUTHORIZATION, adminComprasA)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"plazoEntregaDias\":7,\"diasSeguridad\":1}"))
                    .andExpect(status().isNotFound());
            mvc.perform(patch("/api/productos/" + productoA + "/plazo").header(HttpHeaders.AUTHORIZATION, adminComprasA)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"plazoEntregaDias\":7,\"diasSeguridad\":1}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.plazoEntregaDias").value(7));
            mvc.perform(get("/api/reabastecimiento").header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "COMPRADOR")))
                    .andExpect(jsonPath("$.items[?(@.productoId==" + productoA + ")].plazoEntregaDias").value(7))
                    .andExpect(jsonPath("$.items[?(@.productoId==" + productoA + ")].plazoPropio").value(true));
            assertEquals(null, jdbc.queryForObject("SELECT plazo_entrega_dias FROM producto WHERE id = ?", Integer.class,
                    productoB));
        } finally {
            jdbc.update("DELETE FROM movimiento_inventario WHERE empresa_id = ? AND cantidad = 900", empresaB);
            jdbc.update("DELETE FROM config_notificacion WHERE evento = 'REABASTECIMIENTO_DIARIO'");
            jdbc.update("UPDATE producto SET plazo_entrega_dias = NULL, dias_seguridad = NULL WHERE id IN (?, ?)",
                    productoA, productoB);
        }
    }

    @Test
    void losReportesPorSedeSoloUsanLasBodegasDeEsaSedeYNoAceptanSedesAjenas() throws Exception {
        Long sedeA = jdbc.queryForObject("SELECT sede_id FROM bodega WHERE id = ?", Long.class, bodegaA);
        Long sedeB = jdbc.queryForObject("SELECT sede_id FROM bodega WHERE id = ?", Long.class, bodegaB);
        jdbc.update("INSERT INTO sede (empresa_id, nombre) VALUES (?, 'Sede norte')", empresaA);
        Long sedeNorte = jdbc.queryForObject("SELECT MAX(id) FROM sede WHERE empresa_id = ?", Long.class, empresaA);
        jdbc.update("INSERT INTO bodega (empresa_id, sede_id, nombre) VALUES (?, ?, 'Bodega norte')", empresaA, sedeNorte);
        Long bodegaNorte = jdbc.queryForObject("SELECT MAX(id) FROM bodega WHERE sede_id = ?", Long.class, sedeNorte);
        String compradorA = token(empresaA, usuarioA, "COMPRADOR");
        String adminA = token(empresaA, usuarioA, "ADMIN_SISTEMA");
        try {
            // en la sede norte hay 20 en stock y salieron 45
            jdbc.update("INSERT INTO stock_producto_bodega (empresa_id, producto_id, bodega_id, cantidad) VALUES (?, ?, ?, 20)",
                    empresaA, productoA, bodegaNorte);
            jdbc.update("INSERT INTO movimiento_inventario (empresa_id, producto_id, bodega_origen_id, usuario_id, tipo, "
                    + "cantidad, stock_resultante, creado_en) VALUES (?, ?, ?, ?, 'SALIDA_CONSUMO', 45, 20, NOW())",
                    empresaA, productoA, bodegaNorte, usuarioA);

            mvc.perform(get("/api/reportes/rotacion?sedeId=" + sedeNorte).header(HttpHeaders.AUTHORIZATION, adminA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].productoId").value(productoA))
                    .andExpect(jsonPath("$.items[0].salidas").value(45.0))
                    .andExpect(jsonPath("$.items[0].stock").value(20.0));
            // la sede principal no ve lo de la norte
            mvc.perform(get("/api/reportes/rotacion?sedeId=" + sedeA).header(HttpHeaders.AUTHORIZATION, adminA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].salidas").value(0.0));
            mvc.perform(get("/api/reabastecimiento?sedeId=" + sedeNorte).header(HttpHeaders.AUTHORIZATION, compradorA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].consumoDiario").value(0.5))
                    .andExpect(jsonPath("$.items[0].stockMinimo").value(0.0));
            mvc.perform(get("/api/reportes/gasto?sedeId=" + sedeNorte).header(HttpHeaders.AUTHORIZATION, compradorA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ordenes").value(0));
            mvc.perform(get("/api/reportes/tablero?sedeId=" + sedeNorte).header(HttpHeaders.AUTHORIZATION, adminA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sedeId").value(sedeNorte))
                    .andExpect(jsonPath("$.masMovidos[0].salidas").value(45.0));
            mvc.perform(get("/api/reportes/tablero").header(HttpHeaders.AUTHORIZATION, adminA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.gastoPorSede").isArray());

            // la sede de la otra empresa no existe para A
            mvc.perform(get("/api/reportes/rotacion?sedeId=" + sedeB).header(HttpHeaders.AUTHORIZATION, adminA))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/reabastecimiento?sedeId=" + sedeB).header(HttpHeaders.AUTHORIZATION, compradorA))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/reportes/gasto?sedeId=" + sedeB).header(HttpHeaders.AUTHORIZATION, compradorA))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/reportes/tablero?sedeId=" + sedeB).header(HttpHeaders.AUTHORIZATION, adminA))
                    .andExpect(status().isNotFound());
        } finally {
            jdbc.update("DELETE FROM movimiento_inventario WHERE bodega_origen_id = ?", bodegaNorte);
            jdbc.update("DELETE FROM stock_producto_bodega WHERE bodega_id = ?", bodegaNorte);
            jdbc.update("DELETE FROM bodega WHERE id = ?", bodegaNorte);
            jdbc.update("DELETE FROM sede WHERE id = ?", sedeNorte);
        }
    }

    @Test
    void cadaSedeTieneSusLimitesYElPlanSugiereTrasladarDesdeLaQueLeSobra() throws Exception {
        Long sedeA = jdbc.queryForObject("SELECT sede_id FROM bodega WHERE id = ?", Long.class, bodegaA);
        Long sedeB = jdbc.queryForObject("SELECT sede_id FROM bodega WHERE id = ?", Long.class, bodegaB);
        jdbc.update("INSERT INTO sede (empresa_id, nombre) VALUES (?, 'Sede sur')", empresaA);
        Long sedeSur = jdbc.queryForObject("SELECT MAX(id) FROM sede WHERE empresa_id = ?", Long.class, empresaA);
        jdbc.update("INSERT INTO bodega (empresa_id, sede_id, nombre) VALUES (?, ?, 'Bodega sur')", empresaA, sedeSur);
        Long bodegaSur = jdbc.queryForObject("SELECT MAX(id) FROM bodega WHERE sede_id = ?", Long.class, sedeSur);
        Long stockPrincipal = jdbc.queryForObject("SELECT COALESCE(SUM(cantidad), 0) FROM stock_producto_bodega "
                + "WHERE producto_id = ? AND bodega_id = ?", Long.class, productoA, bodegaA);
        String json = MediaType.APPLICATION_JSON_VALUE;
        String comprasA = token(empresaA, usuarioA, "ADMIN_COMPRAS");
        try {
            // la sede sur tiene 500 y no vende; la principal vende 90 en el período y tiene poco
            jdbc.update("INSERT INTO stock_producto_bodega (empresa_id, producto_id, bodega_id, cantidad) VALUES (?, ?, ?, 500)",
                    empresaA, productoA, bodegaSur);
            jdbc.update("INSERT INTO movimiento_inventario (empresa_id, producto_id, bodega_origen_id, usuario_id, tipo, "
                    + "cantidad, stock_resultante, creado_en) VALUES (?, ?, ?, ?, 'SALIDA_CONSUMO', 90, 0, NOW())",
                    empresaA, productoA, bodegaA, usuarioA);

            // límites propios de la sede principal
            mvc.perform(put("/api/productos/" + productoA + "/sedes/" + sedeA).header(HttpHeaders.AUTHORIZATION, comprasA)
                            .contentType(json).content("{\"stockMinimo\":40,\"stockMaximo\":200}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.configurado").value(true))
                    .andExpect(jsonPath("$.stockMinimo").value(40.0));
            mvc.perform(get("/api/productos/" + productoA + "/sedes").header(HttpHeaders.AUTHORIZATION, comprasA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.sedeId==" + sedeSur + ")].stock").value(500.0))
                    .andExpect(jsonPath("$[?(@.sedeId==" + sedeB + ")]").isEmpty());

            // el plan de la principal usa su mínimo propio y sugiere traer de la sur antes de comprar
            mvc.perform(get("/api/reabastecimiento?sedeId=" + sedeA).header(HttpHeaders.AUTHORIZATION, comprasA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].stockMinimo").value(40.0))
                    .andExpect(jsonPath("$.items[0].trasladoDesdeSedeId").value(sedeSur))
                    .andExpect(jsonPath("$.items[0].cantidadComprar").value(0.0));
            mvc.perform(get("/api/reabastecimiento/sedes").header(HttpHeaders.AUTHORIZATION, comprasA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.sedeId==" + sedeA + ")].traslados").value(1))
                    .andExpect(jsonPath("$[?(@.sedeId==" + sedeB + ")]").isEmpty());

            // nada de la otra empresa: ni su sede ni su producto
            mvc.perform(put("/api/productos/" + productoA + "/sedes/" + sedeB).header(HttpHeaders.AUTHORIZATION, comprasA)
                            .contentType(json).content("{\"stockMinimo\":1}"))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/productos/" + productoB + "/sedes").header(HttpHeaders.AUTHORIZATION, comprasA))
                    .andExpect(status().isNotFound());
            mvc.perform(put("/api/productos/" + productoA + "/sedes/" + sedeA)
                            .header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "COMPRADOR"))
                            .contentType(json).content("{\"stockMinimo\":1}"))
                    .andExpect(status().isForbidden());
        } finally {
            jdbc.update("DELETE FROM movimiento_inventario WHERE empresa_id = ? AND cantidad = 90 AND bodega_origen_id = ?",
                    empresaA, bodegaA);
            jdbc.update("DELETE FROM producto_sede WHERE empresa_id = ?", empresaA);
            jdbc.update("DELETE FROM stock_producto_bodega WHERE bodega_id = ?", bodegaSur);
            jdbc.update("DELETE FROM bodega WHERE id = ?", bodegaSur);
            jdbc.update("DELETE FROM sede WHERE id = ?", sedeSur);
            org.junit.jupiter.api.Assertions.assertEquals(stockPrincipal, jdbc.queryForObject(
                    "SELECT COALESCE(SUM(cantidad), 0) FROM stock_producto_bodega WHERE producto_id = ? AND bodega_id = ?",
                    Long.class, productoA, bodegaA));
        }
    }

    @Test
    void elPedidoInternoSoloLoVeCompraYNuncaCruzaDeEmpresa() throws Exception {
        String json = MediaType.APPLICATION_JSON_VALUE;
        String almacenA = token(empresaA, usuarioA, "ALMACENISTA");
        String compradorA = token(empresaA, usuarioA2, "COMPRADOR");
        try {
            // cualquiera de la empresa pide; compras recibe el aviso
            String creado = mvc.perform(post("/api/pedidos-internos").header(HttpHeaders.AUTHORIZATION, almacenA)
                            .contentType(json).content("{\"productoId\":" + productoA + ",\"cantidad\":12,\"nota\":\"para la obra\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                    .andExpect(jsonPath("$.consecutivo").value(org.hamcrest.Matchers.startsWith("PED-")))
                    .andReturn().getResponse().getContentAsString();
            long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(creado).get("id").asLong();
            mvc.perform(post("/api/pedidos-internos").header(HttpHeaders.AUTHORIZATION, almacenA)
                            .contentType(json).content("{\"descripcion\":\"Guantes de carnaza talla L\"}"))
                    .andExpect(status().isCreated());
            // no se puede pedir sin decir qué, ni un producto de otra empresa
            mvc.perform(post("/api/pedidos-internos").header(HttpHeaders.AUTHORIZATION, almacenA)
                            .contentType(json).content("{\"cantidad\":3}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(post("/api/pedidos-internos").header(HttpHeaders.AUTHORIZATION, almacenA)
                            .contentType(json).content("{\"productoId\":" + productoB + "}"))
                    .andExpect(status().isNotFound());

            // compras ve todos; la otra empresa no ve ninguno ni puede responderlo
            mvc.perform(get("/api/pedidos-internos").header(HttpHeaders.AUTHORIZATION, compradorA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElementos").value(2));
            mvc.perform(get("/api/pedidos-internos").header(HttpHeaders.AUTHORIZATION, token(empresaB, usuarioB, "COMPRADOR")))
                    .andExpect(jsonPath("$.totalElementos").value(0));
            mvc.perform(patch("/api/pedidos-internos/" + id).header(HttpHeaders.AUTHORIZATION, token(empresaB, usuarioB, "COMPRADOR"))
                            .contentType(json).content("{\"estado\":\"ATENDIDO\"}"))
                    .andExpect(status().isNotFound());
            // quien no es de compras no responde
            mvc.perform(patch("/api/pedidos-internos/" + id).header(HttpHeaders.AUTHORIZATION, almacenA)
                            .contentType(json).content("{\"estado\":\"ATENDIDO\"}"))
                    .andExpect(status().isForbidden());

            // descartar exige explicar; atendido queda cerrado y le llega el aviso a quien pidió
            mvc.perform(patch("/api/pedidos-internos/" + id).header(HttpHeaders.AUTHORIZATION, compradorA)
                            .contentType(json).content("{\"estado\":\"DESCARTADO\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("RESPUESTA_REQUERIDA"));
            mvc.perform(patch("/api/pedidos-internos/" + id).header(HttpHeaders.AUTHORIZATION, compradorA)
                            .contentType(json).content("{\"estado\":\"ATENDIDO\",\"respuesta\":\"Se trasladaron 12 desde la sede sur\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("ATENDIDO"));
            mvc.perform(patch("/api/pedidos-internos/" + id).header(HttpHeaders.AUTHORIZATION, compradorA)
                            .contentType(json).content("{\"estado\":\"EN_GESTION\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("PEDIDO_CERRADO"));
            org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notificacion "
                    + "WHERE usuario_id = ? AND evento = 'PEDIDO_INTERNO_RESPONDIDO'", Integer.class, usuarioA));
        } finally {
            jdbc.update("DELETE FROM notificacion WHERE evento IN ('PEDIDO_INTERNO', 'PEDIDO_INTERNO_RESPONDIDO')");
            jdbc.update("DELETE FROM pedido_interno WHERE empresa_id IN (?, ?)", empresaA, empresaB);
        }
    }

    @Test
    void losPreciosDeVentaSonDeCadaEmpresaYSoloLosFijaElDueno() throws Exception {
        String json = MediaType.APPLICATION_JSON_VALUE;
        String duenoA = token(empresaA, usuarioA, "ADMIN_SISTEMA");
        Long costoAntes = null;
        try {
            jdbc.update("UPDATE producto SET costo_referencia = 7000 WHERE id = ?", productoA);
            // cada empresa tiene su lista General (la crea la migración o el primer uso)
            String listas = mvc.perform(get("/api/ventas/listas").header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            long generalA = new com.fasterxml.jackson.databind.ObjectMapper().readTree(listas).get(0).get("id").asLong();
            String listasB = mvc.perform(get("/api/ventas/listas").header(HttpHeaders.AUTHORIZATION,
                    token(empresaB, usuarioB, "ADMIN_SISTEMA"))).andReturn().getResponse().getContentAsString();
            long generalB = new com.fasterxml.jackson.databind.ObjectMapper().readTree(listasB).get(0).get("id").asLong();

            // un producto sin impuesto se marca; al asignarle IVA 19 % el margen se mide sobre la base
            mvc.perform(get("/api/ventas/precios").header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(jsonPath("$.filas[?(@.productoId==" + productoA + ")].alertas[*]",
                            org.hamcrest.Matchers.hasItem("SIN_IMPUESTO")));
            String impuestos = mvc.perform(get("/api/ventas/impuestos").header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andReturn().getResponse().getContentAsString();
            long iva19 = 0;
            for (var t : new com.fasterxml.jackson.databind.ObjectMapper().readTree(impuestos)) {
                if ("IVA19".equals(t.get("codigo").asText())) {
                    iva19 = t.get("id").asLong();
                }
            }
            mvc.perform(put("/api/ventas/productos/" + productoA).header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                            .content("{\"tarifaImpuestoId\":" + iva19 + ",\"vendible\":true,\"precioIncluyeImpuesto\":true}"))
                    .andExpect(status().isOk());

            // IVA incluido 19 %: 11.900 => base 10.000, margen 3.000 sobre costo 7.000 = 30 %
            mvc.perform(put("/api/ventas/precios").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                            .content("{\"listaId\":" + generalA + ",\"productoId\":" + productoA + ",\"precio\":11900}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.base").value(10000.0))
                    .andExpect(jsonPath("$.margenPct").value(30.0));
            mvc.perform(put("/api/ventas/precios").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                            .content("{\"listaId\":" + generalA + ",\"productoId\":" + productoA + ",\"precio\":12500,\"motivo\":\"alza\"}"))
                    .andExpect(status().isOk());
            // el historial conserva los dos: el anterior cerrado y el nuevo vigente
            mvc.perform(get("/api/ventas/precios/historial?listaId=" + generalA + "&productoId=" + productoA)
                            .header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].precio").value(12500.0))
                    .andExpect(jsonPath("$[0].vigenteHasta").doesNotExist())
                    .andExpect(jsonPath("$[1].vigenteHasta").exists());
            mvc.perform(get("/api/ventas/precios").header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(jsonPath("$.conPrecio").value(1))
                    .andExpect(jsonPath("$.filas[?(@.productoId==" + productoB + ")]").isEmpty());

            // el jefe de compras solo fija precios si el dueño se lo delegó
            String comprasA = token(empresaA, usuarioA, "ADMIN_COMPRAS");
            String body = "{\"listaId\":" + generalA + ",\"productoId\":" + productoA + ",\"precio\":13000}";
            mvc.perform(put("/api/ventas/precios").header(HttpHeaders.AUTHORIZATION, comprasA).contentType(json).content(body))
                    .andExpect(status().isForbidden());
            jdbc.update("UPDATE empresa SET precios_delegados_compras = 1 WHERE id = ?", empresaA);
            mvc.perform(put("/api/ventas/precios").header(HttpHeaders.AUTHORIZATION, comprasA).contentType(json).content(body))
                    .andExpect(status().isOk());
            // el comprador no ve costos ni márgenes
            mvc.perform(get("/api/ventas/precios").header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "COMPRADOR")))
                    .andExpect(status().isForbidden());

            // nada cruza de empresa: ni la lista ni el producto de la otra
            mvc.perform(put("/api/ventas/precios").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                            .content("{\"listaId\":" + generalB + ",\"productoId\":" + productoA + ",\"precio\":1}"))
                    .andExpect(status().isNotFound());
            mvc.perform(put("/api/ventas/precios").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                            .content("{\"listaId\":" + generalA + ",\"productoId\":" + productoB + ",\"precio\":1}"))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/ventas/precios?listaId=" + generalB).header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(status().isNotFound());
        } finally {
            jdbc.update("DELETE FROM precio_producto WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("UPDATE empresa SET precios_delegados_compras = 0 WHERE id = ?", empresaA);
            jdbc.update("UPDATE producto SET costo_referencia = NULL, tarifa_impuesto_id = NULL WHERE id = ?", productoA);
        }
    }

    @Test
    void elCatalogoDeVentaTienePrecioPorUnidadUnSoloVigenteYUnaBusquedaSinCostos() throws Exception {
        String json = MediaType.APPLICATION_JSON_VALUE;
        String duenoA = token(empresaA, usuarioA, "ADMIN_SISTEMA");
        String cajeroA = token(empresaA, usuarioA, "CAJERO");
        Long unidadProducto = jdbc.queryForObject("SELECT unidad_medida_id FROM producto WHERE id = ?", Long.class, productoA);
        // una unidad que se convierte a la del producto (misma magnitud) y otra que mide otra cosa
        List<Long> convertibles = jdbc.queryForList("SELECT id FROM unidad_medida WHERE activo = 1 AND id <> ? "
                + "AND magnitud = (SELECT magnitud FROM unidad_medida WHERE id = ?) ORDER BY id", Long.class,
                unidadProducto, unidadProducto);
        List<Long> incompatibles = jdbc.queryForList("SELECT id FROM unidad_medida WHERE activo = 1 "
                + "AND magnitud <> (SELECT magnitud FROM unidad_medida WHERE id = ?) ORDER BY id", Long.class, unidadProducto);
        assertFalse(convertibles.isEmpty(), "la prueba necesita una unidad convertible a la del producto");
        Long caja = convertibles.get(0);
        String codigoCaja = jdbc.queryForObject("SELECT codigo FROM unidad_medida WHERE id = ?", String.class, caja);
        String codigoProducto = jdbc.queryForObject("SELECT codigo FROM producto WHERE id = ?", String.class, productoA);
        String listas = mvc.perform(get("/api/ventas/listas").header(HttpHeaders.AUTHORIZATION, duenoA))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long generalA = new com.fasterxml.jackson.databind.ObjectMapper().readTree(listas).get(0).get("id").asLong();
        try {
            jdbc.update("UPDATE producto SET costo_referencia = 1000, codigo_barras = '7701234567890' WHERE id = ?", productoA);

            // V31: módulos nuevos en sus planes y roles de venta en el catálogo
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM plan_modulo pm JOIN plan p ON p.id = pm.plan_id "
                    + "WHERE pm.modulo_codigo = 'CAJA' AND p.codigo IN ('PROFESIONAL', 'EMPRESARIAL')", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM plan_modulo pm JOIN plan p ON p.id = pm.plan_id "
                    + "WHERE pm.modulo_codigo = 'CARTERA'", Integer.class));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM rol WHERE codigo IN ('SUPERVISOR_VENTAS', 'VENDEDOR')",
                    Integer.class));

            // precio en la unidad del producto y otro en la unidad convertible (D9): dos vigentes que no se pisan
            mvc.perform(put("/api/ventas/precios").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                            .content("{\"listaId\":" + generalA + ",\"productoId\":" + productoA + ",\"precio\":1500}"))
                    .andExpect(status().isOk());
            mvc.perform(put("/api/ventas/precios").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                            .content("{\"listaId\":" + generalA + ",\"productoId\":" + productoA + ",\"precio\":15000,"
                                    + "\"precioMinimo\":14000,\"unidadMedidaId\":" + caja + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.precio").value(1500.0))
                    .andExpect(jsonPath("$.otrasUnidades[0].unidad").value(codigoCaja))
                    .andExpect(jsonPath("$.otrasUnidades[0].precio").value(15000.0));
            mvc.perform(get("/api/ventas/precios/historial?listaId=" + generalA + "&productoId=" + productoA
                            + "&unidadMedidaId=" + caja).header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].precio").value(15000.0));
            if (!incompatibles.isEmpty()) {
                mvc.perform(put("/api/ventas/precios").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                                .content("{\"listaId\":" + generalA + ",\"productoId\":" + productoA + ",\"precio\":1,"
                                        + "\"unidadMedidaId\":" + incompatibles.get(0) + "}"))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.codigo").value("UNIDADES_INCOMPATIBLES"));
            }

            // dos personas fijan a la vez el primer precio en otra lista: nunca quedan dos vigentes
            String mayorista = mvc.perform(post("/api/ventas/listas").header(HttpHeaders.AUTHORIZATION, duenoA)
                            .contentType(json).content("{\"nombre\":\"Mayorista prueba\"}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            long listaMayorista = new com.fasterxml.jackson.databind.ObjectMapper().readTree(mayorista).get("id").asLong();
            java.util.concurrent.CountDownLatch salida = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.ExecutorService hilos = java.util.concurrent.Executors.newFixedThreadPool(2);
            List<java.util.concurrent.Future<Integer>> respuestas = new java.util.ArrayList<>();
            for (int i = 0; i < 2; i++) {
                int precio = 1300 + i;
                respuestas.add(hilos.submit(() -> {
                    salida.await();
                    return mvc.perform(put("/api/ventas/precios").header(HttpHeaders.AUTHORIZATION, duenoA)
                                    .contentType(json).content("{\"listaId\":" + listaMayorista + ",\"productoId\":"
                                            + productoA + ",\"precio\":" + precio + "}"))
                            .andReturn().getResponse().getStatus();
                }));
            }
            salida.countDown();
            List<Integer> estados = new java.util.ArrayList<>();
            for (var r : respuestas) {
                estados.add(r.get(30, java.util.concurrent.TimeUnit.SECONDS));
            }
            hilos.shutdown();
            assertTrue(estados.contains(200), "al menos uno fija el precio: " + estados);
            assertTrue(estados.stream().allMatch(s -> s == 200 || s == 409), "solo 200 o 409: " + estados);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM precio_producto WHERE lista_precio_id = ? "
                    + "AND producto_id = ? AND vigente_hasta IS NULL", Integer.class, listaMayorista, productoA));

            // búsqueda para vender: el cajero encuentra por código de barras, sin costos ni precio mínimo
            mvc.perform(get("/api/ventas/productos?q=7701234567890&bodegaId=" + bodegaA)
                            .header(HttpHeaders.AUTHORIZATION, cajeroA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].productoId").value(productoA))
                    .andExpect(jsonPath("$[0].precio").value(1500.0))
                    .andExpect(jsonPath("$[0].otrasUnidades[0].precio").value(15000.0))
                    .andExpect(jsonPath("$[0].costo").doesNotExist())
                    .andExpect(jsonPath("$[0].precioMinimo").doesNotExist())
                    .andExpect(jsonPath("$[0].otrasUnidades[0].precioMinimo").doesNotExist());
            mvc.perform(get("/api/ventas/productos?q=" + codigoProducto).header(HttpHeaders.AUTHORIZATION,
                            token(empresaA, usuarioA, "VENDEDOR")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].productoId").value(productoA));
            // ni la bodega ni el producto de A existen para B
            mvc.perform(get("/api/ventas/productos?q=7701234567890&bodegaId=" + bodegaB)
                            .header(HttpHeaders.AUTHORIZATION, cajeroA))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/ventas/productos?q=7701234567890")
                            .header(HttpHeaders.AUTHORIZATION, token(empresaB, usuarioB, "CAJERO")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
            // el cajero busca, pero no ve márgenes ni fija precios
            mvc.perform(get("/api/ventas/precios").header(HttpHeaders.AUTHORIZATION, cajeroA))
                    .andExpect(status().isForbidden());

            // carga por Excel: todo o nada
            String[] cabecera = { "codigo", "lista", "unidad", "precio", "precio_minimo", "motivo" };
            var conError = excel(cabecera, new Object[] { codigoProducto, "General", null, 1600, null, "alza" },
                    new Object[] { "NO-EXISTE", "General", null, 10, null, null });
            mvc.perform(multipart("/api/ventas/precios/carga").file(new org.springframework.mock.web.MockMultipartFile(
                            "archivo", "precios.xlsx", conError.getContentType(), conError.getBytes()))
                            .header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.aplicado").value(false))
                    .andExpect(jsonPath("$.errores[0].fila").value(3));
            assertEquals(0, new java.math.BigDecimal("1500").compareTo(jdbc.queryForObject("SELECT precio FROM precio_producto "
                    + "WHERE lista_precio_id = ? AND producto_id = ? AND unidad_medida_id = ? AND vigente_hasta IS NULL",
                    java.math.BigDecimal.class, generalA, productoA, unidadProducto)));
            var bueno = excel(cabecera, new Object[] { codigoProducto, "General", codigoCaja, 16000, 15500, null });
            mvc.perform(multipart("/api/ventas/precios/carga").file(new org.springframework.mock.web.MockMultipartFile(
                            "archivo", "precios.xlsx", bueno.getContentType(), bueno.getBytes()))
                            .header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.aplicado").value(true))
                    .andExpect(jsonPath("$.precios").value(1));
            assertEquals(0, new java.math.BigDecimal("16000").compareTo(jdbc.queryForObject("SELECT precio FROM precio_producto "
                    + "WHERE lista_precio_id = ? AND producto_id = ? AND unidad_medida_id = ? AND vigente_hasta IS NULL",
                    java.math.BigDecimal.class, generalA, productoA, caja)));
            // el jefe de compras sin delegación no carga precios
            mvc.perform(multipart("/api/ventas/precios/carga").file(new org.springframework.mock.web.MockMultipartFile(
                            "archivo", "precios.xlsx", bueno.getContentType(), bueno.getBytes()))
                            .header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "ADMIN_COMPRAS")))
                    .andExpect(status().isForbidden());
        } finally {
            jdbc.update("DELETE FROM precio_producto WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM lista_precio WHERE empresa_id = ? AND nombre = 'Mayorista prueba'", empresaA);
            jdbc.update("UPDATE producto SET costo_referencia = NULL, codigo_barras = NULL WHERE id = ?", productoA);
        }
    }

    @Test
    void losClientesSonDeCadaEmpresaYElConsumidorFinalNoSeToca() throws Exception {
        String json = MediaType.APPLICATION_JSON_VALUE;
        String cajeroA = token(empresaA, usuarioA, "CAJERO");
        String duenoA = token(empresaA, usuarioA, "ADMIN_SISTEMA");
        String cajeroB = token(empresaB, usuarioB, "CAJERO");
        com.fasterxml.jackson.databind.ObjectMapper lector = new com.fasterxml.jackson.databind.ObjectMapper();
        try {
            // V32 sembró un consumidor final por empresa; si falta (empresa nueva), la primera consulta lo crea
            jdbc.update("DELETE FROM cliente WHERE empresa_id = ? AND es_consumidor_final = 1", empresaB);
            mvc.perform(get("/api/clientes").header(HttpHeaders.AUTHORIZATION, cajeroB))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido[0].esConsumidorFinal").value(true));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM cliente WHERE empresa_id = ? AND es_consumidor_final = 1",
                    Integer.class, empresaB));
            long consumidorA = lector.readTree(mvc.perform(get("/api/clientes/consumidor-final")
                            .header(HttpHeaders.AUTHORIZATION, cajeroA)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString()).get("id").asLong();

            // el cajero crea un cliente; el contacto exige autorización de datos
            String sinAutorizar = "{\"tipoDocumento\":\"CC\",\"numeroDocumento\":\"1.020.304.050\",\"nombre\":\"Ana Pérez\","
                    + "\"telefono\":\"3001234567\"}";
            mvc.perform(post("/api/clientes").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json).content(sinAutorizar))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("AUTORIZACION_DATOS_REQUERIDA"));
            String ana = mvc.perform(post("/api/clientes").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content(sinAutorizar.replace("}", ",\"aceptaDatos\":true}")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.numeroDocumento").value("1020304050"))
                    .andExpect(jsonPath("$.aceptaDatosEn").exists())
                    .andReturn().getResponse().getContentAsString();
            long anaId = lector.readTree(ana).get("id").asLong();
            // el mismo documento no se repite en A, pero B sí puede tenerlo
            mvc.perform(post("/api/clientes").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content("{\"tipoDocumento\":\"CC\",\"numeroDocumento\":\"1020304050\",\"nombre\":\"Otra\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("CLIENTE_DUPLICADO"));
            mvc.perform(post("/api/clientes").header(HttpHeaders.AUTHORIZATION, cajeroB).contentType(json)
                            .content("{\"tipoDocumento\":\"CC\",\"numeroDocumento\":\"1020304050\",\"nombre\":\"Ana en B\"}"))
                    .andExpect(status().isCreated());
            // NIT con dígito de verificación equivocado
            mvc.perform(post("/api/clientes").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content("{\"tipoDocumento\":\"NIT\",\"numeroDocumento\":\"800197268-5\",\"nombre\":\"Taller\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("DOCUMENTO_INVALIDO"));

            // el cupo y el plazo solo los fijan el dueño o el supervisor de ventas
            String conCupo = "{\"tipoDocumento\":\"CC\",\"numeroDocumento\":\"1020304050\",\"nombre\":\"Ana Pérez\","
                    + "\"telefono\":\"3001234567\",\"cupoCredito\":500000,\"plazoDias\":30}";
            mvc.perform(put("/api/clientes/" + anaId).header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json).content(conCupo))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/clientes/" + anaId).header(HttpHeaders.AUTHORIZATION,
                            token(empresaA, usuarioA, "SUPERVISOR_VENTAS")).contentType(json).content(conCupo))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cupoCredito").value(500000.0))
                    .andExpect(jsonPath("$.plazoDias").value(30));
            // el cajero edita el nombre sin tocar el cupo (vacío = se conserva)
            mvc.perform(put("/api/clientes/" + anaId).header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content("{\"tipoDocumento\":\"CC\",\"numeroDocumento\":\"1020304050\",\"nombre\":\"Ana María Pérez\","
                                    + "\"telefono\":\"3001234567\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cupoCredito").value(500000.0));
            assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM auditoria WHERE empresa_id = ? AND accion = 'CLIENTE_CUPO_CAMBIAR' "
                    + "AND entidad_id = ?", Integer.class, empresaA, anaId) >= 1);

            // el consumidor final no se edita
            mvc.perform(put("/api/clientes/" + consumidorA).header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                            .content("{\"tipoDocumento\":\"CC\",\"numeroDocumento\":\"1234\",\"nombre\":\"X\",\"activo\":false}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("CONSUMIDOR_FINAL"));

            // búsqueda por documento o nombre, sin cruzar de empresa
            mvc.perform(get("/api/clientes?q=1020").header(HttpHeaders.AUTHORIZATION, cajeroA))
                    .andExpect(jsonPath("$.totalElementos").value(1))
                    .andExpect(jsonPath("$.contenido[0].nombre").value("Ana María Pérez"));
            mvc.perform(get("/api/clientes?q=ana%20mar").header(HttpHeaders.AUTHORIZATION, cajeroB))
                    .andExpect(jsonPath("$.totalElementos").value(0));
            mvc.perform(get("/api/clientes/" + anaId).header(HttpHeaders.AUTHORIZATION, cajeroB))
                    .andExpect(status().isNotFound());
            mvc.perform(put("/api/clientes/" + anaId).header(HttpHeaders.AUTHORIZATION, token(empresaB, usuarioB, "ADMIN_SISTEMA"))
                            .contentType(json).content("{\"tipoDocumento\":\"CC\",\"numeroDocumento\":\"999999\",\"nombre\":\"X\"}"))
                    .andExpect(status().isNotFound());

            // el auditor lee pero no crea; la exportación es solo para dueño y auditoría
            mvc.perform(get("/api/clientes").header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "AUDITOR")))
                    .andExpect(status().isOk());
            mvc.perform(post("/api/clientes").header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "AUDITOR"))
                            .contentType(json).content("{\"tipoDocumento\":\"CC\",\"numeroDocumento\":\"55555\",\"nombre\":\"X\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/exportaciones/clientes?formato=xlsx").header(HttpHeaders.AUTHORIZATION, cajeroA))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/exportaciones/clientes?formato=xlsx").header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(status().isOk());
            // el supervisor de ventas lee las listas de precios (para elegir la del cliente), pero no los precios
            mvc.perform(get("/api/ventas/listas").header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "SUPERVISOR_VENTAS")))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/ventas/precios").header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "SUPERVISOR_VENTAS")))
                    .andExpect(status().isForbidden());
            // un comprador (sin rol de venta) no ve clientes
            mvc.perform(get("/api/clientes").header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "COMPRADOR")))
                    .andExpect(status().isForbidden());
        } finally {
            jdbc.update("DELETE FROM cliente WHERE empresa_id IN (?, ?) AND es_consumidor_final = 0", empresaA, empresaB);
        }
    }

    @Test
    void elTurnoDeCajaSeCierraACiegasConAutorizacionesDeUnSoloUsoYSinCruzarEmpresas() throws Exception {
        String json = MediaType.APPLICATION_JSON_VALUE;
        com.fasterxml.jackson.databind.ObjectMapper lector = new com.fasterxml.jackson.databind.ObjectMapper();
        String duenoA = token(empresaA, usuarioA, "ADMIN_SISTEMA");
        String cajeroA = token(empresaA, usuarioA2, "CAJERO");
        Long sedeA = jdbc.queryForObject("SELECT sede_id FROM bodega WHERE id = ?", Long.class, bodegaA);
        try {
            // el dueño crea la caja; una bodega de otra empresa no existe para A
            mvc.perform(post("/api/cajas").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                            .content("{\"nombre\":\"Caja 1\",\"sedeId\":" + sedeA + ",\"bodegaId\":" + bodegaB
                                    + ",\"prefijoConsecutivo\":\"c1\",\"topeEfectivo\":100000}"))
                    .andExpect(status().isNotFound());
            long cajaId = lector.readTree(mvc.perform(post("/api/cajas").header(HttpHeaders.AUTHORIZATION, duenoA)
                            .contentType(json).content("{\"nombre\":\"Caja 1\",\"sedeId\":" + sedeA + ",\"bodegaId\":" + bodegaA
                                    + ",\"prefijoConsecutivo\":\"c1\",\"topeEfectivo\":100000}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.prefijoConsecutivo").value("C1"))
                    .andReturn().getResponse().getContentAsString()).get("id").asLong();
            mvc.perform(post("/api/cajas").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content("{\"nombre\":\"Caja 2\",\"sedeId\":" + sedeA + ",\"bodegaId\":" + bodegaA
                                    + ",\"prefijoConsecutivo\":\"C2\",\"topeEfectivo\":0}"))
                    .andExpect(status().isForbidden());

            // el cajero abre su turno; ni él abre otro ni otra persona usa la misma caja
            String abrir = "{\"cajaId\":" + cajaId + ",\"baseApertura\":50000}";
            long turnoId = lector.readTree(mvc.perform(post("/api/turnos").header(HttpHeaders.AUTHORIZATION, cajeroA)
                            .contentType(json).content(abrir))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.estado").value("ABIERTO"))
                    .andReturn().getResponse().getContentAsString()).get("id").asLong();
            mvc.perform(post("/api/turnos").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json).content(abrir))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("TURNO_ABIERTO"));
            mvc.perform(post("/api/turnos").header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "CAJERO"))
                            .contentType(json).content(abrir))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CAJA_OCUPADA"));

            // el dueño fija su PIN confirmando la contraseña
            mvc.perform(put("/api/autorizaciones/mi-pin").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                            .content("{\"passwordActual\":\"otra-clave\",\"pin\":\"123456\"}"))
                    .andExpect(status().isConflict());
            mvc.perform(put("/api/autorizaciones/mi-pin").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                            .content("{\"passwordActual\":\"" + CLAVE + "\",\"pin\":\"123456\"}"))
                    .andExpect(status().isNoContent());

            // autorización: PIN equivocado no sirve; el correcto da un permiso de un solo uso
            String pedir = "{\"correo\":\"admin@mismo.com\",\"pin\":\"%s\",\"accion\":\"RETIRO\",\"turnoId\":" + turnoId + "}";
            mvc.perform(post("/api/autorizaciones").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content(pedir.formatted("000000")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("AUTORIZACION_INVALIDA"));
            String permiso = lector.readTree(mvc.perform(post("/api/autorizaciones").header(HttpHeaders.AUTHORIZATION, cajeroA)
                            .contentType(json).content(pedir.formatted("123456")))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString()).get("token").asText();
            // no hay efectivo para retirar 60.000 (la base es 50.000): el permiso no se gasta
            String retiro = "{\"tipo\":\"RETIRO\",\"monto\":%s,\"motivo\":\"A la bóveda\",\"autorizacion\":\"" + permiso + "\"}";
            mvc.perform(post("/api/turnos/" + turnoId + "/movimientos").header(HttpHeaders.AUTHORIZATION, cajeroA)
                            .contentType(json).content(retiro.formatted("60000")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("EFECTIVO_INSUFICIENTE"));
            mvc.perform(post("/api/turnos/" + turnoId + "/movimientos").header(HttpHeaders.AUTHORIZATION, cajeroA)
                            .contentType(json).content(retiro.formatted("20000")))
                    .andExpect(status().isOk());
            mvc.perform(post("/api/turnos/" + turnoId + "/movimientos").header(HttpHeaders.AUTHORIZATION, cajeroA)
                            .contentType(json).content(retiro.formatted("1000")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("AUTORIZACION_INVALIDA"));

            // una venta en efectivo (la registra la spec 004): el cajero no la ve ni ve el esperado, solo el aviso de tope
            jdbc.update("INSERT INTO movimiento_caja (empresa_id, turno_id, tipo, medio_pago, monto, usuario_id) "
                    + "VALUES (?, ?, 'VENTA', 'EFECTIVO', 80000, ?)", empresaA, turnoId, usuarioA2);
            mvc.perform(get("/api/turnos/actual").header(HttpHeaders.AUTHORIZATION, cajeroA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.esperado").doesNotExist())
                    .andExpect(jsonPath("$.debeRetirar").value(true))                    // 50.000 − 20.000 + 80.000 > 100.000
                    .andExpect(jsonPath("$.movimientos[?(@.tipo=='VENTA')]").isEmpty())
                    .andExpect(jsonPath("$.movimientos[?(@.tipo=='RETIRO')].autorizadoPor").isNotEmpty());
            // el supervisor sí ve la venta
            mvc.perform(get("/api/turnos/" + turnoId).header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(jsonPath("$.movimientos[?(@.tipo=='VENTA')]").isNotEmpty());

            // cierre ciego: cuenta 105.000 y el esperado era 110.000; 5.000 pasan la tolerancia de 2.000
            mvc.perform(post("/api/turnos/" + turnoId + "/cerrar").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content("{\"contadoEfectivo\":105000,\"contadoOtros\":{\"TARJETA_DEBITO\":0}}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("CERRADO"))
                    .andExpect(jsonPath("$.esperado.EFECTIVO").value(110000.0))
                    .andExpect(jsonPath("$.diferencias.EFECTIVO").value(-5000.0))
                    .andExpect(jsonPath("$.requiereAprobacion").value(true));
            mvc.perform(get("/api/turnos?pendientes=true").header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(jsonPath("$.contenido[0].id").value(turnoId));
            // nadie aprueba lo suyo; el dueño, sí
            String aprobar = "{\"explicacion\":\"Se pagó un domicilio sin registrarlo\"}";
            mvc.perform(post("/api/turnos/" + turnoId + "/aprobar").header(HttpHeaders.AUTHORIZATION,
                            token(empresaA, usuarioA2, "SUPERVISOR_VENTAS")).contentType(json).content(aprobar))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("APRUEBA_LO_SUYO"));
            mvc.perform(post("/api/turnos/" + turnoId + "/aprobar").header(HttpHeaders.AUTHORIZATION, duenoA)
                            .contentType(json).content(aprobar))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("APROBADO"))
                    .andExpect(jsonPath("$.aprobadoPor").exists());

            // nada cruza de empresa: ni la caja, ni el turno, ni autorizar sobre un turno ajeno
            String duenoB = token(empresaB, usuarioB, "ADMIN_SISTEMA");
            mvc.perform(get("/api/cajas").header(HttpHeaders.AUTHORIZATION, duenoB))
                    .andExpect(jsonPath("$[?(@.id==" + cajaId + ")]").isEmpty());
            mvc.perform(get("/api/turnos/" + turnoId).header(HttpHeaders.AUTHORIZATION, duenoB))
                    .andExpect(status().isNotFound());
            mvc.perform(post("/api/autorizaciones").header(HttpHeaders.AUTHORIZATION, token(empresaB, usuarioB, "CAJERO"))
                            .contentType(json).content(pedir.formatted("123456")))
                    .andExpect(status().isNotFound());

            // un nuevo turno: tras 5 PIN equivocados el PIN se bloquea aunque luego sea el correcto
            long turno2 = lector.readTree(mvc.perform(post("/api/turnos").header(HttpHeaders.AUTHORIZATION, cajeroA)
                            .contentType(json).content("{\"cajaId\":" + cajaId + ",\"baseApertura\":0}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
            String pedir2 = "{\"correo\":\"admin@mismo.com\",\"pin\":\"%s\",\"accion\":\"GASTO\",\"turnoId\":" + turno2 + "}";
            for (int i = 0; i < 5; i++) {
                mvc.perform(post("/api/autorizaciones").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                        .content(pedir2.formatted("999999"))).andExpect(status().isConflict());
            }
            mvc.perform(post("/api/autorizaciones").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content(pedir2.formatted("123456")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PIN_BLOQUEADO"));
            assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM auditoria WHERE empresa_id = ? AND accion = 'AUTORIZACION_FALLO'",
                    Integer.class, empresaA) >= 6);

            // dos personas abren a la vez la misma caja: una abre y la otra recibe 409; nunca hay dos turnos abiertos
            mvc.perform(post("/api/turnos/" + turno2 + "/cerrar").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                    .content("{\"contadoEfectivo\":0}")).andExpect(status().isOk());
            java.util.concurrent.CountDownLatch salida = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.ExecutorService hilos = java.util.concurrent.Executors.newFixedThreadPool(2);
            List<java.util.concurrent.Future<Integer>> estados = new java.util.ArrayList<>();
            for (Long cajero : List.of(usuarioA, usuarioA2)) {
                estados.add(hilos.submit(() -> {
                    salida.await();
                    return mvc.perform(post("/api/turnos").header(HttpHeaders.AUTHORIZATION, token(empresaA, cajero, "CAJERO"))
                            .contentType(json).content("{\"cajaId\":" + cajaId + ",\"baseApertura\":1000}"))
                            .andReturn().getResponse().getStatus();
                }));
            }
            salida.countDown();
            List<Integer> resultado = new java.util.ArrayList<>();
            for (var f : estados) {
                resultado.add(f.get(30, java.util.concurrent.TimeUnit.SECONDS));
            }
            hilos.shutdown();
            assertEquals(List.of(201, 409), resultado.stream().sorted().toList());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM turno_caja WHERE caja_id = ? AND estado = 'ABIERTO'",
                    Integer.class, cajaId));
        } finally {
            jdbc.update("DELETE FROM autorizacion_supervisor WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM movimiento_caja WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM turno_caja WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM caja WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("UPDATE usuario SET pin_supervisor_hash = NULL, pin_intentos_fallidos = 0, pin_bloqueado_hasta = NULL "
                    + "WHERE empresa_id IN (?, ?)", empresaA, empresaB);
        }
    }

    @Test
    void laVentaCobraEnUnaTransaccionSinDuplicarNiVenderSinStockYNoCruzaEmpresas() throws Exception {
        String json = MediaType.APPLICATION_JSON_VALUE;
        com.fasterxml.jackson.databind.ObjectMapper lector = new com.fasterxml.jackson.databind.ObjectMapper();
        String duenoA = token(empresaA, usuarioA, "ADMIN_SISTEMA");
        String cajeroA = token(empresaA, usuarioA2, "CAJERO");
        Long sedeA = jdbc.queryForObject("SELECT sede_id FROM bodega WHERE id = ?", Long.class, bodegaA);
        java.math.BigDecimal stockAntes = jdbc.queryForObject("SELECT COALESCE(SUM(cantidad), 0) FROM stock_producto_bodega "
                + "WHERE producto_id = ? AND bodega_id = ?", java.math.BigDecimal.class, productoA, bodegaA);
        Long tarifaAntes = jdbc.queryForObject("SELECT tarifa_impuesto_id FROM producto WHERE id = ?", Long.class, productoA);
        try {
            // precio de mostrador con IVA incluido y mínimo; 10 unidades en la bodega de la caja
            long generalA = lector.readTree(mvc.perform(get("/api/ventas/listas").header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andReturn().getResponse().getContentAsString()).get(0).get("id").asLong();
            List<Long> iva = jdbc.queryForList("SELECT id FROM tarifa_impuesto WHERE empresa_id = ? AND porcentaje = 19",
                    Long.class, empresaA);
            jdbc.update("UPDATE producto SET costo_referencia = 6000, costo_promedio = 6000, vendible = 1, precio_incluye_impuesto = 1, "
                    + "tarifa_impuesto_id = ? WHERE id = ?", iva.isEmpty() ? null : iva.get(0), productoA);
            mvc.perform(put("/api/ventas/precios").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                            .content("{\"listaId\":" + generalA + ",\"productoId\":" + productoA
                                    + ",\"precio\":11930,\"precioMinimo\":10000}"))
                    .andExpect(status().isOk());
            jdbc.update("INSERT INTO stock_producto_bodega (empresa_id, producto_id, bodega_id, cantidad) VALUES (?, ?, ?, 10) "
                    + "ON DUPLICATE KEY UPDATE cantidad = 10", empresaA, productoA, bodegaA);

            // dos cajas en la misma bodega; el cajero abre turno en la primera
            long caja1 = lector.readTree(mvc.perform(post("/api/cajas").header(HttpHeaders.AUTHORIZATION, duenoA)
                            .contentType(json).content("{\"nombre\":\"Caja venta 1\",\"sedeId\":" + sedeA + ",\"bodegaId\":"
                                    + bodegaA + ",\"prefijoConsecutivo\":\"V1\",\"topeEfectivo\":0}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
            long caja2 = lector.readTree(mvc.perform(post("/api/cajas").header(HttpHeaders.AUTHORIZATION, duenoA)
                            .contentType(json).content("{\"nombre\":\"Caja venta 2\",\"sedeId\":" + sedeA + ",\"bodegaId\":"
                                    + bodegaA + ",\"prefijoConsecutivo\":\"V2\",\"topeEfectivo\":0}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
            String linea = "{\"productoId\":" + productoA + ",\"cantidad\":%s,\"descuentoPct\":%s}";
            String venta = "{\"lineas\":[" + linea + "],\"pagos\":[%s]%s}";
            // sin turno no se vende
            mvc.perform(post("/api/ventas").header(HttpHeaders.AUTHORIZATION, cajeroA).header("Idempotency-Key", "k-0")
                            .contentType(json).content(venta.formatted(1, 0, "{\"medio\":\"EFECTIVO\",\"monto\":20000}", "")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("TURNO_NO_ABIERTO"));
            long turno = lector.readTree(mvc.perform(post("/api/turnos").header(HttpHeaders.AUTHORIZATION, cajeroA)
                            .contentType(json).content("{\"cajaId\":" + caja1 + ",\"baseApertura\":0}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
            // sin clave de idempotencia, 400
            mvc.perform(post("/api/ventas").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content(venta.formatted(1, 0, "{\"medio\":\"EFECTIVO\",\"monto\":20000}", "")))
                    .andExpect(status().isBadRequest());

            // venta en efectivo: el sistema pone el precio, redondea a $50 (D3) y calcula el cambio
            String primera = venta.formatted(1, 0, "{\"medio\":\"EFECTIVO\",\"monto\":20000}", ",\"total\":1");
            String respuesta = mvc.perform(post("/api/ventas").header(HttpHeaders.AUTHORIZATION, cajeroA)
                            .header("Idempotency-Key", "k-1").contentType(json).content(primera))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.consecutivo").value("V1-000001"))
                    .andExpect(jsonPath("$.total").value(11930.0))
                    .andExpect(jsonPath("$.redondeo").value(20.0))
                    .andExpect(jsonPath("$.totalAPagar").value(11950.0))
                    .andExpect(jsonPath("$.cambio").value(8050.0))
                    .andExpect(jsonPath("$.pagos[0].recibido").value(20000.0))
                    .andExpect(jsonPath("$.pagos[0].monto").value(11950.0))
                    .andExpect(jsonPath("$.lineas[0].precioLista").value(11930.0))
                    .andReturn().getResponse().getContentAsString();
            long venta1 = lector.readTree(respuesta).get("id").asLong();
            assertFalse(respuesta.contains("costo"), "el cajero no ve el costo");
            assertEquals(0, new java.math.BigDecimal("9").compareTo(jdbc.queryForObject("SELECT cantidad FROM "
                    + "stock_producto_bodega WHERE producto_id = ? AND bodega_id = ?", java.math.BigDecimal.class, productoA, bodegaA)));
            assertEquals(0, new java.math.BigDecimal("11950").compareTo(jdbc.queryForObject("SELECT SUM(monto) FROM movimiento_caja "
                    + "WHERE turno_id = ? AND tipo = 'VENTA' AND referencia_id = ?", java.math.BigDecimal.class, turno, venta1)));
            assertEquals(0, new java.math.BigDecimal("6000").compareTo(jdbc.queryForObject("SELECT costo_unitario FROM venta_linea "
                    + "WHERE venta_id = ?", java.math.BigDecimal.class, venta1)), "la línea guarda el costo del día");

            // la misma venta reenviada con la misma clave: 200 con la misma venta y no se cobra dos veces
            mvc.perform(post("/api/ventas").header(HttpHeaders.AUTHORIZATION, cajeroA).header("Idempotency-Key", "k-1")
                            .contentType(json).content(primera))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(venta1));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM venta WHERE empresa_id = ?", Integer.class, empresaA));
            assertEquals(0, new java.math.BigDecimal("9").compareTo(jdbc.queryForObject("SELECT cantidad FROM "
                    + "stock_producto_bodega WHERE producto_id = ? AND bodega_id = ?", java.math.BigDecimal.class, productoA, bodegaA)));

            // cotizar (spec 005): mismo cálculo que la venta, sin guardar nada ni gastar permisos
            String carrito = "{\"lineas\":[" + linea.formatted(1, 0) + "],\"pagos\":[%s]}";
            mvc.perform(post("/api/ventas/cotizar").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content(carrito.formatted("{\"medio\":\"EFECTIVO\",\"monto\":20000}")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(11930.0))
                    .andExpect(jsonPath("$.totalAPagar").value(11950.0))
                    .andExpect(jsonPath("$.cambio").value(8050.0))
                    .andExpect(jsonPath("$.lineas[0].nombre").exists());
            mvc.perform(post("/api/ventas/cotizar").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content(carrito.formatted("{\"medio\":\"TRANSFERENCIA\",\"monto\":20000}")))
                    .andExpect(jsonPath("$.cambio").doesNotExist())
                    .andExpect(jsonPath("$.mensajePago").exists());
            mvc.perform(post("/api/ventas/cotizar").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content("{\"lineas\":[" + linea.formatted(1, 10) + "]}"))
                    .andExpect(jsonPath("$.requiereAutorizacionDescuento").value(true))
                    .andExpect(jsonPath("$.limiteDescuentoPct").value(5.0));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM venta WHERE empresa_id = ?", Integer.class, empresaA));

            // tiquete: el original y luego COPIA, cada impresión auditada; otra empresa no lo ve
            mvc.perform(get("/api/ventas/" + venta1 + "/tiquete").header(HttpHeaders.AUTHORIZATION, cajeroA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.copia").value(false))
                    .andExpect(jsonPath("$.nit").exists())
                    .andExpect(jsonPath("$.venta.consecutivo").value("V1-000001"))
                    .andExpect(jsonPath("$.impuestos.length()").value(1));
            mvc.perform(get("/api/ventas/" + venta1 + "/tiquete").header(HttpHeaders.AUTHORIZATION, cajeroA))
                    .andExpect(jsonPath("$.copia").value(true))
                    .andExpect(jsonPath("$.impresion").value(2));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM auditoria WHERE empresa_id = ? AND accion = 'VENTA_REIMPRIMIR' "
                    + "AND entidad_id = ?", Integer.class, empresaA, venta1));
            mvc.perform(get("/api/ventas/" + venta1 + "/tiquete").header(HttpHeaders.AUTHORIZATION,
                            token(empresaB, usuarioB, "ADMIN_SISTEMA")))
                    .andExpect(status().isNotFound());

            // no alcanza el stock (D1): se rechaza toda la venta, nada queda a medias y el consecutivo no deja hueco
            mvc.perform(post("/api/ventas").header(HttpHeaders.AUTHORIZATION, cajeroA).header("Idempotency-Key", "k-2")
                            .contentType(json).content(venta.formatted(50, 0, "{\"medio\":\"EFECTIVO\",\"monto\":1000000}", "")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("STOCK_INSUFICIENTE"));
            // la tarjeta exige últimos 4 dígitos y autorización, y no puede pasarse del total
            mvc.perform(post("/api/ventas").header(HttpHeaders.AUTHORIZATION, cajeroA).header("Idempotency-Key", "k-3")
                            .contentType(json).content(venta.formatted(1, 0, "{\"medio\":\"TARJETA_DEBITO\",\"monto\":11930}", "")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("TARJETA_SIN_DATOS"));
            mvc.perform(post("/api/ventas").header(HttpHeaders.AUTHORIZATION, cajeroA).header("Idempotency-Key", "k-3")
                            .contentType(json).content(venta.formatted(1, 0,
                                    "{\"medio\":\"TARJETA_DEBITO\",\"monto\":11931,\"ultimos4\":\"4242\",\"referencia\":\"A1\"}", "")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PAGO_EXCEDE"));
            mvc.perform(post("/api/ventas").header(HttpHeaders.AUTHORIZATION, cajeroA).header("Idempotency-Key", "k-3")
                            .contentType(json).content(venta.formatted(1, 0,
                                    "{\"medio\":\"TARJETA_DEBITO\",\"monto\":11930,\"ultimos4\":\"424242\",\"referencia\":\"A1\"}", "")))
                    .andExpect(status().isBadRequest());
            mvc.perform(post("/api/ventas").header(HttpHeaders.AUTHORIZATION, cajeroA).header("Idempotency-Key", "k-3")
                            .contentType(json).content(venta.formatted(1, 0,
                                    "{\"medio\":\"TARJETA_DEBITO\",\"monto\":11930,\"ultimos4\":\"4242\",\"referencia\":\"A1\"}", "")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.consecutivo").value("V1-000002"))
                    .andExpect(jsonPath("$.redondeo").value(0.0))
                    .andExpect(jsonPath("$.pagos[0].ultimos4").value("4242"));

            // descuento sobre el límite del cajero (5 %): pide el PIN del supervisor, que se gasta en la venta
            mvc.perform(post("/api/ventas").header(HttpHeaders.AUTHORIZATION, cajeroA).header("Idempotency-Key", "k-4")
                            .contentType(json).content(venta.formatted(1, 10, "{\"medio\":\"EFECTIVO\",\"monto\":20000}", "")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("AUTORIZACION_REQUERIDA"));
            mvc.perform(put("/api/autorizaciones/mi-pin").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                            .content("{\"passwordActual\":\"" + CLAVE + "\",\"pin\":\"123456\"}"))
                    .andExpect(status().isNoContent());
            String pedirPermiso = "{\"correo\":\"admin@mismo.com\",\"pin\":\"123456\",\"accion\":\"%s\",\"turnoId\":" + turno + "}";
            String permiso = lector.readTree(mvc.perform(post("/api/autorizaciones").header(HttpHeaders.AUTHORIZATION, cajeroA)
                            .contentType(json).content(pedirPermiso.formatted("DESCUENTO")))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("token").asText();
            String conPermiso = venta.formatted(1, 10, "{\"medio\":\"EFECTIVO\",\"monto\":20000}",
                    ",\"autorizacionDescuento\":\"" + permiso + "\"");
            mvc.perform(post("/api/ventas").header(HttpHeaders.AUTHORIZATION, cajeroA).header("Idempotency-Key", "k-4")
                            .contentType(json).content(conPermiso))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.lineas[0].precioUnitario").value(10737.0))
                    .andExpect(jsonPath("$.lineas[0].autorizadoPor").exists())
                    .andExpect(jsonPath("$.autorizadaPor").exists());
            // el permiso ya se usó
            mvc.perform(post("/api/ventas").header(HttpHeaders.AUTHORIZATION, cajeroA).header("Idempotency-Key", "k-5")
                            .contentType(json).content(conPermiso))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("AUTORIZACION_INVALIDA"));

            // el dueño sube el límite del cajero a 25 %: un 20 % ya no pide descuento, pero el precio queda bajo el mínimo
            mvc.perform(put("/api/ventas/limites-descuento").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content("[{\"rol\":\"CAJERO\",\"descuentoMaximoPct\":25}]"))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/ventas/limites-descuento").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                            .content("[{\"rol\":\"CAJERO\",\"descuentoMaximoPct\":25}]"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.rol=='CAJERO')].descuentoMaximoPct").value(25.0))
                    .andExpect(jsonPath("$[?(@.rol=='SUPERVISOR_VENTAS')].descuentoMaximoPct").value(15.0));
            mvc.perform(post("/api/ventas").header(HttpHeaders.AUTHORIZATION, cajeroA).header("Idempotency-Key", "k-5")
                            .contentType(json).content(venta.formatted(1, 20, "{\"medio\":\"EFECTIVO\",\"monto\":20000}", "")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("AUTORIZACION_REQUERIDA"));

            // pedido de venta: el vendedor lo arma sin tocar stock; el cajero lo cobra una sola vez
            String vendedorA = token(empresaA, usuarioA, "VENDEDOR");
            long pedido = lector.readTree(mvc.perform(post("/api/pedidos-venta").header(HttpHeaders.AUTHORIZATION, vendedorA)
                            .contentType(json).content("{\"lineas\":[" + linea.formatted(2, 0) + "],\"nota\":\"Mostrador\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.consecutivo").value(org.hamcrest.Matchers.startsWith("PV-")))
                    .andExpect(jsonPath("$.estado").value("PENDIENTE_COBRO"))
                    .andReturn().getResponse().getContentAsString()).get("id").asLong();
            java.math.BigDecimal stockConPedido = jdbc.queryForObject("SELECT cantidad FROM stock_producto_bodega "
                    + "WHERE producto_id = ? AND bodega_id = ?", java.math.BigDecimal.class, productoA, bodegaA);
            assertEquals(0, new java.math.BigDecimal("7").compareTo(stockConPedido), "el pedido no descuenta stock");
            // solo quien lo armó (o un supervisor) lo edita; otro vendedor ni lo ve en su lista
            String otroVendedor = token(empresaA, usuarioA2, "VENDEDOR");
            String editado = "{\"lineas\":[" + linea.formatted(2, 0) + "],\"nota\":\"Editado\"}";
            mvc.perform(put("/api/pedidos-venta/" + pedido).header(HttpHeaders.AUTHORIZATION, otroVendedor)
                            .contentType(json).content(editado))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/pedidos-venta").header(HttpHeaders.AUTHORIZATION, otroVendedor))
                    .andExpect(jsonPath("$.totalElementos").value(0));
            mvc.perform(get("/api/pedidos-venta/" + pedido).header(HttpHeaders.AUTHORIZATION, otroVendedor))
                    .andExpect(status().isNotFound());
            mvc.perform(put("/api/pedidos-venta/" + pedido).header(HttpHeaders.AUTHORIZATION, vendedorA)
                            .contentType(json).content(editado))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.nota").value("Editado"))
                    .andExpect(jsonPath("$.lineas.length()").value(1));
            // un pedido descartado no se vuelve a descartar ni se cobra
            long descartado = lector.readTree(mvc.perform(post("/api/pedidos-venta").header(HttpHeaders.AUTHORIZATION, vendedorA)
                            .contentType(json).content("{\"lineas\":[" + linea.formatted(1, 0) + "]}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
            mvc.perform(post("/api/pedidos-venta/" + descartado + "/descartar").header(HttpHeaders.AUTHORIZATION, vendedorA))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("DESCARTADO"));
            mvc.perform(post("/api/pedidos-venta/" + descartado + "/descartar").header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PEDIDO_NO_PENDIENTE"));
            mvc.perform(get("/api/pedidos-venta?estado=PENDIENTE_COBRO").header(HttpHeaders.AUTHORIZATION, cajeroA))
                    .andExpect(jsonPath("$.totalElementos").value(1));
            String cobrarPedido = "{\"pedidoVentaId\":" + pedido + ",\"lineas\":[" + linea.formatted(2, 0)
                    + "],\"pagos\":[{\"medio\":\"TRANSFERENCIA\",\"monto\":23860,\"referencia\":\"T-9\"}]}";
            mvc.perform(post("/api/ventas").header(HttpHeaders.AUTHORIZATION, cajeroA).header("Idempotency-Key", "k-6")
                            .contentType(json).content(cobrarPedido))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.vendedor").exists())
                    .andExpect(jsonPath("$.pedidoVentaId").value(pedido));
            mvc.perform(get("/api/pedidos-venta/" + pedido).header(HttpHeaders.AUTHORIZATION, vendedorA))
                    .andExpect(jsonPath("$.estado").value("COBRADO"));
            mvc.perform(post("/api/ventas").header(HttpHeaders.AUTHORIZATION, cajeroA).header("Idempotency-Key", "k-7")
                            .contentType(json).content(cobrarPedido))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PEDIDO_NO_PENDIENTE"));
            mvc.perform(get("/api/ventas?estado=CONFIRMADA").header(HttpHeaders.AUTHORIZATION, vendedorA))
                    .andExpect(jsonPath("$.totalElementos").value(1));

            // el cajero no ve el historial (cierre ciego): solo busca una venta suya por consecutivo
            mvc.perform(get("/api/ventas").header(HttpHeaders.AUTHORIZATION, cajeroA)).andExpect(status().isForbidden());
            mvc.perform(get("/api/ventas?consecutivo=v1-000001").header(HttpHeaders.AUTHORIZATION, cajeroA))
                    .andExpect(jsonPath("$.contenido[0].id").value(venta1));
            mvc.perform(get("/api/ventas").header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(jsonPath("$.totalElementos").value(4));

            // nada cruza de empresa: B no ve las ventas ni los pedidos de A, ni vende ni pide productos de A
            String duenoB = token(empresaB, usuarioB, "ADMIN_SISTEMA");
            mvc.perform(get("/api/ventas/" + venta1).header(HttpHeaders.AUTHORIZATION, duenoB)).andExpect(status().isNotFound());
            mvc.perform(get("/api/ventas").header(HttpHeaders.AUTHORIZATION, duenoB)).andExpect(jsonPath("$.totalElementos").value(0));
            mvc.perform(get("/api/pedidos-venta/" + pedido).header(HttpHeaders.AUTHORIZATION, duenoB)).andExpect(status().isNotFound());
            mvc.perform(post("/api/pedidos-venta").header(HttpHeaders.AUTHORIZATION, token(empresaB, usuarioB, "VENDEDOR"))
                            .contentType(json).content("{\"lineas\":[" + linea.formatted(1, 0) + "]}"))
                    .andExpect(status().isNotFound());
            mvc.perform(post("/api/ventas").header(HttpHeaders.AUTHORIZATION, token(empresaB, usuarioB, "CAJERO"))
                            .header("Idempotency-Key", "k-1").contentType(json).content(primera))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("TURNO_NO_ABIERTO"));

            // dos cajas venden a la vez la última unidad: una pasa, la otra no; el stock nunca queda negativo
            jdbc.update("UPDATE stock_producto_bodega SET cantidad = 1 WHERE producto_id = ? AND bodega_id = ?", productoA, bodegaA);
            mvc.perform(post("/api/turnos").header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "CAJERO"))
                            .contentType(json).content("{\"cajaId\":" + caja2 + ",\"baseApertura\":0}"))
                    .andExpect(status().isCreated());
            java.util.concurrent.CountDownLatch salida = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.ExecutorService hilos = java.util.concurrent.Executors.newFixedThreadPool(2);
            List<java.util.concurrent.Future<Integer>> estados = new java.util.ArrayList<>();
            for (Long cajero : List.of(usuarioA, usuarioA2)) {
                estados.add(hilos.submit(() -> {
                    salida.await();
                    return mvc.perform(post("/api/ventas").header(HttpHeaders.AUTHORIZATION, token(empresaA, cajero, "CAJERO"))
                            .header("Idempotency-Key", "ultima-" + cajero).contentType(json)
                            .content(venta.formatted(1, 0, "{\"medio\":\"EFECTIVO\",\"monto\":20000}", "")))
                            .andReturn().getResponse().getStatus();
                }));
            }
            salida.countDown();
            List<Integer> resultado = new java.util.ArrayList<>();
            for (var f : estados) {
                resultado.add(f.get(30, java.util.concurrent.TimeUnit.SECONDS));
            }
            hilos.shutdown();
            assertEquals(List.of(201, 409), resultado.stream().sorted().toList());
            assertEquals(0, java.math.BigDecimal.ZERO.compareTo(jdbc.queryForObject("SELECT cantidad FROM stock_producto_bodega "
                    + "WHERE producto_id = ? AND bodega_id = ?", java.math.BigDecimal.class, productoA, bodegaA)));
            // los consecutivos de cada caja no tienen huecos
            assertEquals(List.of("V1-000001", "V1-000002", "V1-000003", "V1-000004"), jdbc.queryForList("SELECT consecutivo "
                    + "FROM venta WHERE caja_id = ? ORDER BY id", String.class, caja1).subList(0, 4));
            assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM auditoria WHERE empresa_id = ? AND accion = 'VENTA_CONFIRMAR'",
                    Integer.class, empresaA) >= 5);
        } finally {
            jdbc.update("UPDATE pedido_venta SET venta_id = NULL WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE p FROM venta_pago p JOIN venta v ON v.id = p.venta_id WHERE v.empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE l FROM venta_linea l JOIN venta v ON v.id = l.venta_id WHERE v.empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM venta WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM pedido_venta WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM limite_descuento_rol WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM movimiento_inventario WHERE empresa_id IN (?, ?) AND referencia_tipo = 'VENTA'", empresaA, empresaB);
            jdbc.update("DELETE FROM autorizacion_supervisor WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM movimiento_caja WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM turno_caja WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM caja WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM precio_producto WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("UPDATE stock_producto_bodega SET cantidad = ? WHERE producto_id = ? AND bodega_id = ?", stockAntes,
                    productoA, bodegaA);
            jdbc.update("UPDATE producto SET costo_referencia = NULL, costo_promedio = NULL, tarifa_impuesto_id = ? WHERE id = ?", tarifaAntes, productoA);
            jdbc.update("UPDATE usuario SET pin_supervisor_hash = NULL, pin_intentos_fallidos = 0, pin_bloqueado_hasta = NULL "
                    + "WHERE empresa_id IN (?, ?)", empresaA, empresaB);
        }
    }

    @Test
    void anularYDevolverDejanStockYCajaExactosSinPasarLoVendidoNiCruzarEmpresas() throws Exception {
        String json = MediaType.APPLICATION_JSON_VALUE;
        com.fasterxml.jackson.databind.ObjectMapper lector = new com.fasterxml.jackson.databind.ObjectMapper();
        String duenoA = token(empresaA, usuarioA, "ADMIN_SISTEMA");
        String cajeroA = token(empresaA, usuarioA2, "CAJERO");
        Long sedeA = jdbc.queryForObject("SELECT sede_id FROM bodega WHERE id = ?", Long.class, bodegaA);
        java.math.BigDecimal stockAntes = jdbc.queryForObject("SELECT COALESCE(SUM(cantidad), 0) FROM stock_producto_bodega "
                + "WHERE producto_id = ? AND bodega_id = ?", java.math.BigDecimal.class, productoA, bodegaA);
        java.util.function.Supplier<java.math.BigDecimal> stock = () -> jdbc.queryForObject("SELECT cantidad FROM "
                + "stock_producto_bodega WHERE producto_id = ? AND bodega_id = ?", java.math.BigDecimal.class, productoA, bodegaA);
        try {
            long generalA = lector.readTree(mvc.perform(get("/api/ventas/listas").header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andReturn().getResponse().getContentAsString()).get(0).get("id").asLong();
            jdbc.update("UPDATE producto SET costo_referencia = 6000, costo_promedio = 6000, vendible = 1 WHERE id = ?", productoA);
            mvc.perform(put("/api/ventas/precios").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                    .content("{\"listaId\":" + generalA + ",\"productoId\":" + productoA + ",\"precio\":10000}"))
                    .andExpect(status().isOk());
            jdbc.update("INSERT INTO stock_producto_bodega (empresa_id, producto_id, bodega_id, cantidad) VALUES (?, ?, ?, 10) "
                    + "ON DUPLICATE KEY UPDATE cantidad = 10", empresaA, productoA, bodegaA);
            long caja = lector.readTree(mvc.perform(post("/api/cajas").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                            .content("{\"nombre\":\"Caja dev\",\"sedeId\":" + sedeA + ",\"bodegaId\":" + bodegaA
                                    + ",\"prefijoConsecutivo\":\"D1\",\"topeEfectivo\":0}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
            long turno = lector.readTree(mvc.perform(post("/api/turnos").header(HttpHeaders.AUTHORIZATION, cajeroA)
                            .contentType(json).content("{\"cajaId\":" + caja + ",\"baseApertura\":50000}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
            mvc.perform(put("/api/autorizaciones/mi-pin").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                    .content("{\"passwordActual\":\"" + CLAVE + "\",\"pin\":\"123456\"}")).andExpect(status().isNoContent());
            // permiso de un solo uso del dueño para una acción sobre una venta
            java.util.function.BiFunction<String, Long, String> permiso = (accion, ventaId) -> {
                try {
                    return lector.readTree(mvc.perform(post("/api/autorizaciones").header(HttpHeaders.AUTHORIZATION, cajeroA)
                                    .contentType(json).content("{\"correo\":\"admin@mismo.com\",\"pin\":\"123456\",\"accion\":\""
                                            + accion + "\",\"turnoId\":" + turno + ",\"referenciaId\":" + ventaId + "}"))
                            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("token").asText();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            };
            // vende y devuelve la respuesta de la venta
            java.util.function.BiFunction<String, String, com.fasterxml.jackson.databind.JsonNode> vender = (clave, cuerpo) -> {
                try {
                    return lector.readTree(mvc.perform(post("/api/ventas").header(HttpHeaders.AUTHORIZATION, cajeroA)
                                    .header("Idempotency-Key", clave).contentType(json).content(cuerpo))
                            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            };
            String linea = "{\"productoId\":" + productoA + ",\"cantidad\":%s}";

            // 1) anular en el turno: exige permiso de otra persona; devuelve stock y efectivo; no se anula dos veces
            // vender por debajo del costo promedio no se impide: avisa al dueño (spec 008)
            jdbc.update("UPDATE producto SET costo_promedio = 12000 WHERE id = ?", productoA);
            int avisosAntes = jdbc.queryForObject("SELECT COUNT(*) FROM notificacion WHERE evento = 'PRECIO_BAJO_COSTO' "
                    + "AND usuario_id = ?", Integer.class, usuarioA);
            com.fasterxml.jackson.databind.JsonNode v1 = vender.apply("dv-1", "{\"lineas\":[" + linea.formatted(2)
                    + "],\"pagos\":[{\"medio\":\"EFECTIVO\",\"monto\":20000}]}");
            assertEquals(avisosAntes + 1, jdbc.queryForObject("SELECT COUNT(*) FROM notificacion WHERE evento = 'PRECIO_BAJO_COSTO' "
                    + "AND usuario_id = ?", Integer.class, usuarioA));
            assertEquals(0, new java.math.BigDecimal("12000").compareTo(jdbc.queryForObject("SELECT costo_unitario FROM venta_linea "
                    + "WHERE venta_id = ?", java.math.BigDecimal.class, v1.get("id").asLong())), "la línea guarda el costo promedio del momento");
            jdbc.update("UPDATE producto SET costo_promedio = 6000 WHERE id = ?", productoA);
            long venta1 = v1.get("id").asLong();
            assertEquals(0, new java.math.BigDecimal("8").compareTo(stock.get()));
            String anular = "{\"motivo\":\"El cliente desistió\",\"autorizacion\":\"%s\"}";
            mvc.perform(post("/api/ventas/" + venta1 + "/anular").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content(anular.formatted("no-sirve")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("AUTORIZACION_INVALIDA"));
            mvc.perform(post("/api/ventas/" + venta1 + "/anular").header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "CAJERO"))
                            .contentType(json).content(anular.formatted("x")))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/ventas/" + venta1 + "/anular").header(HttpHeaders.AUTHORIZATION, token(empresaB, usuarioB, "CAJERO"))
                            .contentType(json).content(anular.formatted("x")))
                    .andExpect(status().isNotFound());
            mvc.perform(post("/api/ventas/" + venta1 + "/anular").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content(anular.formatted(permiso.apply("ANULAR_VENTA", venta1))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("ANULADA"))
                    .andExpect(jsonPath("$.motivoAnulacion").value("El cliente desistió"));
            assertEquals(0, new java.math.BigDecimal("10").compareTo(stock.get()), "la anulación devuelve el stock");
            assertEquals(0, new java.math.BigDecimal("50000").compareTo(jdbc.queryForObject("SELECT SUM(monto) FROM movimiento_caja "
                    + "WHERE turno_id = ? AND medio_pago = 'EFECTIVO'", java.math.BigDecimal.class, turno)), "la caja vuelve a la base");
            mvc.perform(post("/api/ventas/" + venta1 + "/anular").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content(anular.formatted("x")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("VENTA_ANULADA"));

            // 2) devolución parcial por el mismo medio (tarjeta); nunca más de lo vendido menos lo devuelto
            com.fasterxml.jackson.databind.JsonNode v2 = vender.apply("dv-2", "{\"lineas\":[" + linea.formatted(3)
                    + "],\"pagos\":[{\"medio\":\"TARJETA_CREDITO\",\"monto\":30000,\"ultimos4\":\"1111\",\"referencia\":\"Z9\"}]}");
            assertEquals("D1-000002", v2.get("consecutivo").asText(), "el consecutivo de la anulada no se reutiliza");
            long venta2 = v2.get("id").asLong();
            long linea2 = v2.get("lineas").get(0).get("id").asLong();
            String devolver = "{\"ventaId\":" + venta2 + ",\"motivo\":\"No le sirvió\",\"formaReembolso\":\"%s\",\"lineas\":[{"
                    + "\"ventaLineaId\":" + linea2 + ",\"cantidad\":%s,\"estadoProducto\":\"%s\"}],\"autorizacion\":\"%s\"}";
            mvc.perform(post("/api/devoluciones").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content(devolver.formatted("MISMO_MEDIO", 1, "BUENO", permiso.apply("DEVOLUCION", venta2))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.consecutivo").value(org.hamcrest.Matchers.startsWith("DV-")))
                    .andExpect(jsonPath("$.medioReembolso").value("TARJETA_CREDITO"))
                    .andExpect(jsonPath("$.totalReembolso").value(10000.0));
            assertEquals(0, new java.math.BigDecimal("8").compareTo(stock.get()), "lo bueno vuelve a la bodega");
            assertEquals(0, new java.math.BigDecimal("-10000").compareTo(jdbc.queryForObject("SELECT SUM(monto) FROM movimiento_caja "
                    + "WHERE turno_id = ? AND tipo = 'DEVOLUCION' AND medio_pago = 'TARJETA_CREDITO'", java.math.BigDecimal.class, turno)));
            mvc.perform(post("/api/devoluciones").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content(devolver.formatted("EFECTIVO", 3, "BUENO", permiso.apply("DEVOLUCION", venta2))))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CANTIDAD_DEVOLUCION"));
            // el consumidor final no puede quedar con saldo a favor
            mvc.perform(post("/api/devoluciones").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content(devolver.formatted("SALDO_A_FAVOR", 1, "BUENO", permiso.apply("DEVOLUCION", venta2))))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SALDO_CONSUMIDOR_FINAL"));
            // lo dañado sin bodega de averías no reingresa; la última devolución toma exactamente lo que faltaba
            mvc.perform(post("/api/devoluciones").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content(devolver.formatted("EFECTIVO", 2, "DANADO", permiso.apply("DEVOLUCION", venta2))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.lineas[0].bodega").doesNotExist())
                    .andExpect(jsonPath("$.totalReembolso").value(20000.0));
            assertEquals(0, new java.math.BigDecimal("8").compareTo(stock.get()), "lo dañado no vuelve al stock de venta");
            assertEquals(0, new java.math.BigDecimal("30000").compareTo(jdbc.queryForObject("SELECT SUM(l.valor) FROM "
                    + "devolucion_venta_linea l JOIN devolucion_venta d ON d.id = l.devolucion_id WHERE d.venta_id = ?",
                    java.math.BigDecimal.class, venta2)), "lo devuelto suma exactamente lo vendido");
            mvc.perform(get("/api/ventas/" + venta2).header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(jsonPath("$.lineas[0].cantidadDevuelta").value(3.0))
                    .andExpect(jsonPath("$.totalDevuelto").value(30000.0));
            mvc.perform(post("/api/ventas/" + venta2 + "/anular").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content(anular.formatted("x")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("TIENE_DEVOLUCIONES"));

            // 3) saldo a favor para un cliente registrado
            long cliente = lector.readTree(mvc.perform(post("/api/clientes").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content("{\"tipoDocumento\":\"CC\",\"numeroDocumento\":\"10203040\",\"nombre\":\"Cliente devolución\","
                                    + "\"aceptaDatos\":true}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
            com.fasterxml.jackson.databind.JsonNode v3 = vender.apply("dv-3", "{\"clienteId\":" + cliente + ",\"lineas\":["
                    + linea.formatted(1) + "],\"pagos\":[{\"medio\":\"EFECTIVO\",\"monto\":10000}]}");
            long venta3 = v3.get("id").asLong();
            String devolver3 = "{\"ventaId\":" + venta3 + ",\"motivo\":\"Cambio de opinión\",\"formaReembolso\":\"SALDO_A_FAVOR\","
                    + "\"lineas\":[{\"ventaLineaId\":" + v3.get("lineas").get(0).get("id").asLong()
                    + ",\"cantidad\":1,\"estadoProducto\":\"BUENO\"}],\"autorizacion\":\"%s\"}";
            long devolucion3 = lector.readTree(mvc.perform(post("/api/devoluciones").header(HttpHeaders.AUTHORIZATION, cajeroA)
                            .contentType(json).content(devolver3.formatted(permiso.apply("DEVOLUCION", venta3))))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.medioReembolso").doesNotExist())
                    .andReturn().getResponse().getContentAsString()).get("id").asLong();
            assertEquals(0, new java.math.BigDecimal("10000").compareTo(jdbc.queryForObject("SELECT SUM(monto) FROM saldo_favor_cliente "
                    + "WHERE cliente_id = ?", java.math.BigDecimal.class, cliente)));

            // 4) dos devoluciones a la vez de la última unidad: solo una pasa
            com.fasterxml.jackson.databind.JsonNode v4 = vender.apply("dv-4", "{\"lineas\":[" + linea.formatted(1)
                    + "],\"pagos\":[{\"medio\":\"EFECTIVO\",\"monto\":10000}]}");
            long venta4 = v4.get("id").asLong();
            String devolver4 = "{\"ventaId\":" + venta4 + ",\"motivo\":\"Duplicado\",\"formaReembolso\":\"EFECTIVO\",\"lineas\":[{"
                    + "\"ventaLineaId\":" + v4.get("lineas").get(0).get("id").asLong()
                    + ",\"cantidad\":1,\"estadoProducto\":\"BUENO\"}],\"autorizacion\":\"%s\"}";
            List<String> permisos = List.of(permiso.apply("DEVOLUCION", venta4), permiso.apply("DEVOLUCION", venta4));
            java.util.concurrent.CountDownLatch salida = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.ExecutorService hilos = java.util.concurrent.Executors.newFixedThreadPool(2);
            List<java.util.concurrent.Future<Integer>> estados = new java.util.ArrayList<>();
            for (String t : permisos) {
                estados.add(hilos.submit(() -> {
                    salida.await();
                    return mvc.perform(post("/api/devoluciones").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content(devolver4.formatted(t))).andReturn().getResponse().getStatus();
                }));
            }
            salida.countDown();
            List<Integer> resultado = new java.util.ArrayList<>();
            for (var f : estados) {
                resultado.add(f.get(30, java.util.concurrent.TimeUnit.SECONDS));
            }
            hilos.shutdown();
            assertEquals(List.of(201, 409), resultado.stream().sorted().toList());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM devolucion_venta WHERE venta_id = ?", Integer.class, venta4));

            // 5) la bodega de averías es de la empresa; nada de esto cruza a B
            mvc.perform(put("/api/cajas/configuracion").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                            .content("{\"toleranciaDiferencia\":2000,\"bodegaAveriasId\":" + bodegaB + "}"))
                    .andExpect(status().isNotFound());
            String duenoB = token(empresaB, usuarioB, "ADMIN_SISTEMA");
            mvc.perform(get("/api/devoluciones/" + devolucion3).header(HttpHeaders.AUTHORIZATION, duenoB)).andExpect(status().isNotFound());
            mvc.perform(get("/api/devoluciones").header(HttpHeaders.AUTHORIZATION, duenoB)).andExpect(jsonPath("$.totalElementos").value(0));
            mvc.perform(get("/api/devoluciones?ventaId=" + venta2).header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(jsonPath("$.totalElementos").value(2));

            // 6) la caja cuadra: base + ventas en efectivo − anulación − devoluciones en efectivo
            java.math.BigDecimal efectivo = jdbc.queryForObject("SELECT SUM(monto) FROM movimiento_caja WHERE turno_id = ? "
                    + "AND medio_pago = 'EFECTIVO'", java.math.BigDecimal.class, turno);
            assertEquals(0, new java.math.BigDecimal("50000").subtract(new java.math.BigDecimal("20000"))
                    .add(new java.math.BigDecimal("10000")).add(new java.math.BigDecimal("10000"))
                    .subtract(new java.math.BigDecimal("10000")).compareTo(efectivo));

            // 7) en un turno cerrado no se anula: se devuelve
            mvc.perform(post("/api/turnos/" + turno + "/cerrar").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                    .content("{\"contadoEfectivo\":" + efectivo + "}")).andExpect(status().isOk());
            mvc.perform(post("/api/ventas/" + venta4 + "/anular").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content(anular.formatted("x")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("TURNO_CERRADO"));
            assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM auditoria WHERE empresa_id = ? AND accion IN ('VENTA_ANULAR', "
                    + "'VENTA_DEVOLVER')", Integer.class, empresaA) >= 5);
        } finally {
            jdbc.update("DELETE FROM saldo_favor_cliente WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE l FROM devolucion_venta_linea l JOIN devolucion_venta d ON d.id = l.devolucion_id "
                    + "WHERE d.empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM devolucion_venta WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE p FROM venta_pago p JOIN venta v ON v.id = p.venta_id WHERE v.empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE l FROM venta_linea l JOIN venta v ON v.id = l.venta_id WHERE v.empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM venta WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM cliente WHERE empresa_id IN (?, ?) AND numero_documento = '10203040'", empresaA, empresaB);
            jdbc.update("DELETE FROM movimiento_inventario WHERE empresa_id IN (?, ?) AND referencia_tipo IN ('VENTA', 'DEVOLUCION_VENTA')",
                    empresaA, empresaB);
            jdbc.update("DELETE FROM autorizacion_supervisor WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM movimiento_caja WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM turno_caja WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM caja WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM precio_producto WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("UPDATE empresa SET bodega_averias_id = NULL WHERE id IN (?, ?)", empresaA, empresaB);
            jdbc.update("UPDATE stock_producto_bodega SET cantidad = ? WHERE producto_id = ? AND bodega_id = ?", stockAntes,
                    productoA, bodegaA);
            jdbc.update("UPDATE producto SET costo_referencia = NULL, costo_promedio = NULL WHERE id = ?", productoA);
            jdbc.update("UPDATE usuario SET pin_supervisor_hash = NULL, pin_intentos_fallidos = 0, pin_bloqueado_hasta = NULL "
                    + "WHERE empresa_id IN (?, ?)", empresaA, empresaB);
        }
    }

    @Test
    void laRecepcionActualizaElCostoPromedioAunqueLleguenDosALaVez() throws Exception {
        String almacen = token(empresaA, usuarioA, "ALMACENISTA");
        java.math.BigDecimal stockAntes = jdbc.queryForObject("SELECT COALESCE(SUM(cantidad), 0) FROM stock_producto_bodega "
                + "WHERE producto_id = ?", java.math.BigDecimal.class, productoA);
        List<Long> ordenes = new java.util.ArrayList<>();
        try {
            // existencia de 10 a $80; se deja solo esa bodega con stock para que la cuenta sea clara
            jdbc.update("UPDATE stock_producto_bodega SET cantidad = 0 WHERE producto_id = ?", productoA);
            jdbc.update("INSERT INTO stock_producto_bodega (empresa_id, producto_id, bodega_id, cantidad) VALUES (?, ?, ?, 10) "
                    + "ON DUPLICATE KEY UPDATE cantidad = 10", empresaA, productoA, bodegaA);
            jdbc.update("UPDATE producto SET costo_promedio = 80 WHERE id = ?", productoA);
            Long[] o1 = sembrarOrden(empresaA, proveedorA, bodegaA, productoA, "OC-COSTO-1");
            Long[] o2 = sembrarOrden(empresaA, proveedorA, bodegaA, productoA, "OC-COSTO-2");
            Long[] o3 = sembrarOrden(empresaA, proveedorA, bodegaA, productoA, "OC-COSTO-3");
            ordenes.addAll(List.of(o1[0], o2[0], o3[0]));
            jdbc.update("UPDATE orden_compra_linea SET precio_unitario = 120 WHERE id IN (?, ?)", o2[1], o3[1]);

            // 10 a $100: (10 × 80 + 10 × 100) ÷ 20 = 90
            recibir(o1[0], almacen, "{\"ordenLineaId\":" + o1[1] + ",\"cantidadRecibida\":10}");
            assertEquals(0, new java.math.BigDecimal("90").compareTo(jdbc.queryForObject("SELECT costo_promedio FROM producto "
                    + "WHERE id = ?", java.math.BigDecimal.class, productoA)));
            assertEquals(0, new java.math.BigDecimal("100").compareTo(jdbc.queryForObject("SELECT costo_unitario FROM "
                    + "movimiento_inventario WHERE tipo = 'ENTRADA_COMPRA' AND producto_id = ? ORDER BY id DESC LIMIT 1",
                    java.math.BigDecimal.class, productoA)), "la entrada guarda el costo de la compra");

            // dos recepciones a la vez de 10 a $120: en fila dan 100 y luego 105, nunca un costo que pierda una de las dos
            java.util.concurrent.CountDownLatch salida = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.ExecutorService hilos = java.util.concurrent.Executors.newFixedThreadPool(2);
            List<java.util.concurrent.Future<Integer>> estados = new java.util.ArrayList<>();
            for (Long[] o : List.of(o2, o3)) {
                estados.add(hilos.submit(() -> {
                    salida.await();
                    return mvc.perform(post("/api/ordenes/" + o[0] + "/recepciones").header(HttpHeaders.AUTHORIZATION, almacen)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"lineas\":[{\"ordenLineaId\":" + o[1] + ",\"cantidadRecibida\":10}]}"))
                            .andReturn().getResponse().getStatus();
                }));
            }
            salida.countDown();
            for (var f : estados) {
                assertEquals(201, f.get(30, java.util.concurrent.TimeUnit.SECONDS));
            }
            hilos.shutdown();
            assertEquals(0, new java.math.BigDecimal("105").compareTo(jdbc.queryForObject("SELECT costo_promedio FROM producto "
                    + "WHERE id = ?", java.math.BigDecimal.class, productoA)));
            assertEquals(0, new java.math.BigDecimal("40").compareTo(jdbc.queryForObject("SELECT cantidad FROM stock_producto_bodega "
                    + "WHERE producto_id = ? AND bodega_id = ?", java.math.BigDecimal.class, productoA, bodegaA)));
            // el producto muestra su costo promedio; la empresa B no ve el de A (su producto conserva el suyo)
            mvc.perform(get("/api/productos/" + productoA).header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "ADMIN_COMPRAS")))
                    .andExpect(jsonPath("$.costoPromedio").value(105.0));
            mvc.perform(get("/api/productos/" + productoA).header(HttpHeaders.AUTHORIZATION, token(empresaB, usuarioB, "ADMIN_COMPRAS")))
                    .andExpect(status().isNotFound());
            assertNull(jdbc.queryForObject("SELECT costo_promedio FROM producto WHERE id = ?", java.math.BigDecimal.class, productoB));
        } finally {
            for (Long o : ordenes) {
                jdbc.update("DELETE FROM movimiento_inventario WHERE referencia_tipo = 'RECEPCION' AND referencia_id IN "
                        + "(SELECT id FROM recepcion WHERE orden_compra_id = ?)", o);
                jdbc.update("DELETE l FROM recepcion_linea l JOIN recepcion r ON r.id = l.recepcion_id WHERE r.orden_compra_id = ?", o);
                jdbc.update("DELETE FROM recepcion WHERE orden_compra_id = ?", o);
                jdbc.update("DELETE FROM evaluacion_proveedor WHERE orden_compra_id = ?", o);
                jdbc.update("DELETE FROM orden_compra_estado_historial WHERE orden_compra_id = ?", o);
                jdbc.update("DELETE FROM orden_compra_linea WHERE orden_compra_id = ?", o);
                jdbc.update("DELETE FROM orden_compra WHERE id = ?", o);
            }
            jdbc.update("UPDATE stock_producto_bodega SET cantidad = 0 WHERE producto_id = ?", productoA);
            jdbc.update("UPDATE stock_producto_bodega SET cantidad = ? WHERE producto_id = ? AND bodega_id = ?", stockAntes,
                    productoA, bodegaA);
            jdbc.update("UPDATE producto SET costo_promedio = NULL, costo_referencia = NULL WHERE id = ?", productoA);
        }
    }

    @Test
    void laVentaACreditoRespetaCupoYMoraYLosAbonosEntranALaCajaSinCruzarEmpresas() throws Exception {
        String json = MediaType.APPLICATION_JSON_VALUE;
        com.fasterxml.jackson.databind.ObjectMapper lector = new com.fasterxml.jackson.databind.ObjectMapper();
        String duenoA = token(empresaA, usuarioA, "ADMIN_SISTEMA");
        String cajeroA = token(empresaA, usuarioA2, "CAJERO");
        String cajero2 = token(empresaA, usuarioA, "CAJERO");
        Long sedeA = jdbc.queryForObject("SELECT sede_id FROM bodega WHERE id = ?", Long.class, bodegaA);
        java.math.BigDecimal stockAntes = jdbc.queryForObject("SELECT COALESCE(SUM(cantidad), 0) FROM stock_producto_bodega "
                + "WHERE producto_id = ? AND bodega_id = ?", java.math.BigDecimal.class, productoA, bodegaA);
        try {
            long generalA = lector.readTree(mvc.perform(get("/api/ventas/listas").header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andReturn().getResponse().getContentAsString()).get(0).get("id").asLong();
            jdbc.update("UPDATE producto SET costo_referencia = 6000, costo_promedio = 6000, vendible = 1 WHERE id = ?", productoA);
            mvc.perform(put("/api/ventas/precios").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                    .content("{\"listaId\":" + generalA + ",\"productoId\":" + productoA + ",\"precio\":10000}"))
                    .andExpect(status().isOk());
            jdbc.update("INSERT INTO stock_producto_bodega (empresa_id, producto_id, bodega_id, cantidad) VALUES (?, ?, ?, 20) "
                    + "ON DUPLICATE KEY UPDATE cantidad = 20", empresaA, productoA, bodegaA);
            List<Long> cajas = new java.util.ArrayList<>();
            for (String prefijo : List.of("K1", "K2")) {
                cajas.add(lector.readTree(mvc.perform(post("/api/cajas").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                                .content("{\"nombre\":\"Caja " + prefijo + "\",\"sedeId\":" + sedeA + ",\"bodegaId\":" + bodegaA
                                        + ",\"prefijoConsecutivo\":\"" + prefijo + "\",\"topeEfectivo\":0}"))
                        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong());
            }
            long turno = lector.readTree(mvc.perform(post("/api/turnos").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content("{\"cajaId\":" + cajas.get(0) + ",\"baseApertura\":0}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
            mvc.perform(post("/api/turnos").header(HttpHeaders.AUTHORIZATION, cajero2).contentType(json)
                    .content("{\"cajaId\":" + cajas.get(1) + ",\"baseApertura\":0}")).andExpect(status().isCreated());
            // cliente con cupo de 30.000 a 30 días (el cupo lo fija el dueño)
            long cliente = lector.readTree(mvc.perform(post("/api/clientes").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                            .content("{\"tipoDocumento\":\"CC\",\"numeroDocumento\":\"55667788\",\"nombre\":\"Ferretería cliente\","
                                    + "\"aceptaDatos\":true,\"cupoCredito\":30000,\"plazoDias\":30}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
            String credito = "{\"clienteId\":%s,\"lineas\":[{\"productoId\":" + productoA + ",\"cantidad\":%s}],"
                    + "\"pagos\":[{\"medio\":\"CREDITO\",\"monto\":%s}]}";
            java.util.function.BiFunction<String, String, org.springframework.test.web.servlet.ResultActions> vender = (clave, cuerpo) -> {
                try {
                    return mvc.perform(post("/api/ventas").header(HttpHeaders.AUTHORIZATION, cajeroA).header("Idempotency-Key", clave)
                            .contentType(json).content(cuerpo));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            };

            // 1) nunca al consumidor final; con cliente crea su cuenta y no entra a la caja
            vender.apply("cr-0", "{\"lineas\":[{\"productoId\":" + productoA + ",\"cantidad\":1}],\"pagos\":[{\"medio\":\"CREDITO\","
                    + "\"monto\":10000}]}").andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CREDITO_SIN_CLIENTE"));
            long venta1 = lector.readTree(vender.apply("cr-1", credito.formatted(cliente, 2, 20000))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
            assertEquals(0, new java.math.BigDecimal("20000").compareTo(jdbc.queryForObject("SELECT saldo FROM cuenta_por_cobrar "
                    + "WHERE venta_id = ?", java.math.BigDecimal.class, venta1)));
            assertEquals(java.time.LocalDate.now().plusDays(30), jdbc.queryForObject("SELECT vence_en FROM cuenta_por_cobrar "
                    + "WHERE venta_id = ?", java.time.LocalDate.class, venta1));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM movimiento_caja WHERE turno_id = ? AND tipo = 'VENTA'",
                    Integer.class, turno));
            // 2) el cupo no se pasa: quedan 10.000
            vender.apply("cr-2", credito.formatted(cliente, 2, 20000))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CUPO_INSUFICIENTE"));
            mvc.perform(post("/api/ventas/cotizar").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content("{\"clienteId\":" + cliente + ",\"lineas\":[{\"productoId\":" + productoA + ",\"cantidad\":1}]}"))
                    .andExpect(jsonPath("$.creditoDisponible").value(10000.0))
                    .andExpect(jsonPath("$.creditoBloqueado").value(false));

            // 3) dos cajas venden a crédito a la vez y juntas pasarían el cupo: una se rechaza
            java.util.concurrent.CountDownLatch salida = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.ExecutorService hilos = java.util.concurrent.Executors.newFixedThreadPool(2);
            List<java.util.concurrent.Future<Integer>> estados = new java.util.ArrayList<>();
            for (String t : List.of(cajeroA, cajero2)) {
                estados.add(hilos.submit(() -> {
                    salida.await();
                    return mvc.perform(post("/api/ventas").header(HttpHeaders.AUTHORIZATION, t).header("Idempotency-Key", "cr-par-" + t.hashCode())
                            .contentType(json).content(credito.formatted(cliente, 1, 10000))).andReturn().getResponse().getStatus();
                }));
            }
            salida.countDown();
            List<Integer> resultado = new java.util.ArrayList<>();
            for (var f : estados) {
                resultado.add(f.get(30, java.util.concurrent.TimeUnit.SECONDS));
            }
            hilos.shutdown();
            assertEquals(List.of(201, 409), resultado.stream().sorted().toList());
            assertEquals(0, new java.math.BigDecimal("30000").compareTo(jdbc.queryForObject("SELECT SUM(saldo) FROM cuenta_por_cobrar "
                    + "WHERE cliente_id = ? AND estado = 'ABIERTA'", java.math.BigDecimal.class, cliente)));

            // 4) abono: entra a la caja, se aplica a la cuenta más antigua y no puede pasar lo que debe
            mvc.perform(post("/api/abonos").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content("{\"clienteId\":" + cliente + ",\"valor\":30001,\"medioPago\":\"EFECTIVO\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ABONO_EXCEDE"));
            long abono = lector.readTree(mvc.perform(post("/api/abonos").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content("{\"clienteId\":" + cliente + ",\"valor\":5000,\"medioPago\":\"EFECTIVO\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.consecutivo").value(org.hamcrest.Matchers.startsWith("AB-")))
                    .andExpect(jsonPath("$.aplicaciones[0].cuentaId").value(jdbc.queryForObject("SELECT id FROM cuenta_por_cobrar "
                            + "WHERE venta_id = ?", Long.class, venta1)))
                    .andExpect(jsonPath("$.saldoPendiente").value(25000.0))
                    .andReturn().getResponse().getContentAsString()).get("id").asLong();
            assertEquals(0, new java.math.BigDecimal("5000").compareTo(jdbc.queryForObject("SELECT SUM(monto) FROM movimiento_caja "
                    + "WHERE turno_id = ? AND tipo = 'ABONO_CARTERA'", java.math.BigDecimal.class, turno)));
            mvc.perform(get("/api/abonos/" + abono + "/recibo").header(HttpHeaders.AUTHORIZATION, cajeroA))
                    .andExpect(jsonPath("$.valor").value(5000.0));

            // 5) mora: una cuenta vencida bloquea el crédito y avisa; el dueño lo habilita hasta una fecha
            jdbc.update("UPDATE cuenta_por_cobrar SET vence_en = ? WHERE venta_id = ?", java.time.LocalDate.now().minusDays(3), venta1);
            int avisos = jdbc.queryForObject("SELECT COUNT(*) FROM notificacion WHERE evento = 'CREDITO_BLOQUEADO' AND usuario_id = ?",
                    Integer.class, usuarioA);
            vender.apply("cr-3", credito.formatted(cliente, "0.5", 5000))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CLIENTE_EN_MORA"));
            assertEquals(avisos + 1, jdbc.queryForObject("SELECT COUNT(*) FROM notificacion WHERE evento = 'CREDITO_BLOQUEADO' "
                    + "AND usuario_id = ?", Integer.class, usuarioA), "el aviso queda aunque la venta se rechace");
            String excepcion = "{\"hasta\":\"" + java.time.LocalDate.now() + "\",\"motivo\":\"Paga el viernes, cliente de años\"}";
            mvc.perform(put("/api/cartera/clientes/" + cliente + "/excepcion").header(HttpHeaders.AUTHORIZATION, cajeroA)
                    .contentType(json).content(excepcion)).andExpect(status().isForbidden());
            mvc.perform(put("/api/cartera/clientes/" + cliente + "/excepcion").header(HttpHeaders.AUTHORIZATION, duenoA)
                            .contentType(json).content(excepcion))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.enMora").value(true)).andExpect(jsonPath("$.bloqueado").value(false));
            long venta3 = lector.readTree(vender.apply("cr-3", credito.formatted(cliente, "0.5", 5000))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();

            // 6) edades: la cartera suma lo mismo por tramos que el saldo total
            mvc.perform(get("/api/cartera").header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(jsonPath("$.saldo").value(30000.0))
                    .andExpect(jsonPath("$.d1a30").value(15000.0))
                    .andExpect(jsonPath("$.alDia").value(15000.0))
                    .andExpect(jsonPath("$.enMora").value(1))
                    .andExpect(jsonPath("$.clientes[?(@.clienteId==" + cliente + ")].excepcionPor").isNotEmpty());
            mvc.perform(get("/api/cartera/clientes/" + cliente).header(HttpHeaders.AUTHORIZATION, cajeroA))
                    .andExpect(jsonPath("$.cuentas.length()").value(3))
                    .andExpect(jsonPath("$.abonos.length()").value(1));

            // 7) anular: la venta sin abonos anula su cuenta; la que ya recibió abonos no se anula
            mvc.perform(put("/api/autorizaciones/mi-pin").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                    .content("{\"passwordActual\":\"" + CLAVE + "\",\"pin\":\"123456\"}")).andExpect(status().isNoContent());
            java.util.function.Function<Long, String> permiso = ventaId -> {
                try {
                    return lector.readTree(mvc.perform(post("/api/autorizaciones").header(HttpHeaders.AUTHORIZATION, cajeroA)
                                    .contentType(json).content("{\"correo\":\"admin@mismo.com\",\"pin\":\"123456\",\"accion\":"
                                            + "\"ANULAR_VENTA\",\"turnoId\":" + turno + ",\"referenciaId\":" + ventaId + "}"))
                            .andReturn().getResponse().getContentAsString()).get("token").asText();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            };
            String anular = "{\"motivo\":\"Se cobró a quien no era\",\"autorizacion\":\"%s\"}";
            mvc.perform(post("/api/ventas/" + venta1 + "/anular").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content(anular.formatted(permiso.apply(venta1))))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CUENTA_CON_ABONOS"));
            mvc.perform(post("/api/ventas/" + venta3 + "/anular").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content(anular.formatted(permiso.apply(venta3))))
                    .andExpect(status().isOk());
            assertEquals("ANULADA", jdbc.queryForObject("SELECT estado FROM cuenta_por_cobrar WHERE venta_id = ?", String.class, venta3));

            // 7b) la misma clave con otro carrito no devuelve la venta vieja como si fuera la nueva
            vender.apply("cr-1", credito.formatted(cliente, 1, 10000))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CLAVE_REPETIDA"));

            // 7c) devolver una venta a crédito, aunque se pida en efectivo, rebaja su deuda y no saca plata de la caja
            com.fasterxml.jackson.databind.JsonNode v4 = lector.readTree(vender.apply("cr-4", credito.formatted(cliente, "0.5", 5000))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
            long venta4 = v4.get("id").asLong();
            String permisoDevolucion = lector.readTree(mvc.perform(post("/api/autorizaciones").header(HttpHeaders.AUTHORIZATION, cajeroA)
                            .contentType(json).content("{\"correo\":\"admin@mismo.com\",\"pin\":\"123456\",\"accion\":\"DEVOLUCION\","
                                    + "\"turnoId\":" + turno + ",\"referenciaId\":" + venta4 + "}"))
                    .andReturn().getResponse().getContentAsString()).get("token").asText();
            mvc.perform(post("/api/devoluciones").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content("{\"ventaId\":" + venta4 + ",\"motivo\":\"No era la medida\",\"formaReembolso\":\"EFECTIVO\","
                                    + "\"lineas\":[{\"ventaLineaId\":" + v4.get("lineas").get(0).get("id").asLong()
                                    + ",\"cantidad\":0.5,\"estadoProducto\":\"BUENO\"}],\"autorizacion\":\"" + permisoDevolucion + "\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.totalReembolso").value(5000.0))
                    .andExpect(jsonPath("$.medioReembolso").doesNotExist());
            assertEquals("PAGADA", jdbc.queryForObject("SELECT estado FROM cuenta_por_cobrar WHERE venta_id = ?", String.class, venta4));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM movimiento_caja WHERE turno_id = ? AND tipo = 'DEVOLUCION'",
                    Integer.class, turno), "nada sale de la caja por una venta que no se pagó");

            // 7d) un abono con tarjeta exige la referencia del pago
            mvc.perform(post("/api/abonos").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content("{\"clienteId\":" + cliente + ",\"valor\":1000,\"medioPago\":\"TARJETA_DEBITO\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("REFERENCIA_REQUERIDA"));

            // 8) el saldo a favor se usa como medio de pago y no se gasta dos veces
            jdbc.update("INSERT INTO saldo_favor_cliente (empresa_id, cliente_id, monto, motivo) VALUES (?, ?, 3000, 'Prueba')",
                    empresaA, cliente);
            String conSaldo = "{\"clienteId\":" + cliente + ",\"lineas\":[{\"productoId\":" + productoA + ",\"cantidad\":1}],"
                    + "\"pagos\":[{\"medio\":\"SALDO_FAVOR\",\"monto\":3000},{\"medio\":\"EFECTIVO\",\"monto\":7000}]}";
            vender.apply("sf-1", conSaldo).andExpect(status().isCreated());
            assertEquals(0, java.math.BigDecimal.ZERO.compareTo(jdbc.queryForObject("SELECT SUM(monto) FROM saldo_favor_cliente "
                    + "WHERE cliente_id = ?", java.math.BigDecimal.class, cliente)));
            vender.apply("sf-2", conSaldo).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("SALDO_FAVOR_INSUFICIENTE"));

            // 9) nada de la cartera cruza a B
            String duenoB = token(empresaB, usuarioB, "ADMIN_SISTEMA");
            mvc.perform(get("/api/cartera/clientes/" + cliente).header(HttpHeaders.AUTHORIZATION, duenoB)).andExpect(status().isNotFound());
            mvc.perform(get("/api/abonos/" + abono + "/recibo").header(HttpHeaders.AUTHORIZATION, duenoB)).andExpect(status().isNotFound());
            mvc.perform(get("/api/cartera").header(HttpHeaders.AUTHORIZATION, duenoB))
                    .andExpect(jsonPath("$.clientes[?(@.clienteId==" + cliente + ")]").isEmpty());
            mvc.perform(put("/api/cartera/clientes/" + cliente + "/excepcion").header(HttpHeaders.AUTHORIZATION, duenoB)
                    .contentType(json).content(excepcion)).andExpect(status().isNotFound());
        } finally {
            jdbc.update("DELETE a FROM abono_aplicacion a JOIN abono_cliente b ON b.id = a.abono_id WHERE b.empresa_id IN (?, ?)",
                    empresaA, empresaB);
            jdbc.update("DELETE FROM abono_cliente WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM cuenta_por_cobrar WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM saldo_favor_cliente WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE l FROM devolucion_venta_linea l JOIN devolucion_venta d ON d.id = l.devolucion_id "
                    + "WHERE d.empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM devolucion_venta WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE p FROM venta_pago p JOIN venta v ON v.id = p.venta_id WHERE v.empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE l FROM venta_linea l JOIN venta v ON v.id = l.venta_id WHERE v.empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM venta WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM cliente WHERE empresa_id IN (?, ?) AND numero_documento = '55667788'", empresaA, empresaB);
            jdbc.update("DELETE FROM movimiento_inventario WHERE empresa_id IN (?, ?) AND referencia_tipo IN ('VENTA', 'DEVOLUCION_VENTA')",
                    empresaA, empresaB);
            jdbc.update("DELETE FROM autorizacion_supervisor WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM movimiento_caja WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM turno_caja WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM caja WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM precio_producto WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("UPDATE stock_producto_bodega SET cantidad = ? WHERE producto_id = ? AND bodega_id = ?", stockAntes,
                    productoA, bodegaA);
            jdbc.update("UPDATE producto SET costo_referencia = NULL, costo_promedio = NULL WHERE id = ?", productoA);
            jdbc.update("UPDATE usuario SET pin_supervisor_hash = NULL, pin_intentos_fallidos = 0, pin_bloqueado_hasta = NULL "
                    + "WHERE empresa_id IN (?, ?)", empresaA, empresaB);
        }
    }

    @Test
    void losReportesDeVentasYCajaCuadranYElCuadreDiarioDetectaLoQueNoCuadraSinCruzarEmpresas() throws Exception {
        String json = MediaType.APPLICATION_JSON_VALUE;
        com.fasterxml.jackson.databind.ObjectMapper lector = new com.fasterxml.jackson.databind.ObjectMapper();
        String duenoA = token(empresaA, usuarioA, "ADMIN_SISTEMA");
        String cajeroA = token(empresaA, usuarioA2, "CAJERO");
        String duenoB = token(empresaB, usuarioB, "ADMIN_SISTEMA");
        String hoy = java.time.LocalDate.now().toString();
        Long sedeA = jdbc.queryForObject("SELECT sede_id FROM bodega WHERE id = ?", Long.class, bodegaA);
        java.math.BigDecimal stockAntes = jdbc.queryForObject("SELECT COALESCE(SUM(cantidad), 0) FROM stock_producto_bodega "
                + "WHERE producto_id = ? AND bodega_id = ?", java.math.BigDecimal.class, productoA, bodegaA);
        java.util.function.Function<String, com.fasterxml.jackson.databind.JsonNode> leer = url -> {
            try {
                return lector.readTree(mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, duenoA)).andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
        java.util.function.Function<com.fasterxml.jackson.databind.JsonNode, java.math.BigDecimal> dinero =
                n -> new java.math.BigDecimal(n.asText());
        try {
            long generalA = lector.readTree(mvc.perform(get("/api/ventas/listas").header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andReturn().getResponse().getContentAsString()).get(0).get("id").asLong();
            jdbc.update("UPDATE producto SET costo_referencia = 6000, costo_promedio = 6000, vendible = 1 WHERE id = ?", productoA);
            mvc.perform(put("/api/ventas/precios").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                    .content("{\"listaId\":" + generalA + ",\"productoId\":" + productoA + ",\"precio\":10000}"))
                    .andExpect(status().isOk());
            jdbc.update("INSERT INTO stock_producto_bodega (empresa_id, producto_id, bodega_id, cantidad) VALUES (?, ?, ?, 20) "
                    + "ON DUPLICATE KEY UPDATE cantidad = 20", empresaA, productoA, bodegaA);
            long caja = lector.readTree(mvc.perform(post("/api/cajas").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                            .content("{\"nombre\":\"Caja reportes\",\"sedeId\":" + sedeA + ",\"bodegaId\":" + bodegaA
                                    + ",\"prefijoConsecutivo\":\"R1\",\"topeEfectivo\":0}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
            long turno = lector.readTree(mvc.perform(post("/api/turnos").header(HttpHeaders.AUTHORIZATION, cajeroA)
                            .contentType(json).content("{\"cajaId\":" + caja + ",\"baseApertura\":50000}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
            mvc.perform(put("/api/autorizaciones/mi-pin").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                    .content("{\"passwordActual\":\"" + CLAVE + "\",\"pin\":\"123456\"}")).andExpect(status().isNoContent());
            java.util.function.BiFunction<String, Long, String> permiso = (accion, ventaId) -> {
                try {
                    return lector.readTree(mvc.perform(post("/api/autorizaciones").header(HttpHeaders.AUTHORIZATION, cajeroA)
                                    .contentType(json).content("{\"correo\":\"admin@mismo.com\",\"pin\":\"123456\",\"accion\":\""
                                            + accion + "\",\"turnoId\":" + turno + ",\"referenciaId\":" + ventaId + "}"))
                            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("token").asText();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            };
            java.util.function.BiFunction<String, String, com.fasterxml.jackson.databind.JsonNode> vender = (clave, cuerpo) -> {
                try {
                    return lector.readTree(mvc.perform(post("/api/ventas").header(HttpHeaders.AUTHORIZATION, cajeroA)
                                    .header("Idempotency-Key", clave).contentType(json).content(cuerpo))
                            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            };
            String linea = "{\"productoId\":" + productoA + ",\"cantidad\":%s}";

            // Ventas del día: 2 en efectivo, 3 con tarjeta (luego se devuelve 1) y 1 anulada que no cuenta
            long venta1 = vender.apply("rp-1", "{\"lineas\":[" + linea.formatted(2)
                    + "],\"pagos\":[{\"medio\":\"EFECTIVO\",\"monto\":20000}]}").get("id").asLong();
            com.fasterxml.jackson.databind.JsonNode v2 = vender.apply("rp-2", "{\"lineas\":[" + linea.formatted(3)
                    + "],\"pagos\":[{\"medio\":\"TARJETA_CREDITO\",\"monto\":30000,\"ultimos4\":\"4242\",\"referencia\":\"A1\"}]}");
            long venta2 = v2.get("id").asLong();
            mvc.perform(post("/api/devoluciones").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content("{\"ventaId\":" + venta2 + ",\"motivo\":\"No era el calibre\",\"formaReembolso\":\"MISMO_MEDIO\","
                                    + "\"lineas\":[{\"ventaLineaId\":" + v2.get("lineas").get(0).get("id").asLong()
                                    + ",\"cantidad\":1,\"estadoProducto\":\"BUENO\"}],\"autorizacion\":\""
                                    + permiso.apply("DEVOLUCION", venta2) + "\"}"))
                    .andExpect(status().isCreated());
            long venta3 = vender.apply("rp-3", "{\"lineas\":[" + linea.formatted(1)
                    + "],\"pagos\":[{\"medio\":\"EFECTIVO\",\"monto\":10000}]}").get("id").asLong();
            mvc.perform(post("/api/ventas/" + venta3 + "/anular").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content("{\"motivo\":\"Se registró dos veces\",\"autorizacion\":\"" + permiso.apply("ANULAR_VENTA", venta3)
                                    + "\"}"))
                    .andExpect(status().isOk());

            // 1) ventas netas por producto: bruto 50.000, devuelto 10.000, 2 ventas, 4 unidades, costo 4 x 6.000
            com.fasterxml.jackson.databind.JsonNode porProducto = leer.apply("/api/reportes/ventas?agrupar=PRODUCTO");
            com.fasterxml.jackson.databind.JsonNode tot = porProducto.get("totales");
            assertEquals(2, tot.get("ventas").asLong(), "la venta anulada no cuenta");
            assertEquals(0, new java.math.BigDecimal("50000").compareTo(dinero.apply(tot.get("bruto"))));
            assertEquals(0, new java.math.BigDecimal("10000").compareTo(dinero.apply(tot.get("devoluciones"))));
            assertEquals(0, new java.math.BigDecimal("40000").compareTo(dinero.apply(tot.get("neto"))));
            assertEquals(0, new java.math.BigDecimal("24000").compareTo(dinero.apply(tot.get("costo"))), "costo guardado en la línea");
            assertEquals(0, new java.math.BigDecimal("20000").compareTo(dinero.apply(tot.get("ticketPromedio"))));
            assertEquals(0, new java.math.BigDecimal("4").compareTo(dinero.apply(porProducto.get("filas").get(0).get("unidades"))));
            assertEquals(0, dinero.apply(tot.get("baseNeta")).subtract(dinero.apply(tot.get("costo")))
                    .compareTo(dinero.apply(tot.get("margen"))), "margen = base neta - costo");
            // 2) cualquier agrupación suma lo mismo, y por medio de pago también
            for (String a : List.of("DIA", "SEMANA", "MES", "CATEGORIA", "CLIENTE", "VENDEDOR", "CAJERO", "CAJA", "MEDIO_PAGO")) {
                com.fasterxml.jackson.databind.JsonNode r = leer.apply("/api/reportes/ventas?agrupar=" + a);
                java.math.BigDecimal suma = java.math.BigDecimal.ZERO;
                for (com.fasterxml.jackson.databind.JsonNode f : r.get("filas")) {
                    suma = suma.add(dinero.apply(f.get("neto")));
                }
                java.math.BigDecimal esperado = "MEDIO_PAGO".equals(a) ? dinero.apply(r.get("totales").get("neto"))
                        : dinero.apply(r.get("totales").get("neto")).subtract(dinero.apply(r.get("totales").get("redondeo")));
                assertEquals(0, esperado.compareTo(suma), "la suma por " + a + " cuadra con el total");
            }
            com.fasterxml.jackson.databind.JsonNode medios = leer.apply("/api/reportes/ventas?agrupar=MEDIO_PAGO");
            assertEquals("Efectivo", medios.get("filas").get(0).get("nombre").asText());
            // 3) el cajero ve lo suyo; un vendedor sin pedidos no ve nada
            mvc.perform(get("/api/reportes/ventas").header(HttpHeaders.AUTHORIZATION, cajeroA))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.soloPropio").value(true))
                    .andExpect(jsonPath("$.totales.ventas").value(2));
            mvc.perform(get("/api/reportes/ventas").header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "VENDEDOR")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totales.ventas").value(0));
            mvc.perform(get("/api/reportes/caja").header(HttpHeaders.AUTHORIZATION, cajeroA)).andExpect(status().isForbidden());
            mvc.perform(get("/api/reportes/tablero/ventas").header(HttpHeaders.AUTHORIZATION, cajeroA))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/reportes/ventas?desde=" + hoy + "&hasta=2020-01-01").header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(status().isBadRequest());

            // 4) caja: el turno, la anulación y sus totales
            com.fasterxml.jackson.databind.JsonNode cajaR = leer.apply("/api/reportes/caja");
            assertEquals(1, cajaR.get("totales").get("turnos").asLong());
            assertEquals(1, cajaR.get("anulaciones").size());
            assertEquals(0, new java.math.BigDecimal("10000").compareTo(dinero.apply(cajaR.get("totales").get("anulaciones"))));
            assertEquals("Se registró dos veces", cajaR.get("anulaciones").get(0).get("motivo").asText());

            // 5) tablero del dueño: lo de hoy
            com.fasterxml.jackson.databind.JsonNode tablero = leer.apply("/api/reportes/tablero/ventas");
            assertEquals(0, new java.math.BigDecimal("40000").compareTo(dinero.apply(tablero.get("ventasHoy"))));
            assertEquals(2, tablero.get("numeroVentasHoy").asLong());
            assertEquals(productoA.longValue(), tablero.get("masVendidos").get(0).get("clave").asLong());

            // 6) exportación en Excel y PDF con los mismos datos
            mvc.perform(get("/api/exportaciones/reportes/ventas?formato=xlsx&agrupar=PRODUCTO").header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string(HttpHeaders.CONTENT_TYPE,
                            org.hamcrest.Matchers.containsString("spreadsheetml")));
            mvc.perform(get("/api/exportaciones/reportes/caja?formato=pdf").header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string(HttpHeaders.CONTENT_TYPE,
                            org.hamcrest.Matchers.containsString("pdf")));
            mvc.perform(get("/api/exportaciones/reportes/ventas?formato=csv").header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(status().is4xxClientError());

            // 7) cuadre diario: hoy cuadra
            String cuadre = "/api/reportes/cuadre/" + hoy;
            mvc.perform(post(cuadre).header(HttpHeaders.AUTHORIZATION, cajeroA)).andExpect(status().isForbidden());
            mvc.perform(post(cuadre).header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.cuadra").value(true))
                    .andExpect(jsonPath("$.ventasConfirmadas").value(60000.0))
                    .andExpect(jsonPath("$.ventasEnCaja").value(60000.0));
            // 8) se provocan tres descuadres: caja de menos, una salida de inventario de más y un turno de 25 horas
            int avisos = jdbc.queryForObject("SELECT COUNT(*) FROM notificacion WHERE evento = 'CUADRE_DESCUADRE' AND usuario_id = ?",
                    Integer.class, usuarioA);
            jdbc.update("UPDATE movimiento_caja SET monto = monto - 1000 WHERE tipo = 'VENTA' AND referencia_id = ?", venta1);
            jdbc.update("UPDATE movimiento_inventario SET cantidad = cantidad + 1 WHERE tipo = 'SALIDA_VENTA' "
                    + "AND referencia_tipo = 'VENTA' AND referencia_id = ?", venta2);
            jdbc.update("UPDATE turno_caja SET abierto_en = ? WHERE id = ?",
                    java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusHours(25)), turno);
            mvc.perform(post(cuadre).header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.cuadra").value(false))
                    .andExpect(jsonPath("$.ventas.length()").value(2))
                    .andExpect(jsonPath("$.turnosAbiertos24h").value(1))
                    .andExpect(jsonPath("$.turnos[0].turnoId").value(turno));
            assertEquals(avisos + 1, jdbc.queryForObject("SELECT COUNT(*) FROM notificacion WHERE evento = 'CUADRE_DESCUADRE' "
                    + "AND usuario_id = ?", Integer.class, usuarioA), "el dueño recibe el aviso");
            mvc.perform(get("/api/reportes/cuadre?soloDescuadres=true").header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(jsonPath("$.contenido[0].fecha").value(hoy))
                    .andExpect(jsonPath("$.contenido[0].ventas[0].ventaId").value(venta1));
            // 9) corregido y revisado de nuevo, el día queda cuadrado (una sola fila por día)
            jdbc.update("UPDATE movimiento_caja SET monto = monto + 1000 WHERE tipo = 'VENTA' AND referencia_id = ?", venta1);
            jdbc.update("UPDATE movimiento_inventario SET cantidad = cantidad - 1 WHERE tipo = 'SALIDA_VENTA' "
                    + "AND referencia_tipo = 'VENTA' AND referencia_id = ?", venta2);
            jdbc.update("UPDATE turno_caja SET abierto_en = ? WHERE id = ?", java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()), turno);
            mvc.perform(post(cuadre).header(HttpHeaders.AUTHORIZATION, duenoA)).andExpect(jsonPath("$.cuadra").value(true));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM cuadre_diario WHERE empresa_id = ? AND fecha = ?",
                    Integer.class, empresaA, java.sql.Date.valueOf(hoy)));
            mvc.perform(get("/api/reportes/cuadre?soloDescuadres=true").header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(jsonPath("$.totalElementos").value(0));

            // 10) nada de A llega a B
            mvc.perform(get("/api/reportes/ventas?agrupar=PRODUCTO").header(HttpHeaders.AUTHORIZATION, duenoB))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totales.ventas").value(0))
                    .andExpect(jsonPath("$.filas.length()").value(0));
            mvc.perform(get("/api/reportes/caja").header(HttpHeaders.AUTHORIZATION, duenoB))
                    .andExpect(jsonPath("$.turnos.length()").value(0)).andExpect(jsonPath("$.anulaciones.length()").value(0));
            mvc.perform(get("/api/reportes/tablero/ventas").header(HttpHeaders.AUTHORIZATION, duenoB))
                    .andExpect(jsonPath("$.numeroVentasHoy").value(0));
            mvc.perform(get("/api/reportes/cuadre").header(HttpHeaders.AUTHORIZATION, duenoB))
                    .andExpect(jsonPath("$.totalElementos").value(0));
            mvc.perform(post(cuadre).header(HttpHeaders.AUTHORIZATION, duenoB))
                    .andExpect(jsonPath("$.cuadra").value(true)).andExpect(jsonPath("$.ventasConfirmadas").value(0.0));
        } finally {
            jdbc.update("DELETE FROM cuadre_diario WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM saldo_favor_cliente WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE l FROM devolucion_venta_linea l JOIN devolucion_venta d ON d.id = l.devolucion_id "
                    + "WHERE d.empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM devolucion_venta WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE p FROM venta_pago p JOIN venta v ON v.id = p.venta_id WHERE v.empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE l FROM venta_linea l JOIN venta v ON v.id = l.venta_id WHERE v.empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM venta WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM movimiento_inventario WHERE empresa_id IN (?, ?) AND referencia_tipo IN ('VENTA', 'DEVOLUCION_VENTA')",
                    empresaA, empresaB);
            jdbc.update("DELETE FROM autorizacion_supervisor WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM movimiento_caja WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM turno_caja WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM caja WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM precio_producto WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("UPDATE stock_producto_bodega SET cantidad = ? WHERE producto_id = ? AND bodega_id = ?", stockAntes,
                    productoA, bodegaA);
            jdbc.update("UPDATE producto SET costo_referencia = NULL, costo_promedio = NULL WHERE id = ?", productoA);
            jdbc.update("UPDATE usuario SET pin_supervisor_hash = NULL, pin_intentos_fallidos = 0, pin_bloqueado_hasta = NULL "
                    + "WHERE empresa_id IN (?, ?)", empresaA, empresaB);
        }
    }

    @Test
    void elCajeroQueLlegaAlUmbralDeAnulacionesODescuentosAvisaUnaSolaVezAlDia() throws Exception {
        String json = MediaType.APPLICATION_JSON_VALUE;
        com.fasterxml.jackson.databind.ObjectMapper lector = new com.fasterxml.jackson.databind.ObjectMapper();
        String duenoA = token(empresaA, usuarioA, "ADMIN_SISTEMA");
        String cajeroA = token(empresaA, usuarioA2, "CAJERO");
        Long sedeA = jdbc.queryForObject("SELECT sede_id FROM bodega WHERE id = ?", Long.class, bodegaA);
        java.math.BigDecimal stockAntes = jdbc.queryForObject("SELECT COALESCE(SUM(cantidad), 0) FROM stock_producto_bodega "
                + "WHERE producto_id = ? AND bodega_id = ?", java.math.BigDecimal.class, productoA, bodegaA);
        java.util.function.IntSupplier avisos = () -> jdbc.queryForObject("SELECT COUNT(*) FROM notificacion WHERE "
                + "evento = 'ALERTA_CAJERO' AND usuario_id = ?", Integer.class, usuarioA);
        try {
            long generalA = lector.readTree(mvc.perform(get("/api/ventas/listas").header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andReturn().getResponse().getContentAsString()).get(0).get("id").asLong();
            jdbc.update("UPDATE producto SET costo_referencia = 6000, costo_promedio = 6000, vendible = 1 WHERE id = ?", productoA);
            mvc.perform(put("/api/ventas/precios").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                    .content("{\"listaId\":" + generalA + ",\"productoId\":" + productoA + ",\"precio\":10000}"))
                    .andExpect(status().isOk());
            jdbc.update("INSERT INTO stock_producto_bodega (empresa_id, producto_id, bodega_id, cantidad) VALUES (?, ?, ?, 20) "
                    + "ON DUPLICATE KEY UPDATE cantidad = 20", empresaA, productoA, bodegaA);
            long caja = lector.readTree(mvc.perform(post("/api/cajas").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                            .content("{\"nombre\":\"Caja alertas\",\"sedeId\":" + sedeA + ",\"bodegaId\":" + bodegaA
                                    + ",\"prefijoConsecutivo\":\"AL\",\"topeEfectivo\":0}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
            long turno = lector.readTree(mvc.perform(post("/api/turnos").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content("{\"cajaId\":" + caja + ",\"baseApertura\":0}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
            mvc.perform(put("/api/autorizaciones/mi-pin").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                    .content("{\"passwordActual\":\"" + CLAVE + "\",\"pin\":\"123456\"}")).andExpect(status().isNoContent());

            // los umbrales los fija el dueño; el cajero no; B no cambia los de A
            mvc.perform(get("/api/cajas/configuracion").header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(jsonPath("$.umbralAnulacionesDia").value(3)).andExpect(jsonPath("$.umbralDescuentosDia").value(5));
            String umbrales = "{\"toleranciaDiferencia\":2000,\"umbralAnulacionesDia\":1,\"umbralDescuentosDia\":2}";
            mvc.perform(put("/api/cajas/configuracion").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json).content(umbrales))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/cajas/configuracion").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json).content(umbrales))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.umbralDescuentosDia").value(2));
            mvc.perform(put("/api/cajas/configuracion").header(HttpHeaders.AUTHORIZATION, token(empresaB, usuarioB, "ADMIN_SISTEMA"))
                    .contentType(json).content("{\"toleranciaDiferencia\":2000,\"umbralAnulacionesDia\":50}"))
                    .andExpect(status().isOk());
            assertEquals(1, jdbc.queryForObject("SELECT umbral_anulaciones_dia FROM empresa WHERE id = ?", Integer.class, empresaA));

            java.util.function.BiFunction<String, Long, String> permiso = (accion, referencia) -> {
                try {
                    return lector.readTree(mvc.perform(post("/api/autorizaciones").header(HttpHeaders.AUTHORIZATION, cajeroA)
                                    .contentType(json).content("{\"correo\":\"admin@mismo.com\",\"pin\":\"123456\",\"accion\":\"" + accion
                                            + "\",\"turnoId\":" + turno + (referencia == null ? "" : ",\"referenciaId\":" + referencia) + "}"))
                            .andReturn().getResponse().getContentAsString()).get("token").asText();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            };
            java.util.function.Function<Integer, Long> venderConDescuento = n -> {
                try {
                    return lector.readTree(mvc.perform(post("/api/ventas").header(HttpHeaders.AUTHORIZATION, cajeroA)
                                    .header("Idempotency-Key", "al-" + n).contentType(json)
                                    .content("{\"lineas\":[{\"productoId\":" + productoA + ",\"cantidad\":1,\"descuentoPct\":10}],"
                                            + "\"pagos\":[{\"medio\":\"EFECTIVO\",\"monto\":9000}],\"autorizacionDescuento\":\""
                                            + permiso.apply("DESCUENTO", null) + "\"}"))
                            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            };

            // descuentos: la primera venta no llega al umbral (2); la segunda avisa; la tercera ya no repite el aviso
            int antes = avisos.getAsInt();
            long venta1 = venderConDescuento.apply(1);
            assertEquals(antes, avisos.getAsInt());
            venderConDescuento.apply(2);
            assertEquals(antes + 1, avisos.getAsInt(), "al llegar al umbral avisa al dueño");
            venderConDescuento.apply(3);
            assertEquals(antes + 1, avisos.getAsInt(), "una sola vez por cajero, tipo y día");
            assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM notificacion n JOIN usuario u ON u.id = n.usuario_id "
                    + "JOIN usuario_rol ur ON ur.usuario_id = u.id JOIN rol r ON r.id = ur.rol_id "
                    + "WHERE n.evento = 'ALERTA_CAJERO' AND r.codigo = 'ADMIN_SISTEMA' AND u.empresa_id = ?", Integer.class, empresaA) >= 1);

            // anulaciones: con umbral 1, la primera anulación avisa
            mvc.perform(post("/api/ventas/" + venta1 + "/anular").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content("{\"motivo\":\"Error de digitación\",\"autorizacion\":\"" + permiso.apply("ANULAR_VENTA", venta1) + "\"}"))
                    .andExpect(status().isOk());
            assertEquals(antes + 2, avisos.getAsInt());
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM alerta_cajero WHERE empresa_id = ? AND cajero_usuario_id = ?",
                    Integer.class, empresaA, usuarioA2));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM alerta_cajero WHERE empresa_id = ?", Integer.class, empresaB));
        } finally {
            jdbc.update("DELETE FROM alerta_cajero WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("UPDATE empresa SET umbral_anulaciones_dia = 3, umbral_descuentos_dia = 5 WHERE id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE p FROM venta_pago p JOIN venta v ON v.id = p.venta_id WHERE v.empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE l FROM venta_linea l JOIN venta v ON v.id = l.venta_id WHERE v.empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM venta WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM movimiento_inventario WHERE empresa_id IN (?, ?) AND referencia_tipo = 'VENTA'", empresaA, empresaB);
            jdbc.update("DELETE FROM autorizacion_supervisor WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM movimiento_caja WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM turno_caja WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM caja WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM precio_producto WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("UPDATE stock_producto_bodega SET cantidad = ? WHERE producto_id = ? AND bodega_id = ?", stockAntes,
                    productoA, bodegaA);
            jdbc.update("UPDATE producto SET costo_referencia = NULL, costo_promedio = NULL WHERE id = ?", productoA);
            jdbc.update("UPDATE usuario SET pin_supervisor_hash = NULL, pin_intentos_fallidos = 0, pin_bloqueado_hasta = NULL "
                    + "WHERE empresa_id IN (?, ?)", empresaA, empresaB);
        }
    }

    @Test
    void impuestosListasCargaDePreciosCajasYPinRechazanLoInvalidoSinCruzarEmpresas() throws Exception {
        // Spec 010: cobertura de los caminos de error de precios, cajas y autorizaciones (además de la fuga entre empresas)
        String json = MediaType.APPLICATION_JSON_VALUE;
        com.fasterxml.jackson.databind.ObjectMapper lector = new com.fasterxml.jackson.databind.ObjectMapper();
        String duenoA = token(empresaA, usuarioA, "ADMIN_SISTEMA");
        String duenoB = token(empresaB, usuarioB, "ADMIN_SISTEMA");
        String cajeroA = token(empresaA, usuarioA2, "CAJERO");
        Long sedeA = jdbc.queryForObject("SELECT sede_id FROM bodega WHERE id = ?", Long.class, bodegaA);
        try {
            // impuestos: crear, no repetir el código, desactivar; B no toca los de A
            long tarifa = lector.readTree(mvc.perform(post("/api/ventas/impuestos").header(HttpHeaders.AUTHORIZATION, duenoA)
                            .contentType(json).content("{\"codigo\":\"IVA_PRUEBA\",\"nombre\":\"IVA de prueba\",\"porcentaje\":8}"))
                    .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString()).get("id").asLong();
            mvc.perform(post("/api/ventas/impuestos").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                            .content("{\"codigo\":\"IVA_PRUEBA\",\"nombre\":\"Otro\",\"porcentaje\":8}"))
                    .andExpect(status().is4xxClientError());
            mvc.perform(patch("/api/ventas/impuestos/" + tarifa + "?activo=false").header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
            mvc.perform(patch("/api/ventas/impuestos/" + tarifa + "?activo=true").header(HttpHeaders.AUTHORIZATION, duenoB))
                    .andExpect(status().isNotFound());

            // listas: crear, renombrar, no repetir el nombre, desactivar
            long lista = lector.readTree(mvc.perform(post("/api/ventas/listas").header(HttpHeaders.AUTHORIZATION, duenoA)
                            .contentType(json).content("{\"nombre\":\"Lista prueba 010\"}"))
                    .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString()).get("id").asLong();
            mvc.perform(put("/api/ventas/listas/" + lista).header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                            .content("{\"nombre\":\"Lista prueba 010 B\",\"activo\":false}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Lista prueba 010 B"));
            mvc.perform(post("/api/ventas/listas").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                            .content("{\"nombre\":\"Lista prueba 010 B\"}"))
                    .andExpect(status().is4xxClientError());
            mvc.perform(put("/api/ventas/listas/" + lista).header(HttpHeaders.AUTHORIZATION, duenoB).contentType(json)
                            .content("{\"nombre\":\"De B\"}"))
                    .andExpect(status().isNotFound());

            // carga de precios: la plantilla se descarga; archivos y filas malas no guardan nada
            byte[] plantilla = mvc.perform(get("/api/ventas/precios/carga/plantilla").header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            assertTrue(plantilla.length > 0);
            mvc.perform(multipart("/api/ventas/precios/carga").file(new org.springframework.mock.web.MockMultipartFile("archivo",
                            "precios.csv", "text/csv", "codigo;precio".getBytes())).header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ARCHIVO_INVALIDO"));
            mvc.perform(multipart("/api/ventas/precios/carga").file(new org.springframework.mock.web.MockMultipartFile("archivo",
                            "precios.xlsx", "application/octet-stream", new byte[0])).header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ARCHIVO_VACIO"));
            mvc.perform(multipart("/api/ventas/precios/carga").file(new org.springframework.mock.web.MockMultipartFile("archivo",
                            "precios.xlsx", "application/octet-stream", "esto no es un excel".getBytes())).header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ARCHIVO_INVALIDO"));
            var sinColumnas = excel(new String[] { "producto", "valor" }, new Object[] { "P-001", 10 });
            mvc.perform(multipart("/api/ventas/precios/carga").file(new org.springframework.mock.web.MockMultipartFile("archivo",
                            "precios.xlsx", sinColumnas.getContentType(), sinColumnas.getBytes())).header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ARCHIVO_INVALIDO"));
            String codigoA = jdbc.queryForObject("SELECT codigo FROM producto WHERE id = ?", String.class, productoA);
            String[] cabecera = { "codigo", "lista", "unidad", "precio", "precio_minimo", "motivo" };
            String incompatible = jdbc.queryForObject("SELECT codigo FROM unidad_medida WHERE activo = 1 AND magnitud <> "
                    + "(SELECT u.magnitud FROM unidad_medida u JOIN producto p ON p.unidad_medida_id = u.id WHERE p.id = ?) LIMIT 1",
                    String.class, productoA);
            var malas = excel(cabecera,
                    new Object[] { "NO-EXISTE", "General", null, 1000, null, null },
                    new Object[] { codigoA, "General", null, "abc", null, null },
                    new Object[] { codigoA, "General", null, -5, null, null },
                    new Object[] { codigoA, "General", null, 1000, 2000, null },
                    new Object[] { codigoA, "Lista que no existe", null, 1000, null, null },
                    new Object[] { codigoA, "General", "UNIDAD-RARA", 1000, null, null },
                    new Object[] { codigoA, "General", null, null, null, null },
                    new Object[] { null, "General", null, 1000, null, null },
                    new Object[] { null, null, null, null, null, null },
                    new Object[] { codigoA, "Lista prueba 010 B", null, 1000, null, null },
                    new Object[] { codigoA, "General", incompatible, 1000, null, null },
                    new Object[] { codigoA, "General", null, 1000, 0, null },
                    new Object[] { codigoA, "General", null, 1000, null, "x".repeat(301) },
                    new Object[] { codigoA, "General", null, "1500,5", null, null },
                    new Object[] { codigoA, "General", null, 1e20, null, null },
                    new Object[] { codigoA, "General", null, 10.123, null, null });
            mvc.perform(multipart("/api/ventas/precios/carga").file(new org.springframework.mock.web.MockMultipartFile("archivo",
                            "precios.xlsx", malas.getContentType(), malas.getBytes())).param("soloValidar", "true")
                            .header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.aplicado").value(false))
                    .andExpect(jsonPath("$.errores.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(13)));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM precio_producto WHERE empresa_id = ?", Integer.class, empresaA));
            var buena = excel(cabecera, new Object[] { codigoA, "General", null, 1500, 1200, "Precio de prueba" });
            mvc.perform(multipart("/api/ventas/precios/carga").file(new org.springframework.mock.web.MockMultipartFile("archivo",
                            "precios.xlsx", buena.getContentType(), buena.getBytes())).param("soloValidar", "true")
                            .header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.aplicado").value(false));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM precio_producto WHERE empresa_id = ?", Integer.class, empresaA),
                    "solo validar no guarda nada");

            // cajas: nombre y prefijo únicos, editar, no desactivar con turno abierto, la de otra empresa no existe
            String caja = "{\"nombre\":\"%s\",\"sedeId\":" + sedeA + ",\"bodegaId\":" + bodegaA
                    + ",\"prefijoConsecutivo\":\"%s\",\"topeEfectivo\":1000%s}";
            long cajaId = lector.readTree(mvc.perform(post("/api/cajas").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                            .content(caja.formatted("Caja cobertura", "CV", "")))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
            mvc.perform(post("/api/cajas").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                            .content(caja.formatted("caja COBERTURA", "CW", "")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CAJA_DUPLICADA"));
            mvc.perform(post("/api/cajas").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                            .content(caja.formatted("Caja cobertura 2", "cv", "")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PREFIJO_DUPLICADO"));
            mvc.perform(put("/api/cajas/" + cajaId).header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                            .content(caja.formatted("Caja cobertura editada", "CV", "")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Caja cobertura editada"));
            mvc.perform(put("/api/cajas/" + cajaId).header(HttpHeaders.AUTHORIZATION, duenoB).contentType(json)
                            .content(caja.formatted("De B", "CB", "")))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/cajas?activa=true").header(HttpHeaders.AUTHORIZATION, cajeroA))
                    .andExpect(jsonPath("$[?(@.id==" + cajaId + ")]").isNotEmpty());
            long turno = lector.readTree(mvc.perform(post("/api/turnos").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content("{\"cajaId\":" + cajaId + ",\"baseApertura\":0}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
            mvc.perform(put("/api/cajas/" + cajaId).header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                            .content(caja.formatted("Caja cobertura editada", "CV", ",\"activa\":false")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CAJA_EN_USO"));

            // PIN: saber si se tiene; quien no es supervisor no autoriza; nadie autoriza lo suyo
            mvc.perform(get("/api/autorizaciones/mi-pin").header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(jsonPath("$.tienePin").value(false));
            mvc.perform(put("/api/autorizaciones/mi-pin").header(HttpHeaders.AUTHORIZATION, duenoA).contentType(json)
                    .content("{\"passwordActual\":\"" + CLAVE + "\",\"pin\":\"654321\"}")).andExpect(status().isNoContent());
            mvc.perform(get("/api/autorizaciones/mi-pin").header(HttpHeaders.AUTHORIZATION, duenoA))
                    .andExpect(jsonPath("$.tienePin").value(true));
            String pedir = "{\"correo\":\"%s\",\"pin\":\"654321\",\"accion\":\"RETIRO\",\"turnoId\":" + turno + "}";
            mvc.perform(post("/api/autorizaciones").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content(pedir.formatted("segundo@mismo.com")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("AUTORIZACION_INVALIDA"));
            mvc.perform(post("/api/autorizaciones").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content(pedir.formatted("nadie@mismo.com")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("AUTORIZACION_INVALIDA"));
            mvc.perform(post("/api/turnos/" + turno + "/cerrar").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                    .content("{\"contadoEfectivo\":0}")).andExpect(status().isOk());
            mvc.perform(post("/api/autorizaciones").header(HttpHeaders.AUTHORIZATION, cajeroA).contentType(json)
                            .content(pedir.formatted("admin@mismo.com")))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("TURNO_NO_ABIERTO"));
            long turnoDueno = lector.readTree(mvc.perform(post("/api/turnos").header(HttpHeaders.AUTHORIZATION,
                            token(empresaA, usuarioA, "CAJERO")).contentType(json).content("{\"cajaId\":" + cajaId + ",\"baseApertura\":0}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
            mvc.perform(post("/api/autorizaciones").header(HttpHeaders.AUTHORIZATION, token(empresaA, usuarioA, "CAJERO"))
                            .contentType(json).content("{\"correo\":\"admin@mismo.com\",\"pin\":\"654321\",\"accion\":\"GASTO\","
                                    + "\"turnoId\":" + turnoDueno + "}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("AUTORIZA_LO_SUYO"));
        } finally {
            jdbc.update("DELETE FROM autorizacion_supervisor WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM movimiento_caja WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM turno_caja WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM caja WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM precio_producto WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM lista_precio WHERE empresa_id = ? AND nombre LIKE 'Lista prueba 010%'", empresaA);
            jdbc.update("DELETE FROM tarifa_impuesto WHERE empresa_id = ? AND codigo = 'IVA_PRUEBA'", empresaA);
            jdbc.update("UPDATE usuario SET pin_supervisor_hash = NULL, pin_intentos_fallidos = 0, pin_bloqueado_hasta = NULL "
                    + "WHERE empresa_id IN (?, ?)", empresaA, empresaB);
        }
    }

    @Test
    void elCatalogoDelProveedorSeCruzaConElInventarioSinQueElProveedorVeaNadaNuestro() throws Exception {
        String portalA = tokenProveedor(empresaA, usuarioA, proveedorA);
        String comprasA = token(empresaA, usuarioA, "ADMIN_COMPRAS");
        String[] cabecera = { "codigo", "descripcion", "marca", "unidad", "precio", "incluye_iva" };
        String unidad = jdbc.queryForObject("SELECT u.codigo FROM producto p JOIN unidad_medida u ON u.id = p.unidad_medida_id "
                + "WHERE p.id = ?", String.class, productoA);
        try {
            jdbc.update("UPDATE producto SET costo_referencia = 1000 WHERE id = ?", productoA);
            // el proveedor sube su catálogo: uno se parece a nuestro producto, otro no lo manejamos
            var archivo = excel(cabecera, new Object[] { "PR-77", "Cable de la empresa A calibre 12", "Centelsa", unidad, 900, "no" },
                    new Object[] { "PR-78", "Guantes de carnaza talla L", null, "UND", 5000, "si" });
            mvc.perform(multipart("/api/portal/catalogo").file(new org.springframework.mock.web.MockMultipartFile(
                            "archivo", "catalogo.xlsx", archivo.getContentType(), archivo.getBytes()))
                            .header(HttpHeaders.AUTHORIZATION, portalA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.aplicado").value(true))
                    .andExpect(jsonPath("$.nuevos").value(2));
            // el proveedor ve su catálogo sin ninguna equivalencia
            mvc.perform(get("/api/portal/catalogo").header(HttpHeaders.AUTHORIZATION, portalA))
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].productoId").doesNotExist())
                    .andExpect(jsonPath("$[0].estadoEquivalencia").doesNotExist());

            // compras ve la sugerencia, la confirma y aparece la oportunidad (10 % más barato)
            String sugeridos = mvc.perform(get("/api/catalogos?estado=SUGERIDA").header(HttpHeaders.AUTHORIZATION, comprasA))
                    .andExpect(jsonPath("$.contenido[0].productoId").value(productoA))
                    .andReturn().getResponse().getContentAsString();
            long item = new com.fasterxml.jackson.databind.ObjectMapper().readTree(sugeridos).get("contenido").get(0).get("id").asLong();
            mvc.perform(get("/api/catalogos/oportunidades").header(HttpHeaders.AUTHORIZATION, comprasA))
                    .andExpect(jsonPath("$").isEmpty());    // sin confirmar no hay oportunidad
            mvc.perform(patch("/api/catalogos/" + item).header(HttpHeaders.AUTHORIZATION, comprasA)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"CONFIRMADA\",\"productoId\":" + productoA + "}"))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/catalogos/oportunidades").header(HttpHeaders.AUTHORIZATION, comprasA))
                    .andExpect(jsonPath("$[0].productoId").value(productoA))
                    .andExpect(jsonPath("$[0].ahorroPct").value(10.0));

            // la otra empresa no ve el catálogo ni puede decidir sobre él; su producto no sirve como equivalencia
            mvc.perform(get("/api/catalogos").header(HttpHeaders.AUTHORIZATION, token(empresaB, usuarioB, "ADMIN_COMPRAS")))
                    .andExpect(jsonPath("$.totalElementos").value(0));
            mvc.perform(patch("/api/catalogos/" + item).header(HttpHeaders.AUTHORIZATION, token(empresaB, usuarioB, "ADMIN_COMPRAS"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"DESCARTADA\"}"))
                    .andExpect(status().isNotFound());
            mvc.perform(patch("/api/catalogos/" + item).header(HttpHeaders.AUTHORIZATION, comprasA)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"CONFIRMADA\",\"productoId\":" + productoB + "}"))
                    .andExpect(status().isNotFound());
            // el proveedor no entra a la vista de compras
            mvc.perform(get("/api/catalogos/oportunidades").header(HttpHeaders.AUTHORIZATION, portalA))
                    .andExpect(status().isForbidden());

            // al volver a subir sin el guante, queda inactivo y la equivalencia confirmada se conserva
            var segundo = excel(cabecera, new Object[] { "PR-77", "Cable de la empresa A calibre 12", "Centelsa", unidad, 950, "no" });
            mvc.perform(multipart("/api/portal/catalogo").file(new org.springframework.mock.web.MockMultipartFile(
                            "archivo", "catalogo.xlsx", segundo.getContentType(), segundo.getBytes()))
                            .header(HttpHeaders.AUTHORIZATION, portalA))
                    .andExpect(jsonPath("$.actualizados").value(1))
                    .andExpect(jsonPath("$.retirados").value(1));
            org.junit.jupiter.api.Assertions.assertEquals("CONFIRMADA", jdbc.queryForObject(
                    "SELECT estado_equivalencia FROM catalogo_item WHERE id = ?", String.class, item));
        } finally {
            jdbc.update("DELETE FROM catalogo_item WHERE empresa_id IN (?, ?)", empresaA, empresaB);
            jdbc.update("DELETE FROM notificacion WHERE evento = 'CATALOGO_ACTUALIZADO'");
            jdbc.update("UPDATE producto SET costo_referencia = NULL WHERE id = ?", productoA);
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
