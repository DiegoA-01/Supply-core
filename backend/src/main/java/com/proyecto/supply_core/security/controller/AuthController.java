package com.proyecto.supply_core.security.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.common.exception.NoAutenticadoException;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;
import com.proyecto.supply_core.empresa.repository.EmpresaRepository;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.security.dto.ActivarMfaRequest;
import com.proyecto.supply_core.security.dto.ConfiguracionMfaResponse;
import com.proyecto.supply_core.security.dto.LoginRequest;
import com.proyecto.supply_core.security.dto.LoginResponse;
import com.proyecto.supply_core.security.dto.MensajeResponse;
import com.proyecto.supply_core.security.dto.MfaCodigoRequest;
import com.proyecto.supply_core.security.dto.RecuperarPasswordRequest;
import com.proyecto.supply_core.security.dto.RefreshRequest;
import com.proyecto.supply_core.security.dto.ReiniciarPasswordRequest;
import com.proyecto.supply_core.security.dto.UsuarioSesionResponse;
import com.proyecto.supply_core.security.jwt.DesafioMfaEmpresa;
import com.proyecto.supply_core.security.service.AuthService;
import com.proyecto.supply_core.security.service.MfaUsuarioService;
import com.proyecto.supply_core.security.service.RecuperacionPasswordService;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;

/** Endpoints de autenticación. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final EmpresaRepository empresaRepository;
    private final RecuperacionPasswordService recuperacionService;
    private final MfaUsuarioService mfaService;

    /**
     * @param authService         lógica de login
     * @param empresaRepository   resuelve la empresa por su código público
     * @param recuperacionService recuperación de la contraseña olvidada
     * @param mfaService          segundo factor de los usuarios de empresa
     */
    public AuthController(AuthService authService, EmpresaRepository empresaRepository,
            RecuperacionPasswordService recuperacionService, MfaUsuarioService mfaService) {
        this.mfaService = mfaService;
        this.authService = authService;
        this.empresaRepository = empresaRepository;
        this.recuperacionService = recuperacionService;
    }

    /**
     * Inicia sesión (ruta pública). Primero se resuelve la empresa por su código (esa consulta corre sin
     * empresa) y el login completo corre dentro de ella: así solo se busca al usuario entre los de esa empresa.
     * Un código inexistente responde igual que una clave mala.
     *
     * @param request código de empresa, correo y contraseña
     * @return token JWT y datos de sesión
     */
    @PostMapping("/login")
    @SecurityRequirements // Swagger no pide token aquí
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Long empresaId = empresaRepository.idPorCodigoPublico(request.empresa().trim()).orElse(null);
        if (empresaId == null) {
            return authService.loginEmpresaDesconocida(request);
        }
        return EmpresaContexto.como(empresaId, () -> authService.login(request));
    }

    /**
     * Pide el correo de recuperación de una contraseña olvidada (ruta pública). Responde siempre lo mismo, exista o no
     * la empresa o el correo, para no revelar quién tiene cuenta.
     *
     * @param request código de empresa y correo
     * @return aviso genérico
     */
    @PostMapping("/recuperar")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @SecurityRequirements
    public MensajeResponse recuperar(@Valid @RequestBody RecuperarPasswordRequest request) {
        Long empresaId = empresaRepository.idPorCodigoPublico(request.empresa().trim()).orElse(null);
        if (empresaId != null) {
            EmpresaContexto.como(empresaId, () -> {
                recuperacionService.solicitar(request.correo());
                return null;
            });
        }
        return new MensajeResponse("Si la cuenta existe, te enviamos un correo con las instrucciones");
    }

    /**
     * Cambia la contraseña con el token recibido por correo (ruta pública). El token sirve una sola vez y corta todas
     * las sesiones abiertas del usuario.
     *
     * @param request código de empresa, token y contraseña nueva
     * @return aviso de que ya puede iniciar sesión
     */
    @PostMapping("/restablecer")
    @SecurityRequirements
    public MensajeResponse restablecer(@Valid @RequestBody ReiniciarPasswordRequest request) {
        Long empresaId = empresaRepository.idPorCodigoPublico(request.empresa().trim())
                .orElseThrow(() -> new NoAutenticadoException(RecuperacionPasswordService.TOKEN_INVALIDO));
        EmpresaContexto.como(empresaId, () -> {
            recuperacionService.restablecer(request.token(), request.passwordNueva());
            return null;
        });
        return new MensajeResponse("Contraseña actualizada: ya puedes iniciar sesión");
    }

    /**
     * Renueva la sesión (ruta pública: el refresh token es la credencial). Cada token sirve una sola vez.
     *
     * @param request refresh token vigente
     * @return JWT nuevo y refresh token nuevo
     */
    @PostMapping("/refresh")
    @SecurityRequirements
    public LoginResponse refrescar(@Valid @RequestBody RefreshRequest request) {
        return authService.refrescar(request.refreshToken());
    }

    /**
     * Segundo paso del login de quien usa MFA (ruta pública: el desafío es la credencial). Verifica el código dentro de
     * la empresa del desafío y abre la sesión.
     *
     * @param request desafío del primer paso y código de 6 dígitos
     * @return token JWT y datos de sesión
     */
    @PostMapping("/mfa")
    @SecurityRequirements
    public LoginResponse verificarMfa(@Valid @RequestBody MfaCodigoRequest request) {
        DesafioMfaEmpresa desafio = authService.leerDesafio(request.desafio());
        return EmpresaContexto.como(desafio.empresaId(),
                () -> authService.verificarMfa(desafio.usuarioId(), request.codigo()));
    }

    /**
     * El usuario de la sesión pide el secreto para vincular su app de autenticación (MFA voluntario).
     *
     * @return secreto y dirección del código QR
     */
    @PostMapping("/mfa/configurar")
    public ConfiguracionMfaResponse configurarMfa() {
        return mfaService.configurar();
    }

    /**
     * El usuario de la sesión confirma con un código que su app quedó vinculada: desde ahí el login lo exige.
     *
     * @param request código de 6 dígitos
     */
    @PostMapping("/mfa/activar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activarMfa(@Valid @RequestBody ActivarMfaRequest request) {
        mfaService.activar(request.codigo());
    }

    /**
     * Cierra la sesión del dispositivo (revoca su refresh token).
     *
     * @param request refresh token de esa sesión
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cerrarSesion(@Valid @RequestBody RefreshRequest request) {
        authService.cerrarSesion(request.refreshToken(), SesionActual.usuario().id());
    }

    /** Cierra todas las sesiones del usuario en todos sus dispositivos. */
    @PostMapping("/logout-todo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cerrarTodas() {
        authService.cerrarTodas(SesionActual.usuario().id());
    }

    /**
     * Devuelve el dueño del token (Angular lo usa para recuperar la sesión al recargar).
     *
     * @return datos de sesión
     */
    @GetMapping("/me")
    public UsuarioSesionResponse me() {
        return UsuarioSesionResponse.of(SesionActual.usuario());
    }
}
