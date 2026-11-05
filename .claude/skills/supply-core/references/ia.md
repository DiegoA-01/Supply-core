# IA y motor de comparación

Principio: la IA lee, normaliza descripciones y explica. Java calcula precios, subtotales, impuestos y score. La persona decide.

## Flujo técnico
1. Proveedor sube archivo → `documento` (archivo en storage, nunca en BD) + `cotizacion.origen=ARCHIVO` + `procesamiento_ia` PENDIENTE.
2. Job asíncrono (`@Async` al inicio) → `IAClient.extraer(documento, esquemaJson)` con structured outputs / function calling.
3. Validar respuesta contra el esquema; guardar `respuesta_json`; crear `cotizacion_linea` + `extraccion_campo` (confianza 0–1 por dato).
4. Campo crítico (cantidad, precio, unidad) con confianza < umbral (configurable, p. ej. 0.80) ⇒ `REQUIERE_REVISION`; el comprador confirma (`valor_confirmado`) antes de publicar.
5. Java: `precio_unitario_normalizado = precio / factor_conversion`, aplica descuento e impuesto, `costo_total`; puntajes 0–100 por criterio; `score_ponderado = puntaje × peso / 100`; `score_final = Σ`.
6. IA genera `explicacion_ia` (ventajas, desventajas) y `anomalia_cotizacion` (p. ej. PRECIO_ATIPICO vs histórico). Anomalía = alerta, nunca exclusión.
7. Análisis PUBLICADO con ranking por `score_final`; recomendada = posición 1.

## Criterios y pesos
Criterios: PRECIO, CALIDAD, ENTREGA, GARANTIA, HISTORIAL, RIESGO. Usar siempre la `config_pesos_evaluacion` vigente y copiar `peso_aplicado` (snapshot). Cambiar pesos = nueva config, nunca editar la vigente.

## Reglas
- La clave de la API va en variable de entorno; el dominio solo conoce `IAClient` (proveedor de modelo intercambiable).
- Reintentos acotados (`intentos`); error ⇒ estado ERROR con mensaje, sin bloquear la UI.
- Argumento para la sustentación: un error de la IA nunca termina en compra sin revisión humana.

## Implementación (paso 13, 2026-10-06)
- Proveedor por configuración: `app.ia.proveedor` = `ninguno` (defecto) | `ollama` | `claude`. Claude sin `ANTHROPIC_API_KEY` ⇒ sin IA (la app arranca igual).
- Claude: SDK `com.anthropic:anthropic-java` 2.34.0, modelo `claude-opus-5-5`, structured outputs con el record `ExtraccionIA`. La key es de la API (console.anthropic.com), no de la suscripción.
- Ollama: `POST /api/chat` con `format` = esquema JSON (`OllamaIAClient.esquemaExtraccion`). Modelo sugerido `qwen2.5vl:3b` (CPU, minutos por documento).
- La IA copia valores como texto; `NumeroTexto` los interpreta. Nunca se inventa un número: renglón ilegible ⇒ campo `renglonNoLeido` crítico.
- Explicación por cotización: plantilla Java. La IA solo redacta `explicacion_general` a pedido (`POST /api/rfqs/{id}/analisis/explicar`).
