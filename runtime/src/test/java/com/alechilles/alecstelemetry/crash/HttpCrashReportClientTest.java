package com.alechilles.alecstelemetry.crash;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpCrashReportClientTest {

    @Test
    void returnsSuccessFor2xxAndSendsHeaders() throws Exception {
        AtomicBoolean jsonContentTypePresent = new AtomicBoolean(false);
        AtomicBoolean projectKeyPresent = new AtomicBoolean(false);
        HttpServer server = startServer(exchange -> {
            jsonContentTypePresent.set("application/json; charset=UTF-8".equals(exchange.getRequestHeaders().getFirst("Content-Type")));
            projectKeyPresent.set("key-123".equals(exchange.getRequestHeaders().getFirst("X-Telemetry-Project-Key")));
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        });

        try {
            HttpCrashReportClient client = new HttpCrashReportClient(1000, 1000, null);
            CrashReportClient.UploadResult result = client.upload(
                    new CrashReportClient.DeliveryTarget(
                            "http://127.0.0.1:" + server.getAddress().getPort() + "/telemetry",
                            Map.of("X-Telemetry-Project-Key", "key-123")
                    ),
                    "{\"hello\":\"world\"}"
            );

            assertTrue(result.success());
            assertTrue(jsonContentTypePresent.get());
            assertTrue(projectKeyPresent.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void returnsFailureForNon2xx() throws Exception {
        HttpServer server = startServer(exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });

        try {
            HttpCrashReportClient client = new HttpCrashReportClient(1000, 1000, null);
            CrashReportClient.UploadResult result = client.upload(
                    new CrashReportClient.DeliveryTarget(
                            "http://127.0.0.1:" + server.getAddress().getPort() + "/telemetry",
                            Map.of()
                    ),
                    "{\"hello\":\"world\"}"
            );

            assertFalse(result.success());
            assertTrue(result.statusCode() == 500 || result.statusCode() == 0);
        } finally {
            server.stop(0);
        }
    }

    private interface ExchangeHandler {
        void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException;
    }

    private static HttpServer startServer(ExchangeHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/telemetry", exchange -> handler.handle(exchange));
        server.start();
        return server;
    }
}
