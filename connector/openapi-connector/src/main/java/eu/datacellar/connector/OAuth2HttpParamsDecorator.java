package eu.datacellar.connector;

import java.util.Map;

import org.eclipse.edc.connector.dataplane.http.spi.HttpDataAddress;
import org.eclipse.edc.connector.dataplane.http.spi.HttpParamsDecorator;
import org.eclipse.edc.connector.dataplane.http.spi.HttpRequestParams.Builder;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;

/** Adds a freshly obtained OAuth bearer token to requests for OAuth sources. */
final class OAuth2HttpParamsDecorator implements HttpParamsDecorator {
    private final Map<String, OAuth2PasswordTokenProvider> tokenProviders;

    OAuth2HttpParamsDecorator(Map<String, OAuth2PasswordTokenProvider> tokenProviders) {
        this.tokenProviders = Map.copyOf(tokenProviders);
    }

    @Override
    public Builder decorate(DataFlowStartMessage request, HttpDataAddress address, Builder builder) {
        OAuth2PasswordTokenProvider tokenProvider = tokenProviders.get(
                address.getStringProperty(BackendAPIAuthHttpParamsDecorator.OPENAPI_SOURCE_ID_PROPERTY));
        if (tokenProvider != null) {
            builder.header("Authorization", "Bearer " + tokenProvider.accessToken());
        }
        return builder;
    }
}
