package com.proyecto.supply_core.usuario.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.proyecto.supply_core.empresa.repository.CentroCostoRepository;
import com.proyecto.supply_core.empresa.validation.CentroCostoActivoValidator;
import com.proyecto.supply_core.usuario.dto.UsuarioRequest;
import com.proyecto.supply_core.usuario.entity.Rol;
import com.proyecto.supply_core.usuario.repository.RolRepository;
import com.proyecto.supply_core.usuario.repository.UsuarioRepository;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/** Prueba las anotaciones del request tal como las aplica @Valid en el controller. */
class UsuarioRequestValidationTest {

    private static final Set<String> ROLES_BD = Set.of("COMPRADOR", "PROVEEDOR", "ADMIN_SISTEMA");

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final RolRepository roles = mock(RolRepository.class);
    private final CentroCostoRepository centros = mock(CentroCostoRepository.class);
    private Validator validator;

    @BeforeEach
    void setUp() {
        when(usuarios.existsByCorreoIgnoreCase("ocupado@demo.com")).thenReturn(true);
        when(roles.findByCodigoIn(anyCollection())).thenAnswer(inv -> {
            Collection<String> pedidos = inv.getArgument(0);
            return pedidos.stream().filter(ROLES_BD::contains).map(UsuarioRequestValidationTest::rol).toList();
        });

        ConstraintValidatorFactory porDefecto = Validation.buildDefaultValidatorFactory()
                .getConstraintValidatorFactory();
        validator = Validation.byDefaultProvider().configure()
                .constraintValidatorFactory(new ConstraintValidatorFactory() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
                        if (key == CorreoUnicoValidator.class) {
                            return (T) new CorreoUnicoValidator(usuarios);
                        }
                        if (key == RolesExistentesValidator.class) {
                            return (T) new RolesExistentesValidator(roles);
                        }
                        if (key == CentroCostoActivoValidator.class) {
                            return (T) new CentroCostoActivoValidator(centros);
                        }
                        return porDefecto.getInstance(key);
                    }

                    @Override
                    public void releaseInstance(ConstraintValidator<?, ?> instance) {
                    }
                })
                .buildValidatorFactory().getValidator();
    }

    @Test
    void requestCorrectoNoTieneErrores() {
        assertTrue(errores(valido("nuevo@demo.com", Set.of("comprador"), null)).isEmpty());
    }

    @Test
    void correoYaRegistradoSinImportarMayusculas() {
        assertEquals(Set.of("correo: ya está registrado por otro usuario"),
                errores(valido("OCUPADO@Demo.com", Set.of("COMPRADOR"), null)));
    }

    @Test
    void rolInexistenteSeReportaPorNombre() {
        assertEquals(Set.of("roles: contiene roles inexistentes: JEFE"),
                errores(valido("a@demo.com", Set.of("COMPRADOR", "jefe"), null)));
    }

    @Test
    void reglasDeProveedor() {
        assertEquals(Set.of("proveedorId: es obligatorio para usuarios con rol PROVEEDOR"),
                errores(valido("a@demo.com", Set.of("PROVEEDOR"), null)));
        assertEquals(Set.of("roles: un usuario PROVEEDOR no puede tener roles internos"),
                errores(valido("a@demo.com", Set.of("PROVEEDOR", "COMPRADOR"), 3L)));
        assertEquals(Set.of("proveedorId: solo aplica a usuarios con rol PROVEEDOR"),
                errores(valido("a@demo.com", Set.of("COMPRADOR"), 3L)));
    }

    @Test
    void camposObligatoriosYFormato() {
        Set<String> e = errores(new UsuarioRequest("", "no-es-correo", "corta", Set.of(), -1L, null));
        assertTrue(e.contains("nombreCompleto: es obligatorio"));
        assertTrue(e.contains("correo: no tiene un formato de correo válido"));
        assertTrue(e.contains("password: debe tener entre 8 y 72 caracteres"));
        assertTrue(e.contains("roles: debe tener al menos un rol"));
        assertTrue(e.contains("centroCostoId: debe ser un id válido"));
    }

    @Test
    void centroDeCostoDebeExistirYEstarActivo() {
        when(centros.existsByIdAndActivoTrue(7L)).thenReturn(true);
        assertTrue(errores(new UsuarioRequest("Ana", "a@demo.com", "Secreta123", Set.of("COMPRADOR"), 7L, null))
                .isEmpty());
        assertEquals(Set.of("centroCostoId: no existe o está inactivo"),
                errores(new UsuarioRequest("Ana", "a@demo.com", "Secreta123", Set.of("COMPRADOR"), 8L, null)));
    }

    @Test
    void passwordExigeLetraYNumero() {
        assertEquals(Set.of("password: debe tener al menos una letra y un número"),
                errores(new UsuarioRequest("Ana", "a@demo.com", "soloLetras", Set.of("COMPRADOR"), null, null)));
    }

    private static UsuarioRequest valido(String correo, Set<String> roles, Long proveedorId) {
        return new UsuarioRequest("Ana", correo, "Secreta123", roles, null, proveedorId);
    }

    private Set<String> errores(UsuarioRequest req) {
        return validator.validate(req).stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.toSet());
    }

    private static Rol rol(String codigo) {
        Rol r = new Rol();
        r.setCodigo(codigo);
        return r;
    }
}
