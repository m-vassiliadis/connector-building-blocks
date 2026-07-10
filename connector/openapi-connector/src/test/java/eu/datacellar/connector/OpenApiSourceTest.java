package eu.datacellar.connector;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class OpenApiSourceTest {
    @Test
    void resolvesLegacySourceWithoutChangingItsIdentityMode() {
        var sources = OpenApiSource.resolve("https://primary.example/openapi.json", null);

        assertThat(sources).containsExactly(
                new OpenApiSource("primary", "https://primary.example/openapi.json", true));
    }

    @Test
    void keepsLegacySourceFirstAndAddsNamedSources() {
        String encoded = encode("""
                [
                  {"id":"orders","url":"https://orders.example/openapi.json"},
                  {"id":"payments","url":"https://payments.example/openapi.json"}
                ]
                """);

        var sources = OpenApiSource.resolve("https://orders.example/openapi.json", encoded);

        assertThat(sources).containsExactly(
                new OpenApiSource("orders", "https://orders.example/openapi.json", true),
                new OpenApiSource("payments", "https://payments.example/openapi.json", false));
    }

    @Test
    void rejectsDuplicateSourceIds() {
        String encoded = encode("""
                [
                  {"id":"api","url":"https://one.example/openapi.json"},
                  {"id":"api","url":"https://two.example/openapi.json"}
                ]
                """);

        assertThatThrownBy(() -> OpenApiSource.resolve(null, encoded))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("configured more than once");
    }

    @Test
    void namespacesOnlyNonLegacyAssetIds() {
        assertThat(new OpenApiSource("orders", "https://orders.example/openapi.json", true)
                .assetId("GET-status")).isEqualTo("GET-status");
        assertThat(new OpenApiSource("payments", "https://payments.example/openapi.json", false)
                .assetId("GET-status")).isEqualTo("payments--GET-status");
    }

    @Test
    void parsesAuthenticationMappingsPerSource() {
        String encoded = encode("""
                [{
                  "id":"payments",
                  "url":"https://payments.example/openapi.json",
                  "authHeaders":[{"name":"X-API-Key","envvar":"BACKEND_AUTH_SOURCE_0_HEADER_0"}]
                }]
                """);

        var source = OpenApiSource.resolve(null, encoded).get(0);

        assertThat(source.authHeaders()).containsExactly(
                new BackendAPIAuthHttpParamsDecorator.HeaderMapping(
                        "X-API-Key", "BACKEND_AUTH_SOURCE_0_HEADER_0"));
    }

    private String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(UTF_8));
    }
}
