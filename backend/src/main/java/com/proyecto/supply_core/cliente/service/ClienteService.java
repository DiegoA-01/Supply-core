package com.proyecto.supply_core.cliente.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.service.AuditoriaService;
import com.proyecto.supply_core.cliente.dto.ClienteRequest;
import com.proyecto.supply_core.cliente.dto.ClienteResponse;
import com.proyecto.supply_core.cliente.entity.Cliente;
import com.proyecto.supply_core.cliente.enums.TipoDocumento;
import com.proyecto.supply_core.cliente.repository.ClienteRepository;
import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.common.exception.AccesoDenegadoException;
import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.exception.NoEncontradoException;
import com.proyecto.supply_core.security.context.Roles;
import com.proyecto.supply_core.security.context.SesionActual;
import com.proyecto.supply_core.venta.entity.ListaPrecio;
import com.proyecto.supply_core.venta.repository.ListaPrecioRepository;

/**
 * Clientes de la empresa (spec 002).
 * <ul>
 *   <li>Cada empresa tiene un consumidor final para las ventas sin datos: no se edita ni se desactiva.</li>
 *   <li>El documento se valida y normaliza según su tipo ({@link DocumentoCliente}) y es único en la empresa.</li>
 *   <li>Teléfono, correo y dirección solo se guardan con la autorización de tratamiento de datos (Ley 1581 de 2012).</li>
 *   <li>El cupo de crédito, el plazo y la lista de precios solo los cambian el dueño o el supervisor de ventas.</li>
 *   <li>Nada se borra: los clientes se desactivan. La auditoría no guarda datos personales, solo qué cambió.</li>
 * </ul>
 */
@Service
public class ClienteService {

    /** Documento con el que la DIAN identifica al consumidor final. */
    static final String DOCUMENTO_CONSUMIDOR_FINAL = "222222222222";

    private final ClienteRepository clienteRepository;
    private final ListaPrecioRepository listaRepository;
    private final AuditoriaService auditoriaService;

    /**
     * @param clienteRepository clientes
     * @param listaRepository   listas de precios (la del cliente debe ser de la empresa)
     * @param auditoriaService  registro de cambios
     */
    public ClienteService(ClienteRepository clienteRepository, ListaPrecioRepository listaRepository,
            AuditoriaService auditoriaService) {
        this.clienteRepository = clienteRepository;
        this.listaRepository = listaRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Busca por número de documento (empieza por) o nombre (contiene). El consumidor final sale primero.
     *
     * @param q        texto buscado ({@code null} o vacío = todos)
     * @param activo   filtro por estado ({@code null} = todos)
     * @param pageable página
     * @return página de clientes
     */
    @Transactional
    public PageResponse<ClienteResponse> buscar(String q, Boolean activo, Pageable pageable) {
        asegurarConsumidorFinal();
        String texto = q == null ? "" : q.trim();
        String numero = escaparLike(texto.replace(".", "").replace(" ", "").toUpperCase(Locale.ROOT)) + "%";
        String nombre = "%" + escaparLike(texto.toLowerCase(Locale.ROOT)) + "%";
        return PageResponse.of(clienteRepository.buscar(numero, nombre, activo, pageable), ClienteResponse::of);
    }

    /**
     * @param id cliente
     * @return el cliente
     * @throws NoEncontradoException si no es de la empresa
     */
    @Transactional(readOnly = true)
    public ClienteResponse obtener(Long id) {
        return ClienteResponse.of(cliente(id));
    }

    /**
     * @return el consumidor final de la empresa (se crea si aún no existe)
     */
    @Transactional
    public ClienteResponse consumidorFinal() {
        return ClienteResponse.of(asegurarConsumidorFinal());
    }

    /**
     * @param activo filtro por estado ({@code null} = todos)
     * @return todos los clientes (para la exportación)
     */
    @Transactional
    public List<ClienteResponse> todos(Boolean activo) {
        asegurarConsumidorFinal();
        return clienteRepository.todos(activo).stream().map(ClienteResponse::of).toList();
    }

    /**
     * @param req datos del cliente
     * @return cliente creado
     * @throws NegocioException        si el documento no es válido o ya existe, falta la autorización de datos, o la
     *                                 lista de precios está inactiva
     * @throws NoEncontradoException   si la lista de precios no es de la empresa
     * @throws AccesoDenegadoException si quien crea no puede fijar cupo, plazo o lista
     */
    @Transactional
    public ClienteResponse crear(ClienteRequest req) {
        String numero = DocumentoCliente.normalizar(req.tipoDocumento(), req.numeroDocumento());
        if (clienteRepository.findByTipoDocumentoAndNumeroDocumento(req.tipoDocumento(), numero).isPresent()) {
            throw duplicado(req.tipoDocumento(), numero);
        }
        Cliente c = new Cliente();
        c.setTipoDocumento(req.tipoDocumento());
        c.setNumeroDocumento(numero);
        c.setNombre(req.nombre().trim());
        aplicarContacto(c, req);
        boolean condiciones = req.listaPrecioId() != null || positivo(req.cupoCredito())
                || (req.plazoDias() != null && req.plazoDias() > 0);
        if (condiciones) {
            exigirCondiciones();
            c.setListaPrecio(req.listaPrecioId() == null ? null : lista(req.listaPrecioId()));
            c.setCupoCredito(req.cupoCredito() == null ? BigDecimal.ZERO : dinero(req.cupoCredito()));
            c.setPlazoDias(req.plazoDias() == null ? 0 : req.plazoDias());
        }
        if (Boolean.FALSE.equals(req.activo())) {
            c.setActivo(false);
        }
        Cliente guardado = clienteRepository.saveAndFlush(c);
        auditoriaService.exito(AccionAuditoria.CLIENTE_CREAR, EntidadAuditada.CLIENTE, guardado.getId(),
                "tipo=" + guardado.getTipoDocumento() + "; autorizaDatos=" + (guardado.getAceptaDatosEn() != null));
        if (condiciones) {
            auditoriaService.exito(AccionAuditoria.CLIENTE_CUPO_CAMBIAR, EntidadAuditada.CLIENTE, guardado.getId(),
                    condiciones(guardado));
        }
        return ClienteResponse.of(guardado);
    }

    /**
     * Edita un cliente. Cupo, plazo y lista vacíos se conservan; los datos de contacto se reemplazan.
     *
     * @param id  cliente
     * @param req datos nuevos
     * @return cliente actualizado
     * @throws NoEncontradoException   si no es de la empresa o la lista no existe
     * @throws NegocioException        si es el consumidor final, el documento no es válido o lo usa otro cliente, o
     *                                 falta la autorización de datos
     * @throws AccesoDenegadoException si cambia cupo, plazo o lista sin ser dueño ni supervisor de ventas
     */
    @Transactional
    public ClienteResponse editar(Long id, ClienteRequest req) {
        Cliente c = cliente(id);
        if (c.isEsConsumidorFinal()) {
            throw new NegocioException("CONSUMIDOR_FINAL", "El consumidor final no se edita ni se desactiva");
        }
        String numero = DocumentoCliente.normalizar(req.tipoDocumento(), req.numeroDocumento());
        clienteRepository.findByTipoDocumentoAndNumeroDocumento(req.tipoDocumento(), numero)
                .filter(otro -> !otro.getId().equals(id)).ifPresent(otro -> {
                    throw duplicado(req.tipoDocumento(), numero);
                });
        List<String> cambios = new ArrayList<>();
        if (c.getTipoDocumento() != req.tipoDocumento() || !c.getNumeroDocumento().equals(numero)) {
            cambios.add("documento");
        }
        c.setTipoDocumento(req.tipoDocumento());
        c.setNumeroDocumento(numero);
        if (!c.getNombre().equals(req.nombre().trim())) {
            cambios.add("nombre");
        }
        c.setNombre(req.nombre().trim());
        String contactoAntes = Objects.toString(c.getTelefono()) + Objects.toString(c.getCorreo())
                + Objects.toString(c.getDireccion()) + Objects.toString(c.getAceptaDatosEn());
        aplicarContacto(c, req);
        if (!contactoAntes.equals(Objects.toString(c.getTelefono()) + Objects.toString(c.getCorreo())
                + Objects.toString(c.getDireccion()) + Objects.toString(c.getAceptaDatosEn()))) {
            cambios.add("contacto");
        }
        String condicionesAntes = condiciones(c);
        boolean cambiaLista = req.listaPrecioId() != null
                && (c.getListaPrecio() == null || !c.getListaPrecio().getId().equals(req.listaPrecioId()));
        boolean cambiaCupo = req.cupoCredito() != null && dinero(req.cupoCredito()).compareTo(c.getCupoCredito()) != 0;
        boolean cambiaPlazo = req.plazoDias() != null && req.plazoDias() != c.getPlazoDias();
        if (cambiaLista || cambiaCupo || cambiaPlazo) {
            exigirCondiciones();
            if (cambiaLista) {
                c.setListaPrecio(lista(req.listaPrecioId()));
            }
            if (cambiaCupo) {
                c.setCupoCredito(dinero(req.cupoCredito()));
            }
            if (cambiaPlazo) {
                c.setPlazoDias(req.plazoDias());
            }
        }
        if (req.activo() != null && req.activo() != c.isActivo()) {
            c.setActivo(req.activo());
            cambios.add(req.activo() ? "activado" : "desactivado");
        }
        clienteRepository.saveAndFlush(c);
        if (!cambios.isEmpty()) {
            auditoriaService.exito(AccionAuditoria.CLIENTE_EDITAR, EntidadAuditada.CLIENTE, id,
                    "cambió: " + String.join(", ", cambios));
        }
        if (cambiaLista || cambiaCupo || cambiaPlazo) {
            auditoriaService.exito(AccionAuditoria.CLIENTE_CUPO_CAMBIAR, EntidadAuditada.CLIENTE, id,
                    "antes: " + condicionesAntes + " | después: " + condiciones(c));
        }
        return ClienteResponse.of(c);
    }

    // ----------------------------------------------------------------- apoyo

    /**
     * Crea el consumidor final de la empresa si aún no lo tiene (empresas creadas después de V32).
     *
     * @return el consumidor final
     */
    Cliente asegurarConsumidorFinal() {
        return clienteRepository.findFirstByEsConsumidorFinalTrue().orElseGet(() -> {
            Cliente c = new Cliente();
            c.setTipoDocumento(TipoDocumento.CC);
            c.setNumeroDocumento(DOCUMENTO_CONSUMIDOR_FINAL);
            c.setNombre("Consumidor final");
            c.setEsConsumidorFinal(true);
            return clienteRepository.saveAndFlush(c);
        });
    }

    /**
     * Aplica teléfono, correo, dirección y la autorización de datos.
     *
     * @param c   cliente
     * @param req datos recibidos
     * @throws NegocioException {@code AUTORIZACION_DATOS_REQUERIDA} si hay contacto sin autorización
     */
    private static void aplicarContacto(Cliente c, ClienteRequest req) {
        if (Boolean.TRUE.equals(req.aceptaDatos()) && c.getAceptaDatosEn() == null) {
            c.setAceptaDatosEn(LocalDateTime.now());
        } else if (Boolean.FALSE.equals(req.aceptaDatos())) {
            c.setAceptaDatosEn(null);
        }
        String telefono = vacioANull(req.telefono());
        String correo = vacioANull(req.correo());
        String direccion = vacioANull(req.direccion());
        if ((telefono != null || correo != null || direccion != null) && c.getAceptaDatosEn() == null) {
            throw new NegocioException("AUTORIZACION_DATOS_REQUERIDA",
                    "Para guardar teléfono, correo o dirección el cliente debe autorizar el tratamiento de sus datos");
        }
        c.setTelefono(telefono);
        c.setCorreo(correo == null ? null : correo.toLowerCase(Locale.ROOT));
        c.setDireccion(direccion);
    }

    /**
     * @throws AccesoDenegadoException si la sesión no es dueño ni supervisor de ventas
     */
    private static void exigirCondiciones() {
        var roles = SesionActual.usuario().roles();
        if (!roles.contains(Roles.ADMIN_SISTEMA) && !roles.contains(Roles.SUPERVISOR_VENTAS)) {
            throw new AccesoDenegadoException(
                    "El cupo, el plazo y la lista de precios de un cliente solo los cambian el dueño o el supervisor de ventas");
        }
    }

    /**
     * @param id lista
     * @return lista activa de la empresa
     */
    private ListaPrecio lista(Long id) {
        ListaPrecio l = listaRepository.findById(id)
                .orElseThrow(() -> new NoEncontradoException("Lista de precios no encontrada"));
        if (!l.isActivo()) {
            throw new NegocioException("LISTA_INACTIVA", "La lista " + l.getNombre() + " está inactiva");
        }
        return l;
    }

    /**
     * @param id cliente
     * @return cliente de la empresa
     */
    private Cliente cliente(Long id) {
        return clienteRepository.findById(id).orElseThrow(() -> new NoEncontradoException("Cliente no encontrado"));
    }

    /**
     * @param c cliente
     * @return resumen de cupo, plazo y lista para la auditoría
     */
    private static String condiciones(Cliente c) {
        return "cupo=" + c.getCupoCredito() + ", plazo=" + c.getPlazoDias() + ", lista="
                + (c.getListaPrecio() == null ? "General" : c.getListaPrecio().getNombre());
    }

    /**
     * @param tipo   tipo de documento
     * @param numero número normalizado
     * @return error de documento repetido
     */
    private static NegocioException duplicado(TipoDocumento tipo, String numero) {
        return new NegocioException("CLIENTE_DUPLICADO", "Ya existe un cliente con " + tipo + " " + numero);
    }

    /**
     * @param v monto
     * @return {@code true} si es mayor que cero
     */
    private static boolean positivo(BigDecimal v) {
        return v != null && v.signum() > 0;
    }

    /**
     * @param v monto
     * @return monto con 2 decimales
     */
    private static BigDecimal dinero(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * @param s texto
     * @return texto recortado o {@code null} si queda vacío
     */
    private static String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /**
     * Escapa los comodines de {@code like} con {@code !} (la consulta declara {@code escape '!'}).
     *
     * @param texto texto
     * @return texto con {@code !}, {@code %} y {@code _} escapados
     */
    static String escaparLike(String texto) {
        return texto.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
