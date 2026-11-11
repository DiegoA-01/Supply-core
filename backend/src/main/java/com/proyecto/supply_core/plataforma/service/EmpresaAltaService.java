package com.proyecto.supply_core.plataforma.service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.compra.analisis.service.PesosService;
import com.proyecto.supply_core.empresa.entity.Empresa;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.plataforma.dto.EmpresaCrearRequest;
import com.proyecto.supply_core.plataforma.entity.Plan;
import com.proyecto.supply_core.plataforma.entity.Suscripcion;
import com.proyecto.supply_core.plataforma.enums.EstadoSuscripcion;
import com.proyecto.supply_core.plataforma.repository.SuscripcionRepository;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.usuario.entity.Usuario;
import com.proyecto.supply_core.usuario.repository.RolRepository;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

/**
 * Los pasos transaccionales del alta de una empresa. Están en un bean aparte porque cada paso necesita su propia
 * transacción, y la de sembrar al administrador debe abrirse ya dentro de la empresa nueva (el contexto de empresa se
 * fija antes de abrir la sesión, no durante).
 */
@Service
public class EmpresaAltaService {

    /** Matriz de pesos con la que arranca cada empresa (suma 100), la misma de la empresa demo. */
    static final Map<String, BigDecimal> PESOS_INICIALES = Map.of("PRECIO", new BigDecimal("35"),
            "CALIDAD", new BigDecimal("20"), "ENTREGA", new BigDecimal("15"), "GARANTIA", new BigDecimal("10"),
            "HISTORIAL", new BigDecimal("15"), "RIESGO", new BigDecimal("5"));

    private final EmpresaRepository empresaRepository;
    private final SuscripcionRepository suscripcionRepository;
    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final PesosService pesosService;

    /**
     * @param empresaRepository     empresas
     * @param suscripcionRepository suscripciones
     * @param usuarioRepository     administrador inicial
     * @param rolRepository         rol ADMIN_SISTEMA
     * @param passwordEncoder       hash BCrypt de la contraseña inicial
     * @param pesosService          matriz de pesos inicial
     */
    public EmpresaAltaService(EmpresaRepository empresaRepository, SuscripcionRepository suscripcionRepository,
            UsuarioRepository usuarioRepository, RolRepository rolRepository, PasswordEncoder passwordEncoder,
            PesosService pesosService) {
        this.empresaRepository = empresaRepository;
        this.suscripcionRepository = suscripcionRepository;
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
        this.pesosService = pesosService;
    }

    /**
     * Paso 1: crea la empresa y su suscripción. Se ejecuta sin empresa en el contexto.
     *
     * @param req    datos del alta
     * @param plan   plan asignado
     * @param estado estado inicial de la suscripción
     * @return empresa creada, con su id
     */
    /**
     * Deja el código tal como lo escribió la persona, sin espacios de más: se quitan los de los extremos y varios
     * seguidos pasan a uno solo, para que {@code "Don  Martín"} y {@code "Don Martín"} no sean dos empresas.
     * No se cambian mayúsculas ni acentos: así se muestra y así se escribe al ingresar (la búsqueda no las distingue).
     *
     * @param codigo código escrito en el alta
     * @return código normalizado
     */
    public static String normalizarCodigo(String codigo) {
        return codigo.trim().replaceAll("\\s+", " ");
    }

    @Transactional
    public Empresa crearEmpresa(EmpresaCrearRequest req, Plan plan, EstadoSuscripcion estado) {
        Empresa e = new Empresa();
        e.setRazonSocial(req.razonSocial().trim());
        e.setNit(req.nit().trim());
        e.setCodigoPublico(normalizarCodigo(req.codigoPublico()));
        e.setMonedaBase("COP");
        Empresa guardada = empresaRepository.saveAndFlush(e);
        Suscripcion s = new Suscripcion();
        s.setEmpresaId(guardada.getId());
        s.setPlan(plan);
        s.setEstado(estado);
        suscripcionRepository.save(s);
        return guardada;
    }

    /**
     * Paso 2: crea el administrador de la empresa y su matriz de pesos inicial. Debe llamarse con el contexto de la
     * empresa nueva ya fijado ({@code EmpresaContexto.como}), para que la transacción se abra dentro de ella.
     *
     * @param nombre   nombre del administrador
     * @param correo   correo del administrador
     * @param password contraseña inicial
     */
    @Transactional
    public void sembrarAdministrador(String nombre, String correo, String password) {
        Usuario admin = new Usuario();
        admin.setNombreCompleto(nombre.trim());
        admin.setCorreo(correo.trim().toLowerCase(Locale.ROOT));
        admin.setHashPassword(passwordEncoder.encode(password));
        admin.getRoles().add(rolRepository.findByCodigo(Roles.ADMIN_SISTEMA)
                .orElseThrow(() -> new IllegalStateException("Falta el rol ADMIN_SISTEMA (V2)")));
        usuarioRepository.save(admin);
        pesosService.cambiar(PESOS_INICIALES);
    }

    /**
     * Deshace el paso 1 cuando el paso 2 falló, para no dejar una empresa sin administrador ocupando su NIT y su
     * código. Solo borra la empresa y su suscripción: el paso 2 es todo o nada.
     *
     * @param empresaId empresa recién creada
     */
    @Transactional
    public void deshacer(Long empresaId) {
        suscripcionRepository.findByEmpresaId(empresaId).ifPresent(suscripcionRepository::delete);
        empresaRepository.deleteById(empresaId);
    }
}
