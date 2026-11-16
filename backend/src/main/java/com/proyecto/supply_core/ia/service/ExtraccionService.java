package com.proyecto.supply_core.ia.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.exception.ParametroInvalidoException;
import com.proyecto.supply_core.compra.analisis.enums.EstadoAnalisis;
import com.proyecto.supply_core.compra.analisis.repository.AnalisisRepository;
import com.proyecto.supply_core.compra.cotizacion.dto.LineaCotizacionRequest;
import com.proyecto.supply_core.compra.cotizacion.entity.Cotizacion;
import com.proyecto.supply_core.compra.cotizacion.entity.CotizacionLinea;
import com.proyecto.supply_core.compra.cotizacion.enums.EstadoCotizacion;
import com.proyecto.supply_core.compra.cotizacion.enums.OrigenCotizacion;
import com.proyecto.supply_core.compra.cotizacion.repository.CotizacionRepository;
import com.proyecto.supply_core.compra.cotizacion.service.ArmadorLineas;
import com.proyecto.supply_core.compra.cotizacion.service.CalculadoraCotizacion;
import com.proyecto.supply_core.compra.rfq.entity.Rfq;
import com.proyecto.supply_core.compra.rfq.entity.RfqLinea;
import com.proyecto.supply_core.compra.rfq.enums.EstadoRfq;
import com.proyecto.supply_core.documento.service.DocumentoService;
import com.proyecto.supply_core.ia.client.IAClient;
import com.proyecto.supply_core.ia.client.IAException;
import com.proyecto.supply_core.ia.config.IAProperties;
import com.proyecto.supply_core.ia.dto.ArchivoIA;
import com.proyecto.supply_core.ia.dto.ExtraccionIA;
import com.proyecto.supply_core.ia.dto.LineaContexto;
import com.proyecto.supply_core.ia.dto.ProcesamientoResponse;
import com.proyecto.supply_core.ia.entity.ExtraccionCampo;
import com.proyecto.supply_core.ia.entity.ProcesamientoIa;
import com.proyecto.supply_core.ia.enums.EstadoProcesamiento;
import com.proyecto.supply_core.ia.repository.ExtraccionCampoRepository;
import com.proyecto.supply_core.ia.repository.ProcesamientoIaRepository;
import com.proyecto.supply_core.notificacion.enums.EventoNotificacion;
import com.proyecto.supply_core.notificacion.service.NotificacionService;
import com.proyecto.supply_core.security.context.SesionActual;

import tools.jackson.databind.json.JsonMapper;

/**
 * Lectura de cotizaciones por archivo con IA, y su revisión humana (UC-08, primera parte).
 * <ul>
 *   <li>Al enviarse una cotización por archivo se programa su lectura; si no hay IA queda para digitar.</li>
 *   <li>Cada dato leído se guarda con su confianza. Cantidad, factor y precio son críticos: si su confianza
 *       es menor al umbral ({@code app.ia.umbral-confianza}) el comprador debe confirmarlos.</li>
 *   <li>Java interpreta y valida todos los números ({@link NumeroTexto}); un renglón ilegible no se inventa,
 *       queda marcado para revisión.</li>
 *   <li>Sobre cerrado: compras solo ve o corrige los datos cuando la RFQ ya cerró.</li>
 * </ul>
 */
@Service
public class ExtraccionService {

    private static final Logger log = LoggerFactory.getLogger(ExtraccionService.class);
    /** Datos críticos de una línea (afectan cantidades o dinero). */
    static final Set<String> CRITICOS = Set.of("cantidad", "factorConversion", "precioUnitario");
    /** Nombre del campo que marca un renglón del documento que no se pudo convertir en línea. */
    static final String RENGLON_NO_LEIDO = "renglonNoLeido";
    /** Dato de cabecera con el total impreso en el documento, para cruzarlo con el calculado por el sistema. */
    public static final String TOTAL_IMPRESO = "totalImpreso";

    private final ProcesamientoIaRepository procesamientoRepository;
    private final ExtraccionCampoRepository campoRepository;
    private final CotizacionRepository cotizacionRepository;
    private final DocumentoService documentoService;
    private final IAClient iaClient;
    private final IAProperties props;
    private final ApplicationEventPublisher eventos;
    private final NotificacionService notificacionService;
    private final AuditoriaService auditoriaService;
    private final AnalisisRepository analisisRepository;
    private final JsonMapper json = JsonMapper.builder().build();

    /**
     * @param procesamientoRepository procesamientos
     * @param campoRepository         datos leídos
     * @param cotizacionRepository    cotizaciones
     * @param documentoService        archivo original
     * @param iaClient                IA configurada
     * @param props                   configuración de IA
     * @param eventos                 publicación del evento de lectura
     * @param notificacionService     aviso al comprador
     * @param auditoriaService        registro de correcciones
     * @param analisisRepository      análisis (los datos quedan fijos al publicarlo)
     */
    public ExtraccionService(ProcesamientoIaRepository procesamientoRepository,
            ExtraccionCampoRepository campoRepository, CotizacionRepository cotizacionRepository,
            DocumentoService documentoService, IAClient iaClient, IAProperties props,
            ApplicationEventPublisher eventos, NotificacionService notificacionService,
            AuditoriaService auditoriaService, AnalisisRepository analisisRepository) {
        this.procesamientoRepository = procesamientoRepository;
        this.campoRepository = campoRepository;
        this.cotizacionRepository = cotizacionRepository;
        this.documentoService = documentoService;
        this.iaClient = iaClient;
        this.props = props;
        this.eventos = eventos;
        this.notificacionService = notificacionService;
        this.auditoriaService = auditoriaService;
        this.analisisRepository = analisisRepository;
    }

    /**
     * Programa la lectura de una cotización por archivo recién enviada (se une a la transacción del envío).
     *
     * @param c cotización enviada con origen ARCHIVO
     */
    @Transactional
    public void programar(Cotizacion c) {
        ProcesamientoIa p = procesamientoRepository.findByCotizacionId(c.getId()).orElseGet(() -> {
            ProcesamientoIa nuevo = new ProcesamientoIa();
            nuevo.setCotizacionId(c.getId());
            return nuevo;
        });
        p.setModelo(iaClient.modelo());
        p.setFinalizadoEn(null);
        if (iaClient.disponible()) {
            p.setEstado(EstadoProcesamiento.PENDIENTE);
            p.setError(null);
            procesamientoRepository.save(p);
            eventos.publishEvent(new LecturaProgramadaEvent(p.getId()));
        } else {
            p.setEstado(EstadoProcesamiento.REQUIERE_REVISION);
            p.setError("No hay IA configurada: digite la cotización cuando cierre la RFQ");
            procesamientoRepository.save(p);
        }
    }

    /**
     * Paso 1 de la lectura: marca PROCESANDO y junta el archivo y los productos de la RFQ.
     *
     * @param procesamientoId procesamiento
     * @return lo que necesita la IA
     * @throws IAException si la cotización no tiene documento
     */
    @Transactional
    public Lectura prepararLectura(Long procesamientoId) {
        ProcesamientoIa p = procesamiento(procesamientoId);
        p.setEstado(EstadoProcesamiento.PROCESANDO);
        p.setIntentos(p.getIntentos() + 1);
        p.setModelo(iaClient.modelo());
        Cotizacion c = cotizacion(p.getCotizacionId());
        if (c.getDocumentoOriginalId() == null) {
            throw new IAException("La cotización no tiene documento para leer");
        }
        DocumentoService.ContenidoDocumento doc = documentoService.leerParaSistema(c.getDocumentoOriginalId());
        List<LineaContexto> productos = c.getRfq().getLineas().stream()
                .map(l -> new LineaContexto(l.getId(), l.getProducto().getCodigo(), l.getProducto().getNombre(),
                        l.getCantidad(), l.getUnidadMedida().getCodigo(), l.getEspecificaciones()))
                .toList();
        return new Lectura(new ArchivoIA(doc.bytes(), doc.tipoMime(), doc.nombre()), productos);
    }

    /**
     * Paso 3 de la lectura: convierte lo leído en líneas de la cotización (validadas por Java) y guarda
     * cada dato con su confianza.
     *
     * @param procesamientoId procesamiento
     * @param leido           respuesta de la IA
     */
    @Transactional
    public void guardarResultado(Long procesamientoId, ExtraccionIA leido) {
        ProcesamientoIa p = procesamiento(procesamientoId);
        Cotizacion c = cotizacion(p.getCotizacionId());
        campoRepository.borrarDe(p.getId());
        c.getLineas().clear();
        cotizacionRepository.flush();

        List<ExtraccionCampo> campos = new ArrayList<>();
        BigDecimal confCabecera = confianza(leido.confianzaCabecera());
        aplicarCabecera(c, leido, confCabecera, p, campos);

        Map<Long, RfqLinea> lineasRfq = c.getRfq().getLineas().stream()
                .collect(Collectors.toMap(RfqLinea::getId, Function.identity()));
        Set<Long> usadas = new HashSet<>();
        List<LineaCreada> creadas = new ArrayList<>();
        for (ExtraccionIA.LineaExtraida le : leido.lineas() == null ? List.<ExtraccionIA.LineaExtraida>of() : leido.lineas()) {
            RfqLinea rl = lineasRfq.get(le.rfqLineaId());
            Optional<BigDecimal> cantidad = NumeroTexto.leer(le.cantidad()).filter(v -> v.signum() > 0);
            Optional<BigDecimal> precio = NumeroTexto.leer(le.precioUnitario()).filter(v -> v.signum() >= 0);
            Optional<BigDecimal> factor = NumeroTexto.leer(le.factorConversion()).filter(v -> v.signum() > 0);
            BigDecimal descuento = porcentaje(le.descuentoPct());
            BigDecimal impuesto = porcentaje(le.impuestoPct());
            if (rl == null || !usadas.add(rl.getId()) || cantidad.isEmpty() || precio.isEmpty() || descuento == null
                    || impuesto == null) {
                campos.add(campo(p, null, RENGLON_NO_LEIDO, le.descripcion() + " | cant: " + le.cantidad()
                        + " | precio: " + le.precioUnitario(), BigDecimal.ZERO, true));
                continue;
            }
            CotizacionLinea linea = ArmadorLineas.nueva(rl, cantidad.get(), unidad(le.unidad()),
                    factor.orElse(BigDecimal.ONE), precio.get(), descuento, impuesto);
            c.agregarLinea(linea);
            creadas.add(new LineaCreada(linea, le, factor.isPresent()));
        }
        CalculadoraCotizacion.recalcular(c);
        cotizacionRepository.saveAndFlush(c); // ids de las líneas para enlazar los campos
        // cruce de totales: el total impreso (copiado por la IA) contra el que calcula Java con las líneas
        CruceTotal.Resultado cruce = CruceTotal.evaluar(c.getTotal(), leido.totalImpreso());
        if (cruce.hayTotal()) {
            campos.add(campo(p, null, TOTAL_IMPRESO, leido.totalImpreso(), confianza(cruce.cuadra() ? 1 : 0), true));
        }

        for (LineaCreada creada : creadas) {
            CotizacionLinea linea = creada.linea();
            ExtraccionIA.LineaExtraida le = creada.leida();
            boolean factorLeido = creada.factorLeido();
            campos.add(campo(p, linea.getId(), "cantidad", le.cantidad(), confianza(le.confianzaCantidad()), true));
            campos.add(campo(p, linea.getId(), "unidad", le.unidad(), confianza(le.confianzaUnidad()), false));
            campos.add(campo(p, linea.getId(), "factorConversion", le.factorConversion(),
                    factorLeido ? confianza(le.confianzaUnidad()) : BigDecimal.ZERO, true));
            campos.add(campo(p, linea.getId(), "precioUnitario", le.precioUnitario(), confianza(le.confianzaPrecio()),
                    true));
            campos.add(campo(p, linea.getId(), "descuentoPct", le.descuentoPct(), confianza(le.confianzaPrecio()), false));
            campos.add(campo(p, linea.getId(), "impuestoPct", le.impuestoPct(), confianza(le.confianzaPrecio()), false));
        }
        campoRepository.saveAll(campos);

        p.setRespuestaJson(json.writeValueAsString(leido));
        p.setError(null);
        p.setFinalizadoEn(LocalDateTime.now());
        boolean dudas = campos.stream().anyMatch(cp -> cp.pendiente(props.umbralConfianza())) || c.getLineas().isEmpty();
        p.setEstado(dudas ? EstadoProcesamiento.REQUIERE_REVISION : EstadoProcesamiento.COMPLETADO);
        if (dudas) {
            p.setError(c.getLineas().isEmpty() ? "La IA no encontró precios válidos en el documento"
                    : !cruce.cuadra() ? cruce.mensaje()
                    : "Hay datos críticos con baja confianza: confírmelos antes de publicar el análisis");
            avisarRevision(c, p.getError());
        }
        log.info("Procesamiento {}: {} líneas, {} campos, estado {}", p.getId(), c.getLineas().size(), campos.size(),
                p.getEstado());
    }

    /**
     * Registra que la lectura falló (se puede reintentar o digitar).
     *
     * @param procesamientoId procesamiento
     * @param mensaje         motivo
     */
    @Transactional
    public void guardarError(Long procesamientoId, String mensaje) {
        procesamientoRepository.findById(procesamientoId).ifPresent(p -> {
            p.setEstado(EstadoProcesamiento.ERROR);
            p.setError(mensaje == null ? "Error desconocido" : mensaje.substring(0, Math.min(mensaje.length(), 1000)));
            p.setFinalizadoEn(LocalDateTime.now());
            cotizacionRepository.findWithRfqById(p.getCotizacionId())
                    .ifPresent(c -> avisarRevision(c, "la IA no pudo leerla: " + p.getError()));
            log.warn("Procesamiento {} en ERROR: {}", procesamientoId, p.getError());
        });
    }

    /**
     * Estado de lectura y datos leídos de una cotización (solo con la RFQ cerrada).
     *
     * @param cotizacionId cotización
     * @return procesamiento con sus campos
     * @throws NoEncontradoException si la cotización no se leyó con IA
     */
    @Transactional(readOnly = true)
    public ProcesamientoResponse obtener(Long cotizacionId) {
        exigirSobreAbierto(cotizacion(cotizacionId));
        ProcesamientoIa p = procesamientoRepository.findByCotizacionId(cotizacionId)
                .orElseThrow(() -> new NoEncontradoException("La cotización no tiene lectura de IA"));
        return respuesta(p);
    }

    /**
     * Confirma o corrige un dato leído y lo aplica a la cotización (recalcula montos).
     *
     * @param cotizacionId cotización
     * @param campoId      dato
     * @param valor        valor correcto
     * @return procesamiento actualizado (COMPLETADO si ya no quedan dudas)
     * @throws ParametroInvalidoException si el valor no es válido para ese dato
     */
    @Transactional
    public ProcesamientoResponse confirmar(Long cotizacionId, Long campoId, String valor) {
        Cotizacion c = cotizacion(cotizacionId);
        exigirSobreAbierto(c);
        ProcesamientoIa p = procesamientoRepository.findByCotizacionId(cotizacionId)
                .orElseThrow(() -> new NoEncontradoException("La cotización no tiene lectura de IA"));
        exigirModificable(c);
        ExtraccionCampo campo = campoRepository.findByIdAndProcesamiento_Id(campoId, p.getId())
                .orElseThrow(() -> new NoEncontradoException("Dato", campoId));
        String texto = valor.trim();
        aplicar(c, campo, texto);
        campo.setValorConfirmado(texto);
        campo.setConfirmadoPorUsuarioId(SesionActual.usuario().id());
        campo.setConfirmadoEn(LocalDateTime.now());
        CalculadoraCotizacion.recalcular(c);
        List<ExtraccionCampo> todos = campoRepository.findByProcesamiento_IdOrderByIdAsc(p.getId());
        CruceTotal.Resultado cruce = reevaluarTotal(c, todos);
        boolean pendientes = todos.stream().anyMatch(cp -> cp.pendiente(props.umbralConfianza()));
        if (!pendientes && !c.getLineas().isEmpty()) {
            p.setEstado(EstadoProcesamiento.COMPLETADO);
            p.setError(null);
        } else if (cruce != null && !cruce.cuadra()) {
            p.setError(cruce.mensaje()); // los montos cambiaron: la alerta muestra las cifras de ahora
        }
        auditoriaService.exito(AccionAuditoria.EXTRACCION_CONFIRMAR, EntidadAuditada.COTIZACION, cotizacionId,
                campo.getCampo() + ": leído=" + campo.getValorExtraido() + ", confirmado=" + texto);
        return respuesta(p);
    }

    /**
     * Digitación manual de una cotización por archivo: reemplaza sus líneas y la deja lista para el análisis.
     *
     * @param cotizacionId cotización
     * @param lineas       precios leídos por una persona
     * @return procesamiento COMPLETADO
     * @throws NegocioException si no es una cotización por archivo enviada
     */
    @Transactional
    public ProcesamientoResponse digitar(Long cotizacionId, List<LineaCotizacionRequest> lineas) {
        Cotizacion c = cotizacion(cotizacionId);
        exigirSobreAbierto(c);
        if (c.getOrigen() != OrigenCotizacion.ARCHIVO) {
            throw new NegocioException("COTIZACION_NO_DIGITABLE", "Solo se digitan cotizaciones que llegaron por archivo");
        }
        exigirModificable(c);
        ProcesamientoIa p = procesamientoRepository.findByCotizacionId(cotizacionId).orElseGet(() -> {
            ProcesamientoIa nuevo = new ProcesamientoIa();
            nuevo.setCotizacionId(cotizacionId);
            return procesamientoRepository.save(nuevo);
        });
        campoRepository.borrarDe(p.getId());
        c.getLineas().clear();
        cotizacionRepository.flush();
        ArmadorLineas.armar(c, c.getRfq(), lineas);
        p.setEstado(EstadoProcesamiento.COMPLETADO);
        p.setModelo("manual");
        p.setError(null);
        p.setFinalizadoEn(LocalDateTime.now());
        auditoriaService.exito(AccionAuditoria.COTIZACION_DIGITAR, EntidadAuditada.COTIZACION, cotizacionId,
                "lineas=" + c.getLineas().size() + "; total=" + c.getTotal());
        return respuesta(p);
    }

    /**
     * Vuelve a leer con IA una cotización que falló o quedó con dudas.
     *
     * @param cotizacionId cotización
     * @return procesamiento PENDIENTE
     * @throws NegocioException si no hay IA o el estado no permite reintentar
     */
    @Transactional
    public ProcesamientoResponse reintentar(Long cotizacionId) {
        if (!iaClient.disponible()) {
            throw new NegocioException("IA_NO_DISPONIBLE", "No hay IA configurada; digite la cotización");
        }
        Cotizacion c = cotizacion(cotizacionId);
        ProcesamientoIa p = procesamientoRepository.findByCotizacionId(cotizacionId)
                .orElseThrow(() -> new NoEncontradoException("La cotización no tiene lectura de IA"));
        if (p.getEstado() != EstadoProcesamiento.ERROR && p.getEstado() != EstadoProcesamiento.REQUIERE_REVISION) {
            throw new NegocioException("TRANSICION_INVALIDA", "La lectura está " + p.getEstado() + "; no se reintenta");
        }
        exigirModificable(c);
        programar(c);
        return respuesta(p);
    }

    /**
     * Estado de lectura de varias cotizaciones (para saber si el análisis se puede publicar).
     *
     * @param cotizacionIds cotizaciones
     * @return estado por cotización (las que no tienen lectura no aparecen)
     */
    @Transactional(readOnly = true)
    public Map<Long, EstadoProcesamiento> estados(Collection<Long> cotizacionIds) {
        return procesamientoRepository.findByCotizacionIdIn(cotizacionIds).stream()
                .collect(Collectors.toMap(ProcesamientoIa::getCotizacionId, ProcesamientoIa::getEstado));
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Datos de cabecera: solo se llenan los que el proveedor no escribió en el formulario.
     *
     * @param c         cotización
     * @param leido     respuesta de la IA
     * @param confianza confianza de la cabecera
     * @param p         procesamiento
     * @param campos    acumulador de campos
     */
    private static void aplicarCabecera(Cotizacion c, ExtraccionIA leido, BigDecimal confianza, ProcesamientoIa p,
            List<ExtraccionCampo> campos) {
        if (c.getPlazoEntregaDias() == null) {
            entero(leido.plazoEntregaDias()).ifPresent(c::setPlazoEntregaDias);
        }
        if (c.getGarantiaMeses() == null) {
            entero(leido.garantiaMeses()).ifPresent(c::setGarantiaMeses);
        }
        if (c.getValidezHasta() == null) {
            fecha(leido.validezHasta()).ifPresent(c::setValidezHasta);
        }
        if (c.getCondicionesComerciales() == null && leido.condiciones() != null && !leido.condiciones().isBlank()) {
            String cond = leido.condiciones().trim();
            c.setCondicionesComerciales(cond.substring(0, Math.min(cond.length(), 1000)));
        }
        if (leido.moneda() != null && leido.moneda().trim().matches("[A-Za-z]{3}")) {
            c.setMoneda(leido.moneda().trim().toUpperCase());
        }
        campos.add(campo(p, null, "plazoEntregaDias", leido.plazoEntregaDias(), confianza, false));
        campos.add(campo(p, null, "garantiaMeses", leido.garantiaMeses(), confianza, false));
        campos.add(campo(p, null, "validezHasta", leido.validezHasta(), confianza, false));
        campos.add(campo(p, null, "moneda", leido.moneda(), confianza, false));
    }

    /**
     * Aplica un valor confirmado a la cotización.
     *
     * @param c     cotización con líneas
     * @param campo dato
     * @param valor valor confirmado
     * @throws ParametroInvalidoException si el valor no sirve para ese dato
     */
    private static void aplicar(Cotizacion c, ExtraccionCampo campo, String valor) {
        if (campo.getCotizacionLineaId() == null) {
            switch (campo.getCampo()) {
                case "plazoEntregaDias" -> c.setPlazoEntregaDias(entero(valor).orElseThrow(() -> invalido(campo)));
                case "garantiaMeses" -> c.setGarantiaMeses(entero(valor).orElseThrow(() -> invalido(campo)));
                case "validezHasta" -> c.setValidezHasta(fecha(valor).orElseThrow(() -> invalido(campo)));
                case "moneda" -> {
                    if (!valor.matches("[A-Za-z]{3}")) {
                        throw invalido(campo);
                    }
                    c.setMoneda(valor.toUpperCase());
                }
                case TOTAL_IMPRESO -> NumeroTexto.leer(valor).filter(v -> v.signum() > 0).orElseThrow(() -> invalido(campo));
                default -> { /* renglón no leído: la confirmación solo deja constancia de la revisión */ }
            }
            return;
        }
        CotizacionLinea linea = c.getLineas().stream().filter(l -> campo.getCotizacionLineaId().equals(l.getId()))
                .findFirst().orElseThrow(() -> new NoEncontradoException("Línea", campo.getCotizacionLineaId()));
        switch (campo.getCampo()) {
            case "cantidad" -> linea.setCantidadOfertada(
                    NumeroTexto.leer(valor).filter(v -> v.signum() > 0).orElseThrow(() -> invalido(campo)));
            case "factorConversion" -> linea.setFactorConversion(
                    NumeroTexto.leer(valor).filter(v -> v.signum() > 0).orElseThrow(() -> invalido(campo)));
            case "precioUnitario" -> linea.setPrecioUnitarioOfertado(
                    NumeroTexto.leer(valor).filter(v -> v.signum() >= 0).orElseThrow(() -> invalido(campo)));
            case "descuentoPct" -> linea.setDescuentoPct(Optional.ofNullable(porcentaje(valor)).orElseThrow(() -> invalido(campo)));
            case "impuestoPct" -> linea.setImpuestoPct(Optional.ofNullable(porcentaje(valor)).orElseThrow(() -> invalido(campo)));
            case "unidad" -> linea.setUnidadOfertada(unidad(valor));
            default -> throw invalido(campo);
        }
    }

    /**
     * Vuelve a cruzar el total impreso con el calculado después de corregir un dato. Si ya cuadra, el sistema da el dato
     * por verificado (confianza 1); si aún no, sigue pendiente. Si una persona ya confirmó el total impreso, es su
     * decisión y no se toca.
     *
     * @param c     cotización con los montos recalculados
     * @param todos datos leídos del procesamiento
     * @return resultado del cruce, o {@code null} si el documento no trajo total impreso
     */
    private static CruceTotal.Resultado reevaluarTotal(Cotizacion c, List<ExtraccionCampo> todos) {
        for (ExtraccionCampo cp : todos) {
            if (cp.getCotizacionLineaId() == null && TOTAL_IMPRESO.equals(cp.getCampo())) {
                String impreso = cp.getValorConfirmado() != null ? cp.getValorConfirmado() : cp.getValorExtraido();
                CruceTotal.Resultado r = CruceTotal.evaluar(c.getTotal(), impreso);
                if (cp.getValorConfirmado() == null) {
                    cp.setConfianza(confianza(r.cuadra() ? 1 : 0));
                }
                return r;
            }
        }
        return null;
    }

    /**
     * Compras solo ve precios cuando la RFQ ya no está abierta.
     *
     * @param c cotización con su RFQ
     * @throws NegocioException si la RFQ sigue ABIERTA
     */
    private static void exigirSobreAbierto(Cotizacion c) {
        if (c.getRfq().getEstado() == EstadoRfq.ABIERTA) {
            throw new NegocioException("SOBRE_CERRADO",
                    "La " + c.getRfq().getConsecutivo() + " sigue abierta; las cotizaciones se revisan al cerrarla");
        }
    }

    /**
     * Los datos de una cotización se pueden corregir mientras esté ENVIADA o EN_ANALISIS y el análisis de su
     * RFQ no se haya publicado (después, el ranking queda fijo).
     *
     * @param c cotización con su RFQ
     * @throws NegocioException si ya no se puede modificar
     */
    private void exigirModificable(Cotizacion c) {
        if (c.getEstado() != EstadoCotizacion.ENVIADA && c.getEstado() != EstadoCotizacion.EN_ANALISIS) {
            throw new NegocioException("TRANSICION_INVALIDA", "La cotización está " + c.getEstado()
                    + "; ya no se modifican sus datos");
        }
        if (analisisRepository.findFirstByRfq_IdAndEstado(c.getRfq().getId(), EstadoAnalisis.PUBLICADO).isPresent()) {
            throw new NegocioException("ANALISIS_PUBLICADO",
                    "El análisis de la " + c.getRfq().getConsecutivo() + " ya se publicó; los datos quedaron fijos");
        }
    }

    /**
     * Avisa al comprador de la RFQ que una cotización necesita revisión.
     *
     * @param c       cotización
     * @param detalle motivo
     */
    private void avisarRevision(Cotizacion c, String detalle) {
        Rfq rfq = c.getRfq();
        notificacionService.notificar(rfq.getUsuarioId(), EventoNotificacion.EXTRACCION_REQUIERE_REVISION,
                "La cotización de " + c.getProveedor().getRazonSocial() + " en la " + rfq.getConsecutivo()
                        + " requiere revisión: " + detalle);
    }

    /**
     * Respuesta con campos.
     *
     * @param p procesamiento
     * @return DTO
     */
    private ProcesamientoResponse respuesta(ProcesamientoIa p) {
        return ProcesamientoResponse.of(p, p.getId() == null ? List.of()
                : campoRepository.findByProcesamiento_IdOrderByIdAsc(p.getId()), props.umbralConfianza());
    }

    /**
     * Carga el procesamiento.
     *
     * @param id procesamiento
     * @return entidad
     */
    private ProcesamientoIa procesamiento(Long id) {
        return procesamientoRepository.findById(id).orElseThrow(() -> new NoEncontradoException("Procesamiento", id));
    }

    /**
     * Carga la cotización con su RFQ y proveedor.
     *
     * @param id cotización
     * @return entidad
     */
    private Cotizacion cotizacion(Long id) {
        return cotizacionRepository.findWithRfqById(id).orElseThrow(() -> new NoEncontradoException("Cotización", id));
    }

    /**
     * Crea un campo.
     *
     * @param p         procesamiento
     * @param lineaId   línea o {@code null}
     * @param nombre    dato
     * @param valor     valor leído
     * @param confianza confianza 0–1
     * @param critico   si es crítico
     * @return campo sin guardar
     */
    private static ExtraccionCampo campo(ProcesamientoIa p, Long lineaId, String nombre, String valor,
            BigDecimal confianza, boolean critico) {
        ExtraccionCampo c = new ExtraccionCampo();
        c.setProcesamiento(p);
        c.setCotizacionLineaId(lineaId);
        c.setCampo(nombre);
        c.setValorExtraido(valor == null ? null : valor.substring(0, Math.min(valor.length(), 500)));
        c.setConfianza(confianza);
        c.setCritico(critico);
        return c;
    }

    /**
     * Confianza acotada a 0–1 con 3 decimales.
     *
     * @param valor confianza reportada
     * @return confianza válida
     */
    static BigDecimal confianza(double valor) {
        double acotado = Double.isNaN(valor) ? 0 : Math.max(0, Math.min(1, valor));
        return BigDecimal.valueOf(acotado).setScale(3, RoundingMode.HALF_UP);
    }

    /**
     * Porcentaje 0–100 (vacío = 0).
     *
     * @param texto valor leído
     * @return porcentaje o {@code null} si no es válido
     */
    static BigDecimal porcentaje(String texto) {
        if (texto == null || texto.isBlank()) {
            return BigDecimal.ZERO;
        }
        return NumeroTexto.leer(texto).filter(v -> v.signum() >= 0 && v.compareTo(BigDecimal.valueOf(100)) <= 0)
                .map(v -> v.setScale(2, RoundingMode.HALF_UP)).orElse(null);
    }

    /**
     * Entero no negativo.
     *
     * @param texto valor leído
     * @return entero, si es válido
     */
    private static Optional<Integer> entero(String texto) {
        return NumeroTexto.leer(texto).filter(v -> v.signum() >= 0 && v.compareTo(BigDecimal.valueOf(100000)) < 0)
                .map(v -> v.setScale(0, RoundingMode.HALF_UP).intValue());
    }

    /**
     * Fecha AAAA-MM-DD.
     *
     * @param texto valor leído
     * @return fecha, si es válida
     */
    private static Optional<LocalDate> fecha(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(texto.trim()));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /**
     * Presentación ofertada (máximo 50 caracteres; vacía = "und").
     *
     * @param texto valor leído
     * @return presentación
     */
    private static String unidad(String texto) {
        String t = texto == null || texto.isBlank() ? "und" : texto.trim();
        return t.substring(0, Math.min(t.length(), 50));
    }

    /**
     * Error de valor inválido.
     *
     * @param campo dato
     * @return excepción 400
     */
    private static ParametroInvalidoException invalido(ExtraccionCampo campo) {
        return new ParametroInvalidoException("El valor no es válido para " + campo.getCampo());
    }

    /**
     * Lo que necesita la IA para leer una cotización.
     *
     * @param archivo   documento original
     * @param productos líneas de la RFQ
     */
    public record Lectura(ArchivoIA archivo, List<LineaContexto> productos) {
    }

    /**
     * Línea creada a partir de un renglón leído (para enlazar sus campos cuando ya tenga id).
     *
     * @param linea       línea de la cotización
     * @param leida       renglón leído por la IA
     * @param factorLeido si la IA leyó un factor de conversión válido
     */
    private record LineaCreada(CotizacionLinea linea, ExtraccionIA.LineaExtraida leida, boolean factorLeido) {
    }
}
