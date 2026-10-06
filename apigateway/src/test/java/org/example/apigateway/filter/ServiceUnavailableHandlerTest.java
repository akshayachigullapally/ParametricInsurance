package org.example.apigateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ServiceUnavailableHandlerTest{

    @Test
    void connectionRefusedMeansServiceDown(){
        RuntimeException wrapped=new RuntimeException("io error",new ConnectException("Connection refused"));
        assertThat(ServiceUnavailableHandler.isServiceDown(wrapped)).isTrue();
    }

    @Test
    void missingInstanceMeansServiceDown(){
        assertThat(ServiceUnavailableHandler.isServiceDown(
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Unable to find instance"))).isTrue();
    }

    @Test
    void otherErrorsAreLeftAlone(){
        assertThat(ServiceUnavailableHandler.isServiceDown(new IllegalStateException("boom"))).isFalse();
        assertThat(ServiceUnavailableHandler.isServiceDown(
                new ResponseStatusException(HttpStatus.NOT_FOUND))).isFalse();
    }

    @Test
    void namesTheServiceFromThePath(){
        assertThat(ServiceUnavailableHandler.serviceFor("/api/notifications/me/unread-count"))
                .isEqualTo("Notification service");
        assertThat(ServiceUnavailableHandler.serviceFor("/api/policies/me")).isEqualTo("Product service");
        assertThat(ServiceUnavailableHandler.serviceFor("/something/else")).isEqualTo("The requested service");
    }
}
