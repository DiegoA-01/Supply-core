package com.proyecto.supply_core.common.exception;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.proyecto.supply_core.common.dto.ErrorResponse;

import jakarta.validation.ConstraintViolationException;

/**
 * Traduce todas las excepciones a {@link ErrorResponse} con el código HTTP correcto.
 * <p>Ningún error devuelve trazas ni detalles internos al cliente; los inesperados se registran en el log.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Regla de negocio violada.
     *
     * @param ex excepción lanzada por un Service
     * @return 409 con el código propio de la regla
     */
    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<ErrorResponse> negocio(NegocioException ex) {
        return responder(HttpStatus.CONFLICT, ErrorResponse.of(ex.getCodigo(), ex.getMessage()));
    }

    /**
     * Recurso inexistente.
     *
     * @param ex excepción lanzada por un Service
     * @return 404
     */
    @ExceptionHandler(NoEncontradoException.class)
    public ResponseEntity<ErrorResponse> noEncontrado(NoEncontradoException ex) {
        return responder(HttpStatus.NOT_FOUND, ErrorResponse.of("NO_ENCONTRADO", ex.getMessage()));
    }

    /**
     * Sin sesión válida o credenciales incorrectas.
     *
     * @param ex excepción lanzada por el filtro JWT o el login
     * @return 401
     */
    @ExceptionHandler(NoAutenticadoException.class)
    public ResponseEntity<ErrorResponse> noAutenticado(NoAutenticadoException ex) {
        return responder(HttpStatus.UNAUTHORIZED, ErrorResponse.of("NO_AUTENTICADO", ex.getMessage()));
    }

    /**
     * Autenticado pero sin permiso.
     *
     * @param ex excepción lanzada por el interceptor de roles o un Service
     * @return 403
     */
    @ExceptionHandler(AccesoDenegadoException.class)
    public ResponseEntity<ErrorResponse> accesoDenegado(AccesoDenegadoException ex) {
        return responder(HttpStatus.FORBIDDEN, ErrorResponse.of("ACCESO_DENEGADO", ex.getMessage()));
    }

    /**
     * Body inválido según las anotaciones del DTO ({@code @Valid}).
     *
     * @param ex errores de campo y de clase
     * @return 400 con "campo: mensaje" por cada error
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validacion(MethodArgumentNotValidException ex) {
        List<String> detalles = new ArrayList<>();
        // isBindingFailure = no se pudo convertir el texto al tipo (fecha, número, enum): mensaje claro, sin traza
        ex.getBindingResult().getFieldErrors().forEach(e -> detalles.add(e.getField() + ": "
                + (e.isBindingFailure() ? "tiene un valor inválido" : e.getDefaultMessage())));
        // reglas sobre todo el objeto (anotaciones a nivel de clase sin campo concreto)
        ex.getBindingResult().getGlobalErrors().forEach(e -> detalles.add(e.getDefaultMessage()));
        return responder(HttpStatus.BAD_REQUEST, ErrorResponse.of("VALIDACION", "Datos inválidos", detalles));
    }

    /**
     * Validación del método del controller: ocurre cuando un endpoint tiene restricciones en sus
     * parámetros (p. ej. {@code @Positive Long id}); en ese caso también los errores del body
     * {@code @Valid} llegan por aquí, agrupados por parámetro.
     *
     * @param ex resultado de la validación
     * @return 400 con "campo: mensaje" para el body y "parámetro: mensaje" para ruta/query
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> validacionMetodo(HandlerMethodValidationException ex) {
        List<String> detalles = new ArrayList<>();
        for (ParameterValidationResult r : ex.getParameterValidationResults()) {
            if (r instanceof ParameterErrors errores) {
                // un objeto (@Valid @RequestBody): se informa cada campo del objeto, no el nombre del parámetro
                errores.getFieldErrors().forEach(e -> detalles.add(e.getField() + ": "
                        + (e.isBindingFailure() ? "tiene un valor inválido" : e.getDefaultMessage())));
                errores.getGlobalErrors().forEach(e -> detalles.add(e.getDefaultMessage()));
            } else {
                String parametro = r.getMethodParameter().getParameterName();
                r.getResolvableErrors().forEach(e -> detalles.add(parametro + ": " + e.getDefaultMessage()));
            }
        }
        return responder(HttpStatus.BAD_REQUEST, ErrorResponse.of("VALIDACION", "Datos inválidos", detalles));
    }

    /**
     * Validación programática fallida ({@code @Validated} en un bean).
     *
     * @param ex violaciones detectadas
     * @return 400 con "ruta: mensaje" por cada violación
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> validacionParametros(ConstraintViolationException ex) {
        List<String> detalles = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();
        return responder(HttpStatus.BAD_REQUEST, ErrorResponse.of("VALIDACION", "Parámetros inválidos", detalles));
    }

    /**
     * JSON mal formado, parámetro faltante o de tipo incorrecto.
     *
     * @param ex excepción de Spring MVC
     * @return 400 genérico (no se expone el detalle del parser)
     */
    @ExceptionHandler({ HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class })
    public ResponseEntity<ErrorResponse> peticionMalFormada(Exception ex) {
        return responder(HttpStatus.BAD_REQUEST,
                ErrorResponse.of("PETICION_INVALIDA", "La petición está mal formada o le faltan datos"));
    }

    /**
     * Parámetro de la petición no aceptable (p. ej. orden por un campo fuera de la lista blanca).
     *
     * @param ex excepción lanzada por un Service o utilitario
     * @return 400 con la explicación
     */
    @ExceptionHandler(ParametroInvalidoException.class)
    public ResponseEntity<ErrorResponse> parametroInvalido(ParametroInvalidoException ex) {
        return responder(HttpStatus.BAD_REQUEST, ErrorResponse.of("PETICION_INVALIDA", ex.getMessage()));
    }

    /**
     * Ordenamiento por un campo que no existe en consultas derivadas (p. ej. {@code ?sort=campoInventado}).
     *
     * @param ex excepción de Spring Data
     * @return 400 indicando el campo
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorResponse> ordenInvalido(PropertyReferenceException ex) {
        return responder(HttpStatus.BAD_REQUEST,
                ErrorResponse.of("PETICION_INVALIDA", "No se puede ordenar por '" + ex.getPropertyName() + "'"));
    }

    /**
     * Archivo o petición más grande que el límite configurado ({@code spring.servlet.multipart}).
     *
     * @param ex excepción de Spring MVC
     * @return 413
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> archivoMuyGrande(MaxUploadSizeExceededException ex) {
        return responder(HttpStatus.PAYLOAD_TOO_LARGE,
                ErrorResponse.of("ARCHIVO_DEMASIADO_GRANDE", "El archivo supera el tamaño máximo permitido (10 MB)"));
    }

    /**
     * Petición multipart sin el archivo esperado o mal formada.
     *
     * @param ex excepción de Spring MVC
     * @return 400
     */
    @ExceptionHandler({ MissingServletRequestPartException.class, MultipartException.class })
    public ResponseEntity<ErrorResponse> multipartInvalido(Exception ex) {
        return responder(HttpStatus.BAD_REQUEST,
                ErrorResponse.of("PETICION_INVALIDA", "Debe enviar el archivo en el campo 'archivo' (multipart/form-data)"));
    }

    /**
     * Content-Type no soportado (p. ej. enviar texto plano donde se espera JSON).
     *
     * @param ex excepción de Spring MVC
     * @return 415
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> tipoContenido(HttpMediaTypeNotSupportedException ex) {
        return responder(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                ErrorResponse.of("TIPO_NO_SOPORTADO", "Content-Type no soportado; use application/json"));
    }

    /**
     * Verbo HTTP no permitido en la ruta.
     *
     * @param ex excepción de Spring MVC
     * @return 405
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> metodoNoPermitido(HttpRequestMethodNotSupportedException ex) {
        return responder(HttpStatus.METHOD_NOT_ALLOWED,
                ErrorResponse.of("METODO_NO_PERMITIDO", "Método " + ex.getMethod() + " no permitido en esta ruta"));
    }

    /**
     * Ruta inexistente.
     *
     * @param ex excepción de Spring MVC
     * @return 404
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> rutaNoExiste(NoResourceFoundException ex) {
        return responder(HttpStatus.NOT_FOUND, ErrorResponse.of("NO_ENCONTRADO", "La ruta no existe"));
    }

    /**
     * Respaldo de los UNIQUE/FK de la BD cuando el Service no lo validó antes
     * (p. ej. dos altas simultáneas con el mismo correo).
     *
     * @param ex excepción de Spring Data
     * @return 409 genérico; el detalle técnico solo va al log
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> integridad(DataIntegrityViolationException ex) {
        log.warn("Violación de integridad: {}", ex.getMostSpecificCause().getMessage());
        return responder(HttpStatus.CONFLICT,
                ErrorResponse.of("INTEGRIDAD", "El registro entra en conflicto con datos existentes"));
    }

    /**
     * Cualquier error no previsto.
     *
     * @param ex excepción original
     * @return 500 genérico; la traza completa queda en el log
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> inesperado(Exception ex) {
        log.error("Error no controlado", ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorResponse.of("ERROR_INTERNO", "Ocurrió un error inesperado"));
    }

    /**
     * Arma la respuesta HTTP.
     *
     * @param status código HTTP
     * @param body   cuerpo del error
     * @return respuesta lista para devolver
     */
    private ResponseEntity<ErrorResponse> responder(HttpStatus status, ErrorResponse body) {
        return ResponseEntity.status(status).body(body);
    }
}
