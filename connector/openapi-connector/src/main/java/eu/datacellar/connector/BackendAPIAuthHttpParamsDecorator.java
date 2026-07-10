package eu.datacellar.connector;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.eclipse.edc.connector.dataplane.http.spi.HttpDataAddress;
import org.eclipse.edc.connector.dataplane.http.spi.HttpParamsDecorator;
import org.eclipse.edc.connector.dataplane.http.spi.HttpRequestParams.Builder;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Decorator class for adding static authentication headers to proxied HTTP
 * requests. This decorator adds configured headers to each request for
 * authentication with the backend API.
 * 
 * Header values are retrieved from environment variables specified during
 * construction. If an environment variable is not found, a warning is logged and
 * that header is skipped.
 */
public class BackendAPIAuthHttpParamsDecorator implements HttpParamsDecorator {

    private final Monitor monitor;
    private final List<HeaderMapping> headerMappings;
    private final Map<String, List<HeaderMapping>> sourceHeaderMappings;
    public static final String OPENAPI_SOURCE_ID_PROPERTY = "openapiSourceId";

    /**
     * Constructs a new instance of the BackendAPIAuthHttpParamsDecorator.
     *
     * @param monitor          The monitor object used for logging warnings and
     *                         debug information
     * @param apiKeyHeaderName The name of the HTTP header that will contain the API
     *                         key
     * @param apiKeyEnvVar     The name of the environment variable containing the
     *                         API key value
     */
    public BackendAPIAuthHttpParamsDecorator(Monitor monitor, String apiKeyHeaderName, String apiKeyEnvVar) {
        this.monitor = monitor;
        this.headerMappings = List.of(new HeaderMapping(apiKeyHeaderName, apiKeyEnvVar));
        this.sourceHeaderMappings = Map.of();
    }

    /**
     * Constructs a new instance of the BackendAPIAuthHttpParamsDecorator.
     *
     * @param monitor        The monitor object used for logging warnings and debug
     *                       information
     * @param headerMappings The backend authentication headers to inject. Each
     *                       mapping contains a header name and the environment
     *                       variable that contains its value.
     */
    public BackendAPIAuthHttpParamsDecorator(Monitor monitor, List<HeaderMapping> headerMappings) {
        this.monitor = monitor;
        this.headerMappings = List.copyOf(headerMappings);
        this.sourceHeaderMappings = Map.of();
    }

    public BackendAPIAuthHttpParamsDecorator(
            Monitor monitor,
            Map<String, List<HeaderMapping>> sourceHeaderMappings) {
        this.monitor = monitor;
        this.headerMappings = List.of();
        this.sourceHeaderMappings = Map.copyOf(sourceHeaderMappings);
    }

    /**
     * Parses a base64-encoded JSON array of header mappings.
     *
     * Expected decoded JSON shape:
     * [{"name":"X-User-ID","envvar":"BACKEND_AUTH_HEADER_0"}]
     *
     * @param encodedMappings Base64-encoded JSON header mappings
     * @return Parsed header mappings
     */
    public static List<HeaderMapping> parseHeaderMappings(String encodedMappings) {
        String json = new String(Base64.getDecoder().decode(encodedMappings), StandardCharsets.UTF_8);
        JSONArray mappingsJson = new JSONArray(json);
        List<HeaderMapping> mappings = new ArrayList<>();

        for (int i = 0; i < mappingsJson.length(); i++) {
            JSONObject mappingJson = mappingsJson.getJSONObject(i);
            mappings.add(new HeaderMapping(
                    mappingJson.getString("name"),
                    mappingJson.getString("envvar")));
        }

        return mappings;
    }

    /**
     * Decorates the HTTP request by adding configured backend authentication
     * headers. Header values are retrieved from the mapped environment variables.
     *
     * @param request The data flow start message containing request details
     * @param address The HTTP data address for the request
     * @param builder The builder for HTTP request parameters
     * @return The modified builder with the configured authentication headers
     */
    @Override
    public Builder decorate(DataFlowStartMessage request, HttpDataAddress address, Builder builder) {
        String sourceId = address.getStringProperty(OPENAPI_SOURCE_ID_PROPERTY);
        List<HeaderMapping> effectiveMappings = mappingsForSource(sourceId);

        for (HeaderMapping mapping : effectiveMappings) {
            String headerValue = System.getenv(mapping.envVar());

            if (headerValue == null) {
                monitor.warning(String.format(
                        "Backend authentication header value not found in environment variable: %s",
                        mapping.envVar()));
                continue;
            }

            monitor.debug(String.format(
                    "Backend authentication header value found in environment variable: %s",
                    mapping.envVar()));
            builder.header(mapping.name(), headerValue);
        }

        return builder;
    }

    List<HeaderMapping> mappingsForSource(String sourceId) {
        return sourceHeaderMappings.isEmpty()
                ? headerMappings
                : sourceHeaderMappings.getOrDefault(sourceId, List.of());
    }

    public record HeaderMapping(String name, String envVar) {
    }
}
