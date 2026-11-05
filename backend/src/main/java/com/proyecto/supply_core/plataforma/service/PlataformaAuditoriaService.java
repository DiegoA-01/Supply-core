package com.proyecto.supply_core.plataforma.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.plataforma.dto.AuditoriaPlataformaResponse;
import com.proyecto.supply_core.plataforma.entity.AuditoriaPlataforma;
import com.proyecto.supply_core.plataforma.enums.AccionPlataforma;
import com.proyecto.supply_core.plataforma.repository.AuditoriaPlataformaRepository;

/**
 * Auditoría global de la plataforma. Cada registro se guarda en su propia transacción: queda aunque la operación
 * que lo originó falle o se revierta (un intento fallido de login es justo lo que importa dejar).
 */
@Service
public class PlataformaAuditoriaService {

    private final AuditoriaPlataformaRepository repository;

    /**
     * @param repository acceso a {@code auditoria_plataforma}
     */
    public PlataformaAuditoriaService(AuditoriaPlataformaRepository repository) {
        this.repository = repository;
    }

    /**
     * Registra una acción de la plataforma.
     *
     * @param usuarioPlataformaId quién actuó ({@code null} si no se identificó)
     * @param accion              qué hizo
     * @param empresaId           empresa afectada ({@code null} si no aplica)
     * @param exito               {@code true} si salió bien
     * @param detalle             datos relevantes (nunca contraseñas ni secretos)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(Long usuarioPlataformaId, AccionPlataforma accion, Long empresaId, boolean exito,
            String detalle) {
        repository.save(new AuditoriaPlataforma(usuarioPlataformaId, accion, empresaId, exito, ipActual(), detalle));
    }

    /**
     * Consulta la auditoría, la más reciente primero.
     *
     * @param empresaId empresa afectada ({@code null} = todas)
     * @param pageable  página
     * @return página de registros
     */
    @Transactional(readOnly = true)
    public PageResponse<AuditoriaPlataformaResponse> buscar(Long empresaId, Pageable pageable) {
        Page<AuditoriaPlataforma> pagina = repository.buscar(empresaId, pageable);
        return PageResponse.of(pagina, AuditoriaPlataformaResponse::of);
    }

    /**
     * Dirección de la petición actual, si la hay.
     *
     * @return IP o {@code null} fuera de una petición web
     */
    private static String ipActual() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getRemoteAddr();
        }
        return null;
    }
}
