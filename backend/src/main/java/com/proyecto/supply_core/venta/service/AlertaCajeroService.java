package com.proyecto.supply_core.venta.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.empresa.entity.Empresa;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;
import com.proyecto.supply_core.venta.repository.VentaRepository;

/**
 * Alertas por cajero (spec 010): cuando un cajero llega en un día al umbral de anulaciones o de ventas con descuento o
 * precio autorizado que fijó su empresa, se avisa al dueño y a los supervisores de ventas (decisión de la usuaria), una
 * sola vez por cajero, tipo y día. No bloquea nada: solo avisa para que alguien lo revise. Desde la venta y la
 * anulación se revisa después de confirmar ({@link #revisarDespuesDeConfirmar}): así cuenta lo que ya quedó guardado
 * y un error del aviso nunca deshace la venta.
 */
@Service
public class AlertaCajeroService {

    /** Qué se cuenta. */
    public enum Tipo {
        /** Ventas anuladas por el cajero. */
        ANULACIONES,
        /** Ventas con descuento o precio autorizado. */
        DESCUENTOS
    }

    private final VentaRepository ventaRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionService notificacionService;
    private static final Logger log = LoggerFactory.getLogger(AlertaCajeroService.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate aparte;

    /**
     * @param ventaRepository     conteos del día
     * @param empresaRepository   umbrales de la empresa
     * @param usuarioRepository   nombre del cajero
     * @param notificacionService avisos
     * @param jdbc                registro de la alerta (sin repetir el mismo día)
     * @param transacciones       transacción propia para revisar después de confirmar
     */
    public AlertaCajeroService(VentaRepository ventaRepository, EmpresaRepository empresaRepository,
            UsuarioRepository usuarioRepository, NotificacionService notificacionService, JdbcTemplate jdbc,
            PlatformTransactionManager transacciones) {
        this.ventaRepository = ventaRepository;
        this.empresaRepository = empresaRepository;
        this.usuarioRepository = usuarioRepository;
        this.notificacionService = notificacionService;
        this.jdbc = jdbc;
        this.aparte = new TransactionTemplate(transacciones);
        this.aparte.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Revisa el umbral cuando la transacción en curso (la venta o la anulación) ya se confirmó, en una transacción
     * aparte. Un error aquí se registra en el log y no toca la operación; sin transacción en curso revisa de una vez.
     *
     * @param cajeroId cajero
     * @param tipo     anulaciones o descuentos
     */
    public void revisarDespuesDeConfirmar(Long cajeroId, Tipo tipo) {
        Runnable revisar = () -> {
            try {
                aparte.executeWithoutResult(estado -> revisar(cajeroId, tipo));
            } catch (RuntimeException e) {
                log.error("No se pudo revisar la alerta {} del cajero {}: {}", tipo, cajeroId, e.getMessage(), e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    revisar.run();
                }
            });
        } else {
            revisar.run();
        }
    }

    /**
     * Revisa el umbral del cajero y avisa si lo alcanzó (una vez por día y tipo).
     *
     * @param cajeroId cajero
     * @param tipo     anulaciones o descuentos
     * @return {@code true} si se envió el aviso
     */
    @Transactional
    public boolean revisar(Long cajeroId, Tipo tipo) {
        Long empresaId = EmpresaContexto.requerida();
        Empresa empresa = empresaRepository.findById(empresaId).orElse(null);
        if (empresa == null) {
            return false;
        }
        LocalDate hoy = LocalDate.now();
        LocalDateTime desde = hoy.atStartOfDay();
        LocalDateTime hasta = hoy.plusDays(1).atStartOfDay();
        long cantidad = tipo == Tipo.ANULACIONES ? ventaRepository.anuladasDelDia(cajeroId, desde, hasta)
                : ventaRepository.autorizadasDelDia(cajeroId, desde, hasta);
        int umbral = tipo == Tipo.ANULACIONES ? empresa.getUmbralAnulacionesDia() : empresa.getUmbralDescuentosDia();
        if (!alcanza(cantidad, umbral)) {
            return false;
        }
        // INSERT IGNORE: si ya se avisó hoy (o otra caja lo acaba de hacer) no se repite ni se deshace la venta
        int nueva = jdbc.update("INSERT IGNORE INTO alerta_cajero (empresa_id, cajero_usuario_id, fecha, tipo, cantidad) "
                + "VALUES (?, ?, ?, ?, ?)", empresaId, cajeroId, hoy, tipo.name(), cantidad);
        if (nueva == 0) {
            return false;
        }
        String cajero = usuarioRepository.findById(cajeroId).map(Usuario::getNombreCompleto).orElse("Un cajero");
        String mensaje = cajero + " lleva hoy " + cantidad + (tipo == Tipo.ANULACIONES ? " ventas anuladas" : " ventas con descuento o precio autorizado")
                + " (umbral " + umbral + "). Revise sus ventas en el reporte de caja.";
        notificacionService.notificarRoles(java.util.List.of(Roles.ADMIN_SISTEMA, Roles.SUPERVISOR_VENTAS),
                EventoNotificacion.ALERTA_CAJERO, mensaje);
        return true;
    }

    /**
     * @param cantidad cantidad del día
     * @param umbral   umbral de la empresa
     * @return {@code true} si la cantidad llega al umbral
     */
    static boolean alcanza(long cantidad, int umbral) {
        return umbral > 0 && cantidad >= umbral;
    }
}
