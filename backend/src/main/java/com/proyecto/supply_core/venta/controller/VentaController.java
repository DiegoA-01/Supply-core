package com.proyecto.supply_core.venta.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.plataforma.service.AccesoEmpresaService;
import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.venta.dto.AnularVentaRequest;
import com.proyecto.supply_core.venta.dto.CotizacionVenta;
import com.proyecto.supply_core.venta.dto.CotizarRequest;
import com.proyecto.supply_core.venta.dto.LimiteDescuento;
import com.proyecto.supply_core.venta.dto.TiqueteResponse;
import com.proyecto.supply_core.venta.dto.VentaRequest;
import com.proyecto.supply_core.venta.dto.VentaResponse;
import com.proyecto.supply_core.venta.dto.VentaResumen;
import com.proyecto.supply_core.venta.enums.EstadoVenta;
import com.proyecto.supply_core.venta.service.AnulacionVentaService;
import com.proyecto.supply_core.venta.service.LimiteDescuentoService;
import com.proyecto.supply_core.venta.service.VentaService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * La venta (spec 004): confirmarla en el turno, el historial y el límite de descuento por rol. Las rutas con id usan
 * {@code {id:\d+}} para no chocar con {@code /api/ventas/precios}, {@code /listas} y las demás de la spec 001.
 */
@RequiereModulo(Modulos.VENTAS)
@RestController
@RequestMapping("/api/ventas")
@UsuarioInterno
@Validated
public class VentaController {

    private final VentaService ventaService;
    private final LimiteDescuentoService limiteService;
    private final AccesoEmpresaService accesoEmpresa;
    private final AnulacionVentaService anulacionService;

    /**
     * @param ventaService  lógica de la venta
     * @param limiteService límites de descuento
     * @param accesoEmpresa módulo de caja de la empresa
     * @param anulacionService anulación de ventas del turno (spec 006)
     */
    public VentaController(VentaService ventaService, LimiteDescuentoService limiteService,
            AccesoEmpresaService accesoEmpresa, AnulacionVentaService anulacionService) {
        this.ventaService = ventaService;
        this.limiteService = limiteService;
        this.accesoEmpresa = accesoEmpresa;
        this.anulacionService = anulacionService;
    }

    /**
     * Confirma la venta. Si la misma clave ya se usó, devuelve la venta ya creada con 200 en vez de 201.
     *
     * @param clave   cabecera {@code Idempotency-Key} generada al abrir el carrito
     * @param request cliente, líneas, pagos y autorizaciones
     * @return la venta
     */
    @PostMapping
    @RequiereRol({ Roles.CAJERO, Roles.SUPERVISOR_VENTAS })
    public ResponseEntity<VentaResponse> confirmar(@RequestHeader(name = "Idempotency-Key", required = false) String clave,
            @Valid @RequestBody VentaRequest request) {
        accesoEmpresa.exigirModulo(SesionActual.usuario().empresaId(), Modulos.CAJA);
        try {
            VentaService.Confirmacion c = ventaService.confirmar(request, clave);
            return ResponseEntity.status(c.nueva() ? HttpStatus.CREATED : HttpStatus.OK).body(c.venta());
        } catch (DataIntegrityViolationException e) {
            // Dos envíos con la misma clave al mismo tiempo: el segundo choca con la regla única y recibe la primera
            return ResponseEntity.ok(ventaService.porClave(clave));
        }
    }

    /**
     * @param cajaId      caja
     * @param turnoId     turno
     * @param clienteId   cliente
     * @param estado      estado
     * @param consecutivo consecutivo exacto (el cajero solo busca así)
     * @param desde       desde (incluido)
     * @param hasta       hasta (incluido)
     * @param pageable    página (por defecto las más recientes primero)
     * @return historial según quién mira
     */
    @GetMapping
    @RequiereRol({ Roles.CAJERO, Roles.VENDEDOR, Roles.SUPERVISOR_VENTAS, Roles.ADMIN_SISTEMA, Roles.AUDITOR })
    public PageResponse<VentaResumen> buscar(@RequestParam(required = false) Long cajaId,
            @RequestParam(required = false) Long turnoId, @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) EstadoVenta estado, @RequestParam(required = false) String consecutivo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @PageableDefault(size = 20, sort = "confirmadaEn", direction = Sort.Direction.DESC) Pageable pageable) {
        return ventaService.buscar(cajaId, turnoId, clienteId, estado, consecutivo,
                desde == null ? null : desde.atStartOfDay(), hasta == null ? null : hasta.plusDays(1).atStartOfDay(),
                pageable);
    }

    /**
     * @param id venta
     * @return detalle con líneas y pagos
     */
    @GetMapping("/{id:\\d+}")
    @RequiereRol({ Roles.CAJERO, Roles.VENDEDOR, Roles.SUPERVISOR_VENTAS, Roles.ADMIN_SISTEMA, Roles.AUDITOR })
    public VentaResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return ventaService.obtener(id);
    }

    /**
     * Totales del carrito sin guardar nada (spec 005).
     *
     * @param request cliente, líneas y pagos escritos
     * @return totales, redondeo, cambio y autorizaciones que hacen falta
     */
    @PostMapping("/cotizar")
    @RequiereRol({ Roles.CAJERO, Roles.VENDEDOR, Roles.SUPERVISOR_VENTAS })
    public CotizacionVenta cotizar(@Valid @RequestBody CotizarRequest request) {
        return ventaService.cotizar(request);
    }

    /**
     * @param id venta
     * @return datos del tiquete (desde la segunda vez, COPIA)
     */
    @GetMapping("/{id:\\d+}/tiquete")
    @RequiereRol({ Roles.CAJERO, Roles.VENDEDOR, Roles.SUPERVISOR_VENTAS, Roles.ADMIN_SISTEMA, Roles.AUDITOR })
    public TiqueteResponse tiquete(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return ventaService.tiquete(id);
    }

    /**
     * Anula una venta del turno abierto (spec 006): devuelve el stock y saca el dinero de la caja.
     *
     * @param id      venta
     * @param request motivo y autorización del supervisor
     * @return la venta anulada
     */
    @PostMapping("/{id:\\d+}/anular")
    @RequiereRol({ Roles.CAJERO, Roles.SUPERVISOR_VENTAS })
    public VentaResponse anular(@PathVariable @Positive(message = "debe ser un id válido") Long id,
            @Valid @RequestBody AnularVentaRequest request) {
        accesoEmpresa.exigirModulo(SesionActual.usuario().empresaId(), Modulos.CAJA);
        return anulacionService.anular(id, request);
    }

    /**
     * @return descuento máximo sin autorización de cada rol de venta
     */
    @GetMapping("/limites-descuento")
    @RequiereRol({ Roles.ADMIN_SISTEMA, Roles.SUPERVISOR_VENTAS, Roles.AUDITOR })
    public List<LimiteDescuento> limites() {
        return limiteService.listar();
    }

    /**
     * @param limites límites por rol
     * @return límites vigentes
     */
    @PutMapping("/limites-descuento")
    @RequiereRol(Roles.ADMIN_SISTEMA)
    public List<LimiteDescuento> guardarLimites(
            @RequestBody @NotEmpty(message = "envíe al menos un límite") @Size(max = 10, message = "admite hasta 10 límites")
            List<@Valid LimiteDescuento> limites) {
        return limiteService.guardar(limites);
    }
}
