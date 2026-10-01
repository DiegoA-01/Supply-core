package com.proyecto.supply_core.empresa.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.util.EmpresaUnica;
import com.proyecto.supply_core.empresa.dto.SedeRequest;
import com.proyecto.supply_core.empresa.dto.SedeResponse;
import com.proyecto.supply_core.empresa.entity.Sede;
import com.proyecto.supply_core.empresa.repository.SedeRepository;
import com.proyecto.supply_core.inventario.repository.BodegaRepository;

/** Administración de sedes. Nombre único por empresa; nunca se borran (se desactivan). */
@Service
public class SedeService {

    private final SedeRepository sedeRepository;
    private final BodegaRepository bodegaRepository;
    private final AuditoriaService auditoriaService;

    /**
     * @param sedeRepository   acceso a sedes
     * @param bodegaRepository para no desactivar sedes con bodegas activas
     * @param auditoriaService registro de cambios
     */
    public SedeService(SedeRepository sedeRepository, BodegaRepository bodegaRepository,
            AuditoriaService auditoriaService) {
        this.sedeRepository = sedeRepository;
        this.bodegaRepository = bodegaRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Lista las sedes.
     *
     * @param activo filtro por estado ({@code null} = todas)
     * @return sedes ordenadas por nombre
     */
    @Transactional(readOnly = true)
    public List<SedeResponse> listar(Boolean activo) {
        List<Sede> sedes = activo == null
                ? sedeRepository.findByEmpresaIdOrderByNombreAsc(EmpresaUnica.ID)
                : sedeRepository.findByEmpresaIdAndActivoOrderByNombreAsc(EmpresaUnica.ID, activo);
        return sedes.stream().map(SedeResponse::of).toList();
    }

    /**
     * Detalle de una sede.
     *
     * @param id id de la sede
     * @return sede
     * @throws NoEncontradoException si no existe
     */
    @Transactional(readOnly = true)
    public SedeResponse obtener(Long id) {
        return SedeResponse.of(buscar(id));
    }

    /**
     * Crea una sede.
     *
     * @param req datos validados
     * @return sede creada
     * @throws NegocioException si ya existe una sede con ese nombre
     */
    @Transactional
    public SedeResponse crear(SedeRequest req) {
        String nombre = req.nombre().trim();
        if (sedeRepository.existsByEmpresaIdAndNombreIgnoreCase(EmpresaUnica.ID, nombre)) {
            throw new NegocioException("NOMBRE_DUPLICADO", "Ya existe una sede llamada " + nombre);
        }
        Sede s = new Sede();
        s.setEmpresaId(EmpresaUnica.ID);
        aplicar(s, req);
        Sede guardada = sedeRepository.save(s);
        auditoriaService.exito(AccionAuditoria.SEDE_CREAR, EntidadAuditada.SEDE, guardada.getId(), "nombre=" + nombre);
        return SedeResponse.of(guardada);
    }

    /**
     * Edita una sede.
     *
     * @param id  sede a editar
     * @param req datos validados
     * @return sede actualizada
     * @throws NoEncontradoException si no existe
     * @throws NegocioException      si otra sede ya usa el nombre
     */
    @Transactional
    public SedeResponse actualizar(Long id, SedeRequest req) {
        Sede s = buscar(id);
        String nombre = req.nombre().trim();
        if (sedeRepository.existsByEmpresaIdAndNombreIgnoreCaseAndIdNot(EmpresaUnica.ID, nombre, id)) {
            throw new NegocioException("NOMBRE_DUPLICADO", "Ya existe una sede llamada " + nombre);
        }
        String antes = s.getNombre();
        aplicar(s, req);
        auditoriaService.exito(AccionAuditoria.SEDE_EDITAR, EntidadAuditada.SEDE, id,
                "nombre: " + antes + " -> " + s.getNombre());
        return SedeResponse.of(s);
    }

    /**
     * Activa o desactiva una sede.
     *
     * @param id     sede
     * @param activo nuevo estado
     * @return sede actualizada
     * @throws NoEncontradoException si no existe
     * @throws NegocioException      si se intenta desactivar una sede con bodegas activas
     */
    @Transactional
    public SedeResponse cambiarEstado(Long id, boolean activo) {
        Sede s = buscar(id);
        if (!activo && bodegaRepository.existsBySedeIdAndActivoTrue(id)) {
            throw new NegocioException("SEDE_CON_BODEGAS", "La sede tiene bodegas activas; desactívelas primero");
        }
        s.setActivo(activo);
        auditoriaService.exito(AccionAuditoria.SEDE_CAMBIAR_ESTADO, EntidadAuditada.SEDE, id,
                "nombre=" + s.getNombre() + ", activo=" + activo);
        return SedeResponse.of(s);
    }

    /**
     * Copia los datos del request a la entidad.
     *
     * @param s   sede destino
     * @param req datos validados
     */
    private static void aplicar(Sede s, SedeRequest req) {
        s.setNombre(req.nombre().trim());
        s.setDireccion(req.direccion() == null || req.direccion().isBlank() ? null : req.direccion().trim());
        s.setLatitud(req.latitud());
        s.setLongitud(req.longitud());
    }

    /**
     * Busca la sede.
     *
     * @param id id
     * @return entidad
     * @throws NoEncontradoException si no existe
     */
    private Sede buscar(Long id) {
        return sedeRepository.findById(id).orElseThrow(() -> new NoEncontradoException("Sede", id));
    }
}
