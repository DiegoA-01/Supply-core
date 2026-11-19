package com.proyecto.supply_core.catalogoproveedor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.catalogo.entity.UnidadMedida;
import com.proyecto.supply_core.catalogoproveedor.entity.CatalogoItem;
import com.proyecto.supply_core.inventario.entity.Producto;
import com.proyecto.supply_core.proveedor.entity.Proveedor;
import com.proyecto.supply_core.reorden.enums.EstadoReabastecimiento;

/** Emparejamiento de catálogos con productos y cálculo de la oportunidad, con ejemplos de ferretería. */
class EmparejadorCatalogoTest {

    private static Producto producto(long id, String codigo, String nombre, String unidad) {
        Producto p = new Producto();
        p.setId(id);
        p.setCodigo(codigo);
        p.setNombre(nombre);
        UnidadMedida u = new UnidadMedida();
        u.setCodigo(unidad);
        p.setUnidadMedida(u);
        return p;
    }

    private final List<Producto> inventario = List.of(producto(1, "CAB-012", "Cable THHN 12 AWG", "M"),
            producto(2, "TOR-001", "Tornillo drywall 6x1\"", "CAJA24"), producto(3, "MAR-001", "Martillo de uña 16 oz", "UND"));

    @Test
    void elCodigoExactoGanaSiempre() {
        var c = EmparejadorCatalogo.mejor("mar-001", "Martillo carpintero mango fibra", "Stanley", inventario);
        assertEquals(3L, c.producto().getId());
        assertEquals(0, BigDecimal.ONE.compareTo(c.similitud()));
    }

    @Test
    void reconoceElProductoAunqueElProveedorLoEscribaDistinto() {
        var cable = EmparejadorCatalogo.mejor("PR-77", "Cable thhn calibre 12awg rollo 100 m", "Centelsa", inventario);
        assertEquals(1L, cable.producto().getId());
        assertTrue(cable.similitud().doubleValue() >= EmparejadorCatalogo.UMBRAL_SUGERENCIA);
        var martillo = EmparejadorCatalogo.mejor("X-9", "Martillo uña 16oz", null, inventario);
        assertEquals(3L, martillo.producto().getId());
    }

    @Test
    void siNadaSeParecenoPropone() {
        var c = EmparejadorCatalogo.mejor("Z-1", "Guantes de carnaza talla L", null, inventario);
        assertNull(c.producto());
    }

    @Test
    void laOportunidadComparaSinIvaContraElUltimoCosto() {
        Producto cable = producto(1, "CAB-012", "Cable THHN 12 AWG", "M");
        cable.setCostoReferencia(new BigDecimal("2500"));
        Proveedor prov = new Proveedor();
        prov.setId(9L);
        prov.setRazonSocial("Eléctricos del Valle");
        CatalogoItem item = new CatalogoItem();
        item.setProveedor(prov);
        item.setProducto(cable);
        item.setCodigoProveedor("PR-77");
        item.setDescripcion("Cable THHN 12");
        item.setUnidad("M");
        item.setPrecio(new BigDecimal("2737")); // con IVA 19 % incluido: 2.300 sin IVA => 8 % menos que 2.500
        item.setIncluyeIva(true);

        var o = CatalogoService.oportunidad(item, 2, null);

        assertEquals(0, new BigDecimal("2300.00").compareTo(o.precioSinIva()));
        assertEquals(0, new BigDecimal("8.0").compareTo(o.ahorroPct()));
        assertTrue(o.motivo().contains("8.0 % menos que tu último costo"), o.motivo());
        assertTrue(o.motivo().contains("2 proveedores"), o.motivo());
        assertNull(o.estadoReabastecimiento());
        assertEquals(EstadoReabastecimiento.class, EstadoReabastecimiento.URGENTE.getClass());
    }

    @Test
    void conUnidadDistintaNoCalculaElAhorro() {
        Producto tornillo = producto(2, "TOR-001", "Tornillo", "CAJA24");
        tornillo.setCostoReferencia(new BigDecimal("20000"));
        Proveedor prov = new Proveedor();
        prov.setId(9L);
        prov.setRazonSocial("Ferrecol");
        CatalogoItem item = new CatalogoItem();
        item.setProveedor(prov);
        item.setProducto(tornillo);
        item.setUnidad("UND");
        item.setPrecio(new BigDecimal("900"));
        item.setDescripcion("Tornillo");
        item.setCodigoProveedor("T-1");
        var o = CatalogoService.oportunidad(item, 1, null);
        assertNull(o.ahorroPct());
        assertTrue(o.motivo().contains("revisa la conversión"), o.motivo());
    }
}
