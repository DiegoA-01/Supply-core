package com.proyecto.supply_core.empresa.service;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.common.util.EmpresaUnica;
import com.proyecto.supply_core.empresa.dto.CentroCostoRequest;
import com.proyecto.supply_core.empresa.dto.CentroCostoResponse;
import com.proyecto.supply_core.empresa.dto.CentroCostoUpdateRequest;
import com.proyecto.supply_core.empresa.entity.CentroCosto;
import com.proyecto.supply_core.empresa.repository.CentroCostoRepository;

/**
 * Administración de centros de costo. Código único en mayúsculas; nunca se borran.
 * Un centro inactivo no se puede asignar a usuarios nuevos ni usar en solicitudes nuevas
 * ({@code @CentroCostoActivo}); los registros históricos lo conservan.
 */
@Service
public class CentroCostoService {

    private final CentroCostoRepository centroCostoRepository;
    private final AuditoriaService auditoriaService;

    /**
     * @param centroCostoRepository acceso a centros de costo
     * @param auditoriaService      registro de cambios
     */
    public CentroCostoService(CentroCostoRepository centroCostoRepository, AuditoriaService auditoriaService) {
        this.centroCostoRepository = centroCostoRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Lista los centros de costo.
     *
     * @param activo filtro por estado ({@code null} = todos)
     * @return centros ordenados por código
     */
    @Transactional(readOnly = true)
    public List<CentroCostoResponse> listar(Boolean activo) {
        List<CentroCosto> centros = activo == null
                ? centroCostoRepository.findByEmpresaIdOrderByCodigoAsc(EmpresaUnica.ID)
                : centroCostoRepository.findByEmpresaIdAndActivoOrderByCodigoAsc(EmpresaUnica.ID, activo);
        return centros.stream().map(CentroCostoResponse::of).toList();
    }

    /**
     * Detalle de un centro de costo.
     *
     * @param id id
     * @return centro de costo
     * @throws NoEncontradoException si no existe
     */
    @Transactional(readOnly = true)
    public CentroCostoResponse obtener(Long id) {
        return CentroCostoResponse.of(buscar(id));
    }

    /**
     * Crea un centro de costo ({@code @CodigoCentroCostoUnico} ya validó el código).
     *
     * @param req datos validados
     * @return centro creado
     */
    @Transactional
    public CentroCostoResponse crear(CentroCostoRequest req) {
        CentroCosto c = new CentroCosto();
        c.setEmpresaId(EmpresaUnica.ID);
        c.setCodigo(normalizarCodigo(req.codigo()));
        c.setNombre(req.nombre().trim());
        CentroCosto guardado = centroCostoRepository.save(c);
        auditoriaService.exito(AccionAuditoria.CENTRO_COSTO_CREAR, EntidadAuditada.CENTRO_COSTO, guardado.getId(),
                "codigo=" + guardado.getCodigo() + ", nombre=" + guardado.getNombre());
        return CentroCostoResponse.of(guardado);
    }

    /**
     * Edita un centro de costo.
     *
     * @param id  centro a editar
     * @param req datos validados
     * @return centro actualizado
     * @throws NoEncontradoException si no existe
     * @throws NegocioException      si otro centro ya usa el código
     */
    @Transactional
    public CentroCostoResponse actualizar(Long id, CentroCostoUpdateRequest req) {
        CentroCosto c = buscar(id);
        String codigo = normalizarCodigo(req.codigo());
        if (centroCostoRepository.existsByCodigoIgnoreCaseAndIdNot(codigo, id)) {
            throw new NegocioException("CODIGO_DUPLICADO", "Ya existe un centro de costo con el código " + codigo);
        }
        String antes = c.getCodigo() + " " + c.getNombre();
        c.setCodigo(codigo);
        c.setNombre(req.nombre().trim());
        auditoriaService.exito(AccionAuditoria.CENTRO_COSTO_EDITAR, EntidadAuditada.CENTRO_COSTO, id,
                antes + " -> " + c.getCodigo() + " " + c.getNombre());
        return CentroCostoResponse.of(c);
    }

    /**
     * Activa o desactiva un centro de costo.
     *
     * @param id     centro
     * @param activo nuevo estado
     * @return centro actualizado
     * @throws NoEncontradoException si no existe
     */
    @Transactional
    public CentroCostoResponse cambiarEstado(Long id, boolean activo) {
        CentroCosto c = buscar(id);
        c.setActivo(activo);
        auditoriaService.exito(AccionAuditoria.CENTRO_COSTO_CAMBIAR_ESTADO, EntidadAuditada.CENTRO_COSTO, id,
                "codigo=" + c.getCodigo() + ", activo=" + activo);
        return CentroCostoResponse.of(c);
    }

    /**
     * Busca el centro de costo.
     *
     * @param id id
     * @return entidad
     * @throws NoEncontradoException si no existe
     */
    private CentroCosto buscar(Long id) {
        return centroCostoRepository.findById(id).orElseThrow(() -> new NoEncontradoException("Centro de costo", id));
    }

    /**
     * Normaliza el código para guardarlo y compararlo siempre igual.
     *
     * @param codigo código recibido
     * @return código sin espacios y en mayúsculas
     */
    private static String normalizarCodigo(String codigo) {
        return codigo.trim().toUpperCase(Locale.ROOT);
    }
}
