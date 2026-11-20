package com.proyecto.supply_core.venta.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.venta.dto.LimiteDescuento;
import com.proyecto.supply_core.venta.entity.LimiteDescuentoRol;
import com.proyecto.supply_core.venta.repository.LimiteDescuentoRolRepository;

/**
 * Descuento máximo que cada rol da sin pedir el PIN del supervisor (spec 004). Lo configura el dueño por empresa;
 * mientras no lo haga rigen los valores por defecto: cajero 5 %, supervisor de ventas 15 %, vendedor 5 %. El dueño
 * no tiene límite.
 */
@Service
public class LimiteDescuentoService {

    /** Valores por defecto, en el orden en que se muestran. */
    static final Map<String, BigDecimal> POR_DEFECTO;

    static {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        m.put(Roles.CAJERO, new BigDecimal("5.00"));
        m.put(Roles.SUPERVISOR_VENTAS, new BigDecimal("15.00"));
        m.put(Roles.VENDEDOR, new BigDecimal("5.00"));
        POR_DEFECTO = Map.copyOf(m);
    }

    /** Orden de los roles en la pantalla. */
    private static final List<String> ROLES = List.of(Roles.CAJERO, Roles.SUPERVISOR_VENTAS, Roles.VENDEDOR);
    private static final BigDecimal SIN_LIMITE = new BigDecimal("100.00");

    private final LimiteDescuentoRolRepository limiteRepository;
    private final AuditoriaService auditoriaService;

    /**
     * @param limiteRepository límites de la empresa
     * @param auditoriaService registro de cambios
     */
    public LimiteDescuentoService(LimiteDescuentoRolRepository limiteRepository, AuditoriaService auditoriaService) {
        this.limiteRepository = limiteRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * @return límite vigente de cada rol de venta (el de la empresa o el de por defecto)
     */
    @Transactional(readOnly = true)
    public List<LimiteDescuento> listar() {
        return ROLES.stream().map(r -> new LimiteDescuento(r, de(r))).toList();
    }

    /**
     * Fija los límites de la empresa.
     *
     * @param limites límites por rol
     * @return límites vigentes
     * @throws NegocioException {@code ROL_INVALIDO} si un rol no es de venta
     */
    @Transactional
    public List<LimiteDescuento> guardar(List<LimiteDescuento> limites) {
        for (LimiteDescuento l : limites) {
            String rol = l.rol().trim().toUpperCase();
            if (!ROLES.contains(rol)) {
                throw new NegocioException("ROL_INVALIDO", "Solo se fija el límite de cajero, supervisor de ventas y vendedor");
            }
            LimiteDescuentoRol fila = limiteRepository.findByRolCodigo(rol).orElseGet(() -> {
                LimiteDescuentoRol nueva = new LimiteDescuentoRol();
                nueva.setRolCodigo(rol);
                return nueva;
            });
            BigDecimal anterior = fila.getId() == null ? POR_DEFECTO.get(rol) : fila.getDescuentoMaximoPct();
            fila.setDescuentoMaximoPct(l.descuentoMaximoPct().setScale(2));
            limiteRepository.save(fila);
            if (anterior.compareTo(fila.getDescuentoMaximoPct()) != 0) {
                auditoriaService.exito(AccionAuditoria.LIMITE_DESCUENTO_CAMBIAR, EntidadAuditada.LIMITE_DESCUENTO,
                        fila.getId(), rol + ": " + anterior.toPlainString() + " % → "
                                + fila.getDescuentoMaximoPct().toPlainString() + " %");
            }
        }
        return listar();
    }

    /**
     * @param roles roles de la sesión
     * @return el mayor descuento que puede dar sin autorización (100 si es el dueño)
     */
    @Transactional(readOnly = true)
    public BigDecimal limiteDe(Set<String> roles) {
        if (roles.contains(Roles.ADMIN_SISTEMA)) {
            return SIN_LIMITE;
        }
        return ROLES.stream().filter(roles::contains).map(this::de).max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO.setScale(2));
    }

    /**
     * @param rol rol de venta
     * @return límite de la empresa para el rol, o el de por defecto
     */
    private BigDecimal de(String rol) {
        return limiteRepository.findByRolCodigo(rol).map(LimiteDescuentoRol::getDescuentoMaximoPct)
                .orElse(POR_DEFECTO.get(rol));
    }
}
