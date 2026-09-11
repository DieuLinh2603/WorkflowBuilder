package com.company.workflowbuilder.service;

import com.company.workflowbuilder.entity.data.DataConnector;
import com.company.workflowbuilder.entity.runtime.SystemActionExecution;
import com.company.workflowbuilder.repository.DataConnectorRepository;
import com.company.workflowbuilder.service.data.DataConnectorService;
import com.company.workflowbuilder.service.runtime.RestConnectorHttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RestConnectorHttpClientTest {
    @Test void sendsConnectorAuthenticationAndJsonBody() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>(), body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/orders", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"data\":{\"id\":\"123\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            UUID connectorId = UUID.randomUUID();
            DataConnector connector = DataConnector.builder().id(connectorId).active(true).connectorType("REST")
                    .configJson("{\"baseUrl\":\"http://127.0.0.1:" + server.getAddress().getPort() + "\"}").build();
            DataConnectorRepository connectors = mock(DataConnectorRepository.class);
            DataConnectorService connectorService = mock(DataConnectorService.class);
            when(connectors.findById(connectorId)).thenReturn(Optional.of(connector));
            when(connectorService.credentials(connector)).thenReturn(Map.of("authType", "BEARER", "token", "secret"));
            RestConnectorHttpClient client = new RestConnectorHttpClient(connectors, connectorService, new ObjectMapper());
            SystemActionExecution execution = SystemActionExecution.builder().connector(connector).httpMethod("POST")
                    .requestUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/orders")
                    .requestHeadersJson("{\"Idempotency-Key\":\"execution-1\"}").requestBody("{\"amount\":10}").build();

            var result = client.execute(execution, 5);

            assertThat(result.error()).isNull(); assertThat(result.statusCode()).isEqualTo(201);
            assertThat(result.body()).contains("\"id\":\"123\"");
            assertThat(authorization.get()).isEqualTo("Bearer secret"); assertThat(body.get()).isEqualTo("{\"amount\":10}");
        } finally { server.stop(0); }
    }

    @Test void getHasNoBodyAndServerErrorIsRetryableWithApiKey() throws Exception {
        AtomicReference<String> apiKey = new AtomicReference<>(), body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/status", exchange -> {
            apiKey.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(503, -1); exchange.close();
        });
        server.start();
        try {
            UUID connectorId = UUID.randomUUID();
            DataConnector connector = DataConnector.builder().id(connectorId).active(true).connectorType("REST")
                    .configJson("{\"baseUrl\":\"http://127.0.0.1:" + server.getAddress().getPort() + "\"}").build();
            DataConnectorRepository connectors = mock(DataConnectorRepository.class);
            DataConnectorService connectorService = mock(DataConnectorService.class);
            when(connectors.findById(connectorId)).thenReturn(Optional.of(connector));
            when(connectorService.credentials(connector)).thenReturn(Map.of("authType", "API_KEY", "headerName", "X-API-Key", "apiKey", "key-1"));
            SystemActionExecution execution = SystemActionExecution.builder().connector(connector).httpMethod("GET")
                    .requestUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/status")
                    .requestHeadersJson("{}").requestBody(null).build();

            var result = new RestConnectorHttpClient(connectors, connectorService, new ObjectMapper()).execute(execution, 5);

            assertThat(result.statusCode()).isEqualTo(503); assertThat(result.retryable()).isTrue();
            assertThat(apiKey.get()).isEqualTo("key-1"); assertThat(body.get()).isEmpty();
        } finally { server.stop(0); }
    }
}
