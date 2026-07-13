package eu.datacellar.connector;

import org.json.JSONObject;

/**
 * Environment-variable references for an OAuth 2.0 resource-owner password
 * grant. Secrets are deliberately not stored in the OpenAPI source
 * configuration.
 */
record OAuth2PasswordGrant(
        String tokenUrl,
        String clientIdEnvVar,
        String clientSecretEnvVar,
        String usernameEnvVar,
        String passwordEnvVar) {

    static OAuth2PasswordGrant fromJson(JSONObject json) {
        return new OAuth2PasswordGrant(
                required(json, "tokenUrl"),
                required(json, "clientIdEnvvar"),
                required(json, "clientSecretEnvvar"),
                required(json, "usernameEnvvar"),
                required(json, "passwordEnvvar"));
    }

    private static String required(JSONObject json, String key) {
        String value = json.optString(key, "").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("OAuth2 password grant is missing '" + key + "'");
        }
        return value;
    }
}
