package com.proyecto.supply_core.inventario.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.catalogo.entity.CategoriaProducto;
import com.proyecto.supply_core.catalogo.entity.UnidadMedida;
import com.proyecto.supply_core.catalogo.repository.CategoriaProductoRepository;
import com.proyecto.supply_core.catalogo.repository.UnidadMedidaRepository;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.inventario.dto.CargaMasivaResponse;
import com.proyecto.supply_core.inventario.dto.CargaMasivaResponse.ErrorFila;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.MovimientoInventarioRepository;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;

/**
 * Carga y actualización masiva de productos, categorías y precios desde un Excel .xlsx (paso 18-d).
 * <ul>
 *   <li>Encabezados (sin importar mayúsculas ni tildes): {@code codigo} (obligatorio), {@code nombre}, {@code categoria},
 *       {@code unidad}, {@code descripcion}, {@code stock_minimo}, {@code stock_maximo}, {@code costo_referencia}.</li>
 *   <li>El código identifica el producto. Si existe se actualiza, y una celda vacía deja el dato como está, así que un
 *       archivo con solo {@code codigo} y {@code costo_referencia} sirve para actualizar precios. Si no existe se crea y
 *       exige nombre, categoría y unidad.</li>
 *   <li>La categoría se busca por nombre y se crea si no existe. La unidad se busca por código y debe existir y estar
 *       activa. No se cambia la unidad de un producto que ya tiene movimientos de inventario.</li>
 *   <li><b>Todo o nada:</b> se revisan todas las filas y, si alguna tiene error, no se guarda nada y se devuelven los
 *       errores por fila. Con {@code soloValidar} solo se revisa.</li>
 *   <li>Nunca toca el stock: este servicio no crea movimientos. Tampoco activa ni desactiva productos.</li>
 * </ul>
 */
@Service
public class ProductoCargaService {

    /** Tamaño máximo del archivo. */
    static final long MAX_BYTES = 5L * 1024 * 1024;
    /** Filas con datos máximas por archivo. */
    static final int MAX_FILAS = 2000;
    /** Encabezados de la plantilla, en orden. */
    static final List<String> COLUMNAS = List.of("codigo", "nombre", "categoria", "unidad", "descripcion",
            "stock_minimo", "stock_maximo", "costo_referencia");

    private final ProductoRepository productoRepository;
    private final CategoriaProductoRepository categoriaRepository;
    private final UnidadMedidaRepository unidadRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final AuditoriaService auditoriaService;
    /** Impuesto de venta por defecto de los productos nuevos; {@code null} en pruebas que no lo usan. */
    private final com.proyecto.supply_core.venta.repository.TarifaImpuestoRepository tarifaRepository;

    /**
     * @param productoRepository   productos existentes y nuevos
     * @param categoriaRepository  categorías por nombre
     * @param unidadRepository     unidades por código
     * @param movimientoRepository para no cambiar la unidad de un producto con movimientos
     * @param auditoriaService     registro de la carga
     */
    public ProductoCargaService(ProductoRepository productoRepository, CategoriaProductoRepository categoriaRepository,
            UnidadMedidaRepository unidadRepository, MovimientoInventarioRepository movimientoRepository,
            AuditoriaService auditoriaService) {
        this(productoRepository, categoriaRepository, unidadRepository, movimientoRepository, auditoriaService, null);
    }

    /**
     * @param productoRepository   productos existentes y nuevos
     * @param categoriaRepository  categorías por nombre
     * @param unidadRepository     unidades por código
     * @param movimientoRepository para no cambiar la unidad de un producto con movimientos
     * @param auditoriaService     registro de la carga
     * @param tarifaRepository     impuesto de venta por defecto (IVA 19 %) de los productos nuevos
     */
    @org.springframework.beans.factory.annotation.Autowired
    public ProductoCargaService(ProductoRepository productoRepository, CategoriaProductoRepository categoriaRepository,
            UnidadMedidaRepository unidadRepository, MovimientoInventarioRepository movimientoRepository,
            AuditoriaService auditoriaService,
            com.proyecto.supply_core.venta.repository.TarifaImpuestoRepository tarifaRepository) {
        this.tarifaRepository = tarifaRepository;
        this.productoRepository = productoRepository;
        this.categoriaRepository = categoriaRepository;
        this.unidadRepository = unidadRepository;
        this.movimientoRepository = movimientoRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Lee el Excel, revisa todas las filas y, si están bien y no se pidió solo validar, las guarda.
     *
     * @param archivo     Excel .xlsx con los productos
     * @param soloValidar {@code true} para revisar sin guardar
     * @return resultado con los conteos y los errores por fila
     * @throws NegocioException si el archivo está vacío, es muy grande, no es un .xlsx válido o tiene demasiadas filas
     */
    @Transactional
    public CargaMasivaResponse cargar(MultipartFile archivo, boolean soloValidar) {
        List<Fila> filas = leer(archivo);
        List<ErrorFila> errores = new ArrayList<>();
        Map<String, CategoriaProducto> categorias = new HashMap<>();
        Set<String> categoriasNuevas = new HashSet<>();
        Map<String, UnidadMedida> unidades = new HashMap<>();
        Set<String> vistos = new HashSet<>();
        List<Plan> plan = new ArrayList<>();
        int creados = 0;
        int actualizados = 0;

        for (Fila f : filas) {
            List<String> problemas = new ArrayList<>(f.problemas());
            String codigo = f.codigo() == null ? null : f.codigo().trim().toUpperCase(Locale.ROOT);
            if (codigo == null || codigo.isBlank()) {
                problemas.add("falta el código");
            } else if (codigo.length() > 50) {
                problemas.add("el código pasa de 50 caracteres");
            } else if (!vistos.add(codigo)) {
                problemas.add("el código " + codigo + " ya salió antes en el archivo");
            }
            Producto existente = codigo == null || codigo.isBlank() ? null
                    : productoRepository.findByCodigoIgnoreCase(codigo).orElse(null);

            UnidadMedida unidad = null;
            if (f.unidad() != null) {
                unidad = unidades.computeIfAbsent(f.unidad().toUpperCase(Locale.ROOT),
                        c -> unidadRepository.findByCodigoIgnoreCase(c).orElse(null));
                if (unidad == null || !unidad.isActivo()) {
                    problemas.add("la unidad " + f.unidad() + " no existe o está inactiva");
                    unidad = null;
                }
            }
            CategoriaProducto categoria = null;
            boolean categoriaNueva = false;
            if (f.categoria() != null) {
                String clave = f.categoria().toLowerCase(Locale.ROOT);
                if (!categorias.containsKey(clave)) {
                    categorias.put(clave, categoriaRepository.findByNombreIgnoreCase(f.categoria()).orElse(null));
                }
                categoria = categorias.get(clave);
                if (categoria != null && !categoria.isActivo()) {
                    problemas.add("la categoría " + f.categoria() + " está inactiva");
                } else if (categoria == null) {
                    categoriaNueva = true;
                }
            }

            if (existente == null) {
                if (f.nombre() == null) {
                    problemas.add("producto nuevo: falta el nombre");
                }
                if (f.categoria() == null) {
                    problemas.add("producto nuevo: falta la categoría");
                }
                if (f.unidad() == null) {
                    problemas.add("producto nuevo: falta la unidad");
                }
            } else if (unidad != null && !existente.getUnidadMedida().getId().equals(unidad.getId())
                    && movimientoRepository.existsByProductoId(existente.getId())) {
                problemas.add("no se puede cambiar la unidad: el producto " + codigo + " ya tiene movimientos de inventario");
            }
            BigDecimal minimo = f.stockMinimo() != null ? f.stockMinimo() : existente == null ? BigDecimal.ZERO
                    : existente.getStockMinimo();
            BigDecimal maximo = f.stockMaximo() != null ? f.stockMaximo() : existente == null ? null
                    : existente.getStockMaximo();
            if (maximo != null && minimo != null && maximo.compareTo(minimo) < 0) {
                problemas.add("el stock máximo no puede ser menor que el mínimo");
            }
            if (!problemas.isEmpty()) {
                errores.add(new ErrorFila(f.numero(), String.join("; ", problemas)));
                continue;
            }
            if (categoriaNueva) {
                categoriasNuevas.add(f.categoria());
            }
            if (existente == null) {
                creados++;
            } else {
                actualizados++;
            }
            plan.add(new Plan(f, codigo, existente, unidad, categoria, f.categoria()));
        }
        int categoriasCreadas = (int) categoriasNuevas.stream().map(s -> s.toLowerCase(Locale.ROOT)).distinct().count();
        if (!errores.isEmpty() || soloValidar) {
            return new CargaMasivaResponse(soloValidar, false, filas.size(), creados, actualizados, categoriasCreadas,
                    errores);
        }
        aplicar(plan, categorias);
        auditoriaService.exito(AccionAuditoria.PRODUCTO_CARGA_MASIVA, EntidadAuditada.PRODUCTO, null,
                "archivo=" + archivo.getOriginalFilename() + "; filas=" + filas.size() + "; creados=" + creados
                        + "; actualizados=" + actualizados + "; categoriasCreadas=" + categoriasCreadas);
        return new CargaMasivaResponse(false, true, filas.size(), creados, actualizados, categoriasCreadas, errores);
    }

    /**
     * Excel de ejemplo con los encabezados y una fila de muestra.
     *
     * @return bytes de un .xlsx
     */
    public byte[] plantilla() {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("productos");
            Row encabezado = hoja.createRow(0);
            for (int i = 0; i < COLUMNAS.size(); i++) {
                encabezado.createCell(i).setCellValue(COLUMNAS.get(i));
            }
            Row ejemplo = hoja.createRow(1);
            String[] valores = { "CAB-012", "Cable THHN #12 rollo 100 m", "Eléctricos", "UND", "Cobre, 600 V", "10",
                    "50", "285000" };
            for (int i = 0; i < valores.length; i++) {
                ejemplo.createCell(i).setCellValue(valores[i]);
            }
            for (int i = 0; i < COLUMNAS.size(); i++) {
                hoja.setColumnWidth(i, 6000);
            }
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar la plantilla", e);
        }
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Fila ya revisada y lista para guardar.
     *
     * @param fila       datos leídos
     * @param codigo     código normalizado
     * @param existente  producto que ya existe, o {@code null} si se crea
     * @param unidad     unidad indicada, o {@code null} si el archivo no la trae
     * @param categoria  categoría que ya existe, o {@code null} si no existe o no se indicó
     * @param nombreCategoria nombre de la categoría indicado en el archivo
     */
    private record Plan(Fila fila, String codigo, Producto existente, UnidadMedida unidad, CategoriaProducto categoria,
            String nombreCategoria) {
    }

    /**
     * Fila leída del Excel, con los textos ya recortados ({@code null} = celda vacía).
     *
     * @param numero      número de fila en el Excel
     * @param codigo      código
     * @param nombre      nombre
     * @param categoria   nombre de la categoría
     * @param unidad      código de la unidad
     * @param descripcion descripción
     * @param stockMinimo mínimo
     * @param stockMaximo máximo
     * @param costo       costo de referencia
     * @param problemas   errores de lectura de esta fila (números mal escritos, textos muy largos)
     */
    private record Fila(int numero, String codigo, String nombre, String categoria, String unidad, String descripcion,
            BigDecimal stockMinimo, BigDecimal stockMaximo, BigDecimal costo, List<String> problemas) {
    }

    /**
     * Guarda las categorías nuevas y los productos nuevos o actualizados.
     *
     * @param plan       filas revisadas
     * @param categorias categorías ya conocidas por nombre en minúsculas (se completa con las nuevas)
     */
    private void aplicar(List<Plan> plan, Map<String, CategoriaProducto> categorias) {
        for (Plan p : plan) {
            CategoriaProducto categoria = p.categoria();
            if (p.nombreCategoria() != null && categoria == null) {
                String clave = p.nombreCategoria().toLowerCase(Locale.ROOT);
                categoria = categorias.get(clave);
                if (categoria == null) {
                    CategoriaProducto nueva = new CategoriaProducto();
                    nueva.setNombre(p.nombreCategoria());
                    categoria = categoriaRepository.save(nueva);
                    categorias.put(clave, categoria);
                }
            }
            Producto producto = p.existente() != null ? p.existente() : new Producto();
            if (p.existente() == null && tarifaRepository != null) {
                tarifaRepository.findByCodigo("IVA19").ifPresent(producto::setTarifaImpuesto);
            }
            Fila f = p.fila();
            producto.setCodigo(p.codigo());
            if (f.nombre() != null) {
                producto.setNombre(f.nombre());
            }
            if (categoria != null) {
                producto.setCategoria(categoria);
            }
            if (p.unidad() != null) {
                producto.setUnidadMedida(p.unidad());
            }
            if (f.descripcion() != null) {
                producto.setDescripcion(f.descripcion());
            }
            if (f.stockMinimo() != null) {
                producto.setStockMinimo(f.stockMinimo());
            }
            if (f.stockMaximo() != null) {
                producto.setStockMaximo(f.stockMaximo());
            }
            if (f.costo() != null) {
                producto.setCostoReferencia(f.costo());
            }
            productoRepository.save(producto);
        }
    }

    /**
     * Lee el archivo: valida tamaño, extensión y formato, y devuelve las filas con datos.
     *
     * @param archivo archivo subido
     * @return filas con datos, sin el encabezado ni las filas vacías
     * @throws NegocioException si el archivo no sirve
     */
    private List<Fila> leer(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new NegocioException("ARCHIVO_VACIO", "Suba un archivo Excel con los productos");
        }
        if (archivo.getSize() > MAX_BYTES) {
            throw new NegocioException("ARCHIVO_GRANDE", "El archivo pasa de " + MAX_BYTES / (1024 * 1024) + " MB");
        }
        String nombre = archivo.getOriginalFilename();
        if (nombre == null || !nombre.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new NegocioException("ARCHIVO_INVALIDO", "El archivo debe ser un Excel .xlsx");
        }
        try (InputStream entrada = archivo.getInputStream(); Workbook libro = WorkbookFactory.create(entrada)) {
            Sheet hoja = libro.getSheetAt(0);
            Row encabezado = hoja.getRow(0);
            if (encabezado == null) {
                throw new NegocioException("ARCHIVO_INVALIDO", "La primera fila debe tener los encabezados");
            }
            Map<String, Integer> columnas = columnas(encabezado);
            if (!columnas.containsKey("codigo")) {
                throw new NegocioException("ARCHIVO_INVALIDO",
                        "Falta el encabezado codigo. Descargue la plantilla para ver las columnas");
            }
            DataFormatter formato = new DataFormatter(Locale.ROOT);
            List<Fila> filas = new ArrayList<>();
            for (int i = 1; i <= hoja.getLastRowNum(); i++) {
                Row fila = hoja.getRow(i);
                if (fila == null || vacia(fila, formato)) {
                    continue;
                }
                if (filas.size() >= MAX_FILAS) {
                    throw new NegocioException("ARCHIVO_GRANDE", "El archivo pasa de " + MAX_FILAS + " filas con datos");
                }
                filas.add(leerFila(i + 1, fila, columnas, formato));
            }
            return filas;
        } catch (IOException | RuntimeException e) {
            if (e instanceof NegocioException ne) {
                throw ne;
            }
            throw new NegocioException("ARCHIVO_INVALIDO", "No se pudo leer el archivo: no es un Excel .xlsx válido");
        }
    }

    /**
     * Lee una fila de datos.
     *
     * @param numero   número de fila en el Excel
     * @param fila     fila de POI
     * @param columnas índice de columna por encabezado
     * @param formato  formateador de celdas
     * @return fila con los textos recortados y los números ya convertidos
     */
    private static Fila leerFila(int numero, Row fila, Map<String, Integer> columnas, DataFormatter formato) {
        List<String> problemas = new ArrayList<>();
        String nombre = texto(fila, columnas, "nombre", formato);
        String descripcion = texto(fila, columnas, "descripcion", formato);
        String categoria = texto(fila, columnas, "categoria", formato);
        if (nombre != null && nombre.length() > 200) {
            problemas.add("el nombre pasa de 200 caracteres");
        }
        if (descripcion != null && descripcion.length() > 500) {
            problemas.add("la descripción pasa de 500 caracteres");
        }
        if (categoria != null && categoria.length() > 150) {
            problemas.add("la categoría pasa de 150 caracteres");
        }
        return new Fila(numero, texto(fila, columnas, "codigo", formato), nombre, categoria,
                texto(fila, columnas, "unidad", formato), descripcion,
                numero(fila, columnas, "stock_minimo", formato, problemas),
                numero(fila, columnas, "stock_maximo", formato, problemas),
                numero(fila, columnas, "costo_referencia", formato, problemas), problemas);
    }

    /**
     * Índice de cada encabezado conocido, sin importar mayúsculas, tildes ni espacios.
     *
     * @param encabezado primera fila del Excel
     * @return índice de columna por nombre normalizado
     */
    private static Map<String, Integer> columnas(Row encabezado) {
        Map<String, Integer> mapa = new HashMap<>();
        DataFormatter formato = new DataFormatter(Locale.ROOT);
        for (Cell c : encabezado) {
            String clave = Normalizer.normalize(formato.formatCellValue(c).trim().toLowerCase(Locale.ROOT),
                    Normalizer.Form.NFD).replaceAll("\\p{M}", "").replaceAll("\\s+", "_");
            if (COLUMNAS.contains(clave)) {
                mapa.putIfAbsent(clave, c.getColumnIndex());
            }
        }
        return mapa;
    }

    /**
     * Indica si una fila no tiene nada en las columnas conocidas.
     *
     * @param fila    fila
     * @param formato formateador
     * @return {@code true} si todas las celdas están vacías
     */
    private static boolean vacia(Row fila, DataFormatter formato) {
        for (Cell c : fila) {
            if (c.getCellType() != CellType.BLANK && !formato.formatCellValue(c).isBlank()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Texto de una celda, recortado ({@code null} si la columna no está o la celda está vacía).
     *
     * @param fila     fila
     * @param columnas índice por encabezado
     * @param nombre   encabezado
     * @param formato  formateador
     * @return texto o {@code null}
     */
    private static String texto(Row fila, Map<String, Integer> columnas, String nombre, DataFormatter formato) {
        Integer i = columnas.get(nombre);
        if (i == null) {
            return null;
        }
        Cell c = fila.getCell(i);
        if (c == null) {
            return null;
        }
        String t = formato.formatCellValue(c).trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Número no negativo de una celda con hasta 4 decimales ({@code null} si está vacía o hay un error).
     *
     * @param fila      fila
     * @param columnas  índice por encabezado
     * @param nombre    encabezado
     * @param formato   formateador
     * @param problemas lista a la que se agrega el error si el valor no sirve
     * @return número o {@code null}
     */
    private static BigDecimal numero(Row fila, Map<String, Integer> columnas, String nombre, DataFormatter formato,
            List<String> problemas) {
        Integer i = columnas.get(nombre);
        if (i == null) {
            return null;
        }
        Cell c = fila.getCell(i);
        if (c == null || c.getCellType() == CellType.BLANK) {
            return null;
        }
        try {
            BigDecimal valor;
            if (c.getCellType() == CellType.NUMERIC) {
                valor = BigDecimal.valueOf(c.getNumericCellValue());
            } else {
                String t = formato.formatCellValue(c).trim();
                if (t.isEmpty()) {
                    return null;
                }
                valor = new BigDecimal(t.replace(',', '.'));
            }
            if (valor.signum() < 0) {
                problemas.add(nombre + " no puede ser negativo");
                return null;
            }
            if (valor.compareTo(new BigDecimal("99999999999999")) > 0) {
                problemas.add(nombre + " es demasiado grande");
                return null;
            }
            return valor.setScale(4, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            problemas.add(nombre + " no es un número");
            return null;
        }
    }
}
