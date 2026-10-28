package com.proyecto.supply_core.plataforma.service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.plataforma.dto.PlanDtos;
import com.proyecto.supply_core.plataforma.entity.Plan;
import com.proyecto.supply_core.plataforma.enums.AccionPlataforma;
import com.proyecto.supply_core.plataforma.repository.ModuloRepository;
import com.proyecto.supply_core.plataforma.repository.PlanRepository;
import com.proyecto.supply_core.plataforma.repository.SuscripcionRepository;
import com.proyecto.supply_core.security.context.PlataformaActual;

/**
 * Planes comerciales y catálogo de módulos que administra el dueño de la plataforma. Un plan es un nombre y un
 * conjunto de módulos. Cambiar los módulos de un plan afecta a todas las empresas que lo usan, así que se limpia lo que
 * el interceptor recuerda de todas. Un plan que alguna empresa usa no se puede desactivar.
 */
@Service
public class PlanPlataformaService {

    private final PlanRepository planRepository;
    private final ModuloRepository moduloRepository;
    private final SuscripcionRepository suscripcionRepository;
    private final AccesoEmpresaService accesoEmpresa;
    private final PlataformaAuditoriaService auditoria;

    /**
     * @param planRepository        planes
     * @param moduloRepository      catálogo de módulos
     * @param suscripcionRepository para saber cuántas empresas usan cada plan
     * @param accesoEmpresa         memoria de acceso que se limpia al cambiar un plan
     * @param auditoria             auditoría global
     */
    public PlanPlataformaService(PlanRepository planRepository, ModuloRepository moduloRepository,
            SuscripcionRepository suscripcionRepository, AccesoEmpresaService accesoEmpresa,
            PlataformaAuditoriaService auditoria) {
        this.planRepository = planRepository;
        this.moduloRepository = moduloRepository;
        this.suscripcionRepository = suscripcionRepository;
        this.accesoEmpresa = accesoEmpresa;
        this.auditoria = auditoria;
    }

    /**
     * Catálogo de módulos.
     *
     * @return módulos por código
     */
    @Transactional(readOnly = true)
    public List<PlanDtos.ModuloRespuesta> modulos() {
        return moduloRepository.findAllByOrderByCodigoAsc().stream().map(PlanDtos.ModuloRespuesta::of).toList();
    }

    /**
     * Planes con sus módulos y cuántas empresas los usan.
     *
     * @return planes por código
     */
    @Transactional(readOnly = true)
    public List<PlanDtos.Respuesta> listar() {
        return planRepository.findAllByOrderByCodigoAsc().stream()
                .map(p -> PlanDtos.Respuesta.of(p, suscripcionRepository.countByPlanId(p.getId()))).toList();
    }

    /**
     * Crea un plan.
     *
     * @param req código, nombre y módulos
     * @return plan creado
     * @throws NegocioException si el código ya existe o algún módulo no existe
     */
    @Transactional
    public PlanDtos.Respuesta crear(PlanDtos.Crear req) {
        String codigo = req.codigo().trim().toUpperCase(Locale.ROOT);
        if (planRepository.existsByCodigoIgnoreCase(codigo)) {
            throw new NegocioException("CODIGO_DUPLICADO", "Ya existe un plan con el código " + codigo);
        }
        Plan p = new Plan();
        p.setCodigo(codigo);
        p.setNombre(req.nombre().trim());
        p.setModulos(modulosValidos(req.modulos()));
        Plan guardado = planRepository.save(p);
        auditoria.registrar(PlataformaActual.usuario().id(), AccionPlataforma.PLAN_CREAR, null, true,
                "plan=" + codigo + "; modulos=" + guardado.getModulos().stream().sorted().toList());
        return PlanDtos.Respuesta.of(guardado, 0);
    }

    /**
     * Cambia el nombre, los módulos o el estado de un plan.
     *
     * @param id  plan
     * @param req cambios (lo que no se envía no cambia)
     * @return plan actualizado
     * @throws NoEncontradoException si el plan no existe
     * @throws NegocioException      si algún módulo no existe o se desactiva un plan en uso
     */
    @Transactional
    public PlanDtos.Respuesta editar(Long id, PlanDtos.Editar req) {
        Plan p = planRepository.findById(id).orElseThrow(() -> new NoEncontradoException("Plan", id));
        long empresas = suscripcionRepository.countByPlanId(id);
        StringBuilder cambios = new StringBuilder("plan=" + p.getCodigo());
        if (req.nombre() != null && !req.nombre().isBlank()) {
            p.setNombre(req.nombre().trim());
            cambios.append("; nombre=").append(p.getNombre());
        }
        if (req.modulos() != null) {
            if (req.modulos().isEmpty()) {
                throw new NegocioException("PLAN_SIN_MODULOS", "Un plan debe incluir al menos un módulo");
            }
            Set<String> nuevos = modulosValidos(req.modulos());
            p.getModulos().clear();
            p.getModulos().addAll(nuevos);
            cambios.append("; modulos=").append(nuevos.stream().sorted().toList()).append("; empresas afectadas=")
                    .append(empresas);
        }
        if (req.activo() != null && req.activo() != p.isActivo()) {
            if (!req.activo() && empresas > 0) {
                throw new NegocioException("PLAN_EN_USO", "El plan lo usan " + empresas
                        + " empresas; páselas a otro plan antes de desactivarlo");
            }
            p.setActivo(req.activo());
            cambios.append("; activo=").append(req.activo());
        }
        planRepository.flush();
        accesoEmpresa.invalidarTodo();
        auditoria.registrar(PlataformaActual.usuario().id(), AccionPlataforma.PLAN_EDITAR, null, true, cambios.toString());
        return PlanDtos.Respuesta.of(p, empresas);
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Verifica que todos los módulos existan y devuelve sus códigos normalizados.
     *
     * @param codigos códigos recibidos
     * @return códigos en mayúsculas, sin repetir
     * @throws NegocioException si alguno no existe
     */
    private Set<String> modulosValidos(Set<String> codigos) {
        Set<String> normalizados = codigos.stream().map(c -> c.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(HashSet::new));
        Set<String> inexistentes = normalizados.stream().filter(c -> !moduloRepository.existsById(c))
                .collect(Collectors.toCollection(java.util.TreeSet::new));
        if (!inexistentes.isEmpty()) {
            throw new NegocioException("MODULO_INEXISTENTE", "Estos módulos no existen: " + inexistentes);
        }
        return normalizados;
    }
}
