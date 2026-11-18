package com.proyecto.supply_core.documento.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Activa la configuración tipada {@link StorageProperties}. */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {
}
