/**
 * Modellkatalog der Enterprise-API (KIPITZ): {@code GET <baseUrl>/models} über die Route des
 * {@link com.aresstack.enterpriseai.http.api.HttpRoutePort}s, Bearer-Token je Anfrage. Die Klassifikation stützt sich
 * nur auf {@code capabilities}, {@code architecture.input_modalities}/{@code output_modalities},
 * {@code tool_calling} und {@code reasoning}; Modellnamen bleiben Konfiguration.
 */
package com.aresstack.enterpriseai.model.kipitz;
