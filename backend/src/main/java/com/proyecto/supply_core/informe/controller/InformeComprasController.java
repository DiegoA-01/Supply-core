package com.proyecto.supply_core.informe.controller;

import java.time.LocalDate;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.proyecto.supply_core.common.dto.PageResponse;
import com.proyecto.supply_core.informe.dto.EnviarInformeRequest;
import com.proyecto.supply_core.informe.dto.InformeContenido;
import com.proyecto.supply_core.informe.dto.InformeResponse;
import com.proyecto.supply_core.informe.dto.PedirInformeRequest;
import com.proyecto.supply_core.informe.service.InformeComprasService;
import com.proyecto.supply_core.security.annotation.RequiereModulo;
import com.proyecto.supply_core.security.annotation.RequiereRol;
import com.proyecto.supply_core.security.context.Modulos;
import com.proyecto.supply_core.security.context.Roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

/**
 * Informe de compras entre el área de compras y el dueño de la empresa. El administrador (ADMIN_SISTEMA) lo pide y
 * lo lee; el jefe de compras (ADMIN_COMPRAS) lo revisa en vivo y lo envía con su análisis.
 */
@RequiereModulo(Modulos.COMPRAS)
@RestController
@RequestMapping("/api/informes-compras")
@RequiereRol({ Roles.ADMIN_SISTEMA, Roles.ADMIN_COMPRAS })
public class InformeComprasController {

    private final InformeComprasService service;

    /**
     * @param service lógica del informe de compras
     */
    public InformeComprasController(InformeComprasService service) {
        this.service = service;
    }

    /**
     * Informes pedidos y enviados, del más reciente al más antiguo (sin el contenido).
     *
     * @param pageable página (20 por defecto)
     * @return página de informes
     */
    @GetMapping
    public PageResponse<InformeResponse> listar(@PageableDefault(size = 20) Pageable pageable) {
        return service.listar(pageable);
    }

    /**
     * Informe completo: análisis de compras y foto fija del periodo.
     *
     * @param id id positivo
     * @return informe
     */
    @GetMapping("/{id}")
    public InformeResponse obtener(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return service.obtener(id);
    }

    /**
     * Lo que saldría en el informe con los datos de hoy, sin guardarlo (para que compras lo revise antes de enviar).
     *
     * @param desde primer día del periodo
     * @param hasta último día del periodo
     * @return contenido del periodo
     */
    @GetMapping("/vista-previa")
    @RequiereRol(Roles.ADMIN_COMPRAS)
    public InformeContenido vistaPrevia(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.vistaPrevia(desde, hasta);
    }

    /**
     * El administrador de la empresa pide un informe; al área de compras le llega el aviso.
     *
     * @param request periodo y mensaje
     * @return pedido creado (HTTP 201)
     */
    @PostMapping("/pedir")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol(Roles.ADMIN_SISTEMA)
    public InformeResponse pedir(@Valid @RequestBody PedirInformeRequest request) {
        return service.pedir(request);
    }

    /**
     * El área de compras envía el informe con su análisis (respondiendo un pedido o por su cuenta).
     *
     * @param request periodo, análisis y pedido que responde
     * @return informe enviado (HTTP 201)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol(Roles.ADMIN_COMPRAS)
    public InformeResponse enviar(@Valid @RequestBody EnviarInformeRequest request) {
        return service.enviar(request);
    }

    /**
     * El administrador abrió el informe (se guarda la primera lectura).
     *
     * @param id id positivo
     * @return informe completo
     */
    @PostMapping("/{id}/leido")
    @RequiereRol(Roles.ADMIN_SISTEMA)
    public InformeResponse leido(@PathVariable @Positive(message = "debe ser un id válido") Long id) {
        return service.marcarLeido(id);
    }
}
