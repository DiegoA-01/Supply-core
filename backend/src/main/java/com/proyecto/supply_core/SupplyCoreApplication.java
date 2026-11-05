package com.proyecto.supply_core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de Supply-Core: plataforma de abastecimiento, inventario y compras
 * con análisis de cotizaciones asistido por IA (monolito modular por dominio).
 */
@SpringBootApplication
public class SupplyCoreApplication {

	/**
	 * Arranca la aplicación: ejecuta las migraciones Flyway y levanta la API REST.
	 *
	 * @param args argumentos de línea de comandos (se pasan a Spring Boot)
	 */
	public static void main(String[] args) {
		SpringApplication.run(SupplyCoreApplication.class, args);
	}

}
