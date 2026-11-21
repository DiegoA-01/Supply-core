package com.proyecto.supply_core.caja.service;

import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.caja.dto.CajaRequest;
import com.proyecto.supply_core.caja.dto.CajaResponse;
import com.proyecto.supply_core.caja.dto.ConfiguracionCaja;
import com.proyecto.supply_core.caja.entity.Caja;
import com.proyecto.supply_core.caja.enums.EstadoTurno;
import com.proyecto.supply_core.caja.repository.CajaRepository;
import com.proyecto.supply_core.caja.repository.TurnoCajaRepository;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.empresa.entity.Empresa;
import com.proyecto.supply_core.empresa.entity.Sede;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.empresa.repository.SedeRepository;
import com.proyecto.supply_core.inventario.entity.Bodega;
import com.proyecto.supply_core.inventario.repository.BodegaRepository;

/**
 * Cajas de la empresa y su configuración (spec 003). Una caja pertenece a una sede y descuenta de una sola bodega de esa
 * sede (decisión D7). No se borran: se desactivan, y no con un turno abierto.
 */
@Service
public class CajaService {

    private final CajaRepository cajaRepository;
    private final TurnoCajaRepository turnoRepository;
    private final SedeRepository sedeRepository;
    private final BodegaRepository bodegaRepository;
    private final EmpresaRepository empresaRepository;
    private final AuditoriaService auditoriaService;

    /**
     * @param cajaRepository    cajas
     * @param turnoRepository   turnos (para saber si una caja está en uso)
     * @param sedeRepository    sedes
     * @param bodegaRepository  bodegas
     * @param empresaRepository tolerancia de diferencia de caja
     * @param auditoriaService  registro de cambios
     */
    public CajaService(CajaRepository cajaRepository, TurnoCajaRepository turnoRepository,
            SedeRepository sedeRepository, BodegaRepository bodegaRepository, EmpresaRepository empresaRepository,
            AuditoriaService auditoriaService) {
        this.cajaRepository = cajaRepository;
        this.turnoRepository = turnoRepository;
        this.sedeRepository = sedeRepository;
        this.bodegaRepository = bodegaRepository;
        this.empresaRepository = empresaRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * @param activa filtro por estado ({@code null} = todas)
     * @return cajas con su sede, su bodega y si tienen turno abierto
     */
    @Transactional(readOnly = true)
    public List<CajaResponse> listar(Boolean activa) {
        return cajaRepository.listar(activa).stream()
                .map(c -> CajaResponse.of(c, turnoRepository.existsByCajaIdAndEstado(c.getId(), EstadoTurno.ABIERTO)))
                .toList();
    }

    /**
     * @param req datos de la caja
     * @return caja creada
     * @throws NegocioException      si el nombre o el prefijo ya existen, o la bodega no es de la sede
     * @throws NoEncontradoException si la sede o la bodega no son de la empresa
     */
    @Transactional
    public CajaResponse crear(CajaRequest req) {
        Caja c = new Caja();
        aplicar(c, req, -1L);
        if (Boolean.FALSE.equals(req.activa())) {
            c.setActiva(false);
        }
        Caja guardada = cajaRepository.save(c);
        auditoriaService.exito(AccionAuditoria.CAJA_CREAR, EntidadAuditada.CAJA, guardada.getId(), describir(guardada));
        return CajaResponse.of(guardada, false);
    }

    /**
     * @param id  caja
     * @param req datos nuevos
     * @return caja actualizada
     * @throws NegocioException si se desactiva o se cambia de bodega con un turno abierto
     */
    @Transactional
    public CajaResponse editar(Long id, CajaRequest req) {
        Caja c = cajaRepository.conDetalle(id).orElseThrow(() -> new NoEncontradoException("Caja no encontrada"));
        boolean abierta = turnoRepository.existsByCajaIdAndEstado(id, EstadoTurno.ABIERTO);
        if (abierta && (Boolean.FALSE.equals(req.activa()) || !c.getBodega().getId().equals(req.bodegaId()))) {
            throw new NegocioException("CAJA_EN_USO",
                    "La caja tiene un turno abierto: ciérrelo antes de desactivarla o cambiarle la bodega");
        }
        String antes = describir(c);
        aplicar(c, req, id);
        if (req.activa() != null) {
            c.setActiva(req.activa());
        }
        auditoriaService.exito(AccionAuditoria.CAJA_EDITAR, EntidadAuditada.CAJA, id,
                "antes: " + antes + " | después: " + describir(c));
        return CajaResponse.of(c, abierta);
    }

    /**
     * @return la tolerancia de diferencia de caja de la empresa
     */
    @Transactional(readOnly = true)
    public ConfiguracionCaja configuracion() {
        Empresa e = empresa();
        return new ConfiguracionCaja(e.getToleranciaDiferenciaCaja(), e.getBodegaAveriasId(), e.getUmbralAnulacionesDia(),
                e.getUmbralDescuentosDia());
    }

    /**
     * @param req tolerancia y bodega de averías nuevas
     * @return configuración guardada
     * @throws NoEncontradoException si la bodega de averías no es de la empresa
     */
    @Transactional
    public ConfiguracionCaja configurar(ConfiguracionCaja req) {
        Empresa e = empresa();
        String antes = e.getToleranciaDiferenciaCaja().toPlainString() + "; averías=" + e.getBodegaAveriasId() + "; umbrales=" + e.getUmbralAnulacionesDia() + "/"
                + e.getUmbralDescuentosDia();
        if (req.bodegaAveriasId() != null) {
            bodegaRepository.findById(req.bodegaAveriasId()).orElseThrow(() -> new NoEncontradoException("Bodega no encontrada"));
        }
        e.setToleranciaDiferenciaCaja(req.toleranciaDiferencia().setScale(2, RoundingMode.HALF_UP));
        e.setBodegaAveriasId(req.bodegaAveriasId());
        if (req.umbralAnulacionesDia() != null) {
            e.setUmbralAnulacionesDia(req.umbralAnulacionesDia());
        }
        if (req.umbralDescuentosDia() != null) {
            e.setUmbralDescuentosDia(req.umbralDescuentosDia());
        }
        auditoriaService.exito(AccionAuditoria.CAJA_CONFIGURAR, EntidadAuditada.EMPRESA, e.getId(),
                "antes: " + antes + " | después: " + e.getToleranciaDiferenciaCaja().toPlainString() + "; averías="
                        + e.getBodegaAveriasId() + "; umbrales=" + e.getUmbralAnulacionesDia() + "/"
                        + e.getUmbralDescuentosDia());
        return new ConfiguracionCaja(e.getToleranciaDiferenciaCaja(), e.getBodegaAveriasId(), e.getUmbralAnulacionesDia(),
                e.getUmbralDescuentosDia());
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Valida y copia los datos a la caja.
     *
     * @param c   caja
     * @param req datos
     * @param id  caja que se edita (o -1 al crear)
     */
    private void aplicar(Caja c, CajaRequest req, Long id) {
        String nombre = req.nombre().trim();
        String prefijo = req.prefijoConsecutivo().trim().toUpperCase(Locale.ROOT);
        if (cajaRepository.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw new NegocioException("CAJA_DUPLICADA", "Ya existe una caja llamada " + nombre);
        }
        if (cajaRepository.existsByPrefijoConsecutivoIgnoreCaseAndIdNot(prefijo, id)) {
            throw new NegocioException("PREFIJO_DUPLICADO", "Otra caja ya usa el prefijo " + prefijo);
        }
        Sede sede = sedeRepository.findById(req.sedeId()).orElseThrow(() -> new NoEncontradoException("Sede no encontrada"));
        Bodega bodega = bodegaRepository.findWithSedeById(req.bodegaId())
                .orElseThrow(() -> new NoEncontradoException("Bodega no encontrada"));
        if (!sede.isActivo() || !bodega.isActivo()) {
            throw new NegocioException("SEDE_O_BODEGA_INACTIVA", "La sede y la bodega deben estar activas");
        }
        if (bodega.getSede() == null || !bodega.getSede().getId().equals(sede.getId())) {
            throw new NegocioException("BODEGA_OTRA_SEDE", "La bodega " + bodega.getNombre() + " no es de la sede "
                    + sede.getNombre() + ": una caja descuenta de una bodega de su misma sede");
        }
        c.setNombre(nombre);
        c.setPrefijoConsecutivo(prefijo);
        c.setSede(sede);
        c.setBodega(bodega);
        c.setTopeEfectivo(req.topeEfectivo().setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * @return la empresa de la sesión
     */
    private Empresa empresa() {
        return empresaRepository.findById(EmpresaContexto.requerida())
                .orElseThrow(() -> new NoEncontradoException("Empresa no encontrada"));
    }

    /**
     * @param c caja
     * @return resumen para la auditoría
     */
    private static String describir(Caja c) {
        return "nombre=" + c.getNombre() + ", prefijo=" + c.getPrefijoConsecutivo() + ", sede=" + c.getSede().getId()
                + ", bodega=" + c.getBodega().getId() + ", tope=" + c.getTopeEfectivo() + ", activa=" + c.isActiva();
    }
}
