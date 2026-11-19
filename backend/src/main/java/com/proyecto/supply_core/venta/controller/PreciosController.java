package com.proyecto.supply_core.venta.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.venta.dto.CargaPreciosResponse;
import com.proyecto.supply_core.venta.dto.HistorialPrecio;
import com.proyecto.supply_core.venta.dto.ListaPrecioRequest;
import com.proyecto.supply_core.venta.dto.ListaPrecioResponse;
import com.proyecto.supply_core.venta.dto.PrecioFila;
import com.proyecto.supply_core.venta.dto.PrecioRequest;
import com.proyecto.supply_core.venta.dto.PreciosResponse;
import com.proyecto.supply_core.venta.dto.ProductoParaVender;
import com.proyecto.supply_core.venta.dto.ProductoVentaRequest;
import com.proyecto.supply_core.venta.dto.TarifaImpuestoRequest;
import com.proyecto.supply_core.venta.dto.TarifaImpuestoResponse;
import com.proyecto.supply_core.venta.service.PrecioCargaService;
import com.proyecto.supply_core.venta.service.PreciosService;
import com.proyecto.supply_core.venta.service.VentaProductoService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Precios de venta y margen (fase F1 de Ventas, spec 001). Lo ven el dueño, el jefe de compras y auditoría (los costos
 * son información sensible); fija precios el dueño, o el jefe de compras si se lo delegó (lo valida el servicio). La
 * búsqueda para vender ({@code GET /api/ventas/productos}) es para caja y venta y no muestra costos.
 */
@RequiereModulo(Modulos.VENTAS)
@RestController
@RequestMapping("/api/ventas")
@UsuarioInterno
@RequiereRol({ Roles.ADMIN_SISTEMA, Roles.ADMIN_COMPRAS, Roles.AUDITOR })
public class PreciosController {

    /** Tipo MIME de un libro de Excel .xlsx. */
    private static final MediaType XLSX = MediaType
            .parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final PreciosService preciosService;
    private final PrecioCargaService cargaService;
    private final VentaProductoService ventaProductoService;

    /**
     * @param preciosService       lógica de precios
     * @param cargaService         cambio masivo de precios por Excel
     * @param ventaProductoService búsqueda de productos para vender
     */
    public PreciosController(PreciosService preciosService, PrecioCargaService cargaService,
            VentaProductoService ventaProductoService) {
        this.preciosService = preciosService;
        this.cargaService = cargaService;
        this.ventaProductoService = ventaProductoService;
    }

    /**
     * @return impuestos de venta
     */
    @GetMapping("/impuestos")
    public List<TarifaImpuestoResponse> impuestos() {
        return preciosService.tarifas();
    }

    /**
     * @param request impuesto nuevo
     * @return impuesto creado (HTTP 201)
     */
    @PostMapping("/impuestos")
    @ResponseStatus(HttpStatus.CREATED)
    public TarifaImpuestoResponse crearImpuesto(@Valid @RequestBody TarifaImpuestoRequest request) {
        return preciosService.crearTarifa(request);
    }

    /**
     * @param id     impuesto
     * @param activo nuevo estado
     * @return impuesto
     */
    @PatchMapping("/impuestos/{id}")
    public TarifaImpuestoResponse estadoImpuesto(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @RequestParam boolean activo) {
        return preciosService.cambiarEstadoTarifa(id, activo);
    }

    /**
     * Listas de precios (solo nombre y estado, sin costos): también las leen caja y venta para elegir la lista de un
     * cliente o de una venta.
     *
     * @return listas de precios
     */
    @GetMapping("/listas")
    @RequiereRol({ Roles.ADMIN_SISTEMA, Roles.ADMIN_COMPRAS, Roles.AUDITOR, Roles.SUPERVISOR_VENTAS, Roles.CAJERO,
            Roles.VENDEDOR })
    public List<ListaPrecioResponse> listas() {
        return preciosService.listas();
    }

    /**
     * @param request nombre
     * @return lista creada (HTTP 201)
     */
    @PostMapping("/listas")
    @ResponseStatus(HttpStatus.CREATED)
    public ListaPrecioResponse crearLista(@Valid @RequestBody ListaPrecioRequest request) {
        return preciosService.crearLista(request);
    }

    /**
     * @param id      lista
     * @param request nombre y estado
     * @return lista actualizada
     */
    @PutMapping("/listas/{id}")
    public ListaPrecioResponse editarLista(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody ListaPrecioRequest request) {
        return preciosService.editarLista(id, request);
    }

    /**
     * @param listaId lista (vacío = General)
     * @return precios con margen y resumen
     */
    @GetMapping("/precios")
    public PreciosResponse precios(
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long listaId) {
        return preciosService.precios(listaId);
    }

    /**
     * @param request lista, producto, precio, mínimo y motivo
     * @return el producto con su nuevo margen
     */
    @PutMapping("/precios")
    public PrecioFila fijarPrecio(@Valid @RequestBody PrecioRequest request) {
        return preciosService.fijarPrecio(request);
    }

    /**
     * @param listaId        lista
     * @param productoId     producto
     * @param unidadMedidaId unidad del precio (vacío = la del producto)
     * @return historial de precios
     */
    @GetMapping("/precios/historial")
    public List<HistorialPrecio> historial(
            @RequestParam @Positive(message = "debe ser un id válido") Long listaId,
            @RequestParam @Positive(message = "debe ser un id válido") Long productoId,
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long unidadMedidaId) {
        return preciosService.historial(listaId, productoId, unidadMedidaId);
    }

    /**
     * Cambio masivo de precios por Excel. Es de todo o nada: con algún error no se guarda nada y se devuelven los
     * errores por fila (HTTP 200 con {@code aplicado=false}).
     *
     * @param archivo     Excel .xlsx con codigo, lista, unidad, precio, precio_minimo y motivo
     * @param soloValidar {@code true} para revisar sin guardar
     * @return conteos y errores por fila
     */
    @PostMapping("/precios/carga")
    public CargaPreciosResponse cargarPrecios(@RequestPart("archivo") MultipartFile archivo,
            @RequestParam(defaultValue = "false") boolean soloValidar) {
        return cargaService.cargar(archivo, soloValidar);
    }

    /**
     * @return Excel de ejemplo para la carga de precios
     */
    @GetMapping("/precios/carga/plantilla")
    public ResponseEntity<byte[]> plantillaPrecios() {
        return ResponseEntity.ok().contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"plantilla-precios.xlsx\"")
                .body(cargaService.plantilla());
    }

    /**
     * Busca productos para vender por código, código de barras o nombre, con el precio vigente de la lista y el stock
     * de la bodega. Es para caja y venta: no trae costos, márgenes ni el precio mínimo.
     *
     * @param q        texto buscado (1 a 100 caracteres)
     * @param listaId  lista de precios (vacío = General)
     * @param bodegaId bodega cuyo stock se muestra (vacío = todas)
     * @return hasta 20 productos
     */
    @GetMapping("/productos")
    @RequiereRol({ Roles.CAJERO, Roles.VENDEDOR, Roles.SUPERVISOR_VENTAS, Roles.ADMIN_SISTEMA })
    public List<ProductoParaVender> buscarParaVender(
            @RequestParam @Size(min = 1, max = 100, message = "de 1 a 100 caracteres") String q,
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long listaId,
            @RequestParam(required = false) @Positive(message = "debe ser un id válido") Long bodegaId) {
        return ventaProductoService.buscar(q, listaId, bodegaId);
    }

    /**
     * @param productoId producto
     * @param request    impuesto, vendible, si el precio incluye impuesto y código de barras
     * @return el producto en la lista General
     */
    @PutMapping("/productos/{productoId}")
    public PrecioFila configurarProducto(
            @PathVariable @Positive(message = "debe ser un id válido") Long productoId,
            @Valid @RequestBody ProductoVentaRequest request) {
        return preciosService.configurarProducto(productoId, request);
    }
}
