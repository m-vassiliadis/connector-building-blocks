package eu.datacellar.connector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class BackendAPIAuthHttpParamsDecoratorTest {
    @Test
    void selectsOnlyMappingsForTheRequestedSource() {
        var ordersHeader = new BackendAPIAuthHttpParamsDecorator.HeaderMapping(
                "X-Orders-Key", "ORDERS_KEY");
        var paymentsHeader = new BackendAPIAuthHttpParamsDecorator.HeaderMapping(
                "X-Payments-Key", "PAYMENTS_KEY");
        var decorator = new BackendAPIAuthHttpParamsDecorator(
                null,
                Map.of(
                        "orders", List.of(ordersHeader),
                        "payments", List.of(paymentsHeader)));

        assertThat(decorator.mappingsForSource("orders")).containsExactly(ordersHeader);
        assertThat(decorator.mappingsForSource("payments")).containsExactly(paymentsHeader);
        assertThat(decorator.mappingsForSource("unknown")).isEmpty();
    }
}
