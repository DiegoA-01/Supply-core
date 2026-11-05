package com.proyecto.supply_core.catalogo.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.catalogo.dto.CategoriaRequest;
import com.proyecto.supply_core.catalogo.dto.CategoriaResponse;
import com.proyecto.supply_core.catalogo.dto.CategoriaUpdateRequest;
import com.proyecto.supply_core.catalogo.entity.CategoriaProducto;
import com.proyecto.supply_core.catalogo.repository.CategoriaProductoRepository;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;

/**
 * Administración de categorías de producto. Nombre único; nunca se borran.
 * Una categoría inactiva no aparece en el registro de proveedores ni se usará en productos nuevos;
 * los proveedores y productos que ya la tienen la conservan.
 */
@Service
public class CategoriaService {

    private final CategoriaProductoRepository categoriaRepository;
    private final AuditoriaService auditoriaService;

    /**
     * @param categoriaRepository acceso a categorías
     * @param auditoriaService    registro de cambios
     */
    public CategoriaService(CategoriaProductoRepository categoriaRepository, AuditoriaService auditoriaService) {
        this.categoriaRepository = categoriaRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Lista las categorías.
     *
     * @param activo filtro por estado ({@code null} = todas)
     * @return categorías ordenadas por nombre
     */
    @Transactional(readOnly = true)
    public List<CategoriaResponse> listar(Boolean activo) {
        List<CategoriaProducto> categorias = activo == null
                ? categoriaRepository.findAllByOrderByNombreAsc()
                : categoriaRepository.findByActivoOrderByNombreAsc(activo);
        return categorias.stream().map(CategoriaResponse::of).toList();
    }

    /**
     * Detalle de una categoría.
     *
     * @param id id
     * @return categoría
     * @throws NoEncontradoException si no existe
     */
    @Transactional(readOnly = true)
    public CategoriaResponse obtener(Long id) {
        return CategoriaResponse.of(buscar(id));
    }

    /**
     * Crea una categoría ({@code @NombreCategoriaUnico} ya validó el nombre).
     *
     * @param req datos validados
     * @return categoría creada
     */
    @Transactional
    public CategoriaResponse crear(CategoriaRequest req) {
        CategoriaProducto c = new CategoriaProducto();
        c.setNombre(req.nombre().trim());
        CategoriaProducto guardada = categoriaRepository.save(c);
        auditoriaService.exito(AccionAuditoria.CATEGORIA_CREAR, EntidadAuditada.CATEGORIA_PRODUCTO,
                guardada.getId(), "nombre=" + guardada.getNombre());
        return CategoriaResponse.of(guardada);
    }

    /**
     * Cambia el nombre de una categoría.
     *
     * @param id  categoría
     * @param req nombre nuevo
     * @return categoría actualizada
     * @throws NoEncontradoException si no existe
     * @throws NegocioException      si otra categoría ya usa el nombre
     */
    @Transactional
    public CategoriaResponse actualizar(Long id, CategoriaUpdateRequest req) {
        CategoriaProducto c = buscar(id);
        String nombre = req.nombre().trim();
        if (categoriaRepository.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw new NegocioException("NOMBRE_DUPLICADO", "Ya existe una categoría llamada " + nombre);
        }
        String antes = c.getNombre();
        c.setNombre(nombre);
        auditoriaService.exito(AccionAuditoria.CATEGORIA_EDITAR, EntidadAuditada.CATEGORIA_PRODUCTO, id,
                "nombre: " + antes + " -> " + nombre);
        return CategoriaResponse.of(c);
    }

    /**
     * Activa o desactiva una categoría.
     *
     * @param id     categoría
     * @param activo nuevo estado
     * @return categoría actualizada
     * @throws NoEncontradoException si no existe
     */
    @Transactional
    public CategoriaResponse cambiarEstado(Long id, boolean activo) {
        CategoriaProducto c = buscar(id);
        c.setActivo(activo);
        auditoriaService.exito(AccionAuditoria.CATEGORIA_CAMBIAR_ESTADO, EntidadAuditada.CATEGORIA_PRODUCTO, id,
                "nombre=" + c.getNombre() + ", activo=" + activo);
        return CategoriaResponse.of(c);
    }

    /**
     * Busca la categoría.
     *
     * @param id id
     * @return entidad
     * @throws NoEncontradoException si no existe
     */
    private CategoriaProducto buscar(Long id) {
        return categoriaRepository.findById(id).orElseThrow(() -> new NoEncontradoException("Categoría", id));
    }
}
