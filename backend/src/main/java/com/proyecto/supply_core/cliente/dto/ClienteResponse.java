package com.proyecto.supply_core.cliente.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.proyecto.supply_core.cliente.entity.Cliente;
import com.proyecto.supply_core.cliente.enums.TipoDocumento;

/**
 * Un cliente de la empresa.
 *
 * @param id                cliente
 * @param tipoDocumento     tipo de documento
 * @param numeroDocumento   número normalizado
 * @param nombre            nombre o razón social
 * @param telefono          teléfono o {@code null}
 * @param correo            correo o {@code null}
 * @param direccion         dirección o {@code null}
 * @param listaPrecioId     lista de precios o {@code null} (la General)
 * @param listaPrecio       nombre de la lista o {@code null}
 * @param cupoCredito       cupo de crédito
 * @param plazoDias         plazo en días
 * @param esConsumidorFinal {@code true} en el cliente de las ventas sin datos (no se edita)
 * @param activo            si se le puede vender
 * @param aceptaDatosEn     cuándo autorizó el tratamiento de datos, o {@code null}
 * @param creadoEn          fecha de creación
 */
public record ClienteResponse(Long id, TipoDocumento tipoDocumento, String numeroDocumento, String nombre,
        String telefono, String correo, String direccion, Long listaPrecioId, String listaPrecio,
        BigDecimal cupoCredito, int plazoDias, boolean esConsumidorFinal, boolean activo,
        LocalDateTime aceptaDatosEn, LocalDateTime creadoEn) {

    /**
     * @param c cliente
     * @return respuesta
     */
    public static ClienteResponse of(Cliente c) {
        return new ClienteResponse(c.getId(), c.getTipoDocumento(), c.getNumeroDocumento(), c.getNombre(),
                c.getTelefono(), c.getCorreo(), c.getDireccion(),
                c.getListaPrecio() == null ? null : c.getListaPrecio().getId(),
                c.getListaPrecio() == null ? null : c.getListaPrecio().getNombre(), c.getCupoCredito(),
                c.getPlazoDias(), c.isEsConsumidorFinal(), c.isActivo(), c.getAceptaDatosEn(), c.getCreadoEn());
    }
}
