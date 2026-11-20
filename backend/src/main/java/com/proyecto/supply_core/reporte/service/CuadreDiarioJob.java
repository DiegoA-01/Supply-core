package com.proyecto.supply_core.reporte.service;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.plataforma.service.AccesoEmpresaService;
import com.proyecto.supply_core.reporte.dto.CuadreResponse;
import com.proyecto.supply_core.security.context.Modulos;

/**
 * Cuadre diario automático (spec 009): cada noche, por defecto a la 01:00, revisa el día anterior de cada empresa que
 * tenga el módulo de ventas. La hora se cambia con {@code app.ventas.cuadre-cron}; {@code "-"} lo apaga.
 */
@Component
public class CuadreDiarioJob {

    private static final Logger log = LoggerFactory.getLogger(CuadreDiarioJob.class);

    private final CuadreDiarioService cuadreService;
    private final EmpresaRepository empresaRepository;
    private final AccesoEmpresaService accesoEmpresa;

    /**
     * @param cuadreService     cuadre de una empresa
     * @param empresaRepository empresas a recorrer
     * @param accesoEmpresa     módulos contratados de cada empresa
     */
    public CuadreDiarioJob(CuadreDiarioService cuadreService, EmpresaRepository empresaRepository,
            AccesoEmpresaService accesoEmpresa) {
        this.cuadreService = cuadreService;
        this.empresaRepository = empresaRepository;
        this.accesoEmpresa = accesoEmpresa;
    }

    /**
     * Revisa ayer en cada empresa con su propio contexto. Un error en una empresa se registra y no detiene a las demás.
     */
    @Scheduled(cron = "${app.ventas.cuadre-cron:0 0 1 * * *}")
    public void cuadrarAyer() {
        LocalDate ayer = LocalDate.now().minusDays(1);
        List<Long> empresas;
        try {
            empresas = EmpresaContexto.sinEmpresa(empresaRepository::todosLosIds);
        } catch (RuntimeException e) {
            log.error("No se pudo listar las empresas para el cuadre diario: {}", e.getMessage(), e);
            return;
        }
        for (Long empresaId : empresas) {
            try {
                CuadreResponse r = EmpresaContexto.como(empresaId, () ->
                        accesoEmpresa.resumen(empresaId).modulos().contains(Modulos.VENTAS) ? cuadreService.revisar(ayer) : null);
                if (r != null && !r.cuadra()) {
                    log.warn("Cuadre del {} (empresa {}): no cuadra", ayer, empresaId);
                }
            } catch (RuntimeException e) {
                log.error("Falló el cuadre diario de la empresa {}: {}", empresaId, e.getMessage(), e);
            }
        }
    }
}
