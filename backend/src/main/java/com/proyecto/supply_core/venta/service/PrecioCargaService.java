package com.proyecto.supply_core.venta.service;

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
import com.proyecto.supply_core.catalogo.entity.UnidadMedida;
import com.proyecto.supply_core.catalogo.repository.UnidadMedidaRepository;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.inventario.dto.CargaMasivaResponse.ErrorFila;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.venta.dto.CargaPreciosResponse;
import com.proyecto.supply_core.venta.dto.PrecioRequest;
import com.proyecto.supply_core.venta.entity.ListaPrecio;
import com.proyecto.supply_core.venta.repository.ListaPrecioRepository;

/**
 * Cambio masivo de precios de venta desde Excel (spec 001). Es de todo o nada: se revisan todas las filas y, si una
 * falla, no se guarda ningún precio y se informa la fila. Cada precio se fija con {@link PreciosService#fijarPrecio},
 * así que conserva el historial, la auditoría por precio y la regla de quién puede fijar precios.
 * <p>Columnas: {@code codigo} (producto), {@code lista} (vacía = General), {@code unidad} (vacía = la del producto),
 * {@code precio}, {@code precio_minimo} y {@code motivo}.</p>
 */
@Service
public class PrecioCargaService {

    /** Encabezados que se leen (sin tildes, en minúsculas y con guion bajo en lugar de espacios). */
    static final List<String> COLUMNAS = List.of("codigo", "lista", "unidad", "precio", "precio_minimo", "motivo");
    /** Tamaño máximo del archivo: 5 MB. */
    static final long MAX_BYTES = 5L * 1024 * 1024;
    /** Filas con datos como máximo por archivo. */
    static final int MAX_FILAS = 2000;

    private final PreciosService preciosService;
    private final ListaPrecioRepository listaRepository;
    private final ProductoRepository productoRepository;
    private final UnidadMedidaRepository unidadRepository;
    private final AuditoriaService auditoriaService;

    /**
     * @param preciosService     fija cada precio (historial, permisos y auditoría)
     * @param listaRepository    listas por nombre
     * @param productoRepository productos por código
     * @param unidadRepository   unidades por código
     * @param auditoriaService   resumen de la carga
     */
    public PrecioCargaService(PreciosService preciosService, ListaPrecioRepository listaRepository,
            ProductoRepository productoRepository, UnidadMedidaRepository unidadRepository,
            AuditoriaService auditoriaService) {
        this.preciosService = preciosService;
        this.listaRepository = listaRepository;
        this.productoRepository = productoRepository;
        this.unidadRepository = unidadRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Lee el Excel, revisa todas las filas y, si están bien y no se pidió solo validar, fija los precios.
     *
     * @param archivo     Excel .xlsx
     * @param soloValidar {@code true} para revisar sin guardar
     * @return conteos y errores por fila
     * @throws NegocioException si el archivo no sirve
     * @throws com.proyecto.supply_core.common.exception.AccesoDenegadoException si la sesión no puede fijar precios
     */
    @Transactional
    public CargaPreciosResponse cargar(MultipartFile archivo, boolean soloValidar) {
        preciosService.exigirEdicion();
        preciosService.asegurarBase();
        List<Fila> filas = leer(archivo);
        Map<String, ListaPrecio> listas = new HashMap<>();
        for (ListaPrecio l : listaRepository.findAllByOrderByEsGeneralDescNombreAsc()) {
            listas.put(l.getNombre().toLowerCase(Locale.ROOT), l);
            if (l.isEsGeneral()) {
                listas.put("", l);
            }
        }
        Map<String, UnidadMedida> unidades = new HashMap<>();
        Set<String> vistos = new HashSet<>();
        List<ErrorFila> errores = new ArrayList<>();
        List<PrecioRequest> plan = new ArrayList<>();

        for (Fila f : filas) {
            List<String> problemas = new ArrayList<>(f.problemas());
            Producto producto = null;
            if (f.codigo() == null) {
                problemas.add("falta el código del producto");
            } else {
                producto = productoRepository.findByCodigoIgnoreCase(f.codigo()).orElse(null);
                if (producto == null) {
                    problemas.add("el producto " + f.codigo() + " no existe");
                } else if (!producto.isActivo() || !producto.isVendible()) {
                    problemas.add("el producto " + f.codigo() + " no se vende o está inactivo");
                    producto = null;
                }
            }
            ListaPrecio lista = listas.get(f.lista() == null ? "" : f.lista().toLowerCase(Locale.ROOT));
            if (lista == null) {
                problemas.add("la lista " + f.lista() + " no existe");
            } else if (!lista.isActivo()) {
                problemas.add("la lista " + lista.getNombre() + " está inactiva");
            }
            UnidadMedida unidad = null;
            if (producto != null) {
                if (f.unidad() == null) {
                    unidad = producto.getUnidadMedida();
                } else {
                    unidad = unidades.computeIfAbsent(f.unidad().toUpperCase(Locale.ROOT),
                            c -> unidadRepository.findByCodigoIgnoreCase(c).orElse(null));
                    if (unidad == null) {
                        problemas.add("la unidad " + f.unidad() + " no existe");
                    } else {
                        try {
                            preciosService.unidadDelPrecio(producto, unidad.getId());
                        } catch (NegocioException e) {
                            problemas.add(e.getMessage());
                            unidad = null;
                        }
                    }
                }
            }
            if (f.precio() == null && !f.problemas().stream().anyMatch(s -> s.startsWith("precio "))) {
                problemas.add("falta el precio");
            } else if (f.precio() != null && f.precio().signum() <= 0) {
                problemas.add("el precio debe ser mayor que cero");
            }
            if (f.precioMinimo() != null && f.precio() != null && f.precioMinimo().compareTo(f.precio()) > 0) {
                problemas.add("el precio mínimo no puede ser mayor que el precio");
            }
            if (f.precioMinimo() != null && f.precioMinimo().signum() <= 0) {
                problemas.add("el precio mínimo debe ser mayor que cero");
            }
            if (f.motivo() != null && f.motivo().length() > 300) {
                problemas.add("el motivo pasa de 300 caracteres");
            }
            if (producto != null && lista != null && unidad != null
                    && !vistos.add(producto.getId() + "|" + lista.getId() + "|" + unidad.getId())) {
                problemas.add("el precio de " + f.codigo() + " en " + lista.getNombre() + " (" + unidad.getCodigo()
                        + ") ya salió antes en el archivo");
            }
            if (!problemas.isEmpty()) {
                errores.add(new ErrorFila(f.numero(), String.join("; ", problemas)));
                continue;
            }
            plan.add(new PrecioRequest(lista.getId(), producto.getId(), f.precio(), f.precioMinimo(),
                    f.motivo() == null ? "carga por Excel" : f.motivo(), unidad.getId()));
        }
        if (!errores.isEmpty() || soloValidar) {
            return new CargaPreciosResponse(soloValidar, false, filas.size(), plan.size(), errores);
        }
        plan.forEach(preciosService::fijarPrecio);
        auditoriaService.exito(AccionAuditoria.PRECIO_CONFIGURAR, EntidadAuditada.PRECIO_PRODUCTO, null,
                "carga por Excel: archivo=" + archivo.getOriginalFilename() + "; precios=" + plan.size());
        return new CargaPreciosResponse(false, true, filas.size(), plan.size(), errores);
    }

    /**
     * Excel de ejemplo con los encabezados y dos filas de muestra (unidad y caja).
     *
     * @return bytes de un .xlsx
     */
    public byte[] plantilla() {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("precios");
            Row encabezado = hoja.createRow(0);
            for (int i = 0; i < COLUMNAS.size(); i++) {
                encabezado.createCell(i).setCellValue(COLUMNAS.get(i));
                hoja.setColumnWidth(i, 5000);
            }
            String[][] ejemplos = { { "TOR-001", "General", "UND", "200", "180", "nuevo precio" },
                    { "TOR-001", "General", "CAJA24", "4000", "3800", "precio por caja" } };
            for (int r = 0; r < ejemplos.length; r++) {
                Row fila = hoja.createRow(r + 1);
                for (int i = 0; i < ejemplos[r].length; i++) {
                    fila.createCell(i).setCellValue(ejemplos[r][i]);
                }
            }
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar la plantilla", e);
        }
    }

    // ----------------------------------------------------------------- lectura

    /**
     * Fila leída ({@code null} = celda vacía).
     *
     * @param numero       número de fila en el Excel
     * @param codigo       código del producto
     * @param lista        nombre de la lista
     * @param unidad       código de la unidad
     * @param precio       precio
     * @param precioMinimo piso
     * @param motivo       motivo
     * @param problemas    errores de lectura (números mal escritos)
     */
    private record Fila(int numero, String codigo, String lista, String unidad, BigDecimal precio,
            BigDecimal precioMinimo, String motivo, List<String> problemas) {
    }

    /**
     * Valida tamaño, extensión y formato y devuelve las filas con datos.
     *
     * @param archivo archivo subido
     * @return filas sin el encabezado ni las vacías
     * @throws NegocioException si el archivo no sirve
     */
    private List<Fila> leer(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new NegocioException("ARCHIVO_VACIO", "Suba un archivo Excel con los precios");
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
            if (!columnas.containsKey("codigo") || !columnas.containsKey("precio")) {
                throw new NegocioException("ARCHIVO_INVALIDO",
                        "Faltan los encabezados codigo y precio. Descargue la plantilla para ver las columnas");
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
                List<String> problemas = new ArrayList<>();
                filas.add(new Fila(i + 1, texto(fila, columnas, "codigo", formato), texto(fila, columnas, "lista", formato),
                        texto(fila, columnas, "unidad", formato), dinero(fila, columnas, "precio", formato, problemas),
                        dinero(fila, columnas, "precio_minimo", formato, problemas),
                        texto(fila, columnas, "motivo", formato), problemas));
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
     * @param encabezado primera fila
     * @return índice de cada encabezado conocido, sin importar mayúsculas, tildes ni espacios
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
     * @param fila     fila
     * @param columnas índice por encabezado
     * @param nombre   encabezado
     * @param formato  formateador
     * @return texto recortado o {@code null}
     */
    private static String texto(Row fila, Map<String, Integer> columnas, String nombre, DataFormatter formato) {
        Integer i = columnas.get(nombre);
        Cell c = i == null ? null : fila.getCell(i);
        if (c == null) {
            return null;
        }
        String t = formato.formatCellValue(c).trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Monto con hasta 2 decimales.
     *
     * @param fila      fila
     * @param columnas  índice por encabezado
     * @param nombre    encabezado
     * @param formato   formateador
     * @param problemas lista a la que se agrega el error si el valor no sirve
     * @return monto o {@code null} si la celda está vacía o tiene un error
     */
    private static BigDecimal dinero(Row fila, Map<String, Integer> columnas, String nombre, DataFormatter formato,
            List<String> problemas) {
        Integer i = columnas.get(nombre);
        Cell c = i == null ? null : fila.getCell(i);
        if (c == null || c.getCellType() == CellType.BLANK) {
            return null;
        }
        try {
            BigDecimal valor = c.getCellType() == CellType.NUMERIC ? BigDecimal.valueOf(c.getNumericCellValue())
                    : new BigDecimal(formato.formatCellValue(c).trim().replace(',', '.'));
            if (valor.compareTo(new BigDecimal("9999999999999999")) > 0) {
                problemas.add(nombre + " es demasiado grande");
                return null;
            }
            if (valor.stripTrailingZeros().scale() > 2) {
                problemas.add(nombre + " tiene más de 2 decimales");
                return null;
            }
            return valor.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            problemas.add(nombre + " no es un número");
            return null;
        }
    }
}
