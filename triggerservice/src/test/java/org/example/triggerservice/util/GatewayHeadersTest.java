package org.example.triggerservice.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayHeadersTest {

    @Test
    void shouldHaveCorrectHeaderConstants() {
        assertThat(GatewayHeaders.GATEWAY_KEY).isEqualTo("X-Gateway-Key");
        assertThat(GatewayHeaders.USER_ID).isEqualTo("X-Authenticated-User-Id");
        assertThat(GatewayHeaders.USER_EMAIL).isEqualTo("X-Authenticated-User");
        assertThat(GatewayHeaders.USER_ROLE).isEqualTo("X-Authenticated-Role");
        assertThat(GatewayHeaders.CUSTOMER_ID).isEqualTo("X-Authenticated-Customer-Id");
    }
}
