package eu.datacellar.connector;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import eu.datacellar.connector.BackendAPIAuthHttpParamsDecorator.HeaderMapping;

import org.json.JSONArray;
import org.json.JSONObject;

record OpenApiSource(String id, String url, boolean legacyIds, List<HeaderMapping> authHeaders) {
    OpenApiSource {
        authHeaders = List.copyOf(authHeaders);
    }

    OpenApiSource(String id, String url, boolean legacyIds) {
        this(id, url, legacyIds, List.of());
    }

    String assetId(String operationAssetId) {
        return legacyIds ? operationAssetId : "%s--%s".formatted(id, operationAssetId);
    }

    static List<OpenApiSource> resolve(String legacyUrl, String encodedSources) {
        String normalizedLegacyUrl = trimToNull(legacyUrl);
        List<OpenApiSource> configuredSources = decodeSources(encodedSources);
        Map<String, OpenApiSource> sourcesByUrl = new LinkedHashMap<>();

        if (normalizedLegacyUrl != null) {
            OpenApiSource matchingSource = configuredSources.stream()
                    .filter(source -> source.url().equals(normalizedLegacyUrl))
                    .findFirst()
                    .orElse(new OpenApiSource("primary", normalizedLegacyUrl, true));
            sourcesByUrl.put(normalizedLegacyUrl, new OpenApiSource(
                    matchingSource.id(), matchingSource.url(), true, matchingSource.authHeaders()));
        }

        for (OpenApiSource source : configuredSources) {
            sourcesByUrl.putIfAbsent(source.url(), source);
        }

        List<OpenApiSource> resolved = new ArrayList<>(sourcesByUrl.values());
        if (normalizedLegacyUrl == null && !resolved.isEmpty()) {
            OpenApiSource first = resolved.get(0);
            resolved.set(0, new OpenApiSource(first.id(), first.url(), true, first.authHeaders()));
        }

        Set<String> ids = new HashSet<>();
        for (OpenApiSource source : resolved) {
            if (!ids.add(source.id())) {
                throw new IllegalArgumentException("OpenAPI source ID '%s' is configured more than once"
                        .formatted(source.id()));
            }
        }

        return resolved;
    }

    private static List<OpenApiSource> decodeSources(String encodedSources) {
        String normalized = trimToNull(encodedSources);
        if (normalized == null) {
            return new ArrayList<>();
        }

        try {
            String json = new String(Base64.getDecoder().decode(normalized), StandardCharsets.UTF_8);
            JSONArray array = new JSONArray(json);
            List<OpenApiSource> sources = new ArrayList<>();

            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.getJSONObject(index);
                String id = normalizeId(item.optString("id", ""));
                String url = trimToNull(item.optString("url", null));
                List<HeaderMapping> authHeaders = new ArrayList<>();
                JSONArray authHeadersJson = item.optJSONArray("authHeaders");
                if (authHeadersJson != null) {
                    for (int headerIndex = 0; headerIndex < authHeadersJson.length(); headerIndex++) {
                        JSONObject header = authHeadersJson.getJSONObject(headerIndex);
                        authHeaders.add(new HeaderMapping(
                                header.getString("name"),
                                header.getString("envvar")));
                    }
                }

                if (id == null) {
                    throw new IllegalArgumentException("OpenAPI source #%d is missing a valid ID".formatted(index + 1));
                }
                if (url == null) {
                    throw new IllegalArgumentException("OpenAPI source '%s' is missing a URL".formatted(id));
                }

                sources.add(new OpenApiSource(id, url, false, authHeaders));
            }

            return sources;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to decode OpenAPI sources configuration", exception);
        }
    }

    private static String normalizeId(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }

        normalized = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized.isEmpty() ? null : normalized;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
