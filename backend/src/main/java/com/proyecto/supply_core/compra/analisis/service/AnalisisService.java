package com.proyecto.supply_core.compra.analisis.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.compra.analisis.dto.AnalisisResponse;
import com.proyecto.supply_core.compra.analisis.entity.Analisis;
import com.proyecto.supply_core.compra.analisis.entity.ConfigPesos;
import com.proyecto.supply_core.compra.analisis.entity.CriterioEvaluacion;
import com.proyecto.supply_core.compra.analisis.enums.EstadoAnalisis;
import com.proyecto.supply_core.compra.analisis.repository.AnalisisRepository;
import com.proyecto.supply_core.compra.cotizacion.entity.Cotizacion;
import com.proyecto.supply_core.compra.cotizacion.entity.CotizacionLinea;
import com.proyecto.supply_core.compra.cotizacion.enums.EstadoCotizacion;
import com.proyecto.supply_core.compra.cotizacion.enums.OrigenCotizacion;
import com.proyecto.supply_core.compra.cotizacion.repository.CotizacionRepository;
import com.proyecto.supply_core.compra.rfq.entity.Rfq;
import com.proyecto.supply_core.compra.rfq.entity.RfqLinea;
import com.proyecto.supply_core.compra.rfq.enums.EstadoRfq;
import com.proyecto.supply_core.compra.rfq.repository.RfqRepository;
import com.proyecto.supply_core.ia.client.IAClient;
import com.proyecto.supply_core.ia.client.IAException;
import com.proyecto.supply_core.ia.enums.EstadoProcesamiento;
import com.proyecto.supply_core.ia.service.ExtraccionService;
import com.proyecto.supply_core.proveedor.repository.EvaluacionProveedorRepository;
import com.proyecto.supply_core.security.context.SesionActual;

/**
 * UC-08 Analizar cotizaciones: arma las ofertas, las compara con {@link MotorComparacion} (Java puro) y
 * publica el ranking.
 * <ul>
 *   <li>Solo con la RFQ CERRADA (sobre cerrado). En BORRADOR se puede regenerar las veces que haga falta.</li>
 *   <li>Publicar exige que toda cotización por archivo esté leída y sin datos críticos dudosos.</li>
 *   <li>La versión de pesos queda guardada (snapshot) y cada puntaje copia su peso.</li>
 *   <li>La explicación de cada cotización la arma Java; la IA solo redacta, si se pide, una explicación
 *       general del ranking ya calculado.</li>
 * </ul>
 */
@Service
public class AnalisisService {

    private static final Logger log = LoggerFactory.getLogger(AnalisisService.class);

    private final AnalisisRepository analisisRepository;
    private final RfqRepository rfqRepository;
    private final CotizacionRepository cotizacionRepository;
    private final EvaluacionProveedorRepository evaluacionRepository;
    private final PesosService pesosService;
    private final ExtraccionService extraccionService;
    private final IAClient iaClient;
    private final AuditoriaService auditoriaService;
    private final TransactionTemplate transaccion;

    /**
     * @param analisisRepository   análisis
     * @param rfqRepository        RFQ
     * @param cotizacionRepository cotizaciones
     * @param evaluacionRepository calidad histórica del proveedor
     * @param pesosService         pesos vigentes
     * @param extraccionService    estado de lectura de cotizaciones por archivo
     * @param iaClient             redacción opcional de la explicación general
     * @param auditoriaService     registro de acciones
     * @param transactionManager   transacciones cortas alrededor de la llamada a la IA
     */
    public AnalisisService(AnalisisRepository analisisRepository, RfqRepository rfqRepository,
            CotizacionRepository cotizacionRepository, EvaluacionProveedorRepository evaluacionRepository,
            PesosService pesosService, ExtraccionService extraccionService, IAClient iaClient,
            AuditoriaService auditoriaService, PlatformTransactionManager transactionManager) {
        this.analisisRepository = analisisRepository;
        this.rfqRepository = rfqRepository;
        this.cotizacionRepository = cotizacionRepository;
        this.evaluacionRepository = evaluacionRepository;
        this.pesosService = pesosService;
        this.extraccionService = extraccionService;
        this.iaClient = iaClient;
        this.auditoriaService = auditoriaService;
        this.transaccion = new TransactionTemplate(transactionManager);
    }

    /**
     * Genera (o regenera) el análisis en BORRADOR.
     *
     * @param rfqId RFQ
     * @return análisis con el ranking
     * @throws NegocioException si la RFQ no está CERRADA, ya hay uno publicado o no hay cotizaciones
     */
    @Transactional
    public AnalisisResponse generar(Long rfqId) {
        Rfq rfq = rfqRepository.findWithDetalleById(rfqId).orElseThrow(() -> new NoEncontradoException("RFQ", rfqId));
        if (rfq.getEstado() != EstadoRfq.CERRADA) {
            throw new NegocioException("RFQ_NO_CERRADA",
                    "La " + rfq.getConsecutivo() + " está " + rfq.getEstado() + "; el análisis se hace con la RFQ CERRADA");
        }
        if (analisisRepository.findFirstByRfq_IdAndEstado(rfqId, EstadoAnalisis.PUBLICADO).isPresent()) {
            throw new NegocioException("ANALISIS_PUBLICADO", "El análisis de la " + rfq.getConsecutivo() + " ya se publicó");
        }
        List<Cotizacion> cotizaciones = comparables(rfqId);
        if (cotizaciones.isEmpty()) {
            throw new NegocioException("SIN_COTIZACIONES", "La " + rfq.getConsecutivo() + " no recibió cotizaciones");
        }
        analisisRepository.findFirstByRfq_IdAndEstado(rfqId, EstadoAnalisis.BORRADOR).ifPresent(viejo -> {
            analisisRepository.delete(viejo);
            analisisRepository.flush();
        });

        ConfigPesos pesos = pesosService.entidadVigente();
        Map<String, BigDecimal> pesoPorCodigo = pesos.getDetalles().stream()
                .collect(Collectors.toMap(d -> d.getCriterio().getCodigo(), ConfigPesos.Detalle::getPeso));
        Map<String, CriterioEvaluacion> criterios = pesos.getDetalles().stream()
                .collect(Collectors.toMap(d -> d.getCriterio().getCodigo(), ConfigPesos.Detalle::getCriterio));
        List<Long> lineasRfq = rfq.getLineas().stream().map(RfqLinea::getId).toList();
        List<MotorComparacion.Resultado> ranking = MotorComparacion.comparar(lineasRfq,
                cotizaciones.stream().map(this::oferta).toList(), pesoPorCodigo, LocalDate.now());

        Analisis a = new Analisis();
        a.setRfq(rfq);
        a.setConfigPesosId(pesos.getId());
        a.setUsuarioId(SesionActual.usuario().id());
        Map<Long, Cotizacion> porId = cotizaciones.stream().collect(Collectors.toMap(Cotizacion::getId, Function.identity()));
        for (MotorComparacion.Resultado r : ranking) {
            Analisis.Resultado res = new Analisis.Resultado();
            res.setAnalisis(a);
            res.setCotizacion(porId.get(r.cotizacionId()));
            res.setCostoTotal(r.costoTotal() == null ? BigDecimal.ZERO : r.costoTotal());
            res.setScoreFinal(r.scoreFinal());
            res.setPosicionRanking((short) r.posicion());
            res.setExplicacion(explicacion(r, ranking.size()));
            for (MotorComparacion.Anomalia an : r.anomalias()) {
                Analisis.Anomalia anomalia = new Analisis.Anomalia();
                anomalia.setResultado(res);
                anomalia.setTipo(an.tipo());
                anomalia.setSeveridad(an.severidad());
                anomalia.setDescripcion(an.descripcion());
                res.getAnomalias().add(anomalia);
            }
            a.getResultados().add(res);
            for (Map.Entry<String, BigDecimal> p : r.puntajes().entrySet()) {
                Analisis.Detalle d = new Analisis.Detalle();
                d.setAnalisis(a);
                d.setCotizacionId(r.cotizacionId());
                d.setCriterio(criterios.get(p.getKey()));
                d.setPesoAplicado(pesoPorCodigo.get(p.getKey()));
                d.setPuntaje(p.getValue());
                d.setScorePonderado(r.ponderados().get(p.getKey()));
                a.getDetalles().add(d);
            }
        }
        Analisis guardado = analisisRepository.save(a);
        cotizaciones.forEach(c -> c.setEstado(EstadoCotizacion.EN_ANALISIS));
        auditoriaService.exito(AccionAuditoria.ANALISIS_GENERAR, EntidadAuditada.ANALISIS, guardado.getId(),
                rfq.getConsecutivo() + "; cotizaciones=" + cotizaciones.size() + "; pesos=" + pesos.getId()
                        + "; primera=" + ranking.get(0).proveedor());
        log.info("Análisis de {} generado: {} cotizaciones, primera {}", rfq.getConsecutivo(), ranking.size(),
                ranking.get(0).proveedor());
        return AnalisisResponse.of(guardado, pendientes(cotizaciones));
    }

    /**
     * Publica el análisis en BORRADOR (queda fijo).
     *
     * @param rfqId RFQ
     * @return análisis publicado
     * @throws NegocioException si no hay borrador o quedan cotizaciones por revisar
     */
    @Transactional
    public AnalisisResponse publicar(Long rfqId) {
        Analisis a = analisisRepository.findFirstByRfq_IdAndEstado(rfqId, EstadoAnalisis.BORRADOR)
                .orElseThrow(() -> new NegocioException("SIN_ANALISIS", "No hay un análisis en borrador para publicar"));
        List<String> pendientes = pendientes(a.getResultados().stream().map(Analisis.Resultado::getCotizacion).toList());
        if (!pendientes.isEmpty()) {
            throw new NegocioException("EXTRACCION_PENDIENTE", "No se puede publicar: " + String.join("; ", pendientes));
        }
        a.setEstado(EstadoAnalisis.PUBLICADO);
        a.setPublicadoEn(LocalDateTime.now());
        auditoriaService.exito(AccionAuditoria.ANALISIS_PUBLICAR, EntidadAuditada.ANALISIS, a.getId(),
                a.getRfq().getConsecutivo() + "; recomendada=" + a.getResultados().get(0).getCotizacion().getId());
        return AnalisisResponse.of(a, List.of());
    }

    /**
     * Último análisis de la RFQ.
     *
     * @param rfqId RFQ
     * @return análisis con ranking y pendientes
     * @throws NoEncontradoException si aún no hay análisis
     */
    @Transactional(readOnly = true)
    public AnalisisResponse obtener(Long rfqId) {
        Analisis a = analisisRepository.findFirstByRfq_IdOrderByIdDesc(rfqId)
                .orElseThrow(() -> new NoEncontradoException("La RFQ aún no tiene análisis"));
        List<String> pendientes = a.getEstado() == EstadoAnalisis.PUBLICADO ? List.of()
                : pendientes(a.getResultados().stream().map(Analisis.Resultado::getCotizacion).toList());
        return AnalisisResponse.of(a, pendientes);
    }

    /**
     * Pide a la IA una explicación general del ranking ya calculado. La llamada a la IA corre fuera de
     * transacción (puede tardar).
     *
     * @param rfqId RFQ
     * @return análisis con la explicación general
     * @throws NegocioException si no hay IA o no hay análisis
     */
    public AnalisisResponse explicar(Long rfqId) {
        if (!iaClient.disponible()) {
            throw new NegocioException("IA_NO_DISPONIBLE", "No hay IA configurada para redactar la explicación");
        }
        String resumen = transaccion.execute(s -> resumen(rfqId));
        String texto;
        try {
            texto = iaClient.explicar(resumen);
        } catch (IAException e) {
            throw new NegocioException("IA_FALLO", "La IA no pudo redactar la explicación: " + e.getMessage());
        }
        String limpio = texto.length() > 5000 ? texto.substring(0, 5000) : texto;
        transaccion.executeWithoutResult(s -> analisisRepository.findFirstByRfq_IdOrderByIdDesc(rfqId)
                .ifPresent(a -> a.setExplicacionGeneral(limpio)));
        return obtener(rfqId);
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Cotizaciones que entran a la comparación (enviadas o en análisis; no borradores ni rechazadas).
     *
     * @param rfqId RFQ
     * @return cotizaciones con líneas
     */
    private List<Cotizacion> comparables(Long rfqId) {
        return cotizacionRepository.findByRfq_IdAndEstadoNotOrderByEnviadoEnAsc(rfqId, EstadoCotizacion.BORRADOR)
                .stream().filter(c -> c.getEstado() == EstadoCotizacion.ENVIADA
                        || c.getEstado() == EstadoCotizacion.EN_ANALISIS)
                .toList();
    }

    /**
     * Convierte una cotización en oferta para el motor.
     *
     * @param c cotización con líneas y proveedor
     * @return oferta
     */
    private MotorComparacion.Oferta oferta(Cotizacion c) {
        Map<Long, BigDecimal> precios = new LinkedHashMap<>();
        for (CotizacionLinea l : c.getLineas()) {
            if (l.getPrecioUnitarioNormalizado() != null) {
                precios.put(l.getRfqLinea().getId(), l.getPrecioUnitarioNormalizado());
            }
        }
        Double calidad = evaluacionRepository.promedioCalidad(c.getProveedor().getId());
        return new MotorComparacion.Oferta(c.getId(), c.getProveedor().getRazonSocial(), c.getTotal(),
                c.getPlazoEntregaDias(), c.getGarantiaMeses(), c.getValidezHasta(), c.getProveedor().getScoreActual(),
                calidad == null ? null : BigDecimal.valueOf(calidad).setScale(2, RoundingMode.HALF_UP), precios);
    }

    /**
     * Motivos que impiden publicar: cotizaciones por archivo sin leer o con datos dudosos.
     *
     * @param cotizaciones cotizaciones del análisis
     * @return mensajes (vacío = se puede publicar)
     */
    private List<String> pendientes(List<Cotizacion> cotizaciones) {
        List<Cotizacion> porArchivo = cotizaciones.stream().filter(c -> c.getOrigen() == OrigenCotizacion.ARCHIVO).toList();
        Map<Long, EstadoProcesamiento> estados = extraccionService.estados(porArchivo.stream().map(Cotizacion::getId).toList());
        List<String> pendientes = new ArrayList<>();
        for (Cotizacion c : porArchivo) {
            EstadoProcesamiento e = estados.get(c.getId());
            if (e == null || !e.listo()) {
                pendientes.add(c.getProveedor().getRazonSocial() + ": lectura "
                        + (e == null ? "sin iniciar" : e.name()) + " (revise o digite la cotización)");
            }
        }
        return pendientes;
    }

    /**
     * Explicación de una cotización armada con sus puntajes (sin IA).
     *
     * @param r     resultado
     * @param total cantidad de cotizaciones
     * @return texto
     */
    static String explicacion(MotorComparacion.Resultado r, int total) {
        List<String> fuertes = new ArrayList<>();
        List<String> debiles = new ArrayList<>();
        r.puntajes().forEach((criterio, puntaje) -> {
            String item = criterio.toLowerCase() + " (" + puntaje.stripTrailingZeros().toPlainString() + ")";
            if (puntaje.compareTo(BigDecimal.valueOf(80)) >= 0) {
                fuertes.add(item);
            } else if (puntaje.compareTo(BigDecimal.valueOf(50)) < 0) {
                debiles.add(item);
            }
        });
        StringBuilder sb = new StringBuilder("Puesto ").append(r.posicion()).append(" de ").append(total)
                .append(" con ").append(r.scoreFinal().toPlainString()).append(" puntos.");
        if (!fuertes.isEmpty()) {
            sb.append(" Fortalezas: ").append(String.join(", ", fuertes)).append('.');
        }
        if (!debiles.isEmpty()) {
            sb.append(" Debilidades: ").append(String.join(", ", debiles)).append('.');
        }
        if (!r.anomalias().isEmpty()) {
            sb.append(" Alertas: ").append(r.anomalias().stream().map(MotorComparacion.Anomalia::descripcion)
                    .collect(Collectors.joining("; "))).append('.');
        }
        return sb.toString();
    }

    /**
     * Resumen del ranking para la IA.
     *
     * @param rfqId RFQ
     * @return texto con las cifras ya calculadas
     */
    private String resumen(Long rfqId) {
        Analisis a = analisisRepository.findFirstByRfq_IdOrderByIdDesc(rfqId)
                .orElseThrow(() -> new NoEncontradoException("La RFQ aún no tiene análisis"));
        StringBuilder sb = new StringBuilder("Ranking de cotizaciones de la ").append(a.getRfq().getConsecutivo())
                .append(" (puntaje 0-100 por criterio):\n");
        for (Analisis.Resultado r : a.getResultados()) {
            sb.append(r.getPosicionRanking()).append(". ").append(r.getCotizacion().getProveedor().getRazonSocial())
                    .append(" | score ").append(r.getScoreFinal()).append(" | total ").append(r.getCostoTotal())
                    .append(" | ").append(r.getExplicacion()).append('\n');
        }
        return sb.toString();
    }
}
