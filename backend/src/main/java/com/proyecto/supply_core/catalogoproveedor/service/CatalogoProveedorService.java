package com.proyecto.supply_core.catalogoproveedor.service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.catalogoproveedor.dto.CargaCatalogoResponse;
import com.proyecto.supply_core.catalogoproveedor.dto.CargaCatalogoResponse.ErrorFila;
import com.proyecto.supply_core.catalogoproveedor.dto.PortalCatalogoItem;
import com.proyecto.supply_core.catalogoproveedor.entity.CatalogoItem;
import com.proyecto.supply_core.catalogoproveedor.enums.EstadoEquivalencia;
import com.proyecto.supply_core.catalogoproveedor.repository.CatalogoItemRepository;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.inventario.repository.ProductoRepository;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.proveedor.entity.Proveedor;
import com.proyecto.supply_core.proveedor.repository.ProveedorRepository;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.SesionActual;

/**
 * El proveedor sube su catálogo (Excel) desde el portal. Todo o nada: si una fila está mal no se guarda nada.
 * Los artículos nuevos pasan por el {@link EmparejadorCatalogo}; los que ya existían conservan la equivalencia que
 * confirmó compras; los que no vienen en el archivo quedan inactivos. Compras recibe un aviso.
 * <p>Columnas: codigo, descripcion, marca, unidad, precio, incluye_iva (si/no), vigente_hasta (aaaa-mm-dd).</p>
 */
@Service
public class CatalogoProveedorService {

    /** Filas máximas por archivo. */
    static final int MAX_FILAS = 5000;
    /** Tamaño máximo del archivo. */
    static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> COLUMNAS = Set.of("codigo", "descripcion", "marca", "unidad", "precio",
            "incluye_iva", "vigente_hasta");

    private final CatalogoItemRepository catalogoRepository;
    private final ProveedorRepository proveedorRepository;
    private final ProductoRepository productoRepository;
    private final NotificacionService notificacionService;
    private final AuditoriaService auditoriaService;

    /**
     * @param catalogoRepository  catálogos
     * @param proveedorRepository proveedor de la sesión
     * @param productoRepository  productos para emparejar
     * @param notificacionService aviso a compras
     * @param auditoriaService    registro de la carga
     */
    public CatalogoProveedorService(CatalogoItemRepository catalogoRepository, ProveedorRepository proveedorRepository,
            ProductoRepository productoRepository, NotificacionService notificacionService,
            AuditoriaService auditoriaService) {
        this.catalogoRepository = catalogoRepository;
        this.proveedorRepository = proveedorRepository;
        this.productoRepository = productoRepository;
        this.notificacionService = notificacionService;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Fila leída del Excel.
     *
     * @param numero       fila del Excel
     * @param codigo       código del proveedor
     * @param descripcion  descripción
     * @param marca        marca
     * @param unidad       unidad
     * @param precio       precio
     * @param incluyeIva   si el precio trae el IVA
     * @param vigenteHasta vigencia
     */
    record Fila(int numero, String codigo, String descripcion, String marca, String unidad, BigDecimal precio,
            boolean incluyeIva, LocalDate vigenteHasta) {
    }

    /**
     * Sube (o reemplaza) el catálogo del proveedor de la sesión.
     *
     * @param archivo Excel .xlsx
     * @return conteos y errores
     */
    @Transactional
    public CargaCatalogoResponse cargar(MultipartFile archivo) {
        Proveedor proveedor = proveedorDeSesion();
        List<ErrorFila> errores = new ArrayList<>();
        List<Fila> filas = leer(archivo, errores);
        Set<String> vistos = new HashSet<>();
        for (Fila f : filas) {
            if (!vistos.add(f.codigo().toLowerCase(Locale.ROOT))) {
                errores.add(new ErrorFila(f.numero(), "el código " + f.codigo() + " está repetido en el archivo"));
            }
        }
        if (!errores.isEmpty()) {
            return new CargaCatalogoResponse(false, 0, 0, 0, errores);
        }
        Map<String, CatalogoItem> existentes = catalogoRepository.deProveedor(proveedor.getId()).stream()
                .collect(Collectors.toMap(c -> c.getCodigoProveedor().toLowerCase(Locale.ROOT), Function.identity()));
        List<Producto> productos = productoRepository.findAll().stream().filter(Producto::isActivo).toList();
        LocalDateTime ahora = LocalDateTime.now();
        int nuevos = 0;
        int actualizados = 0;
        int sugeridos = 0;
        for (Fila f : filas) {
            CatalogoItem c = existentes.remove(f.codigo().toLowerCase(Locale.ROOT));
            if (c == null) {
                c = new CatalogoItem();
                c.setProveedor(proveedor);
                c.setCodigoProveedor(f.codigo());
                EmparejadorCatalogo.Candidato candidato = EmparejadorCatalogo.mejor(f.codigo(), f.descripcion(),
                        f.marca(), productos);
                c.setProducto(candidato.producto());
                c.setSimilitud(candidato.similitud());
                c.setEstadoEquivalencia(candidato.producto() == null ? EstadoEquivalencia.SIN_REVISAR
                        : EstadoEquivalencia.SUGERIDA);
                if (candidato.producto() != null) {
                    sugeridos++;
                }
                nuevos++;
            } else if (c.getPrecio().compareTo(f.precio()) != 0 || !c.getDescripcion().equals(f.descripcion())
                    || !c.isActivo()) {
                actualizados++;
            }
            c.setDescripcion(f.descripcion());
            c.setMarca(f.marca());
            c.setUnidad(f.unidad());
            c.setPrecio(f.precio());
            c.setIncluyeIva(f.incluyeIva());
            c.setVigenteHasta(f.vigenteHasta());
            c.setActivo(true);
            c.setActualizadoEn(ahora);
            catalogoRepository.save(c);
        }
        int retirados = 0;
        for (CatalogoItem c : existentes.values()) {
            if (c.isActivo()) {
                c.setActivo(false);
                c.setActualizadoEn(ahora);
                retirados++;
            }
        }
        String mensaje = proveedor.getRazonSocial() + " actualizó su catálogo: " + filas.size() + " artículos (" + nuevos
                + " nuevos, " + actualizados + " con cambios). " + sugeridos + " equivalencias sugeridas por revisar.";
        notificacionService.notificarRol(Roles.COMPRADOR, EventoNotificacion.CATALOGO_ACTUALIZADO, mensaje);
        notificacionService.notificarRol(Roles.ADMIN_COMPRAS, EventoNotificacion.CATALOGO_ACTUALIZADO, mensaje);
        auditoriaService.exito(AccionAuditoria.CATALOGO_CARGAR, EntidadAuditada.PROVEEDOR, proveedor.getId(),
                "articulos=" + filas.size() + "; nuevos=" + nuevos + "; actualizados=" + actualizados + "; retirados="
                        + retirados);
        return new CargaCatalogoResponse(true, nuevos, actualizados, retirados, List.of());
    }

    /**
     * @return el catálogo del proveedor de la sesión, sin datos de la empresa
     */
    @Transactional(readOnly = true)
    public List<PortalCatalogoItem> miCatalogo() {
        return catalogoRepository.deProveedor(proveedorDeSesion().getId()).stream()
                .sorted((a, b) -> Boolean.compare(b.isActivo(), a.isActivo()) != 0
                        ? Boolean.compare(b.isActivo(), a.isActivo()) : a.getDescripcion().compareTo(b.getDescripcion()))
                .map(PortalCatalogoItem::of).toList();
    }

    /**
     * @return proveedor del usuario del portal
     * @throws AccesoDenegadoException si la sesión no es de un proveedor
     */
    private Proveedor proveedorDeSesion() {
        Long id = SesionActual.usuario().proveedorId();
        if (id == null) {
            throw new AccesoDenegadoException("Solo un proveedor sube su catálogo");
        }
        return proveedorRepository.findById(id).orElseThrow(() -> new NoEncontradoException("Proveedor no encontrado"));
    }

    // ----------------------------------------------------------------- lectura del Excel

    /**
     * @param archivo Excel
     * @param errores donde se anotan los problemas por fila
     * @return filas válidas (si hay errores, la carga no se aplica)
     */
    static List<Fila> leer(MultipartFile archivo, List<ErrorFila> errores) {
        if (archivo == null || archivo.isEmpty()) {
            throw new NegocioException("ARCHIVO_VACIO", "Sube un archivo Excel con tu catálogo");
        }
        if (archivo.getSize() > MAX_BYTES) {
            throw new NegocioException("ARCHIVO_GRANDE", "El archivo pasa de 5 MB");
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
            DataFormatter formato = new DataFormatter(Locale.ROOT);
            Map<String, Integer> columnas = new HashMap<>();
            for (Cell c : encabezado) {
                String clave = Normalizer.normalize(formato.formatCellValue(c).trim().toLowerCase(Locale.ROOT),
                        Normalizer.Form.NFD).replaceAll("\\p{M}", "").replaceAll("\\s+", "_");
                if (COLUMNAS.contains(clave)) {
                    columnas.putIfAbsent(clave, c.getColumnIndex());
                }
            }
            for (String obligatoria : List.of("codigo", "descripcion", "precio")) {
                if (!columnas.containsKey(obligatoria)) {
                    throw new NegocioException("ARCHIVO_INVALIDO", "Falta la columna " + obligatoria
                            + ". Columnas: codigo, descripcion, marca, unidad, precio, incluye_iva, vigente_hasta");
                }
            }
            List<Fila> filas = new ArrayList<>();
            for (int i = 1; i <= hoja.getLastRowNum(); i++) {
                Row fila = hoja.getRow(i);
                if (fila == null || texto(fila, columnas, "codigo", formato) == null
                        && texto(fila, columnas, "descripcion", formato) == null) {
                    continue;
                }
                if (filas.size() >= MAX_FILAS) {
                    throw new NegocioException("ARCHIVO_GRANDE", "El archivo pasa de " + MAX_FILAS + " artículos");
                }
                Fila leida = leerFila(i + 1, fila, columnas, formato, errores);
                if (leida != null) {
                    filas.add(leida);
                }
            }
            if (filas.isEmpty() && errores.isEmpty()) {
                throw new NegocioException("ARCHIVO_VACIO", "El archivo no tiene artículos");
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
     * @param numero   fila del Excel
     * @param fila     fila
     * @param columnas columnas por nombre
     * @param formato  formateador
     * @param errores  problemas encontrados
     * @return la fila, o {@code null} si tiene errores
     */
    private static Fila leerFila(int numero, Row fila, Map<String, Integer> columnas, DataFormatter formato,
            List<ErrorFila> errores) {
        int antes = errores.size();
        String codigo = texto(fila, columnas, "codigo", formato);
        String descripcion = texto(fila, columnas, "descripcion", formato);
        String marca = texto(fila, columnas, "marca", formato);
        String unidad = texto(fila, columnas, "unidad", formato);
        if (codigo == null || codigo.length() > 80) {
            errores.add(new ErrorFila(numero, "el código es obligatorio (máximo 80 caracteres)"));
        }
        if (descripcion == null || descripcion.length() > 300) {
            errores.add(new ErrorFila(numero, "la descripción es obligatoria (máximo 300 caracteres)"));
        }
        if (marca != null && marca.length() > 100) {
            errores.add(new ErrorFila(numero, "la marca pasa de 100 caracteres"));
        }
        if (unidad != null && unidad.length() > 30) {
            errores.add(new ErrorFila(numero, "la unidad pasa de 30 caracteres"));
        }
        BigDecimal precio = null;
        String textoPrecio = texto(fila, columnas, "precio", formato);
        try {
            precio = textoPrecio == null ? null : new BigDecimal(textoPrecio.replace("$", "").replace(" ", ""));
        } catch (NumberFormatException e) {
            precio = null;
        }
        if (precio == null || precio.signum() <= 0 || precio.scale() > 2) {
            errores.add(new ErrorFila(numero, "el precio debe ser un número mayor que cero con máximo 2 decimales"));
        }
        String iva = texto(fila, columnas, "incluye_iva", formato);
        boolean incluyeIva = iva != null && List.of("si", "sí", "s", "true", "1", "x").contains(iva.toLowerCase(Locale.ROOT));
        LocalDate vigente = null;
        String textoVigente = texto(fila, columnas, "vigente_hasta", formato);
        if (textoVigente != null) {
            try {
                vigente = LocalDate.parse(textoVigente);
            } catch (DateTimeParseException e) {
                errores.add(new ErrorFila(numero, "vigente_hasta debe ser una fecha aaaa-mm-dd"));
            }
        }
        return errores.size() > antes ? null
                : new Fila(numero, codigo, descripcion, marca, unidad, precio, incluyeIva, vigente);
    }

    /**
     * @param fila     fila
     * @param columnas columnas por nombre
     * @param nombre   columna
     * @param formato  formateador
     * @return texto recortado o {@code null}
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
}
