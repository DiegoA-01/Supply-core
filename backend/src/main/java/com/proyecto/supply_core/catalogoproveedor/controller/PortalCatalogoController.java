package com.proyecto.supply_core.catalogoproveedor.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.proyecto.supply_core.catalogoproveedor.dto.CargaCatalogoResponse;
import com.proyecto.supply_core.catalogoproveedor.dto.PortalCatalogoItem;
import com.proyecto.supply_core.catalogoproveedor.service.CatalogoProveedorService;
import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.security.context.Roles;

/** El proveedor sube y consulta su catálogo. Solo ve lo suyo, sin datos del inventario de la empresa. */
@RequiereModulo(Modulos.PROVEEDORES)
@RestController
@RequestMapping("/api/portal/catalogo")
@RequiereRol(Roles.PROVEEDOR)
public class PortalCatalogoController {

    private final CatalogoProveedorService catalogoProveedorService;

    /**
     * @param catalogoProveedorService carga del catálogo
     */
    public PortalCatalogoController(CatalogoProveedorService catalogoProveedorService) {
        this.catalogoProveedorService = catalogoProveedorService;
    }

    /**
     * @return el catálogo del proveedor de la sesión
     */
    @GetMapping
    public List<PortalCatalogoItem> miCatalogo() {
        return catalogoProveedorService.miCatalogo();
    }

    /**
     * Sube o reemplaza el catálogo (Excel .xlsx). Todo o nada.
     *
     * @param archivo Excel con codigo, descripcion, marca, unidad, precio, incluye_iva, vigente_hasta
     * @return conteos y errores
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CargaCatalogoResponse cargar(@RequestPart("archivo") MultipartFile archivo) {
        return catalogoProveedorService.cargar(archivo);
    }
}
