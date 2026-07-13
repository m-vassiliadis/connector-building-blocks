package eu.datacellar.connector;

import java.io.IOException;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Fetches an OpenAPI document using an explicit bearer-authenticated request. */
final class AuthenticatedOpenApiDocumentReader {
    private final OkHttpClient httpClient;

    AuthenticatedOpenApiDocumentReader() {
        this(new OkHttpClient());
    }

    AuthenticatedOpenApiDocumentReader(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    SwaggerParseResult read(String sourceUrl, String accessToken) {
        Request request = new Request.Builder()
                .url(sourceUrl)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json, application/yaml, */*")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException(
                        "OpenAPI endpoint returned HTTP " + response.code());
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IllegalStateException("OpenAPI endpoint returned an empty response body");
            }

            return new OpenAPIParser().readContents(body.string(), null, null);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to fetch authenticated OpenAPI document", exception);
        }
    }
}
