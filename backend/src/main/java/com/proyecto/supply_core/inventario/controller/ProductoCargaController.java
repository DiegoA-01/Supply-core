package com.proyecto.supply_core.inventario.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.inventario.dto.CargaMasivaResponse;
import com.proyecto.supply_core.inventario.service.ProductoCargaService;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.annotation.UsuarioInterno;
import com.proyecto.supply_core.security.context.Roles;

/**
 * Carga y actualización masiva de productos, categorías y precios desde Excel (paso 18-d). Solo ADMIN_COMPRAS,
 * igual que el resto de la administración del catálogo. Solo usuarios internos.
 */
@RequiereModulo(Modulos.INVENTARIO)
@RestController
@RequestMapping("/api/productos/carga-masiva")
@UsuarioInterno
public class ProductoCargaController {

    /** Tipo MIME de un libro de Excel .xlsx. */
    private static final MediaType XLSX = MediaType
            .parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ProductoCargaService cargaService;

    /**
     * @param cargaService lógica de la carga masiva
     */
    public ProductoCargaController(ProductoCargaService cargaService) {
        this.cargaService = cargaService;
    }

    /**
     * Sube el Excel. La carga es de todo o nada: con algún error no se guarda nada y se devuelven los errores por
     * fila (HTTP 200 con {@code aplicado=false}).
     *
     * @param archivo     Excel .xlsx
     * @param soloValidar {@code true} para revisar el archivo sin guardar
     * @return conteos y errores por fila
     */
    @PostMapping
    @RequiereRol(Roles.ADMIN_COMPRAS)
    public CargaMasivaResponse cargar(@RequestPart("archivo") MultipartFile archivo,
            @RequestParam(defaultValue = "false") boolean soloValidar) {
        return cargaService.cargar(archivo, soloValidar);
    }

    /**
     * Descarga el Excel de ejemplo con los encabezados.
     *
     * @return archivo .xlsx
     */
    @GetMapping("/plantilla")
    @RequiereRol(Roles.ADMIN_COMPRAS)
    public ResponseEntity<byte[]> plantilla() {
        return ResponseEntity.ok().contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("plantilla-productos.xlsx").build().toString())
                .body(cargaService.plantilla());
    }
}
