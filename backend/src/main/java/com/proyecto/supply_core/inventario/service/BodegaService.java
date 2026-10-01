package com.proyecto.supply_core.inventario.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.util.EmpresaUnica;
import com.proyecto.supply_core.empresa.entity.Sede;
import com.proyecto.supply_core.empresa.repository.SedeRepository;
import com.proyecto.supply_core.inventario.dto.BodegaRequest;
import com.proyecto.supply_core.inventario.dto.BodegaResponse;
import com.proyecto.supply_core.inventario.entity.Bodega;
import com.proyecto.supply_core.inventario.repository.BodegaRepository;
import com.proyecto.supply_core.inventario.repository.StockProductoBodegaRepository;

/** Administración de bodegas. Nombre único; solo se desactiva una bodega sin stock. */
@Service
public class BodegaService {

    private final BodegaRepository bodegaRepository;
    private final SedeRepository sedeRepository;
    private final StockProductoBodegaRepository stockRepository;
    private final AuditoriaService auditoriaService;

    /**
     * @param bodegaRepository acceso a bodegas
     * @param sedeRepository   acceso a sedes
     * @param stockRepository  para saber si la bodega tiene stock
     * @param auditoriaService registro de cambios
     */
    public BodegaService(BodegaRepository bodegaRepository, SedeRepository sedeRepository,
            StockProductoBodegaRepository stockRepository, AuditoriaService auditoriaService) {
        this.bodegaRepository = bodegaRepository;
        this.sedeRepository = sedeRepository;
        this.stockRepository = stockRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Lista las bodegas.
     *
     * @param activo filtro por estado ({@code null} = todas)
     * @return bodegas por nombre
     */
    @Transactional(readOnly = true)
    public List<BodegaResponse> listar(Boolean activo) {
        List<Bodega> bodegas = activo == null
                ? bodegaRepository.findByEmpresaIdOrderByNombreAsc(EmpresaUnica.ID)
                : bodegaRepository.findByEmpresaIdAndActivoOrderByNombreAsc(EmpresaUnica.ID, activo);
        return bodegas.stream().map(BodegaResponse::of).toList();
    }

    /**
     * Detalle de una bodega.
     *
     * @param id id
     * @return bodega
     * @throws NoEncontradoException si no existe
     */
    @Transactional(readOnly = true)
    public BodegaResponse obtener(Long id) {
        return BodegaResponse.of(buscar(id));
    }

    /**
     * Crea una bodega.
     *
     * @param req datos validados (sede activa)
     * @return bodega creada
     * @throws NegocioException si ya existe una bodega con ese nombre
     */
    @Transactional
    public BodegaResponse crear(BodegaRequest req) {
        String nombre = req.nombre().trim();
        if (bodegaRepository.existsByEmpresaIdAndNombreIgnoreCase(EmpresaUnica.ID, nombre)) {
            throw new NegocioException("NOMBRE_DUPLICADO", "Ya existe una bodega llamada " + nombre);
        }
        Bodega b = new Bodega();
        b.setEmpresaId(EmpresaUnica.ID);
        aplicar(b, req);
        Bodega guardada = bodegaRepository.save(b);
        auditoriaService.exito(AccionAuditoria.BODEGA_CREAR, EntidadAuditada.BODEGA, guardada.getId(), "nombre=" + nombre);
        return BodegaResponse.of(guardada);
    }

    /**
     * Edita una bodega.
     *
     * @param id  bodega
     * @param req datos validados
     * @return bodega actualizada
     * @throws NoEncontradoException si no existe
     * @throws NegocioException      si otra bodega ya usa el nombre
     */
    @Transactional
    public BodegaResponse actualizar(Long id, BodegaRequest req) {
        Bodega b = buscar(id);
        String nombre = req.nombre().trim();
        if (bodegaRepository.existsByEmpresaIdAndNombreIgnoreCaseAndIdNot(EmpresaUnica.ID, nombre, id)) {
            throw new NegocioException("NOMBRE_DUPLICADO", "Ya existe una bodega llamada " + nombre);
        }
        String antes = b.getNombre();
        aplicar(b, req);
        auditoriaService.exito(AccionAuditoria.BODEGA_EDITAR, EntidadAuditada.BODEGA, id,
                "nombre: " + antes + " -> " + b.getNombre());
        return BodegaResponse.of(b);
    }

    /**
     * Activa o desactiva una bodega. Para desactivarla no puede tener stock.
     *
     * @param id     bodega
     * @param activo nuevo estado
     * @return bodega actualizada
     * @throws NoEncontradoException si no existe
     * @throws NegocioException      si se intenta desactivar con stock
     */
    @Transactional
    public BodegaResponse cambiarEstado(Long id, boolean activo) {
        Bodega b = buscar(id);
        if (!activo && stockRepository.existsByBodegaIdAndCantidadGreaterThan(id, BigDecimal.ZERO)) {
            throw new NegocioException("BODEGA_CON_STOCK",
                    "La bodega tiene stock; trasládelo o ajústelo antes de desactivarla");
        }
        b.setActivo(activo);
        auditoriaService.exito(AccionAuditoria.BODEGA_CAMBIAR_ESTADO, EntidadAuditada.BODEGA, id,
                "nombre=" + b.getNombre() + ", activo=" + activo);
        return BodegaResponse.of(b);
    }

    /**
     * Copia los datos del request a la entidad.
     *
     * @param b   bodega destino
     * @param req datos validados
     */
    private void aplicar(Bodega b, BodegaRequest req) {
        b.setNombre(req.nombre().trim());
        b.setDireccion(req.direccion() == null || req.direccion().isBlank() ? null : req.direccion().trim());
        b.setSede(req.sedeId() == null ? null : sede(req.sedeId()));
    }

    /**
     * Carga la sede ({@code @SedeActiva} ya validó que existe y está activa).
     *
     * @param id id de la sede
     * @return sede
     * @throws NoEncontradoException si no existe (solo si se llamó al Service sin {@code @Valid})
     */
    private Sede sede(Long id) {
        return sedeRepository.findById(id).orElseThrow(() -> new NoEncontradoException("Sede", id));
    }

    /**
     * Busca la bodega con su sede.
     *
     * @param id id
     * @return entidad
     * @throws NoEncontradoException si no existe
     */
    private Bodega buscar(Long id) {
        return bodegaRepository.findWithSedeById(id).orElseThrow(() -> new NoEncontradoException("Bodega", id));
    }
}
