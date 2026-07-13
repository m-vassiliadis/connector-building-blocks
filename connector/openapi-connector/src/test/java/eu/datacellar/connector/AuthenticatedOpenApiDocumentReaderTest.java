package eu.datacellar.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

class AuthenticatedOpenApiDocumentReaderTest {
    @Test
    void fetchesAndParsesSwagger2DocumentWithBearerToken() throws IOException {
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v2/api-docs", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = """
                    {
                      "swagger":"2.0",
                      "info":{"title":"EnergyLabs API","version":"1.0.1"},
                      "paths":{
                        "/api/items/{itemUUID}":{
                          "get":{"responses":{"200":{"description":"OK"}}}
                        }
                      }
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            var result = new AuthenticatedOpenApiDocumentReader().read(
                    "http://127.0.0.1:%d/v2/api-docs".formatted(server.getAddress().getPort()),
                    "access-token");

            assertThat(authorization.get()).isEqualTo("Bearer access-token");
            assertThat(result.getOpenAPI()).isNotNull();
            assertThat(result.getOpenAPI().getPaths()).containsKey("/api/items/{itemUUID}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fetchesAndParsesOpenApi3DocumentWithBearerToken() throws IOException {
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/openapi.json", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = """
                    {
                      "openapi":"3.0.3",
                      "info":{"title":"Modern API","version":"1.0.0"},
                      "paths":{
                        "/items":{
                          "get":{"responses":{"200":{"description":"OK"}}}
                        }
                      }
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            var result = new AuthenticatedOpenApiDocumentReader().read(
                    "http://127.0.0.1:%d/openapi.json".formatted(server.getAddress().getPort()),
                    "access-token");

            assertThat(authorization.get()).isEqualTo("Bearer access-token");
            assertThat(result.getOpenAPI()).isNotNull();
            assertThat(result.getOpenAPI().getOpenapi()).isEqualTo("3.0.3");
            assertThat(result.getOpenAPI().getPaths()).containsKey("/items");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reportsAuthenticatedFetchHttpErrors() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v2/api-docs", exchange -> {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        server.start();

        try {
            String url = "http://127.0.0.1:%d/v2/api-docs".formatted(server.getAddress().getPort());

            assertThatThrownBy(() -> new AuthenticatedOpenApiDocumentReader().read(url, "bad-token"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("OpenAPI endpoint returned HTTP 401");
        } finally {
            server.stop(0);
        }
    }
}
