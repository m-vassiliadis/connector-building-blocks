package eu.datacellar.connector;

import java.io.IOException;
import java.time.Instant;

import org.json.JSONObject;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Obtains and caches OAuth access tokens for a source backend. */
final class OAuth2PasswordTokenProvider {
    private static final long DEFAULT_TTL_SECONDS = 300;
    private static final long EXPIRY_SKEW_SECONDS = 30;

    private final OAuth2PasswordGrant grant;
    private final OkHttpClient httpClient;
    private String accessToken;
    private Instant expiresAt = Instant.EPOCH;

    OAuth2PasswordTokenProvider(OAuth2PasswordGrant grant) {
        this(grant, new OkHttpClient());
    }

    OAuth2PasswordTokenProvider(OAuth2PasswordGrant grant, OkHttpClient httpClient) {
        this.grant = grant;
        this.httpClient = httpClient;
    }

    synchronized String accessToken() {
        if (accessToken != null && Instant.now().isBefore(expiresAt)) {
            return accessToken;
        }

        Request request = new Request.Builder()
                .url(grant.tokenUrl())
                .post(new FormBody.Builder()
                        .add("grant_type", "password")
                        .add("client_id", environmentValue(grant.clientIdEnvVar()))
                        .add("client_secret", environmentValue(grant.clientSecretEnvVar()))
                        .add("username", environmentValue(grant.usernameEnvVar()))
                        .add("password", environmentValue(grant.passwordEnvVar()))
                        .build())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IllegalStateException("OAuth token endpoint returned HTTP " + response.code());
            }

            JSONObject tokenResponse = new JSONObject(response.body().string());
            String newAccessToken = tokenResponse.optString("access_token", "");
            if (newAccessToken.isBlank()) {
                throw new IllegalStateException("OAuth token endpoint did not return an access_token");
            }

            long expiresIn = tokenResponse.optLong("expires_in", DEFAULT_TTL_SECONDS);
            long cacheTtl = Math.max(1, expiresIn - EXPIRY_SKEW_SECONDS);
            accessToken = newAccessToken;
            expiresAt = Instant.now().plusSeconds(cacheTtl);
            return accessToken;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to obtain OAuth access token", exception);
        }
    }

    private String environmentValue(String variableName) {
        String value = System.getenv(variableName);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("OAuth credential environment variable is not set: " + variableName);
        }
        return value;
    }
}
